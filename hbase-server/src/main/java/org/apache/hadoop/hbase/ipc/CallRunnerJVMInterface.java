package org.apache.hadoop.hbase.ipc;

public interface CallRunnerJVMInterface {

    void run();

    java.lang.Object getRpcCall();

    void drop();
}
