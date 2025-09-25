package org.apache.hadoop.hbase.client;

public interface BalanceResponseJVMInterface {

    boolean isBalancerRan();

    int getMovesExecuted();

    int getMovesCalculated();
}
