package org.apache.hadoop.hbase.io.hfile;

public interface FixedFileTrailerJVMInterface {

    void setTotalUncompressedBytes(long arg0);

    void setNumDataIndexLevels(int arg0);

    int getNumDataIndexLevels();

    void setEntryCount(long arg0);

    int getMajorVersion();

    void setEncryptionKey(byte[] arg0);

    void setLoadOnOpenOffset(long arg0);

    void expectMinorVersion(int arg0);

    void setMetaIndexCount(int arg0);

    long getEntryCount();

    void setLastDataBlockOffset(long arg0);

    void expectAtLeastMajorVersion(int arg0);

    long getFileInfoOffset();

    void setDataIndexCount(int arg0);

    long getTotalUncompressedBytes();

    java.lang.String getComparatorClassName();

    long getUncompressedDataIndexSize();

    long getLoadOnOpenDataOffset();

    int getMetaIndexCount();

    java.lang.String toString();

    int getTrailerSize();

    int getMinorVersion();

    long getFirstDataBlockOffset();

    void expectMajorVersion(int arg0);

    long getLastDataBlockOffset();

    byte[] getEncryptionKey();

    void setFirstDataBlockOffset(long arg0);

    void setFileInfoOffset(long arg0);

    java.lang.Object getCompressionCodec();

    int getDataIndexCount();

    void setUncompressedDataIndexSize(long arg0);
}
