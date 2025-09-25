package org.apache.hadoop.hbase.regionserver;

public interface ScanInfoJVMInterface {

    boolean isNewVersionBehavior();

    java.lang.String toString();

    int getMaxVersions();

    long getTimeToPurgeDeletes();

    byte[] getFamily();

    long getTtl();

    java.lang.Object getComparator();

    int getMinVersions();

    java.lang.Object getKeepDeletedCells();
}
