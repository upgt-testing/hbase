package org.apache.hadoop.hbase.regionserver;

public interface RegionScannerJVMInterface extends InternalScannerJVMInterface {

    boolean reseek(byte[] arg0) throws java.io.IOException;

    int getBatch();

    boolean isFilterDone() throws java.io.IOException;

    java.lang.Object getRegionInfo();

    long getMaxResultSize();

    long getMvccReadPoint();
}
