package org.apache.hadoop.hbase.mob;

public interface MobFileCacheJVMInterface {

    void evict();

    void shutdown();

    void evictFile(java.lang.String arg0);

    long getMissCount();

    long getEvictedFileCount();

    double getHitRatio();

    long getAccessCount();

    int getCacheSize();

    void printStatistics();
}
