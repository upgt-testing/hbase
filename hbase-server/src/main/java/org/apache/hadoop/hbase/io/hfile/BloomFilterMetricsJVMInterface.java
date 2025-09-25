package org.apache.hadoop.hbase.io.hfile;

public interface BloomFilterMetricsJVMInterface {

    long getNegativeResultsCount();

    void incrementEligible();

    long getEligibleRequestsCount();

    long getRequestsCount();

    void incrementRequests(boolean arg0);
}
