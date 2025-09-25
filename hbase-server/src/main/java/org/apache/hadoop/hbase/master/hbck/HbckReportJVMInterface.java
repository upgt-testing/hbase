package org.apache.hadoop.hbase.master.hbck;

public interface HbckReportJVMInterface {

    java.util.Map getOrphanRegionsOnFS();

    java.time.Instant getCheckingStartTimestamp();

    java.time.Instant getCheckingEndTimestamp();

    void setCheckingStartTimestamp(java.time.Instant arg0);

    java.util.Set getDisabledTableRegions();

    java.util.Map getOrphanRegionsOnRS();

    java.util.Map getInconsistentRegions();

    java.util.Set getSplitParentRegions();

    java.util.Map getRegionInfoMap();

    void setCheckingEndTimestamp(java.time.Instant arg0);
}
