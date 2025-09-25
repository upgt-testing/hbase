package org.apache.hadoop.hbase.client;

public interface CheckAndMutateJVMInterface extends RowJVMInterface {

    int hashCode();

    boolean hasFilter();

    boolean equals(java.lang.Object arg0);

    byte[] getFamily();

    org.apache.hadoop.hbase.filter.FilterJVMInterface getFilter();

    byte[] getRow();

    byte[] getValue();

    java.lang.Object getAction();

    java.lang.Object getCompareOp();

    org.apache.hadoop.hbase.io.TimeRangeJVMInterface getTimeRange();

    byte[] getQualifier();
}
