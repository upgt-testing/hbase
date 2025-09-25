package org.apache.hadoop.hbase.filter;

public interface ByteArrayComparableJVMInterface {

    int compareTo(byte[] arg0, int arg1, int arg2);

    int compareTo(java.nio.ByteBuffer arg0, int arg1, int arg2);

    byte[] toByteArray();

    int compareTo(byte[] arg0);

    byte[] getValue();
}
