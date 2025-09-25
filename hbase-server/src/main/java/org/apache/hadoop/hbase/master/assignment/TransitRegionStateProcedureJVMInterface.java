package org.apache.hadoop.hbase.master.assignment;

import org.apache.hadoop.hbase.master.procedure.AbstractStateMachineRegionProcedureJVMInterface;

public interface TransitRegionStateProcedureJVMInterface extends AbstractStateMachineRegionProcedureJVMInterface<org.apache.hadoop.hbase.shaded.protobuf.generated.MasterProcedureProtos.RegionStateTransitionState> {

    java.lang.Object getTableOperationType();

    void toStringClassDetails(java.lang.StringBuilder arg0);
}
