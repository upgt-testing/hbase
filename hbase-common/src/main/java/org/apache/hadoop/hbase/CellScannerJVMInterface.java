package org.apache.hadoop.hbase;

public interface CellScannerJVMInterface {

    java.lang.Object current();

    boolean advance() throws java.io.IOException;
}
