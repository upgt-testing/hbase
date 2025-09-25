package org.apache.hadoop.hbase.regionserver.compactions;

public interface CompactionConfigurationJVMInterface {

    java.lang.String getWarmWindowStoragePolicy();

    float getMajorCompactionJitter();

    boolean useDateTieredSingleOutputForMinorCompaction();

    int getMaxFilesToCompact();

    java.lang.String getCompactionPolicyForDateTieredWindow();

    java.lang.String getColdWindowStoragePolicy();

    long getOffPeakMaxCompactSize();

    long getMajorCompactionPeriod();

    long getMaxCompactSize(boolean arg0);

    long getHotWindowAgeMillis();

    boolean isDateTieredStoragePolicyEnable();

    double getCompactionRatioOffPeak();

    java.lang.String toString();

    java.lang.String getDateTieredCompactionWindowFactory();

    float getMinLocalityToForceCompact();

    java.lang.String getHotWindowStoragePolicy();

    long getThrottlePoint();

    long getDateTieredMaxStoreFileAgeMillis();

    int getDateTieredIncomingWindowMin();

    int getMinFilesToCompact();

    double getCompactionRatio();

    long getMinCompactSize();

    long getMaxCompactSize();

    void setMinFilesToCompact(int arg0);

    long getWarmWindowAgeMillis();
}
