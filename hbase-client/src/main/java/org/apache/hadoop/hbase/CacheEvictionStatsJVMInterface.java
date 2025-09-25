package org.apache.hadoop.hbase;

public interface CacheEvictionStatsJVMInterface {

    long getMaxCacheSize();

    long getEvictedBlocks();

    int getExceptionCount();

    java.lang.String toString();

    java.util.Map getExceptions();
}
