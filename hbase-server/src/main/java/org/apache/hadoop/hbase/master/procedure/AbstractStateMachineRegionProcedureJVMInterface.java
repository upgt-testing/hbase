package org.apache.hadoop.hbase.master.procedure;

public interface AbstractStateMachineRegionProcedureJVMInterface<TState> extends AbstractStateMachineTableProcedureJVMInterface<TState> {

    java.lang.Object getRegion();

    org.apache.hadoop.hbase.TableNameJVMInterface getTableName();

    java.lang.Object getTableOperationType();

    void toStringClassDetails(java.lang.StringBuilder arg0);
}
