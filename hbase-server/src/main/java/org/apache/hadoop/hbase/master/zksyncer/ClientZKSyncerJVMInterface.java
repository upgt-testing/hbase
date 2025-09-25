package org.apache.hadoop.hbase.master.zksyncer;

import org.apache.hadoop.hbase.zookeeper.ZKListenerJVMInterface;

public interface ClientZKSyncerJVMInterface extends ZKListenerJVMInterface {

    void nodeDataChanged(java.lang.String arg0);

    void nodeCreated(java.lang.String arg0);

    void nodeDeleted(java.lang.String arg0);

    void start() throws org.apache.zookeeper.KeeperException;
}
