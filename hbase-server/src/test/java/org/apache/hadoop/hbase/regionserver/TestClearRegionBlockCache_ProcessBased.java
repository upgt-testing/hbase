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

import static org.junit.Assert.assertTrue;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.CacheEvictionStats;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.AsyncAdmin;
import org.apache.hadoop.hbase.client.AsyncConnection;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestClearRegionBlockCache}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Reduced version (67% logic preserved): 2 out of 3 test methods transformed
 * (testClearBlockCacheFromAdmin, testClearBlockCacheFromAsyncAdmin) with 4 checkpoint
 * variants (NO_UPGRADE, AFTER_CLUSTER_START per method). Both tests use client-side
 * Admin/AsyncAdmin.clearBlockCache() API. Verification via CacheEvictionStats returned
 * from API (evicted block count). Skipped testClearBlockCache which requires direct
 * BlockCache access (rs.getBlockCache().get(), blockCache.getBlockCount() lines 105-124).
 * Internal verification removed: cannot access BlockCache.getBlockCount() or
 * HBaseTestingUtility.getNumHFilesForRS() to verify exact block counts. Tests verify
 * that eviction occurred (stats.getEvictedBlocks() > 0) instead of exact counts.
 *
 * @see TestClearRegionBlockCache Original test using MiniHBaseCluster
 */
@Category(LargeTests.class)
public class TestClearRegionBlockCache_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestClearRegionBlockCache_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestClearRegionBlockCache_ProcessBased.class);
  private static final TableName TABLE_NAME = TableName.valueOf("testClearRegionBlockCache");
  private static final byte[] FAMILY = Bytes.toBytes("family");
  private static final byte[][] SPLIT_KEY = new byte[][] { Bytes.toBytes("5") };
  private static final int NUM_RS = 2;

  private void setupClusterAndTable(String cacheType) throws Exception {
    if (cacheType.equals("bucket")) {
      conf.set(HConstants.BUCKET_CACHE_IOENGINE_KEY, "offheap");
      conf.setInt(HConstants.BUCKET_CACHE_SIZE_KEY, 30);
    }

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(NUM_RS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    // Create table
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(TABLE_NAME);
    builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY));
    admin.createTable(builder.build(), SPLIT_KEY);

    // Load data
    try (Table table = connection.getTable(TABLE_NAME)) {
      for (int i = 1; i <= 10; i++) {
        Put put = new Put(Bytes.toBytes(i));
        put.addColumn(FAMILY, Bytes.toBytes("qual"), Bytes.toBytes("value" + i));
        table.put(put);
      }
    }
    admin.flush(TABLE_NAME);
  }

  @Test
  public void testClearBlockCacheFromAdmin_LRU_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupClusterAndTable("lru");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    testClearBlockCacheFromAdmin();
  }

  @Test
  public void testClearBlockCacheFromAdmin_LRU_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupClusterAndTable("lru");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    testClearBlockCacheFromAdmin();
  }

  @Test
  public void testClearBlockCacheFromAdmin_Bucket_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupClusterAndTable("bucket");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    testClearBlockCacheFromAdmin();
  }

  @Test
  public void testClearBlockCacheFromAdmin_Bucket_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupClusterAndTable("bucket");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    testClearBlockCacheFromAdmin();
  }

  @Test
  public void testClearBlockCacheFromAsyncAdmin_LRU_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupClusterAndTable("lru");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    testClearBlockCacheFromAsyncAdmin();
  }

  @Test
  public void testClearBlockCacheFromAsyncAdmin_LRU_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupClusterAndTable("lru");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    testClearBlockCacheFromAsyncAdmin();
  }

  @Test
  public void testClearBlockCacheFromAsyncAdmin_Bucket_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupClusterAndTable("bucket");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    testClearBlockCacheFromAsyncAdmin();
  }

  @Test
  public void testClearBlockCacheFromAsyncAdmin_Bucket_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupClusterAndTable("bucket");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    testClearBlockCacheFromAsyncAdmin();
  }

  private void testClearBlockCacheFromAdmin() throws Exception {
    // Scan to populate block cache
    try (Table table = connection.getTable(TABLE_NAME)) {
      table.getScanner(new Scan()).iterator().forEachRemaining(r -> {});
    }

    // Clear block cache via Admin API
    CacheEvictionStats stats = admin.clearBlockCache(TABLE_NAME);

    // Verify eviction occurred
    assertTrue("Expected some blocks to be evicted, but got: " + stats.getEvictedBlocks(),
      stats.getEvictedBlocks() > 0);
    LOG.info("Evicted {} blocks from cache", stats.getEvictedBlocks());
  }

  private void testClearBlockCacheFromAsyncAdmin() throws Exception {
    try (AsyncConnection asyncConn =
      ConnectionFactory.createAsyncConnection(cluster.getConfiguration()).get()) {
      AsyncAdmin asyncAdmin = asyncConn.getAdmin();

      // Scan to populate block cache
      try (Table table = connection.getTable(TABLE_NAME)) {
        table.getScanner(new Scan()).iterator().forEachRemaining(r -> {});
      }

      // Clear block cache via AsyncAdmin API
      CacheEvictionStats stats = asyncAdmin.clearBlockCache(TABLE_NAME).get();

      // Verify eviction occurred
      assertTrue("Expected some blocks to be evicted, but got: " + stats.getEvictedBlocks(),
        stats.getEvictedBlocks() > 0);
      LOG.info("Evicted {} blocks from cache", stats.getEvictedBlocks());
    }
  }
}
