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

import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestMasterRestartAfterDisablingTable}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * @see TestMasterRestartAfterDisablingTable Original test using MiniHBaseCluster
 */
@Category({ MasterTests.class, LargeTests.class })
public class TestMasterRestartAfterDisablingTable_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestMasterRestartAfterDisablingTable_ProcessBased.class);

  private static final Logger LOG =
    LoggerFactory.getLogger(TestMasterRestartAfterDisablingTable_ProcessBased.class);

  @Rule
  public TestName name = new TestName();

  @Test
  public void testForCheckingIfEnableAndDisableWorksFineAfterSwitch_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTest();
  }

  @Test
  public void testForCheckingIfEnableAndDisableWorksFineAfterSwitch_AFTER_CLUSTER_START()
      throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTest();
  }

  @Test
  public void testForCheckingIfEnableAndDisableWorksFineAfterSwitch_AFTER_DISABLE()
      throws Exception {
    upgradeCheckpoint = "AFTER_DISABLE";
    runTest();
  }

  @Test
  public void testForCheckingIfEnableAndDisableWorksFineAfterSwitch_AFTER_MASTER_FAILOVER()
      throws Exception {
    upgradeCheckpoint = "AFTER_MASTER_FAILOVER";
    runTest();
  }

  private void runTest() throws Exception {
    final int NUM_MASTERS = 2;
    final int NUM_REGIONS_TO_CREATE = 4;

    // Start the cluster
    log("Starting cluster");
    cluster = new ProcessBasedMiniHBaseCluster.Builder(HBaseConfiguration.create())
      .numMasters(NUM_MASTERS)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    log("Waiting for active/ready master");
    cluster.waitForActiveAndReadyMaster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with regions
    final TableName tableName = TableName.valueOf(name.getMethodName());
    byte[] family = Bytes.toBytes("family");
    log("Creating table with " + NUM_REGIONS_TO_CREATE + " regions");

    byte[][] splitKeys = new byte[NUM_REGIONS_TO_CREATE - 1][];
    for (int i = 0; i < NUM_REGIONS_TO_CREATE - 1; i++) {
      splitKeys[i] = Bytes.toBytes(String.format("row_%04d", (i + 1) * 100));
    }

    admin.createTable(
      TableDescriptorBuilder.newBuilder(tableName)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(family))
        .build(),
      splitKeys);

    // Verify table is enabled
    assertTrue("Table should be enabled after creation", admin.isTableEnabled(tableName));

    log("Disabling table");
    admin.disableTable(tableName);

    // Verify table is disabled
    assertTrue("Table should be disabled", admin.isTableDisabled(tableName));
    checkpoint("AFTER_DISABLE");

    // Get current active master and stop it to trigger failover
    ServerName activeMasterName = admin.getClusterMetrics().getMasterName();
    log("Stopping active master: " + activeMasterName);
    cluster.stopMaster(activeMasterName);

    log("Waiting for new active master");
    cluster.waitForActiveAndReadyMaster();

    // Verify we have a new master
    ServerName newActiveMasterName = admin.getClusterMetrics().getMasterName();
    assertFalse("Should have a new active master after failover",
                activeMasterName.equals(newActiveMasterName));

    // Verify table state is still DISABLED after master failover
    assertTrue("The table should not be in enabled state after failover",
               admin.isTableDisabled(tableName));
    checkpoint("AFTER_MASTER_FAILOVER");

    log("Enabling table");
    admin.enableTable(tableName);

    // Verify table is enabled
    assertTrue("The table should be in enabled state after enable",
               admin.isTableEnabled(tableName));

    // Verify we can access the table
    int regionCount = admin.getRegions(tableName).size();
    assertEquals("Should have expected number of regions",
                 NUM_REGIONS_TO_CREATE, regionCount);

    log("Test completed successfully");
  }

  private void log(String msg) {
    LOG.debug("\n\nTRR: " + msg + "\n");
  }
}
