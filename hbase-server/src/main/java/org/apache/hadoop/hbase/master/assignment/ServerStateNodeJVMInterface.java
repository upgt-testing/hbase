package org.apache.hadoop.hbase.master.assignment;

public interface ServerStateNodeJVMInterface {

    int hashCode();

    java.lang.Object getState();

    int getRegionCount();

    java.util.List getRegionInfoList();

    boolean equals(java.lang.Object arg0);

    java.util.concurrent.locks.Lock readLock();

    java.lang.String toString();

    java.util.List getSystemRegionInfoList();

    org.apache.hadoop.hbase.ServerNameJVMInterface getServerName();

    java.util.concurrent.locks.Lock writeLock();
}
