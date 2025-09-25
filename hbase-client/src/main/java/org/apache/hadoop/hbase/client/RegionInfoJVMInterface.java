package org.apache.hadoop.hbase.client;

public interface RegionInfoJVMInterface {

    byte[] getRegionName();

    int getReplicaId();

    org.apache.hadoop.hbase.TableNameJVMInterface getTable();

    long getRegionId();

    boolean isMetaRegion();

    boolean isOffline();

    java.lang.String getRegionNameAsString();

    byte[] getStartKey();

    byte[] getEndKey();

    boolean isSplit();

    boolean isSplitParent();

    boolean containsRow(byte[] arg0);

    java.lang.String getEncodedName();

    boolean containsRange(byte[] arg0, byte[] arg1);

    byte[] getEncodedNameAsBytes();

    java.lang.String getShortNameToLog();
}
