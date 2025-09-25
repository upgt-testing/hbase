package org.apache.hadoop.hbase;

public interface ChoreServiceJVMInterface {

    boolean isShutdown();

    void shutdown();

    boolean isTerminated();
}
