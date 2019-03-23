package com.digitalpetri.opcua.stackbench;

public interface Benchmark {

    String getName();

    BenchmarkResult execute();

}
