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

import java.io.IOException;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.MiscTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * ProcessBased version of {@link TestHColumnDescriptorDefaultVersions}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Verifies that HColumnDescriptor version settings are correctly persisted
 * and retrievable via Admin API.
 *
 * Note: The original test also verifies the descriptor from HDFS directly using
 * MasterFileSystem and FSTableDescriptors. In ProcessBasedMiniHBaseCluster, we
 * cannot access the master's filesystem directly. Instead, we verify via Admin API only.
 *
 * @see TestHColumnDescriptorDefaultVersions Original test using MiniHBaseCluster
 */
@Category({ MiscTests.class, MediumTests.class })
public class TestHColumnDescriptorDefaultVersions_ProcessBased
    extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestHColumnDescriptorDefaultVersions_ProcessBased.class);

  private static final byte[] FAMILY = Bytes.toBytes("cf0");

  @Test(timeout = 300000)
  public void testCreateTableWithDefault_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testCreateTableWithDefault");
    // Create a table with one family using default versions
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    ColumnFamilyDescriptor hcd = ColumnFamilyDescriptorBuilder.of(FAMILY);
    tableBuilder.setColumnFamily(hcd);
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    admin.disableTable(tableName);
    checkpoint("AFTER_DISABLE_TABLE");

    try {
      // Verify the column descriptor via Admin API
      verifyHColumnDescriptor(1, tableName, FAMILY);
      checkpoint("AFTER_VERIFY_DESCRIPTOR");
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test(timeout = 300000)
  public void testCreateTableWithDefault_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testCreateTableWithDefault");
    // Create a table with one family using default versions
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    ColumnFamilyDescriptor hcd = ColumnFamilyDescriptorBuilder.of(FAMILY);
    tableBuilder.setColumnFamily(hcd);
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    admin.disableTable(tableName);
    checkpoint("AFTER_DISABLE_TABLE");

    try {
      // Verify the column descriptor via Admin API
      verifyHColumnDescriptor(1, tableName, FAMILY);
      checkpoint("AFTER_VERIFY_DESCRIPTOR");
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test(timeout = 300000)
  public void testCreateTableWithDefault_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testCreateTableWithDefault");
    // Create a table with one family using default versions
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    ColumnFamilyDescriptor hcd = ColumnFamilyDescriptorBuilder.of(FAMILY);
    tableBuilder.setColumnFamily(hcd);
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    admin.disableTable(tableName);
    checkpoint("AFTER_DISABLE_TABLE");

    try {
      // Verify the column descriptor via Admin API
      verifyHColumnDescriptor(1, tableName, FAMILY);
      checkpoint("AFTER_VERIFY_DESCRIPTOR");
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test(timeout = 300000)
  public void testCreateTableWithDefault_AFTER_DISABLE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_DISABLE_TABLE";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testCreateTableWithDefault");
    // Create a table with one family using default versions
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    ColumnFamilyDescriptor hcd = ColumnFamilyDescriptorBuilder.of(FAMILY);
    tableBuilder.setColumnFamily(hcd);
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    admin.disableTable(tableName);
    checkpoint("AFTER_DISABLE_TABLE");

    try {
      // Verify the column descriptor via Admin API
      verifyHColumnDescriptor(1, tableName, FAMILY);
      checkpoint("AFTER_VERIFY_DESCRIPTOR");
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test(timeout = 300000)
  public void testCreateTableWithDefault_AFTER_VERIFY_DESCRIPTOR() throws Exception {
    upgradeCheckpoint = "AFTER_VERIFY_DESCRIPTOR";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testCreateTableWithDefault");
    // Create a table with one family using default versions
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    ColumnFamilyDescriptor hcd = ColumnFamilyDescriptorBuilder.of(FAMILY);
    tableBuilder.setColumnFamily(hcd);
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    admin.disableTable(tableName);
    checkpoint("AFTER_DISABLE_TABLE");

    try {
      // Verify the column descriptor via Admin API
      verifyHColumnDescriptor(1, tableName, FAMILY);
      checkpoint("AFTER_VERIFY_DESCRIPTOR");
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test(timeout = 300000)
  public void testCreateTableWithSetVersion_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testCreateTableWithSetVersion");
    // Create a table with one family with explicitly set version
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    ColumnFamilyDescriptorBuilder cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(FAMILY);
    cfBuilder.setMaxVersions(5);
    tableBuilder.setColumnFamily(cfBuilder.build());
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    admin.disableTable(tableName);
    checkpoint("AFTER_DISABLE_TABLE");

    try {
      // Verify the column descriptor
      verifyHColumnDescriptor(5, tableName, FAMILY);
      checkpoint("AFTER_VERIFY_DESCRIPTOR");
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test(timeout = 300000)
  public void testCreateTableWithSetVersion_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testCreateTableWithSetVersion");
    // Create a table with one family with explicitly set version
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    ColumnFamilyDescriptorBuilder cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(FAMILY);
    cfBuilder.setMaxVersions(5);
    tableBuilder.setColumnFamily(cfBuilder.build());
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    admin.disableTable(tableName);
    checkpoint("AFTER_DISABLE_TABLE");

    try {
      // Verify the column descriptor
      verifyHColumnDescriptor(5, tableName, FAMILY);
      checkpoint("AFTER_VERIFY_DESCRIPTOR");
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test(timeout = 300000)
  public void testCreateTableWithSetVersion_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testCreateTableWithSetVersion");
    // Create a table with one family with explicitly set version
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    ColumnFamilyDescriptorBuilder cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(FAMILY);
    cfBuilder.setMaxVersions(5);
    tableBuilder.setColumnFamily(cfBuilder.build());
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    admin.disableTable(tableName);
    checkpoint("AFTER_DISABLE_TABLE");

    try {
      // Verify the column descriptor
      verifyHColumnDescriptor(5, tableName, FAMILY);
      checkpoint("AFTER_VERIFY_DESCRIPTOR");
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test(timeout = 300000)
  public void testCreateTableWithSetVersion_AFTER_DISABLE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_DISABLE_TABLE";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testCreateTableWithSetVersion");
    // Create a table with one family with explicitly set version
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    ColumnFamilyDescriptorBuilder cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(FAMILY);
    cfBuilder.setMaxVersions(5);
    tableBuilder.setColumnFamily(cfBuilder.build());
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    admin.disableTable(tableName);
    checkpoint("AFTER_DISABLE_TABLE");

    try {
      // Verify the column descriptor
      verifyHColumnDescriptor(5, tableName, FAMILY);
      checkpoint("AFTER_VERIFY_DESCRIPTOR");
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test(timeout = 300000)
  public void testCreateTableWithSetVersion_AFTER_VERIFY_DESCRIPTOR() throws Exception {
    upgradeCheckpoint = "AFTER_VERIFY_DESCRIPTOR";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testCreateTableWithSetVersion");
    // Create a table with one family with explicitly set version
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    ColumnFamilyDescriptorBuilder cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(FAMILY);
    cfBuilder.setMaxVersions(5);
    tableBuilder.setColumnFamily(cfBuilder.build());
    admin.createTable(tableBuilder.build());
    checkpoint("AFTER_CREATE_TABLE");

    admin.disableTable(tableName);
    checkpoint("AFTER_DISABLE_TABLE");

    try {
      // Verify the column descriptor
      verifyHColumnDescriptor(5, tableName, FAMILY);
      checkpoint("AFTER_VERIFY_DESCRIPTOR");
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test(timeout = 300000)
  public void testHColumnDescriptorCachedMaxVersions_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // This test doesn't require cluster - it's testing ColumnFamilyDescriptor behavior
    ColumnFamilyDescriptorBuilder cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(FAMILY);
    cfBuilder.setMaxVersions(5);
    ColumnFamilyDescriptor hcd = cfBuilder.build();
    // Verify the max version
    assertEquals(5, hcd.getMaxVersions());
    checkpoint("AFTER_VERIFY_DESCRIPTOR");
  }

  @Test(timeout = 300000)
  public void testHColumnDescriptorCachedMaxVersions_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // This test doesn't require cluster - it's testing ColumnFamilyDescriptor behavior
    ColumnFamilyDescriptorBuilder cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(FAMILY);
    cfBuilder.setMaxVersions(5);
    ColumnFamilyDescriptor hcd = cfBuilder.build();
    // Verify the max version
    assertEquals(5, hcd.getMaxVersions());
    checkpoint("AFTER_VERIFY_DESCRIPTOR");
  }

  @Test(timeout = 300000)
  public void testHColumnDescriptorCachedMaxVersions_AFTER_VERIFY_DESCRIPTOR() throws Exception {
    upgradeCheckpoint = "AFTER_VERIFY_DESCRIPTOR";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // This test doesn't require cluster - it's testing ColumnFamilyDescriptor behavior
    ColumnFamilyDescriptorBuilder cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(FAMILY);
    cfBuilder.setMaxVersions(5);
    ColumnFamilyDescriptor hcd = cfBuilder.build();
    // Verify the max version
    assertEquals(5, hcd.getMaxVersions());
    checkpoint("AFTER_VERIFY_DESCRIPTOR");
  }

  /**
   * Verify HColumnDescriptor via Admin API.
   *
   * TRANSFORMATION NOTE: The original test also verified the descriptor from HDFS directly
   * using MasterFileSystem and FSTableDescriptors.getTableDescriptorFromFs().
   * In ProcessBasedMiniHBaseCluster, we cannot access the master's filesystem directly.
   * This reduced version verifies via Admin API only, which is still meaningful for
   * testing that column family settings are correctly persisted and retrievable via
   * client API during upgrades.
   */
  private void verifyHColumnDescriptor(int expected, final TableName tableName,
    final byte[]... families) throws IOException {
    // Verify descriptor from master via Admin API
    TableDescriptor htd = admin.getDescriptor(tableName);
    ColumnFamilyDescriptor[] hcds = htd.getColumnFamilies();
    for (ColumnFamilyDescriptor hcd : hcds) {
      assertEquals("Column family max versions should match", expected, hcd.getMaxVersions());
    }
  }
}
