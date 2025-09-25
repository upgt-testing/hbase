package org.apache.hadoop.hbase.security.access;

import org.apache.hadoop.hbase.zookeeper.ZKListenerJVMInterface;

public interface ZKPermissionWatcherJVMInterface extends ZKListenerJVMInterface {

    void nodeChildrenChanged(java.lang.String arg0);

    void nodeDataChanged(java.lang.String arg0);

    void nodeCreated(java.lang.String arg0);

    void nodeDeleted(java.lang.String arg0);

    void start() throws org.apache.zookeeper.KeeperException;

    void deleteNamespaceACLNode(java.lang.String arg0);

    void writeToZookeeper(byte[] arg0, byte[] arg1);

    void close();
}
