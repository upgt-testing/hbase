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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.EnumSet;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.ClientTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * ProcessBased version of {@link TestAsyncAdminMasterSwitch}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Tests async admin API resilience during master failover.
 *
 * @see TestAsyncAdminMasterSwitch Original test using MiniHBaseCluster
 */
@Category({ MediumTests.class, ClientTests.class })
public class TestAsyncAdminMasterSwitch_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestAsyncAdminMasterSwitch_ProcessBased.class);

  private AsyncConnection asyncConn;
  private AsyncAdmin asyncAdmin;

  @Test
  public void testSwitch_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    // Start cluster with multiple masters
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numMasters(2).numRegionServers(3)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    asyncConn = ConnectionFactory.createAsyncConnection(conf).get();
    asyncAdmin = asyncConn.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Verify we can get cluster metrics via async admin
    int numServers = asyncAdmin
      .getClusterMetrics(EnumSet.of(ClusterMetrics.Option.SERVERS_NAME)).get().getServersName()
      .size();
    assertEquals(3, numServers);

    checkpoint("AFTER_INITIAL_METRICS");

    // Stop the active master to trigger failover
    ServerName activeMaster = admin.getClusterMetrics().getMasterName();
    cluster.stopMaster(activeMaster);

    checkpoint("AFTER_MASTER_STOP");

    // Wait for new active master
    assertTrue(cluster.waitForActiveAndReadyMaster(30000));

    checkpoint("AFTER_MASTER_FAILOVER");

    // Verify async admin still works after master switch
    numServers = asyncAdmin
      .getClusterMetrics(EnumSet.of(ClusterMetrics.Option.SERVERS_NAME)).get().getServersName()
      .size();
    assertEquals(3, numServers);

    checkpoint("AFTER_FINAL_VERIFICATION");

    asyncConn.close();
  }

  @Test
  public void testSwitch_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numMasters(2).numRegionServers(3)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    asyncConn = ConnectionFactory.createAsyncConnection(conf).get();
    asyncAdmin = asyncConn.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    int numServers = asyncAdmin
      .getClusterMetrics(EnumSet.of(ClusterMetrics.Option.SERVERS_NAME)).get().getServersName()
      .size();
    assertEquals(3, numServers);

    checkpoint("AFTER_INITIAL_METRICS");

    ServerName activeMaster = admin.getClusterMetrics().getMasterName();
    cluster.stopMaster(activeMaster);

    checkpoint("AFTER_MASTER_STOP");

    assertTrue(cluster.waitForActiveAndReadyMaster(30000));

    checkpoint("AFTER_MASTER_FAILOVER");

    numServers = asyncAdmin
      .getClusterMetrics(EnumSet.of(ClusterMetrics.Option.SERVERS_NAME)).get().getServersName()
      .size();
    assertEquals(3, numServers);

    checkpoint("AFTER_FINAL_VERIFICATION");

    asyncConn.close();
  }

  @Test
  public void testSwitch_AFTER_INITIAL_METRICS() throws Exception {
    upgradeCheckpoint = "AFTER_INITIAL_METRICS";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numMasters(2).numRegionServers(3)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    asyncConn = ConnectionFactory.createAsyncConnection(conf).get();
    asyncAdmin = asyncConn.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    int numServers = asyncAdmin
      .getClusterMetrics(EnumSet.of(ClusterMetrics.Option.SERVERS_NAME)).get().getServersName()
      .size();
    assertEquals(3, numServers);

    checkpoint("AFTER_INITIAL_METRICS");

    ServerName activeMaster = admin.getClusterMetrics().getMasterName();
    cluster.stopMaster(activeMaster);

    checkpoint("AFTER_MASTER_STOP");

    assertTrue(cluster.waitForActiveAndReadyMaster(30000));

    checkpoint("AFTER_MASTER_FAILOVER");

    numServers = asyncAdmin
      .getClusterMetrics(EnumSet.of(ClusterMetrics.Option.SERVERS_NAME)).get().getServersName()
      .size();
    assertEquals(3, numServers);

    checkpoint("AFTER_FINAL_VERIFICATION");

    asyncConn.close();
  }

  @Test
  public void testSwitch_AFTER_MASTER_STOP() throws Exception {
    upgradeCheckpoint = "AFTER_MASTER_STOP";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numMasters(2).numRegionServers(3)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    asyncConn = ConnectionFactory.createAsyncConnection(conf).get();
    asyncAdmin = asyncConn.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    int numServers = asyncAdmin
      .getClusterMetrics(EnumSet.of(ClusterMetrics.Option.SERVERS_NAME)).get().getServersName()
      .size();
    assertEquals(3, numServers);

    checkpoint("AFTER_INITIAL_METRICS");

    ServerName activeMaster = admin.getClusterMetrics().getMasterName();
    cluster.stopMaster(activeMaster);

    checkpoint("AFTER_MASTER_STOP");

    assertTrue(cluster.waitForActiveAndReadyMaster(30000));

    checkpoint("AFTER_MASTER_FAILOVER");

    numServers = asyncAdmin
      .getClusterMetrics(EnumSet.of(ClusterMetrics.Option.SERVERS_NAME)).get().getServersName()
      .size();
    assertEquals(3, numServers);

    checkpoint("AFTER_FINAL_VERIFICATION");

    asyncConn.close();
  }

  @Test
  public void testSwitch_AFTER_MASTER_FAILOVER() throws Exception {
    upgradeCheckpoint = "AFTER_MASTER_FAILOVER";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numMasters(2).numRegionServers(3)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    asyncConn = ConnectionFactory.createAsyncConnection(conf).get();
    asyncAdmin = asyncConn.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    int numServers = asyncAdmin
      .getClusterMetrics(EnumSet.of(ClusterMetrics.Option.SERVERS_NAME)).get().getServersName()
      .size();
    assertEquals(3, numServers);

    checkpoint("AFTER_INITIAL_METRICS");

    ServerName activeMaster = admin.getClusterMetrics().getMasterName();
    cluster.stopMaster(activeMaster);

    checkpoint("AFTER_MASTER_STOP");

    assertTrue(cluster.waitForActiveAndReadyMaster(30000));

    checkpoint("AFTER_MASTER_FAILOVER");

    numServers = asyncAdmin
      .getClusterMetrics(EnumSet.of(ClusterMetrics.Option.SERVERS_NAME)).get().getServersName()
      .size();
    assertEquals(3, numServers);

    checkpoint("AFTER_FINAL_VERIFICATION");

    asyncConn.close();
  }
}
