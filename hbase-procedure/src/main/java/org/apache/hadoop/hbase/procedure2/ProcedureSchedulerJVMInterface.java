package org.apache.hadoop.hbase.procedure2;

public interface ProcedureSchedulerJVMInterface {

    org.apache.hadoop.hbase.procedure2.ProcedureJVMInterface poll(long arg0, java.util.concurrent.TimeUnit arg1);

    void signalAll();

    void clear();

    boolean hasRunnables();

    int size();

    void stop();

    org.apache.hadoop.hbase.procedure2.ProcedureJVMInterface poll();

    java.util.List getLocks();

    void start();
}
