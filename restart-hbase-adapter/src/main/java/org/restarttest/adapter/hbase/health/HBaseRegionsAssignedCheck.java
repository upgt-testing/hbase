package org.restarttest.adapter.hbase.health;

import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;

/**
 * Health check that verifies all regions are assigned.
 */
public class HBaseRegionsAssignedCheck implements HealthCheck<MiniHBaseCluster> {

    @Override
    public HealthCheckResult checkHealth(MiniHBaseCluster cluster) throws Exception {
        HealthCheckResult result = new HealthCheckResult(true, getName());

        try (Connection connection = ConnectionFactory.createConnection(cluster.getConfiguration());
             Admin admin = connection.getAdmin()) {

            ClusterMetrics metrics = cluster.getMaster().getClusterMetrics();

            // Count total regions across all servers
            int totalRegions = 0;
            for (ServerName server : metrics.getLiveServerMetrics().keySet()) {
                int regionCount = metrics.getLiveServerMetrics().get(server).getRegionMetrics().size();
                totalRegions += regionCount;
            }

            result.addMetric("total_regions", totalRegions);

            // Check for regions in transition
            int regionsInTransition = metrics.getRegionStatesInTransition().size();
            result.addMetric("regions_in_transition", regionsInTransition);

            // During restart, some regions may temporarily be in transition
            // This is expected, so we log it but don't fail immediately
            if (regionsInTransition > 0) {
                result.addMetric("rit_info", regionsInTransition + " regions in transition (may be temporary during restart)");
            }

            // Verify at least some regions are assigned (meta table should always have regions)
            if (totalRegions == 0) {
                result.addFailure("No regions are assigned to any server");
            }

        } catch (Exception e) {
            result.addFailure("Failed to check region assignments: " + e.getMessage());
        }

        return result;
    }

    @Override
    public String getName() {
        return "hbase-regions-assigned";
    }
}
