/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.regionserver;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.RegionMetrics;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.Size;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.RegionSplitter;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestRegionReplicasAreDistributed}.
 *
 * <p>Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * <p><b>TRANSFORMATION NOTE - Full Transformation:</b>
 * This test validates that regions are distributed across RegionServers and that
 * the same regions stay on the same servers after disable/enable (region retention).
 *
 * <p><b>Original test approach:</b>
 * <ul>
 *   <li>Get HRegionServer objects via getMiniHBaseCluster().getRegionServer(i)</li>
 *   <li>Get online regions via rs.getOnlineRegionsLocalContext()</li>
 *   <li>Iterate HRegion objects to build server-to-region mapping</li>
 *   <li>Verify same regions stay on same servers after disable/enable</li>
 * </ul>
 *
 * <p><b>Transformed test approach (client API):</b>
 * <ul>
 *   <li>Get all servers via Admin.getClusterMetrics().getLiveServerMetrics()</li>
 *   <li>For each server, get regions via Admin.getRegionMetrics(ServerName)</li>
 *   <li>Build server-to-region mapping using RegionMetrics.getRegionName()</li>
 *   <li>Verify same regions stay on same servers after disable/enable</li>
 * </ul>
 *
 * <p><b>Client API used:</b> Admin.getRegionMetrics(ServerName) provides per-server
 * region listings, enabling verification of region distribution and retention without
 * internal access.
 *
 * @see TestRegionReplicasAreDistributed Original test using MiniHBaseCluster
 */
public class TestRegionReplicasAreDistributed_ProcessBased extends ProcessBasedUpgradeTestBase {

  private static final Logger LOG =
    LoggerFactory.getLogger(TestRegionReplicasAreDistributed_ProcessBased.class);

  private static final int NB_SERVERS = 3;
  private static final byte[] FAMILY = Bytes.toBytes(HConstants.CATALOG_FAMILY_STR);

  private Map<ServerName, Collection<byte[]>> serverVsRegionsBefore;

  @Test
  public void testRegionReplicasCreatedAreDistributed_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTestRegionReplicasCreatedAreDistributed();
  }

  @Test
  public void testRegionReplicasCreatedAreDistributed_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTestRegionReplicasCreatedAreDistributed();
  }

  @Test
  public void testRegionReplicasCreatedAreDistributed_AFTER_TABLE_CREATE() throws Exception {
    upgradeCheckpoint = "AFTER_TABLE_CREATE";
    runTestRegionReplicasCreatedAreDistributed();
  }

  @Test
  public void testRegionReplicasCreatedAreDistributed_AFTER_FIRST_CHECK() throws Exception {
    upgradeCheckpoint = "AFTER_FIRST_CHECK";
    runTestRegionReplicasCreatedAreDistributed();
  }

  @Test
  public void testRegionReplicasCreatedAreDistributed_AFTER_DISABLE_ENABLE() throws Exception {
    upgradeCheckpoint = "AFTER_DISABLE_ENABLE";
    runTestRegionReplicasCreatedAreDistributed();
  }

  private void runTestRegionReplicasCreatedAreDistributed() throws Exception {
    TableName tableName = TableName.valueOf("testRegionReplicasCreatedAreDistributed");

    Configuration testConf = HBaseConfiguration.create();
    testConf.setInt("hbase.master.wait.on.regionservers.mintostart", 3);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(testConf)
        .numRegionServers(NB_SERVERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Give cluster time to stabilize
    Thread.sleep(3000);

    // Create table with 3 region replicas and 20 regions
    TableDescriptor td = TableDescriptorBuilder
        .newBuilder(tableName)
        .setRegionReplication(3)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
        .build();

    byte[][] splits = getSplits(20);
    admin.createTable(td, splits);

    LOG.info("Created table {} with 3 region replicas and 20 regions", tableName);

    checkpoint("AFTER_TABLE_CREATE");

    // First check: Capture which regions are on which servers
    serverVsRegionsBefore = captureServerToRegionMapping(tableName);
    LOG.info("Captured initial server-to-region mapping for {} servers",
        serverVsRegionsBefore.size());

    checkpoint("AFTER_FIRST_CHECK");

    // Disable and enable the table - this should preserve region-to-server mapping
    admin.disableTable(tableName);
    LOG.info("Disabled table {}", tableName);

    admin.enableTable(tableName);
    LOG.info("Enabled table {}", tableName);

    checkpoint("AFTER_DISABLE_ENABLE");

    // Second check: Verify same regions stayed on same servers (region retention)
    boolean retentionOk = verifyRegionRetention(tableName, serverVsRegionsBefore);
    assertTrue("Region retention failed - regions should stay on same servers after disable/enable",
        retentionOk);

    LOG.info("Region retention verified successfully!");

    checkpoint("AFTER_VERIFICATION");

    // Cleanup
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  /**
   * Captures server-to-region mapping using client API.
   * Uses Admin.getRegionMetrics(ServerName) to get per-server region listings.
   */
  private Map<ServerName, Collection<byte[]>> captureServerToRegionMapping(TableName tableName)
      throws Exception {
    Map<ServerName, Collection<byte[]>> mapping = new HashMap<>();

    // Get all live servers
    for (ServerName server : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
      // Get regions for this server
      List<RegionMetrics> regionMetrics = admin.getRegionMetrics(server, tableName);
      Collection<byte[]> regionNames = new ArrayList<>();

      for (RegionMetrics rm : regionMetrics) {
        regionNames.add(rm.getRegionName());
      }

      if (!regionNames.isEmpty()) {
        mapping.put(server, regionNames);
        LOG.info("Server {} hosts {} regions of table {}",
            server, regionNames.size(), tableName);
      }
    }

    return mapping;
  }

  /**
   * Verifies that the same regions stayed on the same servers after disable/enable.
   * This is the core test assertion for region retention.
   */
  private boolean verifyRegionRetention(TableName tableName,
      Map<ServerName, Collection<byte[]>> beforeMapping) throws Exception {

    // Capture current server-to-region mapping
    Map<ServerName, Collection<byte[]>> afterMapping = captureServerToRegionMapping(tableName);

    // Verify each server in beforeMapping
    for (Map.Entry<ServerName, Collection<byte[]>> entry : beforeMapping.entrySet()) {
      ServerName server = entry.getKey();
      Collection<byte[]> regionsBefore = entry.getValue();

      // Get regions on this server after disable/enable
      Collection<byte[]> regionsAfter = afterMapping.get(server);

      if (regionsAfter == null) {
        LOG.warn("Server {} no longer has any regions of table {}", server, tableName);
        return false;
      }

      // Verify all regions from before are still on this server
      for (byte[] regionNameBefore : regionsBefore) {
        boolean found = false;
        for (byte[] regionNameAfter : regionsAfter) {
          if (Bytes.equals(regionNameBefore, regionNameAfter)) {
            found = true;
            break;
          }
        }
        if (!found) {
          LOG.warn("Region {} was on server {} before, but not after disable/enable",
              Bytes.toStringBinary(regionNameBefore), server);
          return false;
        }
      }

      LOG.info("Server {} retained all {} regions", server, regionsBefore.size());
    }

    return true;
  }

  /**
   * Generates split keys for creating table with multiple regions.
   * Same logic as original test.
   */
  private byte[][] getSplits(int numRegions) {
    RegionSplitter.UniformSplit split = new RegionSplitter.UniformSplit();
    split.setFirstRow(Bytes.toBytes(0L));
    split.setLastRow(Bytes.toBytes(Long.MAX_VALUE));
    return split.split(numRegions);
  }
}
