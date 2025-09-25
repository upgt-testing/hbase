package org.apache.hadoop.hbase.quotas;

import org.apache.hadoop.hbase.ScheduledChoreJVMInterface;

public interface QuotaObserverChoreJVMInterface extends ScheduledChoreJVMInterface {

    java.util.Map getNamespaceQuotaSnapshots();

    java.util.Map getTableQuotaSnapshots();
}
