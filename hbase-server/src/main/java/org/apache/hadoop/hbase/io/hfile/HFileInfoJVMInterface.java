package org.apache.hadoop.hbase.io.hfile;

public interface HFileInfoJVMInterface {

    boolean isDecodeMemstoreTS();

    java.lang.String getKeyOfBiggestCell();

    int getAvgKeyLen();

    byte[] get(java.lang.Object arg0);

    void clear();

    byte[] lastKey();

    int getMajorVersion();

    long getLenOfBiggestCell();

    org.apache.hadoop.hbase.io.hfile.FixedFileTrailerJVMInterface getTrailer();

    byte[] remove(java.lang.Object arg0);

    void putAll(java.util.Map<? extends byte[], ? extends byte[]> arg0);

    java.lang.Object getDataBlockIndexReader();

    java.util.Set entrySet();

    java.lang.Object getLastKeyCell();

    void close();

    org.apache.hadoop.hbase.io.hfile.HFileInfoJVMInterface append(byte[] arg0, byte[] arg1, boolean arg2) throws java.io.IOException;

    java.util.SortedMap subMap(byte[] arg0, byte[] arg1);

    int hashCode();

    org.apache.hadoop.hbase.io.hfile.HFileContextJVMInterface getHFileContext();

    int getAvgValueLen();

    boolean isEmpty();

    boolean equals(java.lang.Object arg0);

    int size();

    byte[] put(byte[] arg0, byte[] arg1);

    java.util.Comparator comparator();

    java.util.Collection values();

    boolean containsValue(java.lang.Object arg0);

    java.lang.Object getMetaBlockIndexReader();

    boolean containsKey(java.lang.Object arg0);

    java.util.List getLoadOnOpenBlocks();

    byte[] firstKey();

    java.util.SortedMap headMap(byte[] arg0);

    java.util.Set keySet();

    java.util.SortedMap tailMap(byte[] arg0);

    boolean shouldIncludeMemStoreTS();
}
