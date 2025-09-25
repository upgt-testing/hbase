package org.apache.hadoop.hbase.metrics;

public interface MetricRegistryInfoJVMInterface {

    java.lang.String getMetricsJmxContext();

    int hashCode();

    java.lang.String getMetricsName();

    boolean equals(java.lang.Object arg0);

    java.lang.String getMetricsContext();

    boolean isExistingSource();

    java.lang.String getMetricsDescription();
}
