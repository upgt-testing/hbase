package org.apache.hadoop.hbase.net;

public interface AddressJVMInterface {

    int hashCode();

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    int getPort();

    java.lang.String toStringWithoutDomain();

    java.lang.String getHostName();

    java.lang.String getHostname();
}
