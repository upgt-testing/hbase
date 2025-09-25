package org.apache.hadoop.hbase.client;

public interface DeleteJVMInterface extends MutationJVMInterface {

    org.apache.hadoop.hbase.client.DeleteJVMInterface setPriority(int arg0);

    org.apache.hadoop.hbase.client.DeleteJVMInterface setClusterIds(java.util.List<java.util.UUID> arg0);

    org.apache.hadoop.hbase.client.DeleteJVMInterface addFamilyVersion(byte[] arg0, long arg1);

    org.apache.hadoop.hbase.client.DeleteJVMInterface setId(java.lang.String arg0);

    org.apache.hadoop.hbase.client.DeleteJVMInterface addColumns(byte[] arg0, byte[] arg1);

    org.apache.hadoop.hbase.client.DeleteJVMInterface addColumn(byte[] arg0, byte[] arg1, long arg2);

    org.apache.hadoop.hbase.client.DeleteJVMInterface addColumn(byte[] arg0, byte[] arg1);

    org.apache.hadoop.hbase.client.DeleteJVMInterface addFamily(byte[] arg0);

    org.apache.hadoop.hbase.client.DeleteJVMInterface setTTL(long arg0);

    org.apache.hadoop.hbase.client.DeleteJVMInterface addColumns(byte[] arg0, byte[] arg1, long arg2);

    org.apache.hadoop.hbase.client.DeleteJVMInterface setAttribute(java.lang.String arg0, byte[] arg1);

    org.apache.hadoop.hbase.client.DeleteJVMInterface setFamilyCellMap(java.util.NavigableMap<byte[], java.util.List<org.apache.hadoop.hbase.Cell>> arg0);

    org.apache.hadoop.hbase.client.DeleteJVMInterface setTimestamp(long arg0);

    org.apache.hadoop.hbase.client.DeleteJVMInterface addFamily(byte[] arg0, long arg1);
}
