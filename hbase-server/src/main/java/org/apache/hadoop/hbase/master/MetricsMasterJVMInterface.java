package org.apache.hadoop.hbase.master;

public interface MetricsMasterJVMInterface {

    void incrementSnapshotSizeComputationTime(long arg0);

    void setNumNamespacesInSpaceQuotaViolation(long arg0);

    void incrementSnapshotFetchTime(long arg0);

    void setNumRegionSizeReports(long arg0);

    void incrementSnapshotObserverTime(long arg0);

    void setNumSpaceQuotas(long arg0);

    java.lang.Object getMetricsQuotaSource();

    void incrementRequests(long arg0);

    void incrementQuotaObserverTime(long arg0);

    java.lang.Object getMetricsProcSource();

    java.lang.Object getMetricsSource();

    java.lang.Object getServerCrashProcMetrics();

    void setNumTableInSpaceQuotaViolation(long arg0);
}
