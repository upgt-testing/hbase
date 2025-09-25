package org.apache.hadoop.hbase.master.cleaner;

import org.apache.hadoop.hbase.ScheduledChoreJVMInterface;

public interface CleanerChoreJVMInterface<T> extends ScheduledChoreJVMInterface {

    java.util.concurrent.CompletableFuture triggerCleanerNow() throws java.lang.InterruptedException;

    boolean setEnabled(boolean arg0);

    void cleanup();

    boolean getEnabled();
}
