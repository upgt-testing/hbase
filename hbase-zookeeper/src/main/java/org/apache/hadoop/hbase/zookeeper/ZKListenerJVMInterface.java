package org.apache.hadoop.hbase.zookeeper;

public interface ZKListenerJVMInterface {

    void nodeChildrenChanged(java.lang.String arg0);

    org.apache.hadoop.hbase.zookeeper.ZKWatcherJVMInterface getWatcher();

    void nodeDataChanged(java.lang.String arg0);

    void nodeCreated(java.lang.String arg0);

    void nodeDeleted(java.lang.String arg0);
}
