package org.apache.hadoop.hbase.security.access;

public interface PermissionJVMInterface {

    int hashCode();

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    java.lang.Object[] getActions();

    byte getVersion();

    boolean equalsExceptActions(java.lang.Object arg0);

    java.lang.Object getAccessScope();
}
