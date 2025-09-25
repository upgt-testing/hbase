package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.conf.ConfigurationObserverJVMInterface;

public interface RegionJVMInterface extends ConfigurationObserverJVMInterface {

    long getBlockedRequestsCount();

    java.lang.Object getRowLock(byte[] arg0, boolean arg1) throws java.io.IOException;

    java.lang.Object getRegionInfo();

    java.util.List getStoreFileList(byte[][] arg0);

    java.lang.Object getTableDescriptor();

    long getCheckAndMutateChecksFailed();

    boolean isMergeable();

    long getMemStoreDataSize();

    long getEarliestFlushTimeForAllStores();

    boolean isSplittable();

    long getMaxFlushedSeqId();

    org.apache.hadoop.conf.Configuration getReadOnlyConfiguration();

    java.lang.Object getCellComparator();

    java.util.Map getMaxStoreSeqId();

    boolean isClosed();

    long getOldestHfileTs(boolean arg0) throws java.io.IOException;

    void closeRegionOperation() throws java.io.IOException;

    int getMinBlockSizeBytes();

    long getNumMutationsWithoutWAL();

    java.util.List getStores();

    long getMemStoreHeapSize();

    boolean refreshStoreFiles() throws java.io.IOException;

    boolean isClosing();

    long getFilteredReadRequestsCount();

    boolean isReadOnly();

    long getCheckAndMutateChecksPassed();

    java.lang.Object getCompactionState();

    long getWriteRequestsCount();

    boolean waitForFlushes(long arg0);

    long getReadRequestsCount();

    boolean isAvailable();

    java.lang.Object getStore(byte[] arg0);

    long getDataInMemoryWithoutWAL();

    void startRegionOperation() throws java.io.IOException;

    long getMemStoreOffHeapSize();
}
