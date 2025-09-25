package org.apache.hadoop.hbase.regionserver;

public interface StoreFileInfoJVMInterface {

    int hashCode();

    org.apache.hadoop.hbase.io.ReferenceJVMInterface getReference();

    long getModificationTime() throws java.io.IOException;

    void setConf(org.apache.hadoop.conf.Configuration arg0);

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    org.apache.hadoop.hbase.HDFSBlocksDistributionJVMInterface getHDFSBlockDistribution();

    long getSize();

    org.apache.hadoop.conf.Configuration getConf();

    java.lang.String getActiveFileName();

    org.apache.hadoop.fs.FileStatus getFileStatus() throws java.io.IOException;

    org.apache.hadoop.fs.Path getPath();

    long getCreatedTimestamp();

    boolean isTopReference();

    boolean isLink();

    org.apache.hadoop.hbase.HDFSBlocksDistributionJVMInterface computeHDFSBlocksDistribution(org.apache.hadoop.fs.FileSystem arg0) throws java.io.IOException;

    boolean isReference();

    org.apache.hadoop.fs.FileStatus getReferencedFileStatus(org.apache.hadoop.fs.FileSystem arg0) throws java.io.IOException;

    org.apache.hadoop.hbase.io.hfile.HFileInfoJVMInterface getHFileInfo();
}
