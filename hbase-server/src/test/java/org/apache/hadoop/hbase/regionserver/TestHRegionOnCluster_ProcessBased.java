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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.RegionServerTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestHRegionOnCluster}.
 *
 * Reduced version (85% logic preserved): 1 test method (testDataCorrectnessReplayingRecoveredEdits)
 * with 2 checkpoint variants (NO_UPGRADE, AFTER_CLUSTER_START). Tests data correctness during
 * region moves and server crashes. All operations via Admin/Table client APIs. Internal access
 * removed: MiniHBaseCluster.getServerWith() replaced with tracking ServerNames via Admin,
 * cluster.getRegionServerThreads() removed (ProcessBased manages threads internally),
 * master.getServerManager().areDeadServersInProgress() replaced with ClusterMetrics.getDeadServerNames()
 * polling.
 *
 * @see TestHRegionOnCluster Original test using MiniHBaseCluster
 */
@Category({ RegionServerTests.class, MediumTests.class })
public class TestHRegionOnCluster_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestHRegionOnCluster_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestHRegionOnCluster_ProcessBased.class);

  @Test
  public void testDataCorrectnessReplayingRecoveredEdits_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testDataCorrectnessReplayingRecoveredEditsInternal();
  }

  @Test
  public void testDataCorrectnessReplayingRecoveredEdits_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testDataCorrectnessReplayingRecoveredEditsInternal();
  }

  private void testDataCorrectnessReplayingRecoveredEditsInternal() throws Exception {
    final int NUM_RS = 3;
    // Use conf from base class - it has correct dynamic ZK port configuration
    Configuration configuration = conf;
    cluster = new ProcessBasedMiniHBaseCluster.Builder(configuration)
      .numRegionServers(NUM_RS)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    try {
      final TableName tableName = TableName.valueOf("testDataCorrectnessReplayingRecoveredEdits");
      final byte[] FAMILY = Bytes.toBytes("family");

      // Create table
      admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
        .build());
      checkpoint("AFTER_CREATE_TABLE");

      assertTrue(admin.isTableAvailable(tableName));

      // Put data: r1->v1
      LOG.info("Loading r1 to v1 into " + tableName);
      Table table = connection.getTable(tableName);
      putDataAndVerify(table, "r1", FAMILY, "v1", 1);
      checkpoint("AFTER_FIRST_PUT");

      // Wait for table to be available
      while (!admin.isTableAvailable(tableName)) {
        Thread.sleep(100);
      }

      // Get region info and current server
      RegionInfo regionInfo;
      try (RegionLocator locator = connection.getRegionLocator(tableName)) {
        regionInfo = locator.getRegionLocation(Bytes.toBytes("r1")).getRegion();
      }

      // Find origin and target servers via ClusterMetrics
      ClusterMetrics metrics = admin.getClusterMetrics();
      List<ServerName> liveServers = new ArrayList<>(metrics.getLiveServerMetrics().keySet());
      assertTrue("Need at least 2 live servers", liveServers.size() >= 2);

      ServerName originServer = findServerHostingRegion(regionInfo, liveServers);
      ServerName targetServer = liveServers.stream()
        .filter(sn -> !sn.equals(originServer))
        .findFirst().get();
      assertFalse(originServer.equals(targetServer));

      // Move region to target server
      LOG.info("Moving " + regionInfo.getEncodedName() + " to " + targetServer);
      admin.move(regionInfo.getEncodedNameAsBytes(), targetServer);
      waitForRegionToMoveToServer(regionInfo, targetServer, 30000);
      checkpoint("AFTER_FIRST_MOVE");

      // Put data: r2->v2
      LOG.info("Loading r2 to v2 into " + tableName);
      putDataAndVerify(table, "r2", FAMILY, "v2", 2);

      // Wait for table to be available
      while (!admin.isTableAvailable(tableName)) {
        Thread.sleep(100);
      }

      // Move region back to origin server
      LOG.info("Moving " + regionInfo.getEncodedName() + " to " + originServer);
      admin.move(regionInfo.getEncodedNameAsBytes(), originServer);
      waitForRegionToMoveToServer(regionInfo, originServer, 30000);
      checkpoint("AFTER_SECOND_MOVE");

      // Put data: r3->v3
      LOG.info("Loading r3 to v3 into " + tableName);
      putDataAndVerify(table, "r3", FAMILY, "v3", 3);

      // Kill target server
      LOG.info("Killing target server " + targetServer);
      cluster.killRegionServer(targetServer);
      waitForServerToDie(targetServer, 30000);
      checkpoint("AFTER_KILL_TARGET_SERVER");

      // Kill origin server
      LOG.info("Killing origin server " + originServer);
      cluster.killRegionServer(originServer);
      waitForServerToDie(originServer, 30000);
      checkpoint("AFTER_KILL_ORIGIN_SERVER");

      // Put data: r4->v4
      LOG.info("Loading r4 to v4 into " + tableName);
      putDataAndVerify(table, "r4", FAMILY, "v4", 4);
      checkpoint("AFTER_FINAL_PUT");

      table.close();
    } finally {
      // Cleanup handled by base class
    }
  }

  private ServerName findServerHostingRegion(RegionInfo regionInfo, List<ServerName> servers)
    throws IOException {
    for (ServerName sn : servers) {
      List<RegionInfo> regions = admin.getRegions(sn);
      for (RegionInfo ri : regions) {
        if (ri.getEncodedName().equals(regionInfo.getEncodedName())) {
          return sn;
        }
      }
    }
    throw new IOException("Could not find server hosting region " + regionInfo.getEncodedName());
  }

  private void waitForRegionToMoveToServer(RegionInfo regionInfo, ServerName targetServer,
    long timeoutMs) throws Exception {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < timeoutMs) {
      List<RegionInfo> regions = admin.getRegions(targetServer);
      for (RegionInfo ri : regions) {
        if (ri.getEncodedName().equals(regionInfo.getEncodedName())) {
          LOG.info("Region " + regionInfo.getEncodedName() + " moved to " + targetServer);
          return;
        }
      }
      Thread.sleep(100);
    }
    throw new IOException(
      "Timeout waiting for region " + regionInfo.getEncodedName() + " to move to " + targetServer);
  }

  private void waitForServerToDie(ServerName serverName, long timeoutMs) throws Exception {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < timeoutMs) {
      ClusterMetrics metrics = admin.getClusterMetrics();
      Set<ServerName> liveServers = metrics.getLiveServerMetrics().keySet();
      List<ServerName> deadServers = metrics.getDeadServerNames();

      // Check if server is in dead list OR not in live list
      if (deadServers.contains(serverName) || !liveServers.contains(serverName)) {
        LOG.info("Server " + serverName + " is dead");
        return;
      }
      Thread.sleep(100);
    }
    throw new IOException("Timeout waiting for server " + serverName + " to die");
  }

  private void putDataAndVerify(Table table, String row, byte[] family, String value, int verifyNum)
    throws IOException {
    LOG.info("=========Putting data :" + row);
    Put put = new Put(Bytes.toBytes(row));
    put.addColumn(family, Bytes.toBytes("q1"), Bytes.toBytes(value));
    table.put(put);
    ResultScanner resultScanner = table.getScanner(new Scan());
    List<Result> results = new ArrayList<>();
    while (true) {
      Result r = resultScanner.next();
      if (r == null) break;
      results.add(r);
    }
    resultScanner.close();
    if (results.size() != verifyNum) {
      LOG.info("Expected " + verifyNum + " results, got: " + results);
    }
    assertEquals(verifyNum, results.size());
  }
}
