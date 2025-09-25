package org.apache.hadoop.hbase.regionserver;

public interface ScannerContextJVMInterface {

    boolean getSkippingRow();

    java.lang.String toString();

    boolean isTrackingMetrics();

    org.apache.hadoop.hbase.client.metrics.ServerSideScanMetricsJVMInterface getMetrics();
}
