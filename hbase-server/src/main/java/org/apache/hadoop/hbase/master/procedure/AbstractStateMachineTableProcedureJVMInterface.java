package org.apache.hadoop.hbase.master.procedure;

import org.apache.hadoop.hbase.procedure2.StateMachineProcedureJVMInterface;

public interface AbstractStateMachineTableProcedureJVMInterface<TState> extends StateMachineProcedureJVMInterface<org.apache.hadoop.hbase.master.procedure.MasterProcedureEnv, TState>, TableProcedureInterfaceJVMInterface {

    org.apache.hadoop.hbase.TableNameJVMInterface getTableName();

    java.lang.Object getTableOperationType();

    void toStringClassDetails(java.lang.StringBuilder arg0);
}
