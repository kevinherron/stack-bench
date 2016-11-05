package com.digitalpetri.opcua.stackbench;

import java.io.OutputStream;

public interface BenchmarkResult {

    void writeToStream(OutputStream os);

}
