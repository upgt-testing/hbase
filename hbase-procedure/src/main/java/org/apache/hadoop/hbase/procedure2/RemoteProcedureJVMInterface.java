package org.apache.hadoop.hbase.procedure2;

public interface RemoteProcedureJVMInterface<TEnv, TRemote> {

    void remoteCallFailed(TEnv arg0, TRemote arg1, java.io.IOException arg2);

    void remoteOperationCompleted(TEnv arg0);

    java.util.Optional remoteCallBuild(TEnv arg0, TRemote arg1);
}
