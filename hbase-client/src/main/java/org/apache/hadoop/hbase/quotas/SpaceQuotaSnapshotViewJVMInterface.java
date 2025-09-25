package org.apache.hadoop.hbase.quotas;

public interface SpaceQuotaSnapshotViewJVMInterface {

    long getLimit();

    long getUsage();

    java.lang.Object getQuotaStatus();
}
