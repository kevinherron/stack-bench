package com.digitalpetri.opcua.stackbench.benchmarks

import com.codahale.metrics.Snapshot
import com.codahale.metrics.Timer
import com.digitalpetri.opcua.stackbench.Benchmark
import com.digitalpetri.opcua.stackbench.BenchmarkResult
import com.digitalpetri.opcua.stackbench.METRIC_REGISTRY
import com.google.common.collect.ImmutableList
import com.typesafe.config.Config
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.serialization.UaResponseMessage
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
import org.eclipse.milo.opcua.stack.core.types.structured.ReadRequest
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId
import java.io.OutputStream
import java.io.PrintWriter
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong

class ReadBenchmark(private val config: Config) : Benchmark {

    override fun execute(client: OpcUaClient): ReadBenchmarkResult {
        val requestCount = config.getLong("stack-bench.read-benchmark.request-count")
        val concurrencyLevels = config.getIntList("stack-bench.read-benchmark.concurrency-levels")

        val scalarNodeIds = scalarNodeIds(config)
        val arrayNodeIds = arrayNodes(config)

        val registeredScalarNodeIds = registerNodes(scalarNodeIds).get()
        val registeredArrayNodeIds = registerNodes(arrayNodeIds).get()

        if (verifyNodes(client, registeredScalarNodeIds + registeredArrayNodeIds)) {
            println("verified nodes")
        } else {
            println("not all configured nodes are valid")
            System.exit(-1)
        }

        if (resetScalarValues(client, registeredScalarNodeIds)) {
            println("reset scalar values")
        } else {
            println("could not reset all scalar values")
            System.exit(-1)
        }

        if (resetArrayValues(client, registeredArrayNodeIds)) {
            println("reset array values")
        } else {
            println("could not reset all array values")
            System.exit(-1)
        }

        val nodesToRead = (scalarNodeIds + arrayNodeIds).map {
            ReadValueId(
                it,
                AttributeId.Value.uid(),
                null, QualifiedName.NULL_VALUE
            )
        }.toTypedArray()

        val runs = ArrayList<Run>()

        for (concurrency in concurrencyLevels) {
            println("reading, concurrency=$concurrency...")

            val run = read(
                client,
                AtomicLong(0),
                requestCount,
                concurrency,
                nodesToRead
            )

            runs.add(run)

            println("concurrency=$concurrency, count=$requestCount, duration=${run.duration.toMillis()}ms, meanRate=${run.meanRate}")

            Thread.sleep(500)
        }

        return ReadBenchmarkResult(this, requestCount, runs)
    }

    private fun scalarNodeIds(config: Config): List<NodeId> {
        val nodeIds = ImmutableList.of<String>(
            config.getString("stack-bench.read-benchmark.scalar-boolean"),
            config.getString("stack-bench.read-benchmark.scalar-sbyte"),
            config.getString("stack-bench.read-benchmark.scalar-int16"),
            config.getString("stack-bench.read-benchmark.scalar-int32"),
            config.getString("stack-bench.read-benchmark.scalar-int64"),
            config.getString("stack-bench.read-benchmark.scalar-byte"),
            config.getString("stack-bench.read-benchmark.scalar-uint16"),
            config.getString("stack-bench.read-benchmark.scalar-uint32"),
            config.getString("stack-bench.read-benchmark.scalar-uint64"),
            config.getString("stack-bench.read-benchmark.scalar-float"),
            config.getString("stack-bench.read-benchmark.scalar-double"),
            config.getString("stack-bench.read-benchmark.scalar-string"),
            config.getString("stack-bench.read-benchmark.scalar-datetime"),
            config.getString("stack-bench.read-benchmark.scalar-guid"),
            config.getString("stack-bench.read-benchmark.scalar-bytestring"),
            config.getString("stack-bench.read-benchmark.scalar-xmlelement"),
            config.getString("stack-bench.read-benchmark.scalar-localizedtext"),
            config.getString("stack-bench.read-benchmark.scalar-qualifiedname")
        )

        return nodeIds.map { NodeId.parse(it) }
    }

    private fun arrayNodes(config: Config): List<NodeId> {
        val nodeIds = ImmutableList.of<String>(
            config.getString("stack-bench.read-benchmark.array-boolean"),
            config.getString("stack-bench.read-benchmark.array-sbyte"),
            config.getString("stack-bench.read-benchmark.array-int16"),
            config.getString("stack-bench.read-benchmark.array-int32"),
            config.getString("stack-bench.read-benchmark.array-int64"),
            config.getString("stack-bench.read-benchmark.array-byte"),
            config.getString("stack-bench.read-benchmark.array-uint16"),
            config.getString("stack-bench.read-benchmark.array-uint32"),
            config.getString("stack-bench.read-benchmark.array-uint64"),
            config.getString("stack-bench.read-benchmark.array-float"),
            config.getString("stack-bench.read-benchmark.array-double"),
            config.getString("stack-bench.read-benchmark.array-string"),
            config.getString("stack-bench.read-benchmark.array-datetime"),
            config.getString("stack-bench.read-benchmark.array-guid"),
            config.getString("stack-bench.read-benchmark.array-bytestring"),
            config.getString("stack-bench.read-benchmark.array-xmlelement"),
            config.getString("stack-bench.read-benchmark.array-localizedtext"),
            config.getString("stack-bench.read-benchmark.array-qualifiedname")
        )

        return nodeIds.map { NodeId.parse(it) }
    }

    private fun verifyNodes(client: OpcUaClient, nodeIds: List<NodeId>): Boolean {
        val values = client.readValues(0.0, TimestampsToReturn.Neither, nodeIds).get()

        nodeIds.zip(values).forEach { p ->
            if (p.second.statusCode.isBad) {
                println("${p.first} = ${p.second.statusCode}")
            }
        }

        return values.all { it.statusCode.isGood }
    }

    private fun registerNodes(nodeIds: List<NodeId>): CompletableFuture<List<NodeId>> {
        return CompletableFuture.completedFuture(nodeIds)
    }

    private fun resetScalarValues(client: OpcUaClient, scalarNodeIds: List<NodeId>): Boolean {
        val scalarValues = DEFAULT_VALUES.map { DataValue(Variant(it), null, null, null) }
        val statusCodes = client.writeValues(scalarNodeIds, scalarValues).get()

        return statusCodes.all { it.isGood }
    }

    private fun resetArrayValues(client: OpcUaClient, arrayNodeIds: List<NodeId>): Boolean {
        val arrayValues = DEFAULT_VALUES.map { o ->
            val array = java.lang.reflect.Array.newInstance(o.javaClass, 8)
            for (i in 0..7) {
                java.lang.reflect.Array.set(array, i, o)
            }
            DataValue(Variant(array), null, null, null)
        }
        val statusCodes = client.writeValues(arrayNodeIds, arrayValues).get()

        return statusCodes.all { it.isGood }
    }

    private fun read(client: OpcUaClient,
                     count: AtomicLong,
                     requestCount: Long,
                     concurrency: Int,
                     nodesToRead: Array<ReadValueId>): Run {

        val timer = METRIC_REGISTRY.timer("ReadBenchmark-$concurrency-${System.currentTimeMillis()}")

        val startTime = System.nanoTime()
        val readLatch = CountDownLatch(concurrency)

        for (i in 0..concurrency - 1) {
            count.incrementAndGet()

            read(client, count, requestCount, nodesToRead, timer, readLatch)
        }

        readLatch.await()
        val endTime = System.nanoTime()

        val meanRate = timer.meanRate
        val snapshot = timer.snapshot
        val duration = Duration.ofNanos(endTime - startTime)

        return Run(concurrency, duration, meanRate, snapshot)
    }

    private fun read(client: OpcUaClient,
                     count: AtomicLong,
                     requestCount: Long,
                     nodesToRead: Array<ReadValueId>,
                     timer: Timer,
                     latch: CountDownLatch) {

        client.session.thenAccept { session ->
            val request = ReadRequest(
                client.newRequestHeader(session.authenticationToken),
                0.0, TimestampsToReturn.Both, nodesToRead)

            val context = timer.time()

            client.sendRequest<UaResponseMessage>(request).whenComplete { r, ex ->
                if (ex != null) {
                    ex.printStackTrace()
                    count.decrementAndGet()
                }

                context.stop()

                if (count.incrementAndGet() <= requestCount) {
                    read(client, count, requestCount, nodesToRead, timer, latch)
                } else {
                    latch.countDown()
                }
            }
        }
    }

    override fun toString(): String {
        return "ReadBenchmark"
    }

    data class Run(val concurrency: Int, val duration: Duration, val meanRate: Double, val snapshot: Snapshot)

    class ReadBenchmarkResult(private val benchmark: ReadBenchmark,
                              private val requestCount: Long,
                              private val runs: List<Run>) : BenchmarkResult {

        override fun writeToStream(os: OutputStream) {
            val pw = PrintWriter(os)

            for ((concurrency, duration, meanRate, snapshot) in runs) {
                pw.println(benchmark.toString())
                pw.println("request-count".padEnd(16) + "\t$requestCount")
                pw.println("concurrency".padEnd(16, ' ') + "\t$concurrency")
                pw.println("duration".padEnd(16, ' ') + "\t${duration.toMillis()}ms")
                pw.println("mean-rate".padEnd(16, ' ') + "\t$meanRate")
                pw.println("75%".padEnd(16, ' ') + "\t${snapshot.get75thPercentile() / 1000000}")
                pw.println("95%".padEnd(16, ' ') + "\t${snapshot.get95thPercentile() / 1000000}")
                pw.println("98%".padEnd(16, ' ') + "\t${snapshot.get98thPercentile() / 1000000}")
                pw.println("99%".padEnd(16, ' ') + "\t${snapshot.get99thPercentile() / 1000000}")
                pw.println("99.9%".padEnd(16, ' ') + "\t${snapshot.get999thPercentile() / 1000000}")
                pw.println()
            }

            pw.flush()
        }
    }

    companion object {
        val DEFAULT_VALUES: ImmutableList<Any> = ImmutableList.of(
            true,
            Byte.MAX_VALUE,
            Short.MAX_VALUE,
            Integer.MAX_VALUE,
            Long.MAX_VALUE,
            UByte.MAX,
            UShort.MAX,
            UInteger.MAX,
            ULong.MAX,
            Float.MAX_VALUE,
            Double.MAX_VALUE,
            "abcdefghijklmnopqrstuvwxyz",
            DateTime.now(),
            UUID.randomUUID(),
            ByteString.of(ByteArray(10, Int::toByte)),
            XmlElement.of("<foo></foo>"),
            LocalizedText.english("Hello"),
            QualifiedName.parse("1:Hello")
        )
    }

}
