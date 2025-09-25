package org.apache.hadoop.hbase.procedure2;

public interface ProcedureJVMInterface<TEnvironment> {

    void setOwner(java.lang.String arg0);

    long getProcId();

    boolean isSuccess();

    long getLastUpdate();

    boolean hasParent();

    boolean isInitializing();

    java.lang.String getProcName();

    boolean wasExecuted();

    boolean hasException();

    long getParentProcId();

    org.apache.hadoop.hbase.util.NonceKeyJVMInterface getNonceKey();

    long getSubmittedTime();

    int getTimeout();

    java.lang.Object getState();

    org.apache.hadoop.hbase.procedure2.RemoteProcedureExceptionJVMInterface getException();

    java.lang.String getOwner();

    java.lang.String toString();

    long getRootProcId();

    boolean isLockedWhenLoading();

    boolean hasLock();

    java.lang.String toStringDetails();

    long elapsedTime();

    boolean hasOwner();

    boolean hasTimeout();

    boolean isBypass();

    boolean isFailed();

    byte[] getResult();

    boolean isFinished();

    boolean isRunnable();

    boolean isWaiting();
}
