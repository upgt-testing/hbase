package org.apache.hadoop.hbase.master;

import org.apache.hadoop.hbase.conf.ConfigurationObserverJVMInterface;

public interface ServerManagerJVMInterface extends ConfigurationObserverJVMInterface {

    boolean areDeadServersInProgress();

    org.apache.hadoop.hbase.master.DeadServerJVMInterface getDeadServers();

    java.lang.Object getLastFlushedSequenceId(byte[] arg0);

    java.util.List getDrainingServersList();

    boolean isClusterShutdown();

    void stop();

    boolean getRejectDecommissionedHostsConfig(org.apache.hadoop.conf.Configuration arg0);

    java.util.Map getOnlineServers();

    java.util.List createDestinationServersList();

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);

    java.util.List getOnlineServersList();

    int countOfRegionServers();

    double getAverageLoad();

    void shutdownCluster();
}
