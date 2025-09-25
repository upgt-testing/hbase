package org.apache.hadoop.hbase.master;

public interface MasterFileSystemJVMInterface {

    org.apache.hadoop.conf.Configuration getConfiguration();

    org.apache.hadoop.hbase.ClusterIdJVMInterface getClusterId();

    org.apache.hadoop.fs.FileSystem getWALFileSystem();

    org.apache.hadoop.fs.FileSystem getFileSystem();

    void stop();

    org.apache.hadoop.fs.Path getRootDir();

    org.apache.hadoop.fs.Path getTempDir();

    org.apache.hadoop.fs.Path getWALRootDir();
}
