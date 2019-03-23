package com.digitalpetri.opcua.stackbench;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;

public interface Benchmark {

    String getName();

    BenchmarkResult execute(OpcUaClient client);

}
