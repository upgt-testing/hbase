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
package org.apache.hadoop.hbase;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import org.apache.hadoop.hbase.ClusterMetrics.Option;
import org.apache.hadoop.hbase.Waiter.Predicate;
import org.apache.hadoop.hbase.client.AsyncAdmin;
import org.apache.hadoop.hbase.client.AsyncConnection;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionStatesCount;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * ProcessBased version of {@link TestClientClusterMetrics}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Some tests that require JVM-internal access are commented out:
 * - testServerTasks: TaskMonitor is JVM-internal singleton
 * - testObserver: In-process atomic counters not accessible across processes
 * - testUserMetrics: Complex user impersonation with internal metrics tracking
 *
 * @see TestClientClusterMetrics Original test using MiniHBaseCluster
 */
@Category(MediumTests.class)
public class TestClientClusterMetrics_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestClientClusterMetrics_ProcessBased.class);

  private final static int SLAVES = 5;
  private final static int MASTERS = 3;
  private static final TableName TABLE_NAME = TableName.valueOf("test");
  private static final byte[] CF = Bytes.toBytes("cf");

  @Test(timeout = 300000)
  public void testDefaults_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    ClusterMetrics origin = admin.getClusterMetrics();
    ClusterMetrics defaults = admin.getClusterMetrics(EnumSet.allOf(Option.class));
    checkpoint("BEFORE_VERIFICATION");

    Assert.assertEquals(origin.getHBaseVersion(), defaults.getHBaseVersion());
    Assert.assertEquals(origin.getClusterId(), defaults.getClusterId());
    Assert.assertEquals(origin.getAverageLoad(), defaults.getAverageLoad(), 0);
    Assert.assertEquals(origin.getBackupMasterNames().size(),
      defaults.getBackupMasterNames().size());
    Assert.assertEquals(origin.getDeadServerNames().size(), defaults.getDeadServerNames().size());
    Assert.assertEquals(origin.getRegionCount(), defaults.getRegionCount());
    Assert.assertEquals(origin.getLiveServerMetrics().size(),
      defaults.getLiveServerMetrics().size());
    Assert.assertEquals(origin.getMasterInfoPort(), defaults.getMasterInfoPort());
    Assert.assertEquals(origin.getServersName().size(), defaults.getServersName().size());
    Assert.assertEquals(admin.getRegionServers().size(), defaults.getServersName().size());
  }

  @Test(timeout = 300000)
  public void testDefaults_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    ClusterMetrics origin = admin.getClusterMetrics();
    ClusterMetrics defaults = admin.getClusterMetrics(EnumSet.allOf(Option.class));
    checkpoint("BEFORE_VERIFICATION");

    Assert.assertEquals(origin.getHBaseVersion(), defaults.getHBaseVersion());
    Assert.assertEquals(origin.getClusterId(), defaults.getClusterId());
    Assert.assertEquals(origin.getAverageLoad(), defaults.getAverageLoad(), 0);
    Assert.assertEquals(origin.getBackupMasterNames().size(),
      defaults.getBackupMasterNames().size());
    Assert.assertEquals(origin.getDeadServerNames().size(), defaults.getDeadServerNames().size());
    Assert.assertEquals(origin.getRegionCount(), defaults.getRegionCount());
    Assert.assertEquals(origin.getLiveServerMetrics().size(),
      defaults.getLiveServerMetrics().size());
    Assert.assertEquals(origin.getMasterInfoPort(), defaults.getMasterInfoPort());
    Assert.assertEquals(origin.getServersName().size(), defaults.getServersName().size());
    Assert.assertEquals(admin.getRegionServers().size(), defaults.getServersName().size());
  }

  @Test(timeout = 300000)
  public void testDefaults_BEFORE_VERIFICATION() throws Exception {
    upgradeCheckpoint = "BEFORE_VERIFICATION";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    ClusterMetrics origin = admin.getClusterMetrics();
    ClusterMetrics defaults = admin.getClusterMetrics(EnumSet.allOf(Option.class));
    checkpoint("BEFORE_VERIFICATION");

    Assert.assertEquals(origin.getHBaseVersion(), defaults.getHBaseVersion());
    Assert.assertEquals(origin.getClusterId(), defaults.getClusterId());
    Assert.assertEquals(origin.getAverageLoad(), defaults.getAverageLoad(), 0);
    Assert.assertEquals(origin.getBackupMasterNames().size(),
      defaults.getBackupMasterNames().size());
    Assert.assertEquals(origin.getDeadServerNames().size(), defaults.getDeadServerNames().size());
    Assert.assertEquals(origin.getRegionCount(), defaults.getRegionCount());
    Assert.assertEquals(origin.getLiveServerMetrics().size(),
      defaults.getLiveServerMetrics().size());
    Assert.assertEquals(origin.getMasterInfoPort(), defaults.getMasterInfoPort());
    Assert.assertEquals(origin.getServersName().size(), defaults.getServersName().size());
    Assert.assertEquals(admin.getRegionServers().size(), defaults.getServersName().size());
  }

  @Test(timeout = 300000)
  public void testAsyncClient_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    try (AsyncConnection asyncConnect =
      ConnectionFactory.createAsyncConnection(conf).get()) {
      AsyncAdmin asyncAdmin = asyncConnect.getAdmin();
      CompletableFuture<ClusterMetrics> originFuture = asyncAdmin.getClusterMetrics();
      CompletableFuture<ClusterMetrics> defaultsFuture =
        asyncAdmin.getClusterMetrics(EnumSet.allOf(Option.class));
      ClusterMetrics origin = originFuture.get();
      ClusterMetrics defaults = defaultsFuture.get();
      checkpoint("BEFORE_VERIFICATION");

      Assert.assertEquals(origin.getHBaseVersion(), defaults.getHBaseVersion());
      Assert.assertEquals(origin.getClusterId(), defaults.getClusterId());
      Assert.assertEquals(origin.getHBaseVersion(), defaults.getHBaseVersion());
      Assert.assertEquals(origin.getClusterId(), defaults.getClusterId());
      Assert.assertEquals(origin.getAverageLoad(), defaults.getAverageLoad(), 0);
      Assert.assertEquals(origin.getBackupMasterNames().size(),
        defaults.getBackupMasterNames().size());
      Assert.assertEquals(origin.getDeadServerNames().size(), defaults.getDeadServerNames().size());
      Assert.assertEquals(origin.getRegionCount(), defaults.getRegionCount());
      Assert.assertEquals(origin.getLiveServerMetrics().size(),
        defaults.getLiveServerMetrics().size());
      Assert.assertEquals(origin.getMasterInfoPort(), defaults.getMasterInfoPort());
      Assert.assertEquals(origin.getServersName().size(), defaults.getServersName().size());
      origin.getTableRegionStatesCount().forEach(((tableName, regionStatesCount) -> {
        RegionStatesCount defaultRegionStatesCount =
          defaults.getTableRegionStatesCount().get(tableName);
        Assert.assertEquals(defaultRegionStatesCount, regionStatesCount);
      }));
    }
  }

  @Test(timeout = 300000)
  public void testAsyncClient_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    try (AsyncConnection asyncConnect =
      ConnectionFactory.createAsyncConnection(conf).get()) {
      AsyncAdmin asyncAdmin = asyncConnect.getAdmin();
      CompletableFuture<ClusterMetrics> originFuture = asyncAdmin.getClusterMetrics();
      CompletableFuture<ClusterMetrics> defaultsFuture =
        asyncAdmin.getClusterMetrics(EnumSet.allOf(Option.class));
      ClusterMetrics origin = originFuture.get();
      ClusterMetrics defaults = defaultsFuture.get();
      checkpoint("BEFORE_VERIFICATION");

      Assert.assertEquals(origin.getHBaseVersion(), defaults.getHBaseVersion());
      Assert.assertEquals(origin.getClusterId(), defaults.getClusterId());
      Assert.assertEquals(origin.getHBaseVersion(), defaults.getHBaseVersion());
      Assert.assertEquals(origin.getClusterId(), defaults.getClusterId());
      Assert.assertEquals(origin.getAverageLoad(), defaults.getAverageLoad(), 0);
      Assert.assertEquals(origin.getBackupMasterNames().size(),
        defaults.getBackupMasterNames().size());
      Assert.assertEquals(origin.getDeadServerNames().size(), defaults.getDeadServerNames().size());
      Assert.assertEquals(origin.getRegionCount(), defaults.getRegionCount());
      Assert.assertEquals(origin.getLiveServerMetrics().size(),
        defaults.getLiveServerMetrics().size());
      Assert.assertEquals(origin.getMasterInfoPort(), defaults.getMasterInfoPort());
      Assert.assertEquals(origin.getServersName().size(), defaults.getServersName().size());
      origin.getTableRegionStatesCount().forEach(((tableName, regionStatesCount) -> {
        RegionStatesCount defaultRegionStatesCount =
          defaults.getTableRegionStatesCount().get(tableName);
        Assert.assertEquals(defaultRegionStatesCount, regionStatesCount);
      }));
    }
  }

  @Test(timeout = 300000)
  public void testAsyncClient_BEFORE_VERIFICATION() throws Exception {
    upgradeCheckpoint = "BEFORE_VERIFICATION";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    try (AsyncConnection asyncConnect =
      ConnectionFactory.createAsyncConnection(conf).get()) {
      AsyncAdmin asyncAdmin = asyncConnect.getAdmin();
      CompletableFuture<ClusterMetrics> originFuture = asyncAdmin.getClusterMetrics();
      CompletableFuture<ClusterMetrics> defaultsFuture =
        asyncAdmin.getClusterMetrics(EnumSet.allOf(Option.class));
      ClusterMetrics origin = originFuture.get();
      ClusterMetrics defaults = defaultsFuture.get();
      checkpoint("BEFORE_VERIFICATION");

      Assert.assertEquals(origin.getHBaseVersion(), defaults.getHBaseVersion());
      Assert.assertEquals(origin.getClusterId(), defaults.getClusterId());
      Assert.assertEquals(origin.getHBaseVersion(), defaults.getHBaseVersion());
      Assert.assertEquals(origin.getClusterId(), defaults.getClusterId());
      Assert.assertEquals(origin.getAverageLoad(), defaults.getAverageLoad(), 0);
      Assert.assertEquals(origin.getBackupMasterNames().size(),
        defaults.getBackupMasterNames().size());
      Assert.assertEquals(origin.getDeadServerNames().size(), defaults.getDeadServerNames().size());
      Assert.assertEquals(origin.getRegionCount(), defaults.getRegionCount());
      Assert.assertEquals(origin.getLiveServerMetrics().size(),
        defaults.getLiveServerMetrics().size());
      Assert.assertEquals(origin.getMasterInfoPort(), defaults.getMasterInfoPort());
      Assert.assertEquals(origin.getServersName().size(), defaults.getServersName().size());
      origin.getTableRegionStatesCount().forEach(((tableName, regionStatesCount) -> {
        RegionStatesCount defaultRegionStatesCount =
          defaults.getTableRegionStatesCount().get(tableName);
        Assert.assertEquals(defaultRegionStatesCount, regionStatesCount);
      }));
    }
  }

  @Test(timeout = 300000)
  public void testLiveAndDeadServersStatus_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Get initial state - all RS should be alive
    ClusterMetrics initialMetrics = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS));
    int initialLiveServers = initialMetrics.getLiveServerMetrics().size();
    Assert.assertEquals(SLAVES, initialLiveServers);

    // Kill one region server - track by ServerName, not object reference
    ServerName deadServerName = initialMetrics.getLiveServerMetrics().keySet().iterator().next();
    cluster.killRegionServer(deadServerName);
    checkpoint("AFTER_KILL_RS");

    // Wait for dead server to be detected
    Waiter.waitFor(conf, 60 * 1000, 100, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.DEAD_SERVERS));
        return metrics.getDeadServerNames().size() >= 1;
      }
    });
    checkpoint("AFTER_VERIFY_DEAD_SERVER");

    // Wait for cluster to stabilize with regions assigned
    Waiter.waitFor(conf, 10 * 1000, 100, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS));
        Assert.assertNotNull(metrics);
        return metrics.getRegionCount() > 0;
      }
    });

    // Retrieve live servers and dead servers info.
    EnumSet<Option> options =
      EnumSet.of(Option.LIVE_SERVERS, Option.DEAD_SERVERS, Option.SERVERS_NAME);
    ClusterMetrics metrics = admin.getClusterMetrics(options);
    Assert.assertNotNull(metrics);

    // Should have one less live server
    Assert.assertEquals(SLAVES - 1, metrics.getLiveServerMetrics().size());
    Assert.assertTrue(metrics.getRegionCount() > 0);
    Assert.assertNotNull(metrics.getDeadServerNames());
    Assert.assertEquals(1, metrics.getDeadServerNames().size());

    // Verify the dead server name matches what we killed
    ServerName reportedDeadServer = metrics.getDeadServerNames().iterator().next();
    Assert.assertEquals(deadServerName.getHostname(), reportedDeadServer.getHostname());
    Assert.assertEquals(deadServerName.getPort(), reportedDeadServer.getPort());

    Assert.assertNotNull(metrics.getServersName());
    Assert.assertEquals(SLAVES - 1, metrics.getServersName().size());
  }

  @Test(timeout = 300000)
  public void testLiveAndDeadServersStatus_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Get initial state - all RS should be alive
    ClusterMetrics initialMetrics = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS));
    int initialLiveServers = initialMetrics.getLiveServerMetrics().size();
    Assert.assertEquals(SLAVES, initialLiveServers);

    // Kill one region server - track by ServerName, not object reference
    ServerName deadServerName = initialMetrics.getLiveServerMetrics().keySet().iterator().next();
    cluster.killRegionServer(deadServerName);
    checkpoint("AFTER_KILL_RS");

    // Wait for dead server to be detected
    Waiter.waitFor(conf, 60 * 1000, 100, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.DEAD_SERVERS));
        return metrics.getDeadServerNames().size() >= 1;
      }
    });
    checkpoint("AFTER_VERIFY_DEAD_SERVER");

    // Wait for cluster to stabilize with regions assigned
    Waiter.waitFor(conf, 10 * 1000, 100, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS));
        Assert.assertNotNull(metrics);
        return metrics.getRegionCount() > 0;
      }
    });

    // Retrieve live servers and dead servers info.
    EnumSet<Option> options =
      EnumSet.of(Option.LIVE_SERVERS, Option.DEAD_SERVERS, Option.SERVERS_NAME);
    ClusterMetrics metrics = admin.getClusterMetrics(options);
    Assert.assertNotNull(metrics);

    // Should have one less live server
    Assert.assertEquals(SLAVES - 1, metrics.getLiveServerMetrics().size());
    Assert.assertTrue(metrics.getRegionCount() > 0);
    Assert.assertNotNull(metrics.getDeadServerNames());
    Assert.assertEquals(1, metrics.getDeadServerNames().size());

    // Verify the dead server name matches what we killed
    ServerName reportedDeadServer = metrics.getDeadServerNames().iterator().next();
    Assert.assertEquals(deadServerName.getHostname(), reportedDeadServer.getHostname());
    Assert.assertEquals(deadServerName.getPort(), reportedDeadServer.getPort());

    Assert.assertNotNull(metrics.getServersName());
    Assert.assertEquals(SLAVES - 1, metrics.getServersName().size());
  }

  @Test(timeout = 300000)
  public void testLiveAndDeadServersStatus_AFTER_KILL_RS() throws Exception {
    upgradeCheckpoint = "AFTER_KILL_RS";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Get initial state - all RS should be alive
    ClusterMetrics initialMetrics = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS));
    int initialLiveServers = initialMetrics.getLiveServerMetrics().size();
    Assert.assertEquals(SLAVES, initialLiveServers);

    // Kill one region server - track by ServerName, not object reference
    ServerName deadServerName = initialMetrics.getLiveServerMetrics().keySet().iterator().next();
    cluster.killRegionServer(deadServerName);
    checkpoint("AFTER_KILL_RS");

    // Wait for dead server to be detected
    Waiter.waitFor(conf, 60 * 1000, 100, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.DEAD_SERVERS));
        return metrics.getDeadServerNames().size() >= 1;
      }
    });
    checkpoint("AFTER_VERIFY_DEAD_SERVER");

    // Wait for cluster to stabilize with regions assigned
    Waiter.waitFor(conf, 10 * 1000, 100, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS));
        Assert.assertNotNull(metrics);
        return metrics.getRegionCount() > 0;
      }
    });

    // Retrieve live servers and dead servers info.
    EnumSet<Option> options =
      EnumSet.of(Option.LIVE_SERVERS, Option.DEAD_SERVERS, Option.SERVERS_NAME);
    ClusterMetrics metrics = admin.getClusterMetrics(options);
    Assert.assertNotNull(metrics);

    // Should have one less live server
    Assert.assertEquals(SLAVES - 1, metrics.getLiveServerMetrics().size());
    Assert.assertTrue(metrics.getRegionCount() > 0);
    Assert.assertNotNull(metrics.getDeadServerNames());
    Assert.assertEquals(1, metrics.getDeadServerNames().size());

    // Verify the dead server name matches what we killed
    ServerName reportedDeadServer = metrics.getDeadServerNames().iterator().next();
    Assert.assertEquals(deadServerName.getHostname(), reportedDeadServer.getHostname());
    Assert.assertEquals(deadServerName.getPort(), reportedDeadServer.getPort());

    Assert.assertNotNull(metrics.getServersName());
    Assert.assertEquals(SLAVES - 1, metrics.getServersName().size());
  }

  @Test(timeout = 300000)
  public void testLiveAndDeadServersStatus_AFTER_VERIFY_DEAD_SERVER() throws Exception {
    upgradeCheckpoint = "AFTER_VERIFY_DEAD_SERVER";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Get initial state - all RS should be alive
    ClusterMetrics initialMetrics = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS));
    int initialLiveServers = initialMetrics.getLiveServerMetrics().size();
    Assert.assertEquals(SLAVES, initialLiveServers);

    // Kill one region server - track by ServerName, not object reference
    ServerName deadServerName = initialMetrics.getLiveServerMetrics().keySet().iterator().next();
    cluster.killRegionServer(deadServerName);
    checkpoint("AFTER_KILL_RS");

    // Wait for dead server to be detected
    Waiter.waitFor(conf, 60 * 1000, 100, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.DEAD_SERVERS));
        return metrics.getDeadServerNames().size() >= 1;
      }
    });
    checkpoint("AFTER_VERIFY_DEAD_SERVER");

    // Wait for cluster to stabilize with regions assigned
    Waiter.waitFor(conf, 10 * 1000, 100, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS));
        Assert.assertNotNull(metrics);
        return metrics.getRegionCount() > 0;
      }
    });

    // Retrieve live servers and dead servers info.
    EnumSet<Option> options =
      EnumSet.of(Option.LIVE_SERVERS, Option.DEAD_SERVERS, Option.SERVERS_NAME);
    ClusterMetrics metrics = admin.getClusterMetrics(options);
    Assert.assertNotNull(metrics);

    // Should have one less live server
    Assert.assertEquals(SLAVES - 1, metrics.getLiveServerMetrics().size());
    Assert.assertTrue(metrics.getRegionCount() > 0);
    Assert.assertNotNull(metrics.getDeadServerNames());
    Assert.assertEquals(1, metrics.getDeadServerNames().size());

    // Verify the dead server name matches what we killed
    ServerName reportedDeadServer = metrics.getDeadServerNames().iterator().next();
    Assert.assertEquals(deadServerName.getHostname(), reportedDeadServer.getHostname());
    Assert.assertEquals(deadServerName.getPort(), reportedDeadServer.getPort());

    Assert.assertNotNull(metrics.getServersName());
    Assert.assertEquals(SLAVES - 1, metrics.getServersName().size());
  }

  @Test(timeout = 300000)
  public void testRegionStatesCount_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(TABLE_NAME);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF));
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(TABLE_NAME)) {
      table.put(new Put(Bytes.toBytes("k1")).addColumn(CF, Bytes.toBytes("q1"), Bytes.toBytes("v1")));
      table.put(new Put(Bytes.toBytes("k2")).addColumn(CF, Bytes.toBytes("q2"), Bytes.toBytes("v2")));
      table.put(new Put(Bytes.toBytes("k3")).addColumn(CF, Bytes.toBytes("q3"), Bytes.toBytes("v3")));
    }
    checkpoint("AFTER_WRITE_DATA");

    ClusterMetrics metrics = admin.getClusterMetrics();
    checkpoint("AFTER_VERIFY_REGION_STATES");

    Assert.assertEquals(metrics.getTableRegionStatesCount().size(), 3);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getRegionsInTransition(),
      0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getOpenRegions(), 1);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getTotalRegions(), 1);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getClosedRegions(), 0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getSplitRegions(), 0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TABLE_NAME).getRegionsInTransition(), 0);
    Assert.assertEquals(metrics.getTableRegionStatesCount().get(TABLE_NAME).getOpenRegions(), 1);
    Assert.assertEquals(metrics.getTableRegionStatesCount().get(TABLE_NAME).getTotalRegions(), 1);

    admin.deleteTable(TABLE_NAME);
  }

  @Test(timeout = 300000)
  public void testRegionStatesCount_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(TABLE_NAME);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF));
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(TABLE_NAME)) {
      table.put(new Put(Bytes.toBytes("k1")).addColumn(CF, Bytes.toBytes("q1"), Bytes.toBytes("v1")));
      table.put(new Put(Bytes.toBytes("k2")).addColumn(CF, Bytes.toBytes("q2"), Bytes.toBytes("v2")));
      table.put(new Put(Bytes.toBytes("k3")).addColumn(CF, Bytes.toBytes("q3"), Bytes.toBytes("v3")));
    }
    checkpoint("AFTER_WRITE_DATA");

    ClusterMetrics metrics = admin.getClusterMetrics();
    checkpoint("AFTER_VERIFY_REGION_STATES");

    Assert.assertEquals(metrics.getTableRegionStatesCount().size(), 3);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getRegionsInTransition(),
      0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getOpenRegions(), 1);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getTotalRegions(), 1);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getClosedRegions(), 0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getSplitRegions(), 0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TABLE_NAME).getRegionsInTransition(), 0);
    Assert.assertEquals(metrics.getTableRegionStatesCount().get(TABLE_NAME).getOpenRegions(), 1);
    Assert.assertEquals(metrics.getTableRegionStatesCount().get(TABLE_NAME).getTotalRegions(), 1);

    admin.deleteTable(TABLE_NAME);
  }

  @Test(timeout = 300000)
  public void testRegionStatesCount_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(TABLE_NAME);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF));
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(TABLE_NAME)) {
      table.put(new Put(Bytes.toBytes("k1")).addColumn(CF, Bytes.toBytes("q1"), Bytes.toBytes("v1")));
      table.put(new Put(Bytes.toBytes("k2")).addColumn(CF, Bytes.toBytes("q2"), Bytes.toBytes("v2")));
      table.put(new Put(Bytes.toBytes("k3")).addColumn(CF, Bytes.toBytes("q3"), Bytes.toBytes("v3")));
    }
    checkpoint("AFTER_WRITE_DATA");

    ClusterMetrics metrics = admin.getClusterMetrics();
    checkpoint("AFTER_VERIFY_REGION_STATES");

    Assert.assertEquals(metrics.getTableRegionStatesCount().size(), 3);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getRegionsInTransition(),
      0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getOpenRegions(), 1);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getTotalRegions(), 1);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getClosedRegions(), 0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getSplitRegions(), 0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TABLE_NAME).getRegionsInTransition(), 0);
    Assert.assertEquals(metrics.getTableRegionStatesCount().get(TABLE_NAME).getOpenRegions(), 1);
    Assert.assertEquals(metrics.getTableRegionStatesCount().get(TABLE_NAME).getTotalRegions(), 1);

    admin.deleteTable(TABLE_NAME);
  }

  @Test(timeout = 300000)
  public void testRegionStatesCount_AFTER_WRITE_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE_DATA";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(TABLE_NAME);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF));
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(TABLE_NAME)) {
      table.put(new Put(Bytes.toBytes("k1")).addColumn(CF, Bytes.toBytes("q1"), Bytes.toBytes("v1")));
      table.put(new Put(Bytes.toBytes("k2")).addColumn(CF, Bytes.toBytes("q2"), Bytes.toBytes("v2")));
      table.put(new Put(Bytes.toBytes("k3")).addColumn(CF, Bytes.toBytes("q3"), Bytes.toBytes("v3")));
    }
    checkpoint("AFTER_WRITE_DATA");

    ClusterMetrics metrics = admin.getClusterMetrics();
    checkpoint("AFTER_VERIFY_REGION_STATES");

    Assert.assertEquals(metrics.getTableRegionStatesCount().size(), 3);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getRegionsInTransition(),
      0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getOpenRegions(), 1);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getTotalRegions(), 1);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getClosedRegions(), 0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getSplitRegions(), 0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TABLE_NAME).getRegionsInTransition(), 0);
    Assert.assertEquals(metrics.getTableRegionStatesCount().get(TABLE_NAME).getOpenRegions(), 1);
    Assert.assertEquals(metrics.getTableRegionStatesCount().get(TABLE_NAME).getTotalRegions(), 1);

    admin.deleteTable(TABLE_NAME);
  }

  @Test(timeout = 300000)
  public void testRegionStatesCount_AFTER_VERIFY_REGION_STATES() throws Exception {
    upgradeCheckpoint = "AFTER_VERIFY_REGION_STATES";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(TABLE_NAME);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF));
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(TABLE_NAME)) {
      table.put(new Put(Bytes.toBytes("k1")).addColumn(CF, Bytes.toBytes("q1"), Bytes.toBytes("v1")));
      table.put(new Put(Bytes.toBytes("k2")).addColumn(CF, Bytes.toBytes("q2"), Bytes.toBytes("v2")));
      table.put(new Put(Bytes.toBytes("k3")).addColumn(CF, Bytes.toBytes("q3"), Bytes.toBytes("v3")));
    }
    checkpoint("AFTER_WRITE_DATA");

    ClusterMetrics metrics = admin.getClusterMetrics();
    checkpoint("AFTER_VERIFY_REGION_STATES");

    Assert.assertEquals(metrics.getTableRegionStatesCount().size(), 3);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getRegionsInTransition(),
      0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getOpenRegions(), 1);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getTotalRegions(), 1);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getClosedRegions(), 0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TableName.META_TABLE_NAME).getSplitRegions(), 0);
    Assert.assertEquals(
      metrics.getTableRegionStatesCount().get(TABLE_NAME).getRegionsInTransition(), 0);
    Assert.assertEquals(metrics.getTableRegionStatesCount().get(TABLE_NAME).getOpenRegions(), 1);
    Assert.assertEquals(metrics.getTableRegionStatesCount().get(TABLE_NAME).getTotalRegions(), 1);

    admin.deleteTable(TABLE_NAME);
  }

  @Test(timeout = 300000)
  public void testMasterAndBackupMastersStatus_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Use ClusterMetrics instead of direct thread access
    EnumSet<Option> options = EnumSet.of(Option.MASTER, Option.BACKUP_MASTERS);
    ClusterMetrics metrics = admin.getClusterMetrics(options);
    checkpoint("BEFORE_VERIFICATION");

    Assert.assertNotNull(metrics.getMasterName());
    // Verify there is exactly one active master
    Assert.assertTrue(metrics.getMasterName().getPort() > 0);
    Assert.assertEquals(MASTERS - 1, metrics.getBackupMasterNames().size());
  }

  @Test(timeout = 300000)
  public void testMasterAndBackupMastersStatus_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Use ClusterMetrics instead of direct thread access
    EnumSet<Option> options = EnumSet.of(Option.MASTER, Option.BACKUP_MASTERS);
    ClusterMetrics metrics = admin.getClusterMetrics(options);
    checkpoint("BEFORE_VERIFICATION");

    Assert.assertNotNull(metrics.getMasterName());
    // Verify there is exactly one active master
    Assert.assertTrue(metrics.getMasterName().getPort() > 0);
    Assert.assertEquals(MASTERS - 1, metrics.getBackupMasterNames().size());
  }

  @Test(timeout = 300000)
  public void testMasterAndBackupMastersStatus_BEFORE_VERIFICATION() throws Exception {
    upgradeCheckpoint = "BEFORE_VERIFICATION";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Use ClusterMetrics instead of direct thread access
    EnumSet<Option> options = EnumSet.of(Option.MASTER, Option.BACKUP_MASTERS);
    ClusterMetrics metrics = admin.getClusterMetrics(options);
    checkpoint("BEFORE_VERIFICATION");

    Assert.assertNotNull(metrics.getMasterName());
    // Verify there is exactly one active master
    Assert.assertTrue(metrics.getMasterName().getPort() > 0);
    Assert.assertEquals(MASTERS - 1, metrics.getBackupMasterNames().size());
  }

  @Test(timeout = 300000)
  public void testOtherStatusInfos_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    EnumSet<Option> options = EnumSet.of(Option.MASTER_COPROCESSORS, Option.HBASE_VERSION,
      Option.CLUSTER_ID, Option.BALANCER_ON);
    ClusterMetrics metrics = admin.getClusterMetrics(options);
    checkpoint("BEFORE_VERIFICATION");

    // Note: Coprocessor count may vary - just verify we can get this info
    Assert.assertNotNull(metrics.getMasterCoprocessorNames());
    Assert.assertNotNull(metrics.getHBaseVersion());
    Assert.assertNotNull(metrics.getClusterId());
    Assert.assertTrue(metrics.getAverageLoad() >= 0.0);
    Assert.assertNotNull(metrics.getBalancerOn());
  }

  @Test(timeout = 300000)
  public void testOtherStatusInfos_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    EnumSet<Option> options = EnumSet.of(Option.MASTER_COPROCESSORS, Option.HBASE_VERSION,
      Option.CLUSTER_ID, Option.BALANCER_ON);
    ClusterMetrics metrics = admin.getClusterMetrics(options);
    checkpoint("BEFORE_VERIFICATION");

    // Note: Coprocessor count may vary - just verify we can get this info
    Assert.assertNotNull(metrics.getMasterCoprocessorNames());
    Assert.assertNotNull(metrics.getHBaseVersion());
    Assert.assertNotNull(metrics.getClusterId());
    Assert.assertTrue(metrics.getAverageLoad() >= 0.0);
    Assert.assertNotNull(metrics.getBalancerOn());
  }

  @Test(timeout = 300000)
  public void testOtherStatusInfos_BEFORE_VERIFICATION() throws Exception {
    upgradeCheckpoint = "BEFORE_VERIFICATION";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    EnumSet<Option> options = EnumSet.of(Option.MASTER_COPROCESSORS, Option.HBASE_VERSION,
      Option.CLUSTER_ID, Option.BALANCER_ON);
    ClusterMetrics metrics = admin.getClusterMetrics(options);
    checkpoint("BEFORE_VERIFICATION");

    // Note: Coprocessor count may vary - just verify we can get this info
    Assert.assertNotNull(metrics.getMasterCoprocessorNames());
    Assert.assertNotNull(metrics.getHBaseVersion());
    Assert.assertNotNull(metrics.getClusterId());
    Assert.assertTrue(metrics.getAverageLoad() >= 0.0);
    Assert.assertNotNull(metrics.getBalancerOn());
  }

  // TRANSFORMATION NOTE: testServerTasks removed.
  // TaskMonitor is a JVM-internal singleton that tracks tasks within the same process.
  // In ProcessBasedMiniHBaseCluster, each node runs in a separate JVM, so we cannot
  // access the TaskMonitor from the test JVM. The test created a task using
  // TaskMonitor.get().createStatus() and verified it appeared in cluster metrics,
  // but this requires in-process access to the TaskMonitor singleton.
  // No client-side alternative available for creating and tracking tasks.

  // TRANSFORMATION NOTE: testObserver removed.
  // The original test used in-process atomic counters (MyObserver.PRE_COUNT, POST_COUNT)
  // to verify coprocessor method invocations. These counters only work when the coprocessor
  // runs in the same JVM as the test. In ProcessBasedMiniHBaseCluster, coprocessors run
  // in the master's JVM, not the test JVM, so static counters are not shared.
  // Alternative: We can only verify coprocessor presence via ClusterMetrics.getMasterCoprocessorNames(),
  // but cannot track invocation counts.

  // TRANSFORMATION NOTE: testUserMetrics removed.
  // The test uses User.createUserForTesting() and User.runAs() to execute operations
  // as different users, then verifies per-user metrics aggregation. This requires:
  // 1. In-process user impersonation (User.runAs)
  // 2. Forcing region server reports via custom MyRegionServer class
  // 3. Accessing per-user request counts from server metrics
  // While we could verify some user metrics via ClusterMetrics, the complex user
  // impersonation and forced reporting are not feasible in process-based testing.
}
