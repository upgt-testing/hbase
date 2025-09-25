package org.apache.hadoop.hbase.io.hfile;

import org.apache.hadoop.hbase.io.HeapSizeJVMInterface;

public interface HFileContextJVMInterface extends HeapSizeJVMInterface {

    byte[] getTableName();

    java.lang.Object getChecksumType();

    java.lang.String toString();

    long getFileCreateTime();

    boolean isIncludesMvcc();

    void setIncludesMvcc(boolean arg0);

    java.lang.Object getIndexBlockEncoding();

    void setCompressTags(boolean arg0);

    boolean isCompressTags();

    boolean isCompressedOrEncrypted();

    java.lang.Object getDataBlockEncoding();

    int getBlocksize();

    boolean isIncludesTags();

    java.lang.Object getEncryptionContext();

    void setIncludesTags(boolean arg0);

    org.apache.hadoop.hbase.io.hfile.HFileContextJVMInterface clone();

    java.lang.Object getCompression();

    java.lang.String getHFileName();

    java.lang.Object getCellComparator();

    long heapSize();

    boolean isUseHBaseChecksum();

    byte[] getColumnFamily();

    int getBytesPerChecksum();

    void setFileCreateTime(long arg0);
}
