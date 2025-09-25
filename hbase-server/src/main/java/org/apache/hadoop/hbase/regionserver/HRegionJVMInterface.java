package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.io.HeapSizeJVMInterface;
import org.apache.hadoop.hbase.conf.PropagatingConfigurationObserverJVMInterface;

public interface HRegionJVMInterface extends HeapSizeJVMInterface, PropagatingConfigurationObserverJVMInterface, RegionJVMInterface {
    boolean equalsTableName(int hashCode, java.lang.String arg0);

    void setTimeoutForWriteLock(long arg0);

    org.apache.hadoop.hbase.HDFSBlocksDistributionJVMInterface getHDFSBlocksDistribution();

    java.lang.Object getRowLock(byte[] arg0, boolean arg1) throws java.io.IOException;

    java.lang.Object getBlockCache();

    java.util.Optional checkSplit();

    java.util.Map close(boolean arg0, boolean arg1) throws java.io.IOException;

    long getOldestSeqIdOfStore(byte[] arg0);

    void checkTimestamps(java.util.Map<byte[], java.util.List<org.apache.hadoop.hbase.Cell>> arg0, long arg1) throws org.apache.hadoop.hbase.exceptions.FailedSanityCheckException;

    long getSmallestReadPoint();

    void checkFamilies(java.util.Collection<byte[]> arg0) throws org.apache.hadoop.hbase.regionserver.NoSuchColumnFamilyException;

    java.lang.Object getTableDescriptor();

    long getEarliestFlushTimeForAllStores();

    java.lang.Object getWAL();

    void blockUpdates();

    boolean isSplittable();

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);

    java.lang.Object getCellComparator();

    void incrementFlushesQueuedCount();

    java.lang.Object flush(boolean arg0) throws java.io.IOException;

    int getCompactPriority();

    org.apache.hadoop.hbase.mob.MobFileCacheJVMInterface getMobFileCache();

    long heapSize();

    void decrementCompactionsQueuedCount();

    java.util.concurrent.ConcurrentHashMap getLockedRows();

    org.apache.hadoop.hbase.regionserver.metrics.MetricsTableRequestsJVMInterface getMetricsTableRequests();

    long getOldestHfileTs(boolean arg0) throws java.io.IOException;

    void closeRegionOperation() throws java.io.IOException;

    void incrementCompactionsQueuedCount();

    boolean equals(java.lang.Object arg0);

    long getOpenSeqNum();

    boolean areWritesEnabled();

    java.util.Optional checkSplit(boolean arg0);

    long getMemStoreHeapSize();

    org.apache.hadoop.hbase.regionserver.RegionCoprocessorHostJVMInterface getCoprocessorHost();

    boolean isClosing();

    org.apache.hadoop.hbase.regionserver.MetricsRegionJVMInterface getMetrics();

    long getCheckAndMutateChecksPassed();

    long getFilteredReadRequestsCount();

    java.lang.Object getCompactionState();

    void compactStores() throws java.io.IOException;

    boolean waitForFlushes(long arg0);

    long getWriteRequestsCount();

    void setReadsEnabled(boolean arg0);

    void setRestoredRegion(boolean arg0);

    long getMemStoreFlushSize();

    boolean isAvailable();

    java.lang.Object getRowLock(byte[] arg0) throws java.io.IOException;

    long getDataInMemoryWithoutWAL();

    boolean hasReferences();

    long initialize() throws java.io.IOException;

    long getBlockedRequestsCount();

    void waitForFlushesAndCompactions();

    org.apache.hadoop.hbase.regionserver.RegionServicesForStoresJVMInterface getRegionServicesForStores();

    java.lang.Object getRegionInfo();

    org.apache.hadoop.hbase.regionserver.MultiVersionConcurrencyControlJVMInterface getMVCC();

    java.util.List getStoreFileList(byte[][] arg0) throws java.lang.IllegalArgumentException;

    java.lang.Object getLoadStatistics();

    long getCheckAndMutateChecksFailed();

    boolean isMergeable();

    long getMemStoreDataSize();

    boolean isLoadingCfsOnDemandDefault();

    long getMaxFlushedSeqId();

    org.apache.hadoop.conf.Configuration getReadOnlyConfiguration();

    void reportCompactionRequestStart(boolean arg0);

    java.util.Map getMaxStoreSeqId();

    org.apache.hadoop.fs.Path getWALRegionDir() throws java.io.IOException;

    boolean isClosed();

    int getMinBlockSizeBytes();

    void compact(boolean arg0) throws java.io.IOException;

    java.util.Map close() throws java.io.IOException;

    org.apache.hadoop.hbase.regionserver.HRegionFileSystemJVMInterface getRegionFileSystem();

    int hashCode();

    java.util.Map close(boolean arg0) throws java.io.IOException;

    void unblockUpdates();

    java.util.NavigableMap getReplicationScope();

    java.util.Map close(boolean arg0, boolean arg1, boolean arg2) throws java.io.IOException;

    java.lang.String toString();

    void addWriteRequestsCount(long arg0);

    void setClosing(boolean arg0);

    java.util.List getStores();

    long getNumMutationsWithoutWAL();

    boolean refreshStoreFiles() throws java.io.IOException;

    int getReadLockCount();

    void reportCompactionRequestEnd(boolean arg0, int arg1, long arg2);

    boolean isReadOnly();

    void addReadRequestsCount(long arg0);

    long getReadRequestsCount();

    void reportCompactionRequestFailure();

    void waitForFlushes();

    org.apache.hadoop.hbase.regionserver.HStoreJVMInterface getStore(byte[] arg0);

    void startRegionOperation() throws java.io.IOException;

    org.apache.hadoop.fs.FileSystem getFilesystem();

    long getMemStoreOffHeapSize();
}
