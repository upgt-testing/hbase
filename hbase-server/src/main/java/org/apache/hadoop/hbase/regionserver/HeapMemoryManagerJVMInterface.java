package org.apache.hadoop.hbase.regionserver;

public interface HeapMemoryManagerJVMInterface {

    float getHeapOccupancyPercent();

    void stop();
}
