package org.apache.hadoop.hbase;

import org.apache.hadoop.hbase.client.RegionInfoJVMInterface;

public interface HRegionInfoJVMInterface extends RegionInfoJVMInterface {

    byte[] getRegionName();

    int getReplicaId();

    byte[] toDelimitedByteArray() throws java.io.IOException;

    boolean isOffline();

    java.lang.String getRegionNameAsString();

    boolean isSplitParent();

    boolean containsRow(byte[] arg0);

    java.lang.String getEncodedName();

    java.lang.String getShortNameToLog();

    int hashCode();

    org.apache.hadoop.hbase.TableNameJVMInterface getTable();

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    long getRegionId();

    boolean isMetaRegion();

    java.lang.Object getComparator();

    byte[] getStartKey();

    void setSplit(boolean arg0);

    boolean isMetaTable();

    boolean isSystemTable();

    byte[] getEndKey();

    boolean isSplit();

    byte[] toByteArray();

    void setOffline(boolean arg0);

    boolean containsRange(byte[] arg0, byte[] arg1);

    byte[] getEncodedNameAsBytes();
}
