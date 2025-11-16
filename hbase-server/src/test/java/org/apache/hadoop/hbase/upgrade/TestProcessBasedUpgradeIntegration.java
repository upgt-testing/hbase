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
package org.apache.hadoop.hbase.upgrade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for ProcessBasedUpgradeTestBase that actually launch a
 * ProcessBasedMiniHBaseCluster and test the full upgrade workflow.
 *
 * <p>These tests require HBase distributions to be available at:
 * /Users/allenwang/xlab/hbase-test-distributions/
 *
 * <p>Tests include:
 * <ul>
 *   <li>Cluster lifecycle (start, stop)</li>
 *   <li>Table creation and data operations</li>
 *   <li>Checkpoint-based upgrades</li>
 *   <li>Data integrity verification after upgrades</li>
 * </ul>
 */
@Category(LargeTests.class)
@RunWith(Parameterized.class)
public class TestProcessBasedUpgradeIntegration extends ProcessBasedUpgradeTestBase {
  private static final Logger LOG =
      LoggerFactory.getLogger(TestProcessBasedUpgradeIntegration.class);

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
      HBaseClassTestRule.forClass(TestProcessBasedUpgradeIntegration.class);

  // HBase distribution paths
  private static final String HBASE_DIST_BASE = "/Users/allenwang/xlab/hbase-test-distributions";
  private static final String HBASE_2_5_11 = HBASE_DIST_BASE + "/hbase-2.5.11";
  private static final String HBASE_2_6_2 = HBASE_DIST_BASE + "/hbase-2.6.2";

  // Test table configuration
  private static final TableName TEST_TABLE = TableName.valueOf("upgrade_test_table");
  private static final byte[] CF = Bytes.toBytes("cf");
  private static final byte[] QUAL = Bytes.toBytes("q");
  private static final int NUM_ROWS = 100;

  // ZooKeeper testing utility
  private HBaseTestingUtility testUtil;

  @Parameter
  public String upgradeCheckpoint;

  @Parameters(name = "upgrade-at={0}")
  public static Collection<String> checkpoints() {
    return Arrays.asList(
        HBaseUpgradeCheckpoints.NO_UPGRADE,
        HBaseUpgradeCheckpoints.AFTER_CLUSTER_START,
        HBaseUpgradeCheckpoints.AFTER_CREATE_TABLE,
        HBaseUpgradeCheckpoints.AFTER_WRITE,
        HBaseUpgradeCheckpoints.AFTER_FLUSH
    );
  }

  /**
   * Override setup to start ZooKeeper before cluster initialization.
   */
  @Override
  @Before
  public void setupTest() throws Exception {
    // First, sync the parameter field
    super.setupTest();

    // Start mini ZooKeeper cluster (required for ProcessBasedMiniHBaseCluster)
    LOG.info("Starting mini ZooKeeper cluster");
    testUtil = new HBaseTestingUtility();
    testUtil.startMiniZKCluster();

    // IMPORTANT: Recreate conf AFTER starting ZooKeeper so it has the actual ZK port
    conf = HBaseConfiguration.create(testUtil.getConfiguration());

    LOG.info("ZooKeeper cluster started, quorum: {}",
        testUtil.getConfiguration().get("hbase.zookeeper.quorum"));
  }

  /**
   * Override teardown to shutdown ZooKeeper after cluster cleanup.
   */
  @Override
  @After
  public void tearDownTest() throws Exception {
    // First, clean up cluster and connections
    super.tearDownTest();

    // Then shutdown ZooKeeper
    if (testUtil != null) {
      try {
        LOG.info("Shutting down mini ZooKeeper cluster");
        testUtil.shutdownMiniZKCluster();
        LOG.info("ZooKeeper cluster shut down");
      } catch (Exception e) {
        LOG.warn("Failed to shutdown ZooKeeper cluster", e);
      } finally {
        testUtil = null;
      }
    }
  }

  /**
   * Verify that required HBase distributions are available.
   */
  private void checkDistributionsAvailable() {
    File dist2511 = new File(HBASE_2_5_11);
    File dist262 = new File(HBASE_2_6_2);

    Assume.assumeTrue(
        "HBase 2.5.11 distribution not found at " + HBASE_2_5_11,
        dist2511.exists() && dist2511.isDirectory());
    Assume.assumeTrue(
        "HBase 2.6.2 distribution not found at " + HBASE_2_6_2,
        dist262.exists() && dist262.isDirectory());

    LOG.info("Found HBase distributions: 2.5.11={}, 2.6.2={}", dist2511.exists(), dist262.exists());
  }

  /**
   * Test basic cluster lifecycle: start and shutdown without any operations.
   */
  @Test
  public void testClusterStartupAndShutdown() throws Exception {
    checkDistributionsAvailable();

    LOG.info("Testing cluster startup and shutdown with checkpoint: {}", upgradeCheckpoint);

    // Set upgrade target version
    System.setProperty("hbase.upgrade.home", HBASE_2_6_2);

    try {
      // Build cluster with HBase 2.5.11
      cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
          .numRegionServers(2)
          .allNodesHBaseDistribution(HBASE_2_5_11)
          .build();

      // Start cluster
      cluster.startup();
      LOG.info("Cluster started successfully");

      // Verify cluster is up
      assertTrue("Cluster should be up after startup", cluster.isClusterUp());

      // Get connection
      connection = cluster.getConnection();
      assertNotNull("Connection should not be null", connection);

      // Checkpoint after cluster start
      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      // Verify cluster is still up after checkpoint
      assertTrue("Cluster should be up after checkpoint", cluster.isClusterUp());

      LOG.info("Cluster lifecycle test passed for checkpoint: {}", upgradeCheckpoint);

    } finally {
      System.clearProperty("hbase.upgrade.home");
    }
  }

  /**
   * Test table creation and basic operations with upgrade checkpoints.
   */
  @Test
  public void testTableCreationWithUpgrade() throws Exception {
    checkDistributionsAvailable();

    LOG.info("Testing table creation with checkpoint: {}", upgradeCheckpoint);

    System.setProperty("hbase.upgrade.home", HBASE_2_6_2);

    try {
      // Build and start cluster
      cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
          .numRegionServers(2)
          .allNodesHBaseDistribution(HBASE_2_5_11)
          .build();

      cluster.startup();
      connection = cluster.getConnection();
      admin = connection.getAdmin();

      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      // Create table
      TableDescriptor td = TableDescriptorBuilder.newBuilder(TEST_TABLE)
          .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF))
          .build();

      LOG.info("Creating table: {}", TEST_TABLE);
      admin.createTable(td);

      assertTrue("Table should exist after creation", admin.tableExists(TEST_TABLE));
      LOG.info("Table created successfully");

      checkpoint(HBaseUpgradeCheckpoints.AFTER_CREATE_TABLE);

      // Verify table still exists after potential upgrade
      assertTrue("Table should still exist after checkpoint", admin.tableExists(TEST_TABLE));

      LOG.info("Table creation test passed for checkpoint: {}", upgradeCheckpoint);

    } finally {
      // Clean up table if it exists
      if (admin != null && admin.tableExists(TEST_TABLE)) {
        try {
          admin.disableTable(TEST_TABLE);
          admin.deleteTable(TEST_TABLE);
        } catch (Exception e) {
          LOG.warn("Failed to clean up test table", e);
        }
      }
      System.clearProperty("hbase.upgrade.home");
    }
  }

  /**
   * Test data write and read operations with upgrade checkpoints.
   * This verifies data integrity is maintained across upgrades.
   */
  @Test
  public void testDataIntegrityAcrossUpgrade() throws Exception {
    checkDistributionsAvailable();

    LOG.info("Testing data integrity with checkpoint: {}", upgradeCheckpoint);

    System.setProperty("hbase.upgrade.home", HBASE_2_6_2);

    try {
      // Build and start cluster
      cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
          .numRegionServers(2)
          .allNodesHBaseDistribution(HBASE_2_5_11)
          .build();

      cluster.startup();
      connection = cluster.getConnection();
      admin = connection.getAdmin();

      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      // Create table
      TableDescriptor td = TableDescriptorBuilder.newBuilder(TEST_TABLE)
          .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF))
          .build();
      admin.createTable(td);

      checkpoint(HBaseUpgradeCheckpoints.AFTER_CREATE_TABLE);

      // Write data
      LOG.info("Writing {} rows to table", NUM_ROWS);
      try (Table table = connection.getTable(TEST_TABLE)) {
        for (int i = 0; i < NUM_ROWS; i++) {
          Put put = new Put(Bytes.toBytes("row_" + String.format("%05d", i)));
          put.addColumn(CF, QUAL, Bytes.toBytes("value_" + i));
          table.put(put);
        }
      }
      LOG.info("Data write completed");

      checkpoint(HBaseUpgradeCheckpoints.AFTER_WRITE);

      // Verify data after potential upgrade
      LOG.info("Verifying data integrity after checkpoint");
      try (Table table = connection.getTable(TEST_TABLE)) {
        for (int i = 0; i < NUM_ROWS; i++) {
          Get get = new Get(Bytes.toBytes("row_" + String.format("%05d", i)));
          Result result = table.get(get);

          assertNotNull("Result should not be null for row " + i, result);
          assertTrue("Result should not be empty for row " + i, !result.isEmpty());

          byte[] value = result.getValue(CF, QUAL);
          String expectedValue = "value_" + i;
          assertEquals("Data integrity check failed for row " + i,
              expectedValue, Bytes.toString(value));
        }
      }
      LOG.info("Data integrity verification passed");

      LOG.info("Data integrity test passed for checkpoint: {}", upgradeCheckpoint);

    } finally {
      if (admin != null && admin.tableExists(TEST_TABLE)) {
        try {
          admin.disableTable(TEST_TABLE);
          admin.deleteTable(TEST_TABLE);
        } catch (Exception e) {
          LOG.warn("Failed to clean up test table", e);
        }
      }
      System.clearProperty("hbase.upgrade.home");
    }
  }

  /**
   * Test flush operation with upgrade checkpoint.
   */
  @Test
  public void testFlushWithUpgrade() throws Exception {
    checkDistributionsAvailable();

    LOG.info("Testing flush operation with checkpoint: {}", upgradeCheckpoint);

    System.setProperty("hbase.upgrade.home", HBASE_2_6_2);

    try {
      // Build and start cluster
      cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
          .numRegionServers(2)
          .allNodesHBaseDistribution(HBASE_2_5_11)
          .build();

      cluster.startup();
      connection = cluster.getConnection();
      admin = connection.getAdmin();

      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      // Create table
      TableDescriptor td = TableDescriptorBuilder.newBuilder(TEST_TABLE)
          .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF))
          .build();
      admin.createTable(td);

      checkpoint(HBaseUpgradeCheckpoints.AFTER_CREATE_TABLE);

      // Write data
      LOG.info("Writing data before flush");
      try (Table table = connection.getTable(TEST_TABLE)) {
        for (int i = 0; i < 10; i++) {
          Put put = new Put(Bytes.toBytes("flush_row_" + i));
          put.addColumn(CF, QUAL, Bytes.toBytes("flush_value_" + i));
          table.put(put);
        }
      }

      checkpoint(HBaseUpgradeCheckpoints.AFTER_WRITE);

      // Flush table
      LOG.info("Flushing table: {}", TEST_TABLE);
      admin.flush(TEST_TABLE);
      LOG.info("Flush completed");

      checkpoint(HBaseUpgradeCheckpoints.AFTER_FLUSH);

      // Verify data after flush and potential upgrade
      LOG.info("Verifying data after flush");
      try (Table table = connection.getTable(TEST_TABLE)) {
        for (int i = 0; i < 10; i++) {
          Get get = new Get(Bytes.toBytes("flush_row_" + i));
          Result result = table.get(get);
          assertNotNull("Result should not be null after flush", result);
          assertTrue("Result should not be empty after flush", !result.isEmpty());
        }
      }
      LOG.info("Flush test passed for checkpoint: {}", upgradeCheckpoint);

    } finally {
      if (admin != null && admin.tableExists(TEST_TABLE)) {
        try {
          admin.disableTable(TEST_TABLE);
          admin.deleteTable(TEST_TABLE);
        } catch (Exception e) {
          LOG.warn("Failed to clean up test table", e);
        }
      }
      System.clearProperty("hbase.upgrade.home");
    }
  }

  /**
   * Test that node identities (ServerName) are preserved during upgrade.
   * This is a critical requirement for rolling upgrades.
   */
  @Test
  public void testNodeIdentityPreservation() throws Exception {
    checkDistributionsAvailable();

    // Only run this test when an upgrade is actually performed
    if (HBaseUpgradeCheckpoints.NO_UPGRADE.equals(upgradeCheckpoint)) {
      LOG.info("Skipping node identity test for NO_UPGRADE checkpoint");
      return;
    }

    LOG.info("Testing node identity preservation with checkpoint: {}", upgradeCheckpoint);

    System.setProperty("hbase.upgrade.home", HBASE_2_6_2);

    try {
      // Build and start cluster with 3 region servers
      cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
          .numRegionServers(3)
          .allNodesHBaseDistribution(HBASE_2_5_11)
          .build();

      cluster.startup();
      connection = cluster.getConnection();
      admin = connection.getAdmin();

      // Verify initial RS count
      int initialRsCount = cluster.getNumLiveRegionServers();
      assertEquals("Should have 3 region servers", 3, initialRsCount);
      LOG.info("Initial cluster has {} region servers", initialRsCount);

      // The checkpoint() method automatically verifies node identity preservation
      // If identities change, it will throw an AssertionError
      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      // Create and populate table
      TableDescriptor td = TableDescriptorBuilder.newBuilder(TEST_TABLE)
          .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF))
          .build();
      admin.createTable(td);
      checkpoint(HBaseUpgradeCheckpoints.AFTER_CREATE_TABLE);

      try (Table table = connection.getTable(TEST_TABLE)) {
        Put put = new Put(Bytes.toBytes("identity_test"));
        put.addColumn(CF, QUAL, Bytes.toBytes("test_value"));
        table.put(put);
      }
      checkpoint(HBaseUpgradeCheckpoints.AFTER_WRITE);

      admin.flush(TEST_TABLE);
      checkpoint(HBaseUpgradeCheckpoints.AFTER_FLUSH);

      // Verify final RS count matches initial
      int finalRsCount = cluster.getNumLiveRegionServers();
      assertEquals("RS count should be preserved after upgrade", initialRsCount, finalRsCount);

      LOG.info("Node identity preservation test passed for checkpoint: {}", upgradeCheckpoint);

    } finally {
      if (admin != null && admin.tableExists(TEST_TABLE)) {
        try {
          admin.disableTable(TEST_TABLE);
          admin.deleteTable(TEST_TABLE);
        } catch (Exception e) {
          LOG.warn("Failed to clean up test table", e);
        }
      }
      System.clearProperty("hbase.upgrade.home");
    }
  }

  /**
   * Test complete end-to-end upgrade scenario.
   * This test performs a full workflow: start -> create table -> write -> flush -> read
   * with upgrade at the configured checkpoint.
   */
  @Test
  public void testEndToEndUpgradeScenario() throws Exception {
    checkDistributionsAvailable();

    LOG.info("Testing end-to-end upgrade scenario with checkpoint: {}", upgradeCheckpoint);

    System.setProperty("hbase.upgrade.home", HBASE_2_6_2);

    try {
      // Phase 1: Start cluster
      LOG.info("Phase 1: Starting cluster");
      cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
          .numRegionServers(2)
          .allNodesHBaseDistribution(HBASE_2_5_11)
          .build();

      cluster.startup();
      connection = cluster.getConnection();
      admin = connection.getAdmin();

      assertTrue("Cluster should be up", cluster.isClusterUp());
      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      // Phase 2: Create table
      LOG.info("Phase 2: Creating table");
      TableDescriptor td = TableDescriptorBuilder.newBuilder(TEST_TABLE)
          .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF))
          .build();
      admin.createTable(td);
      assertTrue("Table should exist", admin.tableExists(TEST_TABLE));
      checkpoint(HBaseUpgradeCheckpoints.AFTER_CREATE_TABLE);

      // Phase 3: Write data
      LOG.info("Phase 3: Writing data");
      try (Table table = connection.getTable(TEST_TABLE)) {
        for (int i = 0; i < 50; i++) {
          Put put = new Put(Bytes.toBytes("e2e_row_" + String.format("%03d", i)));
          put.addColumn(CF, QUAL, Bytes.toBytes("e2e_value_" + i));
          table.put(put);
        }
      }
      checkpoint(HBaseUpgradeCheckpoints.AFTER_WRITE);

      // Phase 4: Flush
      LOG.info("Phase 4: Flushing table");
      admin.flush(TEST_TABLE);
      checkpoint(HBaseUpgradeCheckpoints.AFTER_FLUSH);

      // Phase 5: Read and verify
      LOG.info("Phase 5: Reading and verifying data");
      try (Table table = connection.getTable(TEST_TABLE)) {
        int verifiedCount = 0;
        for (int i = 0; i < 50; i++) {
          Get get = new Get(Bytes.toBytes("e2e_row_" + String.format("%03d", i)));
          Result result = table.get(get);
          assertNotNull("Result should not be null", result);
          assertTrue("Result should not be empty", !result.isEmpty());

          byte[] value = result.getValue(CF, QUAL);
          assertEquals("Data should match", "e2e_value_" + i, Bytes.toString(value));
          verifiedCount++;
        }
        LOG.info("Verified {} rows successfully", verifiedCount);
      }

      LOG.info("End-to-end upgrade test passed for checkpoint: {}", upgradeCheckpoint);

    } finally {
      if (admin != null && admin.tableExists(TEST_TABLE)) {
        try {
          admin.disableTable(TEST_TABLE);
          admin.deleteTable(TEST_TABLE);
        } catch (Exception e) {
          LOG.warn("Failed to clean up test table", e);
        }
      }
      System.clearProperty("hbase.upgrade.home");
    }
  }
}
