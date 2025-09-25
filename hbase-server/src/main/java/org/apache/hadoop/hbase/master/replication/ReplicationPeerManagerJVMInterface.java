package org.apache.hadoop.hbase.master.replication;

import org.apache.hadoop.hbase.conf.ConfigurationObserverJVMInterface;

public interface ReplicationPeerManagerJVMInterface extends ConfigurationObserverJVMInterface {

    java.util.Optional getPeerConfig(java.lang.String arg0);

    java.lang.Object getQueueStorage();

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);

    void enablePeer(java.lang.String arg0) throws org.apache.hadoop.hbase.replication.ReplicationException;

    java.util.List listPeers(java.util.regex.Pattern arg0);

    void disablePeer(java.lang.String arg0) throws org.apache.hadoop.hbase.replication.ReplicationException;

    void removePeer(java.lang.String arg0) throws org.apache.hadoop.hbase.replication.ReplicationException;

    boolean getPeerState(java.lang.String arg0) throws org.apache.hadoop.hbase.replication.ReplicationException;
}
