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
package org.apache.hadoop.hbase.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.Waiter;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.testclassification.ClientTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.hbase.thirdparty.com.google.common.base.Preconditions;

/**
 * ProcessBased version of {@link TestMasterRegistry}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * @see TestMasterRegistry Original test using MiniHBaseCluster
 */
@Category({ MediumTests.class, ClientTests.class })
public class TestMasterRegistry_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestMasterRegistry_ProcessBased.class);

  /**
   * Generates a string of dummy master addresses in host:port format. Every other hostname won't
   * have a port number.
   */
  private static String generateDummyMastersList(int size) {
    List<String> masters = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      masters.add(" localhost" + (i % 2 == 0 ? ":" + (1000 + i) : ""));
    }
    return String.join(",", masters);
  }

  /**
   * Makes sure the master registry parses the master end points in the configuration correctly.
   * No cluster required - pure configuration parsing test.
   */
  @Test
  public void testMasterAddressParsing() throws Exception {
    Configuration conf = HBaseConfiguration.create();
    int numMasters = 10;
    conf.set(HConstants.MASTER_ADDRS_KEY, generateDummyMastersList(numMasters));
    List<ServerName> parsedMasters = new ArrayList<>(MasterRegistry.parseMasterAddrs(conf));
    // Half of them would be without a port, duplicates are removed.
    assertEquals(numMasters / 2 + 1, parsedMasters.size());
    // Sort in the increasing order of port numbers.
    Collections.sort(parsedMasters, Comparator.comparingInt(ServerName::getPort));
    for (int i = 0; i < parsedMasters.size(); i++) {
      ServerName sn = parsedMasters.get(i);
      assertEquals("localhost", sn.getHostname());
      if (i == parsedMasters.size() - 1) {
        // Last entry should be the one with default port.
        assertEquals(HConstants.DEFAULT_MASTER_PORT, sn.getPort());
      } else {
        assertEquals(1000 + (2 * i), sn.getPort());
      }
    }
  }

  /**
   * No cluster required - pure configuration parsing test.
   */
  @Test
  public void testMasterPortDefaults() throws Exception {
    Configuration conf = HBaseConfiguration.create();
    conf.set(HConstants.MASTER_ADDRS_KEY, "localhost");
    List<ServerName> parsedMasters = new ArrayList<>(MasterRegistry.parseMasterAddrs(conf));
    ServerName sn = parsedMasters.get(0);
    assertEquals(HConstants.DEFAULT_MASTER_PORT, sn.getPort());
    final int CUSTOM_MASTER_PORT = 9999;
    conf.setInt(HConstants.MASTER_PORT, CUSTOM_MASTER_PORT);
    parsedMasters = new ArrayList<>(MasterRegistry.parseMasterAddrs(conf));
    sn = parsedMasters.get(0);
    assertEquals(CUSTOM_MASTER_PORT, sn.getPort());
  }

  @Test
  public void testRegistryRPCs_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testRegistryRPCs();
  }

  @Test
  public void testRegistryRPCs_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testRegistryRPCs();
  }

  private void testRegistryRPCs() throws Exception {
    conf = HBaseConfiguration.create();

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numMasters(3)
      .numRegionServers(3)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Get active master via ClusterMetrics
    ServerName activeMaster = admin.getClusterMetrics().getMasterName();
    String clusterId = admin.getClusterMetrics().getClusterId();

    // Test MasterRegistry with hedged requests
    // Note: Meta replica configuration not available in ProcessBased, test with 1 hedged req
    conf.setInt(MasterRegistry.MASTER_REGISTRY_HEDGED_REQS_FANOUT_KEY, 1);
    try (MasterRegistry registry = new MasterRegistry(conf, User.getCurrent())) {
      assertEquals(registry.getClusterId().get(), clusterId);
      assertEquals(registry.getActiveMaster().get(), activeMaster);
      // Verify meta region locations are accessible
      assertTrue(registry.getMetaRegionLocations().get().getRegionLocations().length > 0);
    }
  }

  // TRANSFORMATION NOTE: testDynamicMasterConfigurationRefresh removed.
  // This test requires extensive internal cluster state access not available in ProcessBased:
  // 1. activeMaster.getMetaRegionLocationCache() - internal cache inspection (lines 128, 140)
  // 2. TEST_UTIL.getMiniHBaseCluster().getLiveMasterThreads() - thread access (line 181)
  // 3. Dynamic master restart without specifying ServerName (startMaster() with no args, line 191)
  // 4. Direct verification of internal registry state changes tied to master thread lifecycle
  //
  // ProcessBasedMiniHBaseCluster does support:
  // - stopMaster(ServerName) but requires knowing which master to stop
  // - Multiple masters via numMasters(int)
  //
  // However, the test's core logic depends on observing internal MasterRegistry refresh behavior
  // triggered by master failures, which requires inspecting non-client-accessible state.
  // The registry refresh mechanism itself is internal implementation detail.
  //
  // The key registry functionality (getClusterId, getActiveMaster, getMetaRegionLocations)
  // is already tested by testRegistryRPCs() above.

  @Test
  public void testDynamicMasterConfigurationRefresh_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testDynamicMasterConfigurationRefresh();
  }

  @Test
  public void testDynamicMasterConfigurationRefresh_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testDynamicMasterConfigurationRefresh();
  }

  @Test
  public void testDynamicMasterConfigurationRefresh_AFTER_STOP_MASTER() throws Exception {
    upgradeCheckpoint = "AFTER_STOP_MASTER";
    testDynamicMasterConfigurationRefresh();
  }

  /**
   * Reduced version: Tests that the list of masters configured in the MasterRegistry is
   * dynamically refreshed in the event of errors. Cannot fully replicate original test
   * due to internal access requirements.
   */
  private void testDynamicMasterConfigurationRefresh() throws Exception {
    conf = HBaseConfiguration.create();

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numMasters(3)
      .numRegionServers(3)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    String currentMasterAddrs = Preconditions.checkNotNull(conf.get(HConstants.MASTER_ADDRS_KEY));
    ServerName activeMaster = admin.getClusterMetrics().getMasterName();
    String clusterId = admin.getClusterMetrics().getClusterId();

    // Add a non-working master
    ServerName badServer = ServerName.valueOf("localhost", 1234, -1);
    Configuration testConf = new Configuration(conf);
    testConf.set(HConstants.MASTER_ADDRS_KEY, badServer.toShortString() + "," + currentMasterAddrs);
    // Set the hedging fan out so that all masters are queried.
    testConf.setInt(MasterRegistry.MASTER_REGISTRY_HEDGED_REQS_FANOUT_KEY, 4);
    // Do not limit the number of refreshes during the test run.
    testConf.setLong(MasterRegistry.MASTER_REGISTRY_MIN_SECS_BETWEEN_REFRESHES, 0);
    try (MasterRegistry registry = new MasterRegistry(testConf, User.getCurrent())) {
      final Set<ServerName> masters = registry.getParsedServers();
      assertTrue(masters.contains(badServer));
      // Make a registry RPC, this should trigger a refresh since one of the hedged RPC fails.
      assertEquals(registry.getClusterId().get(), clusterId);
      // Wait for new set of masters to be populated.
      Waiter.waitFor(testConf, 5000,
        (Waiter.Predicate<Exception>) () -> !registry.getParsedServers().equals(masters));
      // new set of masters should not include the bad server
      final Set<ServerName> newMasters = registry.getParsedServers();
      // Bad one should be out.
      assertEquals(3, newMasters.size());
      assertFalse(newMasters.contains(badServer));

      // Kill one of the backup masters (not active)
      List<ServerName> allMasters = new ArrayList<>(admin.getClusterMetrics().getBackupMasterNames());
      allMasters.add(admin.getClusterMetrics().getMasterName());
      ServerName masterToKill = null;
      for (ServerName sn : allMasters) {
        if (!sn.equals(activeMaster)) {
          masterToKill = sn;
          break;
        }
      }
      if (masterToKill != null) {
        cluster.stopMaster(masterToKill);
        checkpoint("AFTER_STOP_MASTER");

        // Wait until the killed master de-registered. This should also trigger another refresh.
        Waiter.waitFor(testConf, 10000, () -> registry.getMasters().get().size() == 2);
        Waiter.waitFor(testConf, 20000, () -> registry.getParsedServers().size() == 2);
        final Set<ServerName> newMasters2 = registry.getParsedServers();
        assertEquals(2, newMasters2.size());
        assertFalse(newMasters2.contains(masterToKill));
      }
    }
  }
}
