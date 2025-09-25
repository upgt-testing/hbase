package org.apache.hadoop.hbase.regionserver.compactions;

public interface CompactionPolicyJVMInterface {

    void setConf(org.apache.hadoop.conf.Configuration arg0);

    org.apache.hadoop.hbase.regionserver.compactions.CompactionConfigurationJVMInterface getConf();

    boolean throttleCompaction(long arg0);
}
