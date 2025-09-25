package org.apache.hadoop.hbase.client;

public interface BalanceRequestJVMInterface {

    boolean isDryRun();

    boolean isIgnoreRegionsInTransition();
}
