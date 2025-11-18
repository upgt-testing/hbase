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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.MiscTests;
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
 * ProcessBased version of {@link TestGlobalMemStoreSize}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Note: The original test verifies internal accounting consistency (GlobalMemStoreSize
 * equals sum of region MemStoreSize). In ProcessBasedMiniHBaseCluster, we cannot access
 * internal server accounting. Instead, we verify that:
 * 1. Memstore size increases after writes (via RegionMetrics)
 * 2. Memstore size decreases after flush (via Admin.flush())
 * 3. Client-visible metrics are tracked correctly
 *
 * This is still meaningful as it tests memstore behavior during upgrades.
 *
 * @see TestGlobalMemStoreSize Original test using MiniHBaseCluster
 */
@Category({ MiscTests.class, MediumTests.class })
public class TestGlobalMemStoreSize_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestGlobalMemStoreSize_ProcessBased.class);

  private static final Logger LOG =
      LoggerFactory.getLogger(TestGlobalMemStoreSize_ProcessBased.class);
  private static int regionServerNum = 4;
  private static int regionNum = 16;

  @Rule
  public TestName name = new TestName();

  /**
   * Test that memstore size is tracked correctly by verifying:
   * 1. Size increases after writes
   * 2. Size decreases after flush
   *
   * TRANSFORMATION NOTE: The original test verified internal GlobalMemStoreSize
   * consistency by directly accessing server.getRegionServerAccounting().getGlobalMemStoreDataSize()
   * and comparing it to the sum of each region's getMemStoreDataSize(). In ProcessBased,
   * we cannot access internal accounting objects. Instead, we verify memstore behavior
   * via Admin.getRegionMetrics() which provides client-visible memstore size.
   */
  @Test(timeout = 300000)
  public void testGlobalMemStore_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    // Start the cluster
    LOG.info("Starting cluster");
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(regionServerNum)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with regions
    final TableName tableName = TableName.valueOf(name.getMethodName());
    byte[] family = Bytes.toBytes("family");
    LOG.info("Creating table with " + regionNum + " regions");

    byte[][] splits = new byte[regionNum - 1][];
    for (int i = 0; i < regionNum - 1; i++) {
      splits[i] = Bytes.toBytes(String.format("%03d", (i + 1) * (256 / regionNum)));
    }

    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(family));
    admin.createTable(tableBuilder.build(), splits);
    checkpoint("AFTER_CREATE_TABLE");

    int numRegions = -1;
    try (RegionLocator r = connection.getRegionLocator(tableName)) {
      numRegions = r.getStartKeys().length;
    }
    assertEquals(regionNum, numRegions);

    // Wait for all regions to be assigned
    List<RegionInfo> regions = admin.getRegions(tableName);
    assertEquals(regionNum, regions.size());

    // Get initial memstore size (should be 0 or minimal)
    long initialMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Initial memstore size: " + initialMemstoreSize);

    // Write data to the table
    LOG.info("Writing data to table");
    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        Put put = new Put(Bytes.toBytes(String.format("row-%05d", i)));
        // Write larger values to ensure memstore increase is visible
        byte[] value = new byte[1000];
        Arrays.fill(value, (byte) i);
        put.addColumn(family, Bytes.toBytes("col"), value);
        table.put(put);
      }
    }
    checkpoint("AFTER_WRITE_DATA");

    // Wait for metrics to be updated
    Thread.sleep(5000);

    // Verify memstore size increased
    long afterWriteMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Memstore size after writes: " + afterWriteMemstoreSize);
    assertTrue("Memstore size should increase after writes. Initial: " + initialMemstoreSize
        + ", After: " + afterWriteMemstoreSize, afterWriteMemstoreSize > initialMemstoreSize);
    checkpoint("AFTER_VERIFY_MEMSTORE_INCREASE");

    // Flush the table
    LOG.info("Flushing table");
    admin.flush(tableName);
    checkpoint("AFTER_FLUSH");

    // Wait for flush to complete and metrics to update
    Thread.sleep(10000);

    // Verify memstore size decreased after flush
    long afterFlushMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Memstore size after flush: " + afterFlushMemstoreSize);
    assertTrue("Memstore size should decrease after flush. After writes: " + afterWriteMemstoreSize
        + ", After flush: " + afterFlushMemstoreSize, afterFlushMemstoreSize < afterWriteMemstoreSize);
    checkpoint("AFTER_VERIFY_MEMSTORE_DECREASE");

    // Disable table before deletion (required by HBase)
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test(timeout = 300000)
  public void testGlobalMemStore_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    // Start the cluster
    LOG.info("Starting cluster");
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(regionServerNum)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with regions
    final TableName tableName = TableName.valueOf(name.getMethodName());
    byte[] family = Bytes.toBytes("family");
    LOG.info("Creating table with " + regionNum + " regions");

    byte[][] splits = new byte[regionNum - 1][];
    for (int i = 0; i < regionNum - 1; i++) {
      splits[i] = Bytes.toBytes(String.format("%03d", (i + 1) * (256 / regionNum)));
    }

    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(family));
    admin.createTable(tableBuilder.build(), splits);
    checkpoint("AFTER_CREATE_TABLE");

    int numRegions = -1;
    try (RegionLocator r = connection.getRegionLocator(tableName)) {
      numRegions = r.getStartKeys().length;
    }
    assertEquals(regionNum, numRegions);

    // Wait for all regions to be assigned
    List<RegionInfo> regions = admin.getRegions(tableName);
    assertEquals(regionNum, regions.size());

    // Get initial memstore size (should be 0 or minimal)
    long initialMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Initial memstore size: " + initialMemstoreSize);

    // Write data to the table
    LOG.info("Writing data to table");
    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        Put put = new Put(Bytes.toBytes(String.format("row-%05d", i)));
        // Write larger values to ensure memstore increase is visible
        byte[] value = new byte[1000];
        Arrays.fill(value, (byte) i);
        put.addColumn(family, Bytes.toBytes("col"), value);
        table.put(put);
      }
    }
    checkpoint("AFTER_WRITE_DATA");

    // Wait for metrics to be updated
    Thread.sleep(5000);

    // Verify memstore size increased
    long afterWriteMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Memstore size after writes: " + afterWriteMemstoreSize);
    assertTrue("Memstore size should increase after writes. Initial: " + initialMemstoreSize
        + ", After: " + afterWriteMemstoreSize, afterWriteMemstoreSize > initialMemstoreSize);
    checkpoint("AFTER_VERIFY_MEMSTORE_INCREASE");

    // Flush the table
    LOG.info("Flushing table");
    admin.flush(tableName);
    checkpoint("AFTER_FLUSH");

    // Wait for flush to complete and metrics to update
    Thread.sleep(10000);

    // Verify memstore size decreased after flush
    long afterFlushMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Memstore size after flush: " + afterFlushMemstoreSize);
    assertTrue("Memstore size should decrease after flush. After writes: " + afterWriteMemstoreSize
        + ", After flush: " + afterFlushMemstoreSize, afterFlushMemstoreSize < afterWriteMemstoreSize);
    checkpoint("AFTER_VERIFY_MEMSTORE_DECREASE");

    // Disable table before deletion (required by HBase)
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test(timeout = 300000)
  public void testGlobalMemStore_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    // Start the cluster
    LOG.info("Starting cluster");
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(regionServerNum)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with regions
    final TableName tableName = TableName.valueOf(name.getMethodName());
    byte[] family = Bytes.toBytes("family");
    LOG.info("Creating table with " + regionNum + " regions");

    byte[][] splits = new byte[regionNum - 1][];
    for (int i = 0; i < regionNum - 1; i++) {
      splits[i] = Bytes.toBytes(String.format("%03d", (i + 1) * (256 / regionNum)));
    }

    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(family));
    admin.createTable(tableBuilder.build(), splits);
    checkpoint("AFTER_CREATE_TABLE");

    int numRegions = -1;
    try (RegionLocator r = connection.getRegionLocator(tableName)) {
      numRegions = r.getStartKeys().length;
    }
    assertEquals(regionNum, numRegions);

    // Wait for all regions to be assigned
    List<RegionInfo> regions = admin.getRegions(tableName);
    assertEquals(regionNum, regions.size());

    // Get initial memstore size (should be 0 or minimal)
    long initialMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Initial memstore size: " + initialMemstoreSize);

    // Write data to the table
    LOG.info("Writing data to table");
    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        Put put = new Put(Bytes.toBytes(String.format("row-%05d", i)));
        // Write larger values to ensure memstore increase is visible
        byte[] value = new byte[1000];
        Arrays.fill(value, (byte) i);
        put.addColumn(family, Bytes.toBytes("col"), value);
        table.put(put);
      }
    }
    checkpoint("AFTER_WRITE_DATA");

    // Wait for metrics to be updated
    Thread.sleep(5000);

    // Verify memstore size increased
    long afterWriteMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Memstore size after writes: " + afterWriteMemstoreSize);
    assertTrue("Memstore size should increase after writes. Initial: " + initialMemstoreSize
        + ", After: " + afterWriteMemstoreSize, afterWriteMemstoreSize > initialMemstoreSize);
    checkpoint("AFTER_VERIFY_MEMSTORE_INCREASE");

    // Flush the table
    LOG.info("Flushing table");
    admin.flush(tableName);
    checkpoint("AFTER_FLUSH");

    // Wait for flush to complete and metrics to update
    Thread.sleep(10000);

    // Verify memstore size decreased after flush
    long afterFlushMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Memstore size after flush: " + afterFlushMemstoreSize);
    assertTrue("Memstore size should decrease after flush. After writes: " + afterWriteMemstoreSize
        + ", After flush: " + afterFlushMemstoreSize, afterFlushMemstoreSize < afterWriteMemstoreSize);
    checkpoint("AFTER_VERIFY_MEMSTORE_DECREASE");

    // Disable table before deletion (required by HBase)
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test(timeout = 300000)
  public void testGlobalMemStore_AFTER_WRITE_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE_DATA";
    // Start the cluster
    LOG.info("Starting cluster");
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(regionServerNum)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with regions
    final TableName tableName = TableName.valueOf(name.getMethodName());
    byte[] family = Bytes.toBytes("family");
    LOG.info("Creating table with " + regionNum + " regions");

    byte[][] splits = new byte[regionNum - 1][];
    for (int i = 0; i < regionNum - 1; i++) {
      splits[i] = Bytes.toBytes(String.format("%03d", (i + 1) * (256 / regionNum)));
    }

    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(family));
    admin.createTable(tableBuilder.build(), splits);
    checkpoint("AFTER_CREATE_TABLE");

    int numRegions = -1;
    try (RegionLocator r = connection.getRegionLocator(tableName)) {
      numRegions = r.getStartKeys().length;
    }
    assertEquals(regionNum, numRegions);

    // Wait for all regions to be assigned
    List<RegionInfo> regions = admin.getRegions(tableName);
    assertEquals(regionNum, regions.size());

    // Get initial memstore size (should be 0 or minimal)
    long initialMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Initial memstore size: " + initialMemstoreSize);

    // Write data to the table
    LOG.info("Writing data to table");
    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        Put put = new Put(Bytes.toBytes(String.format("row-%05d", i)));
        // Write larger values to ensure memstore increase is visible
        byte[] value = new byte[1000];
        Arrays.fill(value, (byte) i);
        put.addColumn(family, Bytes.toBytes("col"), value);
        table.put(put);
      }
    }
    checkpoint("AFTER_WRITE_DATA");

    // Wait for metrics to be updated
    Thread.sleep(5000);

    // Verify memstore size increased
    long afterWriteMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Memstore size after writes: " + afterWriteMemstoreSize);
    assertTrue("Memstore size should increase after writes. Initial: " + initialMemstoreSize
        + ", After: " + afterWriteMemstoreSize, afterWriteMemstoreSize > initialMemstoreSize);
    checkpoint("AFTER_VERIFY_MEMSTORE_INCREASE");

    // Flush the table
    LOG.info("Flushing table");
    admin.flush(tableName);
    checkpoint("AFTER_FLUSH");

    // Wait for flush to complete and metrics to update
    Thread.sleep(10000);

    // Verify memstore size decreased after flush
    long afterFlushMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Memstore size after flush: " + afterFlushMemstoreSize);
    assertTrue("Memstore size should decrease after flush. After writes: " + afterWriteMemstoreSize
        + ", After flush: " + afterFlushMemstoreSize, afterFlushMemstoreSize < afterWriteMemstoreSize);
    checkpoint("AFTER_VERIFY_MEMSTORE_DECREASE");

    // Disable table before deletion (required by HBase)
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test(timeout = 300000)
  public void testGlobalMemStore_AFTER_VERIFY_MEMSTORE_INCREASE() throws Exception {
    upgradeCheckpoint = "AFTER_VERIFY_MEMSTORE_INCREASE";
    // Start the cluster
    LOG.info("Starting cluster");
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(regionServerNum)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with regions
    final TableName tableName = TableName.valueOf(name.getMethodName());
    byte[] family = Bytes.toBytes("family");
    LOG.info("Creating table with " + regionNum + " regions");

    byte[][] splits = new byte[regionNum - 1][];
    for (int i = 0; i < regionNum - 1; i++) {
      splits[i] = Bytes.toBytes(String.format("%03d", (i + 1) * (256 / regionNum)));
    }

    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(family));
    admin.createTable(tableBuilder.build(), splits);
    checkpoint("AFTER_CREATE_TABLE");

    int numRegions = -1;
    try (RegionLocator r = connection.getRegionLocator(tableName)) {
      numRegions = r.getStartKeys().length;
    }
    assertEquals(regionNum, numRegions);

    // Wait for all regions to be assigned
    List<RegionInfo> regions = admin.getRegions(tableName);
    assertEquals(regionNum, regions.size());

    // Get initial memstore size (should be 0 or minimal)
    long initialMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Initial memstore size: " + initialMemstoreSize);

    // Write data to the table
    LOG.info("Writing data to table");
    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        Put put = new Put(Bytes.toBytes(String.format("row-%05d", i)));
        // Write larger values to ensure memstore increase is visible
        byte[] value = new byte[1000];
        Arrays.fill(value, (byte) i);
        put.addColumn(family, Bytes.toBytes("col"), value);
        table.put(put);
      }
    }
    checkpoint("AFTER_WRITE_DATA");

    // Wait for metrics to be updated
    Thread.sleep(5000);

    // Verify memstore size increased
    long afterWriteMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Memstore size after writes: " + afterWriteMemstoreSize);
    assertTrue("Memstore size should increase after writes. Initial: " + initialMemstoreSize
        + ", After: " + afterWriteMemstoreSize, afterWriteMemstoreSize > initialMemstoreSize);
    checkpoint("AFTER_VERIFY_MEMSTORE_INCREASE");

    // Flush the table
    LOG.info("Flushing table");
    admin.flush(tableName);
    checkpoint("AFTER_FLUSH");

    // Wait for flush to complete and metrics to update
    Thread.sleep(10000);

    // Verify memstore size decreased after flush
    long afterFlushMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Memstore size after flush: " + afterFlushMemstoreSize);
    assertTrue("Memstore size should decrease after flush. After writes: " + afterWriteMemstoreSize
        + ", After flush: " + afterFlushMemstoreSize, afterFlushMemstoreSize < afterWriteMemstoreSize);
    checkpoint("AFTER_VERIFY_MEMSTORE_DECREASE");

    // Disable table before deletion (required by HBase)
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test(timeout = 300000)
  public void testGlobalMemStore_AFTER_FLUSH() throws Exception {
    upgradeCheckpoint = "AFTER_FLUSH";
    // Start the cluster
    LOG.info("Starting cluster");
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(regionServerNum)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with regions
    final TableName tableName = TableName.valueOf(name.getMethodName());
    byte[] family = Bytes.toBytes("family");
    LOG.info("Creating table with " + regionNum + " regions");

    byte[][] splits = new byte[regionNum - 1][];
    for (int i = 0; i < regionNum - 1; i++) {
      splits[i] = Bytes.toBytes(String.format("%03d", (i + 1) * (256 / regionNum)));
    }

    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(family));
    admin.createTable(tableBuilder.build(), splits);
    checkpoint("AFTER_CREATE_TABLE");

    int numRegions = -1;
    try (RegionLocator r = connection.getRegionLocator(tableName)) {
      numRegions = r.getStartKeys().length;
    }
    assertEquals(regionNum, numRegions);

    // Wait for all regions to be assigned
    List<RegionInfo> regions = admin.getRegions(tableName);
    assertEquals(regionNum, regions.size());

    // Get initial memstore size (should be 0 or minimal)
    long initialMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Initial memstore size: " + initialMemstoreSize);

    // Write data to the table
    LOG.info("Writing data to table");
    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        Put put = new Put(Bytes.toBytes(String.format("row-%05d", i)));
        // Write larger values to ensure memstore increase is visible
        byte[] value = new byte[1000];
        Arrays.fill(value, (byte) i);
        put.addColumn(family, Bytes.toBytes("col"), value);
        table.put(put);
      }
    }
    checkpoint("AFTER_WRITE_DATA");

    // Wait for metrics to be updated
    Thread.sleep(5000);

    // Verify memstore size increased
    long afterWriteMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Memstore size after writes: " + afterWriteMemstoreSize);
    assertTrue("Memstore size should increase after writes. Initial: " + initialMemstoreSize
        + ", After: " + afterWriteMemstoreSize, afterWriteMemstoreSize > initialMemstoreSize);
    checkpoint("AFTER_VERIFY_MEMSTORE_INCREASE");

    // Flush the table
    LOG.info("Flushing table");
    admin.flush(tableName);
    checkpoint("AFTER_FLUSH");

    // Wait for flush to complete and metrics to update
    Thread.sleep(10000);

    // Verify memstore size decreased after flush
    long afterFlushMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Memstore size after flush: " + afterFlushMemstoreSize);
    assertTrue("Memstore size should decrease after flush. After writes: " + afterWriteMemstoreSize
        + ", After flush: " + afterFlushMemstoreSize, afterFlushMemstoreSize < afterWriteMemstoreSize);
    checkpoint("AFTER_VERIFY_MEMSTORE_DECREASE");

    // Disable table before deletion (required by HBase)
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test(timeout = 300000)
  public void testGlobalMemStore_AFTER_VERIFY_MEMSTORE_DECREASE() throws Exception {
    upgradeCheckpoint = "AFTER_VERIFY_MEMSTORE_DECREASE";
    // Start the cluster
    LOG.info("Starting cluster");
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(regionServerNum)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with regions
    final TableName tableName = TableName.valueOf(name.getMethodName());
    byte[] family = Bytes.toBytes("family");
    LOG.info("Creating table with " + regionNum + " regions");

    byte[][] splits = new byte[regionNum - 1][];
    for (int i = 0; i < regionNum - 1; i++) {
      splits[i] = Bytes.toBytes(String.format("%03d", (i + 1) * (256 / regionNum)));
    }

    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(family));
    admin.createTable(tableBuilder.build(), splits);
    checkpoint("AFTER_CREATE_TABLE");

    int numRegions = -1;
    try (RegionLocator r = connection.getRegionLocator(tableName)) {
      numRegions = r.getStartKeys().length;
    }
    assertEquals(regionNum, numRegions);

    // Wait for all regions to be assigned
    List<RegionInfo> regions = admin.getRegions(tableName);
    assertEquals(regionNum, regions.size());

    // Get initial memstore size (should be 0 or minimal)
    long initialMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Initial memstore size: " + initialMemstoreSize);

    // Write data to the table
    LOG.info("Writing data to table");
    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        Put put = new Put(Bytes.toBytes(String.format("row-%05d", i)));
        // Write larger values to ensure memstore increase is visible
        byte[] value = new byte[1000];
        Arrays.fill(value, (byte) i);
        put.addColumn(family, Bytes.toBytes("col"), value);
        table.put(put);
      }
    }
    checkpoint("AFTER_WRITE_DATA");

    // Wait for metrics to be updated
    Thread.sleep(5000);

    // Verify memstore size increased
    long afterWriteMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Memstore size after writes: " + afterWriteMemstoreSize);
    assertTrue("Memstore size should increase after writes. Initial: " + initialMemstoreSize
        + ", After: " + afterWriteMemstoreSize, afterWriteMemstoreSize > initialMemstoreSize);
    checkpoint("AFTER_VERIFY_MEMSTORE_INCREASE");

    // Flush the table
    LOG.info("Flushing table");
    admin.flush(tableName);
    checkpoint("AFTER_FLUSH");

    // Wait for flush to complete and metrics to update
    Thread.sleep(10000);

    // Verify memstore size decreased after flush
    long afterFlushMemstoreSize = getTotalMemstoreSize(tableName);
    LOG.info("Memstore size after flush: " + afterFlushMemstoreSize);
    assertTrue("Memstore size should decrease after flush. After writes: " + afterWriteMemstoreSize
        + ", After flush: " + afterFlushMemstoreSize, afterFlushMemstoreSize < afterWriteMemstoreSize);
    checkpoint("AFTER_VERIFY_MEMSTORE_DECREASE");

    // Disable table before deletion (required by HBase)
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  /**
   * Get total memstore size across all regions of a table.
   * Uses Admin.getRegionMetrics() to get client-visible memstore size.
   */
  private long getTotalMemstoreSize(TableName tableName) throws Exception {
    long totalSize = 0;
    for (ServerName serverName : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
      List<RegionMetrics> regionMetrics = admin.getRegionMetrics(serverName, tableName);
      for (RegionMetrics rm : regionMetrics) {
        totalSize += rm.getMemStoreSize().get(Size.Unit.BYTE);
      }
    }
    return totalSize;
  }
}
