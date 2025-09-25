package org.apache.hadoop.hbase.zookeeper;

import org.apache.hadoop.hbase.AbortableJVMInterface;

public interface ZKWatcherJVMInterface extends AbortableJVMInterface {

    java.util.List createACL(java.lang.String arg0, boolean arg1);

    org.apache.hadoop.hbase.zookeeper.ZNodePathsJVMInterface getZNodePaths();

    java.lang.String toString();

    java.util.List createACL(java.lang.String arg0);

    java.util.List getListeners();

    java.util.List getMetaReplicaNodes() throws org.apache.zookeeper.KeeperException;

    void reconnectAfterExpiration() throws java.io.IOException, org.apache.zookeeper.KeeperException, java.lang.InterruptedException;

    void process(org.apache.zookeeper.WatchedEvent arg0);

    java.lang.String getQuorum();

    java.lang.String prefix(java.lang.String arg0);

    void abort(java.lang.String arg0, java.lang.Throwable arg1);

    void syncOrTimeout(java.lang.String arg0) throws org.apache.zookeeper.KeeperException;

    boolean isAborted();

    void keeperException(org.apache.zookeeper.KeeperException arg0) throws org.apache.zookeeper.KeeperException;

    org.apache.hadoop.conf.Configuration getConfiguration();

    void unregisterAllListeners();

    java.util.List getMetaReplicaNodesAndWatchChildren() throws org.apache.zookeeper.KeeperException;

    void checkAndSetZNodeAcls();

    org.apache.hadoop.hbase.zookeeper.RecoverableZooKeeperJVMInterface getRecoverableZooKeeper();

    int getNumberOfListeners();

    void interruptedException(java.lang.InterruptedException arg0) throws org.apache.zookeeper.KeeperException;

    void interruptedExceptionNoThrow(java.lang.InterruptedException arg0, boolean arg1);

    void close();
}
