package com.digitalpetri.opcua.stackbench.benchmarks

import com.typesafe.config.Config
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId


class ReadScalarsRegisteredBenchmark(client: OpcUaClient, config: Config) : ReadBenchmark(client, config) {

    override fun getName(): String = "ReadScalarsRegistered"

    override fun getNodesToRead(): List<ReadValueId> {
        val registeredNodeIds = registerNodes(client, scalarNodeIds).get()

        return registeredNodeIds.map {
            ReadValueId(
                it,
                AttributeId.Value.uid(),
                null, QualifiedName.NULL_VALUE
            )
        }
    }

}