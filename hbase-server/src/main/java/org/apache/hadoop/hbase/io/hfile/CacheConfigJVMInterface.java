package org.apache.hadoop.hbase.io.hfile;

import org.apache.hadoop.hbase.conf.ConfigurationObserverJVMInterface;

public interface CacheConfigJVMInterface extends ConfigurationObserverJVMInterface {

    long getCacheCompactedBlocksOnWriteThreshold();

    boolean shouldCacheDataCompressed();

    java.util.Optional getBlockCache();

    boolean isCombinedBlockCache();

    java.lang.String toString();

    void enableCacheOnWrite();

    boolean shouldDropBehindCompaction();

    boolean isInMemory();

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);

    boolean shouldCacheCompactedBlocksOnWrite();

    boolean shouldCacheBloomsOnWrite();

    void setCacheDataOnWrite(boolean arg0);

    void setEvictOnClose(boolean arg0);

    boolean shouldCacheIndexesOnWrite();

    boolean shouldPrefetchOnOpen();

    org.apache.hadoop.hbase.io.ByteBuffAllocatorJVMInterface getByteBuffAllocator();

    boolean shouldCacheDataOnWrite();

    boolean shouldCacheDataOnRead();

    boolean shouldEvictOnClose();
}
