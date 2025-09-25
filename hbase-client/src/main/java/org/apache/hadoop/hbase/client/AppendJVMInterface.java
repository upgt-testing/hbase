package org.apache.hadoop.hbase.client;

public interface AppendJVMInterface extends MutationJVMInterface {

    org.apache.hadoop.hbase.client.AppendJVMInterface setPriority(int arg0);

    org.apache.hadoop.hbase.client.AppendJVMInterface add(byte[] arg0, byte[] arg1, byte[] arg2);

    org.apache.hadoop.hbase.client.AppendJVMInterface setClusterIds(java.util.List<java.util.UUID> arg0);

    org.apache.hadoop.hbase.client.AppendJVMInterface addColumn(byte[] arg0, byte[] arg1, byte[] arg2);

    org.apache.hadoop.hbase.client.AppendJVMInterface setId(java.lang.String arg0);

    org.apache.hadoop.hbase.io.TimeRangeJVMInterface getTimeRange();

    org.apache.hadoop.hbase.client.AppendJVMInterface setTTL(long arg0);

    org.apache.hadoop.hbase.client.AppendJVMInterface setReturnResults(boolean arg0);

    org.apache.hadoop.hbase.client.AppendJVMInterface setTimeRange(long arg0, long arg1);

    org.apache.hadoop.hbase.client.AppendJVMInterface setAttribute(java.lang.String arg0, byte[] arg1);

    org.apache.hadoop.hbase.client.AppendJVMInterface setFamilyCellMap(java.util.NavigableMap<byte[], java.util.List<org.apache.hadoop.hbase.Cell>> arg0);

    org.apache.hadoop.hbase.client.AppendJVMInterface setTimestamp(long arg0);

    boolean isReturnResults();
}
