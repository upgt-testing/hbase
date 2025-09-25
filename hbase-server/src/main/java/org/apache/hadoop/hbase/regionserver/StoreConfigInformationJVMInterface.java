package org.apache.hadoop.hbase.regionserver;

public interface StoreConfigInformationJVMInterface {

    java.lang.String getColumnFamilyName();

    java.lang.Object getRegionInfo();

    long getStoreFileTtl();

    long getMemStoreFlushSize();

    long getCompactionCheckMultiplier();

    long getBlockingFileCount();
}
