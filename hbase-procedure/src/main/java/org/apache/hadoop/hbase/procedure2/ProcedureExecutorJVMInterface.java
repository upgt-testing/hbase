package org.apache.hadoop.hbase.procedure2;

public interface ProcedureExecutorJVMInterface<TEnvironment> {

    void startWorkers() throws java.io.IOException;

    org.apache.hadoop.hbase.procedure2.ProcedureJVMInterface getResult(long arg0);

    void init(int arg0, boolean arg1) throws java.io.IOException;

    org.apache.hadoop.hbase.procedure2.ProcedureJVMInterface getResultOrProcedure(long arg0);

    boolean isRunning();

    void stop();

    long getKeepAliveTime(java.util.concurrent.TimeUnit arg0);

    java.util.Set getActiveProcIds();

    void refreshConfiguration(org.apache.hadoop.conf.Configuration arg0);

    boolean isFinished(long arg0);

    int getActiveExecutorCount();

    void join();

    org.apache.hadoop.hbase.procedure2.ProcedureJVMInterface getProcedure(long arg0);

    java.util.Collection getActiveProceduresNoCopy();

    int getCorePoolSize();

    java.lang.Object getStore();

    boolean abort(long arg0, boolean arg1);

    boolean isStarted(long arg0);

    void removeResult(long arg0);

    void setKeepAliveTime(long arg0, java.util.concurrent.TimeUnit arg1);

    org.apache.hadoop.hbase.util.NonceKeyJVMInterface createNonceKey(long arg0, long arg1);

    org.apache.hadoop.hbase.util.IdLockJVMInterface getProcExecutionLock();

    java.util.List bypassProcedure(java.util.List<java.lang.Long> arg0, long arg1, boolean arg2, boolean arg3) throws java.io.IOException;

    TEnvironment getEnvironment();

    java.util.List getProcedures();

    int getWorkerThreadCount();

    boolean abort(long arg0);
}
