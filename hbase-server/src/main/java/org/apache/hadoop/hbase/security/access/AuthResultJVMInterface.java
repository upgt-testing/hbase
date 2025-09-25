package org.apache.hadoop.hbase.security.access;

public interface AuthResultJVMInterface {

    java.lang.String getRequest();

    void setAllowed(boolean arg0);

    org.apache.hadoop.hbase.TableNameJVMInterface getTableName();

    java.lang.String toString();

    org.apache.hadoop.hbase.security.UserJVMInterface getUser();

    java.lang.String toFamilyString();

    java.lang.Object getParams();

    byte[] getFamily();

    boolean isAllowed();

    byte[] getQualifier();

    java.lang.String toContextString();

    java.lang.String getReason();

    void setReason(java.lang.String arg0);

    java.lang.Object getAction();
}
