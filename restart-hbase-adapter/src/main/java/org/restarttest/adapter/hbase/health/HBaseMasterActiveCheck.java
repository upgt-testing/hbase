package org.restarttest.adapter.hbase.health;

import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;

/**
 * Health check that verifies an active master exists and is initialized.
 * Currently a no-op implementation.
 */
public class HBaseMasterActiveCheck implements HealthCheck<MiniHBaseCluster> {

    @Override
    public HealthCheckResult checkHealth(MiniHBaseCluster cluster) throws Exception {
        // No-op: just return success
        return new HealthCheckResult(true, getName());
    }

    @Override
    public String getName() {
        return "hbase-master-active";
    }
}
