package org.apache.hadoop.hbase.master;

public interface RegionPlanJVMInterface {

    java.lang.String getRegionName();

    int hashCode();

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    java.lang.Object getRegionInfo();

    org.apache.hadoop.hbase.ServerNameJVMInterface getSource();

    org.apache.hadoop.hbase.ServerNameJVMInterface getDestination();
}
