# Building

`mvn clean package`

# Running

Start a server to test against and make sure it has a .conf entry in the `configs` directory.

Then run: `java -jar target/stack-bench-$version.jar configs/$config.conf`

# Results

Benchmark performed on Intel Core i7-6700K running Fedora 24, kernel 4.7.9, Oracle Java 8u101

## Throughput, SecurityPolicy=None
| Stack | Concurrency=1 | C=4 | C=16 | C=64 | C=128 |
| ----- | ------------- | --- | ---- | ---- | ----- |
| Eclipse Milo | **7599 req/s** | **23626 req/s** | **28867 req/s** | **27979 req/s** | **29085 req/s** |
| Prosys/OPC Foundation | 2784 req/s | 6298 req/s | 8507 req/s | 10515 req/s | 11842 req/s |
| node-opcua | 743 req/s | 1024 req/s | 1038 req/s | 1042 req/s | 1034 req/s |

## Throughput, SecurityPolicy=Basic128Rsa15
| Stack | Concurrency=1 | C=4 | C=16 | C=64 | C=128 |
| ----- | ------------- | --- | ---- | ---- | ----- |
| Eclipse Milo | **4214 req/s** | **14202 req/s** | **16328 req/s** | **16334 req/s** | **16339 req/s** |
| Prosys/OPC Foundation | 2493 req/s | 6118 req/s | 7850 req/s | 9574 req/s | 10426 req/s |
| node-opcua | 723 req/s | 986 req/s | 996 req/s | 997 req/s | 984 req/s |
