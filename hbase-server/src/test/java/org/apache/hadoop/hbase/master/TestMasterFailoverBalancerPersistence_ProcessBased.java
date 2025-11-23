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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * ProcessBased version of {@link TestMasterFailoverBalancerPersistence}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * @see TestMasterFailoverBalancerPersistence Original test using MiniHBaseCluster
 */
@Category({ MasterTests.class, LargeTests.class })
public class TestMasterFailoverBalancerPersistence_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestMasterFailoverBalancerPersistence_ProcessBased.class);

  /**
   * Test that if the master fails, the load balancer maintains its state (running or not) when the
   * next master takes over - NO_UPGRADE variant
   */
  @Test
  public void testMasterFailoverBalancerPersistence_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTestMasterFailoverBalancerPersistence();
  }

  /**
   * Test that if the master fails, the load balancer maintains its state (running or not) when the
   * next master takes over - AFTER_CLUSTER_START variant
   */
  @Test
  public void testMasterFailoverBalancerPersistence_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTestMasterFailoverBalancerPersistence();
  }

  /**
   * Test that if the master fails, the load balancer maintains its state (running or not) when the
   * next master takes over - AFTER_FIRST_FAILOVER variant
   */
  @Test
  public void testMasterFailoverBalancerPersistence_AFTER_FIRST_FAILOVER() throws Exception {
    upgradeCheckpoint = "AFTER_FIRST_FAILOVER";
    runTestMasterFailoverBalancerPersistence();
  }

  /**
   * Test that if the master fails, the load balancer maintains its state (running or not) when the
   * next master takes over - AFTER_BALANCER_OFF variant
   */
  @Test
  public void testMasterFailoverBalancerPersistence_AFTER_BALANCER_OFF() throws Exception {
    upgradeCheckpoint = "AFTER_BALANCER_OFF";
    runTestMasterFailoverBalancerPersistence();
  }

  private void runTestMasterFailoverBalancerPersistence() throws Exception {
    // Start the cluster with 3 masters
    // Use conf from base class - it has correct dynamic ZK port configuration
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numMasters(3)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitForActiveAndReadyMaster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // check that the balancer is on by default for the active master
    ClusterMetrics clusterStatus = admin.getClusterMetrics();
    assertTrue("Balancer should be on by default", clusterStatus.getBalancerOn());

    // Kill active master and wait for new active master
    killActiveAndWaitForNewActive();
    checkpoint("AFTER_FIRST_FAILOVER");

    // ensure the load balancer is still running on new master
    clusterStatus = admin.getClusterMetrics();
    assertTrue("Balancer should still be on after failover", clusterStatus.getBalancerOn());

    // turn off the load balancer
    admin.balancerSwitch(false, false);
    checkpoint("AFTER_BALANCER_OFF");

    // once more, kill active master and wait for new active master to show up
    killActiveAndWaitForNewActive();

    // ensure the load balancer is not running on the new master
    clusterStatus = admin.getClusterMetrics();
    assertFalse("Balancer should be off after failover", clusterStatus.getBalancerOn());
  }

  /**
   * Kill the active master and wait for a new active master to show up
   */
  private void killActiveAndWaitForNewActive() throws Exception {
    // Get current active master's ServerName
    ServerName activeMaster = admin.getClusterMetrics().getMasterName();

    // Stop the active master
    cluster.stopMaster(activeMaster);

    // Wait for a new active master
    cluster.waitForActiveAndReadyMaster();

    // Verify we have a new master
    ServerName newActiveMaster = admin.getClusterMetrics().getMasterName();
    assertFalse("Should have a new active master",
                activeMaster.equals(newActiveMaster));
  }
}
