package org.apache.hadoop.hbase.master.locking;

import org.apache.hadoop.hbase.procedure2.ProcedureJVMInterface;
import org.apache.hadoop.hbase.master.procedure.TableProcedureInterfaceJVMInterface;

public interface LockProcedureJVMInterface extends ProcedureJVMInterface<org.apache.hadoop.hbase.master.procedure.MasterProcedureEnv>, TableProcedureInterfaceJVMInterface {

    void updateHeartBeat();

    java.lang.String getDescription();

    org.apache.hadoop.hbase.TableNameJVMInterface getTableName();

    java.lang.Object getTableOperationType();

    java.lang.Object getType();

    boolean isLocked();
}
