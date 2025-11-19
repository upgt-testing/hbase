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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ClusterConnection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.conf.Configuration;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestMetaAssignmentWithStopMaster}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * @see TestMetaAssignmentWithStopMaster Original test using MiniHBaseCluster
 */
@Category({ LargeTests.class })
public class TestMetaAssignmentWithStopMaster_ProcessBased extends ProcessBasedUpgradeTestBase {

  private static final Logger LOG =
    LoggerFactory.getLogger(TestMetaAssignmentWithStopMaster_ProcessBased.class);

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestMetaAssignmentWithStopMaster_ProcessBased.class);

  private static final long WAIT_TIMEOUT = 120000;

  @Test(timeout = 240000)
  public void testStopActiveMaster_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTestStopActiveMaster();
  }

  @Test(timeout = 240000)
  public void testStopActiveMaster_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTestStopActiveMaster();
  }

  @Test(timeout = 240000)
  public void testStopActiveMaster_AFTER_META_LOCATION() throws Exception {
    upgradeCheckpoint = "AFTER_META_LOCATION";
    runTestStopActiveMaster();
  }

  @Test(timeout = 240000)
  public void testStopActiveMaster_AFTER_MASTER_FAILOVER() throws Exception {
    upgradeCheckpoint = "AFTER_MASTER_FAILOVER";
    runTestStopActiveMaster();
  }

  private void runTestStopActiveMaster() throws Exception {
    conf = HBaseConfiguration.create();

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numMasters(2)
        .numRegionServers(3)
        .build();

    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Get meta region location
    ClusterConnection clusterConn = (ClusterConnection) connection;
    ServerName oldMetaServer = clusterConn.locateRegions(TableName.META_TABLE_NAME)
        .get(0).getServerName();
    assertNotNull("Meta server should be assigned", oldMetaServer);

    // Get active master ServerName
    ServerName oldMaster = admin.getClusterMetrics().getMasterName();
    assertNotNull("Active master should exist", oldMaster);

    checkpoint("AFTER_META_LOCATION");

    // Stop active master to trigger failover
    LOG.info("Stopping active master: {}", oldMaster);
    cluster.stopMaster(oldMaster);

    // Wait for standby master to become active
    long startTime = System.currentTimeMillis();
    ServerName newMaster = null;
    while (newMaster == null || newMaster.equals(oldMaster)) {
      LOG.info("Waiting for standby master to become active");
      Thread.sleep(3000);
      try {
        newMaster = admin.getClusterMetrics().getMasterName();
      } catch (Exception e) {
        // During failover, we might get exceptions - that's okay
        LOG.debug("Exception while getting cluster metrics during failover", e);
      }
      if (System.currentTimeMillis() - startTime > WAIT_TIMEOUT) {
        throw new AssertionError("Waited too long for standby master to become active");
      }
    }
    LOG.info("New active master: {}", newMaster);
    assertNotEquals("New master should be different from old master", oldMaster, newMaster);

    // Wait for new master to be initialized and cluster to stabilize
    cluster.waitForActiveAndReadyMaster(WAIT_TIMEOUT);
    LOG.info("New master is initialized and ready");

    checkpoint("AFTER_MASTER_FAILOVER");

    // Verify meta region is still on the same server (didn't move during failover)
    ServerName newMetaServer = clusterConn.locateRegions(TableName.META_TABLE_NAME)
        .get(0).getServerName();
    assertEquals("Meta region should stay on same server during master failover",
        oldMetaServer, newMetaServer);

    LOG.info("Test completed successfully: Meta region stayed on {} during master failover",
        oldMetaServer);
  }
}
