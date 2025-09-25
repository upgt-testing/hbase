package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.conf.ConfigurationObserverJVMInterface;

public interface HRegionServerJVMInterface extends RegionServerServicesJVMInterface, LastSequenceIdJVMInterface, ConfigurationObserverJVMInterface, Runnable {

    boolean isAlive();

    java.util.Collection getOnlineRegionsLocalContext();

    java.util.Optional getBlockCache();

    org.apache.hadoop.hbase.regionserver.HeapMemoryManagerJVMInterface getHeapMemoryManager();

    org.apache.hadoop.hbase.regionserver.RegionServerAccountingJVMInterface getRegionServerAccounting();

    org.apache.hadoop.hbase.security.access.ZKPermissionWatcherJVMInterface getZKPermissionWatcher();

    org.apache.hadoop.hbase.namequeues.NamedQueueRecorderJVMInterface getNamedQueueRecorder();

    java.net.InetSocketAddress[] getFavoredNodesForRegion(java.lang.String arg0);

    org.apache.hadoop.hbase.quotas.RegionServerRpcQuotaManagerJVMInterface getRegionServerRpcQuotaManager();

    org.apache.hadoop.hbase.executor.ExecutorServiceJVMInterface getExecutorService();

    org.apache.hadoop.hbase.http.InfoServerJVMInterface getInfoServer();

    boolean isAborted();

    boolean isShutDown();

    java.lang.Object createRegionLoad(java.lang.String arg0) throws java.io.IOException;

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);

    java.util.List getWALs();

    boolean isStopping();

    void waitForServerOnline();

    java.util.Optional getMobFileCache();

    int movedRegionCacheExpiredTime();

    int getNumberOfOnlineRegions();

    org.apache.hadoop.hbase.util.NettyEventLoopGroupConfigJVMInterface getEventLoopGroupConfig();

    void remoteProcedureComplete(long arg0, long arg1, java.lang.Throwable arg2);

    java.lang.Object getCoordinatedStateManager();

    org.apache.hadoop.hbase.regionserver.ServerNonceManagerJVMInterface getNonceManager();

    org.apache.hadoop.hbase.regionserver.HRegionJVMInterface getOnlineRegion(byte[] arg0);

    org.apache.hadoop.hbase.regionserver.BrokenStoreFileCleanerJVMInterface getBrokenStoreFileCleaner();

    void run();

    void unassign(byte[] arg0) throws java.io.IOException;

    boolean isStopped();

    org.apache.hadoop.hbase.regionserver.CompactedHFilesDischargerJVMInterface getCompactedHFilesDischarger();

    double getFlushPressure();

    org.apache.hadoop.hbase.regionserver.MetricsRegionServerJVMInterface getMetrics();

    void abort(java.lang.String arg0, java.lang.Throwable arg1);

    java.util.List getRegions();

    java.util.Optional getActiveMaster();

    java.lang.String getClusterId();

    org.apache.hadoop.fs.FileSystem getWALFileSystem();

    void dumpRowLocks(java.io.PrintWriter arg0);

    org.apache.hadoop.hbase.conf.ConfigurationManagerJVMInterface getConfigurationManager();

    java.lang.Object getReplicationSourceService();

    java.lang.Object createConnection(org.apache.hadoop.conf.Configuration arg0) throws java.io.IOException;

    org.apache.hadoop.hbase.regionserver.RegionServerCoprocessorHostJVMInterface getRegionServerCoprocessorHost();

    java.lang.Object getLastSequenceId(byte[] arg0);

    java.util.concurrent.ConcurrentMap getRegionsInTransitionInRS();

    java.lang.Object getRpcServer();

    java.util.Map getWalGroupsReplicationStatus();

    java.lang.String[] getRegionServerCoprocessors();

    boolean isClusterUp();

    void stop(java.lang.String arg0);

    org.apache.hadoop.hbase.MetaRegionLocationCacheJVMInterface getMetaRegionLocationCache();

    java.lang.Object getCompactionRequestor();

    java.lang.Object getFlushRequester();

    org.apache.hadoop.hbase.security.access.AccessCheckerJVMInterface getAccessChecker();

    java.util.Iterator getBootstrapNodes();

    org.apache.hadoop.hbase.zookeeper.MasterAddressTrackerJVMInterface getMasterAddressTracker();

    java.lang.Object getConnection();

    org.apache.hadoop.conf.Configuration getConfiguration();

    boolean isShutdownHookInstalled();

    org.apache.hadoop.hbase.regionserver.LogRollerJVMInterface getWalRoller();

    org.apache.hadoop.hbase.zookeeper.ZKWatcherJVMInterface getZooKeeper();

    org.apache.hadoop.hbase.regionserver.HRegionJVMInterface getRegion(java.lang.String arg0);

    org.apache.hadoop.fs.FileSystem getFileSystem();

    org.apache.hadoop.hbase.mob.RSMobFileCleanerChoreJVMInterface getRSMobFileCleanerChore();

    java.lang.Object getMovedRegion(java.lang.String arg0);

    void finishRegionProcedure(long arg0);

    org.apache.hadoop.hbase.ChoreServiceJVMInterface getChoreService();

    boolean isOnline();

    org.apache.hadoop.hbase.regionserver.HRegionJVMInterface getRegionByEncodedName(java.lang.String arg0) throws org.apache.hadoop.hbase.NotServingRegionException;

    java.util.List getBackupMasters();

    long getRetryPauseTime();

    org.apache.hadoop.hbase.quotas.RegionServerSpaceQuotaManagerJVMInterface getRegionServerSpaceQuotaManager();

    org.apache.hadoop.fs.Path getWALRootDir();

    java.lang.String toString();

    org.apache.hadoop.hbase.regionserver.RSRpcServicesJVMInterface getRSRpcServices();

    boolean walRollRequestFinished();

    java.lang.Object getTableDescriptors();

    org.apache.hadoop.hbase.regionserver.LeaseManagerJVMInterface getLeaseManager();

    java.lang.Object getClusterConnection();

    java.lang.Object getFlushThroughputController();

    org.apache.hadoop.hbase.regionserver.SecureBulkLoadManagerJVMInterface getSecureBulkLoadManager();

    java.util.Set getOnlineTables();

    org.apache.hadoop.hbase.regionserver.MemStoreFlusherJVMInterface getMemStoreFlusher();

    org.apache.hadoop.hbase.ServerName getServerName();

    long getStartcode();

    org.apache.hadoop.hbase.regionserver.CompactSplitJVMInterface getCompactSplitThread();

    double getCompactionPressure();
}
