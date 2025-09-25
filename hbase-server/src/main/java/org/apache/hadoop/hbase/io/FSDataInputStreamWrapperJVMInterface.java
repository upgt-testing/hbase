package org.apache.hadoop.hbase.io;

public interface FSDataInputStreamWrapperJVMInterface {

    org.apache.hadoop.hbase.fs.HFileSystemJVMInterface getHfs();

    void checksumOk();

    void prepareForBlockReader(boolean arg0) throws java.io.IOException;

    void unbuffer();

    org.apache.hadoop.fs.Path getReaderPath();

    boolean shouldUseHBaseChecksum();

    org.apache.hadoop.fs.FSDataInputStream fallbackToFsChecksum(int arg0) throws java.io.IOException;

    org.apache.hadoop.fs.FSDataInputStream getStream(boolean arg0);

    void close();
}
