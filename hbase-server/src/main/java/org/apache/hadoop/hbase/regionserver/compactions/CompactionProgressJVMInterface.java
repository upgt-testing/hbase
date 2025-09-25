package org.apache.hadoop.hbase.regionserver.compactions;

public interface CompactionProgressJVMInterface {

    long getTotalCompactingKVs();

    float getProgressPct();

    void cancel();

    java.lang.String toString();

    void complete();

    long getTotalCompactedSize();

    long getCurrentCompactedKvs();
}
