package org.apache.hadoop.hbase;

public interface ServerJVMInterface extends AbortableJVMInterface, StoppableJVMInterface {

    org.apache.hadoop.conf.Configuration getConfiguration();

    org.apache.hadoop.hbase.zookeeper.ZKWatcherJVMInterface getZooKeeper();

    org.apache.hadoop.hbase.ChoreServiceJVMInterface getChoreService();

    java.lang.Object createConnection(org.apache.hadoop.conf.Configuration arg0) throws java.io.IOException;

    org.apache.hadoop.hbase.ServerNameJVMInterface getServerName();

    java.lang.Object getClusterConnection();

    java.lang.Object getConnection();

    java.lang.Object getCoordinatedStateManager();
}
