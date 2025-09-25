package org.apache.hadoop.hbase.regionserver;

public interface RegionServerAccountingJVMInterface {

    long getGlobalMemStoreOffHeapSize();

    java.lang.Object isAboveLowWaterMark();

    long getGlobalMemStoreHeapSize();

    java.lang.Object isAboveHighWaterMark();

    double getFlushPressure();

    void incGlobalMemStoreSize(long arg0, long arg1, long arg2);

    long getGlobalMemStoreDataSize();

    void decGlobalMemStoreSize(long arg0, long arg1, long arg2);
}
