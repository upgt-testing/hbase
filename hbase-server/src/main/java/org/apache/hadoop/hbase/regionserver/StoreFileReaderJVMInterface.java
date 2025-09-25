package org.apache.hadoop.hbase.regionserver;

public interface StoreFileReaderJVMInterface {

    java.lang.Object getBloomFilterType();

    long getFilterEntries();

    java.lang.Object getScanner(boolean arg0, boolean arg1);

    java.util.Optional getFirstKey();

    int getHFileMinorVersion();

    boolean passesDeleteFamilyBloomFilter(byte[] arg0, int arg1, int arg2);

    void setSequenceID(long arg0);

    java.util.Optional getLastRowKey();

    void setBulkLoaded(boolean arg0);

    long getTotalUncompressedBytes();

    boolean isPrimaryReplicaReader();

    java.util.Map loadFileInfo() throws java.io.IOException;

    org.apache.hadoop.hbase.regionserver.StoreFileScannerJVMInterface getStoreFileScanner(boolean arg0, boolean arg1, boolean arg2, long arg3, long arg4, boolean arg5);

    long getMaxTimestamp();

    long getEntries();

    void close(boolean arg0) throws java.io.IOException;

    org.apache.hadoop.hbase.io.hfile.ReaderContextJVMInterface getReaderContext();

    long length();

    long getTotalBloomSize();

    int getHFileVersion();

    long getSequenceID();

    java.lang.Object getHFileReader();

    int getPrefixLength();

    java.lang.Object getComparator();

    java.lang.Object getScanner(boolean arg0, boolean arg1, boolean arg2);

    java.util.Optional midKey() throws java.io.IOException;

    long indexSize();

    boolean isBulkLoaded();

    java.util.Optional getLastKey();

    long getDeleteFamilyCnt();
}
