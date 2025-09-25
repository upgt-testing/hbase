package org.apache.hadoop.hbase.master.assignment;

import org.apache.hadoop.hbase.master.procedure.AbstractStateMachineRegionProcedureJVMInterface;

public interface SplitTableRegionProcedureJVMInterface extends AbstractStateMachineRegionProcedureJVMInterface<org.apache.hadoop.hbase.shaded.protobuf.generated.MasterProcedureProtos.SplitTableRegionState> {

    java.lang.Object getDaughterOneRI();

    java.lang.Object getTableOperationType();

    void toStringClassDetails(java.lang.StringBuilder arg0);

    java.lang.Object getDaughterTwoRI();
}
