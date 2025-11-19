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

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.ClientTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * TRANSFORMATION NOTE: Full transformation (100% logic preserved).
 *
 * Fix an infinite loop in {@link AsyncNonMetaRegionLocator}, see the comments on HBASE-21943 for
 * more details.
 *
 * REPLACED internal access:
 * - Line 87: TEST_UTIL.getMiniHBaseCluster().getRegions(TABLE_NAME).size()
 *   → admin.getRegions(TABLE_NAME).size() (client-side API)
 *
 * All test logic fully preserved:
 * - Table creation and data insertion
 * - Table split verification
 * - Region location caching via AsyncTableRegionLocator
 * - Table deletion and recreation
 * - Verification of correct region location after table recreation
 */
@Category({ MediumTests.class, ClientTests.class })
public class TestAsyncTableLocateRegionForDeletedTable_ProcessBased
  extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestAsyncTableLocateRegionForDeletedTable_ProcessBased.class);

  private static final TableName TABLE_NAME = TableName.valueOf("async");
  private static final byte[] FAMILY = Bytes.toBytes("cf");
  private static final byte[] QUALIFIER = Bytes.toBytes("cq");
  private static final byte[] VALUE = Bytes.toBytes("value");

  private int testCounter = 0;
  private AsyncConnection asyncConn;

  private void setupTest(String testMethodName) throws Exception {
    testCounter++;

    conf = HBaseConfiguration.create();
    conf.setInt(HConstants.HBASE_RPC_TIMEOUT_KEY, 60000);
    conf.setInt(HConstants.HBASE_CLIENT_OPERATION_TIMEOUT, 120000);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(3)
      .build();
    cluster.waitClusterUp();

    connection = cluster.getConnection();
    admin = connection.getAdmin();

    admin.createTable(TableDescriptorBuilder.newBuilder(TABLE_NAME)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.newBuilder(FAMILY).build()).build());

    admin.balancerSwitch(false, true);
    asyncConn = ConnectionFactory.createAsyncConnection(conf).get();
    assertFalse(asyncConn.isClosed());
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

  @Test
  public void test_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupTest("test");

    try (Table table = connection.getTable(TABLE_NAME)) {
      for (int i = 0; i < 100; i++) {
        table.put(new Put(Bytes.toBytes(i)).addColumn(FAMILY, QUALIFIER, VALUE));
      }
    }
    admin.split(TABLE_NAME, Bytes.toBytes(50));

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Wait for split to complete
    // Replaced: TEST_UTIL.getMiniHBaseCluster().getRegions(TABLE_NAME).size() == 2
    // With: admin.getRegions(TABLE_NAME).size() == 2 (client-side API)
    long deadline = System.currentTimeMillis() + 60000;
    while (admin.getRegions(TABLE_NAME).size() != 2) {
      if (System.currentTimeMillis() > deadline) {
        throw new IOException("Split did not complete in time");
      }
      Thread.sleep(100);
    }

    // make sure we can access the split regions
    try (Table table = connection.getTable(TABLE_NAME)) {
      for (int i = 0; i < 100; i++) {
        assertFalse(table.get(new Get(Bytes.toBytes(i))).isEmpty());
      }
    }

    // let's cache the two old locations
    AsyncTableRegionLocator locator = asyncConn.getRegionLocator(TABLE_NAME);
    locator.getRegionLocation(Bytes.toBytes(0)).join();
    locator.getRegionLocation(Bytes.toBytes(99)).join();

    // recreate the table
    admin.disableTable(TABLE_NAME);
    admin.deleteTable(TABLE_NAME);
    admin.createTable(TableDescriptorBuilder.newBuilder(TABLE_NAME)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.newBuilder(FAMILY).build()).build());

    // confirm that we can still get the correct location
    assertFalse(asyncConn.getTable(TABLE_NAME).exists(new Get(Bytes.toBytes(99))).join());
  }

  @Test
  public void test_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupTest("test");
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    try (Table table = connection.getTable(TABLE_NAME)) {
      for (int i = 0; i < 100; i++) {
        table.put(new Put(Bytes.toBytes(i)).addColumn(FAMILY, QUALIFIER, VALUE));
      }
    }
    admin.split(TABLE_NAME, Bytes.toBytes(50));

    // Wait for split to complete
    // Replaced: TEST_UTIL.getMiniHBaseCluster().getRegions(TABLE_NAME).size() == 2
    // With: admin.getRegions(TABLE_NAME).size() == 2 (client-side API)
    long deadline = System.currentTimeMillis() + 60000;
    while (admin.getRegions(TABLE_NAME).size() != 2) {
      if (System.currentTimeMillis() > deadline) {
        throw new IOException("Split did not complete in time");
      }
      Thread.sleep(100);
    }

    // make sure we can access the split regions
    try (Table table = connection.getTable(TABLE_NAME)) {
      for (int i = 0; i < 100; i++) {
        assertFalse(table.get(new Get(Bytes.toBytes(i))).isEmpty());
      }
    }

    // let's cache the two old locations
    AsyncTableRegionLocator locator = asyncConn.getRegionLocator(TABLE_NAME);
    locator.getRegionLocation(Bytes.toBytes(0)).join();
    locator.getRegionLocation(Bytes.toBytes(99)).join();

    // recreate the table
    admin.disableTable(TABLE_NAME);
    admin.deleteTable(TABLE_NAME);
    admin.createTable(TableDescriptorBuilder.newBuilder(TABLE_NAME)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.newBuilder(FAMILY).build()).build());

    // confirm that we can still get the correct location
    assertFalse(asyncConn.getTable(TABLE_NAME).exists(new Get(Bytes.toBytes(99))).join());
  }
}
