package org.apache.hadoop.hbase.executor;

public interface ExecutorServiceJVMInterface {

    java.util.Map getAllExecutorStatuses();

    void shutdown();
}
