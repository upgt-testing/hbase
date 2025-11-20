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
package org.apache.hadoop.hbase.master;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.Waiter;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.BalanceRequest;
import org.apache.hadoop.hbase.client.BalanceResponse;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.RegionSplitter;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * ProcessBased version of {@link TestMasterDryRunBalancer}.
 *
 * Reduced transformation: Tests dry-run balancer client-visible behavior (no moves executed).
 * Removed: Mockito.verify() on internal method executeRegionPlansWithThrottling(),
 * direct HRegionServer access for region verification.
 *
 * @see TestMasterDryRunBalancer Original test using MiniHBaseCluster
 */
@Category({ MasterTests.class, MediumTests.class })
public class TestMasterDryRunBalancer_ProcessBased extends ProcessBasedUpgradeTestBase {
  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestMasterDryRunBalancer_ProcessBased.class);

  private static final byte[] FAMILYNAME = Bytes.toBytes("fam");

  @Test
  public void testDryRunBalancer_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    conf = HBaseConfiguration.create();
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(2)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    testDryRunBalancerLogic();
  }

  @Test
  public void testDryRunBalancer_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    conf = HBaseConfiguration.create();
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(2)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    testDryRunBalancerLogic();
  }

  @Test
  public void testDryRunBalancer_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";

    conf = HBaseConfiguration.create();
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(2)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    testDryRunBalancerLogic();
  }

  @Test
  public void testDryRunBalancer_AFTER_UNBALANCE() throws Exception {
    upgradeCheckpoint = "AFTER_UNBALANCE";

    conf = HBaseConfiguration.create();
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(2)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    testDryRunBalancerLogic();
  }

  private void testDryRunBalancerLogic() throws Exception {
    int numRegions = 100;
    int regionsPerRs = numRegions / 2;
    TableName tableName = createTable("testDryRunBalancer", numRegions);

    checkpoint("AFTER_CREATE_TABLE");

    // Dry run should be possible with balancer disabled
    // Disabling it will ensure the chore does not mess with our forced unbalance below
    admin.balancerSwitch(false, true);
    assertFalse(admin.getClusterMetrics().getBalancerOn());

    ServerName biasedServer = unbalance(tableName);

    checkpoint("AFTER_UNBALANCE");

    BalanceResponse response = admin.balance(BalanceRequest.newBuilder().setDryRun(true).build());
    assertTrue(response.isBalancerRan());
    // We don't know for sure that it will be exactly half the regions
    assertTrue(response.getMovesCalculated() >= (regionsPerRs - 1)
      && response.getMovesCalculated() <= (regionsPerRs + 1));
    // But we expect no moves executed due to dry run
    assertEquals(0, response.getMovesExecuted());

    // TRANSFORMATION NOTE: Removed Mockito.verify() on internal method executeRegionPlansWithThrottling().
    // Cannot spy on HMaster across process boundaries. Verification now relies on:
    // (1) response.getMovesExecuted() == 0 (client-visible API)
    // (2) Cluster still unbalanced after dry run (verified below)

    // Should still be unbalanced post dry run
    assertServerContainsAllRegions(biasedServer, tableName);

    // Cleanup
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  private TableName createTable(String table, int numRegions) throws IOException {
    TableName tableName = TableName.valueOf(table);
    byte[][] splitKeys = new RegionSplitter.HexStringSplit().split(numRegions);
    TableDescriptor td = TableDescriptorBuilder
        .newBuilder(tableName)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILYNAME))
        .build();
    admin.createTable(td, splitKeys);
    return tableName;
  }

  private ServerName unbalance(TableName tableName) throws Exception {
    waitForRegionsToSettle();

    // Get first RegionServer ServerName via ClusterMetrics
    ServerName biasedServer = admin.getClusterMetrics().getLiveServerMetrics().keySet()
        .iterator().next();

    // Move all regions to the biased server
    List<RegionInfo> regions = admin.getRegions(tableName);
    for (RegionInfo regionInfo : regions) {
      admin.move(regionInfo.getEncodedNameAsBytes(),
        Bytes.toBytes(biasedServer.getServerName()));
    }

    waitForRegionsToSettle();

    assertServerContainsAllRegions(biasedServer, tableName);

    return biasedServer;
  }

  private void assertServerContainsAllRegions(ServerName serverName, TableName tableName)
    throws IOException {
    int totalRegions = admin.getRegions(tableName).size();

    // TRANSFORMATION NOTE: Cannot use getRegionServer(serverName).getRegions(tableName)
    // across process boundaries. Instead verify via Admin.getRegionMetrics().
    // Count regions hosted by this server.
    int regionsOnServer = 0;
    List<RegionInfo> allRegions = admin.getRegions(tableName);
    for (RegionInfo region : allRegions) {
      // Get hosting server for this region
      ServerName hostingServer = connection.getRegionLocator(tableName)
          .getRegionLocation(region.getStartKey()).getServerName();
      if (hostingServer.equals(serverName)) {
        regionsOnServer++;
      }
    }

    assertEquals(totalRegions, regionsOnServer);
  }

  private void waitForRegionsToSettle() {
    // Use ClusterMetrics.getTableRegionStatesCount() to get RIT count across all tables
    Waiter.waitFor(conf, 60_000, () -> {
      int totalRIT = admin.getClusterMetrics().getTableRegionStatesCount().values().stream()
          .mapToInt(count -> count.getRegionsInTransition())
          .sum();
      return totalRIT <= 0;
    });
  }
}
