package org.apache.hadoop.hbase.master;

import org.apache.hadoop.hbase.regionserver.HRegionServerJVMInterface;

public interface HMasterJVMInterface extends HRegionServerJVMInterface, MasterServicesJVMInterface, Runnable {

    long getMasterFinishedInitializationTime();

    int getActiveMasterInfoPort();

    java.util.List listTableNamesByNamespace(java.lang.String arg0) throws java.io.IOException;

    void setInitialized(boolean arg0);

    java.util.List listTableNames(java.lang.String arg0, java.lang.String arg1, boolean arg2) throws java.io.IOException;

    void checkIfShouldMoveSystemRegionAsync();

    void runReplicationBarrierCleaner();

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);

    org.apache.hadoop.hbase.master.ServerManagerJVMInterface getServerManager();

    boolean isReplicationPeerModificationEnabled();

    org.apache.hadoop.hbase.quotas.MasterQuotaManagerJVMInterface getMasterQuotaManager();

    org.apache.hadoop.hbase.client.BalanceResponseJVMInterface balance() throws java.io.IOException;

    org.apache.hadoop.hbase.client.BalanceResponseJVMInterface balanceOrUpdateMetrics() throws java.io.IOException;

    org.apache.hadoop.hbase.master.SplitOrMergeStateStoreJVMInterface getSplitOrMergeStateStore();

    org.apache.hadoop.hbase.procedure2.ProcedureEventJVMInterface getInitializedEvent();

    org.apache.hadoop.hbase.monitoring.MemoryBoundedLogMessageBufferJVMInterface getRegionServerFatalLogBuffer();

    void abort(java.lang.String arg0, java.lang.Throwable arg1);

    java.util.Optional getActiveMaster();

    void flushMasterStore() throws java.io.IOException;

    java.lang.String getClusterId();

    org.apache.hadoop.hbase.master.locking.LockManagerJVMInterface getLockManager();

    void shutdown() throws java.io.IOException;

    org.apache.hadoop.hbase.procedure2.ProcedureExecutorJVMInterface getMasterProcedureExecutor();

    org.apache.hadoop.hbase.master.assignment.AssignmentManagerJVMInterface getAssignmentManager();

    double getAverageLoad();

    void setCatalogJanitorEnabled(boolean arg0);

    long removeReplicationPeer(java.lang.String arg0) throws org.apache.hadoop.hbase.replication.ReplicationException, java.io.IOException;

    long disableReplicationPeer(java.lang.String arg0) throws org.apache.hadoop.hbase.replication.ReplicationException, java.io.IOException;

    java.util.Map getWalGroupsReplicationStatus();

    java.lang.Object getClusterSchema();

    boolean balanceSwitch(boolean arg0) throws java.io.IOException;

    boolean abortProcedure(long arg0, boolean arg1) throws java.io.IOException;

    void stop(java.lang.String arg0);

    org.apache.hadoop.hbase.master.replication.ReplicationPeerManagerJVMInterface getReplicationPeerManager();

    org.apache.hadoop.hbase.master.MetricsMasterJVMInterface getMasterMetrics();

    void setServiceStarted(boolean arg0);

    java.lang.Object getProcedureStore();

    long enableReplicationPeer(java.lang.String arg0) throws org.apache.hadoop.hbase.replication.ReplicationException, java.io.IOException;

    org.apache.hadoop.hbase.master.janitor.CatalogJanitorJVMInterface getCatalogJanitor();

    boolean isOnline();

    org.apache.hadoop.hbase.master.cleaner.LogCleanerJVMInterface getLogCleaner();

    org.apache.hadoop.hbase.master.region.MasterRegionJVMInterface getMasterRegion();

    boolean isBalancerOn();

    void move(byte[] arg0, byte[] arg1) throws org.apache.hadoop.hbase.HBaseIOException;

    org.apache.hadoop.hbase.master.MasterFileSystemJVMInterface getMasterFileSystem();

    java.util.List getHFileCleaners();

    java.lang.String[] getMasterCoprocessors();

    org.apache.hadoop.hbase.master.MasterWalManagerJVMInterface getMasterWalManager();

    org.apache.hadoop.hbase.ServerName getServerName();

    java.util.List listDecommissionedRegionServers();

    int getNumWALFiles();

    long getMasterActiveTime();

    boolean isInitialized();

    org.apache.hadoop.hbase.favored.FavoredNodesManagerJVMInterface getFavoredNodesManager();

    boolean isNormalizerOn();

    org.apache.hadoop.hbase.master.zksyncer.MetaLocationSyncerJVMInterface getMetaLocationSyncer();

    java.util.List listReplicationPeers(java.lang.String arg0) throws org.apache.hadoop.hbase.replication.ReplicationException, java.io.IOException;

    java.lang.Object getClusterMetricsWithoutCoprocessor() throws java.io.InterruptedIOException;

    boolean replicationPeerModificationSwitch(boolean arg0) throws java.io.IOException;

    org.apache.hadoop.hbase.replication.ReplicationPeerConfigJVMInterface getReplicationPeerConfig(java.lang.String arg0) throws org.apache.hadoop.hbase.replication.ReplicationException, java.io.IOException;

    boolean skipRegionManagementAction(java.lang.String arg0);

    void run();

    org.apache.hadoop.hbase.master.hbck.HbckChoreJVMInterface getHbckChore();

    void updateConfigurationForQuotasObserver(org.apache.hadoop.conf.Configuration arg0);

    org.apache.hadoop.hbase.monitoring.TaskGroupJVMInterface getStartupProgress();

    void remoteProcedureCompleted(long arg0);

    org.apache.hadoop.hbase.quotas.QuotaObserverChoreJVMInterface getQuotaObserverChore();

    boolean waitForMetaOnline();

    boolean isInMaintenanceMode();

    java.lang.Object getLoadBalancer();

    java.lang.String getLoadBalancerClassName();

    long getMasterStartTime();

    java.lang.String getClientIdAuditPrefix();

    void stopMaster() throws java.io.IOException;

    boolean isActiveMaster();

    java.util.List listNamespaces() throws java.io.IOException;

    java.util.List getLocks() throws java.io.IOException;

    org.apache.hadoop.hbase.master.SplitWALManagerJVMInterface getSplitWALManager();

    java.lang.Object getSpaceQuotaSnapshotNotifier();

    org.apache.hadoop.hbase.ClusterMetrics getClusterMetrics() throws java.io.IOException;

    org.apache.hadoop.hbase.master.snapshot.SnapshotManagerJVMInterface getSnapshotManager();

    java.util.Iterator getBootstrapNodes();

    org.apache.hadoop.hbase.master.cleaner.HFileCleanerJVMInterface getHFileCleaner();

    org.apache.hadoop.hbase.master.MasterCoprocessorHostJVMInterface getMasterCoprocessorHost();

    org.apache.hadoop.hbase.zookeeper.ZKWatcherJVMInterface getZooKeeper();

    java.util.List getBackupMasters();

    boolean isCatalogJanitorEnabled();

    java.util.List listTableDescriptorsByNamespace(java.lang.String arg0) throws java.io.IOException;

    org.apache.hadoop.hbase.master.MasterRpcServicesJVMInterface getMasterRpcServices();

    org.apache.hadoop.hbase.master.normalizer.RegionNormalizerManagerJVMInterface getRegionNormalizerManager();

    long getLastMajorCompactionTimestampForRegion(byte[] arg0) throws java.io.IOException;

    org.apache.hadoop.hbase.quotas.SnapshotQuotaObserverChoreJVMInterface getSnapshotQuotaObserverChore();

    java.util.List getProcedures() throws java.io.IOException;

    org.apache.hadoop.hbase.master.TableStateManagerJVMInterface getTableStateManager();

    boolean waitForNamespaceOnline();

    org.apache.hadoop.hbase.procedure.MasterProcedureManagerHostJVMInterface getMasterProcedureManagerHost();

    java.util.Collection getLiveRegionServers();
}
