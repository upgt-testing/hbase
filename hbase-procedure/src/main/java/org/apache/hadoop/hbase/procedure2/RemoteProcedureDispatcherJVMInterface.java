package org.apache.hadoop.hbase.procedure2;

public interface RemoteProcedureDispatcherJVMInterface<TEnv, TRemote> {

    boolean hasNode(TRemote arg0);

    void join();

    void addNode(TRemote arg0);

    boolean stop();

    boolean start();

    boolean removeNode(TRemote arg0);
}
