package org.apache.hadoop.hbase.client;

public interface CheckAndMutateResultJVMInterface {

    boolean isSuccess();

    org.apache.hadoop.hbase.client.ResultJVMInterface getResult();
}
