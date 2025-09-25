package org.apache.hadoop.hbase.regionserver;

public interface StoreFileScannerJVMInterface extends KeyValueScannerJVMInterface {

    java.lang.String toString();

    boolean isFileScanner();

    long getScannerOrder();

    java.lang.Object next() throws java.io.IOException;

    boolean seekToLastRow() throws java.io.IOException;

    void recordBlockSize(java.util.function.IntConsumer arg0);

    void shipped() throws java.io.IOException;

    org.apache.hadoop.fs.Path getFilePath();

    void enforceSeek() throws java.io.IOException;

    boolean realSeekDone();

    java.lang.Object getNextIndexedKey();

    java.lang.Object peek();

    void close();
}
