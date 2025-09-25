package org.apache.hadoop.hbase.procedure;

public interface MasterProcedureManagerHostJVMInterface extends ProcedureManagerHostJVMInterface<org.apache.hadoop.hbase.procedure.MasterProcedureManager> {

    org.apache.hadoop.hbase.procedure.MasterProcedureManagerJVMInterface getProcedureManager(java.lang.String arg0);

    void loadProcedures(org.apache.hadoop.conf.Configuration arg0);

    void stop(java.lang.String arg0);
}
