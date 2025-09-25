package org.apache.hadoop.hbase.master;

public interface SnapshotOfRegionAssignmentFromMetaJVMInterface {

    org.apache.hadoop.hbase.favored.FavoredNodesPlanJVMInterface getExistingAssignmentPlan();

    java.util.Map getSecondaryToRegionInfoMap();

    java.util.Map getPrimaryToRegionInfoMap();

    java.util.Map getRegionToRegionServerMap();

    java.util.Map getTableToRegionMap();

    java.util.Map getRegionNameToRegionInfoMap();

    java.util.Map getRegionServerToRegionMap();

    java.util.Set getTableSet();

    java.util.Map getTertiaryToRegionInfoMap();

    void initialize() throws java.io.IOException;
}
