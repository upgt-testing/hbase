package org.apache.hadoop.hbase.master.cleaner;

import org.apache.hadoop.hbase.conf.ConfigurationObserverJVMInterface;

public interface HFileCleanerJVMInterface extends CleanerChoreJVMInterface<org.apache.hadoop.hbase.master.cleaner.BaseHFileCleanerDelegate>, ConfigurationObserverJVMInterface {

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);

    java.util.List getDelegatesForTesting();

    long getNumOfDeletedLargeFiles();

    long getThrottlePoint();

    void cleanup();

    void cancel(boolean arg0);

    long getSmallQueueInitSize();

    int deleteFiles(java.lang.Iterable<org.apache.hadoop.fs.FileStatus> arg0);

    long getNumOfDeletedSmallFiles();

    java.util.List getCleanerThreads();

    long getLargeQueueInitSize();
}
