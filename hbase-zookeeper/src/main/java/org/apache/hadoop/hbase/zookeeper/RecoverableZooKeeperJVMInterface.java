package org.apache.hadoop.hbase.zookeeper;

public interface RecoverableZooKeeperJVMInterface {

    byte[] getSessionPasswd();

    java.lang.String getIdentifier();

    java.util.List getChildren(java.lang.String arg0, boolean arg1) throws org.apache.zookeeper.KeeperException, java.lang.InterruptedException;

    java.lang.Object getState();

    java.util.List multi(java.lang.Iterable<org.apache.zookeeper.Op> arg0) throws org.apache.zookeeper.KeeperException, java.lang.InterruptedException;

    int getMaxMultiSizeLimit();

    void reconnectAfterExpiration() throws java.io.IOException, org.apache.zookeeper.KeeperException, java.lang.InterruptedException;

    org.apache.zookeeper.data.Stat setData(java.lang.String arg0, byte[] arg1, int arg2) throws org.apache.zookeeper.KeeperException, java.lang.InterruptedException;

    long getSessionId();

    byte[] getData(java.lang.String arg0, boolean arg1, org.apache.zookeeper.data.Stat arg2) throws org.apache.zookeeper.KeeperException, java.lang.InterruptedException;

    org.apache.zookeeper.data.Stat setAcl(java.lang.String arg0, java.util.List<org.apache.zookeeper.data.ACL> arg1, int arg2) throws org.apache.zookeeper.KeeperException, java.lang.InterruptedException;

    org.apache.zookeeper.ZooKeeper getZooKeeper();

    org.apache.zookeeper.data.Stat exists(java.lang.String arg0, boolean arg1) throws org.apache.zookeeper.KeeperException, java.lang.InterruptedException;

    void delete(java.lang.String arg0, int arg1) throws java.lang.InterruptedException, org.apache.zookeeper.KeeperException;

    java.util.List getAcl(java.lang.String arg0, org.apache.zookeeper.data.Stat arg1) throws org.apache.zookeeper.KeeperException, java.lang.InterruptedException;

    void close() throws java.lang.InterruptedException;
}
