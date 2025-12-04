package org.restarttest.adapter.hbase.health;

import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.ServerName;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;

/**
 * Health check that verifies all RegionServers are registered with the master.
 */
public class HBaseRegionServersRegisteredCheck implements HealthCheck<MiniHBaseCluster> {

    @Override
    public HealthCheckResult checkHealth(MiniHBaseCluster cluster) throws Exception {
        HealthCheckResult result = new HealthCheckResult(true, getName());

        // Get live RegionServers from master
        ClusterMetrics metrics = cluster.getMaster().getClusterMetrics();
        int liveServerCount = metrics.getLiveServerMetrics().size();
        int deadServerCount = metrics.getDeadServerNames().size();

        // Get expected count
        int expectedCount = cluster.getLiveRegionServerThreads().size();

        result.addMetric("expected_regionservers", expectedCount);
        result.addMetric("live_regionservers", liveServerCount);
        result.addMetric("dead_regionservers", deadServerCount);

        // Allow some tolerance during restart - live servers should eventually match
        if (liveServerCount < expectedCount) {
            result.addFailure(
                "Expected " + expectedCount + " RegionServers but only " +
                liveServerCount + " are live (waiting for " +
                (expectedCount - liveServerCount) + " to register)");
        }

        if (deadServerCount > 0) {
            // This is informational during restart, not necessarily a failure
            result.addMetric("dead_servers_info", deadServerCount + " dead servers detected");
        }

        return result;
    }

    @Override
    public String getName() {
        return "hbase-regionservers-registered";
    }
}
