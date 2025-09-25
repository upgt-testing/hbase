package org.apache.hadoop.hbase.client;

public interface RegionLoadStatsJVMInterface {

    int getMemStoreLoad();

    int getCompactionPressure();

    int getHeapOccupancy();

    int getMemstoreLoad();
}
