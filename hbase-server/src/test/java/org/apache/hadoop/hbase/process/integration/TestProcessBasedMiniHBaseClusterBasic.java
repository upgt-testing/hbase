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
 * Basic integration tests for ProcessBasedMiniHBaseCluster.
 * Tests fundamental cluster operations like startup, shutdown, and basic data operations.
 *
 * NOTE: These tests require a built HBase distribution to be available.
 * Set the HBASE_HOME environment variable to point to a valid HBase installation.
 */
@Category(LargeTests.class)
public class TestProcessBasedMiniHBaseClusterBasic {
  private static final Logger LOG = LoggerFactory.getLogger(TestProcessBasedMiniHBaseClusterBasic.class);

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
      HBaseClassTestRule.forClass(TestProcessBasedMiniHBaseClusterBasic.class);

  private Configuration conf;
  private ProcessBasedMiniHBaseCluster cluster;
  private HBaseTestingUtility testUtil;

  @Before
  public void setUp() throws Exception {
    testUtil = new HBaseTestingUtility();

    // Start mini ZooKeeper cluster (required for HBase)
    testUtil.startMiniZKCluster();

    // IMPORTANT: Create conf AFTER starting ZooKeeper so it has the actual ZK port
    conf = HBaseConfiguration.create(testUtil.getConfiguration());

    // Check for HBASE_HOME environment variable
    String hbaseHome = System.getenv("HBASE_HOME");
    if (hbaseHome == null) {
      hbaseHome = System.getProperty("hbase.home", "/opt/hbase");
      LOG.warn("HBASE_HOME not set, using default: {}", hbaseHome);
    }

    LOG.info("Using HBase distribution: {}", hbaseHome);

    // Build process-based cluster
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numMasters(1)
        .numRegionServers(3)
        .allNodesHBaseDistribution(hbaseHome)
        .build();

    LOG.info("Starting ProcessBasedMiniHBaseCluster");
    cluster.startup();
    LOG.info("Cluster started successfully");
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

  @Test(timeout = 300000) // 5 minutes
  public void testClusterStartup() throws Exception {
    LOG.info("Testing cluster startup");

    // Verify we can connect
    try (Connection conn = cluster.getConnection()) {
      assertNotNull("Connection should not be null", conn);

      // Verify we can get admin
      Admin admin = conn.getAdmin();
      assertNotNull("Admin should not be null", admin);

      // Verify cluster metrics
      assertNotNull("Cluster metrics should not be null", cluster.getClusterMetrics());
      assertEquals("Should have 3 region servers", 3,
          cluster.getClusterMetrics().getLiveServerMetrics().size());
    }

    LOG.info("Cluster startup test passed");
  }

  @Test(timeout = 300000) // 5 minutes
  public void testBasicOperations() throws Exception {
    LOG.info("Testing basic table operations");

    TableName tableName = TableName.valueOf("test_table");

    try (Connection conn = cluster.getConnection()) {
      Admin admin = conn.getAdmin();

      // Create table
      TableDescriptor td = TableDescriptorBuilder
          .newBuilder(tableName)
          .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf"))
          .build();

      LOG.info("Creating table: {}", tableName);
      admin.createTable(td);
      assertTrue("Table should exist", admin.tableExists(tableName));

      // Put data
      try (Table table = conn.getTable(tableName)) {
        Put put = new Put(Bytes.toBytes("row1"));
        put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("qual"), Bytes.toBytes("value1"));

        LOG.info("Putting data into table");
        table.put(put);

        // Get data
        Get get = new Get(Bytes.toBytes("row1"));
        Result result = table.get(get);

        assertNotNull("Result should not be null", result);
        assertFalse("Result should not be empty", result.isEmpty());

        byte[] value = result.getValue(Bytes.toBytes("cf"), Bytes.toBytes("qual"));
        assertNotNull("Value should not be null", value);
        assertEquals("Value should match", "value1", Bytes.toString(value));
      }

      // Drop table
      LOG.info("Disabling and deleting table");
      admin.disableTable(tableName);
      admin.deleteTable(tableName);
      assertFalse("Table should not exist", admin.tableExists(tableName));
    }

    LOG.info("Basic operations test passed");
  }

  @Test(timeout = 60000)
  public void testUnsupportedOperations() throws Exception {
    LOG.info("Testing unsupported operations throw exceptions");

    // Test that direct access methods throw UnsupportedOperationException
    try {
      cluster.getMasterAdminService();
      fail("getMasterAdminService() should throw UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      // Expected
      assertTrue(e.getMessage().contains("not supported"));
    }

    try {
      cluster.getAdminProtocol(null);
      fail("getAdminProtocol() should throw UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      // Expected
      assertTrue(e.getMessage().contains("not supported"));
    }

    LOG.info("Unsupported operations test passed");
  }

  @Test(timeout = 120000)
  public void testWaitForClusterReady() throws Exception {
    LOG.info("Testing wait for cluster ready");

    // Cluster should already be ready from setUp()
    boolean ready = cluster.waitForActiveAndReadyMaster(10000);
    assertTrue("Cluster should be ready", ready);

    LOG.info("Wait for cluster ready test passed");
  }
}
