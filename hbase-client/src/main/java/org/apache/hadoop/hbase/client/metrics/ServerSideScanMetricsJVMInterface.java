package org.apache.hadoop.hbase.client.metrics;

public interface ServerSideScanMetricsJVMInterface {

    java.util.Map getMetricsMap(boolean arg0);

    java.util.concurrent.atomic.AtomicLong getCounter(java.lang.String arg0);

    boolean hasCounter(java.lang.String arg0);

    void addToCounter(java.lang.String arg0, long arg1);

    java.util.Map getMetricsMap();

    void setCounter(java.lang.String arg0, long arg1);
}
