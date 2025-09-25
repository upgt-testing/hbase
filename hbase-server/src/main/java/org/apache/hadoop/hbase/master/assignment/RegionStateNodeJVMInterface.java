package org.apache.hadoop.hbase.master.assignment;

public interface RegionStateNodeJVMInterface {

    org.apache.hadoop.hbase.procedure2.ProcedureEventJVMInterface getProcedureEvent();

    java.lang.String toDescriptiveString();

    long getLastUpdate();

    java.lang.Object getRegionInfo();

    org.apache.hadoop.hbase.ServerNameJVMInterface getLastHost();

    boolean tryLock();

    org.apache.hadoop.hbase.master.RegionStateJVMInterface toRegionState();

    void lock();

    int getFormatVersion();

    org.apache.hadoop.hbase.ServerNameJVMInterface getRegionLocation();

    org.apache.hadoop.hbase.master.assignment.TransitRegionStateProcedureJVMInterface getProcedure();

    int hashCode();

    java.lang.Object getState();

    boolean equals(java.lang.Object arg0);

    org.apache.hadoop.hbase.TableNameJVMInterface getTable();

    long getOpenSeqNum();

    java.lang.String toString();

    void checkOnline() throws org.apache.hadoop.hbase.client.DoNotRetryRegionException;

    java.lang.String toShortString();

    void unlock();

    org.apache.hadoop.hbase.ServerNameJVMInterface offline();

    boolean isInTransition();

    boolean isSystemTable();

    boolean isStuck();

    boolean isSplit();

    void setOpenSeqNum(long arg0);
}
