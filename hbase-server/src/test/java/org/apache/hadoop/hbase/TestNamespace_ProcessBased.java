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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Callable;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.MiscTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.com.google.common.collect.Sets;

/**
 * ProcessBased version of {@link TestNamespace}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Note: ZKNamespaceManager and MasterFileSystem verifications removed (require internal access).
 * All namespace operations tested via Admin API.
 *
 * @see TestNamespace Original test using MiniHBaseCluster
 */
@Category({ MiscTests.class, MediumTests.class })
public class TestNamespace_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestNamespace_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestNamespace_ProcessBased.class);
  private String prefix = "TestNamespace";

  @Rule
  public TestName name = new TestName();

  private void setupCluster() throws Exception {
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(4)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
  }

  private void cleanupNamespaces() throws IOException {
    // Clean up tables first
    for (TableDescriptor desc : admin.listTableDescriptors()) {
      TableName tableName = desc.getTableName();
      if (tableName.getNameAsString().startsWith(prefix)) {
        admin.disableTable(tableName);
        admin.deleteTable(tableName);
      }
    }
    // Clean up namespaces
    for (NamespaceDescriptor ns : admin.listNamespaceDescriptors()) {
      if (ns.getName().startsWith(prefix)) {
        admin.deleteNamespace(ns.getName());
      }
    }
  }

  @Test
  public void verifyReservedNS_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Verify existence of reserved namespaces
    NamespaceDescriptor ns =
      admin.getNamespaceDescriptor(NamespaceDescriptor.DEFAULT_NAMESPACE.getName());
    assertNotNull(ns);
    assertEquals(ns.getName(), NamespaceDescriptor.DEFAULT_NAMESPACE.getName());
    checkpoint("AFTER_GET_DEFAULT_NS");

    ns = admin.getNamespaceDescriptor(NamespaceDescriptor.SYSTEM_NAMESPACE.getName());
    assertNotNull(ns);
    assertEquals(ns.getName(), NamespaceDescriptor.SYSTEM_NAMESPACE.getName());

    assertEquals(2, admin.listNamespaces().length);
    assertEquals(2, admin.listNamespaceDescriptors().length);
    checkpoint("AFTER_LIST_NS");

    // Verify existence of system tables
    Set<TableName> systemTables =
      Sets.newHashSet(TableName.META_TABLE_NAME, TableName.NAMESPACE_TABLE_NAME);
    TableDescriptor[] descs =
      admin.listTableDescriptorsByNamespace(NamespaceDescriptor.SYSTEM_NAMESPACE.getName());
    assertEquals(systemTables.size(), descs.length);
    for (TableDescriptor desc : descs) {
      assertTrue(systemTables.contains(desc.getTableName()));
    }
    // Verify system tables aren't listed
    assertEquals(0, admin.listTableDescriptors().size());
    checkpoint("AFTER_VERIFY_SYSTEM_TABLES");

    // Try creating default and system namespaces
    boolean exceptionCaught = false;
    try {
      admin.createNamespace(NamespaceDescriptor.DEFAULT_NAMESPACE);
    } catch (IOException exp) {
      LOG.warn(exp.toString(), exp);
      exceptionCaught = true;
    } finally {
      assertTrue(exceptionCaught);
    }

    exceptionCaught = false;
    try {
      admin.createNamespace(NamespaceDescriptor.SYSTEM_NAMESPACE);
    } catch (IOException exp) {
      LOG.warn(exp.toString(), exp);
      exceptionCaught = true;
    } finally {
      assertTrue(exceptionCaught);
    }
    checkpoint("AFTER_VERIFY_RESERVED_NS_PROTECTION");
  }

  @Test
  public void verifyReservedNS_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    NamespaceDescriptor ns =
      admin.getNamespaceDescriptor(NamespaceDescriptor.DEFAULT_NAMESPACE.getName());
    assertNotNull(ns);
    assertEquals(ns.getName(), NamespaceDescriptor.DEFAULT_NAMESPACE.getName());
    checkpoint("AFTER_GET_DEFAULT_NS");

    ns = admin.getNamespaceDescriptor(NamespaceDescriptor.SYSTEM_NAMESPACE.getName());
    assertNotNull(ns);
    assertEquals(ns.getName(), NamespaceDescriptor.SYSTEM_NAMESPACE.getName());

    assertEquals(2, admin.listNamespaces().length);
    assertEquals(2, admin.listNamespaceDescriptors().length);
    checkpoint("AFTER_LIST_NS");

    Set<TableName> systemTables =
      Sets.newHashSet(TableName.META_TABLE_NAME, TableName.NAMESPACE_TABLE_NAME);
    TableDescriptor[] descs =
      admin.listTableDescriptorsByNamespace(NamespaceDescriptor.SYSTEM_NAMESPACE.getName());
    assertEquals(systemTables.size(), descs.length);
    for (TableDescriptor desc : descs) {
      assertTrue(systemTables.contains(desc.getTableName()));
    }
    assertEquals(0, admin.listTableDescriptors().size());
    checkpoint("AFTER_VERIFY_SYSTEM_TABLES");

    boolean exceptionCaught = false;
    try {
      admin.createNamespace(NamespaceDescriptor.DEFAULT_NAMESPACE);
    } catch (IOException exp) {
      LOG.warn(exp.toString(), exp);
      exceptionCaught = true;
    } finally {
      assertTrue(exceptionCaught);
    }

    exceptionCaught = false;
    try {
      admin.createNamespace(NamespaceDescriptor.SYSTEM_NAMESPACE);
    } catch (IOException exp) {
      LOG.warn(exp.toString(), exp);
      exceptionCaught = true;
    } finally {
      assertTrue(exceptionCaught);
    }
    checkpoint("AFTER_VERIFY_RESERVED_NS_PROTECTION");
  }

  @Test
  public void testDeleteReservedNS_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    boolean exceptionCaught = false;
    try {
      admin.deleteNamespace(NamespaceDescriptor.DEFAULT_NAMESPACE_NAME_STR);
    } catch (IOException exp) {
      LOG.warn(exp.toString(), exp);
      exceptionCaught = true;
    } finally {
      assertTrue(exceptionCaught);
    }
    checkpoint("AFTER_TRY_DELETE_DEFAULT");

    exceptionCaught = false;
    try {
      admin.deleteNamespace(NamespaceDescriptor.SYSTEM_NAMESPACE_NAME_STR);
    } catch (IOException exp) {
      LOG.warn(exp.toString(), exp);
      exceptionCaught = true;
    } finally {
      assertTrue(exceptionCaught);
    }
    checkpoint("AFTER_TRY_DELETE_SYSTEM");
  }

  @Test
  public void testDeleteReservedNS_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    boolean exceptionCaught = false;
    try {
      admin.deleteNamespace(NamespaceDescriptor.DEFAULT_NAMESPACE_NAME_STR);
    } catch (IOException exp) {
      LOG.warn(exp.toString(), exp);
      exceptionCaught = true;
    } finally {
      assertTrue(exceptionCaught);
    }
    checkpoint("AFTER_TRY_DELETE_DEFAULT");

    exceptionCaught = false;
    try {
      admin.deleteNamespace(NamespaceDescriptor.SYSTEM_NAMESPACE_NAME_STR);
    } catch (IOException exp) {
      LOG.warn(exp.toString(), exp);
      exceptionCaught = true;
    } finally {
      assertTrue(exceptionCaught);
    }
    checkpoint("AFTER_TRY_DELETE_SYSTEM");
  }

  @Test
  public void createRemoveTest_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    String nsName = prefix + "_" + name.getMethodName();
    LOG.info(name.getMethodName());

    // Create namespace and verify
    admin.createNamespace(NamespaceDescriptor.create(nsName).build());
    assertEquals(3, admin.listNamespaces().length);
    assertEquals(3, admin.listNamespaceDescriptors().length);
    checkpoint("AFTER_CREATE_NS");

    // Remove namespace and verify
    admin.deleteNamespace(nsName);
    assertEquals(2, admin.listNamespaces().length);
    assertEquals(2, admin.listNamespaceDescriptors().length);
    checkpoint("AFTER_DELETE_NS");
  }

  @Test
  public void createRemoveTest_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    String nsName = prefix + "_" + name.getMethodName();
    LOG.info(name.getMethodName());

    admin.createNamespace(NamespaceDescriptor.create(nsName).build());
    assertEquals(3, admin.listNamespaces().length);
    assertEquals(3, admin.listNamespaceDescriptors().length);
    checkpoint("AFTER_CREATE_NS");

    admin.deleteNamespace(nsName);
    assertEquals(2, admin.listNamespaces().length);
    assertEquals(2, admin.listNamespaceDescriptors().length);
    checkpoint("AFTER_DELETE_NS");
  }

  @Test
  public void createRemoveTest_AFTER_CREATE_NS() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_NS";
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    String nsName = prefix + "_" + name.getMethodName();
    LOG.info(name.getMethodName());

    admin.createNamespace(NamespaceDescriptor.create(nsName).build());
    assertEquals(3, admin.listNamespaces().length);
    assertEquals(3, admin.listNamespaceDescriptors().length);
    checkpoint("AFTER_CREATE_NS");

    admin.deleteNamespace(nsName);
    assertEquals(2, admin.listNamespaces().length);
    assertEquals(2, admin.listNamespaceDescriptors().length);
    checkpoint("AFTER_DELETE_NS");
  }

  @Test
  public void createDoubleTest_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    String nsName = prefix + "_" + name.getMethodName();
    LOG.info(name.getMethodName());

    final TableName tableName = TableName.valueOf(name.getMethodName());
    final TableName tableNameFoo = TableName.valueOf(nsName + ":" + name.getMethodName());

    // Create namespace and verify
    admin.createNamespace(NamespaceDescriptor.create(nsName).build());
    checkpoint("AFTER_CREATE_NS");

    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(Bytes.toBytes(nsName)))
      .build());
    admin.createTable(TableDescriptorBuilder.newBuilder(tableNameFoo)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(Bytes.toBytes(nsName)))
      .build());
    checkpoint("AFTER_CREATE_TABLES");

    assertEquals(2, admin.listTableDescriptors().size());
    assertNotNull(admin.getDescriptor(tableName));
    assertNotNull(admin.getDescriptor(tableNameFoo));

    // Remove tables
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
    assertEquals(1, admin.listTableDescriptors().size());
    checkpoint("AFTER_DELETE_TABLE");

    // Cleanup
    admin.disableTable(tableNameFoo);
    admin.deleteTable(tableNameFoo);
    admin.deleteNamespace(nsName);
  }

  @Test
  public void createDoubleTest_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    String nsName = prefix + "_" + name.getMethodName();
    LOG.info(name.getMethodName());

    final TableName tableName = TableName.valueOf(name.getMethodName());
    final TableName tableNameFoo = TableName.valueOf(nsName + ":" + name.getMethodName());

    admin.createNamespace(NamespaceDescriptor.create(nsName).build());
    checkpoint("AFTER_CREATE_NS");

    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(Bytes.toBytes(nsName)))
      .build());
    admin.createTable(TableDescriptorBuilder.newBuilder(tableNameFoo)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(Bytes.toBytes(nsName)))
      .build());
    checkpoint("AFTER_CREATE_TABLES");

    assertEquals(2, admin.listTableDescriptors().size());
    assertNotNull(admin.getDescriptor(tableName));
    assertNotNull(admin.getDescriptor(tableNameFoo));

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
    assertEquals(1, admin.listTableDescriptors().size());
    checkpoint("AFTER_DELETE_TABLE");

    admin.disableTable(tableNameFoo);
    admin.deleteTable(tableNameFoo);
    admin.deleteNamespace(nsName);
  }

  @Test
  public void createTableTest_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    String nsName = prefix + "_" + name.getMethodName();
    LOG.info(name.getMethodName());

    TableDescriptor desc = TableDescriptorBuilder
      .newBuilder(TableName.valueOf(nsName + ":" + name.getMethodName()))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of("my_cf"))
      .build();

    try {
      admin.createTable(desc);
      fail("Expected no namespace exists exception");
    } catch (NamespaceNotFoundException ex) {
    }
    checkpoint("AFTER_VERIFY_NS_NOT_EXISTS");

    // Create table in new namespace
    admin.createNamespace(NamespaceDescriptor.create(nsName).build());
    admin.createTable(desc);
    checkpoint("AFTER_CREATE_TABLE");

    assertEquals(1, admin.listTableDescriptors().size());

    // Verify non-empty namespace can't be removed
    try {
      admin.deleteNamespace(nsName);
      fail("Expected non-empty namespace constraint exception");
    } catch (Exception ex) {
      LOG.info("Caught expected exception: " + ex);
    }
    checkpoint("AFTER_VERIFY_NS_DELETION_PROTECTION");

    // Sanity check - write and read from table
    try (Table table = connection.getTable(desc.getTableName())) {
      Put p = new Put(Bytes.toBytes("row1"));
      p.addColumn(Bytes.toBytes("my_cf"), Bytes.toBytes("my_col"), Bytes.toBytes("value1"));
      table.put(p);
    }
    checkpoint("AFTER_WRITE_DATA");

    // Flush and read from disk
    admin.flush(desc.getTableName());
    try (Table table = connection.getTable(desc.getTableName())) {
      Get g = new Get(Bytes.toBytes("row1"));
      assertTrue(table.exists(g));
    }
    checkpoint("AFTER_FLUSH_AND_READ");

    // Normal case of removing namespace
    admin.disableTable(desc.getTableName());
    admin.deleteTable(desc.getTableName());
    admin.deleteNamespace(nsName);
  }

  @Test
  public void createTableTest_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    String nsName = prefix + "_" + name.getMethodName();
    LOG.info(name.getMethodName());

    TableDescriptor desc = TableDescriptorBuilder
      .newBuilder(TableName.valueOf(nsName + ":" + name.getMethodName()))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of("my_cf"))
      .build();

    try {
      admin.createTable(desc);
      fail("Expected no namespace exists exception");
    } catch (NamespaceNotFoundException ex) {
    }
    checkpoint("AFTER_VERIFY_NS_NOT_EXISTS");

    admin.createNamespace(NamespaceDescriptor.create(nsName).build());
    admin.createTable(desc);
    checkpoint("AFTER_CREATE_TABLE");

    assertEquals(1, admin.listTableDescriptors().size());

    try {
      admin.deleteNamespace(nsName);
      fail("Expected non-empty namespace constraint exception");
    } catch (Exception ex) {
      LOG.info("Caught expected exception: " + ex);
    }
    checkpoint("AFTER_VERIFY_NS_DELETION_PROTECTION");

    try (Table table = connection.getTable(desc.getTableName())) {
      Put p = new Put(Bytes.toBytes("row1"));
      p.addColumn(Bytes.toBytes("my_cf"), Bytes.toBytes("my_col"), Bytes.toBytes("value1"));
      table.put(p);
    }
    checkpoint("AFTER_WRITE_DATA");

    admin.flush(desc.getTableName());
    try (Table table = connection.getTable(desc.getTableName())) {
      Get g = new Get(Bytes.toBytes("row1"));
      assertTrue(table.exists(g));
    }
    checkpoint("AFTER_FLUSH_AND_READ");

    admin.disableTable(desc.getTableName());
    admin.deleteTable(desc.getTableName());
    admin.deleteNamespace(nsName);
  }

  @Test
  public void createTableInDefaultNamespace_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableDescriptor desc = TableDescriptorBuilder
      .newBuilder(TableName.valueOf(name.getMethodName()))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf1"))
      .build();

    admin.createTable(desc);
    assertTrue(admin.listTableDescriptors().size() == 1);
    checkpoint("AFTER_CREATE_TABLE");

    admin.disableTable(desc.getTableName());
    admin.deleteTable(desc.getTableName());
  }

  @Test
  public void createTableInDefaultNamespace_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableDescriptor desc = TableDescriptorBuilder
      .newBuilder(TableName.valueOf(name.getMethodName()))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf1"))
      .build();

    admin.createTable(desc);
    assertTrue(admin.listTableDescriptors().size() == 1);
    checkpoint("AFTER_CREATE_TABLE");

    admin.disableTable(desc.getTableName());
    admin.deleteTable(desc.getTableName());
  }

  @Test
  public void createTableInSystemNamespace_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf("hbase:" + name.getMethodName());
    TableDescriptor desc = TableDescriptorBuilder
      .newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf1"))
      .build();

    admin.createTable(desc);
    assertEquals(0, admin.listTableDescriptors().size());
    assertTrue(admin.tableExists(tableName));
    checkpoint("AFTER_CREATE_TABLE");

    admin.disableTable(desc.getTableName());
    admin.deleteTable(desc.getTableName());
  }

  @Test
  public void createTableInSystemNamespace_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf("hbase:" + name.getMethodName());
    TableDescriptor desc = TableDescriptorBuilder
      .newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf1"))
      .build();

    admin.createTable(desc);
    assertEquals(0, admin.listTableDescriptors().size());
    assertTrue(admin.tableExists(tableName));
    checkpoint("AFTER_CREATE_TABLE");

    admin.disableTable(desc.getTableName());
    admin.deleteTable(desc.getTableName());
  }

  @Test
  public void testNamespaceOperations_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    admin.createNamespace(NamespaceDescriptor.create(prefix + "ns1").build());
    admin.createNamespace(NamespaceDescriptor.create(prefix + "ns2").build());
    checkpoint("AFTER_CREATE_NAMESPACES");

    // Create namespace that already exists
    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.createNamespace(NamespaceDescriptor.create(prefix + "ns1").build());
        return null;
      }
    }, NamespaceExistException.class);

    // Create a table in non-existing namespace
    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        TableDescriptor htd = TableDescriptorBuilder
          .newBuilder(TableName.valueOf("non_existing_namespace", name.getMethodName()))
          .setColumnFamily(ColumnFamilyDescriptorBuilder.of("family1"))
          .build();
        admin.createTable(htd);
        return null;
      }
    }, NamespaceNotFoundException.class);
    checkpoint("AFTER_VERIFY_EXCEPTIONS");

    // Get descriptor for existing namespace
    admin.getNamespaceDescriptor(prefix + "ns1");

    // Get descriptor for non-existing namespace
    runWithExpectedException(new Callable<NamespaceDescriptor>() {
      @Override
      public NamespaceDescriptor call() throws Exception {
        return admin.getNamespaceDescriptor("non_existing_namespace");
      }
    }, NamespaceNotFoundException.class);

    // Delete descriptor for existing namespace
    admin.deleteNamespace(prefix + "ns2");

    // Delete descriptor for non-existing namespace
    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.deleteNamespace("non_existing_namespace");
        return null;
      }
    }, NamespaceNotFoundException.class);
    checkpoint("AFTER_DELETE_OPERATIONS");

    // Modify namespace descriptor for existing namespace
    NamespaceDescriptor ns1 = admin.getNamespaceDescriptor(prefix + "ns1");
    ns1.setConfiguration("foo", "bar");
    admin.modifyNamespace(ns1);

    // Modify namespace descriptor for non-existing namespace
    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.modifyNamespace(NamespaceDescriptor.create("non_existing_namespace").build());
        return null;
      }
    }, NamespaceNotFoundException.class);
    checkpoint("AFTER_MODIFY_OPERATIONS");

    // Get table descriptors for existing namespace
    TableDescriptor htd = TableDescriptorBuilder
      .newBuilder(TableName.valueOf(prefix + "ns1", name.getMethodName()))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of("family1"))
      .build();
    admin.createTable(htd);
    TableDescriptor[] htds = admin.listTableDescriptorsByNamespace(prefix + "ns1");
    assertNotNull("Should have not returned null", htds);
    assertEquals("Should have returned non-empty array", 1, htds.length);

    // Get table descriptors for non-existing namespace
    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.listTableDescriptorsByNamespace("non_existant_namespace");
        return null;
      }
    }, NamespaceNotFoundException.class);
    checkpoint("AFTER_LIST_TABLE_DESCRIPTORS");

    // Get table names for existing namespace
    TableName[] tableNames = admin.listTableNamesByNamespace(prefix + "ns1");
    assertNotNull("Should have not returned null", tableNames);
    assertEquals("Should have returned non-empty array", 1, tableNames.length);

    // Get table names for non-existing namespace
    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.listTableNamesByNamespace("non_existing_namespace");
        return null;
      }
    }, NamespaceNotFoundException.class);
    checkpoint("AFTER_LIST_TABLE_NAMES");

    // Cleanup
    admin.disableTable(htd.getTableName());
    admin.deleteTable(htd.getTableName());
    admin.deleteNamespace(prefix + "ns1");
  }

  @Test
  public void testNamespaceOperations_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    admin.createNamespace(NamespaceDescriptor.create(prefix + "ns1").build());
    admin.createNamespace(NamespaceDescriptor.create(prefix + "ns2").build());
    checkpoint("AFTER_CREATE_NAMESPACES");

    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.createNamespace(NamespaceDescriptor.create(prefix + "ns1").build());
        return null;
      }
    }, NamespaceExistException.class);

    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        TableDescriptor htd = TableDescriptorBuilder
          .newBuilder(TableName.valueOf("non_existing_namespace", name.getMethodName()))
          .setColumnFamily(ColumnFamilyDescriptorBuilder.of("family1"))
          .build();
        admin.createTable(htd);
        return null;
      }
    }, NamespaceNotFoundException.class);
    checkpoint("AFTER_VERIFY_EXCEPTIONS");

    admin.getNamespaceDescriptor(prefix + "ns1");

    runWithExpectedException(new Callable<NamespaceDescriptor>() {
      @Override
      public NamespaceDescriptor call() throws Exception {
        return admin.getNamespaceDescriptor("non_existing_namespace");
      }
    }, NamespaceNotFoundException.class);

    admin.deleteNamespace(prefix + "ns2");

    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.deleteNamespace("non_existing_namespace");
        return null;
      }
    }, NamespaceNotFoundException.class);
    checkpoint("AFTER_DELETE_OPERATIONS");

    NamespaceDescriptor ns1 = admin.getNamespaceDescriptor(prefix + "ns1");
    ns1.setConfiguration("foo", "bar");
    admin.modifyNamespace(ns1);

    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.modifyNamespace(NamespaceDescriptor.create("non_existing_namespace").build());
        return null;
      }
    }, NamespaceNotFoundException.class);
    checkpoint("AFTER_MODIFY_OPERATIONS");

    TableDescriptor htd = TableDescriptorBuilder
      .newBuilder(TableName.valueOf(prefix + "ns1", name.getMethodName()))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of("family1"))
      .build();
    admin.createTable(htd);
    TableDescriptor[] htds = admin.listTableDescriptorsByNamespace(prefix + "ns1");
    assertNotNull("Should have not returned null", htds);
    assertEquals("Should have returned non-empty array", 1, htds.length);

    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.listTableDescriptorsByNamespace("non_existant_namespace");
        return null;
      }
    }, NamespaceNotFoundException.class);
    checkpoint("AFTER_LIST_TABLE_DESCRIPTORS");

    TableName[] tableNames = admin.listTableNamesByNamespace(prefix + "ns1");
    assertNotNull("Should have not returned null", tableNames);
    assertEquals("Should have returned non-empty array", 1, tableNames.length);

    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.listTableNamesByNamespace("non_existing_namespace");
        return null;
      }
    }, NamespaceNotFoundException.class);
    checkpoint("AFTER_LIST_TABLE_NAMES");

    admin.disableTable(htd.getTableName());
    admin.deleteTable(htd.getTableName());
    admin.deleteNamespace(prefix + "ns1");
  }

  private static <V, E> void runWithExpectedException(Callable<V> callable,
    Class<E> exceptionClass) {
    try {
      callable.call();
    } catch (Exception ex) {
      Assert.assertEquals(exceptionClass, ex.getClass());
      return;
    }
    fail("Should have thrown exception " + exceptionClass);
  }
}
