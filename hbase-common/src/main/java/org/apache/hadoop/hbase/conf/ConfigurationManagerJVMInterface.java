package org.apache.hadoop.hbase.conf;

public interface ConfigurationManagerJVMInterface {

    void notifyAllObservers(org.apache.hadoop.conf.Configuration arg0);

    int getNumObservers();
}
