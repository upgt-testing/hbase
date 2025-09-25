package org.apache.hadoop.hbase.namequeues.response;

public interface NamedQueueGetResponseJVMInterface {

    java.lang.Object getNamedQueueEvent();

    java.util.List getSlowLogPayloads();

    java.lang.String toString();

    java.util.List getBalancerDecisions();

    java.util.List getBalancerRejections();

    void setNamedQueueEvent(int arg0);
}
