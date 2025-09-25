package org.apache.hadoop.hbase.client;

import org.apache.hadoop.hbase.CellScannableJVMInterface;
import org.apache.hadoop.hbase.io.HeapSizeJVMInterface;

public interface MutationJVMInterface extends OperationWithAttributesJVMInterface, RowJVMInterface, CellScannableJVMInterface, HeapSizeJVMInterface {

    long getTimeStamp();

    java.util.Map getFingerprint();

    boolean has(byte[] arg0, byte[] arg1, long arg2);

    long getTTL();

    org.apache.hadoop.hbase.security.visibility.CellVisibilityJVMInterface getCellVisibility() throws org.apache.hadoop.hbase.exceptions.DeserializationException;

    boolean has(byte[] arg0, byte[] arg1);

    byte[] getACL();

    java.util.List get(byte[] arg0, byte[] arg1);

    long heapSize();

    java.lang.Object getDurability();

    org.apache.hadoop.hbase.client.MutationJVMInterface setFamilyCellMap(java.util.NavigableMap<byte[], java.util.List<org.apache.hadoop.hbase.Cell>> arg0);

    byte[] getRow();

    boolean has(byte[] arg0, byte[] arg1, byte[] arg2);

    java.lang.Object cellScanner();

    org.apache.hadoop.hbase.client.MutationJVMInterface setClusterIds(java.util.List<java.util.UUID> arg0);

    boolean isEmpty();

    java.util.NavigableMap getFamilyCellMap();

    int numFamilies();

    int size();

    java.util.List getClusterIds();

    org.apache.hadoop.hbase.client.MutationJVMInterface setTTL(long arg0);

    java.util.Map toMap(int arg0);

    org.apache.hadoop.hbase.client.MutationJVMInterface setTimestamp(long arg0);

    boolean has(byte[] arg0, byte[] arg1, long arg2, byte[] arg3);

    long getTimestamp();
}
