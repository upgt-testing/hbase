package org.apache.hadoop.hbase.client;

import org.apache.hadoop.hbase.io.HeapSizeJVMInterface;

public interface PutJVMInterface extends MutationJVMInterface, HeapSizeJVMInterface {

    org.apache.hadoop.hbase.client.PutJVMInterface setPriority(int arg0);

    org.apache.hadoop.hbase.client.PutJVMInterface setClusterIds(java.util.List<java.util.UUID> arg0);

    org.apache.hadoop.hbase.client.PutJVMInterface addColumn(byte[] arg0, byte[] arg1, byte[] arg2);

    org.apache.hadoop.hbase.client.PutJVMInterface setId(java.lang.String arg0);

    org.apache.hadoop.hbase.client.PutJVMInterface addColumn(byte[] arg0, byte[] arg1, long arg2, byte[] arg3);

    org.apache.hadoop.hbase.client.PutJVMInterface setTTL(long arg0);

    org.apache.hadoop.hbase.client.PutJVMInterface addImmutable(byte[] arg0, byte[] arg1, long arg2, byte[] arg3);

    org.apache.hadoop.hbase.client.PutJVMInterface addColumn(byte[] arg0, java.nio.ByteBuffer arg1, long arg2, java.nio.ByteBuffer arg3);

    org.apache.hadoop.hbase.client.PutJVMInterface addImmutable(byte[] arg0, byte[] arg1, byte[] arg2);

    org.apache.hadoop.hbase.client.PutJVMInterface addImmutable(byte[] arg0, java.nio.ByteBuffer arg1, long arg2, java.nio.ByteBuffer arg3);

    org.apache.hadoop.hbase.client.PutJVMInterface setAttribute(java.lang.String arg0, byte[] arg1);

    org.apache.hadoop.hbase.client.PutJVMInterface setFamilyCellMap(java.util.NavigableMap<byte[], java.util.List<org.apache.hadoop.hbase.Cell>> arg0);

    org.apache.hadoop.hbase.client.PutJVMInterface setTimestamp(long arg0);
}
