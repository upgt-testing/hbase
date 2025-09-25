package org.apache.hadoop.hbase.conf;

public interface ConfigurationObserverJVMInterface {

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);
}
