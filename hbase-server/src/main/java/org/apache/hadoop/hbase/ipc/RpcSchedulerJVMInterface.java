package org.apache.hadoop.hbase.ipc;

public interface RpcSchedulerJVMInterface {

    int getMetaPriorityQueueLength();

    int getGeneralQueueLength();

    org.apache.hadoop.hbase.ipc.CallQueueInfoJVMInterface getCallQueueInfo();

    int getActiveGeneralRpcHandlerCount();

    int getWriteQueueLength();

    int getActiveReadRpcHandlerCount();

    int getReplicationQueueLength();

    int getActiveMetaPriorityRpcHandlerCount();

    void stop();

    long getNumGeneralCallsDropped();

    int getActivePriorityRpcHandlerCount();

    void start();

    int getActiveRpcHandlerCount();

    int getActiveScanRpcHandlerCount();

    int getScanQueueLength();

    long getNumLifoModeSwitches();

    int getReadQueueLength();

    int getBulkLoadQueueLength();

    int getActiveWriteRpcHandlerCount();

    int getActiveBulkLoadRpcHandlerCount();

    int getActiveReplicationRpcHandlerCount();

    int getPriorityQueueLength();
}
