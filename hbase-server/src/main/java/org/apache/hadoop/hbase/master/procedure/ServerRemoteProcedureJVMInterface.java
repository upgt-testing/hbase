package org.apache.hadoop.hbase.master.procedure;

import org.apache.hadoop.hbase.procedure2.ProcedureJVMInterface;
import org.apache.hadoop.hbase.procedure2.RemoteProcedureJVMInterface;

public interface ServerRemoteProcedureJVMInterface extends ProcedureJVMInterface<org.apache.hadoop.hbase.master.procedure.MasterProcedureEnv>, RemoteProcedureJVMInterface<org.apache.hadoop.hbase.master.procedure.MasterProcedureEnv, org.apache.hadoop.hbase.ServerName> {
}
