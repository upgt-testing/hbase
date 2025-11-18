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
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import org.apache.hadoop.hbase.ClusterMetrics.Option;
import org.apache.hadoop.hbase.Waiter.ExplainingPredicate;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestFullLogReconstruction}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Tests WAL (Write-Ahead Log) recovery after a RegionServer failure.
 * This is critical for upgrade testing as it verifies data durability.
 *
 * @see TestFullLogReconstruction Original test using MiniHBaseCluster
 */
@Category({ MiscTests.class, MediumTests.class })
public class TestFullLogReconstruction_ProcessBased extends ProcessBasedUpgradeTestBase {
  private static final Logger LOG =
      LoggerFactory.getLogger(TestFullLogReconstruction_ProcessBased.class);

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestFullLogReconstruction_ProcessBased.class);

  private final static TableName TABLE_NAME = TableName.valueOf("tabletest");
  private final static byte[] FAMILY = Bytes.toBytes("family");

  /**
   * Test the whole reconstruction loop. Build a table with multiple regions and load data.
   * Kill one of the region servers and scan the table. We should see all the rows due to
   * WAL recovery.
   * Checkpoint: NO_UPGRADE
   */
  @Test(timeout = 300000)
  public void testReconstruction_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table with multiple regions
    byte[][] splits = new byte[][] {
      Bytes.toBytes("aaa"), Bytes.toBytes("bbb"), Bytes.toBytes("ccc"),
      Bytes.toBytes("ddd"), Bytes.toBytes("eee"), Bytes.toBytes("fff"),
      Bytes.toBytes("ggg"), Bytes.toBytes("hhh"), Bytes.toBytes("iii")
    };
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(TABLE_NAME);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY));
    admin.createTable(tableBuilder.build(), splits);
    checkpoint("AFTER_CREATE_TABLE");

    // Load up the table with simple rows and count them
    int initialCount;
    try (Table table = connection.getTable(TABLE_NAME)) {
      initialCount = loadTable(table);
    }
    checkpoint("AFTER_INITIAL_LOAD");

    int count;
    try (Table table = connection.getTable(TABLE_NAME)) {
      count = countRows(table);
    }
    assertEquals(initialCount, count);

    // Load more data
    for (int i = 0; i < 4; i++) {
      try (Table table = connection.getTable(TABLE_NAME)) {
        loadTable(table);
      }
    }
    checkpoint("AFTER_ADDITIONAL_LOADS");

    // Get one of the region servers to kill
    ServerName rsToKill = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS))
        .getLiveServerMetrics().keySet().iterator().next();
    LOG.info("Killing {}", rsToKill);

    // Kill the region server
    cluster.killRegionServer(rsToKill);
    checkpoint("AFTER_KILL_RS");

    // Wait for the RS to be marked as dead
    admin.getConnection().clearRegionLocationCache();
    Waiter.waitFor(conf, 60000, new ExplainingPredicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.DEAD_SERVERS));
        return metrics.getDeadServerNames().size() >= 1;
      }

      @Override
      public String explainFailure() throws Exception {
        return "Region server " + rsToKill + " is not yet in dead servers list";
      }
    });

    // Wait for regions to be reassigned and WAL recovery to complete
    LOG.info("Waiting for regions to be reassigned after RS death");
    Thread.sleep(10000); // Give time for WAL recovery

    LOG.info("Starting count after RS death");
    int newCount;
    try (Table table = connection.getTable(TABLE_NAME)) {
      newCount = countRows(table);
    }
    assertEquals("All data should be recovered after RS failure", count, newCount);
    checkpoint("AFTER_VERIFY_RECOVERY");

    // Disable table before deletion (required by HBase)
    admin.disableTable(TABLE_NAME);
    admin.deleteTable(TABLE_NAME);
  }

  /**
   * Test the whole reconstruction loop. Build a table with multiple regions and load data.
   * Kill one of the region servers and scan the table. We should see all the rows due to
   * WAL recovery.
   * Checkpoint: AFTER_CLUSTER_START
   */
  @Test(timeout = 300000)
  public void testReconstruction_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table with multiple regions
    byte[][] splits = new byte[][] {
      Bytes.toBytes("aaa"), Bytes.toBytes("bbb"), Bytes.toBytes("ccc"),
      Bytes.toBytes("ddd"), Bytes.toBytes("eee"), Bytes.toBytes("fff"),
      Bytes.toBytes("ggg"), Bytes.toBytes("hhh"), Bytes.toBytes("iii")
    };
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(TABLE_NAME);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY));
    admin.createTable(tableBuilder.build(), splits);
    checkpoint("AFTER_CREATE_TABLE");

    // Load up the table with simple rows and count them
    int initialCount;
    try (Table table = connection.getTable(TABLE_NAME)) {
      initialCount = loadTable(table);
    }
    checkpoint("AFTER_INITIAL_LOAD");

    int count;
    try (Table table = connection.getTable(TABLE_NAME)) {
      count = countRows(table);
    }
    assertEquals(initialCount, count);

    // Load more data
    for (int i = 0; i < 4; i++) {
      try (Table table = connection.getTable(TABLE_NAME)) {
        loadTable(table);
      }
    }
    checkpoint("AFTER_ADDITIONAL_LOADS");

    // Get one of the region servers to kill
    ServerName rsToKill = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS))
        .getLiveServerMetrics().keySet().iterator().next();
    LOG.info("Killing {}", rsToKill);

    // Kill the region server
    cluster.killRegionServer(rsToKill);
    checkpoint("AFTER_KILL_RS");

    // Wait for the RS to be marked as dead
    admin.getConnection().clearRegionLocationCache();
    Waiter.waitFor(conf, 60000, new ExplainingPredicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.DEAD_SERVERS));
        return metrics.getDeadServerNames().size() >= 1;
      }

      @Override
      public String explainFailure() throws Exception {
        return "Region server " + rsToKill + " is not yet in dead servers list";
      }
    });

    // Wait for regions to be reassigned and WAL recovery to complete
    LOG.info("Waiting for regions to be reassigned after RS death");
    Thread.sleep(10000); // Give time for WAL recovery

    LOG.info("Starting count after RS death");
    int newCount;
    try (Table table = connection.getTable(TABLE_NAME)) {
      newCount = countRows(table);
    }
    assertEquals("All data should be recovered after RS failure", count, newCount);
    checkpoint("AFTER_VERIFY_RECOVERY");

    // Disable table before deletion (required by HBase)
    admin.disableTable(TABLE_NAME);
    admin.deleteTable(TABLE_NAME);
  }

  /**
   * Test the whole reconstruction loop. Build a table with multiple regions and load data.
   * Kill one of the region servers and scan the table. We should see all the rows due to
   * WAL recovery.
   * Checkpoint: AFTER_CREATE_TABLE
   */
  @Test(timeout = 300000)
  public void testReconstruction_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table with multiple regions
    byte[][] splits = new byte[][] {
      Bytes.toBytes("aaa"), Bytes.toBytes("bbb"), Bytes.toBytes("ccc"),
      Bytes.toBytes("ddd"), Bytes.toBytes("eee"), Bytes.toBytes("fff"),
      Bytes.toBytes("ggg"), Bytes.toBytes("hhh"), Bytes.toBytes("iii")
    };
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(TABLE_NAME);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY));
    admin.createTable(tableBuilder.build(), splits);
    checkpoint("AFTER_CREATE_TABLE");

    // Load up the table with simple rows and count them
    int initialCount;
    try (Table table = connection.getTable(TABLE_NAME)) {
      initialCount = loadTable(table);
    }
    checkpoint("AFTER_INITIAL_LOAD");

    int count;
    try (Table table = connection.getTable(TABLE_NAME)) {
      count = countRows(table);
    }
    assertEquals(initialCount, count);

    // Load more data
    for (int i = 0; i < 4; i++) {
      try (Table table = connection.getTable(TABLE_NAME)) {
        loadTable(table);
      }
    }
    checkpoint("AFTER_ADDITIONAL_LOADS");

    // Get one of the region servers to kill
    ServerName rsToKill = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS))
        .getLiveServerMetrics().keySet().iterator().next();
    LOG.info("Killing {}", rsToKill);

    // Kill the region server
    cluster.killRegionServer(rsToKill);
    checkpoint("AFTER_KILL_RS");

    // Wait for the RS to be marked as dead
    admin.getConnection().clearRegionLocationCache();
    Waiter.waitFor(conf, 60000, new ExplainingPredicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.DEAD_SERVERS));
        return metrics.getDeadServerNames().size() >= 1;
      }

      @Override
      public String explainFailure() throws Exception {
        return "Region server " + rsToKill + " is not yet in dead servers list";
      }
    });

    // Wait for regions to be reassigned and WAL recovery to complete
    LOG.info("Waiting for regions to be reassigned after RS death");
    Thread.sleep(10000); // Give time for WAL recovery

    LOG.info("Starting count after RS death");
    int newCount;
    try (Table table = connection.getTable(TABLE_NAME)) {
      newCount = countRows(table);
    }
    assertEquals("All data should be recovered after RS failure", count, newCount);
    checkpoint("AFTER_VERIFY_RECOVERY");

    // Disable table before deletion (required by HBase)
    admin.disableTable(TABLE_NAME);
    admin.deleteTable(TABLE_NAME);
  }

  /**
   * Test the whole reconstruction loop. Build a table with multiple regions and load data.
   * Kill one of the region servers and scan the table. We should see all the rows due to
   * WAL recovery.
   * Checkpoint: AFTER_INITIAL_LOAD
   */
  @Test(timeout = 300000)
  public void testReconstruction_AFTER_INITIAL_LOAD() throws Exception {
    upgradeCheckpoint = "AFTER_INITIAL_LOAD";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table with multiple regions
    byte[][] splits = new byte[][] {
      Bytes.toBytes("aaa"), Bytes.toBytes("bbb"), Bytes.toBytes("ccc"),
      Bytes.toBytes("ddd"), Bytes.toBytes("eee"), Bytes.toBytes("fff"),
      Bytes.toBytes("ggg"), Bytes.toBytes("hhh"), Bytes.toBytes("iii")
    };
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(TABLE_NAME);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY));
    admin.createTable(tableBuilder.build(), splits);
    checkpoint("AFTER_CREATE_TABLE");

    // Load up the table with simple rows and count them
    int initialCount;
    try (Table table = connection.getTable(TABLE_NAME)) {
      initialCount = loadTable(table);
    }
    checkpoint("AFTER_INITIAL_LOAD");

    int count;
    try (Table table = connection.getTable(TABLE_NAME)) {
      count = countRows(table);
    }
    assertEquals(initialCount, count);

    // Load more data
    for (int i = 0; i < 4; i++) {
      try (Table table = connection.getTable(TABLE_NAME)) {
        loadTable(table);
      }
    }
    checkpoint("AFTER_ADDITIONAL_LOADS");

    // Get one of the region servers to kill
    ServerName rsToKill = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS))
        .getLiveServerMetrics().keySet().iterator().next();
    LOG.info("Killing {}", rsToKill);

    // Kill the region server
    cluster.killRegionServer(rsToKill);
    checkpoint("AFTER_KILL_RS");

    // Wait for the RS to be marked as dead
    admin.getConnection().clearRegionLocationCache();
    Waiter.waitFor(conf, 60000, new ExplainingPredicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.DEAD_SERVERS));
        return metrics.getDeadServerNames().size() >= 1;
      }

      @Override
      public String explainFailure() throws Exception {
        return "Region server " + rsToKill + " is not yet in dead servers list";
      }
    });

    // Wait for regions to be reassigned and WAL recovery to complete
    LOG.info("Waiting for regions to be reassigned after RS death");
    Thread.sleep(10000); // Give time for WAL recovery

    LOG.info("Starting count after RS death");
    int newCount;
    try (Table table = connection.getTable(TABLE_NAME)) {
      newCount = countRows(table);
    }
    assertEquals("All data should be recovered after RS failure", count, newCount);
    checkpoint("AFTER_VERIFY_RECOVERY");

    // Disable table before deletion (required by HBase)
    admin.disableTable(TABLE_NAME);
    admin.deleteTable(TABLE_NAME);
  }

  /**
   * Test the whole reconstruction loop. Build a table with multiple regions and load data.
   * Kill one of the region servers and scan the table. We should see all the rows due to
   * WAL recovery.
   * Checkpoint: AFTER_ADDITIONAL_LOADS
   */
  @Test(timeout = 300000)
  public void testReconstruction_AFTER_ADDITIONAL_LOADS() throws Exception {
    upgradeCheckpoint = "AFTER_ADDITIONAL_LOADS";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table with multiple regions
    byte[][] splits = new byte[][] {
      Bytes.toBytes("aaa"), Bytes.toBytes("bbb"), Bytes.toBytes("ccc"),
      Bytes.toBytes("ddd"), Bytes.toBytes("eee"), Bytes.toBytes("fff"),
      Bytes.toBytes("ggg"), Bytes.toBytes("hhh"), Bytes.toBytes("iii")
    };
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(TABLE_NAME);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY));
    admin.createTable(tableBuilder.build(), splits);
    checkpoint("AFTER_CREATE_TABLE");

    // Load up the table with simple rows and count them
    int initialCount;
    try (Table table = connection.getTable(TABLE_NAME)) {
      initialCount = loadTable(table);
    }
    checkpoint("AFTER_INITIAL_LOAD");

    int count;
    try (Table table = connection.getTable(TABLE_NAME)) {
      count = countRows(table);
    }
    assertEquals(initialCount, count);

    // Load more data
    for (int i = 0; i < 4; i++) {
      try (Table table = connection.getTable(TABLE_NAME)) {
        loadTable(table);
      }
    }
    checkpoint("AFTER_ADDITIONAL_LOADS");

    // Get one of the region servers to kill
    ServerName rsToKill = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS))
        .getLiveServerMetrics().keySet().iterator().next();
    LOG.info("Killing {}", rsToKill);

    // Kill the region server
    cluster.killRegionServer(rsToKill);
    checkpoint("AFTER_KILL_RS");

    // Wait for the RS to be marked as dead
    admin.getConnection().clearRegionLocationCache();
    Waiter.waitFor(conf, 60000, new ExplainingPredicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.DEAD_SERVERS));
        return metrics.getDeadServerNames().size() >= 1;
      }

      @Override
      public String explainFailure() throws Exception {
        return "Region server " + rsToKill + " is not yet in dead servers list";
      }
    });

    // Wait for regions to be reassigned and WAL recovery to complete
    LOG.info("Waiting for regions to be reassigned after RS death");
    Thread.sleep(10000); // Give time for WAL recovery

    LOG.info("Starting count after RS death");
    int newCount;
    try (Table table = connection.getTable(TABLE_NAME)) {
      newCount = countRows(table);
    }
    assertEquals("All data should be recovered after RS failure", count, newCount);
    checkpoint("AFTER_VERIFY_RECOVERY");

    // Disable table before deletion (required by HBase)
    admin.disableTable(TABLE_NAME);
    admin.deleteTable(TABLE_NAME);
  }

  /**
   * Test the whole reconstruction loop. Build a table with multiple regions and load data.
   * Kill one of the region servers and scan the table. We should see all the rows due to
   * WAL recovery.
   * Checkpoint: AFTER_KILL_RS
   */
  @Test(timeout = 300000)
  public void testReconstruction_AFTER_KILL_RS() throws Exception {
    upgradeCheckpoint = "AFTER_KILL_RS";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table with multiple regions
    byte[][] splits = new byte[][] {
      Bytes.toBytes("aaa"), Bytes.toBytes("bbb"), Bytes.toBytes("ccc"),
      Bytes.toBytes("ddd"), Bytes.toBytes("eee"), Bytes.toBytes("fff"),
      Bytes.toBytes("ggg"), Bytes.toBytes("hhh"), Bytes.toBytes("iii")
    };
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(TABLE_NAME);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY));
    admin.createTable(tableBuilder.build(), splits);
    checkpoint("AFTER_CREATE_TABLE");

    // Load up the table with simple rows and count them
    int initialCount;
    try (Table table = connection.getTable(TABLE_NAME)) {
      initialCount = loadTable(table);
    }
    checkpoint("AFTER_INITIAL_LOAD");

    int count;
    try (Table table = connection.getTable(TABLE_NAME)) {
      count = countRows(table);
    }
    assertEquals(initialCount, count);

    // Load more data
    for (int i = 0; i < 4; i++) {
      try (Table table = connection.getTable(TABLE_NAME)) {
        loadTable(table);
      }
    }
    checkpoint("AFTER_ADDITIONAL_LOADS");

    // Get one of the region servers to kill
    ServerName rsToKill = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS))
        .getLiveServerMetrics().keySet().iterator().next();
    LOG.info("Killing {}", rsToKill);

    // Kill the region server
    cluster.killRegionServer(rsToKill);
    checkpoint("AFTER_KILL_RS");

    // Wait for the RS to be marked as dead
    admin.getConnection().clearRegionLocationCache();
    Waiter.waitFor(conf, 60000, new ExplainingPredicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.DEAD_SERVERS));
        return metrics.getDeadServerNames().size() >= 1;
      }

      @Override
      public String explainFailure() throws Exception {
        return "Region server " + rsToKill + " is not yet in dead servers list";
      }
    });

    // Wait for regions to be reassigned and WAL recovery to complete
    LOG.info("Waiting for regions to be reassigned after RS death");
    Thread.sleep(10000); // Give time for WAL recovery

    LOG.info("Starting count after RS death");
    int newCount;
    try (Table table = connection.getTable(TABLE_NAME)) {
      newCount = countRows(table);
    }
    assertEquals("All data should be recovered after RS failure", count, newCount);
    checkpoint("AFTER_VERIFY_RECOVERY");

    // Disable table before deletion (required by HBase)
    admin.disableTable(TABLE_NAME);
    admin.deleteTable(TABLE_NAME);
  }

  /**
   * Test the whole reconstruction loop. Build a table with multiple regions and load data.
   * Kill one of the region servers and scan the table. We should see all the rows due to
   * WAL recovery.
   * Checkpoint: AFTER_VERIFY_RECOVERY
   */
  @Test(timeout = 300000)
  public void testReconstruction_AFTER_VERIFY_RECOVERY() throws Exception {
    upgradeCheckpoint = "AFTER_VERIFY_RECOVERY";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table with multiple regions
    byte[][] splits = new byte[][] {
      Bytes.toBytes("aaa"), Bytes.toBytes("bbb"), Bytes.toBytes("ccc"),
      Bytes.toBytes("ddd"), Bytes.toBytes("eee"), Bytes.toBytes("fff"),
      Bytes.toBytes("ggg"), Bytes.toBytes("hhh"), Bytes.toBytes("iii")
    };
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(TABLE_NAME);
    tableBuilder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY));
    admin.createTable(tableBuilder.build(), splits);
    checkpoint("AFTER_CREATE_TABLE");

    // Load up the table with simple rows and count them
    int initialCount;
    try (Table table = connection.getTable(TABLE_NAME)) {
      initialCount = loadTable(table);
    }
    checkpoint("AFTER_INITIAL_LOAD");

    int count;
    try (Table table = connection.getTable(TABLE_NAME)) {
      count = countRows(table);
    }
    assertEquals(initialCount, count);

    // Load more data
    for (int i = 0; i < 4; i++) {
      try (Table table = connection.getTable(TABLE_NAME)) {
        loadTable(table);
      }
    }
    checkpoint("AFTER_ADDITIONAL_LOADS");

    // Get one of the region servers to kill
    ServerName rsToKill = admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS))
        .getLiveServerMetrics().keySet().iterator().next();
    LOG.info("Killing {}", rsToKill);

    // Kill the region server
    cluster.killRegionServer(rsToKill);
    checkpoint("AFTER_KILL_RS");

    // Wait for the RS to be marked as dead
    admin.getConnection().clearRegionLocationCache();
    Waiter.waitFor(conf, 60000, new ExplainingPredicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(Option.DEAD_SERVERS));
        return metrics.getDeadServerNames().size() >= 1;
      }

      @Override
      public String explainFailure() throws Exception {
        return "Region server " + rsToKill + " is not yet in dead servers list";
      }
    });

    // Wait for regions to be reassigned and WAL recovery to complete
    LOG.info("Waiting for regions to be reassigned after RS death");
    Thread.sleep(10000); // Give time for WAL recovery

    LOG.info("Starting count after RS death");
    int newCount;
    try (Table table = connection.getTable(TABLE_NAME)) {
      newCount = countRows(table);
    }
    assertEquals("All data should be recovered after RS failure", count, newCount);
    checkpoint("AFTER_VERIFY_RECOVERY");

    // Disable table before deletion (required by HBase)
    admin.disableTable(TABLE_NAME);
    admin.deleteTable(TABLE_NAME);
  }

  /**
   * Load simple rows into the table.
   * @return number of rows loaded
   */
  private int loadTable(Table table) throws Exception {
    int numRows = 100;
    for (int i = 0; i < numRows; i++) {
      byte[] row = Bytes.toBytes(String.format("row-%05d", i));
      Put put = new Put(row);
      put.addColumn(FAMILY, Bytes.toBytes("col"), Bytes.toBytes("value-" + i));
      table.put(put);
    }
    return numRows;
  }

  /**
   * Count all rows in the table.
   * @return total row count
   */
  private int countRows(Table table) throws Exception {
    int count = 0;
    try (ResultScanner scanner = table.getScanner(new Scan())) {
      for (Result r = scanner.next(); r != null; r = scanner.next()) {
        count++;
      }
    }
    return count;
  }
}
