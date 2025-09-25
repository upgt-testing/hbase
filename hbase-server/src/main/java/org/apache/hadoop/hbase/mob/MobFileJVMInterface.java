package org.apache.hadoop.hbase.mob;

public interface MobFileJVMInterface {

    java.lang.String getFileName();

    org.apache.hadoop.hbase.regionserver.StoreFileScannerJVMInterface getScanner() throws java.io.IOException;

    void open() throws java.io.IOException;

    void close() throws java.io.IOException;
}
