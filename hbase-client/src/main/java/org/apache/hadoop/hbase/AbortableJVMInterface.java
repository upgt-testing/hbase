package org.apache.hadoop.hbase;

public interface AbortableJVMInterface {

    void abort(java.lang.String arg0, java.lang.Throwable arg1);

    boolean isAborted();
}
