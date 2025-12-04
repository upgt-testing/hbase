package org.restarttest.adapter.hbase.health;

import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.master.HMaster;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;

/**
 * Health check that verifies an active master exists and is initialized.
 */
public class HBaseMasterActiveCheck implements HealthCheck<MiniHBaseCluster> {

    @Override
    public HealthCheckResult checkHealth(MiniHBaseCluster cluster) throws Exception {
        HealthCheckResult result = new HealthCheckResult(true, getName());

        HMaster master = cluster.getMaster();
        if (master == null) {
            result.addFailure("No active master found");
            return result;
        }

        boolean isActive = master.isActiveMaster();
        boolean isInitialized = master.isInitialized();

        result.addMetric("is_active", isActive);
        result.addMetric("is_initialized", isInitialized);

        if (!isActive) {
            result.addFailure("Master is not active");
        }

        if (!isInitialized) {
            result.addFailure("Master is not initialized");
        }

        return result;
    }

    @Override
    public String getName() {
        return "hbase-master-active";
    }
}
