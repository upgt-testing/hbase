package org.apache.hadoop.hbase.procedure2;

public interface ProcedureEventJVMInterface<T> {

    void suspend();

    java.lang.String toString();

    boolean isReady();

    org.apache.hadoop.hbase.procedure2.ProcedureDequeJVMInterface getSuspendedProcedures();
}
