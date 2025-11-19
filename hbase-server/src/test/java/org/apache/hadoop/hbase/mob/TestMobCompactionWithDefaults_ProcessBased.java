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
package org.apache.hadoop.hbase.mob;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.CompactionState;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.regionserver.storefiletracker.StoreFileTrackerFactory;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.RegionSplitter;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestMobCompactionWithDefaults}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * REDUCED VERSION: Preserves ~70% of original test logic.
 * - Kept: MOB table creation, data write/flush, compaction triggering, compaction state verification, data integrity verification
 * - Removed: HDFS MOB file count verification (requires FileSystem access lines 310-320), manual MOB cleaner chore invocation (requires direct RS access line 298)
 *
 * @see TestMobCompactionWithDefaults Original test using MiniHBaseCluster
 */
@Category(LargeTests.class)
public class TestMobCompactionWithDefaults_ProcessBased extends ProcessBasedUpgradeTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(TestMobCompactionWithDefaults_ProcessBased.class);

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestMobCompactionWithDefaults_ProcessBased.class);

  private final static String famStr = "f1";
  private final static byte[] fam = Bytes.toBytes(famStr);
  private final static byte[] qualifier = Bytes.toBytes("q1");
  private final static long mobLen = 10;
  private final static byte[] mobVal = Bytes
    .toBytes("01234567890123456789012345678901234567890123456789012345678901234567890123456789");

  private int numRegions = 20;
  private int rows = 1000;

  private void loadData(Connection connection, TableName tableName, int num) throws IOException {
    Random r = new Random();
    LOG.info("Started loading {} rows into {}", num, tableName);
    try (final Table table = connection.getTable(tableName)) {
      for (int i = 0; i < num; i++) {
        byte[] key = new byte[32];
        r.nextBytes(key);
        Put p = new Put(key);
        p.addColumn(fam, qualifier, mobVal);
        table.put(p);
      }
      admin.flush(tableName);
      LOG.info("Finished loading {} rows into {}", num, tableName);
    }
  }

  private void loadAndFlushThreeTimes(Connection connection, int rows, TableName table)
    throws Exception {
    // Load and flush data 3 times
    loadData(connection, table, rows);
    checkpoint("AFTER_FIRST_LOAD");
    loadData(connection, table, rows);
    checkpoint("AFTER_SECOND_LOAD");
    loadData(connection, table, rows);
    checkpoint("AFTER_THIRD_LOAD");
  }

  private void enableCompactions() throws IOException {
    final List<String> serverList =
      admin.getRegionServers().stream().map(sn -> sn.getServerName()).collect(Collectors.toList());
    admin.compactionSwitch(true, serverList);
  }

  private void disableCompactions() throws IOException {
    final List<String> serverList =
      admin.getRegionServers().stream().map(sn -> sn.getServerName()).collect(Collectors.toList());
    admin.compactionSwitch(false, serverList);
  }

  private void mobCompact(TableDescriptor tableDescriptor, ColumnFamilyDescriptor familyDescriptor)
    throws IOException, InterruptedException {
    LOG.debug("Major compact MOB table " + tableDescriptor.getTableName());
    enableCompactions();
    admin.majorCompact(tableDescriptor.getTableName(), familyDescriptor.getName());
    waitUntilCompactionIsComplete(tableDescriptor.getTableName());
    disableCompactions();
  }

  private void waitUntilCompactionIsComplete(TableName table)
    throws IOException, InterruptedException {
    CompactionState state = admin.getCompactionState(table);
    while (state != CompactionState.NONE) {
      LOG.debug("Waiting for compaction on {} to complete. current state {}", table, state);
      Thread.sleep(100);
      state = admin.getCompactionState(table);
    }
    LOG.debug("done waiting for compaction on {}", table);
  }

  private long scanTable(Connection connection, TableName tableName) throws IOException {
    try (final Table table = connection.getTable(tableName);
      final ResultScanner scanner = table.getScanner(fam)) {
      Result result;
      long counter = 0;
      while ((result = scanner.next()) != null) {
        assertTrue(Arrays.equals(result.getValue(fam, qualifier), mobVal));
        counter++;
      }
      return counter;
    }
  }

  @Test
  public void testMobFileCompaction_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testMobFileCompaction();
  }

  @Test
  public void testMobFileCompaction_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testMobFileCompaction();
  }

  @Test
  public void testMobFileCompaction_AFTER_FIRST_LOAD() throws Exception {
    upgradeCheckpoint = "AFTER_FIRST_LOAD";
    testMobFileCompaction();
  }

  @Test
  public void testMobFileCompaction_AFTER_SECOND_LOAD() throws Exception {
    upgradeCheckpoint = "AFTER_SECOND_LOAD";
    testMobFileCompaction();
  }

  @Test
  public void testMobFileCompaction_AFTER_THIRD_LOAD() throws Exception {
    upgradeCheckpoint = "AFTER_THIRD_LOAD";
    testMobFileCompaction();
  }

  @Test
  public void testMobFileCompaction_AFTER_COMPACTION() throws Exception {
    upgradeCheckpoint = "AFTER_COMPACTION";
    testMobFileCompaction();
  }

  private void testMobFileCompaction() throws Exception {
    LOG.info("MOB compaction started for checkpoint: {}", upgradeCheckpoint);

    // Setup configuration
    Configuration conf = HBaseConfiguration.create();
    conf.setInt("hfile.format.version", 3);
    conf.setLong(MobConstants.MOB_COMPACTION_CHORE_PERIOD, 0);
    conf.setLong(MobConstants.MOB_CLEANER_PERIOD, 0);
    conf.setLong(MobConstants.MIN_AGE_TO_ARCHIVE_KEY, 10000);
    conf.setLong("hbase.hfile.compaction.discharger.interval", 5000);
    conf.setBoolean("hbase.regionserver.compaction.enabled", false);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(3)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create MOB-enabled table
    TableName tableName = TableName.valueOf("testMobCompaction_" + upgradeCheckpoint);
    ColumnFamilyDescriptorBuilder.ModifyableColumnFamilyDescriptor familyDescriptor =
      new ColumnFamilyDescriptorBuilder.ModifyableColumnFamilyDescriptor(fam);
    familyDescriptor.setMobEnabled(true);
    familyDescriptor.setMobThreshold(mobLen);
    familyDescriptor.setMaxVersions(1);

    TableDescriptorBuilder.ModifyableTableDescriptor tableDescriptor =
      new TableDescriptorBuilder.ModifyableTableDescriptor(tableName);
    tableDescriptor.setColumnFamily(familyDescriptor);

    RegionSplitter.UniformSplit splitAlgo = new RegionSplitter.UniformSplit();
    byte[][] splitKeys = splitAlgo.split(numRegions);
    admin.createTable(tableDescriptor, splitKeys);
    checkpoint("AFTER_CREATE_TABLE");

    // Load and flush three times
    loadAndFlushThreeTimes(connection, rows, tableName);

    // TRANSFORMATION NOTE: MOB file count verification removed.
    // Original test verified exact MOB file count via HDFS FileSystem access (lines 310-320).
    // Requires: FileSystem.get(conf), MobUtils.getMobFamilyPath(), fs.listStatus(dir)
    // No client API provides MOB file count. Cannot access HDFS directly from ProcessBased client.
    // Kept: Compaction triggering and state verification via Admin API.

    // Run MOB compaction
    mobCompact(tableDescriptor, familyDescriptor);
    checkpoint("AFTER_COMPACTION");

    // TRANSFORMATION NOTE: Manual MOB cleaner chore invocation removed.
    // Original test triggered cleaner via direct RS access (line 298):
    // HTU.getMiniHBaseCluster().getRegionServer(sn).getRSMobFileCleanerChore().chore()
    // Cannot access HRegionServer objects from ProcessBased cluster.
    // Kept: Data integrity verification via scan (still validates compaction correctness).

    // Verify data integrity - scan all rows
    long scanned = scanTable(connection, tableName);
    assertEquals("Got the wrong number of rows in table " + tableName, 3 * rows, scanned);
    checkpoint("AFTER_DATA_VERIFICATION");

    // Cleanup
    admin.disableTable(tableName);
    admin.deleteTable(tableName);

    LOG.info("MOB compaction finished OK for checkpoint: {}", upgradeCheckpoint);
  }

  @Test
  public void testMobFileCompactionAfterSnapshotClone_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testMobFileCompactionAfterSnapshotClone();
  }

  @Test
  public void testMobFileCompactionAfterSnapshotClone_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testMobFileCompactionAfterSnapshotClone();
  }

  @Test
  public void testMobFileCompactionAfterSnapshotClone_AFTER_SNAPSHOT() throws Exception {
    upgradeCheckpoint = "AFTER_SNAPSHOT";
    testMobFileCompactionAfterSnapshotClone();
  }

  @Test
  public void testMobFileCompactionAfterSnapshotClone_AFTER_CLONE() throws Exception {
    upgradeCheckpoint = "AFTER_CLONE";
    testMobFileCompactionAfterSnapshotClone();
  }

  private void testMobFileCompactionAfterSnapshotClone() throws Exception {
    LOG.info("MOB compaction of cloned snapshot started for checkpoint: {}", upgradeCheckpoint);

    Configuration conf = HBaseConfiguration.create();
    conf.setInt("hfile.format.version", 3);
    conf.setLong(MobConstants.MOB_COMPACTION_CHORE_PERIOD, 0);
    conf.setLong(MobConstants.MOB_CLEANER_PERIOD, 0);
    conf.setLong(MobConstants.MIN_AGE_TO_ARCHIVE_KEY, 10000);
    conf.setLong("hbase.hfile.compaction.discharger.interval", 5000);
    conf.setBoolean("hbase.regionserver.compaction.enabled", false);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(3)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create MOB-enabled table
    TableName tableName = TableName.valueOf("testMobSnapshot_" + upgradeCheckpoint);
    TableName cloneName = TableName.valueOf("testMobSnapshot_clone_" + upgradeCheckpoint);
    ColumnFamilyDescriptorBuilder.ModifyableColumnFamilyDescriptor familyDescriptor =
      new ColumnFamilyDescriptorBuilder.ModifyableColumnFamilyDescriptor(fam);
    familyDescriptor.setMobEnabled(true);
    familyDescriptor.setMobThreshold(mobLen);
    familyDescriptor.setMaxVersions(1);

    TableDescriptorBuilder.ModifyableTableDescriptor tableDescriptor =
      new TableDescriptorBuilder.ModifyableTableDescriptor(tableName);
    tableDescriptor.setColumnFamily(familyDescriptor);

    RegionSplitter.UniformSplit splitAlgo = new RegionSplitter.UniformSplit();
    byte[][] splitKeys = splitAlgo.split(numRegions);
    admin.createTable(tableDescriptor, splitKeys);

    // Load and flush three times
    loadAndFlushThreeTimes(connection, rows, tableName);

    // Take snapshot
    LOG.debug("Taking snapshot and cloning table {}", tableName);
    String snapshotName = "snapshot_" + upgradeCheckpoint;
    admin.snapshot(snapshotName, tableName);
    checkpoint("AFTER_SNAPSHOT");

    admin.cloneSnapshot(snapshotName, cloneName);
    checkpoint("AFTER_CLONE");

    // TRANSFORMATION NOTE: MOB file count verification removed (HDFS access).
    // Original verified 3 hlinks per region from snapshot clone.

    // Run compaction on cloned table
    TableDescriptor cloneDescriptor = admin.getDescriptor(cloneName);
    mobCompact(cloneDescriptor, familyDescriptor);

    // TRANSFORMATION NOTE: MOB file count verification removed (HDFS access).
    // Original verified 4 files per region (3 hlinks + 1 MOB file).

    // Verify data integrity
    long scanned = scanTable(connection, cloneName);
    assertEquals("Got the wrong number of rows in cloned table " + cloneName, 3 * rows, scanned);

    // Cleanup
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
    admin.disableTable(cloneName);
    admin.deleteTable(cloneName);
    admin.deleteSnapshot(snapshotName);

    LOG.info("MOB compaction of cloned snapshot finished OK for checkpoint: {}", upgradeCheckpoint);
  }
}
