package org.apache.hadoop.hbase.regionserver;

public interface StoreEngineJVMInterface<SF, CP, C, SFM> {

    boolean requireWritingToTmpDirFirst();

    void refreshStoreFiles(java.util.Collection<java.lang.String> arg0) throws java.io.IOException;

    void validateStoreFile(org.apache.hadoop.fs.Path arg0) throws java.io.IOException;

    void readUnlock();

    void refreshStoreFiles() throws java.io.IOException;

    org.apache.hadoop.hbase.regionserver.compactions.CompactionPolicyJVMInterface getCompactionPolicy();

    org.apache.hadoop.hbase.regionserver.StoreFlusherJVMInterface getStoreFlusher();

    org.apache.hadoop.hbase.io.hfile.BloomFilterMetricsJVMInterface getBloomFilterMetrics();

    org.apache.hadoop.hbase.regionserver.compactions.CompactionContextJVMInterface createCompaction() throws java.io.IOException;

    void writeUnlock();

    java.util.List commitStoreFiles(java.util.List<org.apache.hadoop.fs.Path> arg0, boolean arg1) throws java.io.IOException;

    org.apache.hadoop.hbase.regionserver.compactions.CompactorJVMInterface getCompactor();

    void initialize(boolean arg0) throws java.io.IOException;

    void readLock();

    java.lang.Object getStoreFileManager();

    void writeLock();

    org.apache.hadoop.hbase.regionserver.HStoreFileJVMInterface createStoreFileAndReader(org.apache.hadoop.fs.Path arg0) throws java.io.IOException;
}
