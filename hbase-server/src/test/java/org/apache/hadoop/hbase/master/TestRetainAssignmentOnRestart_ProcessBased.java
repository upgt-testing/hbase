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

import static org.apache.hadoop.hbase.master.assignment.AssignmentManager.FORCE_REGION_RETAINMENT;
import static org.apache.hadoop.hbase.master.assignment.AssignmentManager.FORCE_REGION_RETAINMENT_WAIT_INTERVAL;
import static org.apache.hadoop.hbase.master.procedure.ServerCrashProcedure.MASTER_SCP_RETAIN_ASSIGNMENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestRetainAssignmentOnRestart}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * TRANSFORMATION NOTES:
 * - testRetainAssignmentOnClusterRestart removed: requires full cluster shutdown/restart
 *   with data persistence, which ProcessBasedMiniHBaseCluster does not support (always formats on restart)
 * - testRetainAssignmentOnSingleRSRestart transformed: tests RS restart with assignment retention
 * - testForceRetainAssignment transformed: tests force retain with SCP
 * - Custom HMasterForTest class removed: ProcessBased cannot inject custom classes into separate JVMs.
 *   The custom master only delayed procedure executor start - not essential to test value.
 * - Port preservation: ProcessBased handles automatically (persists ports to disk)
 * - getLiveRegionServerThreads() replaced with ClusterMetrics.getLiveServerMetrics()
 * - ServerManager.getOnlineServersList() replaced with ClusterMetrics.getLiveServerMetrics().keySet()
 * - SnapshotOfRegionAssignmentFromMeta still works (only needs Connection)
 *
 * @see TestRetainAssignmentOnRestart Original test using MiniHBaseCluster
 */
@Category({ MasterTests.class, MediumTests.class })
public class TestRetainAssignmentOnRestart_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestRetainAssignmentOnRestart_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestRetainAssignmentOnRestart_ProcessBased.class);

  private static final int NUM_OF_RS = 3;

  private static final TableName[] TABLES = { TableName.valueOf("restartTableOne"),
    TableName.valueOf("restartTableTwo"), TableName.valueOf("restartTableThree") };

  private static final byte[] FAMILY = Bytes.toBytes("family");

  /**
   * This tests retaining assignments on a single node restart - NO_UPGRADE
   */
  @Test(timeout = 300000)
  public void testRetainAssignmentOnSingleRSRestart_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTestRetainAssignmentOnSingleRSRestart();
  }

  /**
   * This tests retaining assignments on a single node restart - AFTER_CLUSTER_START
   */
  @Test(timeout = 300000)
  public void testRetainAssignmentOnSingleRSRestart_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTestRetainAssignmentOnSingleRSRestart();
  }

  /**
   * This tests retaining assignments on a single node restart - AFTER_CREATE_TABLES
   */
  @Test(timeout = 300000)
  public void testRetainAssignmentOnSingleRSRestart_AFTER_CREATE_TABLES() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLES";
    runTestRetainAssignmentOnSingleRSRestart();
  }

  /**
   * This tests retaining assignments on a single node restart - AFTER_STOP_RS
   */
  @Test(timeout = 300000)
  public void testRetainAssignmentOnSingleRSRestart_AFTER_STOP_RS() throws Exception {
    upgradeCheckpoint = "AFTER_STOP_RS";
    runTestRetainAssignmentOnSingleRSRestart();
  }

  /**
   * This tests retaining assignments on a single node restart - AFTER_RESTART_RS
   */
  @Test(timeout = 300000)
  public void testRetainAssignmentOnSingleRSRestart_AFTER_RESTART_RS() throws Exception {
    upgradeCheckpoint = "AFTER_RESTART_RS";
    runTestRetainAssignmentOnSingleRSRestart();
  }

  private void runTestRetainAssignmentOnSingleRSRestart() throws Exception {
    setupClusterForRetainAssignment();

    ClusterMetrics metrics = admin.getClusterMetrics();
    assertEquals(NUM_OF_RS, metrics.getLiveServerMetrics().size());

    // Capture server ports before restart
    ServerName[] serverNames = metrics.getLiveServerMetrics().keySet().toArray(new ServerName[0]);
    int[] rsPorts = new int[NUM_OF_RS];
    for (int i = 0; i < NUM_OF_RS; i++) {
      rsPorts[i] = serverNames[i].getPort();
    }

    // Capture region assignments before restart
    SnapshotOfRegionAssignmentFromMeta snapshot =
      new SnapshotOfRegionAssignmentFromMeta(connection);
    snapshot.initialize();
    Map<RegionInfo, ServerName> regionToRegionServerMap = snapshot.getRegionToRegionServerMap();
    for (ServerName serverName : regionToRegionServerMap.values()) {
      boolean found = false;
      for (int k = 0; k < NUM_OF_RS && !found; k++) {
        found = serverName.getPort() == rsPorts[k];
      }
      assertTrue("Region should be on one of the known servers", found);
    }

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);
    checkpoint("AFTER_CREATE_TABLES");

    // Server to be restarted - pick the first one
    ServerName deadRS = serverNames[0];
    LOG.info("\n\nStopping region server {}", deadRS);
    cluster.stopRegionServer(deadRS);
    cluster.waitForRegionServerToStop(deadRS, 60000);

    checkpoint("AFTER_STOP_RS");

    LOG.info("\n\nSleeping a bit");
    Thread.sleep(2000);

    LOG.info("\n\nRestarting region server 0 (was: {})", deadRS);
    // ProcessBased automatically preserves port allocations via restartRegionServer
    // Restarting index 0 since we stopped serverNames[0]
    cluster.restartRegionServer(0);

    checkpoint("AFTER_RESTART_RS");

    // Verify servers are still using same ports
    ensureServersWithSamePort(rsPorts);

    // Wait till all regions are assigned
    for (TableName TABLE : TABLES) {
      admin.getConnection().getTable(TABLE).close(); // ensure table accessible
    }
    waitForNoRegionsInTransition(60000);

    // Verify assignments retained
    snapshot = new SnapshotOfRegionAssignmentFromMeta(connection);
    snapshot.initialize();
    Map<RegionInfo, ServerName> newRegionToRegionServerMap = snapshot.getRegionToRegionServerMap();
    assertEquals(regionToRegionServerMap.size(), newRegionToRegionServerMap.size());

    for (Map.Entry<RegionInfo, ServerName> entry : newRegionToRegionServerMap.entrySet()) {
      ServerName oldServer = regionToRegionServerMap.get(entry.getKey());
      ServerName currentServer = entry.getValue();
      LOG.info(
        "Key=" + entry.getKey() + " oldServer=" + oldServer + ", currentServer=" + currentServer);
      assertEquals(entry.getKey().toString(), oldServer.getAddress(), currentServer.getAddress());

      if (deadRS.getPort() == oldServer.getPort()) {
        // Restarted RS start code won't be same
        assertNotEquals(oldServer.getStartcode(), currentServer.getStartcode());
      } else {
        assertEquals(oldServer.getStartcode(), currentServer.getStartcode());
      }
    }
  }

  /**
   * This tests the force retaining assignments upon an RS restart, even when master triggers an SCP - NO_UPGRADE
   */
  @Test(timeout = 300000)
  public void testForceRetainAssignment_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTestForceRetainAssignment();
  }

  /**
   * This tests the force retaining assignments upon an RS restart, even when master triggers an SCP - AFTER_CLUSTER_START
   */
  @Test(timeout = 300000)
  public void testForceRetainAssignment_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTestForceRetainAssignment();
  }

  /**
   * This tests the force retaining assignments upon an RS restart, even when master triggers an SCP - AFTER_CREATE_TABLES
   */
  @Test(timeout = 300000)
  public void testForceRetainAssignment_AFTER_CREATE_TABLES() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLES";
    runTestForceRetainAssignment();
  }

  /**
   * This tests the force retaining assignments upon an RS restart, even when master triggers an SCP - AFTER_STOP_RS
   */
  @Test(timeout = 300000)
  public void testForceRetainAssignment_AFTER_STOP_RS() throws Exception {
    upgradeCheckpoint = "AFTER_STOP_RS";
    runTestForceRetainAssignment();
  }

  /**
   * This tests the force retaining assignments upon an RS restart, even when master triggers an SCP - AFTER_RESTART_RS
   */
  @Test(timeout = 300000)
  public void testForceRetainAssignment_AFTER_RESTART_RS() throws Exception {
    upgradeCheckpoint = "AFTER_RESTART_RS";
    runTestForceRetainAssignment();
  }

  private void runTestForceRetainAssignment() throws Exception {
    conf.setBoolean(FORCE_REGION_RETAINMENT, true);
    conf.setLong(FORCE_REGION_RETAINMENT_WAIT_INTERVAL, 50);

    setupClusterForRetainAssignment();

    ClusterMetrics metrics = admin.getClusterMetrics();
    assertEquals(NUM_OF_RS, metrics.getLiveServerMetrics().size());

    // Capture server ports before restart
    ServerName[] serverNames = metrics.getLiveServerMetrics().keySet().toArray(new ServerName[0]);
    int[] rsPorts = new int[NUM_OF_RS];
    for (int i = 0; i < NUM_OF_RS; i++) {
      rsPorts[i] = serverNames[i].getPort();
    }

    // Capture region assignments before restart
    SnapshotOfRegionAssignmentFromMeta snapshot =
      new SnapshotOfRegionAssignmentFromMeta(connection);
    snapshot.initialize();
    Map<RegionInfo, ServerName> regionToRegionServerMap = snapshot.getRegionToRegionServerMap();
    for (ServerName serverName : regionToRegionServerMap.values()) {
      boolean found = false;
      for (int k = 0; k < NUM_OF_RS && !found; k++) {
        found = serverName.getPort() == rsPorts[k];
      }
      LOG.info("Server {} has regions? {}", serverName, found);
      assertTrue("Region should be on one of the known servers", found);
    }

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);
    checkpoint("AFTER_CREATE_TABLES");

    // Server to be restarted - pick the first one
    ServerName deadRS = serverNames[0];
    LOG.info("\n\nStopping region server {}", deadRS);
    cluster.stopRegionServer(deadRS);
    cluster.waitForRegionServerToStop(deadRS, 60000);

    checkpoint("AFTER_STOP_RS");

    LOG.info("\n\nSleeping a bit");
    Thread.sleep(2000);

    LOG.info("\n\nRestarting region server 0 (was: {})", deadRS);
    // ProcessBased automatically preserves port allocations via restartRegionServer
    // Restarting index 0 since we stopped serverNames[0]
    cluster.restartRegionServer(0);

    checkpoint("AFTER_RESTART_RS");

    // Verify servers are still using same ports
    ensureServersWithSamePort(rsPorts);

    // Wait till all regions are assigned
    for (TableName TABLE : TABLES) {
      admin.getConnection().getTable(TABLE).close(); // ensure table accessible
    }
    waitForNoRegionsInTransition(60000);

    // Verify assignments retained
    snapshot = new SnapshotOfRegionAssignmentFromMeta(connection);
    snapshot.initialize();
    Map<RegionInfo, ServerName> newRegionToRegionServerMap = snapshot.getRegionToRegionServerMap();
    assertEquals(regionToRegionServerMap.size(), newRegionToRegionServerMap.size());

    for (Map.Entry<RegionInfo, ServerName> entry : newRegionToRegionServerMap.entrySet()) {
      ServerName oldServer = regionToRegionServerMap.get(entry.getKey());
      ServerName currentServer = entry.getValue();
      LOG.info(
        "Key=" + entry.getKey() + " oldServer=" + oldServer + ", currentServer=" + currentServer);
      assertEquals(entry.getKey().toString(), oldServer.getAddress(), currentServer.getAddress());

      if (deadRS.getPort() == oldServer.getPort()) {
        // Restarted RS start code won't be same
        assertNotEquals(oldServer.getStartcode(), currentServer.getStartcode());
      } else {
        assertEquals(oldServer.getStartcode(), currentServer.getStartcode());
      }
    }
  }

  private void setupClusterForRetainAssignment() throws Exception {
    // Enable retain assignment during ServerCrashProcedure
    conf.setBoolean(MASTER_SCP_RETAIN_ASSIGNMENT, true);
    conf.setBoolean(HConstants.HBASE_SPLIT_WAL_COORDINATED_BY_ZK, true);

    // Build cluster (ProcessBased auto-formats on first start)
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(NUM_OF_RS)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitClusterUp();

    // Turn off balancer
    admin.balancerSwitch(false, true);

    LOG.info("\n\nCreating tables");
    for (TableName TABLE : TABLES) {
      admin.createTable(TableDescriptorBuilder.newBuilder(TABLE)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
        .build());
    }
    for (TableName TABLE : TABLES) {
      admin.getConnection().getTable(TABLE).close(); // ensure table enabled
    }

    waitForNoRegionsInTransition(60000);
  }

  private void ensureServersWithSamePort(int[] rsPorts) throws Exception {
    // Make sure live regionservers are on the same host/port
    ClusterMetrics metrics = admin.getClusterMetrics();
    ServerName[] localServers = metrics.getLiveServerMetrics().keySet().toArray(new ServerName[0]);
    assertEquals(NUM_OF_RS, localServers.length);
    for (int i = 0; i < NUM_OF_RS; i++) {
      boolean found = false;
      for (ServerName serverName : localServers) {
        if (serverName.getPort() == rsPorts[i]) {
          found = true;
          break;
        }
      }
      assertTrue("Port " + rsPorts[i] + " should be found in live servers", found);
    }
  }

  private void waitForNoRegionsInTransition(long timeout) throws Exception {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < timeout) {
      ClusterMetrics metrics = admin.getClusterMetrics();
      if (metrics.getRegionStatesInTransition().isEmpty()) {
        return;
      }
      Thread.sleep(100);
    }
    throw new Exception("Regions still in transition after " + timeout + "ms");
  }
}
