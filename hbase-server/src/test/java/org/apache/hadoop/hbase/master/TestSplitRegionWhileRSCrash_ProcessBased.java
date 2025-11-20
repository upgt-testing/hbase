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

import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestSplitRegionWhileRSCrash}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Tests split procedure recovery when RS crashes mid-split.
 *
 * @see TestSplitRegionWhileRSCrash Original test using MiniHBaseCluster
 */
@Category({ MasterTests.class, MediumTests.class })
public class TestSplitRegionWhileRSCrash_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestSplitRegionWhileRSCrash_ProcessBased.class);

  private static final Logger LOG =
    LoggerFactory.getLogger(TestSplitRegionWhileRSCrash_ProcessBased.class);

  private static final TableName TABLE_NAME = TableName.valueOf("test");
  private static final byte[] CF = Bytes.toBytes("cf");

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
  public void test_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    runTest();
  }

  @Test
  public void test_AFTER_WRITE_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE_DATA";
    runTest();
  }

  @Test
  public void test_AFTER_SPLIT_INITIATE() throws Exception {
    upgradeCheckpoint = "AFTER_SPLIT_INITIATE";
    runTest();
  }

  @Test
  public void test_AFTER_RS_CRASH() throws Exception {
    upgradeCheckpoint = "AFTER_RS_CRASH";
    runTest();
  }

  private void runTest() throws Exception {
    Configuration conf = HBaseConfiguration.create();

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(2).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table
    TableDescriptor td = TableDescriptorBuilder.newBuilder(TABLE_NAME)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF)).build();
    admin.createTable(td);

    checkpoint("AFTER_CREATE_TABLE");

    // Write data
    LOG.info("Begin to put data");
    try (Table table = connection.getTable(TABLE_NAME)) {
      for (int i = 0; i < 10; i++) {
        byte[] row = Bytes.toBytes("row" + i);
        Put put = new Put(row);
        put.addColumn(CF, CF, CF);
        table.put(put);
      }
    }

    checkpoint("AFTER_WRITE_DATA");

    // Get the region to split
    List<RegionInfo> regionInfos = admin.getRegions(TABLE_NAME);
    Assert.assertEquals("Should have exactly 1 region before split", 1, regionInfos.size());
    RegionInfo regionToSplit = regionInfos.get(0);

    // Initiate split asynchronously
    LOG.info("Initiating split on region: {}", regionToSplit.getRegionNameAsString());
    admin.splitRegionAsync(regionToSplit.getRegionName(), Bytes.toBytes("row5"));

    checkpoint("AFTER_SPLIT_INITIATE");

    // Wait a moment for split to start (give it time to begin the procedure)
    Thread.sleep(2000);

    // Find RS hosting the table regions
    int rsIndexToKill = -1;
    for (int i = 0; i < 2; i++) {
      ServerName sn = cluster.getRegionServerName(i);
      List<RegionInfo> regions = admin.getRegions(sn);
      boolean hasTableRegion = false;
      for (RegionInfo ri : regions) {
        if (TABLE_NAME.equals(ri.getTable())) {
          hasTableRegion = true;
          break;
        }
      }
      if (hasTableRegion) {
        rsIndexToKill = i;
        break;
      }
    }

    Assert.assertTrue("Should find RS hosting table", rsIndexToKill >= 0);
    ServerName rsToKill = cluster.getRegionServerName(rsIndexToKill);
    LOG.info("Killing RegionServer {}: {}", rsIndexToKill, rsToKill);
    cluster.killRegionServer(rsToKill);

    checkpoint("AFTER_RS_CRASH");

    // Restart the RS to help recovery
    LOG.info("Restarting RegionServer {}", rsIndexToKill);
    cluster.restartRegionServer(rsIndexToKill);

    // Wait for split to complete and all regions to be online
    LOG.info("Waiting for split to complete");
    waitForRegionsToSettle(admin, TABLE_NAME, 2, 60000);

    // Verify data integrity
    LOG.info("Verifying data integrity");
    try (Table table = connection.getTable(TABLE_NAME)) {
      Scan scan = new Scan();
      ResultScanner results = table.getScanner(scan);
      int count = 0;
      Result result;
      while ((result = results.next()) != null) {
        count++;
      }
      results.close();
      Assert.assertEquals("There should be 10 rows!", 10, count);
    }

    LOG.info("Test completed successfully");
  }

  /**
   * Wait for regions to settle to expected count.
   */
  private void waitForRegionsToSettle(Admin admin, TableName tableName, int expectedRegionCount,
    long timeout) throws Exception {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < timeout) {
      List<RegionInfo> regions = admin.getRegions(tableName);
      if (regions.size() == expectedRegionCount) {
        // Also check all regions are online (have locations)
        boolean allOnline = true;
        for (RegionInfo ri : regions) {
          try {
            // Try to get region location - if it throws, region is not online
            connection.getRegionLocator(tableName).getRegionLocation(ri.getStartKey());
          } catch (Exception e) {
            allOnline = false;
            break;
          }
        }
        if (allOnline) {
          LOG.info("All {} regions are online and settled", expectedRegionCount);
          return;
        }
      }
      Thread.sleep(500);
    }
    throw new AssertionError("Regions did not settle to " + expectedRegionCount + " within "
      + timeout + "ms. Current count: " + admin.getRegions(tableName).size());
  }
}
