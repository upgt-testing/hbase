package org.apache.hadoop.hbase.client;

public interface SnapshotDescriptionJVMInterface {

    org.apache.hadoop.hbase.TableNameJVMInterface getTableName();

    java.lang.String getTable();

    java.lang.String getTableNameAsString();

    java.lang.String getOwner();

    java.lang.String toString();

    java.lang.String getName();

    int getVersion();

    long getTtl();

    java.lang.Object getType();

    long getCreationTime();

    long getMaxFileSize();
}
