package org.apache.hadoop.hbase.regionserver;

public interface MovedRegionInfoJVMInterface {

    long getSeqNum();

    org.apache.hadoop.hbase.ServerNameJVMInterface getServerName();
}
