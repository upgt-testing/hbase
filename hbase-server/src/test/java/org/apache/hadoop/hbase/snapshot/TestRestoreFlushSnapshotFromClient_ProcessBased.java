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
package org.apache.hadoop.hbase.snapshot;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.SnapshotType;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.master.snapshot.SnapshotManager;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.regionserver.snapshot.RegionServerSnapshotManager;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.RegionServerTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestRestoreFlushSnapshotFromClient}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Tests snapshot take/restore/clone operations using pure client APIs (Admin.snapshot,
 * Admin.restoreSnapshot, Admin.cloneSnapshot, Table operations).
 *
 * @see TestRestoreFlushSnapshotFromClient Original test using MiniHBaseCluster
 */
@Category({ RegionServerTests.class, MediumTests.class })
public class TestRestoreFlushSnapshotFromClient_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestRestoreFlushSnapshotFromClient_ProcessBased.class);

  private static final Logger LOG =
    LoggerFactory.getLogger(TestRestoreFlushSnapshotFromClient_ProcessBased.class);

  protected final byte[] FAMILY = Bytes.toBytes("cf");

  protected byte[] snapshotName0;
  protected byte[] snapshotName1;
  protected byte[] snapshotName2;
  protected int snapshot0Rows;
  protected int snapshot1Rows;
  protected TableName tableName;

  protected void createTable() throws Exception {
    TableDescriptor td = TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY)).build();

    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      admin.createTable(td);
    }
  }

  protected void loadData(TableName tableName, int rows) throws IOException {
    try (Connection connection = ConnectionFactory.createConnection(conf);
      Table table = connection.getTable(tableName)) {
      for (int i = 0; i < rows; i++) {
        Put put = new Put(Bytes.toBytes("row-" + i));
        put.addColumn(FAMILY, Bytes.toBytes("q"), Bytes.toBytes("value-" + i));
        table.put(put);
      }
    }
  }

  protected int countRows(TableName tableName) throws IOException {
    int count = 0;
    try (Connection connection = ConnectionFactory.createConnection(conf);
      Table table = connection.getTable(tableName)) {
      Scan scan = new Scan();
      try (ResultScanner scanner = table.getScanner(scan)) {
        for (Result result : scanner) {
          count++;
        }
      }
    }
    return count;
  }

  protected void verifyRowCount(TableName tableName, long expectedRows) throws IOException {
    long actualRows = countRows(tableName);
    assertEquals("Row count mismatch", expectedRows, actualRows);
  }

  protected void deleteAllSnapshots() throws IOException {
    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      for (org.apache.hadoop.hbase.client.SnapshotDescription snapshot : admin
        .listSnapshots()) {
        admin.deleteSnapshot(snapshot.getName());
      }
    }
  }

  private void setupTestData() throws Exception {
    long tid = EnvironmentEdgeManager.currentTime();
    tableName = TableName.valueOf("testtb-" + tid);
    snapshotName0 = Bytes.toBytes("snaptb0-" + tid);
    snapshotName1 = Bytes.toBytes("snaptb1-" + tid);
    snapshotName2 = Bytes.toBytes("snaptb2-" + tid);

    // create Table
    createTable();
    loadData(tableName, 500);
    snapshot0Rows = countRows(tableName);
    LOG.info("=== before snapshot with {} rows", snapshot0Rows);

    // take a snapshot
    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      admin.snapshot(Bytes.toString(snapshotName0), tableName, SnapshotType.FLUSH);
    }

    LOG.info("=== after snapshot with {} rows", snapshot0Rows);

    // insert more data
    loadData(tableName, 500);
    snapshot1Rows = countRows(tableName);
    LOG.info("=== before snapshot with {} rows", snapshot1Rows);

    // take a snapshot of the updated table
    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      admin.snapshot(Bytes.toString(snapshotName1), tableName, SnapshotType.FLUSH);
    }
    LOG.info("=== after snapshot with {} rows", snapshot1Rows);
  }

  @Test
  public void testTakeFlushSnapshot_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testTakeFlushSnapshotImpl();
  }

  @Test
  public void testTakeFlushSnapshot_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testTakeFlushSnapshotImpl();
  }

  private void testTakeFlushSnapshotImpl() throws Exception {
    conf.setInt("hbase.regionserver.msginterval", 100);
    conf.setInt("hbase.client.pause", 250);
    conf.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 6);
    conf.setBoolean("hbase.master.enabletable.roundrobin", true);
    conf.setBoolean(SnapshotManager.HBASE_SNAPSHOT_ENABLED, true);
    conf.setLong(RegionServerSnapshotManager.SNAPSHOT_TIMEOUT_MILLIS_KEY,
      RegionServerSnapshotManager.SNAPSHOT_TIMEOUT_MILLIS_DEFAULT * 2);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(3).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    setupTestData();

    tearDownTable();
  }

  @Test
  public void testRestoreSnapshot_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testRestoreSnapshotImpl();
  }

  @Test
  public void testRestoreSnapshot_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testRestoreSnapshotImpl();
  }

  @Test
  public void testRestoreSnapshot_AFTER_FIRST_SNAPSHOT() throws Exception {
    upgradeCheckpoint = "AFTER_FIRST_SNAPSHOT";
    testRestoreSnapshotImpl();
  }

  private void testRestoreSnapshotImpl() throws Exception {
    conf.setInt("hbase.regionserver.msginterval", 100);
    conf.setInt("hbase.client.pause", 250);
    conf.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 6);
    conf.setBoolean("hbase.master.enabletable.roundrobin", true);
    conf.setBoolean(SnapshotManager.HBASE_SNAPSHOT_ENABLED, true);
    conf.setLong(RegionServerSnapshotManager.SNAPSHOT_TIMEOUT_MILLIS_KEY,
      RegionServerSnapshotManager.SNAPSHOT_TIMEOUT_MILLIS_DEFAULT * 2);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(3).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    setupTestData();

    checkpoint("AFTER_FIRST_SNAPSHOT");

    verifyRowCount(tableName, snapshot1Rows);

    // Restore from snapshot-0
    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      admin.disableTable(tableName);
      admin.restoreSnapshot(snapshotName0);
      admin.enableTable(tableName);
    }
    LOG.info("=== after restore with {} row snapshot", snapshot0Rows);
    verifyRowCount(tableName, snapshot0Rows);

    // Restore from snapshot-1
    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      admin.disableTable(tableName);
      admin.restoreSnapshot(snapshotName1);
      admin.enableTable(tableName);
    }
    verifyRowCount(tableName, snapshot1Rows);

    tearDownTable();
  }

  @Test(expected = SnapshotDoesNotExistException.class)
  public void testCloneNonExistentSnapshot_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    conf.setBoolean(SnapshotManager.HBASE_SNAPSHOT_ENABLED, true);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(3).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    String snapshotName = "random-snapshot-" + EnvironmentEdgeManager.currentTime();
    TableName tableName =
      TableName.valueOf("random-table-" + EnvironmentEdgeManager.currentTime());

    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      admin.cloneSnapshot(snapshotName, tableName);
    }
  }

  @Test
  public void testCloneSnapshot_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testCloneSnapshotImpl();
  }

  @Test
  public void testCloneSnapshot_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testCloneSnapshotImpl();
  }

  private void testCloneSnapshotImpl() throws Exception {
    conf.setBoolean(SnapshotManager.HBASE_SNAPSHOT_ENABLED, true);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(3).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    setupTestData();

    TableName clonedTableName =
      TableName.valueOf("clonedtb-" + EnvironmentEdgeManager.currentTime());
    testCloneSnapshot(clonedTableName, snapshotName0, snapshot0Rows);
    testCloneSnapshot(clonedTableName, snapshotName1, snapshot1Rows);

    tearDownTable();
  }

  private void testCloneSnapshot(final TableName tableName, final byte[] snapshotName,
    int snapshotRows) throws IOException, InterruptedException {
    // create a new table from snapshot
    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      admin.cloneSnapshot(snapshotName, tableName);
    }
    verifyRowCount(tableName, snapshotRows);

    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      admin.disableTable(tableName);
      admin.deleteTable(tableName);
    }
  }

  @Test
  public void testRestoreSnapshotOfCloned_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testRestoreSnapshotOfClonedImpl();
  }

  @Test
  public void testRestoreSnapshotOfCloned_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testRestoreSnapshotOfClonedImpl();
  }

  private void testRestoreSnapshotOfClonedImpl() throws Exception {
    conf.setBoolean(SnapshotManager.HBASE_SNAPSHOT_ENABLED, true);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(3).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    setupTestData();

    TableName clonedTableName =
      TableName.valueOf("clonedtb-" + EnvironmentEdgeManager.currentTime());

    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      admin.cloneSnapshot(snapshotName0, clonedTableName);
    }
    verifyRowCount(clonedTableName, snapshot0Rows);

    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      admin.snapshot(Bytes.toString(snapshotName2), clonedTableName, SnapshotType.FLUSH);
      admin.disableTable(clonedTableName);
      admin.deleteTable(clonedTableName);

      admin.cloneSnapshot(snapshotName2, clonedTableName);
    }
    verifyRowCount(clonedTableName, snapshot0Rows);

    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      admin.disableTable(clonedTableName);
      admin.deleteTable(clonedTableName);
    }

    tearDownTable();
  }

  private void tearDownTable() throws Exception {
    deleteAllSnapshots();
    if (tableName != null) {
      try (Connection connection = ConnectionFactory.createConnection(conf);
        Admin admin = connection.getAdmin()) {
        if (admin.tableExists(tableName)) {
          admin.disableTable(tableName);
          admin.deleteTable(tableName);
        }
      }
    }
  }

  @After
  @Override
  public void tearDownTest() throws Exception {
    super.tearDownTest();
  }
}
