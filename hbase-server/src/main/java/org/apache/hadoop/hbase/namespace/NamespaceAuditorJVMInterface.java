package org.apache.hadoop.hbase.namespace;

public interface NamespaceAuditorJVMInterface {

    org.apache.hadoop.hbase.namespace.NamespaceTableAndRegionInfoJVMInterface getState(java.lang.String arg0);

    void deleteNamespace(java.lang.String arg0) throws java.io.IOException;

    void start() throws java.io.IOException;

    boolean isInitialized();
}
