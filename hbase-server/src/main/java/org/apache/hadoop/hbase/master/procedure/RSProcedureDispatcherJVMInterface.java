package org.apache.hadoop.hbase.master.procedure;

import org.apache.hadoop.hbase.procedure2.RemoteProcedureDispatcherJVMInterface;
import org.apache.hadoop.hbase.master.ServerListenerJVMInterface;

public interface RSProcedureDispatcherJVMInterface extends RemoteProcedureDispatcherJVMInterface<org.apache.hadoop.hbase.master.procedure.MasterProcedureEnv, org.apache.hadoop.hbase.ServerName>, ServerListenerJVMInterface {

    boolean stop();

    boolean start();
}
