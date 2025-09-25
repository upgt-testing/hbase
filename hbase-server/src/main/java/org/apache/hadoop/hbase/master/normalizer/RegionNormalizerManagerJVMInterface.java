package org.apache.hadoop.hbase.master.normalizer;

import org.apache.hadoop.hbase.conf.PropagatingConfigurationObserverJVMInterface;

public interface RegionNormalizerManagerJVMInterface extends PropagatingConfigurationObserverJVMInterface {

    void setNormalizerOn(boolean arg0) throws java.io.IOException;

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);

    long getSplitPlanCount();

    void stop();

    long getMergePlanCount();

    boolean isNormalizerOn();

    void start();

    org.apache.hadoop.hbase.ScheduledChoreJVMInterface getRegionNormalizerChore();
}
