package org.apache.hadoop.hbase.master.assignment;

import org.apache.hadoop.hbase.master.procedure.AbstractStateMachineTableProcedureJVMInterface;

public interface MergeTableRegionsProcedureJVMInterface extends AbstractStateMachineTableProcedureJVMInterface<org.apache.hadoop.hbase.shaded.protobuf.generated.MasterProcedureProtos.MergeTableRegionsState> {

    org.apache.hadoop.hbase.TableNameJVMInterface getTableName();

    java.lang.Object getTableOperationType();

    void toStringClassDetails(java.lang.StringBuilder arg0);
}
