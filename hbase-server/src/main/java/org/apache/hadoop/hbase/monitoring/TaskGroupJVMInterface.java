package org.apache.hadoop.hbase.monitoring;

public interface TaskGroupJVMInterface extends MonitoredTaskImplJVMInterface {

    void cleanup();

    void abort(java.lang.String arg0);

    java.lang.Object addTask(java.lang.String arg0, boolean arg1);

    void markComplete(java.lang.String arg0);

    java.util.Collection getTasks();

    java.lang.Object addTask(java.lang.String arg0);
}
