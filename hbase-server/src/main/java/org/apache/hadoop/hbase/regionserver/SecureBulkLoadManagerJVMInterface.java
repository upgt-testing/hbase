package org.apache.hadoop.hbase.regionserver;

public interface SecureBulkLoadManagerJVMInterface {

    void stop() throws java.io.IOException;

    void start() throws java.io.IOException;
}
