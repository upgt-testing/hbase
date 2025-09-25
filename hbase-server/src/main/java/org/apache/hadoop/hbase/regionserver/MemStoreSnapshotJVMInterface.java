package org.apache.hadoop.hbase.regionserver;

public interface MemStoreSnapshotJVMInterface {

    long getDataSize();

    org.apache.hadoop.hbase.regionserver.MemStoreSizeJVMInterface getMemStoreSize();

    boolean isTagsPresent();

    long getId();

    java.util.List getScanners();

    int getCellsCount();

    org.apache.hadoop.hbase.regionserver.TimeRangeTrackerJVMInterface getTimeRangeTracker();
}
