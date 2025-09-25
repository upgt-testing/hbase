package org.apache.hadoop.hbase.fs;

public interface HFileSystemJVMInterface {

    org.apache.hadoop.fs.FileSystem getBackingFs() throws java.io.IOException;

    boolean useHBaseChecksum();

    org.apache.hadoop.fs.FileSystem getNoChecksumFs();

    void setStoragePolicy(org.apache.hadoop.fs.Path arg0, java.lang.String arg1);

    java.lang.String getStoragePolicyName(org.apache.hadoop.fs.Path arg0);

    void close() throws java.io.IOException;
}
