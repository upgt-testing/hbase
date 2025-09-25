package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.regionserver.compactions.CompactionRequesterJVMInterface;
import org.apache.hadoop.hbase.conf.PropagatingConfigurationObserverJVMInterface;

public interface CompactSplitJVMInterface extends CompactionRequesterJVMInterface, PropagatingConfigurationObserverJVMInterface {

    int getLargeCompactionQueueSize();

    int getCompactionQueueSize();

    void clearLongCompactionsQueue();

    java.lang.String toString();

    boolean isCompactionsEnabled();

    int getSmallCompactionQueueSize();

    void switchCompaction(boolean arg0);

    java.lang.Object getCompactionThroughputController();

    java.lang.String dumpQueue();

    void clearShortCompactionsQueue();

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);

    int getSplitQueueSize();

    void setCompactionsEnabled(boolean arg0);

    int getRegionSplitLimit();
}
