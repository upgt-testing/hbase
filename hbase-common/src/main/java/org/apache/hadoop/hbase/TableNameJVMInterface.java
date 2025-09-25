package org.apache.hadoop.hbase;

public interface TableNameJVMInterface {

    int hashCode();

    java.lang.String getNamespaceAsString();

    java.lang.String getNameAsString();

    boolean isSystemTable();

    java.lang.String getQualifierAsString();

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    byte[] getNamespace();

    byte[] getName();

    byte[] toBytes();

    java.lang.String getNameWithNamespaceInclAsString();

    byte[] getQualifier();
}
