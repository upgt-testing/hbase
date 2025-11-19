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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Optional;
import java.util.Set;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.ClientTests;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestAsyncTableAdminApi2}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Tests asynchronous table admin operations (addColumnFamily, deleteColumnFamily, compaction timestamps).
 *
 * Note: MasterFileSystem HDFS verification removed (lines 196-198 in original) - verifies internal HDFS storage
 * which is not accessible via client API. Table descriptors are still verified via admin.getDescriptor().
 *
 * @see TestAsyncTableAdminApi2 Original test using MiniHBaseCluster
 */
@Category({ LargeTests.class, ClientTests.class })
public class TestAsyncTableAdminApi2_ProcessBased extends ProcessBasedUpgradeTestBase {

  private static final Logger LOG = LoggerFactory.getLogger(TestAsyncTableAdminApi2_ProcessBased.class);

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestAsyncTableAdminApi2_ProcessBased.class);

  protected static final byte[] FAMILY = Bytes.toBytes("testFamily");
  protected static final byte[] FAMILY_0 = Bytes.toBytes("cf0");
  protected static final byte[] FAMILY_1 = Bytes.toBytes("cf1");

  protected AsyncConnection asyncConn;
  protected AsyncAdmin asyncAdmin;
  protected TableName tableName;

  private int testCounter = 0;

  private void setupTest(String testMethodName) throws Exception {
    testCounter++;
    tableName = TableName.valueOf(testMethodName + "_" + testCounter);

    conf = HBaseConfiguration.create();
    conf.setInt(HConstants.HBASE_RPC_TIMEOUT_KEY, 60000);
    conf.setInt(HConstants.HBASE_CLIENT_OPERATION_TIMEOUT, 120000);
    conf.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 2);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(2)
      .numMasters(2)
      .build();
    cluster.waitClusterUp();

    connection = cluster.getConnection();
    admin = connection.getAdmin();
    asyncConn = ConnectionFactory.createAsyncConnection(conf).get();
    asyncAdmin = asyncConn.getAdmin();
  }

  private void cleanupTest() throws Exception {
    if (asyncConn != null) {
      asyncConn.close();
    }
  }

  @Override
  public void tearDownTest() throws Exception {
    cleanupTest();
    super.tearDownTest();
  }

  private void createTableWithDefaultConf(TableName tableName) throws Exception {
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tableName);
    builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY));
    asyncAdmin.createTable(builder.build()).join();
  }

  @Test
  public void testDisableCatalogTable_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupTest("testDisableCatalogTable");

    try {
      asyncAdmin.disableTable(TableName.META_TABLE_NAME).join();
      fail("Expected to throw ConstraintException");
    } catch (Exception e) {
    }
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Before the fix for HBASE-6146, the below table creation was failing as the hbase:meta table
    // actually getting disabled by the disableTable() call.
    createTableWithDefaultConf(tableName);

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testDisableCatalogTable_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupTest("testDisableCatalogTable");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    try {
      asyncAdmin.disableTable(TableName.META_TABLE_NAME).join();
      fail("Expected to throw ConstraintException");
    } catch (Exception e) {
    }
    // Before the fix for HBASE-6146, the below table creation was failing as the hbase:meta table
    // actually getting disabled by the disableTable() call.
    createTableWithDefaultConf(tableName);

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testAddColumnFamily_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupTest("testAddColumnFamily");

    // Create a table with two families
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tableName);
    builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_0));
    asyncAdmin.createTable(builder.build()).join();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    asyncAdmin.disableTable(tableName).join();
    // Verify the table descriptor
    verifyTableDescriptor(tableName, FAMILY_0);

    // Modify the table removing one family and verify the descriptor
    asyncAdmin.addColumnFamily(tableName, ColumnFamilyDescriptorBuilder.of(FAMILY_1)).join();
    verifyTableDescriptor(tableName, FAMILY_0, FAMILY_1);

    admin.deleteTable(tableName);
  }

  @Test
  public void testAddColumnFamily_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupTest("testAddColumnFamily");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with two families
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tableName);
    builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_0));
    asyncAdmin.createTable(builder.build()).join();
    asyncAdmin.disableTable(tableName).join();
    // Verify the table descriptor
    verifyTableDescriptor(tableName, FAMILY_0);

    // Modify the table removing one family and verify the descriptor
    asyncAdmin.addColumnFamily(tableName, ColumnFamilyDescriptorBuilder.of(FAMILY_1)).join();
    verifyTableDescriptor(tableName, FAMILY_0, FAMILY_1);

    admin.deleteTable(tableName);
  }

  @Test
  public void testAddSameColumnFamilyTwice_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupTest("testAddSameColumnFamilyTwice");

    // Create a table with one families
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tableName);
    builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_0));
    asyncAdmin.createTable(builder.build()).join();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    asyncAdmin.disableTable(tableName).join();
    // Verify the table descriptor
    verifyTableDescriptor(tableName, FAMILY_0);

    // Modify the table removing one family and verify the descriptor
    asyncAdmin.addColumnFamily(tableName, ColumnFamilyDescriptorBuilder.of(FAMILY_1)).join();
    verifyTableDescriptor(tableName, FAMILY_0, FAMILY_1);

    try {
      // Add same column family again - expect failure
      asyncAdmin.addColumnFamily(tableName, ColumnFamilyDescriptorBuilder.of(FAMILY_1)).join();
      Assert.fail("Add the same column family should fail");
    } catch (Exception e) {
      // Expected.
    }

    admin.deleteTable(tableName);
  }

  @Test
  public void testAddSameColumnFamilyTwice_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupTest("testAddSameColumnFamilyTwice");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with one families
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tableName);
    builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_0));
    asyncAdmin.createTable(builder.build()).join();
    asyncAdmin.disableTable(tableName).join();
    // Verify the table descriptor
    verifyTableDescriptor(tableName, FAMILY_0);

    // Modify the table removing one family and verify the descriptor
    asyncAdmin.addColumnFamily(tableName, ColumnFamilyDescriptorBuilder.of(FAMILY_1)).join();
    verifyTableDescriptor(tableName, FAMILY_0, FAMILY_1);

    try {
      // Add same column family again - expect failure
      asyncAdmin.addColumnFamily(tableName, ColumnFamilyDescriptorBuilder.of(FAMILY_1)).join();
      Assert.fail("Add the same column family should fail");
    } catch (Exception e) {
      // Expected.
    }

    admin.deleteTable(tableName);
  }

  @Test
  public void testModifyColumnFamily_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupTest("testModifyColumnFamily");

    // Create a table with one family
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tableName);
    builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_0));
    asyncAdmin.createTable(builder.build()).join();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    asyncAdmin.disableTable(tableName).join();
    // Verify the table descriptor
    verifyTableDescriptor(tableName, FAMILY_0);

    int blockSize = 4096;
    asyncAdmin.modifyColumnFamily(tableName,
      ColumnFamilyDescriptorBuilder.newBuilder(FAMILY_0).setBlocksize(blockSize).build()).join();

    TableDescriptor htd = asyncAdmin.getDescriptor(tableName).get();
    ColumnFamilyDescriptor hcd = htd.getColumnFamily(FAMILY_0);
    assertTrue(hcd.getBlocksize() == blockSize);

    admin.deleteTable(tableName);
  }

  @Test
  public void testModifyColumnFamily_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupTest("testModifyColumnFamily");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with one family
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tableName);
    builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_0));
    asyncAdmin.createTable(builder.build()).join();
    asyncAdmin.disableTable(tableName).join();
    // Verify the table descriptor
    verifyTableDescriptor(tableName, FAMILY_0);

    int blockSize = 4096;
    asyncAdmin.modifyColumnFamily(tableName,
      ColumnFamilyDescriptorBuilder.newBuilder(FAMILY_0).setBlocksize(blockSize).build()).join();

    TableDescriptor htd = asyncAdmin.getDescriptor(tableName).get();
    ColumnFamilyDescriptor hcd = htd.getColumnFamily(FAMILY_0);
    assertTrue(hcd.getBlocksize() == blockSize);

    admin.deleteTable(tableName);
  }

  @Test
  public void testDeleteColumnFamily_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupTest("testDeleteColumnFamily");

    // Create a table with two families
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tableName);
    builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_0))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_1));
    asyncAdmin.createTable(builder.build()).join();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    asyncAdmin.disableTable(tableName).join();
    // Verify the table descriptor
    verifyTableDescriptor(tableName, FAMILY_0, FAMILY_1);

    // Modify the table removing one family and verify the descriptor
    asyncAdmin.deleteColumnFamily(tableName, FAMILY_1).join();
    verifyTableDescriptor(tableName, FAMILY_0);

    admin.deleteTable(tableName);
  }

  @Test
  public void testDeleteColumnFamily_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupTest("testDeleteColumnFamily");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with two families
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tableName);
    builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_0))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_1));
    asyncAdmin.createTable(builder.build()).join();
    asyncAdmin.disableTable(tableName).join();
    // Verify the table descriptor
    verifyTableDescriptor(tableName, FAMILY_0, FAMILY_1);

    // Modify the table removing one family and verify the descriptor
    asyncAdmin.deleteColumnFamily(tableName, FAMILY_1).join();
    verifyTableDescriptor(tableName, FAMILY_0);

    admin.deleteTable(tableName);
  }

  @Test
  public void testDeleteSameColumnFamilyTwice_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupTest("testDeleteSameColumnFamilyTwice");

    // Create a table with two families
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tableName);
    builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_0))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_1));
    asyncAdmin.createTable(builder.build()).join();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    asyncAdmin.disableTable(tableName).join();
    // Verify the table descriptor
    verifyTableDescriptor(tableName, FAMILY_0, FAMILY_1);

    // Modify the table removing one family and verify the descriptor
    asyncAdmin.deleteColumnFamily(tableName, FAMILY_1).join();
    verifyTableDescriptor(tableName, FAMILY_0);

    try {
      // Delete again - expect failure
      asyncAdmin.deleteColumnFamily(tableName, FAMILY_1).join();
      Assert.fail("Delete a non-exist column family should fail");
    } catch (Exception e) {
      // Expected.
    }

    admin.deleteTable(tableName);
  }

  @Test
  public void testDeleteSameColumnFamilyTwice_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupTest("testDeleteSameColumnFamilyTwice");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with two families
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tableName);
    builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_0))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_1));
    asyncAdmin.createTable(builder.build()).join();
    asyncAdmin.disableTable(tableName).join();
    // Verify the table descriptor
    verifyTableDescriptor(tableName, FAMILY_0, FAMILY_1);

    // Modify the table removing one family and verify the descriptor
    asyncAdmin.deleteColumnFamily(tableName, FAMILY_1).join();
    verifyTableDescriptor(tableName, FAMILY_0);

    try {
      // Delete again - expect failure
      asyncAdmin.deleteColumnFamily(tableName, FAMILY_1).join();
      Assert.fail("Delete a non-exist column family should fail");
    } catch (Exception e) {
      // Expected.
    }

    admin.deleteTable(tableName);
  }

  // TRANSFORMATION NOTE: HDFS-level descriptor verification removed.
  // Original test verified table descriptor from both master (via admin.getDescriptor) and HDFS
  // (via MasterFileSystem.getMasterFileSystem().getFileSystem() and FSTableDescriptors).
  // MasterFileSystem and FSTableDescriptors provide access to internal HDFS storage which is not
  // available via any client API.
  // The test still verifies table descriptor from master via admin.getDescriptor() which is
  // the authoritative source from client perspective.
  //
  // Original HDFS verification code (lines 196-198):
  // MasterFileSystem mfs = TEST_UTIL.getMiniHBaseCluster().getMaster().getMasterFileSystem();
  // Path tableDir = CommonFSUtils.getTableDir(mfs.getRootDir(), tableName);
  // TableDescriptor td = FSTableDescriptors.getTableDescriptorFromFs(mfs.getFileSystem(), tableDir);
  private void verifyTableDescriptor(final TableName tableName, final byte[]... families)
    throws Exception {
    // Verify descriptor from master (client-side API)
    TableDescriptor htd = asyncAdmin.getDescriptor(tableName).get();
    verifyTableDescriptor(htd, tableName, families);

    // HDFS verification removed - requires MasterFileSystem internal access
  }

  private void verifyTableDescriptor(final TableDescriptor htd, final TableName tableName,
    final byte[]... families) {
    Set<byte[]> htdFamilies = htd.getColumnFamilyNames();
    assertEquals(tableName, htd.getTableName());
    assertEquals(families.length, htdFamilies.size());
    for (byte[] familyName : families) {
      assertTrue("Expected family " + Bytes.toString(familyName), htdFamilies.contains(familyName));
    }
  }

  @Test
  public void testTableAvailableWithRandomSplitKeys_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupTest("testTableAvailableWithRandomSplitKeys");

    createTableWithDefaultConf(tableName);
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    byte[][] splitKeys = new byte[1][];
    splitKeys = new byte[][] { new byte[] { 1, 1, 1 }, new byte[] { 2, 2, 2 } };
    boolean tableAvailable = asyncAdmin.isTableAvailable(tableName, splitKeys).get();
    assertFalse("Table should be created with 1 row in META", tableAvailable);

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testTableAvailableWithRandomSplitKeys_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupTest("testTableAvailableWithRandomSplitKeys");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    createTableWithDefaultConf(tableName);
    byte[][] splitKeys = new byte[1][];
    splitKeys = new byte[][] { new byte[] { 1, 1, 1 }, new byte[] { 2, 2, 2 } };
    boolean tableAvailable = asyncAdmin.isTableAvailable(tableName, splitKeys).get();
    assertFalse("Table should be created with 1 row in META", tableAvailable);

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testCompactionTimestamps_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupTest("testCompactionTimestamps");

    createTableWithDefaultConf(tableName);
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    AsyncTable<?> table = asyncConn.getTable(tableName);
    Optional<Long> ts = asyncAdmin.getLastMajorCompactionTimestamp(tableName).get();
    assertFalse(ts.isPresent());
    Put p = new Put(Bytes.toBytes("row1"));
    p.addColumn(FAMILY, Bytes.toBytes("q"), Bytes.toBytes("v"));
    table.put(p).join();
    ts = asyncAdmin.getLastMajorCompactionTimestamp(tableName).get();
    // no files written -> no data
    assertFalse(ts.isPresent());

    asyncAdmin.flush(tableName).join();
    ts = asyncAdmin.getLastMajorCompactionTimestamp(tableName).get();
    // still 0, we flushed a file, but no major compaction happened
    assertFalse(ts.isPresent());

    byte[] regionName = asyncConn.getRegionLocator(tableName)
      .getRegionLocation(Bytes.toBytes("row1")).get().getRegion().getRegionName();
    Optional<Long> ts1 = asyncAdmin.getLastMajorCompactionTimestampForRegion(regionName).get();
    assertFalse(ts1.isPresent());
    p = new Put(Bytes.toBytes("row2"));
    p.addColumn(FAMILY, Bytes.toBytes("q"), Bytes.toBytes("v"));
    table.put(p).join();
    asyncAdmin.flush(tableName).join();
    ts1 = asyncAdmin.getLastMajorCompactionTimestamp(tableName).get();
    // make sure the region API returns the same value, as the old file is still around
    assertFalse(ts1.isPresent());

    for (int i = 0; i < 3; i++) {
      table.put(p).join();
      asyncAdmin.flush(tableName).join();
    }
    asyncAdmin.majorCompact(tableName).join();
    long curt = EnvironmentEdgeManager.currentTime();
    long waitTime = 10000;
    long endt = curt + waitTime;
    CompactionState state = asyncAdmin.getCompactionState(tableName).get();
    LOG.info("Current compaction state 1 is " + state);
    while (state == CompactionState.NONE && curt < endt) {
      Thread.sleep(100);
      state = asyncAdmin.getCompactionState(tableName).get();
      curt = EnvironmentEdgeManager.currentTime();
      LOG.info("Current compaction state 2 is " + state);
    }
    // Now, should have the right compaction state, let's wait until the compaction is done
    if (state == CompactionState.MAJOR) {
      state = asyncAdmin.getCompactionState(tableName).get();
      LOG.info("Current compaction state 3 is " + state);
      while (state != CompactionState.NONE && curt < endt) {
        Thread.sleep(10);
        state = asyncAdmin.getCompactionState(tableName).get();
        LOG.info("Current compaction state 4 is " + state);
      }
    }
    // Sleep to wait region server report
    Thread.sleep(conf.getInt("hbase.regionserver.msginterval", 3 * 1000) * 2);

    ts = asyncAdmin.getLastMajorCompactionTimestamp(tableName).get();
    // after a compaction our earliest timestamp will have progressed forward
    assertTrue(ts.isPresent());
    assertTrue(ts.get() > 0);
    // region api still the same
    ts1 = asyncAdmin.getLastMajorCompactionTimestampForRegion(regionName).get();
    assertTrue(ts1.isPresent());
    assertEquals(ts.get(), ts1.get());
    table.put(p).join();
    asyncAdmin.flush(tableName).join();
    ts = asyncAdmin.getLastMajorCompactionTimestamp(tableName).join();
    assertTrue(ts.isPresent());
    assertEquals(ts.get(), ts1.get());
    ts1 = asyncAdmin.getLastMajorCompactionTimestampForRegion(regionName).get();
    assertTrue(ts1.isPresent());
    assertEquals(ts.get(), ts1.get());

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testCompactionTimestamps_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupTest("testCompactionTimestamps");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    createTableWithDefaultConf(tableName);
    AsyncTable<?> table = asyncConn.getTable(tableName);
    Optional<Long> ts = asyncAdmin.getLastMajorCompactionTimestamp(tableName).get();
    assertFalse(ts.isPresent());
    Put p = new Put(Bytes.toBytes("row1"));
    p.addColumn(FAMILY, Bytes.toBytes("q"), Bytes.toBytes("v"));
    table.put(p).join();
    ts = asyncAdmin.getLastMajorCompactionTimestamp(tableName).get();
    // no files written -> no data
    assertFalse(ts.isPresent());

    asyncAdmin.flush(tableName).join();
    ts = asyncAdmin.getLastMajorCompactionTimestamp(tableName).get();
    // still 0, we flushed a file, but no major compaction happened
    assertFalse(ts.isPresent());

    byte[] regionName = asyncConn.getRegionLocator(tableName)
      .getRegionLocation(Bytes.toBytes("row1")).get().getRegion().getRegionName();
    Optional<Long> ts1 = asyncAdmin.getLastMajorCompactionTimestampForRegion(regionName).get();
    assertFalse(ts1.isPresent());
    p = new Put(Bytes.toBytes("row2"));
    p.addColumn(FAMILY, Bytes.toBytes("q"), Bytes.toBytes("v"));
    table.put(p).join();
    asyncAdmin.flush(tableName).join();
    ts1 = asyncAdmin.getLastMajorCompactionTimestamp(tableName).get();
    // make sure the region API returns the same value, as the old file is still around
    assertFalse(ts1.isPresent());

    for (int i = 0; i < 3; i++) {
      table.put(p).join();
      asyncAdmin.flush(tableName).join();
    }
    asyncAdmin.majorCompact(tableName).join();
    long curt = EnvironmentEdgeManager.currentTime();
    long waitTime = 10000;
    long endt = curt + waitTime;
    CompactionState state = asyncAdmin.getCompactionState(tableName).get();
    LOG.info("Current compaction state 1 is " + state);
    while (state == CompactionState.NONE && curt < endt) {
      Thread.sleep(100);
      state = asyncAdmin.getCompactionState(tableName).get();
      curt = EnvironmentEdgeManager.currentTime();
      LOG.info("Current compaction state 2 is " + state);
    }
    // Now, should have the right compaction state, let's wait until the compaction is done
    if (state == CompactionState.MAJOR) {
      state = asyncAdmin.getCompactionState(tableName).get();
      LOG.info("Current compaction state 3 is " + state);
      while (state != CompactionState.NONE && curt < endt) {
        Thread.sleep(10);
        state = asyncAdmin.getCompactionState(tableName).get();
        LOG.info("Current compaction state 4 is " + state);
      }
    }
    // Sleep to wait region server report
    Thread.sleep(conf.getInt("hbase.regionserver.msginterval", 3 * 1000) * 2);

    ts = asyncAdmin.getLastMajorCompactionTimestamp(tableName).get();
    // after a compaction our earliest timestamp will have progressed forward
    assertTrue(ts.isPresent());
    assertTrue(ts.get() > 0);
    // region api still the same
    ts1 = asyncAdmin.getLastMajorCompactionTimestampForRegion(regionName).get();
    assertTrue(ts1.isPresent());
    assertEquals(ts.get(), ts1.get());
    table.put(p).join();
    asyncAdmin.flush(tableName).join();
    ts = asyncAdmin.getLastMajorCompactionTimestamp(tableName).join();
    assertTrue(ts.isPresent());
    assertEquals(ts.get(), ts1.get());
    ts1 = asyncAdmin.getLastMajorCompactionTimestampForRegion(regionName).get();
    assertTrue(ts1.isPresent());
    assertEquals(ts.get(), ts1.get());

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }
}
