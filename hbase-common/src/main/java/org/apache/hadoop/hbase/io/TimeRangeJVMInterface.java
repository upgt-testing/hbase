package org.apache.hadoop.hbase.io;

public interface TimeRangeJVMInterface {

    boolean withinTimeRange(byte[] arg0, int arg1);

    int compare(long arg0);

    boolean withinTimeRange(long arg0);

    long getMin();

    java.lang.String toString();

    long getMax();

    boolean withinOrAfterTimeRange(long arg0);

    boolean isAllTime();
}
