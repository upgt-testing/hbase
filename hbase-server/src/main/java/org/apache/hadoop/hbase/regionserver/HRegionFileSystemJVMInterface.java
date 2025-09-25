package org.apache.hadoop.hbase.regionserver;

public interface HRegionFileSystemJVMInterface {

    void removeStoreFile(java.lang.String arg0, org.apache.hadoop.fs.Path arg1) throws java.io.IOException;

    void setStoragePolicy(java.lang.String arg0);

    org.apache.hadoop.fs.Path createTempName();

    java.lang.Object getRegionInfo();

    java.lang.Object getRegionInfoForFS();

    void deleteFamily(java.lang.String arg0) throws java.io.IOException;

    org.apache.hadoop.fs.Path createTempName(java.lang.String arg0);

    org.apache.hadoop.fs.Path getRegionDir();

    java.util.List getStoreFiles(java.lang.String arg0, boolean arg1) throws java.io.IOException;

    java.lang.String getStoragePolicyName(java.lang.String arg0);

    java.util.Collection getFamilies() throws java.io.IOException;

    void setStoragePolicy(java.lang.String arg0, java.lang.String arg1);

    org.apache.hadoop.fs.Path getTableDir();

    java.util.List getStoreFiles(java.lang.String arg0) throws java.io.IOException;

    org.apache.hadoop.fs.FileSystem getFileSystem();

    org.apache.hadoop.fs.Path getTempDir();

    org.apache.hadoop.fs.Path getStoreDir(java.lang.String arg0);

    boolean hasReferences(java.lang.String arg0) throws java.io.IOException;

    org.apache.hadoop.fs.Path commitStoreFile(java.lang.String arg0, org.apache.hadoop.fs.Path arg1) throws java.io.IOException;
}
