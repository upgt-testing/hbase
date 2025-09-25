package org.apache.hadoop.hbase.regionserver;

public interface MiniBatchOperationInProgressJVMInterface<T> {

    int getNumOfDeletes();

    void incrementNumOfIncrements();

    void addCellCount(int arg0);

    int getNumOfAppends();

    int getCellCount();

    int getReadyToWriteCount();

    int size();

    void incrementNumOfAppends();

    void incrementNumOfPuts();

    org.apache.hadoop.hbase.wal.WALEditJVMInterface getWalEdit(int arg0);

    org.apache.hadoop.hbase.client.MutationJVMInterface[] getOperationsFromCoprocessors(int arg0);

    void incrementNumOfDeletes();

    int getNumOfPuts();

    int getNumOfIncrements();

    T getOperation(int arg0);

    int getLastIndexExclusive();

    org.apache.hadoop.hbase.regionserver.OperationStatusJVMInterface getOperationStatus(int arg0);
}
