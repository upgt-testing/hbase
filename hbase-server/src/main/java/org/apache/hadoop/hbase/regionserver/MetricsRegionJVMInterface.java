package org.apache.hadoop.hbase.regionserver;

public interface MetricsRegionJVMInterface {

    void updateDelete();

    void updateReadRequestCount();

    void updateAppend();

    void updateGet(long arg0);

    void updatePut();

    void updateFilteredRecords();

    void updateIncrement();

    void updateScanTime(long arg0);

    java.lang.Object getRegionWrapper();

    void close();
}
