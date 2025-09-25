package org.apache.hadoop.hbase.util;

public interface IdLockJVMInterface {

    void waitForWaiters(long arg0, int arg1) throws java.lang.InterruptedException;

    java.lang.Object tryLockEntry(long arg0, long arg1) throws java.io.IOException;

    boolean isHeldByCurrentThread(long arg0);

    java.lang.Object getLockEntry(long arg0) throws java.io.IOException;
}
