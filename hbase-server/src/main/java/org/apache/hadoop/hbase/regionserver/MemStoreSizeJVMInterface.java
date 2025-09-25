package org.apache.hadoop.hbase.regionserver;

public interface MemStoreSizeJVMInterface {

    long getHeapSize();

    int hashCode();

    long getOffHeapSize();

    boolean equals(java.lang.Object arg0);

    boolean isEmpty();

    java.lang.String toString();

    long getDataSize();

    int getCellsCount();
}
