package com.digitalpetri.opcua.stackbench

import com.codahale.metrics.MetricRegistry
import com.digitalpetri.opcua.stackbench.benchmarks.ReadBenchmark
import com.typesafe.config.ConfigFactory
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig
import org.eclipse.milo.opcua.stack.client.UaTcpStackClient
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned
import java.io.File
import java.io.FileOutputStream
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

val METRIC_REGISTRY = MetricRegistry()

fun main(args: Array<String>) {
    if (args.size != 1) {
        println("usage: java -jar stack-bench.jar <config file>")
        System.exit(-1)
    }

    val filename = args[0]
    val config = ConfigFactory.parseFile(File(filename))

    val name = config.getString("stack-bench.name")
    val endpointUrl = config.getString("stack-bench.endpoint-url")

    val result = ReadBenchmark(config).execute(getOpcUaClient(endpointUrl))

    result.writeToStream(System.out)

    File("results").mkdirs()

    val fos = FileOutputStream("results/" + name.replace("\\s+".toRegex(), "_") + "_read_${System.currentTimeMillis()}.txt")
    result.writeToStream(fos)
    fos.flush()
    fos.close()
}

private fun getOpcUaClient(endpointUrl: String): OpcUaClient {
    val endpoints = UaTcpStackClient.getEndpoints(endpointUrl).get()

    val endpoint = endpoints.find { e -> e.securityPolicyUri == SecurityPolicy.Basic128Rsa15.securityPolicyUri } ?:
        throw Exception("endpoint for URL '$endpointUrl' not found")

    println("Connecting to endpoint: " + endpoint.endpointUrl + " [" + endpoint.securityPolicyUri + "]")

    val loader = KeyStoreLoader().load()

    val config = OpcUaClientConfig.builder()
        .setApplicationName(LocalizedText.english("StackBench"))
        .setApplicationUri("urn:digitalpetri:opcua:stackbench")
        .setEndpoint(endpoint)
        .setRequestTimeout(Unsigned.uint(30000))
        .setCertificate(loader.clientCertificate)
        .setKeyPair(loader.clientKeyPair)
        .build()

    val client = OpcUaClient(config)

    client.connect().get()

    return client
}

private class KeyStoreLoader {

    var clientCertificate: X509Certificate? = null
        private set
    var clientKeyPair: KeyPair? = null
        private set

    fun load(): KeyStoreLoader {
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(javaClass.classLoader.getResourceAsStream("stackbench.pfx"), PASSWORD)

        val clientPrivateKey = keyStore.getKey(CLIENT_ALIAS, PASSWORD)
        if (clientPrivateKey is PrivateKey) {
            clientCertificate = keyStore.getCertificate(CLIENT_ALIAS) as X509Certificate
            val clientPublicKey = clientCertificate!!.publicKey
            clientKeyPair = KeyPair(clientPublicKey, clientPrivateKey)
        }

        return this
    }

    companion object {
        private val CLIENT_ALIAS = "client-ai"
        private val PASSWORD = "password".toCharArray()
    }

}
