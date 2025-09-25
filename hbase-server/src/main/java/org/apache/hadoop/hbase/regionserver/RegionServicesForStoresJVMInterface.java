package org.apache.hadoop.hbase.regionserver;

public interface RegionServicesForStoresJVMInterface {

    java.lang.Object getWAL();

    java.lang.Object getRegionInfo();

    long getMemStoreFlushSize();

    org.apache.hadoop.hbase.io.ByteBuffAllocatorJVMInterface getByteBuffAllocator();

    void addMemStoreSize(long arg0, long arg1, long arg2, int arg3);

    int getNumStores();
}
