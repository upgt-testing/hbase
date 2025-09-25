package org.apache.hadoop.hbase.monitoring;

public interface MonitoredTaskImplJVMInterface extends MonitoredTaskJVMInterface {

    long getWarnTime();

    java.lang.String getStatus();

    java.lang.Object getState();

    void pause(java.lang.String arg0);

    java.lang.String toString();

    void setWarnTime(long arg0);

    void cleanup();

    void abort(java.lang.String arg0);

    java.lang.String toJSON() throws java.io.IOException;

    long getStateTime();

    void markComplete(java.lang.String arg0);

    java.util.List getStatusJournal();

    org.apache.hadoop.hbase.monitoring.MonitoredTaskImplJVMInterface clone();

    java.lang.String getDescription();

    void resume(java.lang.String arg0);

    java.util.Map toMap();

    java.lang.String prettyPrintJournal();

    long getStartTime();

    void expireNow();

    void setStatus(java.lang.String arg0);

    long getStatusTime();

    long getCompletionTimestamp();

    void setDescription(java.lang.String arg0);
}
