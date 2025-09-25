package org.apache.hadoop.hbase;

public interface StoppableJVMInterface {

    boolean isStopped();

    void stop(java.lang.String arg0);
}
