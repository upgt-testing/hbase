package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.io.HeapSizeJVMInterface;
import org.apache.hadoop.hbase.conf.PropagatingConfigurationObserverJVMInterface;

public interface HStoreJVMInterface extends StoreJVMInterface, HeapSizeJVMInterface, StoreConfigInformationJVMInterface, PropagatingConfigurationObserverJVMInterface {

    void postSnapshotOperation();

    java.util.OptionalLong getMaxMemStoreTS();

    java.lang.String getColumnFamilyName();

    long getSize();

    long getFlushedCellsSize();

    boolean isSloppyMemStore();

    long getSmallestReadPoint();

    long getNumReferenceFiles();

    int getStoreRefCount();

    long getTotalStaticIndexSize();

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);

    org.apache.hadoop.hbase.regionserver.HStoreFileJVMInterface tryCommitRecoveredHFile(org.apache.hadoop.fs.Path arg0) throws java.io.IOException;

    java.lang.Object getDataBlockEncoder();

    java.util.OptionalLong getMinStoreFileAge();

    org.apache.hadoop.hbase.io.hfile.CacheConfigJVMInterface getCacheConfig();

    long heapSize();

    int getCompactPriority();

    void assertBulkLoadHFileOk(org.apache.hadoop.fs.Path arg0) throws java.io.IOException;

    long getTotalStaticBloomSize();

    boolean shouldPerformMajorCompaction() throws java.io.IOException;

    boolean throttleCompaction(long arg0);

    java.lang.Object getColumnFamilyDescriptor();

    long getStorefilesRootLevelIndexSize();

    java.util.Optional getSplitPoint();

    java.util.OptionalLong getMaxSequenceId();

    boolean areWritesEnabled();

    long getStoreFileTtl();

    org.apache.hadoop.hbase.regionserver.RegionCoprocessorHostJVMInterface getCoprocessorHost();

    int getCompactedFilesCount();

    long getMemstoreOnlyRowReadsCount();

    int getStorefilesCount();

    void stopReplayingFromWAL();

    void closeAndArchiveCompactedFiles() throws java.io.IOException;

    org.apache.hadoop.fs.Path bulkLoadHFile(byte[] arg0, java.lang.String arg1, org.apache.hadoop.fs.Path arg2) throws java.io.IOException;

    long getMemStoreFlushSize();

    java.util.Collection getCompactedFiles();

    void startReplayingFromWAL();

    org.apache.hadoop.hbase.regionserver.StoreEngineJVMInterface getStoreEngine();

    boolean hasReferences();

    long getFlushedOutputFileSize();

    org.apache.hadoop.hbase.util.PairJVMInterface preBulkLoadHFile(java.lang.String arg0, long arg1) throws java.io.IOException;

    java.util.Collection getStorefiles();

    long getFlushedCellsCount();

    void triggerMajorCompaction();

    void refreshStoreFiles(java.util.Collection<java.lang.String> arg0) throws java.io.IOException;

    org.apache.hadoop.hbase.TableNameJVMInterface getTableName();

    java.lang.Long preFlushSeqIDEstimation();

    long getHFilesSize();

    java.lang.Object getRegionInfo();

    org.apache.hadoop.hbase.regionserver.MemStoreSizeJVMInterface getFlushableSize();

    org.apache.hadoop.hbase.regionserver.ScanInfoJVMInterface getScanInfo();

    org.apache.hadoop.hbase.regionserver.MemStoreSizeJVMInterface getMemStoreSize();

    java.util.OptionalDouble getAvgStoreFileAge();

    long getBloomFilterRequestsCount();

    int getCurrentParallelPutCount();

    long getStoreSizeUncompressed();

    org.apache.hadoop.conf.Configuration getReadOnlyConfiguration();

    boolean needsCompaction();

    long getStorefilesSize();

    org.apache.hadoop.fs.FileSystem getFileSystem();

    java.util.Optional requestCompaction() throws java.io.IOException;

    long getLastCompactSize();

    org.apache.hadoop.hbase.regionserver.MemStoreSizeJVMInterface getSnapshotSize();

    long getBlockingFileCount();

    org.apache.hadoop.hbase.regionserver.HRegionFileSystemJVMInterface getRegionFileSystem();

    java.lang.Object close() throws java.io.IOException;

    java.util.Set getStoreFilesBeingWritten();

    boolean hasTooManyStoreFiles();

    long getMajorCompactedCellsSize();

    java.lang.String toString();

    long getNumHFiles();

    void refreshStoreFiles() throws java.io.IOException;

    long getCompactionCheckMultiplier();

    boolean isPrimaryReplicaStore();

    int getMaxCompactedStoreFileRefCount();

    boolean canSplit();

    java.util.OptionalLong getMaxStoreFileAge();

    java.lang.Object getComparator();

    long getMixedRowReadsCount();

    long getMajorCompactedCellsCount();

    org.apache.hadoop.hbase.regionserver.compactions.CompactionProgressJVMInterface getCompactionProgress();

    long getCompactedCellsCount();

    org.apache.hadoop.hbase.regionserver.HRegionJVMInterface getHRegion();

    long getBloomFilterNegativeResultsCount();

    long getCompactedCellsSize();

    long getBloomFilterEligibleRequestsCount();

    void preSnapshotOperation();

    double getCompactionPressure();

    long timeOfOldestEdit();
}
