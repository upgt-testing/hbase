package org.apache.hadoop.hbase;

public interface HRegionLocationJVMInterface {

    int hashCode();

    java.lang.Object getRegion();

    java.lang.String getHostnamePort();

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    long getSeqNum();

    org.apache.hadoop.hbase.HRegionInfoJVMInterface getRegionInfo();

    int getPort();

    org.apache.hadoop.hbase.ServerNameJVMInterface getServerName();

    java.lang.String getHostname();
}
