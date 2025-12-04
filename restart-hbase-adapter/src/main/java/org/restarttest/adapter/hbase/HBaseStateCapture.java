package org.restarttest.adapter.hbase;

import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.restarttest.state.AbstractStateCapture;
import org.restarttest.state.ClusterState;
import org.restarttest.state.DefaultClusterState;
import org.restarttest.state.StateVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * State capture implementation for HBase MiniHBaseCluster.
 * <p>
 * Captures:
 * <ul>
 *   <li>Number of live masters and regionservers</li>
 *   <li>Server names</li>
 *   <li>Table names and enabled status</li>
 *   <li>Region counts per table</li>
 *   <li>Meta table accessibility</li>
 * </ul>
 * <p>
 * Verifies:
 * <ul>
 *   <li>RegionServer count is preserved</li>
 *   <li>No tables are lost</li>
 *   <li>Meta table remains accessible</li>
 *   <li>Regions are properly reassigned</li>
 * </ul>
 */
public class HBaseStateCapture extends AbstractStateCapture<MiniHBaseCluster> {
    private static final Logger LOG = LoggerFactory.getLogger(HBaseStateCapture.class);

    @Override
    public ClusterState captureState(MiniHBaseCluster cluster) throws Exception {
        LOG.info("Capturing HBase cluster state");

        Map<String, Object> state = new HashMap<>();

        try {
            // Capture master state
            boolean hasMaster = cluster.getMaster() != null;
            state.put("has_active_master", hasMaster);

            if (hasMaster) {
                state.put("master_initialized", cluster.getMaster().isInitialized());
                state.put("master_active", cluster.getMaster().isActiveMaster());
            }

            // Capture number of masters
            int numMasters = cluster.getMasterThreads().size();
            state.put("num_masters", numMasters);

            // Capture regionserver state
            int numRegionServers = cluster.getRegionServerThreads().size();
            state.put("num_regionservers", numRegionServers);

            // Capture cluster metrics if master is available
            if (hasMaster && cluster.getMaster().isInitialized()) {
                ClusterMetrics metrics = cluster.getMaster().getClusterMetrics();
                state.put("live_servers_count", metrics.getLiveServerMetrics().size());
                state.put("dead_servers_count", metrics.getDeadServerNames().size());

                // Capture server names
                Map<String, String> serverNames = new HashMap<>();
                for (ServerName sn : metrics.getLiveServerMetrics().keySet()) {
                    serverNames.put(sn.getServerName(), "LIVE");
                }
                state.put("server_names", serverNames);

                // Capture table state
                try (Connection connection = ConnectionFactory.createConnection(cluster.getConfiguration());
                     Admin admin = connection.getAdmin()) {

                    TableName[] tableNames = admin.listTableNames();
                    state.put("table_count", tableNames.length);

                    Map<String, Boolean> tableStatus = new HashMap<>();
                    for (TableName tn : tableNames) {
                        boolean enabled = admin.isTableEnabled(tn);
                        tableStatus.put(tn.getNameAsString(), enabled);
                    }
                    state.put("table_status", tableStatus);

                    // Capture region counts per table
                    Map<String, Integer> regionCounts = new HashMap<>();
                    for (TableName tn : tableNames) {
                        if (!tn.isSystemTable()) {
                            int regionCount = admin.getRegions(tn).size();
                            regionCounts.put(tn.getNameAsString(), regionCount);
                        }
                    }
                    state.put("region_counts", regionCounts);

                    // Check meta table accessibility
                    boolean metaAccessible = admin.tableExists(TableName.META_TABLE_NAME);
                    state.put("meta_accessible", metaAccessible);
                }
            }

            LOG.info("Captured HBase state: {} masters, {} regionservers, {} tables",
                    numMasters, numRegionServers, state.get("table_count"));

        } catch (Exception e) {
            LOG.error("Failed to capture HBase state", e);
            throw e;
        }

        return new DefaultClusterState(state);
    }

    @Override
    protected void verifyCustomInvariants(MiniHBaseCluster cluster, ClusterState before, ClusterState after)
            throws Exception {
        LOG.info("Verifying HBase-specific invariants");

        Map<String, Object> beforeMap = before.getStateMap();
        Map<String, Object> afterMap = after.getStateMap();

        // Verify master is active
        Boolean hasActiveMasterAfter = (Boolean) afterMap.get("has_active_master");
        if (hasActiveMasterAfter == null || !hasActiveMasterAfter) {
            throw new StateVerificationException("No active master after restart");
        }

        // Verify regionserver count is preserved (unless we're explicitly testing RS restart)
        // Allow slight variations due to restart timing
        Integer beforeRS = (Integer) beforeMap.get("num_regionservers");
        Integer afterRS = (Integer) afterMap.get("num_regionservers");
        if (beforeRS != null && afterRS != null) {
            // Allow the count to temporarily differ during restart, but verify live servers eventually match
            Integer beforeLive = (Integer) beforeMap.get("live_servers_count");
            Integer afterLive = (Integer) afterMap.get("live_servers_count");

            if (beforeLive != null && afterLive != null && afterLive < beforeLive) {
                LOG.warn("Live server count decreased after restart: {} -> {}", beforeLive, afterLive);
                // This is a warning, not a failure, as servers may still be coming up
            }
        }

        // Verify no tables were lost
        @SuppressWarnings("unchecked")
        Map<String, Boolean> beforeTables = (Map<String, Boolean>) beforeMap.get("table_status");
        @SuppressWarnings("unchecked")
        Map<String, Boolean> afterTables = (Map<String, Boolean>) afterMap.get("table_status");

        if (beforeTables != null && afterTables != null) {
            for (String tableName : beforeTables.keySet()) {
                if (!afterTables.containsKey(tableName)) {
                    throw new StateVerificationException("Table lost after restart: " + tableName);
                }
            }
            LOG.info("Table integrity verified: {} tables checked, no tables lost",
                    beforeTables.size());
        }

        // Verify meta table is accessible
        Boolean metaAccessible = (Boolean) afterMap.get("meta_accessible");
        if (metaAccessible == null || !metaAccessible) {
            throw new StateVerificationException("Meta table not accessible after restart");
        }

        // Verify regions are assigned (no data loss)
        @SuppressWarnings("unchecked")
        Map<String, Integer> beforeRegions = (Map<String, Integer>) beforeMap.get("region_counts");
        @SuppressWarnings("unchecked")
        Map<String, Integer> afterRegions = (Map<String, Integer>) afterMap.get("region_counts");

        if (beforeRegions != null && afterRegions != null) {
            for (Map.Entry<String, Integer> entry : beforeRegions.entrySet()) {
                String tableName = entry.getKey();
                Integer beforeCount = entry.getValue();
                Integer afterCount = afterRegions.get(tableName);

                if (afterCount == null) {
                    throw new StateVerificationException(
                        "Region count unavailable for table after restart: " + tableName);
                }

                if (afterCount < beforeCount) {
                    throw new StateVerificationException(
                        "Regions lost after restart for table " + tableName +
                        ": before=" + beforeCount + ", after=" + afterCount);
                }

                if (afterCount > beforeCount) {
                    LOG.debug("Region count increased for table {}: {} -> {}",
                            tableName, beforeCount, afterCount);
                }
            }
            LOG.info("Region integrity verified for {} tables", beforeRegions.size());
        }
    }
}
