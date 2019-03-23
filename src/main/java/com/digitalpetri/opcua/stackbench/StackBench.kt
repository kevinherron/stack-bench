package com.digitalpetri.opcua.stackbench

import com.codahale.metrics.MetricRegistry
import com.digitalpetri.opcua.stackbench.benchmarks.ReadScalarsRegisteredBenchmark
import com.digitalpetri.opcua.stackbench.benchmarks.ReadScalarsBenchmark
import com.typesafe.config.ConfigFactory
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig
import org.eclipse.milo.opcua.stack.client.DiscoveryClient
import org.eclipse.milo.opcua.stack.core.channel.MessageLimits
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths

val METRIC_REGISTRY = MetricRegistry()

fun main(args: Array<String>) {
    if (args.size != 1) {
        println("usage: java -jar stack-bench.jar <config file>")
        System.exit(-1)
    }

    val filename = args[0]
    val config = ConfigFactory.parseFile(File(filename))

    val serverName = config.getString("stack-bench.name")
    val endpointUrl = config.getString("stack-bench.endpoint-url")
    val securityPolicyName = config.getString("stack-bench.security-policy")

    val securityPolicy = SecurityPolicy.values().first { it.name == securityPolicyName }

    val client = getOpcUaClient(endpointUrl, securityPolicy)

    executeBenchmark(serverName, ReadScalarsBenchmark(client, config))
    executeBenchmark(serverName, ReadScalarsRegisteredBenchmark(client, config))
}

private fun executeBenchmark(serverName: String, benchmark: Benchmark) {
    println("starting benchmark: ${benchmark.name}")

    val result = benchmark.execute()

    result.writeToStream(System.out)

    File("results").mkdirs()

    val fos =
        FileOutputStream(
            "results/" + serverName.replace(
                "\\s+".toRegex(),
                "_"
            ) + "_${benchmark.name}_${System.currentTimeMillis()}.txt"
        )
    result.writeToStream(fos)
    fos.flush()
    fos.close()
}

private fun getOpcUaClient(endpointUrl: String, securityPolicy: SecurityPolicy): OpcUaClient {
    val endpoints = DiscoveryClient.getEndpoints(endpointUrl).get()

    endpoints.forEach { println("Got endpoint: ${it.endpointUrl} [${it.securityPolicyUri}]") }

    val endpoint = endpoints.find { e -> e.securityPolicyUri == securityPolicy.uri }
        ?: throw Exception("endpoint for URL '$endpointUrl' not found")

    println("Connecting to endpoint: " + endpoint.endpointUrl + " [" + endpoint.securityPolicyUri + "]")

    val securityTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "security").apply {
        Files.createDirectories(this)
        if (!Files.exists(this)) {
            throw Exception("unable to create security dir: $this")
        }
    }

    LoggerFactory.getLogger("StackBench")
        .info("security temp dir: {}", securityTempDir.toAbsolutePath())

    val loader = KeyStoreLoader().load(securityTempDir)

    val config = OpcUaClientConfig.builder()
        .setApplicationName(LocalizedText.english("StackBench"))
        .setApplicationUri("urn:digitalpetri:opcua:stackbench")
        .setEndpoint(endpoint)
        .setRequestTimeout(Unsigned.uint(30000))
        .setCertificate(loader.clientCertificate)
        .setKeyPair(loader.clientKeyPair)
        .setMessageLimits(MessageLimits(8196, 16, 32 * 8196))
        .build()

    val client = OpcUaClient.create(config)

    client.connect().get()

    return client
}
