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
package org.apache.hadoop.hbase.process.integration;

import static org.junit.Assert.*;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
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
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for rolling upgrade scenarios using ProcessBasedMiniHBaseCluster.
 * Tests upgrading RegionServers from one version to another while maintaining data integrity.
 *
 * These tests demonstrate the key capability of ProcessBasedMiniHBaseCluster:
 * simulating rolling upgrades between HBase versions.
 */
@Category(LargeTests.class)
public class TestProcessBasedMiniHBaseClusterUpgrade {
  private static final Logger LOG = LoggerFactory.getLogger(TestProcessBasedMiniHBaseClusterUpgrade.class);

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
      HBaseClassTestRule.forClass(TestProcessBasedMiniHBaseClusterUpgrade.class);

  private static final String DIST_DIR = "/Users/allenwang/xlab/hbase-test-distributions";
  private static final String HBASE_2_5_11 = DIST_DIR + "/hbase-2.5.11";
  private static final String HBASE_2_6_2 = DIST_DIR + "/hbase-2.6.2";

  private Configuration conf;
  private ProcessBasedMiniHBaseCluster cluster;
  private HBaseTestingUtility testUtil;

  @Before
  public void setUp() throws Exception {
    testUtil = new HBaseTestingUtility();

    // Start mini ZooKeeper cluster
    testUtil.startMiniZKCluster();

    // IMPORTANT: Create conf AFTER starting ZooKeeper so it has the actual ZK port
    conf = HBaseConfiguration.create(testUtil.getConfiguration());

    LOG.info("Test distributions directory: {}", DIST_DIR);
    LOG.info("Using HBase 2.5.11: {}", HBASE_2_5_11);
    LOG.info("Using HBase 2.6.2: {}", HBASE_2_6_2);
  }

  @After
  public void tearDown() throws Exception {
    if (cluster != null) {
      LOG.info("Shutting down cluster");
      cluster.shutdown();
    }

    if (testUtil != null) {
      testUtil.shutdownMiniZKCluster();
    }
  }

  @Test(timeout = 600000) // 10 minutes
  public void testRollingUpgradeRegionServers() throws Exception {
    LOG.info("=== Testing Rolling Upgrade: RegionServers 2.5.11 -> 2.6.2 ===");

    // Start cluster with all nodes running HBase 2.5.11
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numMasters(1)
        .numRegionServers(3)
        .allNodesHBaseDistribution(HBASE_2_5_11)
        .build();

    LOG.info("Starting cluster with HBase 2.5.11");
    cluster.startup();

    // Create test table and write data
    TableName tableName = TableName.valueOf("upgrade_test_table");
    createTableAndWriteData(tableName, 100);

    LOG.info("Verifying initial data with HBase 2.5.11");
    verifyData(tableName, 100);

    // Perform rolling upgrade of each RegionServer
    for (int i = 0; i < 3; i++) {
      LOG.info("=== Upgrading RegionServer {} from 2.5.11 to 2.6.2 ===", i);

      ServerName rsBeforeUpgrade = cluster.getRegionServerName(i);
      LOG.info("RS {} before upgrade: {}", i, rsBeforeUpgrade);

      // Change version and restart
      cluster.changeRegionServerVersion(i, HBASE_2_6_2);

      ServerName rsAfterUpgrade = cluster.getRegionServerName(i);
      LOG.info("RS {} after upgrade: {}", i, rsAfterUpgrade);

      // Verify identity is preserved (same host:port)
      assertEquals("RS hostname should be preserved",
          rsBeforeUpgrade.getHostname(), rsAfterUpgrade.getHostname());
      assertEquals("RS port should be preserved",
          rsBeforeUpgrade.getPort(), rsAfterUpgrade.getPort());

      LOG.info("RS {} identity preserved: {} -> {}", i, rsBeforeUpgrade, rsAfterUpgrade);

      // Wait for cluster to stabilize
      cluster.waitClusterUp();

      // Verify data is still accessible after each upgrade
      LOG.info("Verifying data after upgrading RS {}", i);
      verifyData(tableName, 100);

      // Verify cluster has all 3 region servers
      int liveRs = cluster.getClusterMetrics().getLiveServerMetrics().size();
      assertEquals("Should have 3 live region servers", 3, liveRs);

      LOG.info("RS {} upgrade successful. Cluster status: {} live RSs", i, liveRs);
    }

    LOG.info("=== Rolling Upgrade Complete ===");
    LOG.info("All RegionServers upgraded from 2.5.11 to 2.6.2");

    // Final verification
    verifyData(tableName, 100);

    // Verify all RegionServers are now running 2.6.2
    for (int i = 0; i < 3; i++) {
      String version = cluster.getRegionServerVersion(i);
      assertEquals("RS should be running 2.6.2", HBASE_2_6_2, version);
      LOG.info("RS {} confirmed running version: {}", i, version);
    }

    LOG.info("Rolling upgrade test PASSED");
  }

  @Test(timeout = 600000) // 10 minutes
  public void testMixedVersionCluster() throws Exception {
    LOG.info("=== Testing Mixed Version Cluster ===");

    // Create cluster with mixed versions
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numMasters(1)
        .numRegionServers(3)
        .masterHBaseDistribution(HBASE_2_5_11)
        .regionServerHBaseDistribution(0, HBASE_2_6_2)
        .regionServerHBaseDistribution(1, HBASE_2_6_2)
        .regionServerHBaseDistribution(2, HBASE_2_5_11)
        .build();

    LOG.info("Starting mixed version cluster:");
    LOG.info("  Master: 2.5.11");
    LOG.info("  RS 0: 2.6.2");
    LOG.info("  RS 1: 2.6.2");
    LOG.info("  RS 2: 2.5.11");

    cluster.startup();

    // Create table and write data
    TableName tableName = TableName.valueOf("mixed_version_test");
    createTableAndWriteData(tableName, 50);

    // Verify data works in mixed version environment
    verifyData(tableName, 50);

    // Verify cluster composition
    assertEquals("Should have 3 region servers", 3,
        cluster.getClusterMetrics().getLiveServerMetrics().size());

    // Verify versions
    assertEquals(HBASE_2_5_11, cluster.getMasterVersion(0));
    assertEquals(HBASE_2_6_2, cluster.getRegionServerVersion(0));
    assertEquals(HBASE_2_6_2, cluster.getRegionServerVersion(1));
    assertEquals(HBASE_2_5_11, cluster.getRegionServerVersion(2));

    LOG.info("Mixed version cluster test PASSED");
  }

  @Test(timeout = 300000) // 5 minutes
  public void testNodeIdentityPreservation() throws Exception {
    LOG.info("=== Testing Node Identity Preservation ===");

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numMasters(1)
        .numRegionServers(2)
        .allNodesHBaseDistribution(HBASE_2_5_11)
        .build();

    cluster.startup();

    // Get initial server names
    ServerName rs0Before = cluster.getRegionServerName(0);
    ServerName rs1Before = cluster.getRegionServerName(1);

    LOG.info("Initial RS 0: {}", rs0Before);
    LOG.info("Initial RS 1: {}", rs1Before);

    // Restart RS 0
    LOG.info("Restarting RS 0 (same version)");
    cluster.restartRegionServer(0);

    ServerName rs0After = cluster.getRegionServerName(0);
    LOG.info("RS 0 after restart: {}", rs0After);

    // Verify identity preserved
    assertEquals("RS 0 hostname should be preserved",
        rs0Before.getHostname(), rs0After.getHostname());
    assertEquals("RS 0 port should be preserved",
        rs0Before.getPort(), rs0After.getPort());

    // Upgrade RS 1
    LOG.info("Upgrading RS 1 from 2.5.11 to 2.6.2");
    cluster.changeRegionServerVersion(1, HBASE_2_6_2);

    ServerName rs1After = cluster.getRegionServerName(1);
    LOG.info("RS 1 after upgrade: {}", rs1After);

    // Verify identity preserved during upgrade
    assertEquals("RS 1 hostname should be preserved during upgrade",
        rs1Before.getHostname(), rs1After.getHostname());
    assertEquals("RS 1 port should be preserved during upgrade",
        rs1Before.getPort(), rs1After.getPort());

    LOG.info("Node identity preservation test PASSED");
  }

  @Test(timeout = 300000) // 5 minutes
  public void testDowngradeScenario() throws Exception {
    LOG.info("=== Testing Downgrade Scenario ===");

    // Start with newer version
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numMasters(1)
        .numRegionServers(2)
        .allNodesHBaseDistribution(HBASE_2_6_2)
        .build();

    LOG.info("Starting cluster with HBase 2.6.2");
    cluster.startup();

    TableName tableName = TableName.valueOf("downgrade_test");
    createTableAndWriteData(tableName, 30);
    verifyData(tableName, 30);

    // Downgrade RS 0 to 2.5.11
    LOG.info("Downgrading RS 0 from 2.6.2 to 2.5.11");
    cluster.changeRegionServerVersion(0, HBASE_2_5_11);

    // Verify data still accessible
    verifyData(tableName, 30);

    // Verify versions
    assertEquals(HBASE_2_6_2, cluster.getMasterVersion(0));
    assertEquals(HBASE_2_5_11, cluster.getRegionServerVersion(0));
    assertEquals(HBASE_2_6_2, cluster.getRegionServerVersion(1));

    LOG.info("Downgrade scenario test PASSED");
  }

  // ========== Helper Methods ==========

  private void createTableAndWriteData(TableName tableName, int numRows) throws Exception {
    LOG.info("Creating table {} and writing {} rows", tableName, numRows);

    // Don't close the connection - it's shared by the cluster
    Connection conn = cluster.getConnection();
    Admin admin = conn.getAdmin();

    // Create table
    TableDescriptor td = TableDescriptorBuilder
        .newBuilder(tableName)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf"))
        .build();

    admin.createTable(td);
    LOG.info("Table created: {}", tableName);

    // Write data
    try (Table table = conn.getTable(tableName)) {
      for (int i = 0; i < numRows; i++) {
        Put put = new Put(Bytes.toBytes("row" + i));
        put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("qual"),
            Bytes.toBytes("value" + i));
        table.put(put);
      }
    }

    LOG.info("Wrote {} rows to {}", numRows, tableName);
  }

  private void verifyData(TableName tableName, int numRows) throws Exception {
    LOG.info("Verifying {} rows in {}", numRows, tableName);

    // Don't close the connection - it's shared by the cluster
    Connection conn = cluster.getConnection();
    try (Table table = conn.getTable(tableName)) {
      for (int i = 0; i < numRows; i++) {
        Get get = new Get(Bytes.toBytes("row" + i));
        Result result = table.get(get);

        assertNotNull("Result should not be null for row" + i, result);
        assertFalse("Result should not be empty for row" + i, result.isEmpty());

        byte[] value = result.getValue(Bytes.toBytes("cf"), Bytes.toBytes("qual"));
        assertNotNull("Value should not be null for row" + i, value);
        assertEquals("Value should match for row" + i,
            "value" + i, Bytes.toString(value));
      }
    }

    LOG.info("Data verification successful: {} rows", numRows);
  }
}
