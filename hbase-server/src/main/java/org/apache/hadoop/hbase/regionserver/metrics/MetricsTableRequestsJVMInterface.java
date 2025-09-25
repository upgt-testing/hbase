package org.apache.hadoop.hbase.regionserver.metrics;

public interface MetricsTableRequestsJVMInterface {

    void updateCheckAndPut(long arg0);

    void updateCheckAndMutate(long arg0, long arg1);

    void updateTableReadQueryMeter();

    boolean isEnabTableQueryMeterMetrics();

    void updateScan(long arg0, long arg1, long arg2);

    void updateTableWriteQueryMeter();

    void removeRegistry();

    void updatePutBatch(long arg0);

    boolean isEnableTableLatenciesMetrics();

    void updateCheckAndDelete(long arg0);

    void updateTableWriteQueryMeter(long arg0);

    void updateDeleteBatch(long arg0);

    void updateTableReadQueryMeter(long arg0);

    void updateDelete(long arg0);

    org.apache.hadoop.hbase.metrics.MetricRegistryInfoJVMInterface getMetricRegistryInfo();

    void updateGet(long arg0, long arg1);

    void updatePut(long arg0);

    void updateAppend(long arg0, long arg1);

    void updateIncrement(long arg0, long arg1);
}
