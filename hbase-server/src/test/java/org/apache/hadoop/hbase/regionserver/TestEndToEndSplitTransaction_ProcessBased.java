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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.ChoreService;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.MetaTableAccessor;
import org.apache.hadoop.hbase.NotServingRegionException;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.ScheduledChore;
import org.apache.hadoop.hbase.Stoppable;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.hadoop.hbase.util.StoppableImplementation;
import org.apache.hadoop.hbase.util.Threads;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.com.google.common.collect.Iterators;

/**
 * ProcessBased version of {@link TestEndToEndSplitTransaction}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * This is a REDUCED transformation that preserves the core split verification logic:
 * - testFromClientSideWhileSplitting (transformed)
 * - testCanSplitJustAfterASplit (removed - requires CompactSplitThread control and internal HStore access)
 *
 * @see TestEndToEndSplitTransaction Original test using MiniHBaseCluster
 */
@Category(LargeTests.class)
public class TestEndToEndSplitTransaction_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestEndToEndSplitTransaction_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestEndToEndSplitTransaction_ProcessBased.class);

  @Rule
  public TestName name = new TestName();

  // TRANSFORMATION NOTE: testCanSplitJustAfterASplit removed.
  // Requires CompactSplitThread control (setCompactionsEnabled), internal HStore/StoreFileReader
  // manipulation, and direct region.compact() calls - no client API equivalents exist.
  // Original test verified reference file locking prevents splits - requires deep internal access.

  /**
   * Tests that the client sees meta table changes as atomic during splits.
   * Checkpoint: NO_UPGRADE
   */
  @Test(timeout = 300000)
  public void testFromClientSideWhileSplitting_NO_UPGRADE() throws Throwable {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testFromClientSideWhileSplitting();
  }

  /**
   * Tests that the client sees meta table changes as atomic during splits.
   * Checkpoint: AFTER_CLUSTER_START
   */
  @Test(timeout = 300000)
  public void testFromClientSideWhileSplitting_AFTER_CLUSTER_START() throws Throwable {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testFromClientSideWhileSplitting();
  }

  /**
   * Tests that the client sees meta table changes as atomic during splits.
   * Checkpoint: AFTER_CREATE_TABLE
   */
  @Test(timeout = 300000)
  public void testFromClientSideWhileSplitting_AFTER_CREATE_TABLE() throws Throwable {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    testFromClientSideWhileSplitting();
  }

  /**
   * Tests that the client sees meta table changes as atomic during splits.
   * Checkpoint: AFTER_SPLIT
   */
  @Test(timeout = 300000)
  public void testFromClientSideWhileSplitting_AFTER_SPLIT() throws Throwable {
    upgradeCheckpoint = "AFTER_SPLIT";
    testFromClientSideWhileSplitting();
  }

  private void testFromClientSideWhileSplitting() throws Throwable {
    LOG.info("Starting testFromClientSideWhileSplitting");
    final TableName tableName = TableName.valueOf(name.getMethodName());
    final byte[] FAMILY = Bytes.toBytes("family");

    conf.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 10); // Increased for process-based cluster startup

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitClusterUp();

    // Wait for Master to be fully initialized before creating tables
    // This prevents PleaseHoldException: Master is initializing
    cluster.waitForActiveAndReadyMaster(60000);

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .build());
    Table table = connection.getTable(tableName);
    checkpoint("AFTER_CREATE_TABLE");

    Stoppable stopper = new StoppableImplementation();
    RegionSplitter regionSplitter = new RegionSplitter(table, connection, admin);
    RegionChecker regionChecker = new RegionChecker(conf, stopper, tableName);
    final ChoreService choreService = new ChoreService("TEST_SERVER");

    choreService.scheduleChore(regionChecker);
    regionSplitter.start();

    // wait until the splitter is finished
    regionSplitter.join();
    stopper.stop(null);

    checkpoint("AFTER_SPLIT");

    if (regionChecker.ex != null) {
      throw new AssertionError("regionChecker", regionChecker.ex);
    }

    if (regionSplitter.ex != null) {
      throw new AssertionError("regionSplitter", regionSplitter.ex);
    }

    // one final check
    regionChecker.verify();

    table.close();
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  static class RegionSplitter extends Thread {
    final Connection connection;
    Throwable ex;
    Table table;
    TableName tableName;
    byte[] family;
    Admin admin;

    RegionSplitter(Table table, Connection connection, Admin admin) throws IOException {
      this.table = table;
      this.tableName = table.getName();
      this.family = table.getDescriptor().getColumnFamilies()[0].getName();
      this.admin = admin;
      this.connection = connection;
    }

    @Override
    public void run() {
      try {
        Random random = ThreadLocalRandom.current();
        for (int i = 0; i < 5; i++) {
          List<RegionInfo> regions = MetaTableAccessor.getTableRegions(connection, tableName, true);
          if (regions.isEmpty()) {
            continue;
          }
          int regionIndex = random.nextInt(regions.size());

          // pick a random region and split it into two
          RegionInfo region = Iterators.get(regions.iterator(), regionIndex);

          // pick the mid split point
          int start = 0, end = Integer.MAX_VALUE;
          if (region.getStartKey().length > 0) {
            start = Bytes.toInt(region.getStartKey());
          }
          if (region.getEndKey().length > 0) {
            end = Bytes.toInt(region.getEndKey());
          }
          int mid = start + ((end - start) / 2);
          byte[] splitPoint = Bytes.toBytes(mid);

          // put some rows to the regions
          addData(start);
          addData(mid);

          // TRANSFORMATION NOTE: Original used flushAndBlockUntilDone/compactAndBlockUntilDone
          // which require rs.getOnlineRegion() access. Replaced with Admin API.
          flushRegion(admin, region.getRegionName());
          compactRegion(admin, region.getRegionName());

          log("Initiating region split for:" + region.getRegionNameAsString());
          try {
            admin.splitRegionAsync(region.getRegionName(), splitPoint);
            // wait until the split is complete
            blockUntilRegionSplit(connection, 50000, region.getRegionName(), true);

          } catch (NotServingRegionException ex) {
            // ignore
          }
        }
      } catch (Throwable ex) {
        this.ex = ex;
      }
    }

    void addData(int start) throws IOException {
      List<Put> puts = new ArrayList<>();
      for (int i = start; i < start + 100; i++) {
        Put put = new Put(Bytes.toBytes(i));
        put.addColumn(family, family, Bytes.toBytes(i));
        puts.add(put);
      }
      table.put(puts);
    }

    void flushRegion(Admin admin, byte[] regionName) throws IOException, InterruptedException {
      log("flushing region: " + Bytes.toStringBinary(regionName));
      admin.flushRegion(regionName);
      log("blocking until flush is complete: " + Bytes.toStringBinary(regionName));
      // Wait for flush to complete - no direct access to memstore size, use time-based wait
      Thread.sleep(2000);
    }

    void compactRegion(Admin admin, byte[] regionName) throws IOException, InterruptedException {
      log("Compacting region: " + Bytes.toStringBinary(regionName));
      admin.majorCompactRegion(regionName);
      log("blocking until compaction is complete: " + Bytes.toStringBinary(regionName));
      // Wait for compaction to complete - no direct access to store file count, use time-based wait
      Thread.sleep(3000);
    }
  }

  /**
   * Checks regions using MetaTableAccessor and HTable methods
   */
  static class RegionChecker extends ScheduledChore {
    Connection connection;
    Configuration conf;
    TableName tableName;
    Throwable ex;

    RegionChecker(Configuration conf, Stoppable stopper, TableName tableName) throws IOException {
      super("RegionChecker", stopper, 100);
      this.conf = conf;
      this.tableName = tableName;
      this.connection = ConnectionFactory.createConnection(conf);
    }

    /** verify region boundaries obtained from MetaScanner */
    void verifyRegionsUsingMetaTableAccessor() throws Exception {
      List<RegionInfo> regionList = MetaTableAccessor.getTableRegions(connection, tableName, true);
      verifyTableRegions(regionList.stream()
        .collect(Collectors.toCollection(() -> new TreeSet<>(RegionInfo.COMPARATOR))));
      regionList = MetaTableAccessor.getAllRegions(connection, true);
      verifyTableRegions(regionList.stream()
        .collect(Collectors.toCollection(() -> new TreeSet<>(RegionInfo.COMPARATOR))));
    }

    /** verify region boundaries obtained from HTable.getStartEndKeys() */
    void verifyRegionsUsingHTable() throws IOException {
      try (RegionLocator rl = connection.getRegionLocator(tableName)) {
        Pair<byte[][], byte[][]> keys = rl.getStartEndKeys();
        verifyStartEndKeys(keys);

        Set<RegionInfo> regions = new TreeSet<>(RegionInfo.COMPARATOR);
        for (HRegionLocation loc : rl.getAllRegionLocations()) {
          regions.add(loc.getRegion());
        }
        verifyTableRegions(regions);
      }
    }

    void verify() throws Exception {
      verifyRegionsUsingMetaTableAccessor();
      verifyRegionsUsingHTable();
    }

    void verifyTableRegions(Set<RegionInfo> regions) {
      log("Verifying " + regions.size() + " regions: " + regions);

      byte[][] startKeys = new byte[regions.size()][];
      byte[][] endKeys = new byte[regions.size()][];

      int i = 0;
      for (RegionInfo region : regions) {
        startKeys[i] = region.getStartKey();
        endKeys[i] = region.getEndKey();
        i++;
      }

      Pair<byte[][], byte[][]> keys = new Pair<>(startKeys, endKeys);
      verifyStartEndKeys(keys);
    }

    void verifyStartEndKeys(Pair<byte[][], byte[][]> keys) {
      byte[][] startKeys = keys.getFirst();
      byte[][] endKeys = keys.getSecond();
      assertEquals(startKeys.length, endKeys.length);
      assertTrue("Found 0 regions for the table", startKeys.length > 0);

      assertArrayEquals("Start key for the first region is not byte[0]", HConstants.EMPTY_START_ROW,
        startKeys[0]);
      byte[] prevEndKey = HConstants.EMPTY_START_ROW;

      // ensure that we do not have any gaps
      for (int i = 0; i < startKeys.length; i++) {
        assertArrayEquals(
          "Hole in hbase:meta is detected. prevEndKey=" + Bytes.toStringBinary(prevEndKey)
            + " ,regionStartKey=" + Bytes.toStringBinary(startKeys[i]),
          prevEndKey, startKeys[i]);
        prevEndKey = endKeys[i];
      }
      assertArrayEquals("End key for the last region is not byte[0]", HConstants.EMPTY_END_ROW,
        endKeys[endKeys.length - 1]);
    }

    @Override
    protected void chore() {
      try {
        verify();
      } catch (Throwable ex) {
        this.ex = ex;
        getStopper().stop("caught exception");
      }
    }
  }

  public static void log(String msg) {
    LOG.info(msg);
  }

  /**
   * Blocks until the region split is complete in hbase:meta and region server opens the daughters
   * TRANSFORMATION NOTE: removeCompactedFiles() step removed - requires internal HStore access.
   * Compacted file cleanup happens automatically.
   */
  public static void blockUntilRegionSplit(Connection conn, long timeout,
    final byte[] regionName, boolean waitForDaughters) throws IOException, InterruptedException {
    long start = EnvironmentEdgeManager.currentTime();
    log("blocking until region is split:" + Bytes.toStringBinary(regionName));
    RegionInfo daughterA = null, daughterB = null;
    try (Table metaTable = conn.getTable(TableName.META_TABLE_NAME)) {
      Result result = null;
      RegionInfo region = null;
      while ((EnvironmentEdgeManager.currentTime() - start) < timeout) {
        result = metaTable.get(new Get(regionName));
        if (result == null) {
          break;
        }

        region = MetaTableAccessor.getRegionInfo(result);
        if (region.isSplitParent()) {
          log("found parent region: " + region.toString());
          daughterA = MetaTableAccessor.getDaughterRegions(result).getFirst();
          daughterB = MetaTableAccessor.getDaughterRegions(result).getSecond();
          break;
        }
        Threads.sleep(100);
      }
      if (daughterA == null || daughterB == null) {
        throw new IOException("Failed to get daughters, daughterA=" + daughterA + ", daughterB="
          + daughterB + ", timeout=" + timeout + ", result=" + result + ", regionName="
          + Bytes.toString(regionName) + ", region=" + region);
      }

      // if we are here, this means the region split is complete or timed out
      if (waitForDaughters) {
        long rem = timeout - (EnvironmentEdgeManager.currentTime() - start);
        blockUntilRegionIsInMeta(conn, rem, daughterA);

        rem = timeout - (EnvironmentEdgeManager.currentTime() - start);
        blockUntilRegionIsInMeta(conn, rem, daughterB);

        rem = timeout - (EnvironmentEdgeManager.currentTime() - start);
        blockUntilRegionIsOpened(conn.getConfiguration(), rem, daughterA);

        rem = timeout - (EnvironmentEdgeManager.currentTime() - start);
        blockUntilRegionIsOpened(conn.getConfiguration(), rem, daughterB);

        // TRANSFORMATION NOTE: Original compacted daughters and removed compacted files.
        // Removed - requires rs.getOnlineRegion() and store.closeAndArchiveCompactedFiles().
        // Compacted file cleanup happens automatically in production.
      }
    }
  }

  public static void blockUntilRegionIsInMeta(Connection conn, long timeout, RegionInfo hri)
    throws IOException, InterruptedException {
    log("blocking until region is in META: " + hri.getRegionNameAsString());
    long start = EnvironmentEdgeManager.currentTime();
    while (EnvironmentEdgeManager.currentTime() - start < timeout) {
      HRegionLocation loc = MetaTableAccessor.getRegionLocation(conn, hri);
      if (loc != null && !loc.getRegion().isOffline()) {
        log("found region in META: " + hri.getRegionNameAsString());
        break;
      }
      Threads.sleep(100);
    }
  }

  public static void blockUntilRegionIsOpened(Configuration conf, long timeout, RegionInfo hri)
    throws IOException, InterruptedException {
    log("blocking until region is opened for reading:" + hri.getRegionNameAsString());
    long start = EnvironmentEdgeManager.currentTime();
    try (Connection conn = ConnectionFactory.createConnection(conf);
      Table table = conn.getTable(hri.getTable())) {
      byte[] row = hri.getStartKey();
      // Check for null/empty row. If we find one, use a key that is likely to be in first region.
      if (row == null || row.length <= 0) {
        row = new byte[] { '0' };
      }
      Get get = new Get(row);
      while (EnvironmentEdgeManager.currentTime() - start < timeout) {
        try {
          table.get(get);
          break;
        } catch (IOException ex) {
          // wait some more
        }
        Threads.sleep(100);
      }
    }
  }
}
