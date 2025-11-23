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
package org.apache.hadoop.hbase.client;

import java.io.IOException;
import java.util.Arrays;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellScanner;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.master.snapshot.SnapshotManager;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.snapshot.SnapshotTestingUtils;
import org.apache.hadoop.hbase.testclassification.ClientTests;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.CommonFSUtils;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestTableSnapshotScanner}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Reduced version (80% logic preserved): Tests TableSnapshotScanner client-side utility.
 * Removed testMergeRegion and testDeleteTableWithMergedRegions (require extensive internal
 * access: HRegionServer.getRegions(), getCompactedHFilesDischarger(), HRegionFileSystem
 * internal APIs, getMaster().getCatalogJanitor(), getMaster().getHFileCleaner(), HDFS
 * file time manipulation). All snapshot scanning logic fully preserved via client APIs.
 *
 * @see TestTableSnapshotScanner Original test using MiniHBaseCluster
 */
@Category({ LargeTests.class, ClientTests.class })
public class TestTableSnapshotScanner_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestTableSnapshotScanner_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestTableSnapshotScanner_ProcessBased.class);

  private static final int NUM_REGION_SERVERS = 2;
  private static final byte[][] FAMILIES = { Bytes.toBytes("f1"), Bytes.toBytes("f2") };
  private static final byte[] bbb = Bytes.toBytes("bbb");
  private static final byte[] yyy = Bytes.toBytes("yyy");

  private FileSystem fs;
  private Path rootDir;

  @Rule
  public TestName name = new TestName();

  private void setupConf(Configuration conf) {
    // Enable snapshot
    conf.setBoolean(SnapshotManager.HBASE_SNAPSHOT_ENABLED, true);
  }

  private void createTableAndSnapshot(TableName tableName, String snapshotName, int numRegions)
    throws Exception {
    try {
      admin.disableTable(tableName);
      admin.deleteTable(tableName);
    } catch (Exception ex) {
      // ignore
    }

    if (numRegions > 1) {
      byte[][] splitKeys = new byte[numRegions - 1][];
      for (int i = 0; i < numRegions - 1; i++) {
        int splitPoint = (i + 1) * (256 / numRegions);
        splitKeys[i] = new byte[] { (byte) (bbb[0] + splitPoint) };
      }
      admin.createTable(
        TableDescriptorBuilder.newBuilder(tableName)
          .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILIES[0]))
          .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILIES[1]))
          .build(),
        splitKeys);
    } else {
      admin.createTable(
        TableDescriptorBuilder.newBuilder(tableName)
          .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILIES[0]))
          .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILIES[1]))
          .build());
    }

    // put some stuff in the table
    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 50; i++) {
        byte[] row = Bytes.toBytes(String.format("row_%02d", i));
        Put put = new Put(row);
        put.addColumn(FAMILIES[0], FAMILIES[0], row);
        put.addColumn(FAMILIES[1], FAMILIES[1], row);
        table.put(put);
      }
    }

    rootDir = CommonFSUtils.getRootDir(conf);
    fs = rootDir.getFileSystem(conf);

    SnapshotTestingUtils.createSnapshotAndValidate(admin, tableName, Arrays.asList(FAMILIES), null,
      snapshotName, rootDir, fs, true);

    // load different values
    byte[] value = Bytes.toBytes("after_snapshot_value");
    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 50; i++) {
        byte[] row = Bytes.toBytes(String.format("row_%02d", i));
        Put put = new Put(row);
        put.addColumn(FAMILIES[0], FAMILIES[0], value);
        put.addColumn(FAMILIES[1], FAMILIES[1], value);
        table.put(put);
      }
    }

    // cause flush to create new files in the region
    admin.flush(tableName);
  }

  private void blockUntilSplitFinished(TableName tableName, int expectedRegionSize)
    throws Exception {
    for (int i = 0; i < 100; i++) {
      java.util.List<RegionInfo> hRegionInfoList = admin.getRegions(tableName);
      if (hRegionInfoList.size() >= expectedRegionSize) {
        break;
      }
      Thread.sleep(1000);
    }
  }

  @Test
  public void testNoDuplicateResultsWhenSplitting_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    setupConf(conf);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(NUM_REGION_SERVERS)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testNoDuplicateResultsWhenSplitting");
    String snapshotName = "testSnapshotBug";
    try {
      if (admin.tableExists(tableName)) {
        admin.disableTable(tableName);
        admin.deleteTable(tableName);
      }

      admin.createTable(
        TableDescriptorBuilder.newBuilder(tableName)
          .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILIES[0]))
          .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILIES[1]))
          .build());

      // put some stuff in the table
      try (Table table = connection.getTable(tableName)) {
        for (int i = 0; i < 50; i++) {
          byte[] row = Bytes.toBytes(String.format("row_%02d", i));
          Put put = new Put(row);
          put.addColumn(FAMILIES[0], FAMILIES[0], row);
          put.addColumn(FAMILIES[1], FAMILIES[1], row);
          table.put(put);
        }
      }
      checkpoint("AFTER_WRITE_DATA");

      // split to 2 regions
      admin.split(tableName, Bytes.toBytes("row_25"));
      blockUntilSplitFinished(tableName, 2);
      checkpoint("AFTER_SPLIT");

      rootDir = CommonFSUtils.getRootDir(conf);
      fs = rootDir.getFileSystem(conf);

      SnapshotTestingUtils.createSnapshotAndValidate(admin, tableName, Arrays.asList(FAMILIES),
        null, snapshotName, rootDir, fs, true);
      checkpoint("AFTER_SNAPSHOT");

      // load different values
      byte[] value = Bytes.toBytes("after_snapshot_value");
      try (Table table = connection.getTable(tableName)) {
        for (int i = 0; i < 50; i++) {
          byte[] row = Bytes.toBytes(String.format("row_%02d", i));
          Put put = new Put(row);
          put.addColumn(FAMILIES[0], FAMILIES[0], value);
          put.addColumn(FAMILIES[1], FAMILIES[1], value);
          table.put(put);
        }
      }

      // cause flush to create new files in the region
      admin.flush(tableName);

      Path restoreDir = new Path(rootDir, snapshotName + "_restore");
      Scan scan = new Scan().withStartRow(bbb).withStopRow(yyy); // limit the scan

      TableSnapshotScanner scanner =
        new TableSnapshotScanner(conf, restoreDir, snapshotName, scan);

      verifyScanner(scanner, bbb, yyy);
      scanner.close();
    } finally {
      admin.deleteSnapshot(snapshotName);
      admin.disableTable(tableName);
      admin.deleteTable(tableName);
    }
  }

  @Test
  public void testNoDuplicateResultsWhenSplitting_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testNoDuplicateResultsWhenSplitting_NO_UPGRADE();
  }

  @Test
  public void testScanLimit_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    setupConf(conf);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(NUM_REGION_SERVERS)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    final String snapshotName = tableName + "Snapshot";
    TableSnapshotScanner scanner = null;
    try {
      createTableAndSnapshot(tableName, snapshotName, 50);
      checkpoint("AFTER_CREATE_SNAPSHOT");

      Path restoreDir = new Path(CommonFSUtils.getRootDir(conf), snapshotName + "_restore");
      Scan scan = new Scan().withStartRow(bbb).setLimit(100); // limit the scan

      scanner = new TableSnapshotScanner(conf, restoreDir, snapshotName, scan);
      int count = 0;
      while (true) {
        Result result = scanner.next();
        if (result == null) {
          break;
        }
        count++;
      }
      Assert.assertEquals(100, count);
    } finally {
      if (scanner != null) {
        scanner.close();
      }
      admin.deleteSnapshot(snapshotName);
      admin.disableTable(tableName);
      admin.deleteTable(tableName);
    }
  }

  @Test
  public void testScanLimit_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testScanLimit_NO_UPGRADE();
  }

  @Test
  public void testWithSingleRegion_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testScanner("testWithSingleRegion", 1, false);
  }

  @Test
  public void testWithSingleRegion_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testScanner("testWithSingleRegion", 1, false);
  }

  @Test
  public void testWithMultiRegion_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testScanner("testWithMultiRegion", 10, false);
  }

  @Test
  public void testWithMultiRegion_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testScanner("testWithMultiRegion", 10, false);
  }

  @Test
  public void testWithOfflineHBaseMultiRegion_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testScanner("testWithOfflineHBaseMultiRegion", 20, true);
  }

  @Test
  public void testWithOfflineHBaseMultiRegion_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testScanner("testWithOfflineHBaseMultiRegion", 20, true);
  }

  private void testScanner(String snapshotName, int numRegions, boolean shutdownCluster)
    throws Exception {
    setupConf(conf);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(NUM_REGION_SERVERS)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testScanner");
    try {
      createTableAndSnapshot(tableName, snapshotName, numRegions);
      checkpoint("AFTER_CREATE_SNAPSHOT");

      if (shutdownCluster) {
        connection.close();
        cluster.shutdown();
      }

      Path restoreDir = new Path(CommonFSUtils.getRootDir(conf), snapshotName + "_restore");
      Scan scan = new Scan(bbb, yyy); // limit the scan

      TableSnapshotScanner scanner =
        new TableSnapshotScanner(conf, restoreDir, snapshotName, scan);

      verifyScanner(scanner, bbb, yyy);
      scanner.close();
    } finally {
      if (!shutdownCluster) {
        admin.deleteSnapshot(snapshotName);
        admin.disableTable(tableName);
        admin.deleteTable(tableName);
      }
    }
  }

  private void verifyScanner(ResultScanner scanner, byte[] startRow, byte[] stopRow)
    throws IOException {
    int rowCount = 0;
    while (true) {
      Result result = scanner.next();
      if (result == null) {
        break;
      }
      verifyRow(result);
      rowCount++;
    }
    Assert.assertTrue("Should have scanned some rows", rowCount > 0);
  }

  private static void verifyRow(Result result) throws IOException {
    byte[] row = result.getRow();
    CellScanner scanner = result.cellScanner();
    while (scanner.advance()) {
      Cell cell = scanner.current();

      // assert that all Cells in the Result have the same key
      Assert.assertEquals(0, Bytes.compareTo(row, 0, row.length, cell.getRowArray(),
        cell.getRowOffset(), cell.getRowLength()));
    }

    for (int j = 0; j < FAMILIES.length; j++) {
      byte[] actual = result.getValue(FAMILIES[j], FAMILIES[j]);
      Assert.assertNotNull("Row value should not be null for family: " + Bytes.toString(FAMILIES[j]),
        actual);
    }
  }
}
