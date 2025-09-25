package org.apache.hadoop.hbase.procedure2;

public interface AbstractProcedureSchedulerJVMInterface extends ProcedureSchedulerJVMInterface {

    org.apache.hadoop.hbase.procedure2.ProcedureJVMInterface poll(long arg0, java.util.concurrent.TimeUnit arg1);

    void signalAll();

    java.lang.String toString();

    boolean hasRunnables();

    int size();

    long getNullPollCalls();

    void stop();

    org.apache.hadoop.hbase.procedure2.ProcedureJVMInterface poll(long arg0);

    void start();

    org.apache.hadoop.hbase.procedure2.ProcedureJVMInterface poll();

    long getPollCalls();
}
