package org.apache.hadoop.hbase.master;

import org.apache.hadoop.hbase.ServerJVMInterface;

public interface MasterServicesJVMInterface extends ServerJVMInterface {

    org.apache.hadoop.hbase.security.access.ZKPermissionWatcherJVMInterface getZKPermissionWatcher();

    java.lang.Object getClusterSchema();

    java.util.List listTableNamesByNamespace(java.lang.String arg0) throws java.io.IOException;

    org.apache.hadoop.hbase.favored.FavoredNodesManagerJVMInterface getFavoredNodesManager();

    org.apache.hadoop.hbase.master.snapshot.SnapshotManagerJVMInterface getSnapshotManager();

    boolean abortProcedure(long arg0, boolean arg1) throws java.io.IOException;

    org.apache.hadoop.hbase.master.replication.ReplicationPeerManagerJVMInterface getReplicationPeerManager();

    boolean isClusterUp();

    org.apache.hadoop.hbase.executor.ExecutorServiceJVMInterface getExecutorService();

    org.apache.hadoop.hbase.security.access.AccessCheckerJVMInterface getAccessChecker();

    void checkIfShouldMoveSystemRegionAsync();

    void runReplicationBarrierCleaner();

    org.apache.hadoop.hbase.master.MetricsMasterJVMInterface getMasterMetrics();

    org.apache.hadoop.hbase.master.zksyncer.MetaLocationSyncerJVMInterface getMetaLocationSyncer();

    org.apache.hadoop.hbase.master.MasterCoprocessorHostJVMInterface getMasterCoprocessorHost();

    java.util.List listReplicationPeers(java.lang.String arg0) throws org.apache.hadoop.hbase.replication.ReplicationException, java.io.IOException;

    long enableReplicationPeer(java.lang.String arg0) throws org.apache.hadoop.hbase.replication.ReplicationException, java.io.IOException;

    org.apache.hadoop.hbase.master.janitor.CatalogJanitorJVMInterface getCatalogJanitor();

    boolean replicationPeerModificationSwitch(boolean arg0) throws java.io.IOException;

    org.apache.hadoop.hbase.master.ServerManagerJVMInterface getServerManager();

    org.apache.hadoop.hbase.replication.ReplicationPeerConfigJVMInterface getReplicationPeerConfig(java.lang.String arg0) throws org.apache.hadoop.hbase.replication.ReplicationException, java.io.IOException;

    boolean isReplicationPeerModificationEnabled();

    org.apache.hadoop.hbase.quotas.MasterQuotaManagerJVMInterface getMasterQuotaManager();

    boolean skipRegionManagementAction(java.lang.String arg0);

    java.util.List listTableDescriptorsByNamespace(java.lang.String arg0) throws java.io.IOException;

    org.apache.hadoop.hbase.procedure2.ProcedureEventJVMInterface getInitializedEvent();

    org.apache.hadoop.hbase.master.hbck.HbckChoreJVMInterface getHbckChore();

    java.lang.Object getTableDescriptors();

    org.apache.hadoop.hbase.master.normalizer.RegionNormalizerManagerJVMInterface getRegionNormalizerManager();

    org.apache.hadoop.hbase.master.MasterFileSystemJVMInterface getMasterFileSystem();

    long getLastMajorCompactionTimestampForRegion(byte[] arg0) throws java.io.IOException;

    void flushMasterStore() throws java.io.IOException;

    boolean isInMaintenanceMode();

    java.lang.Object getLoadBalancer();

    org.apache.hadoop.hbase.master.locking.LockManagerJVMInterface getLockManager();

    java.util.List getProcedures() throws java.io.IOException;

    org.apache.hadoop.hbase.master.TableStateManagerJVMInterface getTableStateManager();

    org.apache.hadoop.hbase.master.MasterWalManagerJVMInterface getMasterWalManager();

    org.apache.hadoop.hbase.procedure.MasterProcedureManagerHostJVMInterface getMasterProcedureManagerHost();

    org.apache.hadoop.hbase.procedure2.ProcedureExecutorJVMInterface getMasterProcedureExecutor();

    java.lang.String getClientIdAuditPrefix();

    boolean isActiveMaster();

    org.apache.hadoop.hbase.master.assignment.AssignmentManagerJVMInterface getAssignmentManager();

    long removeReplicationPeer(java.lang.String arg0) throws org.apache.hadoop.hbase.replication.ReplicationException, java.io.IOException;

    java.util.List getLocks() throws java.io.IOException;

    long getMasterActiveTime();

    boolean isInitialized();

    long disableReplicationPeer(java.lang.String arg0) throws org.apache.hadoop.hbase.replication.ReplicationException, java.io.IOException;
}
