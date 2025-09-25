package org.apache.hadoop.hbase.procedure2;

public interface StateMachineProcedureJVMInterface<TEnvironment, TState> extends ProcedureJVMInterface<TEnvironment> {

    int getCurrentStateId();
}
