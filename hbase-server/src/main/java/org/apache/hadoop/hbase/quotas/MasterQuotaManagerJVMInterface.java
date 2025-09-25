package org.apache.hadoop.hbase.quotas;

import org.apache.hadoop.hbase.RegionStateListenerJVMInterface;

public interface MasterQuotaManagerJVMInterface extends RegionStateListenerJVMInterface {

    boolean isExceedThrottleQuotaEnabled() throws java.io.IOException;

    boolean isQuotaInitialized();

    org.apache.hadoop.hbase.namespace.NamespaceAuditorJVMInterface getNamespaceQuotaManager();

    boolean isRpcThrottleEnabled() throws java.io.IOException;

    void removeNamespaceQuota(java.lang.String arg0) throws java.io.IOException;

    void stop();

    void start() throws java.io.IOException;

    java.util.Map snapshotRegionSizes();
}
