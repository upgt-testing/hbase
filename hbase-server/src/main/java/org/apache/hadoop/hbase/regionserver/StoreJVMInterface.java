package org.apache.hadoop.hbase.regionserver;

public interface StoreJVMInterface {

    java.util.OptionalLong getMaxMemStoreTS();

    java.lang.String getColumnFamilyName();

    long getFlushedCellsSize();

    long getSize();

    boolean isSloppyMemStore();

    long getSmallestReadPoint();

    long getNumReferenceFiles();

    long getTotalStaticIndexSize();

    java.util.OptionalLong getMinStoreFileAge();

    int getCompactPriority();

    boolean shouldPerformMajorCompaction() throws java.io.IOException;

    long getTotalStaticBloomSize();

    java.lang.Object getColumnFamilyDescriptor();

    long getStorefilesRootLevelIndexSize();

    java.util.OptionalLong getMaxSequenceId();

    boolean areWritesEnabled();

    long getMemstoreOnlyRowReadsCount();

    int getCompactedFilesCount();

    int getStorefilesCount();

    java.util.Collection getCompactedFiles();

    boolean hasReferences();

    long getFlushedOutputFileSize();

    java.util.Collection getStorefiles();

    long getFlushedCellsCount();

    org.apache.hadoop.hbase.TableNameJVMInterface getTableName();

    java.lang.Object getRegionInfo();

    long getHFilesSize();

    org.apache.hadoop.hbase.regionserver.MemStoreSizeJVMInterface getFlushableSize();

    org.apache.hadoop.hbase.regionserver.MemStoreSizeJVMInterface getMemStoreSize();

    java.util.OptionalDouble getAvgStoreFileAge();

    int getCurrentParallelPutCount();

    long getBloomFilterRequestsCount();

    long getStoreSizeUncompressed();

    org.apache.hadoop.conf.Configuration getReadOnlyConfiguration();

    long getStorefilesSize();

    boolean needsCompaction();

    org.apache.hadoop.fs.FileSystem getFileSystem();

    long getLastCompactSize();

    org.apache.hadoop.hbase.regionserver.MemStoreSizeJVMInterface getSnapshotSize();

    boolean hasTooManyStoreFiles();

    long getMajorCompactedCellsSize();

    long getNumHFiles();

    void refreshStoreFiles() throws java.io.IOException;

    boolean isPrimaryReplicaStore();

    boolean canSplit();

    java.util.OptionalLong getMaxStoreFileAge();

    java.lang.Object getComparator();

    long getMixedRowReadsCount();

    long getMajorCompactedCellsCount();

    long getCompactedCellsCount();

    long getBloomFilterNegativeResultsCount();

    long getCompactedCellsSize();

    long getBloomFilterEligibleRequestsCount();

    double getCompactionPressure();

    long timeOfOldestEdit();
}
