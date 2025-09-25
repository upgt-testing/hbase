package org.apache.hadoop.hbase.master;

public interface MetricsAssignmentManagerJVMInterface {

    void updateOrphanRegionsOnFs(int arg0);

    void updateUnknownServerRegions(int arg0);

    void updateRitDuration(long arg0);

    java.lang.Object getSplitProcMetrics();

    void updateRITCount(int arg0);

    void updateUnknownServerOpenRegions(int arg0);

    void updateOverlaps(int arg0);

    void incrementOperationCounter();

    java.lang.Object getMergeProcMetrics();

    void updateHoles(int arg0);

    java.lang.Object getReopenProcMetrics();

    java.lang.Object getOpenProcMetrics();

    java.lang.Object getUnassignProcMetrics();

    void updateOrphanRegionsOnRs(int arg0);

    java.lang.Object getAssignProcMetrics();

    java.lang.Object getMetricsProcSource();

    void updateDeadServerOpenRegions(int arg0);

    java.lang.Object getMoveProcMetrics();

    void updateInconsistentRegions(int arg0);

    void updateRITOldestAge(long arg0);

    java.lang.Object getCloseProcMetrics();

    void updateEmptyRegionInfoRegions(int arg0);

    void updateRITCountOverThreshold(int arg0);
}
