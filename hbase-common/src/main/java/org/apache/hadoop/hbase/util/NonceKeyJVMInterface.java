package org.apache.hadoop.hbase.util;

public interface NonceKeyJVMInterface {

    int hashCode();

    long getNonce();

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    long getNonceGroup();
}
