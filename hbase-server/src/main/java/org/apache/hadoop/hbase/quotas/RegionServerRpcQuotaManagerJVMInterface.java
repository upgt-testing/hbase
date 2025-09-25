package org.apache.hadoop.hbase.quotas;

public interface RegionServerRpcQuotaManagerJVMInterface extends RpcQuotaManagerJVMInterface {

    void switchRpcThrottle(boolean arg0) throws java.io.IOException;

    void stop();
}
