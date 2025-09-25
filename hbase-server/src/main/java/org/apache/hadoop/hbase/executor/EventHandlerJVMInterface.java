package org.apache.hadoop.hbase.executor;

public interface EventHandlerJVMInterface {

    java.lang.String getInformativeName();

    org.apache.hadoop.hbase.executor.EventHandlerJVMInterface prepare() throws java.lang.Exception;

    java.lang.Object getEventType();

    void run();

    void process() throws java.io.IOException;

    java.lang.String toString();

    long getSeqid();

    int getPriority();
}
