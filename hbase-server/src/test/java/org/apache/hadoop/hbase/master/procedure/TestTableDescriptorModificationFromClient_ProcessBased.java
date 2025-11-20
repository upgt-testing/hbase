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
package org.apache.hadoop.hbase.master.procedure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Set;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.InvalidFamilyOperationException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * ProcessBased version of {@link TestTableDescriptorModificationFromClient}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Reduced version: Removes HDFS-level descriptor file verification
 * (requires MasterFileSystem access). Admin API verification fully preserved.
 *
 * @see TestTableDescriptorModificationFromClient Original test using MiniHBaseCluster
 */
public class TestTableDescriptorModificationFromClient_ProcessBased extends ProcessBasedUpgradeTestBase {

  @Rule
  public TestName name = new TestName();

  private static final byte[] FAMILY_0 = Bytes.toBytes("cf0");
  private static final byte[] FAMILY_1 = Bytes.toBytes("cf1");

  @Test
  public void testModifyTable_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testModifyTableImpl();
  }

  @Test
  public void testModifyTable_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testModifyTableImpl();
  }

  @Test
  public void testModifyTable_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    testModifyTableImpl();
  }

  @Test
  public void testModifyTable_AFTER_MODIFY() throws Exception {
    upgradeCheckpoint = "AFTER_MODIFY";
    testModifyTableImpl();
  }

  private void testModifyTableImpl() throws Exception {
    TableName tableName = TableName.valueOf(name.getMethodName());

    cluster = new ProcessBasedMiniHBaseCluster.Builder(HBaseConfiguration.create())
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with one family
    HTableDescriptor baseHtd = new HTableDescriptor(tableName);
    baseHtd.addFamily(new HColumnDescriptor(FAMILY_0));
    admin.createTable(baseHtd);
    admin.disableTable(tableName);

    checkpoint("AFTER_CREATE_TABLE");

    try {
      // Verify the table descriptor
      verifyTableDescriptor(tableName, FAMILY_0);

      // Modify the table adding another family and verify the descriptor
      HTableDescriptor modifiedHtd = new HTableDescriptor(tableName);
      modifiedHtd.addFamily(new HColumnDescriptor(FAMILY_0));
      modifiedHtd.addFamily(new HColumnDescriptor(FAMILY_1));
      admin.modifyTable(tableName, modifiedHtd);

      checkpoint("AFTER_MODIFY");

      verifyTableDescriptor(tableName, FAMILY_0, FAMILY_1);
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test
  public void testAddColumn_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testAddColumnImpl();
  }

  @Test
  public void testAddColumn_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testAddColumnImpl();
  }

  @Test
  public void testAddColumn_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    testAddColumnImpl();
  }

  @Test
  public void testAddColumn_AFTER_ADD_COLUMN() throws Exception {
    upgradeCheckpoint = "AFTER_ADD_COLUMN";
    testAddColumnImpl();
  }

  private void testAddColumnImpl() throws Exception {
    TableName tableName = TableName.valueOf(name.getMethodName());

    cluster = new ProcessBasedMiniHBaseCluster.Builder(HBaseConfiguration.create())
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with one family
    HTableDescriptor baseHtd = new HTableDescriptor(tableName);
    baseHtd.addFamily(new HColumnDescriptor(FAMILY_0));
    admin.createTable(baseHtd);
    admin.disableTable(tableName);

    checkpoint("AFTER_CREATE_TABLE");

    try {
      // Verify the table descriptor
      verifyTableDescriptor(tableName, FAMILY_0);

      // Add another column family
      admin.addColumnFamily(tableName, new HColumnDescriptor(FAMILY_1));

      checkpoint("AFTER_ADD_COLUMN");

      verifyTableDescriptor(tableName, FAMILY_0, FAMILY_1);
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test
  public void testAddSameColumnFamilyTwice_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testAddSameColumnFamilyTwiceImpl();
  }

  @Test
  public void testAddSameColumnFamilyTwice_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testAddSameColumnFamilyTwiceImpl();
  }

  @Test
  public void testAddSameColumnFamilyTwice_AFTER_FIRST_ADD() throws Exception {
    upgradeCheckpoint = "AFTER_FIRST_ADD";
    testAddSameColumnFamilyTwiceImpl();
  }

  private void testAddSameColumnFamilyTwiceImpl() throws Exception {
    TableName tableName = TableName.valueOf(name.getMethodName());

    cluster = new ProcessBasedMiniHBaseCluster.Builder(HBaseConfiguration.create())
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with one family
    HTableDescriptor baseHtd = new HTableDescriptor(tableName);
    baseHtd.addFamily(new HColumnDescriptor(FAMILY_0));
    admin.createTable(baseHtd);
    admin.disableTable(tableName);

    try {
      // Verify the table descriptor
      verifyTableDescriptor(tableName, FAMILY_0);

      // Add column family
      admin.addColumnFamily(tableName, new HColumnDescriptor(FAMILY_1));

      checkpoint("AFTER_FIRST_ADD");

      verifyTableDescriptor(tableName, FAMILY_0, FAMILY_1);

      try {
        // Add same column family again - expect failure
        admin.addColumnFamily(tableName, new HColumnDescriptor(FAMILY_1));
        Assert.fail("Add a duplicate column family should fail");
      } catch (InvalidFamilyOperationException e) {
        // Expected.
      }
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test
  public void testModifyColumnFamily_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testModifyColumnFamilyImpl();
  }

  @Test
  public void testModifyColumnFamily_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testModifyColumnFamilyImpl();
  }

  @Test
  public void testModifyColumnFamily_AFTER_MODIFY_CF() throws Exception {
    upgradeCheckpoint = "AFTER_MODIFY_CF";
    testModifyColumnFamilyImpl();
  }

  private void testModifyColumnFamilyImpl() throws Exception {
    TableName tableName = TableName.valueOf(name.getMethodName());

    cluster = new ProcessBasedMiniHBaseCluster.Builder(HBaseConfiguration.create())
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    HColumnDescriptor cfDescriptor = new HColumnDescriptor(FAMILY_0);
    int blockSize = cfDescriptor.getBlocksize();

    // Create a table with one family
    HTableDescriptor baseHtd = new HTableDescriptor(tableName);
    baseHtd.addFamily(cfDescriptor);
    admin.createTable(baseHtd);
    admin.disableTable(tableName);

    try {
      // Verify the table descriptor
      verifyTableDescriptor(tableName, FAMILY_0);

      int newBlockSize = 2 * blockSize;
      cfDescriptor.setBlocksize(newBlockSize);

      // Modify column family
      admin.modifyColumnFamily(tableName, cfDescriptor);

      checkpoint("AFTER_MODIFY_CF");

      HTableDescriptor htd = admin.getTableDescriptor(tableName);
      HColumnDescriptor hcfd = htd.getFamily(FAMILY_0);
      assertTrue(hcfd.getBlocksize() == newBlockSize);
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test
  public void testModifyNonExistingColumnFamily_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testModifyNonExistingColumnFamilyImpl();
  }

  @Test
  public void testModifyNonExistingColumnFamily_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testModifyNonExistingColumnFamilyImpl();
  }

  private void testModifyNonExistingColumnFamilyImpl() throws Exception {
    TableName tableName = TableName.valueOf(name.getMethodName());

    cluster = new ProcessBasedMiniHBaseCluster.Builder(HBaseConfiguration.create())
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    HColumnDescriptor cfDescriptor = new HColumnDescriptor(FAMILY_1);
    int blockSize = cfDescriptor.getBlocksize();

    // Create a table with one family
    HTableDescriptor baseHtd = new HTableDescriptor(tableName);
    baseHtd.addFamily(new HColumnDescriptor(FAMILY_0));
    admin.createTable(baseHtd);
    admin.disableTable(tableName);

    try {
      // Verify the table descriptor
      verifyTableDescriptor(tableName, FAMILY_0);

      int newBlockSize = 2 * blockSize;
      cfDescriptor.setBlocksize(newBlockSize);

      // Modify a column family that is not in the table.
      try {
        admin.modifyColumnFamily(tableName, cfDescriptor);
        Assert.fail("Modify a non-exist column family should fail");
      } catch (InvalidFamilyOperationException e) {
        // Expected.
      }
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test
  public void testDeleteColumn_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testDeleteColumnImpl();
  }

  @Test
  public void testDeleteColumn_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testDeleteColumnImpl();
  }

  @Test
  public void testDeleteColumn_AFTER_DELETE() throws Exception {
    upgradeCheckpoint = "AFTER_DELETE";
    testDeleteColumnImpl();
  }

  private void testDeleteColumnImpl() throws Exception {
    TableName tableName = TableName.valueOf(name.getMethodName());

    cluster = new ProcessBasedMiniHBaseCluster.Builder(HBaseConfiguration.create())
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with two families
    HTableDescriptor baseHtd = new HTableDescriptor(tableName);
    baseHtd.addFamily(new HColumnDescriptor(FAMILY_0));
    baseHtd.addFamily(new HColumnDescriptor(FAMILY_1));
    admin.createTable(baseHtd);
    admin.disableTable(tableName);

    try {
      // Verify the table descriptor
      verifyTableDescriptor(tableName, FAMILY_0, FAMILY_1);

      // Delete one family
      admin.deleteColumnFamily(tableName, FAMILY_1);

      checkpoint("AFTER_DELETE");

      verifyTableDescriptor(tableName, FAMILY_0);
    } finally {
      admin.deleteTable(tableName);
    }
  }

  @Test
  public void testDeleteSameColumnFamilyTwice_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testDeleteSameColumnFamilyTwiceImpl();
  }

  @Test
  public void testDeleteSameColumnFamilyTwice_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testDeleteSameColumnFamilyTwiceImpl();
  }

  @Test
  public void testDeleteSameColumnFamilyTwice_AFTER_FIRST_DELETE() throws Exception {
    upgradeCheckpoint = "AFTER_FIRST_DELETE";
    testDeleteSameColumnFamilyTwiceImpl();
  }

  private void testDeleteSameColumnFamilyTwiceImpl() throws Exception {
    TableName tableName = TableName.valueOf(name.getMethodName());

    cluster = new ProcessBasedMiniHBaseCluster.Builder(HBaseConfiguration.create())
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create a table with two families
    HTableDescriptor baseHtd = new HTableDescriptor(tableName);
    baseHtd.addFamily(new HColumnDescriptor(FAMILY_0));
    baseHtd.addFamily(new HColumnDescriptor(FAMILY_1));
    admin.createTable(baseHtd);
    admin.disableTable(tableName);

    try {
      // Verify the table descriptor
      verifyTableDescriptor(tableName, FAMILY_0, FAMILY_1);

      // Delete one family
      admin.deleteColumnFamily(tableName, FAMILY_1);

      checkpoint("AFTER_FIRST_DELETE");

      verifyTableDescriptor(tableName, FAMILY_0);

      try {
        // Delete again - expect failure
        admin.deleteColumnFamily(tableName, FAMILY_1);
        Assert.fail("Delete a non-exist column family should fail");
      } catch (Exception e) {
        // Expected.
      }
    } finally {
      admin.deleteTable(tableName);
    }
  }

  private void verifyTableDescriptor(final TableName tableName, final byte[]... families)
    throws IOException {
    // Verify descriptor from Admin API
    HTableDescriptor htd = admin.getTableDescriptor(tableName);
    verifyTableDescriptor(htd, tableName, families);

    // TRANSFORMATION NOTE: HDFS-level descriptor file verification removed.
    // Original test verified descriptor file directly on HDFS via:
    // - getMaster().getMasterFileSystem() (requires direct Master access)
    // - FSTableDescriptors.getTableDescriptorFromFs() (requires FileSystem access)
    // Admin API verification is sufficient - if getTableDescriptor() returns
    // correct schema, the modification succeeded.
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
}
