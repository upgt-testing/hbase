package org.apache.hadoop.hbase;

public interface ScheduledChoreJVMInterface {

    java.lang.Object getStopper();

    void run();

    boolean triggerNow();

    void cancel();

    boolean isInitialChoreComplete();

    java.lang.String toString();

    void shutdown(boolean arg0);

    boolean isScheduled();

    void cancel(boolean arg0);

    int getPeriod();

    void shutdown();

    java.util.concurrent.TimeUnit getTimeUnit();

    void choreForTesting();

    java.lang.String getName();

    long getInitialDelay();
}
