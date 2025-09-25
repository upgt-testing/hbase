package org.apache.hadoop.hbase.regionserver;

public interface KeyValueScannerJVMInterface extends ShipperJVMInterface {

    java.lang.Object next() throws java.io.IOException;

    boolean seekToLastRow() throws java.io.IOException;

    void recordBlockSize(java.util.function.IntConsumer arg0);

    org.apache.hadoop.fs.Path getFilePath();

    void enforceSeek() throws java.io.IOException;

    boolean isFileScanner();

    boolean realSeekDone();

    java.lang.Object peek();

    java.lang.Object getNextIndexedKey();

    void close();
}
