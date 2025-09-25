package org.apache.hadoop.hbase.master;

import org.apache.hadoop.hbase.coprocessor.CoprocessorHostJVMInterface;

public interface MasterCoprocessorHostJVMInterface extends CoprocessorHostJVMInterface<org.apache.hadoop.hbase.coprocessor.MasterCoprocessor, org.apache.hadoop.hbase.coprocessor.MasterCoprocessorEnvironment> {

    void preGetLocks() throws java.io.IOException;

    void postListNamespaces(java.util.List<java.lang.String> arg0) throws java.io.IOException;

    void postGetRSGroupInfo(java.lang.String arg0) throws java.io.IOException;

    void postAddRSGroup(java.lang.String arg0) throws java.io.IOException;

    void postEnableReplicationPeer(java.lang.String arg0) throws java.io.IOException;

    void preListDecommissionedRegionServers() throws java.io.IOException;

    void postListDecommissionedRegionServers() throws java.io.IOException;

    void postDisableReplicationPeer(java.lang.String arg0) throws java.io.IOException;

    void preGetProcedures() throws java.io.IOException;

    void preGetNamespaceDescriptor(java.lang.String arg0) throws java.io.IOException;

    void preMasterStoreFlush() throws java.io.IOException;

    void preDisableReplicationPeer(java.lang.String arg0) throws java.io.IOException;

    void preRemoveRSGroup(java.lang.String arg0) throws java.io.IOException;

    void preDeleteNamespace(java.lang.String arg0) throws java.io.IOException;

    void postIsRpcThrottleEnabled(boolean arg0) throws java.io.IOException;

    void postSwitchExceedThrottleQuota(boolean arg0, boolean arg1) throws java.io.IOException;

    void preSwitchExceedThrottleQuota(boolean arg0) throws java.io.IOException;

    void postUpdateRSGroupConfig(java.lang.String arg0, java.util.Map<java.lang.String, java.lang.String> arg1) throws java.io.IOException;

    void postRenameRSGroup(java.lang.String arg0, java.lang.String arg1) throws java.io.IOException;

    void postSwitchRpcThrottle(boolean arg0, boolean arg1) throws java.io.IOException;

    void postMasterStoreFlush() throws java.io.IOException;

    void preMasterInitialization() throws java.io.IOException;

    void preUpdateRSGroupConfig(java.lang.String arg0, java.util.Map<java.lang.String, java.lang.String> arg1) throws java.io.IOException;

    void preGetClusterMetrics() throws java.io.IOException;

    void postBalanceSwitch(boolean arg0, boolean arg1) throws java.io.IOException;

    void postListRSGroups() throws java.io.IOException;

    void postDeleteNamespace(java.lang.String arg0) throws java.io.IOException;

    void postStartMaster() throws java.io.IOException;

    void preRemoveReplicationPeer(java.lang.String arg0) throws java.io.IOException;

    void postGetReplicationPeerConfig(java.lang.String arg0) throws java.io.IOException;

    void postUpdateConfiguration(org.apache.hadoop.conf.Configuration arg0) throws java.io.IOException;

    void preUpdateConfiguration(org.apache.hadoop.conf.Configuration arg0) throws java.io.IOException;

    void preIsRpcThrottleEnabled() throws java.io.IOException;

    void postAbortProcedure() throws java.io.IOException;

    void preListRSGroups() throws java.io.IOException;

    void preShutdown() throws java.io.IOException;

    void preAddRSGroup(java.lang.String arg0) throws java.io.IOException;

    void preListReplicationPeers(java.lang.String arg0) throws java.io.IOException;

    void postListReplicationPeers(java.lang.String arg0) throws java.io.IOException;

    void preRenameRSGroup(java.lang.String arg0, java.lang.String arg1) throws java.io.IOException;

    void preEnableReplicationPeer(java.lang.String arg0) throws java.io.IOException;

    void preBalanceSwitch(boolean arg0) throws java.io.IOException;

    void preClearDeadServers() throws java.io.IOException;

    void preSwitchRpcThrottle(boolean arg0) throws java.io.IOException;

    void preListNamespaces(java.util.List<java.lang.String> arg0) throws java.io.IOException;

    void postRemoveReplicationPeer(java.lang.String arg0) throws java.io.IOException;

    void postRemoveRSGroup(java.lang.String arg0) throws java.io.IOException;

    void preStopMaster() throws java.io.IOException;

    void preGetRSGroupInfo(java.lang.String arg0) throws java.io.IOException;

    void preGetReplicationPeerConfig(java.lang.String arg0) throws java.io.IOException;
}
