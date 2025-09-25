package org.apache.hadoop.hbase.regionserver;

public interface ServerNonceManagerJVMInterface {

    void setConflictWaitIterationMs(int arg0);

    void endOperation(long arg0, long arg1, boolean arg2);

    void addMvccToOperationContext(long arg0, long arg1, long arg2);

    long getMvccFromOperationContext(long arg0, long arg1);

    void reportOperationFromWal(long arg0, long arg1, long arg2);
}
