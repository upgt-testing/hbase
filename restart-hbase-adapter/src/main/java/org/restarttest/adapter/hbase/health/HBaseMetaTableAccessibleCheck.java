package org.restarttest.adapter.hbase.health;

import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;

/**
 * Health check that verifies the meta table is accessible.
 */
public class HBaseMetaTableAccessibleCheck implements HealthCheck<MiniHBaseCluster> {

    @Override
    public HealthCheckResult checkHealth(MiniHBaseCluster cluster) throws Exception {
        HealthCheckResult result = new HealthCheckResult(true, getName());

        try (Connection connection = ConnectionFactory.createConnection(cluster.getConfiguration());
             Admin admin = connection.getAdmin()) {

            boolean metaExists = admin.tableExists(TableName.META_TABLE_NAME);
            result.addMetric("meta_exists", metaExists);

            if (!metaExists) {
                result.addFailure("Meta table does not exist");
                return result;
            }

            boolean metaEnabled = admin.isTableEnabled(TableName.META_TABLE_NAME);
            result.addMetric("meta_enabled", metaEnabled);

            if (!metaEnabled) {
                result.addFailure("Meta table is not enabled");
            }

            // Try to get meta table regions
            int metaRegionCount = admin.getRegions(TableName.META_TABLE_NAME).size();
            result.addMetric("meta_region_count", metaRegionCount);

            if (metaRegionCount == 0) {
                result.addFailure("Meta table has no regions");
            }

        } catch (Exception e) {
            result.addFailure("Failed to access meta table: " + e.getMessage());
        }

        return result;
    }

    @Override
    public String getName() {
        return "hbase-meta-accessible";
    }
}
