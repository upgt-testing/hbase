package org.apache.hadoop.hbase.ipc;

public interface CallQueueInfoJVMInterface {

    long getCallMethodCount(java.lang.String arg0, java.lang.String arg1);

    java.util.Set getCallQueueNames();

    long getCallMethodSize(java.lang.String arg0, java.lang.String arg1);

    java.util.Set getCalledMethodNames(java.lang.String arg0);
}
