package org.apache.hadoop.hbase.master.assignment;

public interface AssignmentManagerJVMInterface {

    java.util.Set getMetaRegionSet();

    boolean isForceRegionRetainment();

    boolean isRunning();

    java.lang.Object getRegionInfo(java.lang.String arg0);

    boolean hasRegionsInTransition();

    void populateRegionStatesFromMeta(java.lang.String arg0) throws java.io.IOException;

    int getNumRegionsOpened();

    void stop();

    void checkIfShouldMoveSystemRegionAsync();

    org.apache.hadoop.conf.Configuration getConfiguration();

    java.util.Map getRSReports();

    void joinCluster() throws java.io.IOException;

    java.lang.Object computeRegionInTransitionStat();

    org.apache.hadoop.hbase.master.assignment.RegionStateStoreJVMInterface getRegionStateStore();

    boolean isMetaRegionInTransition();

    int getForceRegionRetainmentRetries();

    boolean isMetaLoaded();

    java.util.List getExcludedServersForSystemTable();

    org.apache.hadoop.hbase.master.MetricsAssignmentManagerJVMInterface getAssignmentManagerMetrics();

    void processOfflineRegions();

    long getForceRegionRetainmentWaitInterval();

    void start() throws java.io.IOException, org.apache.zookeeper.KeeperException;

    boolean isMetaAssigned();

    org.apache.hadoop.hbase.master.assignment.RegionStatesJVMInterface getRegionStates();

    java.util.List getAssignedRegions();

    void wakeMetaLoadedEvent();

    java.lang.Object getRegionInfo(byte[] arg0);

    boolean isMetaRegion(byte[] arg0);

    java.util.List getRegionsInTransition();

    java.lang.Object getMetaRegionFromName(byte[] arg0);
}
