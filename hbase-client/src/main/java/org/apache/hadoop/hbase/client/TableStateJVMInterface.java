package org.apache.hadoop.hbase.client;

public interface TableStateJVMInterface {

    int hashCode();

    boolean isEnabling();

    boolean isEnabled();

    boolean isDisabled();

    boolean isEnabledOrEnabling();

    java.lang.Object getState();

    org.apache.hadoop.hbase.TableNameJVMInterface getTableName();

    java.lang.Object convert();

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    boolean isDisabling();

    boolean isDisabledOrDisabling();
}
