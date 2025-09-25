package org.apache.hadoop.hbase.security.access;

public interface UserPermissionJVMInterface {

    int hashCode();

    org.apache.hadoop.hbase.security.access.PermissionJVMInterface getPermission();

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    java.lang.String getUser();

    boolean equalsExceptActions(java.lang.Object arg0);

    java.lang.Object getAccessScope();
}
