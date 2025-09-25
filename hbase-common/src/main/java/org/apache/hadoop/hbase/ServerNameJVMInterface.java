package org.apache.hadoop.hbase;

public interface ServerNameJVMInterface {

    int hashCode();

    boolean equals(java.lang.Object arg0);

    byte[] getVersionedBytes();

    java.lang.String toString();

    long getStartCode();

    java.lang.String getHostAndPort();

    java.lang.String toShortString();

    java.lang.String getHostname();

    java.lang.String getHostnameLowerCase();

    org.apache.hadoop.hbase.net.AddressJVMInterface getAddress();

    int getPort();

    java.lang.String getServerName();

    long getStartcode();
}
