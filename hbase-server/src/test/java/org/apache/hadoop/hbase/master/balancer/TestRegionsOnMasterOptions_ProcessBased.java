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
package org.apache.hadoop.hbase.master.balancer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
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
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.master.LoadBalancer;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestRegionsOnMasterOptions}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Test options for regions on master; none, system, or any (i.e. master is like any other
 * regionserver). Checks how regions are deployed when each of the options are enabled.
 *
 * NOTE: Regions on Master does not work well. See HBASE-19828. Disabled like original test,
 * but transformation preserved for when feature is fixed.
 *
 * @see TestRegionsOnMasterOptions Original test using MiniHBaseCluster
 */
@Ignore // Disabled like original - Regions on Master does not work well (HBASE-19828)
@Category({ MediumTests.class })
public class TestRegionsOnMasterOptions_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestRegionsOnMasterOptions_ProcessBased.class);

  private static final Logger LOG =
    LoggerFactory.getLogger(TestRegionsOnMasterOptions_ProcessBased.class);

  private static final int SLAVES = 3;
  // Make the count of REGIONS high enough so I can distinguish case where master is only carrying
  // system regions from the case where it is carrying any region; i.e. 2 system regions vs more
  // if user + system.
  private static final int REGIONS = 12;
  private static final int SYSTEM_REGIONS = 2; // ns and meta -- no acl unless enabled.

  @Test
  public void testRegionsOnAllServers_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    conf.setBoolean(LoadBalancer.TABLES_ON_MASTER, true);
    conf.setBoolean(LoadBalancer.SYSTEM_TABLES_ON_MASTER, false);
    int rsCount = (REGIONS + SYSTEM_REGIONS) / (SLAVES + 1/* Master */);
    checkBalance(rsCount, rsCount);
  }

  @Test
  public void testRegionsOnAllServers_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    conf.setBoolean(LoadBalancer.TABLES_ON_MASTER, true);
    conf.setBoolean(LoadBalancer.SYSTEM_TABLES_ON_MASTER, false);
    int rsCount = (REGIONS + SYSTEM_REGIONS) / (SLAVES + 1/* Master */);
    checkBalance(rsCount, rsCount);
  }

  @Test
  public void testNoRegionOnMaster_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    conf.setBoolean(LoadBalancer.TABLES_ON_MASTER, false);
    conf.setBoolean(LoadBalancer.SYSTEM_TABLES_ON_MASTER, false);
    int rsCount = (REGIONS + SYSTEM_REGIONS) / SLAVES;
    checkBalance(0, rsCount);
  }

  @Test
  public void testNoRegionOnMaster_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    conf.setBoolean(LoadBalancer.TABLES_ON_MASTER, false);
    conf.setBoolean(LoadBalancer.SYSTEM_TABLES_ON_MASTER, false);
    int rsCount = (REGIONS + SYSTEM_REGIONS) / SLAVES;
    checkBalance(0, rsCount);
  }

  @Ignore // Disabled like original - Fix this. The Master startup doesn't allow Master reporting
         // as a RegionServer, not until way late after the Master startup finishes. Needs more work.
  @Test
  public void testSystemTablesOnMaster_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    conf.setBoolean(LoadBalancer.TABLES_ON_MASTER, true);
    conf.setBoolean(LoadBalancer.SYSTEM_TABLES_ON_MASTER, true);
    checkBalance(SYSTEM_REGIONS, REGIONS / SLAVES);
  }

  private void checkBalance(int masterCount, int rsCount) throws Exception {
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(SLAVES)
      .build();

    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testTable_" + System.currentTimeMillis());
    try {
      // Create table with multiple regions
      byte[][] splitKeys = new byte[REGIONS - 1][];
      for (int i = 0; i < REGIONS - 1; i++) {
        splitKeys[i] = Bytes.toBytes(String.format("row_%04d", (i + 1) * 1000));
      }

      TableDescriptor tableDescriptor = TableDescriptorBuilder.newBuilder(tableName)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(HConstants.CATALOG_FAMILY))
        .build();
      admin.createTable(tableDescriptor, splitKeys);
      // Wait for table to be available
      Thread.sleep(10000);
      checkpoint("AFTER_CREATE_TABLE");

      // Get cluster metrics to identify master and region servers
      ClusterMetrics metrics = admin.getClusterMetrics(
        EnumSet.of(ClusterMetrics.Option.LIVE_SERVERS));
      ServerName masterServerName = metrics.getMasterName();
      LOG.info("Master ServerName: " + masterServerName);

      // Count regions on master using Admin.getRegions(ServerName)
      int mActualCount = admin.getRegions(masterServerName).size();
      LOG.info("Master hosting {} regions (expected around {})", mActualCount, masterCount);

      if (masterCount == 0 || masterCount == SYSTEM_REGIONS) {
        // 0 means no regions on master.
        // SYSTEM_REGIONS means only system tables on master.
        assertEquals(masterCount, mActualCount);
      } else {
        // This is master as a regionserver scenario.
        checkCount(masterCount, mActualCount);
      }
      checkpoint("AFTER_INITIAL_BALANCE_CHECK");

      // Check region counts on each region server
      for (ServerName serverName : metrics.getLiveServerMetrics().keySet()) {
        if (serverName.equals(masterServerName)) {
          continue; // Skip master, already checked
        }
        int rsActualCount = admin.getRegions(serverName).size();
        LOG.info("RegionServer {} hosting {} regions (expected around {})",
                 serverName, rsActualCount, rsCount);
        checkCount(rsActualCount, rsCount);
      }
      checkpoint("AFTER_RS_BALANCE_CHECK");

      // Restart master
      ServerName oldMasterName = masterServerName;
      cluster.stopMaster(oldMasterName);
      cluster.waitForMasterToStop(oldMasterName, 60000);
      checkpoint("AFTER_KILL_MASTER");

      // ProcessBased restarts master automatically or we can restart manually
      cluster.restartMaster(0);
      cluster.waitForActiveAndReadyMaster();
      checkpoint("AFTER_START_NEW_MASTER");

      // Wait for regions to come online after master restart
      Thread.sleep(5000);

      // Run balancer
      admin.balance();
      Thread.sleep(5000); // Wait for balance to complete
      checkpoint("AFTER_BALANCE");

      // Get new master ServerName
      metrics = admin.getClusterMetrics(EnumSet.of(ClusterMetrics.Option.LIVE_SERVERS));
      ServerName newMasterName = metrics.getMasterName();
      LOG.info("New Master ServerName: " + newMasterName);

      // Check regions on new master
      int mNewActualCount = admin.getRegions(newMasterName).size();
      LOG.info("New master hosting {} regions (expected around {})",
               mNewActualCount, masterCount);

      if (masterCount == 0 || masterCount == SYSTEM_REGIONS) {
        // 0 means no regions on master. After crash, should still be no regions on master.
        // If masterCount == SYSTEM_REGIONS, means master only carrying system regions and should
        // still only carry system regions post crash.
        assertEquals(masterCount, mNewActualCount);
      }

      // Cleanup
      admin.disableTable(tableName);
      admin.deleteTable(tableName);
    } finally {
      LOG.info("Test completed");
    }
  }

  private void checkCount(int actual, int expected) {
    assertTrue("Actual=" + actual + ", expected=" + expected,
      actual >= (expected - 2) && actual <= (expected + 2)); // Lots of slop +/- 2
  }
}
