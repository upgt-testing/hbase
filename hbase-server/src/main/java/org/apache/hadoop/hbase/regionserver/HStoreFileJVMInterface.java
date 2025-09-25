package org.apache.hadoop.hbase.regionserver;

public interface HStoreFileJVMInterface extends StoreFileJVMInterface {

    long getMaxMemStoreTS();

    org.apache.hadoop.hbase.HDFSBlocksDistributionJVMInterface getHDFSBlockDistribution();

    java.util.OptionalLong getMinimumTimestamp();

    java.util.Optional getFirstKey();

    java.util.OptionalLong getBulkLoadTimestamp();

    org.apache.hadoop.fs.Path getQualifiedPath();

    java.util.OptionalLong getMaximumTimestamp();

    void markCompactedAway();

    org.apache.hadoop.hbase.regionserver.StoreFileScannerJVMInterface getStreamScanner(boolean arg0, boolean arg1, boolean arg2, long arg3, long arg4, boolean arg5) throws java.io.IOException;

    boolean isReference();

    void deleteStoreFile() throws java.io.IOException;

    boolean isMajorCompactionResult();

    byte[] getMetadataValue(byte[] arg0);

    org.apache.hadoop.hbase.regionserver.StoreFileReaderJVMInterface getReader();

    org.apache.hadoop.hbase.io.hfile.CacheConfigJVMInterface getCacheConf();

    long getModificationTimeStamp() throws java.io.IOException;

    org.apache.hadoop.fs.Path getEncodedPath();

    long getMaxSequenceId();

    java.lang.String toString();

    long getModificationTimestamp() throws java.io.IOException;

    java.lang.Object getComparator();

    boolean isBulkLoadResult();

    int getRefCount();

    org.apache.hadoop.hbase.regionserver.StoreFileScannerJVMInterface getPreadScanner(boolean arg0, long arg1, long arg2, boolean arg3);

    org.apache.hadoop.fs.Path getPath();

    java.util.Optional getLastKey();

    boolean isCompactedAway();

    boolean isHFile();

    boolean excludeFromMinorCompaction();

    boolean isReferencedInReads();

    boolean isHistorical();

    void initReader() throws java.io.IOException;

    org.apache.hadoop.hbase.regionserver.StoreFileInfoJVMInterface getFileInfo();

    void closeStoreFile(boolean arg0) throws java.io.IOException;

    java.lang.String toStringDetailed();
}
