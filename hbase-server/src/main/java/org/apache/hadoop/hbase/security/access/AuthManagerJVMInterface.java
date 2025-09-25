package org.apache.hadoop.hbase.security.access;

public interface AuthManagerJVMInterface {

    long getMTime();

    void removeNamespace(byte[] arg0);

    void refreshNamespaceCacheFromWritable(java.lang.String arg0, byte[] arg1) throws java.io.IOException;
}
