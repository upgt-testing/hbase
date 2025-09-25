package org.apache.hadoop.hbase.io;

public interface ByteBuffAllocatorJVMInterface {

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface allocateOneBuffer();

    long getHeapAllocationBytes();

    int getTotalBufferCount();

    int getUsedBufferCount();

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface allocate(int arg0);

    void clean();

    int getFreeBufferCount();

    long getPoolAllocationBytes();

    int getBufferSize();

    boolean isReservoirEnabled();
}
