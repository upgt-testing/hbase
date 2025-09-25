package org.apache.hadoop.hbase.regionserver;

public interface MetricsRegionServerJVMInterface {

    java.lang.Object getRegionServerWrapper();

    void incrementNumRegionSizeReportsSent(long arg0);

    void incrSplitRequest();

    void incrScannerLeaseExpired();

    void updateReplay(long arg0);

    void updateFlush(java.lang.String arg0, long arg1, long arg2, long arg3);

    void updateSplitTime(long arg0);

    void incrSplitSuccess();

    void incrementRegionSizeReportingChoreTime(long arg0);

    void updateBulkLoad(long arg0);

    java.lang.Object getMetricsSource();

    void updateCompaction(java.lang.String arg0, boolean arg1, long arg2, int arg3, int arg4, long arg5, long arg6);

    java.lang.Object getMetricsUserAggregate();
}
