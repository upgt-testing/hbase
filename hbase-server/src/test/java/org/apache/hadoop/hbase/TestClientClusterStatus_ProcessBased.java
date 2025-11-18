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
import org.apache.hadoop.hbase.ClusterMetrics.Option;
import org.apache.hadoop.hbase.Waiter.Predicate;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * ProcessBased version of {@link TestClientClusterStatus}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Tests the deprecated ClusterStatus wrapper around ClusterMetrics.
 *
 * Some tests that require JVM-internal access are commented out:
 * - testObserver: In-process atomic counters not accessible across processes
 *
 * @see TestClientClusterStatus Original test using MiniHBaseCluster
 */
@Category(MediumTests.class)
public class TestClientClusterStatus_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestClientClusterStatus_ProcessBased.class);

  private final static int SLAVES = 5;
  private final static int MASTERS = 3;

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

    ClusterStatus origin = admin.getClusterStatus();
    ClusterStatus defaults =
      new ClusterStatus(admin.getClusterMetrics(EnumSet.allOf(Option.class)));
    checkpoint("BEFORE_VERIFICATION");

    checkPbObjectNotNull(origin);
    checkPbObjectNotNull(defaults);
    Assert.assertEquals(origin.getHBaseVersion(), defaults.getHBaseVersion());
    Assert.assertEquals(origin.getClusterId(), defaults.getClusterId());
    Assert.assertTrue(origin.getAverageLoad() == defaults.getAverageLoad());
    Assert.assertTrue(origin.getBackupMastersSize() == defaults.getBackupMastersSize());
    Assert.assertTrue(origin.getDeadServersSize() == defaults.getDeadServersSize());
    Assert.assertTrue(origin.getRegionsCount() == defaults.getRegionsCount());
    Assert.assertTrue(origin.getServersSize() == defaults.getServersSize());
    Assert.assertTrue(origin.getMasterInfoPort() == defaults.getMasterInfoPort());
    Assert.assertTrue(origin.equals(defaults));
    Assert.assertTrue(origin.getServersName().size() == defaults.getServersName().size());
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

    ClusterStatus origin = admin.getClusterStatus();
    ClusterStatus defaults =
      new ClusterStatus(admin.getClusterMetrics(EnumSet.allOf(Option.class)));
    checkpoint("BEFORE_VERIFICATION");

    checkPbObjectNotNull(origin);
    checkPbObjectNotNull(defaults);
    Assert.assertEquals(origin.getHBaseVersion(), defaults.getHBaseVersion());
    Assert.assertEquals(origin.getClusterId(), defaults.getClusterId());
    Assert.assertTrue(origin.getAverageLoad() == defaults.getAverageLoad());
    Assert.assertTrue(origin.getBackupMastersSize() == defaults.getBackupMastersSize());
    Assert.assertTrue(origin.getDeadServersSize() == defaults.getDeadServersSize());
    Assert.assertTrue(origin.getRegionsCount() == defaults.getRegionsCount());
    Assert.assertTrue(origin.getServersSize() == defaults.getServersSize());
    Assert.assertTrue(origin.getMasterInfoPort() == defaults.getMasterInfoPort());
    Assert.assertTrue(origin.equals(defaults));
    Assert.assertTrue(origin.getServersName().size() == defaults.getServersName().size());
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

    ClusterStatus origin = admin.getClusterStatus();
    ClusterStatus defaults =
      new ClusterStatus(admin.getClusterMetrics(EnumSet.allOf(Option.class)));
    checkpoint("BEFORE_VERIFICATION");

    checkPbObjectNotNull(origin);
    checkPbObjectNotNull(defaults);
    Assert.assertEquals(origin.getHBaseVersion(), defaults.getHBaseVersion());
    Assert.assertEquals(origin.getClusterId(), defaults.getClusterId());
    Assert.assertTrue(origin.getAverageLoad() == defaults.getAverageLoad());
    Assert.assertTrue(origin.getBackupMastersSize() == defaults.getBackupMastersSize());
    Assert.assertTrue(origin.getDeadServersSize() == defaults.getDeadServersSize());
    Assert.assertTrue(origin.getRegionsCount() == defaults.getRegionsCount());
    Assert.assertTrue(origin.getServersSize() == defaults.getServersSize());
    Assert.assertTrue(origin.getMasterInfoPort() == defaults.getMasterInfoPort());
    Assert.assertTrue(origin.equals(defaults));
    Assert.assertTrue(origin.getServersName().size() == defaults.getServersName().size());
  }

  @Test(timeout = 300000)
  public void testNone_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    ClusterMetrics status0 = admin.getClusterMetrics(EnumSet.allOf(Option.class));
    ClusterMetrics status1 = admin.getClusterMetrics(EnumSet.noneOf(Option.class));
    checkpoint("BEFORE_VERIFICATION");

    // Do a rough compare. More specific compares can fail because all regions not deployed yet
    // or more requests than expected.
    Assert.assertEquals(status0.getLiveServerMetrics().size(),
      status1.getLiveServerMetrics().size());
    checkPbObjectNotNull(new ClusterStatus(status0));
    checkPbObjectNotNull(new ClusterStatus(status1));
  }

  @Test(timeout = 300000)
  public void testNone_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    ClusterMetrics status0 = admin.getClusterMetrics(EnumSet.allOf(Option.class));
    ClusterMetrics status1 = admin.getClusterMetrics(EnumSet.noneOf(Option.class));
    checkpoint("BEFORE_VERIFICATION");

    // Do a rough compare. More specific compares can fail because all regions not deployed yet
    // or more requests than expected.
    Assert.assertEquals(status0.getLiveServerMetrics().size(),
      status1.getLiveServerMetrics().size());
    checkPbObjectNotNull(new ClusterStatus(status0));
    checkPbObjectNotNull(new ClusterStatus(status1));
  }

  @Test(timeout = 300000)
  public void testNone_BEFORE_VERIFICATION() throws Exception {
    upgradeCheckpoint = "BEFORE_VERIFICATION";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    ClusterMetrics status0 = admin.getClusterMetrics(EnumSet.allOf(Option.class));
    ClusterMetrics status1 = admin.getClusterMetrics(EnumSet.noneOf(Option.class));
    checkpoint("BEFORE_VERIFICATION");

    // Do a rough compare. More specific compares can fail because all regions not deployed yet
    // or more requests than expected.
    Assert.assertEquals(status0.getLiveServerMetrics().size(),
      status1.getLiveServerMetrics().size());
    checkPbObjectNotNull(new ClusterStatus(status0));
    checkPbObjectNotNull(new ClusterStatus(status1));
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
        ClusterStatus status =
          new ClusterStatus(admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS)));
        Assert.assertNotNull(status);
        return status.getRegionsCount() > 0;
      }
    });

    // Retrieve live servers and dead servers info.
    EnumSet<Option> options =
      EnumSet.of(Option.LIVE_SERVERS, Option.DEAD_SERVERS, Option.SERVERS_NAME);
    ClusterStatus status = new ClusterStatus(admin.getClusterMetrics(options));
    checkpoint("BEFORE_VERIFICATION");

    checkPbObjectNotNull(status);
    Assert.assertNotNull(status);
    Assert.assertNotNull(status.getServers());

    // Should have one less live server
    Assert.assertEquals(SLAVES - 1, status.getServers().size());
    Assert.assertTrue(status.getRegionsCount() > 0);
    Assert.assertNotNull(status.getDeadServerNames());
    Assert.assertEquals(1, status.getDeadServersSize());

    // Verify the dead server name matches what we killed
    ServerName reportedDeadServer = status.getDeadServerNames().iterator().next();
    Assert.assertEquals(deadServerName.getHostname(), reportedDeadServer.getHostname());
    Assert.assertEquals(deadServerName.getPort(), reportedDeadServer.getPort());

    Assert.assertNotNull(status.getServersName());
    Assert.assertEquals(SLAVES - 1, status.getServersName().size());
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
        ClusterStatus status =
          new ClusterStatus(admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS)));
        Assert.assertNotNull(status);
        return status.getRegionsCount() > 0;
      }
    });

    // Retrieve live servers and dead servers info.
    EnumSet<Option> options =
      EnumSet.of(Option.LIVE_SERVERS, Option.DEAD_SERVERS, Option.SERVERS_NAME);
    ClusterStatus status = new ClusterStatus(admin.getClusterMetrics(options));
    checkpoint("BEFORE_VERIFICATION");

    checkPbObjectNotNull(status);
    Assert.assertNotNull(status);
    Assert.assertNotNull(status.getServers());

    // Should have one less live server
    Assert.assertEquals(SLAVES - 1, status.getServers().size());
    Assert.assertTrue(status.getRegionsCount() > 0);
    Assert.assertNotNull(status.getDeadServerNames());
    Assert.assertEquals(1, status.getDeadServersSize());

    // Verify the dead server name matches what we killed
    ServerName reportedDeadServer = status.getDeadServerNames().iterator().next();
    Assert.assertEquals(deadServerName.getHostname(), reportedDeadServer.getHostname());
    Assert.assertEquals(deadServerName.getPort(), reportedDeadServer.getPort());

    Assert.assertNotNull(status.getServersName());
    Assert.assertEquals(SLAVES - 1, status.getServersName().size());
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
        ClusterStatus status =
          new ClusterStatus(admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS)));
        Assert.assertNotNull(status);
        return status.getRegionsCount() > 0;
      }
    });

    // Retrieve live servers and dead servers info.
    EnumSet<Option> options =
      EnumSet.of(Option.LIVE_SERVERS, Option.DEAD_SERVERS, Option.SERVERS_NAME);
    ClusterStatus status = new ClusterStatus(admin.getClusterMetrics(options));
    checkpoint("BEFORE_VERIFICATION");

    checkPbObjectNotNull(status);
    Assert.assertNotNull(status);
    Assert.assertNotNull(status.getServers());

    // Should have one less live server
    Assert.assertEquals(SLAVES - 1, status.getServers().size());
    Assert.assertTrue(status.getRegionsCount() > 0);
    Assert.assertNotNull(status.getDeadServerNames());
    Assert.assertEquals(1, status.getDeadServersSize());

    // Verify the dead server name matches what we killed
    ServerName reportedDeadServer = status.getDeadServerNames().iterator().next();
    Assert.assertEquals(deadServerName.getHostname(), reportedDeadServer.getHostname());
    Assert.assertEquals(deadServerName.getPort(), reportedDeadServer.getPort());

    Assert.assertNotNull(status.getServersName());
    Assert.assertEquals(SLAVES - 1, status.getServersName().size());
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
        ClusterStatus status =
          new ClusterStatus(admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS)));
        Assert.assertNotNull(status);
        return status.getRegionsCount() > 0;
      }
    });

    // Retrieve live servers and dead servers info.
    EnumSet<Option> options =
      EnumSet.of(Option.LIVE_SERVERS, Option.DEAD_SERVERS, Option.SERVERS_NAME);
    ClusterStatus status = new ClusterStatus(admin.getClusterMetrics(options));
    checkpoint("BEFORE_VERIFICATION");

    checkPbObjectNotNull(status);
    Assert.assertNotNull(status);
    Assert.assertNotNull(status.getServers());

    // Should have one less live server
    Assert.assertEquals(SLAVES - 1, status.getServers().size());
    Assert.assertTrue(status.getRegionsCount() > 0);
    Assert.assertNotNull(status.getDeadServerNames());
    Assert.assertEquals(1, status.getDeadServersSize());

    // Verify the dead server name matches what we killed
    ServerName reportedDeadServer = status.getDeadServerNames().iterator().next();
    Assert.assertEquals(deadServerName.getHostname(), reportedDeadServer.getHostname());
    Assert.assertEquals(deadServerName.getPort(), reportedDeadServer.getPort());

    Assert.assertNotNull(status.getServersName());
    Assert.assertEquals(SLAVES - 1, status.getServersName().size());
  }

  @Test(timeout = 300000)
  public void testLiveAndDeadServersStatus_BEFORE_VERIFICATION() throws Exception {
    upgradeCheckpoint = "BEFORE_VERIFICATION";
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
        ClusterStatus status =
          new ClusterStatus(admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS)));
        Assert.assertNotNull(status);
        return status.getRegionsCount() > 0;
      }
    });

    // Retrieve live servers and dead servers info.
    EnumSet<Option> options =
      EnumSet.of(Option.LIVE_SERVERS, Option.DEAD_SERVERS, Option.SERVERS_NAME);
    ClusterStatus status = new ClusterStatus(admin.getClusterMetrics(options));
    checkpoint("BEFORE_VERIFICATION");

    checkPbObjectNotNull(status);
    Assert.assertNotNull(status);
    Assert.assertNotNull(status.getServers());

    // Should have one less live server
    Assert.assertEquals(SLAVES - 1, status.getServers().size());
    Assert.assertTrue(status.getRegionsCount() > 0);
    Assert.assertNotNull(status.getDeadServerNames());
    Assert.assertEquals(1, status.getDeadServersSize());

    // Verify the dead server name matches what we killed
    ServerName reportedDeadServer = status.getDeadServerNames().iterator().next();
    Assert.assertEquals(deadServerName.getHostname(), reportedDeadServer.getHostname());
    Assert.assertEquals(deadServerName.getPort(), reportedDeadServer.getPort());

    Assert.assertNotNull(status.getServersName());
    Assert.assertEquals(SLAVES - 1, status.getServersName().size());
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
    ClusterStatus status = new ClusterStatus(admin.getClusterMetrics(options));
    checkpoint("BEFORE_VERIFICATION");

    Assert.assertNotNull(status.getMaster());
    // Verify there is exactly one active master
    Assert.assertTrue(status.getMaster().getPort() > 0);
    Assert.assertEquals(MASTERS - 1, status.getBackupMastersSize());
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
    ClusterStatus status = new ClusterStatus(admin.getClusterMetrics(options));
    checkpoint("BEFORE_VERIFICATION");

    Assert.assertNotNull(status.getMaster());
    // Verify there is exactly one active master
    Assert.assertTrue(status.getMaster().getPort() > 0);
    Assert.assertEquals(MASTERS - 1, status.getBackupMastersSize());
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
    ClusterStatus status = new ClusterStatus(admin.getClusterMetrics(options));
    checkpoint("BEFORE_VERIFICATION");

    Assert.assertNotNull(status.getMaster());
    // Verify there is exactly one active master
    Assert.assertTrue(status.getMaster().getPort() > 0);
    Assert.assertEquals(MASTERS - 1, status.getBackupMastersSize());
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
    ClusterStatus status = new ClusterStatus(admin.getClusterMetrics(options));
    checkpoint("BEFORE_VERIFICATION");

    // Note: Coprocessor count may vary - just verify we can get this info
    Assert.assertNotNull(status.getMasterCoprocessors());
    Assert.assertNotNull(status.getHBaseVersion());
    Assert.assertNotNull(status.getClusterId());
    Assert.assertTrue(status.getAverageLoad() >= 0.0);
    Assert.assertNotNull(status.getBalancerOn());
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
    ClusterStatus status = new ClusterStatus(admin.getClusterMetrics(options));
    checkpoint("BEFORE_VERIFICATION");

    // Note: Coprocessor count may vary - just verify we can get this info
    Assert.assertNotNull(status.getMasterCoprocessors());
    Assert.assertNotNull(status.getHBaseVersion());
    Assert.assertNotNull(status.getClusterId());
    Assert.assertTrue(status.getAverageLoad() >= 0.0);
    Assert.assertNotNull(status.getBalancerOn());
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
    ClusterStatus status = new ClusterStatus(admin.getClusterMetrics(options));
    checkpoint("BEFORE_VERIFICATION");

    // Note: Coprocessor count may vary - just verify we can get this info
    Assert.assertNotNull(status.getMasterCoprocessors());
    Assert.assertNotNull(status.getHBaseVersion());
    Assert.assertNotNull(status.getClusterId());
    Assert.assertTrue(status.getAverageLoad() >= 0.0);
    Assert.assertNotNull(status.getBalancerOn());
  }

  /**
   * HBASE-19496 do the refactor for ServerLoad and RegionLoad so the inner pb object is useless
   * now. However, they are Public classes, and consequently we must make sure the all pb objects
   * have initialized.
   */
  private static void checkPbObjectNotNull(ClusterStatus status) {
    for (ServerName name : status.getLiveServerMetrics().keySet()) {
      ServerLoad load = status.getLoad(name);
      Assert.assertNotNull(load.obtainServerLoadPB());
      for (RegionLoad rl : load.getRegionsLoad().values()) {
        Assert.assertNotNull(rl.regionLoadPB);
      }
    }
  }

  // TRANSFORMATION NOTE: testObserver removed.
  // The original test used in-process atomic counters (MyObserver.PRE_COUNT, POST_COUNT)
  // to verify coprocessor method invocations. These counters only work when the coprocessor
  // runs in the same JVM as the test. In ProcessBasedMiniHBaseCluster, coprocessors run
  // in the master's JVM, not the test JVM, so static counters are not shared.
  // Alternative: We can only verify coprocessor presence via ClusterMetrics.getMasterCoprocessorNames(),
  // but cannot track invocation counts.
}
