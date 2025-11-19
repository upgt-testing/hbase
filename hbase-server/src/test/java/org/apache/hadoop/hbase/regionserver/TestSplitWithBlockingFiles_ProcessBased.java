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
package org.apache.hadoop.hbase.regionserver;

import static org.apache.hadoop.hbase.regionserver.HRegion.SPLIT_IGNORE_BLOCKING_ENABLED_KEY;
import static org.junit.Assert.assertEquals;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
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
 * ProcessBased version of {@link TestSplitWithBlockingFiles}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Reduced version: Tests split behavior with blocking files via client APIs.
 * Internal verifications removed (getSplitPolicy(), getCompactPriority(), requestSplit())
 * as they require direct HRegion/HRegionServer access not available across process boundaries.
 * Core logic preserved: write data to create blocking files, trigger split via Admin API,
 * verify split succeeded via region count and data integrity.
 *
 * @see TestSplitWithBlockingFiles Original test using MiniHBaseCluster
 */
@Category({ MediumTests.class })
public class TestSplitWithBlockingFiles_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestSplitWithBlockingFiles_ProcessBased.class);

  private static final Logger LOG =
    LoggerFactory.getLogger(TestSplitWithBlockingFiles_ProcessBased.class);

  private static final TableName TABLE_NAME = TableName.valueOf("test");
  private static final byte[] CF = Bytes.toBytes("cf");

  @Test
  public void testSplitIgnoreBlockingFiles_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTestSplitIgnoreBlockingFiles();
  }

  @Test
  public void testSplitIgnoreBlockingFiles_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTestSplitIgnoreBlockingFiles();
  }

  @Test
  public void testSplitIgnoreBlockingFiles_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    runTestSplitIgnoreBlockingFiles();
  }

  @Test
  public void testSplitIgnoreBlockingFiles_AFTER_WRITE_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE_DATA";
    runTestSplitIgnoreBlockingFiles();
  }

  @Test
  public void testSplitIgnoreBlockingFiles_AFTER_SPLIT() throws Exception {
    upgradeCheckpoint = "AFTER_SPLIT";
    runTestSplitIgnoreBlockingFiles();
  }

  private void runTestSplitIgnoreBlockingFiles() throws Exception {
    Configuration conf = HBaseConfiguration.create();
    conf.setLong(HConstants.HREGION_MAX_FILESIZE, 8 * 2 * 10240L);
    conf.setInt(HStore.BLOCKING_STOREFILES_KEY, 1);
    conf.set(HConstants.HBASE_REGION_SPLIT_POLICY_KEY,
      ConstantSizeRegionSplitPolicy.class.getName());
    conf.setBoolean(SPLIT_IGNORE_BLOCKING_ENABLED_KEY, true);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(1).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table
    TableDescriptor td = TableDescriptorBuilder.newBuilder(TABLE_NAME)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.newBuilder(CF).setBlocksize(1000).build())
      .build();

    admin.createTable(td);
    admin.balancerSwitch(false, true);

    checkpoint("AFTER_CREATE_TABLE");

    // Write data to create blocking files condition
    byte[] value = new byte[1024];
    try (Table table = connection.getTable(TABLE_NAME)) {
      for (int m = 0; m < 10; m++) {
        String rowPrefix = "row" + m;
        for (int i = 0; i < 10; i++) {
          Put p = new Put(Bytes.toBytes(rowPrefix + i));
          p.addColumn(CF, Bytes.toBytes("qualifier"), value);
          p.addColumn(CF, Bytes.toBytes("qualifier2"), value);
          table.put(p);
        }
        admin.flush(TABLE_NAME);
      }

      // Verify data before split
      Scan scan = new Scan();
      try (ResultScanner results = table.getScanner(scan)) {
        int count = 0;
        while (results.next() != null) {
          count++;
        }
        Assert.assertEquals("There should be 100 rows!", 100, count);
      }
    }

    // Verify 1 region before split
    assertEquals(1, admin.getRegions(TABLE_NAME).size());

    // TRANSFORMATION NOTE: Internal HRegion verification removed.
    // Original test verified getSplitPolicy().getSplitPoint() (line 118),
    // getCompactPriority() >= PRIORITY_USER (line 119), and
    // requestSplit() return value (lines 120-121).
    // These require direct HRegion/HRegionServer access not available via client APIs.
    //
    // Original code:
    // List<HRegion> regions = UTIL.getMiniHBaseCluster().getRegionServer(0).getRegions();
    // regions.removeIf(r -> !r.getRegionInfo().getTable().equals(TABLE_NAME));
    // assertEquals(1, regions.size());
    // assertNotNull(regions.get(0).getSplitPolicy().getSplitPoint());
    // assertTrue(regions.get(0).getCompactPriority() >= PRIORITY_USER);
    // assertTrue(UTIL.getMiniHBaseCluster().getRegionServer(0).getCompactSplitThread()
    //   .requestSplit(regions.get(0)));

    checkpoint("AFTER_WRITE_DATA");

    // Trigger split via Admin API
    admin.balancerSwitch(true, true);

    // TRANSFORMATION NOTE: Direct procedure submission replaced with Admin API.
    // Original test created SplitTableRegionProcedure directly and submitted to
    // MasterProcedureExecutor (lines 125-132).
    // ProcessBasedMiniHBaseCluster doesn't expose MasterProcedureExecutor across
    // process boundaries. Use admin.split() instead.
    //
    // Original code:
    // MasterProcedureEnv env =
    //   UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor().getEnvironment();
    // final ProcedureExecutor<MasterProcedureEnv> executor =
    //   UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
    // SplitTableRegionProcedure splitProcedure =
    //   new SplitTableRegionProcedure(env, regions.get(0).getRegionInfo(), Bytes.toBytes("row5"));
    // executor.submitProcedure(splitProcedure);
    // ProcedureTestingUtility.waitProcedure(executor, splitProcedure.getProcId());

    admin.split(TABLE_NAME, Bytes.toBytes("row5"));

    // Wait for split to complete
    long startTime = System.currentTimeMillis();
    while (admin.getRegions(TABLE_NAME).size() < 2) {
      Thread.sleep(100);
      if (System.currentTimeMillis() - startTime > 60000) {
        throw new RuntimeException("Split did not complete within 60 seconds");
      }
    }

    checkpoint("AFTER_SPLIT");

    // Verify 2 regions after split
    assertEquals(2, admin.getRegions(TABLE_NAME).size());

    // Verify data integrity after split
    try (Table table = connection.getTable(TABLE_NAME)) {
      Scan scan = new Scan();
      try (ResultScanner results = table.getScanner(scan)) {
        int count = 0;
        while (results.next() != null) {
          count++;
        }
        Assert.assertEquals("There should be 100 rows!", 100, count);
      }
    }

    // TRANSFORMATION NOTE: Post-split internal verification removed.
    // Original test verified getCompactPriority() < PRIORITY_USER (line 145) and
    // requestSplit() returns false (line 147) for daughter regions.
    // These require direct HRegion/CompactSplitThread access.
    //
    // Original code:
    // regions = UTIL.getMiniHBaseCluster().getRegionServer(0).getRegions();
    // regions.removeIf(r -> !r.getRegionInfo().getTable().equals(TABLE_NAME));
    // assertEquals(2, regions.size());
    // for (HRegion region : regions) {
    //   assertTrue(region.getCompactPriority() < PRIORITY_USER);
    //   assertFalse(
    //     UTIL.getMiniHBaseCluster().getRegionServer(0).getCompactSplitThread().requestSplit(region));
    // }

    // Cleanup
    admin.disableTable(TABLE_NAME);
    admin.deleteTable(TABLE_NAME);
  }
}
