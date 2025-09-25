package org.apache.hadoop.hbase.client.locking;

public interface EntityLockJVMInterface {

    java.lang.String toString();

    boolean await(long arg0, java.util.concurrent.TimeUnit arg1) throws java.lang.InterruptedException;

    void requestLock() throws java.io.IOException;

    void unlock() throws java.io.IOException;

    void await() throws java.lang.InterruptedException;

    boolean isLocked();
}
