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
package org.apache.hadoop.hbase;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.MiscTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.com.google.common.collect.Iterables;

/**
 * ProcessBased version of {@link TestMultiVersions}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable process-based
 * testing and multi-version upgrade scenarios.
 *
 * <p>
 * Tests user specifiable time stamps putting, getting and scanning across HBase versions. Also
 * tests data survival across upgrades.
 *
 * @see TestMultiVersions Original test using MiniHBaseCluster
 */
@Category({ MiscTests.class, MediumTests.class })
public class TestMultiVersions_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestMultiVersions_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestMultiVersions_ProcessBased.class);

  private static final int NUM_SLAVES = 3;

  @Rule
  public TestName name = new TestName();

  /**
   * Tests five cases of scans and timestamps across potential upgrades.
   * <p>
   * Port of old TestScanMultipleVersions test here so can better utilize the spun up cluster
   * running more than just a single test. Keep old tests crazyness.
   */
  @Test(timeout = 300000)
  public void testScanMultipleVersions_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    // Start the cluster
    LOG.info("Starting cluster with {} region servers", NUM_SLAVES);
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(NUM_SLAVES).build();
    cluster.waitClusterUp();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    final byte[] family = HConstants.CATALOG_FAMILY;
    final byte[][] rows = new byte[][] { Bytes.toBytes("row_0200"), Bytes.toBytes("row_0800") };
    final byte[][] splitRows = new byte[][] { Bytes.toBytes("row_0500") };
    final long[] timestamp = new long[] { 100L, 1000L };

    // Create table with split
    TableDescriptor td = TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(family)).build();
    admin.createTable(td, splitRows);
    LOG.info("Created table {} with split at {}", tableName, Bytes.toString(splitRows[0]));

    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(tableName)) {
      // Assert we got the region layout wanted.
      Pair<byte[][], byte[][]> keys =
        connection.getRegionLocator(tableName).getStartEndKeys();
      assertEquals(2, keys.getFirst().length);
      byte[][] startKeys = keys.getFirst();
      byte[][] endKeys = keys.getSecond();

      for (int i = 0; i < startKeys.length; i++) {
        if (i == 0) {
          assertArrayEquals(HConstants.EMPTY_START_ROW, startKeys[i]);
          assertArrayEquals(endKeys[i], splitRows[0]);
        } else if (i == 1) {
          assertArrayEquals(splitRows[0], startKeys[i]);
          assertArrayEquals(endKeys[i], HConstants.EMPTY_END_ROW);
        }
      }

      // Insert data
      List<Put> puts = new ArrayList<>();
      for (int i = 0; i < startKeys.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Put put = new Put(rows[i], timestamp[j]);
          put.addColumn(family, null, timestamp[j], Bytes.toBytes(timestamp[j]));
          puts.add(put);
        }
      }
      table.put(puts);
      LOG.info("Inserted {} puts into table", puts.size());
    }

    checkpoint("AFTER_INSERT_DATA");

    // Verify data can be read - must reopen table after checkpoint
    try (Table table = connection.getTable(tableName)) {
      // Verify individual gets work
      for (int i = 0; i < rows.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Get get = new Get(rows[i]);
          get.addFamily(family);
          get.setTimestamp(timestamp[j]);
          Result result = table.get(get);
          int cellCount = result.rawCells().length;
          assertEquals(1, cellCount);
        }
      }

      // Case 1: scan with LATEST_TIMESTAMP. Should get two rows
      int count;
      Scan scan = new Scan();
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 1: LATEST_TIMESTAMP scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_1");

    try (Table table = connection.getTable(tableName)) {
      // Case 2: Scan with a timestamp greater than most recent timestamp
      // (in this case > 1000 and < LATEST_TIMESTAMP. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(1000L, Long.MAX_VALUE);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 2: TimeRange(1000, MAX) scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_2");

    try (Table table = connection.getTable(tableName)) {
      // Case 3: scan with timestamp equal to most recent timestamp
      // (in this case == 1000. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimestamp(1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 3: Timestamp=1000 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_3");

    try (Table table = connection.getTable(tableName)) {
      // Case 4: scan with timestamp greater than first timestamp but less than
      // second timestamp (100 < timestamp < 1000). Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(100L, 1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 4: TimeRange(100, 1000) scan found {} rows", count);

      // Case 5: scan with timestamp equal to first timestamp (100)
      // Should get 2 rows.
      scan = new Scan();
      scan.setTimestamp(100L);
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 5: Timestamp=100 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_4");

    // Final verification after all operations
    try (Table table = connection.getTable(tableName)) {
      // Verify all data still accessible
      Scan scan = new Scan();
      scan.addFamily(family);
      scan.setMaxVersions();
      int totalRows = 0;
      try (ResultScanner s = table.getScanner(scan)) {
        for (Result r : s) {
          totalRows++;
          // Each row should have 2 versions
          assertEquals("Each row should have 2 versions", 2, r.rawCells().length);
        }
      }
      assertEquals("Total rows should be 2", 2, totalRows);
      LOG.info("Final verification: {} rows with all versions intact", totalRows);
    }

    checkpoint("AFTER_ALL_SCANS");

    LOG.info("Test completed successfully for checkpoint: {}", upgradeCheckpoint);
  }

  @Test(timeout = 300000)
  public void testScanMultipleVersions_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    // Start the cluster
    LOG.info("Starting cluster with {} region servers", NUM_SLAVES);
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(NUM_SLAVES).build();
    cluster.waitClusterUp();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    final byte[] family = HConstants.CATALOG_FAMILY;
    final byte[][] rows = new byte[][] { Bytes.toBytes("row_0200"), Bytes.toBytes("row_0800") };
    final byte[][] splitRows = new byte[][] { Bytes.toBytes("row_0500") };
    final long[] timestamp = new long[] { 100L, 1000L };

    // Create table with split
    TableDescriptor td = TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(family)).build();
    admin.createTable(td, splitRows);
    LOG.info("Created table {} with split at {}", tableName, Bytes.toString(splitRows[0]));

    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(tableName)) {
      // Assert we got the region layout wanted.
      Pair<byte[][], byte[][]> keys =
        connection.getRegionLocator(tableName).getStartEndKeys();
      assertEquals(2, keys.getFirst().length);
      byte[][] startKeys = keys.getFirst();
      byte[][] endKeys = keys.getSecond();

      for (int i = 0; i < startKeys.length; i++) {
        if (i == 0) {
          assertArrayEquals(HConstants.EMPTY_START_ROW, startKeys[i]);
          assertArrayEquals(endKeys[i], splitRows[0]);
        } else if (i == 1) {
          assertArrayEquals(splitRows[0], startKeys[i]);
          assertArrayEquals(endKeys[i], HConstants.EMPTY_END_ROW);
        }
      }

      // Insert data
      List<Put> puts = new ArrayList<>();
      for (int i = 0; i < startKeys.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Put put = new Put(rows[i], timestamp[j]);
          put.addColumn(family, null, timestamp[j], Bytes.toBytes(timestamp[j]));
          puts.add(put);
        }
      }
      table.put(puts);
      LOG.info("Inserted {} puts into table", puts.size());
    }

    checkpoint("AFTER_INSERT_DATA");

    // Verify data can be read - must reopen table after checkpoint
    try (Table table = connection.getTable(tableName)) {
      // Verify individual gets work
      for (int i = 0; i < rows.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Get get = new Get(rows[i]);
          get.addFamily(family);
          get.setTimestamp(timestamp[j]);
          Result result = table.get(get);
          int cellCount = result.rawCells().length;
          assertEquals(1, cellCount);
        }
      }

      // Case 1: scan with LATEST_TIMESTAMP. Should get two rows
      int count;
      Scan scan = new Scan();
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 1: LATEST_TIMESTAMP scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_1");

    try (Table table = connection.getTable(tableName)) {
      // Case 2: Scan with a timestamp greater than most recent timestamp
      // (in this case > 1000 and < LATEST_TIMESTAMP. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(1000L, Long.MAX_VALUE);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 2: TimeRange(1000, MAX) scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_2");

    try (Table table = connection.getTable(tableName)) {
      // Case 3: scan with timestamp equal to most recent timestamp
      // (in this case == 1000. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimestamp(1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 3: Timestamp=1000 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_3");

    try (Table table = connection.getTable(tableName)) {
      // Case 4: scan with timestamp greater than first timestamp but less than
      // second timestamp (100 < timestamp < 1000). Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(100L, 1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 4: TimeRange(100, 1000) scan found {} rows", count);

      // Case 5: scan with timestamp equal to first timestamp (100)
      // Should get 2 rows.
      scan = new Scan();
      scan.setTimestamp(100L);
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 5: Timestamp=100 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_4");

    // Final verification after all operations
    try (Table table = connection.getTable(tableName)) {
      // Verify all data still accessible
      Scan scan = new Scan();
      scan.addFamily(family);
      scan.setMaxVersions();
      int totalRows = 0;
      try (ResultScanner s = table.getScanner(scan)) {
        for (Result r : s) {
          totalRows++;
          // Each row should have 2 versions
          assertEquals("Each row should have 2 versions", 2, r.rawCells().length);
        }
      }
      assertEquals("Total rows should be 2", 2, totalRows);
      LOG.info("Final verification: {} rows with all versions intact", totalRows);
    }

    checkpoint("AFTER_ALL_SCANS");

    LOG.info("Test completed successfully for checkpoint: {}", upgradeCheckpoint);
  }

  @Test(timeout = 300000)
  public void testScanMultipleVersions_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    // Start the cluster
    LOG.info("Starting cluster with {} region servers", NUM_SLAVES);
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(NUM_SLAVES).build();
    cluster.waitClusterUp();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    final byte[] family = HConstants.CATALOG_FAMILY;
    final byte[][] rows = new byte[][] { Bytes.toBytes("row_0200"), Bytes.toBytes("row_0800") };
    final byte[][] splitRows = new byte[][] { Bytes.toBytes("row_0500") };
    final long[] timestamp = new long[] { 100L, 1000L };

    // Create table with split
    TableDescriptor td = TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(family)).build();
    admin.createTable(td, splitRows);
    LOG.info("Created table {} with split at {}", tableName, Bytes.toString(splitRows[0]));

    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(tableName)) {
      // Assert we got the region layout wanted.
      Pair<byte[][], byte[][]> keys =
        connection.getRegionLocator(tableName).getStartEndKeys();
      assertEquals(2, keys.getFirst().length);
      byte[][] startKeys = keys.getFirst();
      byte[][] endKeys = keys.getSecond();

      for (int i = 0; i < startKeys.length; i++) {
        if (i == 0) {
          assertArrayEquals(HConstants.EMPTY_START_ROW, startKeys[i]);
          assertArrayEquals(endKeys[i], splitRows[0]);
        } else if (i == 1) {
          assertArrayEquals(splitRows[0], startKeys[i]);
          assertArrayEquals(endKeys[i], HConstants.EMPTY_END_ROW);
        }
      }

      // Insert data
      List<Put> puts = new ArrayList<>();
      for (int i = 0; i < startKeys.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Put put = new Put(rows[i], timestamp[j]);
          put.addColumn(family, null, timestamp[j], Bytes.toBytes(timestamp[j]));
          puts.add(put);
        }
      }
      table.put(puts);
      LOG.info("Inserted {} puts into table", puts.size());
    }

    checkpoint("AFTER_INSERT_DATA");

    // Verify data can be read - must reopen table after checkpoint
    try (Table table = connection.getTable(tableName)) {
      // Verify individual gets work
      for (int i = 0; i < rows.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Get get = new Get(rows[i]);
          get.addFamily(family);
          get.setTimestamp(timestamp[j]);
          Result result = table.get(get);
          int cellCount = result.rawCells().length;
          assertEquals(1, cellCount);
        }
      }

      // Case 1: scan with LATEST_TIMESTAMP. Should get two rows
      int count;
      Scan scan = new Scan();
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 1: LATEST_TIMESTAMP scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_1");

    try (Table table = connection.getTable(tableName)) {
      // Case 2: Scan with a timestamp greater than most recent timestamp
      // (in this case > 1000 and < LATEST_TIMESTAMP. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(1000L, Long.MAX_VALUE);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 2: TimeRange(1000, MAX) scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_2");

    try (Table table = connection.getTable(tableName)) {
      // Case 3: scan with timestamp equal to most recent timestamp
      // (in this case == 1000. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimestamp(1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 3: Timestamp=1000 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_3");

    try (Table table = connection.getTable(tableName)) {
      // Case 4: scan with timestamp greater than first timestamp but less than
      // second timestamp (100 < timestamp < 1000). Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(100L, 1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 4: TimeRange(100, 1000) scan found {} rows", count);

      // Case 5: scan with timestamp equal to first timestamp (100)
      // Should get 2 rows.
      scan = new Scan();
      scan.setTimestamp(100L);
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 5: Timestamp=100 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_4");

    // Final verification after all operations
    try (Table table = connection.getTable(tableName)) {
      // Verify all data still accessible
      Scan scan = new Scan();
      scan.addFamily(family);
      scan.setMaxVersions();
      int totalRows = 0;
      try (ResultScanner s = table.getScanner(scan)) {
        for (Result r : s) {
          totalRows++;
          // Each row should have 2 versions
          assertEquals("Each row should have 2 versions", 2, r.rawCells().length);
        }
      }
      assertEquals("Total rows should be 2", 2, totalRows);
      LOG.info("Final verification: {} rows with all versions intact", totalRows);
    }

    checkpoint("AFTER_ALL_SCANS");

    LOG.info("Test completed successfully for checkpoint: {}", upgradeCheckpoint);
  }

  @Test(timeout = 300000)
  public void testScanMultipleVersions_AFTER_INSERT_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_INSERT_DATA";
    // Start the cluster
    LOG.info("Starting cluster with {} region servers", NUM_SLAVES);
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(NUM_SLAVES).build();
    cluster.waitClusterUp();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    final byte[] family = HConstants.CATALOG_FAMILY;
    final byte[][] rows = new byte[][] { Bytes.toBytes("row_0200"), Bytes.toBytes("row_0800") };
    final byte[][] splitRows = new byte[][] { Bytes.toBytes("row_0500") };
    final long[] timestamp = new long[] { 100L, 1000L };

    // Create table with split
    TableDescriptor td = TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(family)).build();
    admin.createTable(td, splitRows);
    LOG.info("Created table {} with split at {}", tableName, Bytes.toString(splitRows[0]));

    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(tableName)) {
      // Assert we got the region layout wanted.
      Pair<byte[][], byte[][]> keys =
        connection.getRegionLocator(tableName).getStartEndKeys();
      assertEquals(2, keys.getFirst().length);
      byte[][] startKeys = keys.getFirst();
      byte[][] endKeys = keys.getSecond();

      for (int i = 0; i < startKeys.length; i++) {
        if (i == 0) {
          assertArrayEquals(HConstants.EMPTY_START_ROW, startKeys[i]);
          assertArrayEquals(endKeys[i], splitRows[0]);
        } else if (i == 1) {
          assertArrayEquals(splitRows[0], startKeys[i]);
          assertArrayEquals(endKeys[i], HConstants.EMPTY_END_ROW);
        }
      }

      // Insert data
      List<Put> puts = new ArrayList<>();
      for (int i = 0; i < startKeys.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Put put = new Put(rows[i], timestamp[j]);
          put.addColumn(family, null, timestamp[j], Bytes.toBytes(timestamp[j]));
          puts.add(put);
        }
      }
      table.put(puts);
      LOG.info("Inserted {} puts into table", puts.size());
    }

    checkpoint("AFTER_INSERT_DATA");

    // Verify data can be read - must reopen table after checkpoint
    try (Table table = connection.getTable(tableName)) {
      // Verify individual gets work
      for (int i = 0; i < rows.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Get get = new Get(rows[i]);
          get.addFamily(family);
          get.setTimestamp(timestamp[j]);
          Result result = table.get(get);
          int cellCount = result.rawCells().length;
          assertEquals(1, cellCount);
        }
      }

      // Case 1: scan with LATEST_TIMESTAMP. Should get two rows
      int count;
      Scan scan = new Scan();
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 1: LATEST_TIMESTAMP scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_1");

    try (Table table = connection.getTable(tableName)) {
      // Case 2: Scan with a timestamp greater than most recent timestamp
      // (in this case > 1000 and < LATEST_TIMESTAMP. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(1000L, Long.MAX_VALUE);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 2: TimeRange(1000, MAX) scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_2");

    try (Table table = connection.getTable(tableName)) {
      // Case 3: scan with timestamp equal to most recent timestamp
      // (in this case == 1000. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimestamp(1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 3: Timestamp=1000 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_3");

    try (Table table = connection.getTable(tableName)) {
      // Case 4: scan with timestamp greater than first timestamp but less than
      // second timestamp (100 < timestamp < 1000). Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(100L, 1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 4: TimeRange(100, 1000) scan found {} rows", count);

      // Case 5: scan with timestamp equal to first timestamp (100)
      // Should get 2 rows.
      scan = new Scan();
      scan.setTimestamp(100L);
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 5: Timestamp=100 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_4");

    // Final verification after all operations
    try (Table table = connection.getTable(tableName)) {
      // Verify all data still accessible
      Scan scan = new Scan();
      scan.addFamily(family);
      scan.setMaxVersions();
      int totalRows = 0;
      try (ResultScanner s = table.getScanner(scan)) {
        for (Result r : s) {
          totalRows++;
          // Each row should have 2 versions
          assertEquals("Each row should have 2 versions", 2, r.rawCells().length);
        }
      }
      assertEquals("Total rows should be 2", 2, totalRows);
      LOG.info("Final verification: {} rows with all versions intact", totalRows);
    }

    checkpoint("AFTER_ALL_SCANS");

    LOG.info("Test completed successfully for checkpoint: {}", upgradeCheckpoint);
  }

  @Test(timeout = 300000)
  public void testScanMultipleVersions_AFTER_SCAN_CASE_1() throws Exception {
    upgradeCheckpoint = "AFTER_SCAN_CASE_1";
    // Start the cluster
    LOG.info("Starting cluster with {} region servers", NUM_SLAVES);
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(NUM_SLAVES).build();
    cluster.waitClusterUp();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    final byte[] family = HConstants.CATALOG_FAMILY;
    final byte[][] rows = new byte[][] { Bytes.toBytes("row_0200"), Bytes.toBytes("row_0800") };
    final byte[][] splitRows = new byte[][] { Bytes.toBytes("row_0500") };
    final long[] timestamp = new long[] { 100L, 1000L };

    // Create table with split
    TableDescriptor td = TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(family)).build();
    admin.createTable(td, splitRows);
    LOG.info("Created table {} with split at {}", tableName, Bytes.toString(splitRows[0]));

    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(tableName)) {
      // Assert we got the region layout wanted.
      Pair<byte[][], byte[][]> keys =
        connection.getRegionLocator(tableName).getStartEndKeys();
      assertEquals(2, keys.getFirst().length);
      byte[][] startKeys = keys.getFirst();
      byte[][] endKeys = keys.getSecond();

      for (int i = 0; i < startKeys.length; i++) {
        if (i == 0) {
          assertArrayEquals(HConstants.EMPTY_START_ROW, startKeys[i]);
          assertArrayEquals(endKeys[i], splitRows[0]);
        } else if (i == 1) {
          assertArrayEquals(splitRows[0], startKeys[i]);
          assertArrayEquals(endKeys[i], HConstants.EMPTY_END_ROW);
        }
      }

      // Insert data
      List<Put> puts = new ArrayList<>();
      for (int i = 0; i < startKeys.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Put put = new Put(rows[i], timestamp[j]);
          put.addColumn(family, null, timestamp[j], Bytes.toBytes(timestamp[j]));
          puts.add(put);
        }
      }
      table.put(puts);
      LOG.info("Inserted {} puts into table", puts.size());
    }

    checkpoint("AFTER_INSERT_DATA");

    // Verify data can be read - must reopen table after checkpoint
    try (Table table = connection.getTable(tableName)) {
      // Verify individual gets work
      for (int i = 0; i < rows.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Get get = new Get(rows[i]);
          get.addFamily(family);
          get.setTimestamp(timestamp[j]);
          Result result = table.get(get);
          int cellCount = result.rawCells().length;
          assertEquals(1, cellCount);
        }
      }

      // Case 1: scan with LATEST_TIMESTAMP. Should get two rows
      int count;
      Scan scan = new Scan();
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 1: LATEST_TIMESTAMP scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_1");

    try (Table table = connection.getTable(tableName)) {
      // Case 2: Scan with a timestamp greater than most recent timestamp
      // (in this case > 1000 and < LATEST_TIMESTAMP. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(1000L, Long.MAX_VALUE);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 2: TimeRange(1000, MAX) scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_2");

    try (Table table = connection.getTable(tableName)) {
      // Case 3: scan with timestamp equal to most recent timestamp
      // (in this case == 1000. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimestamp(1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 3: Timestamp=1000 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_3");

    try (Table table = connection.getTable(tableName)) {
      // Case 4: scan with timestamp greater than first timestamp but less than
      // second timestamp (100 < timestamp < 1000). Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(100L, 1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 4: TimeRange(100, 1000) scan found {} rows", count);

      // Case 5: scan with timestamp equal to first timestamp (100)
      // Should get 2 rows.
      scan = new Scan();
      scan.setTimestamp(100L);
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 5: Timestamp=100 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_4");

    // Final verification after all operations
    try (Table table = connection.getTable(tableName)) {
      // Verify all data still accessible
      Scan scan = new Scan();
      scan.addFamily(family);
      scan.setMaxVersions();
      int totalRows = 0;
      try (ResultScanner s = table.getScanner(scan)) {
        for (Result r : s) {
          totalRows++;
          // Each row should have 2 versions
          assertEquals("Each row should have 2 versions", 2, r.rawCells().length);
        }
      }
      assertEquals("Total rows should be 2", 2, totalRows);
      LOG.info("Final verification: {} rows with all versions intact", totalRows);
    }

    checkpoint("AFTER_ALL_SCANS");

    LOG.info("Test completed successfully for checkpoint: {}", upgradeCheckpoint);
  }

  @Test(timeout = 300000)
  public void testScanMultipleVersions_AFTER_SCAN_CASE_2() throws Exception {
    upgradeCheckpoint = "AFTER_SCAN_CASE_2";
    // Start the cluster
    LOG.info("Starting cluster with {} region servers", NUM_SLAVES);
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(NUM_SLAVES).build();
    cluster.waitClusterUp();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    final byte[] family = HConstants.CATALOG_FAMILY;
    final byte[][] rows = new byte[][] { Bytes.toBytes("row_0200"), Bytes.toBytes("row_0800") };
    final byte[][] splitRows = new byte[][] { Bytes.toBytes("row_0500") };
    final long[] timestamp = new long[] { 100L, 1000L };

    // Create table with split
    TableDescriptor td = TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(family)).build();
    admin.createTable(td, splitRows);
    LOG.info("Created table {} with split at {}", tableName, Bytes.toString(splitRows[0]));

    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(tableName)) {
      // Assert we got the region layout wanted.
      Pair<byte[][], byte[][]> keys =
        connection.getRegionLocator(tableName).getStartEndKeys();
      assertEquals(2, keys.getFirst().length);
      byte[][] startKeys = keys.getFirst();
      byte[][] endKeys = keys.getSecond();

      for (int i = 0; i < startKeys.length; i++) {
        if (i == 0) {
          assertArrayEquals(HConstants.EMPTY_START_ROW, startKeys[i]);
          assertArrayEquals(endKeys[i], splitRows[0]);
        } else if (i == 1) {
          assertArrayEquals(splitRows[0], startKeys[i]);
          assertArrayEquals(endKeys[i], HConstants.EMPTY_END_ROW);
        }
      }

      // Insert data
      List<Put> puts = new ArrayList<>();
      for (int i = 0; i < startKeys.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Put put = new Put(rows[i], timestamp[j]);
          put.addColumn(family, null, timestamp[j], Bytes.toBytes(timestamp[j]));
          puts.add(put);
        }
      }
      table.put(puts);
      LOG.info("Inserted {} puts into table", puts.size());
    }

    checkpoint("AFTER_INSERT_DATA");

    // Verify data can be read - must reopen table after checkpoint
    try (Table table = connection.getTable(tableName)) {
      // Verify individual gets work
      for (int i = 0; i < rows.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Get get = new Get(rows[i]);
          get.addFamily(family);
          get.setTimestamp(timestamp[j]);
          Result result = table.get(get);
          int cellCount = result.rawCells().length;
          assertEquals(1, cellCount);
        }
      }

      // Case 1: scan with LATEST_TIMESTAMP. Should get two rows
      int count;
      Scan scan = new Scan();
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 1: LATEST_TIMESTAMP scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_1");

    try (Table table = connection.getTable(tableName)) {
      // Case 2: Scan with a timestamp greater than most recent timestamp
      // (in this case > 1000 and < LATEST_TIMESTAMP. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(1000L, Long.MAX_VALUE);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 2: TimeRange(1000, MAX) scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_2");

    try (Table table = connection.getTable(tableName)) {
      // Case 3: scan with timestamp equal to most recent timestamp
      // (in this case == 1000. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimestamp(1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 3: Timestamp=1000 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_3");

    try (Table table = connection.getTable(tableName)) {
      // Case 4: scan with timestamp greater than first timestamp but less than
      // second timestamp (100 < timestamp < 1000). Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(100L, 1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 4: TimeRange(100, 1000) scan found {} rows", count);

      // Case 5: scan with timestamp equal to first timestamp (100)
      // Should get 2 rows.
      scan = new Scan();
      scan.setTimestamp(100L);
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 5: Timestamp=100 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_4");

    // Final verification after all operations
    try (Table table = connection.getTable(tableName)) {
      // Verify all data still accessible
      Scan scan = new Scan();
      scan.addFamily(family);
      scan.setMaxVersions();
      int totalRows = 0;
      try (ResultScanner s = table.getScanner(scan)) {
        for (Result r : s) {
          totalRows++;
          // Each row should have 2 versions
          assertEquals("Each row should have 2 versions", 2, r.rawCells().length);
        }
      }
      assertEquals("Total rows should be 2", 2, totalRows);
      LOG.info("Final verification: {} rows with all versions intact", totalRows);
    }

    checkpoint("AFTER_ALL_SCANS");

    LOG.info("Test completed successfully for checkpoint: {}", upgradeCheckpoint);
  }

  @Test(timeout = 300000)
  public void testScanMultipleVersions_AFTER_SCAN_CASE_3() throws Exception {
    upgradeCheckpoint = "AFTER_SCAN_CASE_3";
    // Start the cluster
    LOG.info("Starting cluster with {} region servers", NUM_SLAVES);
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(NUM_SLAVES).build();
    cluster.waitClusterUp();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    final byte[] family = HConstants.CATALOG_FAMILY;
    final byte[][] rows = new byte[][] { Bytes.toBytes("row_0200"), Bytes.toBytes("row_0800") };
    final byte[][] splitRows = new byte[][] { Bytes.toBytes("row_0500") };
    final long[] timestamp = new long[] { 100L, 1000L };

    // Create table with split
    TableDescriptor td = TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(family)).build();
    admin.createTable(td, splitRows);
    LOG.info("Created table {} with split at {}", tableName, Bytes.toString(splitRows[0]));

    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(tableName)) {
      // Assert we got the region layout wanted.
      Pair<byte[][], byte[][]> keys =
        connection.getRegionLocator(tableName).getStartEndKeys();
      assertEquals(2, keys.getFirst().length);
      byte[][] startKeys = keys.getFirst();
      byte[][] endKeys = keys.getSecond();

      for (int i = 0; i < startKeys.length; i++) {
        if (i == 0) {
          assertArrayEquals(HConstants.EMPTY_START_ROW, startKeys[i]);
          assertArrayEquals(endKeys[i], splitRows[0]);
        } else if (i == 1) {
          assertArrayEquals(splitRows[0], startKeys[i]);
          assertArrayEquals(endKeys[i], HConstants.EMPTY_END_ROW);
        }
      }

      // Insert data
      List<Put> puts = new ArrayList<>();
      for (int i = 0; i < startKeys.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Put put = new Put(rows[i], timestamp[j]);
          put.addColumn(family, null, timestamp[j], Bytes.toBytes(timestamp[j]));
          puts.add(put);
        }
      }
      table.put(puts);
      LOG.info("Inserted {} puts into table", puts.size());
    }

    checkpoint("AFTER_INSERT_DATA");

    // Verify data can be read - must reopen table after checkpoint
    try (Table table = connection.getTable(tableName)) {
      // Verify individual gets work
      for (int i = 0; i < rows.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Get get = new Get(rows[i]);
          get.addFamily(family);
          get.setTimestamp(timestamp[j]);
          Result result = table.get(get);
          int cellCount = result.rawCells().length;
          assertEquals(1, cellCount);
        }
      }

      // Case 1: scan with LATEST_TIMESTAMP. Should get two rows
      int count;
      Scan scan = new Scan();
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 1: LATEST_TIMESTAMP scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_1");

    try (Table table = connection.getTable(tableName)) {
      // Case 2: Scan with a timestamp greater than most recent timestamp
      // (in this case > 1000 and < LATEST_TIMESTAMP. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(1000L, Long.MAX_VALUE);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 2: TimeRange(1000, MAX) scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_2");

    try (Table table = connection.getTable(tableName)) {
      // Case 3: scan with timestamp equal to most recent timestamp
      // (in this case == 1000. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimestamp(1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 3: Timestamp=1000 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_3");

    try (Table table = connection.getTable(tableName)) {
      // Case 4: scan with timestamp greater than first timestamp but less than
      // second timestamp (100 < timestamp < 1000). Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(100L, 1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 4: TimeRange(100, 1000) scan found {} rows", count);

      // Case 5: scan with timestamp equal to first timestamp (100)
      // Should get 2 rows.
      scan = new Scan();
      scan.setTimestamp(100L);
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 5: Timestamp=100 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_4");

    // Final verification after all operations
    try (Table table = connection.getTable(tableName)) {
      // Verify all data still accessible
      Scan scan = new Scan();
      scan.addFamily(family);
      scan.setMaxVersions();
      int totalRows = 0;
      try (ResultScanner s = table.getScanner(scan)) {
        for (Result r : s) {
          totalRows++;
          // Each row should have 2 versions
          assertEquals("Each row should have 2 versions", 2, r.rawCells().length);
        }
      }
      assertEquals("Total rows should be 2", 2, totalRows);
      LOG.info("Final verification: {} rows with all versions intact", totalRows);
    }

    checkpoint("AFTER_ALL_SCANS");

    LOG.info("Test completed successfully for checkpoint: {}", upgradeCheckpoint);
  }

  @Test(timeout = 300000)
  public void testScanMultipleVersions_AFTER_SCAN_CASE_4() throws Exception {
    upgradeCheckpoint = "AFTER_SCAN_CASE_4";
    // Start the cluster
    LOG.info("Starting cluster with {} region servers", NUM_SLAVES);
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(NUM_SLAVES).build();
    cluster.waitClusterUp();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    final byte[] family = HConstants.CATALOG_FAMILY;
    final byte[][] rows = new byte[][] { Bytes.toBytes("row_0200"), Bytes.toBytes("row_0800") };
    final byte[][] splitRows = new byte[][] { Bytes.toBytes("row_0500") };
    final long[] timestamp = new long[] { 100L, 1000L };

    // Create table with split
    TableDescriptor td = TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(family)).build();
    admin.createTable(td, splitRows);
    LOG.info("Created table {} with split at {}", tableName, Bytes.toString(splitRows[0]));

    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(tableName)) {
      // Assert we got the region layout wanted.
      Pair<byte[][], byte[][]> keys =
        connection.getRegionLocator(tableName).getStartEndKeys();
      assertEquals(2, keys.getFirst().length);
      byte[][] startKeys = keys.getFirst();
      byte[][] endKeys = keys.getSecond();

      for (int i = 0; i < startKeys.length; i++) {
        if (i == 0) {
          assertArrayEquals(HConstants.EMPTY_START_ROW, startKeys[i]);
          assertArrayEquals(endKeys[i], splitRows[0]);
        } else if (i == 1) {
          assertArrayEquals(splitRows[0], startKeys[i]);
          assertArrayEquals(endKeys[i], HConstants.EMPTY_END_ROW);
        }
      }

      // Insert data
      List<Put> puts = new ArrayList<>();
      for (int i = 0; i < startKeys.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Put put = new Put(rows[i], timestamp[j]);
          put.addColumn(family, null, timestamp[j], Bytes.toBytes(timestamp[j]));
          puts.add(put);
        }
      }
      table.put(puts);
      LOG.info("Inserted {} puts into table", puts.size());
    }

    checkpoint("AFTER_INSERT_DATA");

    // Verify data can be read - must reopen table after checkpoint
    try (Table table = connection.getTable(tableName)) {
      // Verify individual gets work
      for (int i = 0; i < rows.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Get get = new Get(rows[i]);
          get.addFamily(family);
          get.setTimestamp(timestamp[j]);
          Result result = table.get(get);
          int cellCount = result.rawCells().length;
          assertEquals(1, cellCount);
        }
      }

      // Case 1: scan with LATEST_TIMESTAMP. Should get two rows
      int count;
      Scan scan = new Scan();
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 1: LATEST_TIMESTAMP scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_1");

    try (Table table = connection.getTable(tableName)) {
      // Case 2: Scan with a timestamp greater than most recent timestamp
      // (in this case > 1000 and < LATEST_TIMESTAMP. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(1000L, Long.MAX_VALUE);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 2: TimeRange(1000, MAX) scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_2");

    try (Table table = connection.getTable(tableName)) {
      // Case 3: scan with timestamp equal to most recent timestamp
      // (in this case == 1000. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimestamp(1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 3: Timestamp=1000 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_3");

    try (Table table = connection.getTable(tableName)) {
      // Case 4: scan with timestamp greater than first timestamp but less than
      // second timestamp (100 < timestamp < 1000). Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(100L, 1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 4: TimeRange(100, 1000) scan found {} rows", count);

      // Case 5: scan with timestamp equal to first timestamp (100)
      // Should get 2 rows.
      scan = new Scan();
      scan.setTimestamp(100L);
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 5: Timestamp=100 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_4");

    // Final verification after all operations
    try (Table table = connection.getTable(tableName)) {
      // Verify all data still accessible
      Scan scan = new Scan();
      scan.addFamily(family);
      scan.setMaxVersions();
      int totalRows = 0;
      try (ResultScanner s = table.getScanner(scan)) {
        for (Result r : s) {
          totalRows++;
          // Each row should have 2 versions
          assertEquals("Each row should have 2 versions", 2, r.rawCells().length);
        }
      }
      assertEquals("Total rows should be 2", 2, totalRows);
      LOG.info("Final verification: {} rows with all versions intact", totalRows);
    }

    checkpoint("AFTER_ALL_SCANS");

    LOG.info("Test completed successfully for checkpoint: {}", upgradeCheckpoint);
  }

  @Test(timeout = 300000)
  public void testScanMultipleVersions_AFTER_ALL_SCANS() throws Exception {
    upgradeCheckpoint = "AFTER_ALL_SCANS";
    // Start the cluster
    LOG.info("Starting cluster with {} region servers", NUM_SLAVES);
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(NUM_SLAVES).build();
    cluster.waitClusterUp();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    final byte[] family = HConstants.CATALOG_FAMILY;
    final byte[][] rows = new byte[][] { Bytes.toBytes("row_0200"), Bytes.toBytes("row_0800") };
    final byte[][] splitRows = new byte[][] { Bytes.toBytes("row_0500") };
    final long[] timestamp = new long[] { 100L, 1000L };

    // Create table with split
    TableDescriptor td = TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(family)).build();
    admin.createTable(td, splitRows);
    LOG.info("Created table {} with split at {}", tableName, Bytes.toString(splitRows[0]));

    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(tableName)) {
      // Assert we got the region layout wanted.
      Pair<byte[][], byte[][]> keys =
        connection.getRegionLocator(tableName).getStartEndKeys();
      assertEquals(2, keys.getFirst().length);
      byte[][] startKeys = keys.getFirst();
      byte[][] endKeys = keys.getSecond();

      for (int i = 0; i < startKeys.length; i++) {
        if (i == 0) {
          assertArrayEquals(HConstants.EMPTY_START_ROW, startKeys[i]);
          assertArrayEquals(endKeys[i], splitRows[0]);
        } else if (i == 1) {
          assertArrayEquals(splitRows[0], startKeys[i]);
          assertArrayEquals(endKeys[i], HConstants.EMPTY_END_ROW);
        }
      }

      // Insert data
      List<Put> puts = new ArrayList<>();
      for (int i = 0; i < startKeys.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Put put = new Put(rows[i], timestamp[j]);
          put.addColumn(family, null, timestamp[j], Bytes.toBytes(timestamp[j]));
          puts.add(put);
        }
      }
      table.put(puts);
      LOG.info("Inserted {} puts into table", puts.size());
    }

    checkpoint("AFTER_INSERT_DATA");

    // Verify data can be read - must reopen table after checkpoint
    try (Table table = connection.getTable(tableName)) {
      // Verify individual gets work
      for (int i = 0; i < rows.length; i++) {
        for (int j = 0; j < timestamp.length; j++) {
          Get get = new Get(rows[i]);
          get.addFamily(family);
          get.setTimestamp(timestamp[j]);
          Result result = table.get(get);
          int cellCount = result.rawCells().length;
          assertEquals(1, cellCount);
        }
      }

      // Case 1: scan with LATEST_TIMESTAMP. Should get two rows
      int count;
      Scan scan = new Scan();
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 1: LATEST_TIMESTAMP scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_1");

    try (Table table = connection.getTable(tableName)) {
      // Case 2: Scan with a timestamp greater than most recent timestamp
      // (in this case > 1000 and < LATEST_TIMESTAMP. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(1000L, Long.MAX_VALUE);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 2: TimeRange(1000, MAX) scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_2");

    try (Table table = connection.getTable(tableName)) {
      // Case 3: scan with timestamp equal to most recent timestamp
      // (in this case == 1000. Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimestamp(1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 3: Timestamp=1000 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_3");

    try (Table table = connection.getTable(tableName)) {
      // Case 4: scan with timestamp greater than first timestamp but less than
      // second timestamp (100 < timestamp < 1000). Should get 2 rows.
      Scan scan = new Scan();
      scan.setTimeRange(100L, 1000L);
      scan.addFamily(family);
      int count;
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 4: TimeRange(100, 1000) scan found {} rows", count);

      // Case 5: scan with timestamp equal to first timestamp (100)
      // Should get 2 rows.
      scan = new Scan();
      scan.setTimestamp(100L);
      scan.addFamily(family);
      try (ResultScanner s = table.getScanner(scan)) {
        count = Iterables.size(s);
      }
      assertEquals("Number of rows should be 2", 2, count);
      LOG.info("Case 5: Timestamp=100 scan found {} rows", count);
    }

    checkpoint("AFTER_SCAN_CASE_4");

    // Final verification after all operations
    try (Table table = connection.getTable(tableName)) {
      // Verify all data still accessible
      Scan scan = new Scan();
      scan.addFamily(family);
      scan.setMaxVersions();
      int totalRows = 0;
      try (ResultScanner s = table.getScanner(scan)) {
        for (Result r : s) {
          totalRows++;
          // Each row should have 2 versions
          assertEquals("Each row should have 2 versions", 2, r.rawCells().length);
        }
      }
      assertEquals("Total rows should be 2", 2, totalRows);
      LOG.info("Final verification: {} rows with all versions intact", totalRows);
    }

    checkpoint("AFTER_ALL_SCANS");

    LOG.info("Test completed successfully for checkpoint: {}", upgradeCheckpoint);
  }

  /**
   * Tests getting multiple versions of a row across potential upgrades.
   */
  @Test(timeout = 300000)
  public void testGetRowVersions_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    // Start the cluster
    LOG.info("Starting cluster with {} region servers", NUM_SLAVES);
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(NUM_SLAVES).build();
    cluster.waitClusterUp();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final byte[] contents = Bytes.toBytes("contents");
    final byte[] row = Bytes.toBytes("row");
    final byte[] value1 = Bytes.toBytes("value1");
    final byte[] value2 = Bytes.toBytes("value2");
    final long timestamp1 = 100L;
    final long timestamp2 = 200L;

    TableDescriptor td = TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName()))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.newBuilder(contents).setMaxVersions(3).build())
      .build();
    admin.createTable(td);
    LOG.info("Created table {}", td.getTableName());

    checkpoint("AFTER_CREATE_TABLE");

    // Insert first version
    try (Table table = connection.getTable(td.getTableName())) {
      Put put = new Put(row, timestamp1);
      put.addColumn(contents, contents, value1);
      table.put(put);
      LOG.info("Inserted first version with timestamp {}", timestamp1);
    }

    checkpoint("AFTER_INSERT_DATA");

    // Insert second version (potentially after upgrade)
    try (Table table = connection.getTable(td.getTableName())) {
      Put put = new Put(row, timestamp2);
      put.addColumn(contents, contents, value2);
      table.put(put);
      LOG.info("Inserted second version with timestamp {}", timestamp2);
    }

    // Verify single version get
    try (Table table = connection.getTable(td.getTableName())) {
      Get get = new Get(row);
      // Should get one version by default
      Result r = table.get(get);
      assertNotNull(r);
      assertFalse(r.isEmpty());
      assertEquals(1, r.size());
      byte[] value = r.getValue(contents, contents);
      assertNotEquals(0, value.length);
      assertTrue(Bytes.equals(value, value2));
      LOG.info("Single version get returned latest value as expected");

      // Now check getRow with multiple versions
      get = new Get(row);
      get.setMaxVersions();
      r = table.get(get);
      assertEquals(2, r.size());
      value = r.getValue(contents, contents);
      assertNotEquals(0, value.length);
      assertArrayEquals(value, value2);
      NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> map = r.getMap();
      NavigableMap<byte[], NavigableMap<Long, byte[]>> familyMap = map.get(contents);
      NavigableMap<Long, byte[]> versionMap = familyMap.get(contents);
      assertEquals(2, versionMap.size());
      assertArrayEquals(value1, versionMap.get(timestamp1));
      assertArrayEquals(value2, versionMap.get(timestamp2));
      LOG.info("Multi-version get returned both versions as expected");
    }

    checkpoint("AFTER_ALL_SCANS");

    LOG.info("Test completed successfully for checkpoint: {}", upgradeCheckpoint);
  }

  @Test(timeout = 300000)
  public void testGetRowVersions_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    // Start the cluster
    LOG.info("Starting cluster with {} region servers", NUM_SLAVES);
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(NUM_SLAVES).build();
    cluster.waitClusterUp();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final byte[] contents = Bytes.toBytes("contents");
    final byte[] row = Bytes.toBytes("row");
    final byte[] value1 = Bytes.toBytes("value1");
    final byte[] value2 = Bytes.toBytes("value2");
    final long timestamp1 = 100L;
    final long timestamp2 = 200L;

    TableDescriptor td = TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName()))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.newBuilder(contents).setMaxVersions(3).build())
      .build();
    admin.createTable(td);
    LOG.info("Created table {}", td.getTableName());

    checkpoint("AFTER_CREATE_TABLE");

    // Insert first version
    try (Table table = connection.getTable(td.getTableName())) {
      Put put = new Put(row, timestamp1);
      put.addColumn(contents, contents, value1);
      table.put(put);
      LOG.info("Inserted first version with timestamp {}", timestamp1);
    }

    checkpoint("AFTER_INSERT_DATA");

    // Insert second version (potentially after upgrade)
    try (Table table = connection.getTable(td.getTableName())) {
      Put put = new Put(row, timestamp2);
      put.addColumn(contents, contents, value2);
      table.put(put);
      LOG.info("Inserted second version with timestamp {}", timestamp2);
    }

    // Verify single version get
    try (Table table = connection.getTable(td.getTableName())) {
      Get get = new Get(row);
      // Should get one version by default
      Result r = table.get(get);
      assertNotNull(r);
      assertFalse(r.isEmpty());
      assertEquals(1, r.size());
      byte[] value = r.getValue(contents, contents);
      assertNotEquals(0, value.length);
      assertTrue(Bytes.equals(value, value2));
      LOG.info("Single version get returned latest value as expected");

      // Now check getRow with multiple versions
      get = new Get(row);
      get.setMaxVersions();
      r = table.get(get);
      assertEquals(2, r.size());
      value = r.getValue(contents, contents);
      assertNotEquals(0, value.length);
      assertArrayEquals(value, value2);
      NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> map = r.getMap();
      NavigableMap<byte[], NavigableMap<Long, byte[]>> familyMap = map.get(contents);
      NavigableMap<Long, byte[]> versionMap = familyMap.get(contents);
      assertEquals(2, versionMap.size());
      assertArrayEquals(value1, versionMap.get(timestamp1));
      assertArrayEquals(value2, versionMap.get(timestamp2));
      LOG.info("Multi-version get returned both versions as expected");
    }

    checkpoint("AFTER_ALL_SCANS");

    LOG.info("Test completed successfully for checkpoint: {}", upgradeCheckpoint);
  }

  @Test(timeout = 300000)
  public void testGetRowVersions_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    // Start the cluster
    LOG.info("Starting cluster with {} region servers", NUM_SLAVES);
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(NUM_SLAVES).build();
    cluster.waitClusterUp();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final byte[] contents = Bytes.toBytes("contents");
    final byte[] row = Bytes.toBytes("row");
    final byte[] value1 = Bytes.toBytes("value1");
    final byte[] value2 = Bytes.toBytes("value2");
    final long timestamp1 = 100L;
    final long timestamp2 = 200L;

    TableDescriptor td = TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName()))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.newBuilder(contents).setMaxVersions(3).build())
      .build();
    admin.createTable(td);
    LOG.info("Created table {}", td.getTableName());

    checkpoint("AFTER_CREATE_TABLE");

    // Insert first version
    try (Table table = connection.getTable(td.getTableName())) {
      Put put = new Put(row, timestamp1);
      put.addColumn(contents, contents, value1);
      table.put(put);
      LOG.info("Inserted first version with timestamp {}", timestamp1);
    }

    checkpoint("AFTER_INSERT_DATA");

    // Insert second version (potentially after upgrade)
    try (Table table = connection.getTable(td.getTableName())) {
      Put put = new Put(row, timestamp2);
      put.addColumn(contents, contents, value2);
      table.put(put);
      LOG.info("Inserted second version with timestamp {}", timestamp2);
    }

    // Verify single version get
    try (Table table = connection.getTable(td.getTableName())) {
      Get get = new Get(row);
      // Should get one version by default
      Result r = table.get(get);
      assertNotNull(r);
      assertFalse(r.isEmpty());
      assertEquals(1, r.size());
      byte[] value = r.getValue(contents, contents);
      assertNotEquals(0, value.length);
      assertTrue(Bytes.equals(value, value2));
      LOG.info("Single version get returned latest value as expected");

      // Now check getRow with multiple versions
      get = new Get(row);
      get.setMaxVersions();
      r = table.get(get);
      assertEquals(2, r.size());
      value = r.getValue(contents, contents);
      assertNotEquals(0, value.length);
      assertArrayEquals(value, value2);
      NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> map = r.getMap();
      NavigableMap<byte[], NavigableMap<Long, byte[]>> familyMap = map.get(contents);
      NavigableMap<Long, byte[]> versionMap = familyMap.get(contents);
      assertEquals(2, versionMap.size());
      assertArrayEquals(value1, versionMap.get(timestamp1));
      assertArrayEquals(value2, versionMap.get(timestamp2));
      LOG.info("Multi-version get returned both versions as expected");
    }

    checkpoint("AFTER_ALL_SCANS");

    LOG.info("Test completed successfully for checkpoint: {}", upgradeCheckpoint);
  }

  @Test(timeout = 300000)
  public void testGetRowVersions_AFTER_INSERT_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_INSERT_DATA";
    // Start the cluster
    LOG.info("Starting cluster with {} region servers", NUM_SLAVES);
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(NUM_SLAVES).build();
    cluster.waitClusterUp();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final byte[] contents = Bytes.toBytes("contents");
    final byte[] row = Bytes.toBytes("row");
    final byte[] value1 = Bytes.toBytes("value1");
    final byte[] value2 = Bytes.toBytes("value2");
    final long timestamp1 = 100L;
    final long timestamp2 = 200L;

    TableDescriptor td = TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName()))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.newBuilder(contents).setMaxVersions(3).build())
      .build();
    admin.createTable(td);
    LOG.info("Created table {}", td.getTableName());

    checkpoint("AFTER_CREATE_TABLE");

    // Insert first version
    try (Table table = connection.getTable(td.getTableName())) {
      Put put = new Put(row, timestamp1);
      put.addColumn(contents, contents, value1);
      table.put(put);
      LOG.info("Inserted first version with timestamp {}", timestamp1);
    }

    checkpoint("AFTER_INSERT_DATA");

    // Insert second version (potentially after upgrade)
    try (Table table = connection.getTable(td.getTableName())) {
      Put put = new Put(row, timestamp2);
      put.addColumn(contents, contents, value2);
      table.put(put);
      LOG.info("Inserted second version with timestamp {}", timestamp2);
    }

    // Verify single version get
    try (Table table = connection.getTable(td.getTableName())) {
      Get get = new Get(row);
      // Should get one version by default
      Result r = table.get(get);
      assertNotNull(r);
      assertFalse(r.isEmpty());
      assertEquals(1, r.size());
      byte[] value = r.getValue(contents, contents);
      assertNotEquals(0, value.length);
      assertTrue(Bytes.equals(value, value2));
      LOG.info("Single version get returned latest value as expected");

      // Now check getRow with multiple versions
      get = new Get(row);
      get.setMaxVersions();
      r = table.get(get);
      assertEquals(2, r.size());
      value = r.getValue(contents, contents);
      assertNotEquals(0, value.length);
      assertArrayEquals(value, value2);
      NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> map = r.getMap();
      NavigableMap<byte[], NavigableMap<Long, byte[]>> familyMap = map.get(contents);
      NavigableMap<Long, byte[]> versionMap = familyMap.get(contents);
      assertEquals(2, versionMap.size());
      assertArrayEquals(value1, versionMap.get(timestamp1));
      assertArrayEquals(value2, versionMap.get(timestamp2));
      LOG.info("Multi-version get returned both versions as expected");
    }

    checkpoint("AFTER_ALL_SCANS");

    LOG.info("Test completed successfully for checkpoint: {}", upgradeCheckpoint);
  }

  @Test(timeout = 300000)
  public void testGetRowVersions_AFTER_ALL_SCANS() throws Exception {
    upgradeCheckpoint = "AFTER_ALL_SCANS";
    // Start the cluster
    LOG.info("Starting cluster with {} region servers", NUM_SLAVES);
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(NUM_SLAVES).build();
    cluster.waitClusterUp();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final byte[] contents = Bytes.toBytes("contents");
    final byte[] row = Bytes.toBytes("row");
    final byte[] value1 = Bytes.toBytes("value1");
    final byte[] value2 = Bytes.toBytes("value2");
    final long timestamp1 = 100L;
    final long timestamp2 = 200L;

    TableDescriptor td = TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName()))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.newBuilder(contents).setMaxVersions(3).build())
      .build();
    admin.createTable(td);
    LOG.info("Created table {}", td.getTableName());

    checkpoint("AFTER_CREATE_TABLE");

    // Insert first version
    try (Table table = connection.getTable(td.getTableName())) {
      Put put = new Put(row, timestamp1);
      put.addColumn(contents, contents, value1);
      table.put(put);
      LOG.info("Inserted first version with timestamp {}", timestamp1);
    }

    checkpoint("AFTER_INSERT_DATA");

    // Insert second version (potentially after upgrade)
    try (Table table = connection.getTable(td.getTableName())) {
      Put put = new Put(row, timestamp2);
      put.addColumn(contents, contents, value2);
      table.put(put);
      LOG.info("Inserted second version with timestamp {}", timestamp2);
    }

    // Verify single version get
    try (Table table = connection.getTable(td.getTableName())) {
      Get get = new Get(row);
      // Should get one version by default
      Result r = table.get(get);
      assertNotNull(r);
      assertFalse(r.isEmpty());
      assertEquals(1, r.size());
      byte[] value = r.getValue(contents, contents);
      assertNotEquals(0, value.length);
      assertTrue(Bytes.equals(value, value2));
      LOG.info("Single version get returned latest value as expected");

      // Now check getRow with multiple versions
      get = new Get(row);
      get.setMaxVersions();
      r = table.get(get);
      assertEquals(2, r.size());
      value = r.getValue(contents, contents);
      assertNotEquals(0, value.length);
      assertArrayEquals(value, value2);
      NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> map = r.getMap();
      NavigableMap<byte[], NavigableMap<Long, byte[]>> familyMap = map.get(contents);
      NavigableMap<Long, byte[]> versionMap = familyMap.get(contents);
      assertEquals(2, versionMap.size());
      assertArrayEquals(value1, versionMap.get(timestamp1));
      assertArrayEquals(value2, versionMap.get(timestamp2));
      LOG.info("Multi-version get returned both versions as expected");
    }

    checkpoint("AFTER_ALL_SCANS");

    LOG.info("Test completed successfully for checkpoint: {}", upgradeCheckpoint);
  }
}
