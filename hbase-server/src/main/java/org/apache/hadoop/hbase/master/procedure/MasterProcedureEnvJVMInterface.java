package org.apache.hadoop.hbase.master.procedure;

import org.apache.hadoop.hbase.conf.ConfigurationObserverJVMInterface;

public interface MasterProcedureEnvJVMInterface extends ConfigurationObserverJVMInterface {

    org.apache.hadoop.hbase.master.MasterCoprocessorHostJVMInterface getMasterCoprocessorHost();

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);

    boolean isRunning();

    org.apache.hadoop.hbase.master.replication.ReplicationPeerManagerJVMInterface getReplicationPeerManager();

    org.apache.hadoop.hbase.master.assignment.AssignmentManagerJVMInterface getAssignmentManager();

    org.apache.hadoop.hbase.master.procedure.RSProcedureDispatcherJVMInterface getRemoteDispatcher();

    java.lang.Object getMasterServices();

    org.apache.hadoop.conf.Configuration getMasterConfiguration();

    org.apache.hadoop.hbase.security.UserJVMInterface getRequestUser();

    org.apache.hadoop.hbase.master.MasterFileSystemJVMInterface getMasterFileSystem();

    boolean isInitialized();

    org.apache.hadoop.hbase.master.procedure.MasterProcedureSchedulerJVMInterface getProcedureScheduler();
}
