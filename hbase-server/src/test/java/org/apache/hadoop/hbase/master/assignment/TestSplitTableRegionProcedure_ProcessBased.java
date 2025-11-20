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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.Waiter;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestSplitTableRegionProcedure}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * This is a REDUCED transformation that tests functional split behavior via client APIs:
 * - Split operation works correctly
 * - Data is preserved across split
 * - Daughter regions are created and accessible
 *
 * The following aspects from the original test are NOT transformed (not accessible via client APIs):
 * - Procedure framework testing (rollback, recovery, failure injection)
 * - Internal procedure executor manipulation
 * - AssignmentManager metrics inspection
 * - Coprocessor with static coordination fields
 *
 * @see TestSplitTableRegionProcedure Original test using MiniHBaseCluster
 */
@Category({ MasterTests.class, MediumTests.class })
public class TestSplitTableRegionProcedure_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestSplitTableRegionProcedure_ProcessBased.class);

  private static final Logger LOG =
    LoggerFactory.getLogger(TestSplitTableRegionProcedure_ProcessBased.class);

  private static final byte[] CF1 = Bytes.toBytes("cf1");
  private static final byte[] CF2 = Bytes.toBytes("cf2");

  private static final int START_ROW_NUM = 11;
  private static final int ROW_COUNT = 60;

  /**
   * Insert test data into table.
   */
  private void insertData(TableName tableName, int rowCount, int startRowNum, byte[]... families)
    throws IOException {
    try (Table table = connection.getTable(tableName)) {
      for (int i = startRowNum; i < startRowNum + rowCount; i++) {
        byte[] row = Bytes.toBytes("" + i);
        Put put = new Put(row);
        for (byte[] family : families) {
          put.addColumn(family, Bytes.toBytes("q"), Bytes.toBytes("value"));
        }
        table.put(put);
      }
    }
  }

  /**
   * Verify data in a specific row range.
   */
  private void verifyData(TableName tableName, byte[] startKey, byte[] endKey, int expectedRows,
    byte[]... families) throws IOException {
    try (Table table = connection.getTable(tableName)) {
      Scan scan = new Scan();
      if (startKey != null && startKey.length > 0) {
        scan.withStartRow(startKey);
      }
      if (endKey != null && endKey.length > 0) {
        scan.withStopRow(endKey);
      }

      int count = 0;
      try (ResultScanner scanner = table.getScanner(scan)) {
        for (Result result : scanner) {
          count++;
          Cell[] raw = result.rawCells();
          assertEquals("Expected " + families.length + " families", families.length, result.size());
          for (int j = 0; j < families.length; j++) {
            assertTrue(CellUtil.matchingFamily(raw[j], families[j]));
          }
        }
      }
      assertEquals("Expected " + expectedRows + " rows in range", expectedRows, count);
    }
  }

  @Test(timeout = 300000)
  public void testSplitTableRegion_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    Configuration conf = HBaseConfiguration.create();
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(3).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table
    TableName tableName = TableName.valueOf("testSplitTableRegion");
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF1))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF2)).build());

    checkpoint("AFTER_CREATE_TABLE");

    // Insert data
    insertData(tableName, ROW_COUNT, START_ROW_NUM, CF1, CF2);

    checkpoint("AFTER_INSERT_DATA");

    // Flush to create store files
    admin.flush(tableName);

    checkpoint("AFTER_FLUSH");

    // Get original region
    List<RegionInfo> regions = admin.getRegions(tableName);
    assertEquals("Should have 1 region before split", 1, regions.size());
    RegionInfo originalRegion = regions.get(0);

    // Split region
    int splitRowNum = START_ROW_NUM + ROW_COUNT / 2;
    byte[] splitKey = Bytes.toBytes("" + splitRowNum);

    admin.splitRegionAsync(originalRegion.getRegionName(), splitKey).get();

    checkpoint("AFTER_SPLIT");

    // Wait for split to complete
    Waiter.waitFor(conf, 60000, () -> {
      try {
        List<RegionInfo> currentRegions = admin.getRegions(tableName);
        return currentRegions.size() == 2;
      } catch (IOException e) {
        return false;
      }
    });

    // Verify we have 2 daughter regions
    List<RegionInfo> daughterRegions = admin.getRegions(tableName);
    assertEquals("Should have 2 regions after split", 2, daughterRegions.size());

    // Verify row count
    long totalRows = 0;
    try (Table table = connection.getTable(tableName)) {
      try (ResultScanner scanner = table.getScanner(new Scan())) {
        for (Result result : scanner) {
          totalRows++;
        }
      }
    }
    assertEquals("Row count should be preserved", ROW_COUNT, totalRows);

    checkpoint("AFTER_VERIFY_SPLIT");

    // Clean up
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test(timeout = 300000)
  public void testSplitTableRegion_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    Configuration conf = HBaseConfiguration.create();
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(3).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testSplitTableRegion");
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF1))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF2)).build());

    checkpoint("AFTER_CREATE_TABLE");

    insertData(tableName, ROW_COUNT, START_ROW_NUM, CF1, CF2);
    checkpoint("AFTER_INSERT_DATA");

    admin.flush(tableName);
    checkpoint("AFTER_FLUSH");

    List<RegionInfo> regions = admin.getRegions(tableName);
    assertEquals("Should have 1 region before split", 1, regions.size());
    RegionInfo originalRegion = regions.get(0);

    int splitRowNum = START_ROW_NUM + ROW_COUNT / 2;
    byte[] splitKey = Bytes.toBytes("" + splitRowNum);

    admin.splitRegionAsync(originalRegion.getRegionName(), splitKey).get();
    checkpoint("AFTER_SPLIT");

    Waiter.waitFor(conf, 60000, () -> {
      try {
        return admin.getRegions(tableName).size() == 2;
      } catch (IOException e) {
        return false;
      }
    });

    List<RegionInfo> daughterRegions = admin.getRegions(tableName);
    assertEquals("Should have 2 regions after split", 2, daughterRegions.size());

    long totalRows = 0;
    try (Table table = connection.getTable(tableName)) {
      try (ResultScanner scanner = table.getScanner(new Scan())) {
        for (Result result : scanner) {
          totalRows++;
        }
      }
    }
    assertEquals("Row count should be preserved", ROW_COUNT, totalRows);
    checkpoint("AFTER_VERIFY_SPLIT");

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test(timeout = 300000)
  public void testSplitTableRegion_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";

    Configuration conf = HBaseConfiguration.create();
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(3).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testSplitTableRegion");
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF1))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF2)).build());

    checkpoint("AFTER_CREATE_TABLE");

    insertData(tableName, ROW_COUNT, START_ROW_NUM, CF1, CF2);
    checkpoint("AFTER_INSERT_DATA");

    admin.flush(tableName);
    checkpoint("AFTER_FLUSH");

    List<RegionInfo> regions = admin.getRegions(tableName);
    assertEquals("Should have 1 region before split", 1, regions.size());
    RegionInfo originalRegion = regions.get(0);

    int splitRowNum = START_ROW_NUM + ROW_COUNT / 2;
    byte[] splitKey = Bytes.toBytes("" + splitRowNum);

    admin.splitRegionAsync(originalRegion.getRegionName(), splitKey).get();
    checkpoint("AFTER_SPLIT");

    Waiter.waitFor(conf, 60000, () -> {
      try {
        return admin.getRegions(tableName).size() == 2;
      } catch (IOException e) {
        return false;
      }
    });

    List<RegionInfo> daughterRegions = admin.getRegions(tableName);
    assertEquals("Should have 2 regions after split", 2, daughterRegions.size());

    long totalRows = 0;
    try (Table table = connection.getTable(tableName)) {
      try (ResultScanner scanner = table.getScanner(new Scan())) {
        for (Result result : scanner) {
          totalRows++;
        }
      }
    }
    assertEquals("Row count should be preserved", ROW_COUNT, totalRows);
    checkpoint("AFTER_VERIFY_SPLIT");

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test(timeout = 300000)
  public void testSplitTableRegionNoStoreFile_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    Configuration conf = HBaseConfiguration.create();
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(3).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table but don't write data - test split on empty region
    TableName tableName = TableName.valueOf("testSplitTableRegionNoStoreFile");
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF1))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF2)).build());

    checkpoint("AFTER_CREATE_TABLE");

    List<RegionInfo> regions = admin.getRegions(tableName);
    assertEquals("Should have 1 region before split", 1, regions.size());
    RegionInfo originalRegion = regions.get(0);

    // Split region with no data
    int splitRowNum = START_ROW_NUM + ROW_COUNT / 2;
    byte[] splitKey = Bytes.toBytes("" + splitRowNum);

    admin.splitRegionAsync(originalRegion.getRegionName(), splitKey).get();

    checkpoint("AFTER_SPLIT");

    // Wait for split to complete
    Waiter.waitFor(conf, 60000, () -> {
      try {
        return admin.getRegions(tableName).size() == 2;
      } catch (IOException e) {
        return false;
      }
    });

    // Verify we have 2 daughter regions
    List<RegionInfo> daughterRegions = admin.getRegions(tableName);
    assertEquals("Should have 2 regions after split", 2, daughterRegions.size());

    checkpoint("AFTER_VERIFY_SPLIT");

    // Clean up
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test(timeout = 300000)
  public void testSplitTableRegionNoStoreFile_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    Configuration conf = HBaseConfiguration.create();
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(3).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testSplitTableRegionNoStoreFile");
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF1))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF2)).build());

    checkpoint("AFTER_CREATE_TABLE");

    List<RegionInfo> regions = admin.getRegions(tableName);
    assertEquals("Should have 1 region before split", 1, regions.size());
    RegionInfo originalRegion = regions.get(0);

    int splitRowNum = START_ROW_NUM + ROW_COUNT / 2;
    byte[] splitKey = Bytes.toBytes("" + splitRowNum);

    admin.splitRegionAsync(originalRegion.getRegionName(), splitKey).get();
    checkpoint("AFTER_SPLIT");

    Waiter.waitFor(conf, 60000, () -> {
      try {
        return admin.getRegions(tableName).size() == 2;
      } catch (IOException e) {
        return false;
      }
    });

    List<RegionInfo> daughterRegions = admin.getRegions(tableName);
    assertEquals("Should have 2 regions after split", 2, daughterRegions.size());
    checkpoint("AFTER_VERIFY_SPLIT");

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }
}
