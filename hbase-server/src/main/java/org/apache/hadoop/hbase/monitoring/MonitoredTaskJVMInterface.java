package org.apache.hadoop.hbase.monitoring;

public interface MonitoredTaskJVMInterface {

    long getWarnTime();

    java.lang.String getStatus();

    java.lang.Object getState();

    void pause(java.lang.String arg0);

    void setWarnTime(long arg0);

    void cleanup();

    void abort(java.lang.String arg0);

    java.lang.String toJSON() throws java.io.IOException;

    long getStateTime();

    void markComplete(java.lang.String arg0);

    java.lang.Object clone();

    java.util.List getStatusJournal();

    void resume(java.lang.String arg0);

    java.lang.String getDescription();

    java.util.Map toMap() throws java.io.IOException;

    java.lang.String prettyPrintJournal();

    long getStartTime();

    void expireNow();

    void setStatus(java.lang.String arg0);

    long getStatusTime();

    void setDescription(java.lang.String arg0);

    long getCompletionTimestamp();
}
