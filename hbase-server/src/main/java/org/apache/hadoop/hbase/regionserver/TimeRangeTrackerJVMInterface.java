package org.apache.hadoop.hbase.regionserver;

public interface TimeRangeTrackerJVMInterface {

    long getMin();

    java.lang.String toString();

    long getMax();
}
