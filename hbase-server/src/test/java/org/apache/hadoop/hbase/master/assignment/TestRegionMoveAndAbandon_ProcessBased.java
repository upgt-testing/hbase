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
package org.apache.hadoop.hbase.master.assignment;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.Waiter;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
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
 * ProcessBased version of {@link TestRegionMoveAndAbandon}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * @see TestRegionMoveAndAbandon Original test using MiniHBaseCluster
 */
@Category({ MediumTests.class, MasterTests.class })
public class TestRegionMoveAndAbandon_ProcessBased extends ProcessBasedUpgradeTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(TestRegionMoveAndAbandon_ProcessBased.class);

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestRegionMoveAndAbandon_ProcessBased.class);

  @Rule
  public TestName name = new TestName();

  @Test
  public void test_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTest();
  }

  @Test
  public void test_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTest();
  }

  @Test
  public void test_AFTER_FIRST_MOVE() throws Exception {
    upgradeCheckpoint = "AFTER_FIRST_MOVE";
    runTest();
  }

  @Test
  public void test_AFTER_SECOND_MOVE() throws Exception {
    upgradeCheckpoint = "AFTER_SECOND_MOVE";
    runTest();
  }

  @Test
  public void test_AFTER_KILL_RS1() throws Exception {
    upgradeCheckpoint = "AFTER_KILL_RS1";
    runTest();
  }

  @Test
  public void test_AFTER_MASTER_RESTART() throws Exception {
    upgradeCheckpoint = "AFTER_MASTER_RESTART";
    runTest();
  }

  private void runTest() throws Exception {
    conf = HBaseConfiguration.create();
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(2)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Wait for namespace table to be available
    Waiter.waitFor(conf, 30000, () -> admin.tableExists(TableName.NAMESPACE_TABLE_NAME));
    Waiter.waitFor(conf, 30000, () -> admin.isTableAvailable(TableName.NAMESPACE_TABLE_NAME));

    // Get initial server names
    ClusterMetrics metrics = admin.getClusterMetrics();
    List<ServerName> serverNames = new java.util.ArrayList<>(metrics.getLiveServerMetrics().keySet());
    assertEquals(2, serverNames.size());
    ServerName rs1ServerName = serverNames.get(0);
    ServerName rs2ServerName = serverNames.get(1);

    // Get the namespace table region
    List<RegionInfo> regions = admin.getRegions(TableName.NAMESPACE_TABLE_NAME);
    assertEquals(1, regions.size());
    RegionInfo regionInfo = regions.get(0);

    LOG.info("Moving {} to {}", regionInfo, rs2ServerName);
    // Move to RS2
    admin.move(regionInfo.getEncodedNameAsBytes(), rs2ServerName);
    Waiter.waitFor(conf, 30000, () -> {
      List<RegionInfo> currentRegions = admin.getRegions(TableName.NAMESPACE_TABLE_NAME);
      return !currentRegions.isEmpty();
    });
    checkpoint("AFTER_FIRST_MOVE");

    LOG.info("Moving {} to {}", regionInfo, rs1ServerName);
    // Move to RS1
    admin.move(regionInfo.getEncodedNameAsBytes(), rs1ServerName);
    Waiter.waitFor(conf, 30000, () -> {
      List<RegionInfo> currentRegions = admin.getRegions(TableName.NAMESPACE_TABLE_NAME);
      return !currentRegions.isEmpty();
    });
    checkpoint("AFTER_SECOND_MOVE");

    LOG.info("Killing RS {}", rs1ServerName);
    // Stop RS1
    cluster.killRegionServer(rs1ServerName);
    Waiter.waitFor(conf, 30000, () -> {
      ClusterMetrics m = admin.getClusterMetrics();
      return !m.getLiveServerMetrics().containsKey(rs1ServerName);
    });

    // Region should get moved to RS2
    Waiter.waitFor(conf, 60000, () -> admin.isTableAvailable(TableName.NAMESPACE_TABLE_NAME));
    checkpoint("AFTER_KILL_RS1");

    // Restart the master
    ServerName masterName = admin.getClusterMetrics().getMasterName();
    LOG.info("Killing master {}", masterName);
    cluster.killMaster(masterName);

    // Stop RS2
    LOG.info("Killing RS {}", rs2ServerName);
    cluster.killRegionServer(rs2ServerName);

    // Wait for both RSs to be gone
    Waiter.waitFor(conf, 30000, () -> {
      ClusterMetrics m = admin.getClusterMetrics();
      return !m.getLiveServerMetrics().containsKey(rs1ServerName) &&
             !m.getLiveServerMetrics().containsKey(rs2ServerName);
    });

    // Start up master again
    LOG.info("Restarting master");
    cluster.restartMaster(0);
    cluster.waitForActiveAndReadyMaster(60000);

    // Restart the region servers
    LOG.info("Restarting region servers");
    cluster.restartRegionServer(0);
    cluster.restartRegionServer(1);

    // Wait for 2 RSs to be available
    Waiter.waitFor(conf, 60000, () -> {
      ClusterMetrics m = admin.getClusterMetrics();
      return m.getLiveServerMetrics().size() >= 2;
    });
    checkpoint("AFTER_MASTER_RESTART");

    // Verify we can access the namespace table
    Waiter.waitFor(conf, 60000, new Waiter.Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        try (Table nsTable = connection.getTable(TableName.NAMESPACE_TABLE_NAME)) {
          // Doesn't matter what we're getting. We just want to make sure we can access the region
          nsTable.get(new Get(Bytes.toBytes("a")));
          return true;
        } catch (Exception e) {
          LOG.debug("Not yet accessible: {}", e.getMessage());
          return false;
        }
      }
    });

    LOG.info("Test completed successfully");
  }
}
