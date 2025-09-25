package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.conf.ConfigurationObserverJVMInterface;

public interface MemStoreFlusherJVMInterface extends FlushRequesterJVMInterface, ConfigurationObserverJVMInterface {

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);

    java.lang.String toString();

    int getFlusherCount();

    java.util.concurrent.atomic.LongAdder getUpdatesBlockedMsHighWater();

    int getFlushQueueSize();

    void setGlobalMemStoreLimit(long arg0);

    java.lang.String dumpQueue();

    void reclaimMemStoreMemory();
}
