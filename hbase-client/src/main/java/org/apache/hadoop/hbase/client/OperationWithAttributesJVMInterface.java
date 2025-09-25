package org.apache.hadoop.hbase.client;

public interface OperationWithAttributesJVMInterface extends OperationJVMInterface, AttributesJVMInterface {

    org.apache.hadoop.hbase.client.OperationWithAttributesJVMInterface setPriority(int arg0);

    byte[] getAttribute(java.lang.String arg0);

    java.util.Map getAttributesMap();

    org.apache.hadoop.hbase.client.OperationWithAttributesJVMInterface setId(java.lang.String arg0);

    org.apache.hadoop.hbase.client.OperationWithAttributesJVMInterface setAttribute(java.lang.String arg0, byte[] arg1);

    java.lang.String getId();

    int getPriority();
}
