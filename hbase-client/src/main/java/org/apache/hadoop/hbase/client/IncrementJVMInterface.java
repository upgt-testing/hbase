package org.apache.hadoop.hbase.client;

public interface IncrementJVMInterface extends MutationJVMInterface {

    int hashCode();

    org.apache.hadoop.hbase.client.IncrementJVMInterface setPriority(int arg0);

    org.apache.hadoop.hbase.client.IncrementJVMInterface setClusterIds(java.util.List<java.util.UUID> arg0);

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    int numFamilies();

    org.apache.hadoop.hbase.client.IncrementJVMInterface setId(java.lang.String arg0);

    org.apache.hadoop.hbase.client.IncrementJVMInterface addColumn(byte[] arg0, byte[] arg1, long arg2);

    org.apache.hadoop.hbase.io.TimeRangeJVMInterface getTimeRange();

    org.apache.hadoop.hbase.client.IncrementJVMInterface setTTL(long arg0);

    org.apache.hadoop.hbase.client.IncrementJVMInterface setReturnResults(boolean arg0);

    java.util.Map getFamilyMapOfLongs();

    boolean hasFamilies();

    org.apache.hadoop.hbase.client.IncrementJVMInterface setTimeRange(long arg0, long arg1) throws java.io.IOException;

    org.apache.hadoop.hbase.client.IncrementJVMInterface setAttribute(java.lang.String arg0, byte[] arg1);

    org.apache.hadoop.hbase.client.IncrementJVMInterface setFamilyCellMap(java.util.NavigableMap<byte[], java.util.List<org.apache.hadoop.hbase.Cell>> arg0);

    org.apache.hadoop.hbase.client.IncrementJVMInterface setTimestamp(long arg0);

    boolean isReturnResults();
}
