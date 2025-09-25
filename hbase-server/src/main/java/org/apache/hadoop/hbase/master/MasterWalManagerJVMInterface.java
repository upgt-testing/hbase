package org.apache.hadoop.hbase.master;

public interface MasterWalManagerJVMInterface {

    java.util.Set getSplittingServersFromWALDir() throws java.io.IOException;

    org.apache.hadoop.fs.FileSystem getFileSystem();

    java.util.Set getLiveServersFromWALDir() throws java.io.IOException;

    void updateOldWALsDirSize() throws java.io.IOException;

    java.util.Set getFailedServersFromLogFolders() throws java.io.IOException;

    void stop();

    long getOldWALsDirSize();
}
