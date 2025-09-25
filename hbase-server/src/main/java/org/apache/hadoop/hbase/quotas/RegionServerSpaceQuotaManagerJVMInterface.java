package org.apache.hadoop.hbase.quotas;

public interface RegionServerSpaceQuotaManagerJVMInterface {

    java.util.Map copyQuotaSnapshots();

    java.util.Map getActivePoliciesAsMap();

    void stop();

    void start() throws java.io.IOException;

    java.lang.Object getRegionSizeStore();

    org.apache.hadoop.hbase.quotas.ActivePolicyEnforcementJVMInterface getActiveEnforcements();

    boolean isStarted();
}
