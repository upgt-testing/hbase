package org.apache.hadoop.hbase.procedure2;

public interface LockedResourceJVMInterface {

    java.lang.Object getLockType();

    java.lang.String getResourceName();

    int getSharedLockCount();

    org.apache.hadoop.hbase.procedure2.ProcedureJVMInterface getExclusiveLockOwnerProcedure();

    java.lang.Object getResourceType();

    java.util.List getWaitingProcedures();
}
