package org.apache.hadoop.hbase.io.hfile;

public interface ReaderContextJVMInterface {

    long getFileSize();

    org.apache.hadoop.hbase.fs.HFileSystemJVMInterface getFileSystem();

    boolean isPreadAllBytes();

    org.apache.hadoop.fs.Path getFilePath();

    boolean isPrimaryReplicaReader();

    org.apache.hadoop.hbase.io.FSDataInputStreamWrapperJVMInterface getInputStreamWrapper();

    java.lang.Object getReaderType();
}
