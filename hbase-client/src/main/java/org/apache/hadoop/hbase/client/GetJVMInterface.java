package org.apache.hadoop.hbase.client;

public interface GetJVMInterface extends QueryJVMInterface, RowJVMInterface {

    org.apache.hadoop.hbase.client.GetJVMInterface setMaxVersions();

    org.apache.hadoop.hbase.client.GetJVMInterface readVersions(int arg0) throws java.io.IOException;

    java.util.Map getFingerprint();

    int getMaxVersions();

    org.apache.hadoop.hbase.client.GetJVMInterface setId(java.lang.String arg0);

    org.apache.hadoop.hbase.client.GetJVMInterface setLoadColumnFamiliesOnDemand(boolean arg0);

    org.apache.hadoop.hbase.io.TimeRangeJVMInterface getTimeRange();

    org.apache.hadoop.hbase.client.GetJVMInterface setClosestRowBefore(boolean arg0);

    org.apache.hadoop.hbase.client.GetJVMInterface setCacheBlocks(boolean arg0);

    org.apache.hadoop.hbase.client.GetJVMInterface addFamily(byte[] arg0);

    org.apache.hadoop.hbase.client.GetJVMInterface setTimeRange(long arg0, long arg1) throws java.io.IOException;

    org.apache.hadoop.hbase.client.GetJVMInterface setMaxResultsPerColumnFamily(int arg0);

    boolean getCacheBlocks();

    org.apache.hadoop.hbase.client.GetJVMInterface setMaxVersions(int arg0) throws java.io.IOException;

    byte[] getRow();

    int getRowOffsetPerColumnFamily();

    int hashCode();

    org.apache.hadoop.hbase.client.GetJVMInterface setPriority(int arg0);

    int getMaxResultsPerColumnFamily();

    boolean isCheckExistenceOnly();

    boolean equals(java.lang.Object arg0);

    int numFamilies();

    org.apache.hadoop.hbase.client.GetJVMInterface setRowOffsetPerColumnFamily(int arg0);

    org.apache.hadoop.hbase.client.GetJVMInterface addColumn(byte[] arg0, byte[] arg1);

    boolean isClosestRowBefore();

    org.apache.hadoop.hbase.client.GetJVMInterface setColumnFamilyTimeRange(byte[] arg0, long arg1, long arg2);

    org.apache.hadoop.hbase.client.GetJVMInterface setTimeStamp(long arg0) throws java.io.IOException;

    org.apache.hadoop.hbase.client.GetJVMInterface setReplicaId(int arg0);

    boolean hasFamilies();

    org.apache.hadoop.hbase.client.GetJVMInterface setAttribute(java.lang.String arg0, byte[] arg1);

    java.util.Map getFamilyMap();

    java.util.Map toMap(int arg0);

    org.apache.hadoop.hbase.client.GetJVMInterface setTimestamp(long arg0);

    org.apache.hadoop.hbase.client.GetJVMInterface setCheckExistenceOnly(boolean arg0);

    java.util.Set familySet();

    org.apache.hadoop.hbase.client.GetJVMInterface readAllVersions();
}
