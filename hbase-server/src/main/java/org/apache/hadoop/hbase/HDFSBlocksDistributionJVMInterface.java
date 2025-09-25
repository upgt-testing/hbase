package org.apache.hadoop.hbase;

public interface HDFSBlocksDistributionJVMInterface {

    long getBlocksLocalWeight(java.lang.String arg0);

    float getBlockLocalityIndexForSsd(java.lang.String arg0);

    java.lang.Object[] getTopHostsWithWeights();

    long getWeight(java.lang.String arg0);

    void addHostsAndBlockWeight(java.lang.String[] arg0, long arg1);

    java.lang.String toString();

    long getUniqueBlocksTotalWeight();

    long getBlocksLocalWithSsdWeight(java.lang.String arg0);

    java.util.Map getHostAndWeights();

    float getBlockLocalityIndex(java.lang.String arg0);

    java.util.List getTopHosts();
}
