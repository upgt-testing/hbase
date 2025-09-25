package org.apache.hadoop.hbase.client;

public interface ScanJVMInterface extends QueryJVMInterface {

    org.apache.hadoop.hbase.client.ScanJVMInterface setStartRow(byte[] arg0);

    int getLimit();

    org.apache.hadoop.hbase.client.ScanJVMInterface setMaxVersions();

    org.apache.hadoop.hbase.client.ScanJVMInterface setId(java.lang.String arg0);

    org.apache.hadoop.hbase.client.ScanJVMInterface setLoadColumnFamiliesOnDemand(boolean arg0);

    org.apache.hadoop.hbase.io.TimeRangeJVMInterface getTimeRange();

    org.apache.hadoop.hbase.client.ScanJVMInterface withStartRow(byte[] arg0, boolean arg1);

    org.apache.hadoop.hbase.client.ScanJVMInterface setMaxResultSize(long arg0);

    org.apache.hadoop.hbase.client.ScanJVMInterface setCacheBlocks(boolean arg0);

    byte[][] getFamilies();

    org.apache.hadoop.hbase.client.ScanJVMInterface setAsyncPrefetch(boolean arg0);

    org.apache.hadoop.hbase.client.metrics.ScanMetricsJVMInterface getScanMetrics();

    org.apache.hadoop.hbase.client.ScanJVMInterface setMaxVersions(int arg0);

    org.apache.hadoop.hbase.client.ScanJVMInterface setAllowPartialResults(boolean arg0);

    long getMaxResultSize();

    int getMaxResultsPerColumnFamily();

    org.apache.hadoop.hbase.client.ScanJVMInterface setPriority(int arg0);

    boolean isGetScan();

    org.apache.hadoop.hbase.client.ScanJVMInterface setOneRowLimit();

    org.apache.hadoop.hbase.client.ScanJVMInterface setReversed(boolean arg0);

    org.apache.hadoop.hbase.client.ScanJVMInterface withStopRow(byte[] arg0);

    org.apache.hadoop.hbase.client.ScanJVMInterface setRowOffsetPerColumnFamily(int arg0);

    org.apache.hadoop.hbase.client.ScanJVMInterface setReplicaId(int arg0);

    boolean isReversed();

    org.apache.hadoop.hbase.client.ScanJVMInterface setStartStopRowForPrefixScan(byte[] arg0);

    org.apache.hadoop.hbase.client.ScanJVMInterface setStopRow(byte[] arg0);

    boolean hasFamilies();

    org.apache.hadoop.hbase.client.ScanJVMInterface setAttribute(java.lang.String arg0, byte[] arg1);

    java.util.Map getFamilyMap();

    org.apache.hadoop.hbase.client.ScanJVMInterface setRowPrefixFilter(byte[] arg0);

    java.util.Map toMap(int arg0);

    org.apache.hadoop.hbase.client.ScanJVMInterface setScanMetricsEnabled(boolean arg0);

    boolean includeStopRow();

    org.apache.hadoop.hbase.client.ScanJVMInterface setBatch(int arg0);

    org.apache.hadoop.hbase.client.ScanJVMInterface readVersions(int arg0);

    java.util.Map getFingerprint();

    int getMaxVersions();

    org.apache.hadoop.hbase.client.ScanJVMInterface setRaw(boolean arg0);

    org.apache.hadoop.hbase.filter.FilterJVMInterface getFilter();

    boolean isRaw();

    boolean includeStartRow();

    org.apache.hadoop.hbase.client.ScanJVMInterface setCaching(int arg0);

    byte[] getStopRow();

    org.apache.hadoop.hbase.client.ScanJVMInterface addFamily(byte[] arg0);

    org.apache.hadoop.hbase.client.ScanJVMInterface withStartRow(byte[] arg0);

    boolean getAllowPartialResults();

    org.apache.hadoop.hbase.client.ScanJVMInterface setTimeRange(long arg0, long arg1) throws java.io.IOException;

    org.apache.hadoop.hbase.client.ScanJVMInterface setNeedCursorResult(boolean arg0);

    org.apache.hadoop.hbase.client.ScanJVMInterface setMaxResultsPerColumnFamily(int arg0);

    boolean getCacheBlocks();

    boolean isNeedCursorResult();

    java.lang.Boolean isAsyncPrefetch();

    int getCaching();

    int getRowOffsetPerColumnFamily();

    int getBatch();

    org.apache.hadoop.hbase.client.ScanJVMInterface withStopRow(byte[] arg0, boolean arg1);

    org.apache.hadoop.hbase.client.ScanJVMInterface setLimit(int arg0);

    boolean hasFilter();

    int numFamilies();

    org.apache.hadoop.hbase.client.ScanJVMInterface addColumn(byte[] arg0, byte[] arg1);

    java.lang.Object getReadType();

    org.apache.hadoop.hbase.client.ScanJVMInterface setColumnFamilyTimeRange(byte[] arg0, long arg1, long arg2);

    org.apache.hadoop.hbase.client.ScanJVMInterface setTimeStamp(long arg0) throws java.io.IOException;

    org.apache.hadoop.hbase.client.ScanJVMInterface setSmall(boolean arg0);

    org.apache.hadoop.hbase.client.ScanJVMInterface setFamilyMap(java.util.Map<byte[], java.util.NavigableSet<byte[]>> arg0);

    byte[] getStartRow();

    boolean isScanMetricsEnabled();

    org.apache.hadoop.hbase.client.ScanJVMInterface setTimestamp(long arg0);

    boolean isSmall();

    org.apache.hadoop.hbase.client.ScanJVMInterface readAllVersions();
}
