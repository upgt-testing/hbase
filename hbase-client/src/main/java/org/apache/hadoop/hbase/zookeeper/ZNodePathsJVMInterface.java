package org.apache.hadoop.hbase.zookeeper;

public interface ZNodePathsJVMInterface {

    java.lang.String getZNodeForReplica(int arg0);

    int getMetaReplicaIdFromPath(java.lang.String arg0);

    int getMetaReplicaIdFromZNode(java.lang.String arg0);

    java.lang.String toString();

    boolean isClientReadable(java.lang.String arg0);

    boolean isMetaZNodePrefix(java.lang.String arg0);

    boolean isMetaZNodePath(java.lang.String arg0);
}
