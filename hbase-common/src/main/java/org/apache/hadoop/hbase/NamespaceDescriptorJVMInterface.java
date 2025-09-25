package org.apache.hadoop.hbase;

public interface NamespaceDescriptorJVMInterface {

    java.util.Map getConfiguration();

    java.lang.String toString();

    java.lang.String getConfigurationValue(java.lang.String arg0);

    java.lang.String getName();

    void removeConfiguration(java.lang.String arg0);

    void setConfiguration(java.lang.String arg0, java.lang.String arg1);
}
