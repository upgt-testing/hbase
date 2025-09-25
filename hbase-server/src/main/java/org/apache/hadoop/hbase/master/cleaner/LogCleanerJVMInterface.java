package org.apache.hadoop.hbase.master.cleaner;

import org.apache.hadoop.hbase.conf.ConfigurationObserverJVMInterface;

public interface LogCleanerJVMInterface extends CleanerChoreJVMInterface<org.apache.hadoop.hbase.master.cleaner.BaseLogCleanerDelegate>, ConfigurationObserverJVMInterface {

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);

    void cleanup();

    void cancel(boolean arg0);
}
