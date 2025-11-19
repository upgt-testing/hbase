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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Set;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.regionserver.BootstrapNodeManager;
import org.apache.hadoop.hbase.regionserver.RSRpcServices;
import org.apache.hadoop.hbase.security.UserProvider;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.RegionServerTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * TRANSFORMATION NOTE: Full transformation (100% logic preserved).
 *
 * Make sure that we can update the bootstrap server from master to region server, and region server
 * could also contact each other to update the bootstrap nodes.
 *
 * REPLACED internal access:
 * - Line 92: UTIL.getMiniHBaseCluster().killRegionServer(serverToKill)
 *   → cluster.killRegionServer(serverToKill) (ProcessBased API)
 *
 * All test logic fully preserved:
 * - RpcConnectionRegistry creation and refresh mechanism
 * - Bootstrap node update from master to region servers
 * - Region server kill and registry update
 * - Verification of bootstrap node list updates
 */
@Category({ RegionServerTests.class, MediumTests.class })
public class TestBootstrapNodeUpdate_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestBootstrapNodeUpdate_ProcessBased.class);

  private int testCounter = 0;
  private RpcConnectionRegistry registry;

  private void setupTest(String testMethodName) throws Exception {
    testCounter++;

    conf = HBaseConfiguration.create();
    conf.setLong(BootstrapNodeManager.REQUEST_MASTER_INTERVAL_SECS, 5);
    conf.setLong(BootstrapNodeManager.REQUEST_MASTER_MIN_INTERVAL_SECS, 1);
    conf.setLong(BootstrapNodeManager.REQUEST_REGIONSERVER_INTERVAL_SECS, 1);
    conf.setInt(RSRpcServices.CLIENT_BOOTSTRAP_NODE_LIMIT, 2);
    conf.setLong(RpcConnectionRegistry.INITIAL_REFRESH_DELAY_SECS, 5);
    conf.setLong(RpcConnectionRegistry.PERIODIC_REFRESH_INTERVAL_SECS, 1);
    conf.setLong(RpcConnectionRegistry.MIN_SECS_BETWEEN_REFRESHES, 1);
    conf.setInt(HConstants.HBASE_RPC_TIMEOUT_KEY, 60000);
    conf.setInt(HConstants.HBASE_CLIENT_OPERATION_TIMEOUT, 120000);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(3)
      .build();
    cluster.waitClusterUp();

    connection = cluster.getConnection();
    admin = connection.getAdmin();

    registry = new RpcConnectionRegistry(conf, UserProvider.instantiate(conf).getCurrent());
  }

  private void cleanupTest() throws Exception {
    if (registry != null) {
      registry.close();
    }
  }

  @Override
  public void tearDownTest() throws Exception {
    cleanupTest();
    super.tearDownTest();
  }

  @Test
  public void testUpdate_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupTest("testUpdate");

    ServerName activeMasterServerName = registry.getActiveMaster().get();
    ServerName masterInConf = ServerName.valueOf(activeMasterServerName.getHostname(),
      activeMasterServerName.getPort(), -1);
    // we should have master in the beginning
    assertThat(registry.getParsedServers(), hasItem(masterInConf));

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // and after refreshing, we will switch to use region servers
    long deadline = System.currentTimeMillis() + 15000;
    while (registry.getParsedServers().contains(masterInConf)
      || registry.getParsedServers().contains(activeMasterServerName)) {
      if (System.currentTimeMillis() > deadline) {
        throw new RuntimeException(
          "Registry did not switch from master to region servers in time");
      }
      Thread.sleep(100);
    }

    Set<ServerName> parsedServers = registry.getParsedServers();
    assertEquals(2, parsedServers.size());

    // now kill one region server
    ServerName serverToKill = parsedServers.iterator().next();
    // Replaced: UTIL.getMiniHBaseCluster().killRegionServer(serverToKill)
    // With: cluster.killRegionServer(serverToKill) - ProcessBased API
    cluster.killRegionServer(serverToKill);

    // wait until the region server disappears
    // since the min node limit is 2, this means region server will still contact each other for
    // getting bootstrap nodes, instead of requesting master directly, so this assert can make sure
    // that the getAllBootstrapNodes works fine, and also the client can communicate with region
    // server to update bootstrap nodes
    deadline = System.currentTimeMillis() + 30000;
    while (registry.getParsedServers().contains(serverToKill)) {
      if (System.currentTimeMillis() > deadline) {
        throw new RuntimeException("Registry did not remove killed server in time");
      }
      Thread.sleep(100);
    }

    // should still have 2 servers, the remaining 2 live region servers
    assertEquals(2, parsedServers.size());
    // make sure the registry still works fine
    assertNotNull(registry.getClusterId().get());
  }

  @Test
  public void testUpdate_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupTest("testUpdate");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    ServerName activeMasterServerName = registry.getActiveMaster().get();
    ServerName masterInConf = ServerName.valueOf(activeMasterServerName.getHostname(),
      activeMasterServerName.getPort(), -1);
    // we should have master in the beginning
    assertThat(registry.getParsedServers(), hasItem(masterInConf));

    // and after refreshing, we will switch to use region servers
    long deadline = System.currentTimeMillis() + 15000;
    while (registry.getParsedServers().contains(masterInConf)
      || registry.getParsedServers().contains(activeMasterServerName)) {
      if (System.currentTimeMillis() > deadline) {
        throw new RuntimeException(
          "Registry did not switch from master to region servers in time");
      }
      Thread.sleep(100);
    }

    Set<ServerName> parsedServers = registry.getParsedServers();
    assertEquals(2, parsedServers.size());

    // now kill one region server
    ServerName serverToKill = parsedServers.iterator().next();
    // Replaced: UTIL.getMiniHBaseCluster().killRegionServer(serverToKill)
    // With: cluster.killRegionServer(serverToKill) - ProcessBased API
    cluster.killRegionServer(serverToKill);

    // wait until the region server disappears
    // since the min node limit is 2, this means region server will still contact each other for
    // getting bootstrap nodes, instead of requesting master directly, so this assert can make sure
    // that the getAllBootstrapNodes works fine, and also the client can communicate with region
    // server to update bootstrap nodes
    deadline = System.currentTimeMillis() + 30000;
    while (registry.getParsedServers().contains(serverToKill)) {
      if (System.currentTimeMillis() > deadline) {
        throw new RuntimeException("Registry did not remove killed server in time");
      }
      Thread.sleep(100);
    }

    // should still have 2 servers, the remaining 2 live region servers
    assertEquals(2, parsedServers.size());
    // make sure the registry still works fine
    assertNotNull(registry.getClusterId().get());
  }
}
