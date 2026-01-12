package org.restarttest.adapter.hbase;

import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.regionserver.HRegion;
import org.apache.hadoop.hbase.regionserver.HRegionServer;
import org.restarttest.adapter.hbase.health.HBaseMasterActiveCheck;
import org.restarttest.adapter.hbase.health.HBaseMetaTableAccessibleCheck;
import org.restarttest.adapter.hbase.health.HBaseRegionServersRegisteredCheck;
import org.restarttest.adapter.hbase.health.HBaseRegionsAssignedCheck;
import org.restarttest.core.ClusterAdapter;
import org.restarttest.core.RestartMode;
import org.restarttest.health.CompositeHealthCheck;
import org.restarttest.health.HealthCheck;
import org.restarttest.state.StateCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter for HBase MiniHBaseCluster.
 * <p>
 * This adapter provides restart capabilities for HBase mini-clusters by directly
 * calling MiniHBaseCluster's native restart methods. No wrapper objects are needed.
 * <p>
 * Supports restarting:
 * <ul>
 *   <li>Masters (role: "master" or "hmaster")</li>
 *   <li>RegionServers (role: "regionserver" or "worker")</li>
 *   <li>All nodes (role: "all" - restarts both Masters and RegionServers)</li>
 * </ul>
 * <p>
 * This adapter is automatically discovered via Java's ServiceLoader mechanism.
 * See META-INF/services/org.restarttest.core.ClusterAdapter for configuration.
 */
public class HBaseClusterAdapter implements ClusterAdapter<MiniHBaseCluster> {
    private static final Logger LOG = LoggerFactory.getLogger(HBaseClusterAdapter.class);

    private final HBaseStateCapture stateCapture;
    private final CompositeHealthCheck<MiniHBaseCluster> healthCheck;

    /**
     * Create an HBase cluster adapter.
     */
    public HBaseClusterAdapter() {
        this.stateCapture = new HBaseStateCapture();
        this.healthCheck = new CompositeHealthCheck<>("hbase-health");

        // Add default health checks
        // this.healthCheck.addCheck(new HBaseMasterActiveCheck());
        // this.healthCheck.addCheck(new HBaseRegionServersRegisteredCheck());
        // this.healthCheck.addCheck(new HBaseMetaTableAccessibleCheck());
        // this.healthCheck.addCheck(new HBaseRegionsAssignedCheck());
    }

    @Override
    public Class<MiniHBaseCluster> getClusterType() {
        return MiniHBaseCluster.class;
    }

    @Override
    public void restartNode(MiniHBaseCluster cluster, String nodeRole, int nodeIndex, RestartMode mode)
            throws Exception {
        String normalizedRole = normalizeRole(nodeRole);

        if ("master".equals(normalizedRole)) {
            restartMaster(cluster, nodeIndex, mode);
        } else if ("regionserver".equals(normalizedRole)) {
            restartRegionServer(cluster, nodeIndex, mode);
        } else if ("all".equals(normalizedRole)) {
            restartAllNodes(cluster, "all", mode);
        } else {
            throw new IllegalArgumentException(
                "Unknown node role: " + nodeRole + ". Supported roles: master, hmaster, regionserver, worker, all");
        }
    }

    @Override
    public void restartAllNodes(MiniHBaseCluster cluster, String nodeRole, RestartMode mode)
            throws Exception {
        String normalizedRole = normalizeRole(nodeRole);

        if ("master".equals(normalizedRole)) {
            int numMasters = cluster.getMasterThreads().size();
            LOG.info("Restarting all {} Masters with mode {}", numMasters, mode);
            for (int i = 0; i < numMasters; i++) {
                restartMaster(cluster, i, mode);
            }
        } else if ("regionserver".equals(normalizedRole)) {
            int numRegionServers = cluster.getRegionServerThreads().size();
            LOG.info("Restarting all {} RegionServers with mode {}", numRegionServers, mode);
            for (int i = 0; i < numRegionServers; i++) {
                restartRegionServer(cluster, i, mode);
            }
        } else if ("all".equals(normalizedRole)) {
            int numMasters = cluster.getMasterThreads().size();
            LOG.info("Restarting all {} Masters with mode {}", numMasters, mode);
            for (int i = 0; i < numMasters; i++) {
                restartMaster(cluster, i, mode);
            }
            int numRegionServers = cluster.getRegionServerThreads().size();
            LOG.info("Restarting all {} RegionServers with mode {}", numRegionServers, mode);
            for (int i = 0; i < numRegionServers; i++) {
                restartRegionServer(cluster, i, mode);
            }
        } else {
            throw new IllegalArgumentException(
                "Unknown node role: " + nodeRole + ". Supported roles: master, hmaster, regionserver, worker, all");
        }
    }

    @Override
    public void waitActive(MiniHBaseCluster cluster) throws Exception {
        LOG.info("Waiting for HBase cluster to become active");
        // Wait for active master to be ready
        if (!cluster.waitForActiveAndReadyMaster(60000)) {
            throw new Exception("HBase cluster failed to become active within timeout");
        }
        LOG.info("HBase cluster is active");
    }

    @Override
    public StateCapture<MiniHBaseCluster> getStateCapture() {
        return stateCapture;
    }

    @Override
    public HealthCheck<MiniHBaseCluster> getHealthCheck() {
        return healthCheck;
    }

    @Override
    public int getNodeCount(MiniHBaseCluster cluster, String nodeRole) throws Exception {
        String normalizedRole = normalizeRole(nodeRole);

        if ("master".equals(normalizedRole)) {
            return cluster.getMasterThreads().size();
        } else if ("regionserver".equals(normalizedRole)) {
            return cluster.getRegionServerThreads().size();
        } else if ("all".equals(normalizedRole)) {
            return cluster.getMasterThreads().size() + cluster.getRegionServerThreads().size();
        } else {
            throw new IllegalArgumentException(
                "Unknown node role: " + nodeRole + ". Supported roles: master, hmaster, regionserver, worker, all");
        }
    }

    /**
     * Normalize node role to standard names.
     */
    private String normalizeRole(String role) {
        String lower = role.toLowerCase();
        if ("hmaster".equals(lower)) {
            return "master";
        } else if ("worker".equals(lower)) {
            return "regionserver";
        }
        return lower;
    }

    /**
     * Restart a Master with the specified mode.
     */
    private void restartMaster(MiniHBaseCluster cluster, int masterIndex, RestartMode mode)
            throws Exception {
        LOG.info("Restarting Master {} with mode {}", masterIndex, mode);

        switch (mode) {
            case GRACEFUL:
                // Graceful restart: stop master cleanly, allowing proper state transitions
                // Masters handle procedures/metadata so no region flush is needed
                LOG.info("Initiating graceful shutdown of Master {}", masterIndex);
                cluster.stopMaster(masterIndex);
                cluster.waitOnMaster(masterIndex);

                // Small delay to allow ZK state and cluster state to propagate
                Thread.sleep(100);

                cluster.startMaster();
                break;

            case CRASH:
                // Abort master (simulated crash), then start new one
                cluster.abortMaster(masterIndex);
                cluster.waitOnMaster(masterIndex);
                cluster.startMaster();
                break;

            case DELAYED_CRASH:
                // Abort master, wait, then start new one
                cluster.abortMaster(masterIndex);
                cluster.waitOnMaster(masterIndex);
                Thread.sleep(500); // Allow some state propagation
                cluster.startMaster();
                break;

            default:
                throw new IllegalArgumentException("Unknown restart mode: " + mode);
        }

        // Wait for new master to become active and ready
        if (!cluster.waitForActiveAndReadyMaster(60000)) {
            throw new Exception("Master failed to become active after restart");
        }

        LOG.info("Master {} restarted successfully", masterIndex);
    }

    /**
     * Restart a RegionServer with the specified mode.
     */
    private void restartRegionServer(MiniHBaseCluster cluster, int rsIndex, RestartMode mode)
            throws Exception {
        LOG.info("Restarting RegionServer {} with mode {}", rsIndex, mode);

        switch (mode) {
            case GRACEFUL:
                // Graceful restart: flush all regions before stopping to ensure data durability
                // This ensures all memstore data is persisted to HFiles before shutdown
                flushRegionServerRegions(cluster, rsIndex);

                // Stop regionserver gracefully, then start new one
                cluster.stopRegionServer(rsIndex);
                cluster.waitOnRegionServer(rsIndex);

                // Small delay to allow region reassignment state to propagate
                Thread.sleep(100);

                cluster.startRegionServerAndWait(60000);

                // Wait for regions to be reassigned and for the RPC client's failed servers list
                // to expire (default 2 seconds). This prevents FailedServerException when tests
                // immediately call Admin operations after restart.
                // See TEST-BUG-GROUP-4.md for detailed analysis.
                Thread.sleep(6000);
                break;

            case CRASH:
                // Abort regionserver (simulated crash), then start new one
                cluster.abortRegionServer(rsIndex);
                cluster.waitOnRegionServer(rsIndex);
                cluster.startRegionServerAndWait(60000);

                // Wait for regions to be reassigned and for the RPC client's failed servers list
                // to expire (default 2 seconds). This prevents FailedServerException when tests
                // immediately call Admin operations after restart.
                Thread.sleep(6000);
                break;

            case DELAYED_CRASH:
                // Abort regionserver, wait, then start new one
                cluster.abortRegionServer(rsIndex);
                cluster.waitOnRegionServer(rsIndex);
                Thread.sleep(500); // Allow some state propagation
                cluster.startRegionServerAndWait(60000);

                // Wait for regions to be reassigned and for the RPC client's failed servers list
                // to expire (default 2 seconds). This prevents FailedServerException when tests
                // immediately call Admin operations after restart.
                Thread.sleep(6000);
                break;

            default:
                throw new IllegalArgumentException("Unknown restart mode: " + mode);
        }

        LOG.info("RegionServer {} restarted successfully", rsIndex);
    }

    /**
     * Flush all regions on a specific RegionServer before graceful shutdown.
     * <p>
     * This ensures all memstore data is persisted to HFiles before the server stops,
     * providing maximum data durability and faster restart (no WAL replay needed for
     * flushed data).
     *
     * @param cluster the MiniHBaseCluster
     * @param rsIndex the index of the RegionServer to flush
     */
    private void flushRegionServerRegions(MiniHBaseCluster cluster, int rsIndex) {
        try {
            HRegionServer server = cluster.getRegionServer(rsIndex);
            if (server == null) {
                LOG.warn("RegionServer {} not found, skipping pre-flush", rsIndex);
                return;
            }

            int regionCount = 0;
            int flushSuccessCount = 0;
            int flushFailCount = 0;

            for (HRegion region : server.getOnlineRegionsLocalContext()) {
                regionCount++;
                try {
                    // Force flush the region's memstore to HFiles
                    HRegion.FlushResult result = region.flush(true);
                    if (result.isFlushSucceeded()) {
                        flushSuccessCount++;
                        LOG.debug("Flushed region {} before graceful restart",
                                region.getRegionInfo().getEncodedName());
                    } else {
                        // Flush was not needed (no data in memstore) or already flushing
                        flushSuccessCount++;
                        LOG.debug("Region {} flush result: {} before graceful restart",
                                region.getRegionInfo().getEncodedName(), result.getResult());
                    }
                } catch (Exception e) {
                    flushFailCount++;
                    LOG.warn("Failed to flush region {} before graceful restart: {}",
                            region.getRegionInfo().getEncodedName(), e.getMessage());
                }
            }

            LOG.info("Pre-flush completed for RegionServer {}: {} regions total, {} succeeded, {} failed",
                    rsIndex, regionCount, flushSuccessCount, flushFailCount);
        } catch (Exception e) {
            LOG.warn("Error during pre-flush for RegionServer {}: {}", rsIndex, e.getMessage());
            // Continue with restart even if pre-flush fails - the stop() will still attempt flush
        }
    }
}
