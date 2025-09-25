package org.apache.hadoop.hbase.regionserver;

public interface OperationStatusJVMInterface {

    java.lang.Object getOperationStatusCode();

    org.apache.hadoop.hbase.client.ResultJVMInterface getResult();

    java.lang.String getExceptionMsg();
}
