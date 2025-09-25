package org.apache.hadoop.hbase.security.access;

public interface TablePermissionJVMInterface extends PermissionJVMInterface {

    int hashCode();

    boolean hasFamily();

    org.apache.hadoop.hbase.TableNameJVMInterface getTableName();

    boolean equals(java.lang.Object arg0);

    java.lang.String getNamespace();

    java.lang.String toString();

    byte[] getFamily();

    boolean hasQualifier();

    boolean equalsExceptActions(java.lang.Object arg0);

    byte[] getQualifier();
}
