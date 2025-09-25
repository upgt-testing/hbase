package org.apache.hadoop.hbase.master.procedure;

public interface TruncateRegionProcedureJVMInterface extends AbstractStateMachineRegionProcedureJVMInterface<org.apache.hadoop.hbase.shaded.protobuf.generated.MasterProcedureProtos.TruncateRegionState> {

    java.lang.Object getTableOperationType();

    void toStringClassDetails(java.lang.StringBuilder arg0);
}
