package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.ServerJVMInterface;

public interface RegionServerServicesJVMInterface extends ServerJVMInterface, MutableOnlineRegionsJVMInterface, FavoredNodesForRegionJVMInterface {

    java.util.concurrent.ConcurrentMap getRegionsInTransitionInRS();

    void unassign(byte[] arg0) throws java.io.IOException;

    org.apache.hadoop.hbase.regionserver.HeapMemoryManagerJVMInterface getHeapMemoryManager();

    java.util.Optional getBlockCache();

    org.apache.hadoop.hbase.regionserver.RegionServerAccountingJVMInterface getRegionServerAccounting();

    org.apache.hadoop.hbase.security.access.ZKPermissionWatcherJVMInterface getZKPermissionWatcher();

    java.lang.Object getRpcServer();

    double getFlushPressure();

    boolean isClusterUp();

    java.lang.Object getTableDescriptors();

    java.lang.Object getCompactionRequestor();

    org.apache.hadoop.hbase.executor.ExecutorServiceJVMInterface getExecutorService();

    java.lang.Object getFlushRequester();

    org.apache.hadoop.hbase.quotas.RegionServerRpcQuotaManagerJVMInterface getRegionServerRpcQuotaManager();

    org.apache.hadoop.hbase.regionserver.LeaseManagerJVMInterface getLeaseManager();

    org.apache.hadoop.hbase.regionserver.MetricsRegionServerJVMInterface getMetrics();

    org.apache.hadoop.hbase.security.access.AccessCheckerJVMInterface getAccessChecker();

    org.apache.hadoop.hbase.regionserver.SecureBulkLoadManagerJVMInterface getSecureBulkLoadManager();

    java.lang.Object getFlushThroughputController();

    java.util.List getWALs() throws java.io.IOException;

    java.util.Optional getMobFileCache();

    org.apache.hadoop.hbase.quotas.RegionServerSpaceQuotaManagerJVMInterface getRegionServerSpaceQuotaManager();

    double getCompactionPressure();

    org.apache.hadoop.hbase.regionserver.ServerNonceManagerJVMInterface getNonceManager();
}
