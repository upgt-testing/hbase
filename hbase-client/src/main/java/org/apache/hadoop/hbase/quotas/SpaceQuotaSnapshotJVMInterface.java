package org.apache.hadoop.hbase.quotas;

public interface SpaceQuotaSnapshotJVMInterface extends SpaceQuotaSnapshotViewJVMInterface {

    long getLimit();

    int hashCode();

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    long getUsage();

    java.lang.Object getQuotaStatus();
}
