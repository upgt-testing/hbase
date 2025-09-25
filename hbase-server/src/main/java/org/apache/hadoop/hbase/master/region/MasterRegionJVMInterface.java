package org.apache.hadoop.hbase.master.region;

public interface MasterRegionJVMInterface {

    void close(boolean arg0);

    java.lang.Object flush(boolean arg0) throws java.io.IOException;

    void requestRollAll();

    void waitUntilWalRollFinished() throws java.lang.InterruptedException;
}
