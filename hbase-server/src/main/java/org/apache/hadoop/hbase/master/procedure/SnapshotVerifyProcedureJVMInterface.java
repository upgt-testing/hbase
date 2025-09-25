package org.apache.hadoop.hbase.master.procedure;

public interface SnapshotVerifyProcedureJVMInterface extends ServerRemoteProcedureJVMInterface, TableProcedureInterfaceJVMInterface {

    org.apache.hadoop.hbase.TableNameJVMInterface getTableName();

    java.lang.Object getTableOperationType();

    org.apache.hadoop.hbase.ServerNameJVMInterface getServerName();
}
