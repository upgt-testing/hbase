package org.apache.hadoop.hbase.replication;

public interface ReplicationPeerConfigJVMInterface {

    java.lang.String getClusterKey();

    org.apache.hadoop.hbase.replication.ReplicationPeerConfigJVMInterface setReplicationEndpointImpl(java.lang.String arg0);

    long getBandwidth();

    java.lang.String toString();

    java.util.Map getTableCFsMap();

    boolean isSerial();

    java.lang.String getReplicationEndpointImpl();

    java.util.Set getNamespaces();

    java.util.Map getConfiguration();

    org.apache.hadoop.hbase.replication.ReplicationPeerConfigJVMInterface setReplicateAllUserTables(boolean arg0);

    org.apache.hadoop.hbase.replication.ReplicationPeerConfigJVMInterface setNamespaces(java.util.Set<java.lang.String> arg0);

    java.util.Set getExcludeNamespaces();

    org.apache.hadoop.hbase.replication.ReplicationPeerConfigJVMInterface setClusterKey(java.lang.String arg0);

    org.apache.hadoop.hbase.replication.ReplicationPeerConfigJVMInterface setBandwidth(long arg0);

    boolean replicateAllUserTables();

    org.apache.hadoop.hbase.replication.ReplicationPeerConfigJVMInterface setExcludeNamespaces(java.util.Set<java.lang.String> arg0);

    java.util.Map getPeerData();

    java.util.Map getExcludeTableCFsMap();
}
