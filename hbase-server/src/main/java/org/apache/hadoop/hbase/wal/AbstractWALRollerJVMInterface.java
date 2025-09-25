package org.apache.hadoop.hbase.wal;

public interface AbstractWALRollerJVMInterface<T> {

    void run();

    boolean walRollFinished();

    void waitUntilWalRollFinished() throws java.lang.InterruptedException;

    void requestRollAll();

    void close();
}
