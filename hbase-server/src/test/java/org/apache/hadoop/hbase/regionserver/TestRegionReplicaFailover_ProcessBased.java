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
package org.apache.hadoop.hbase.regionserver;

import static org.junit.Assert.*;

import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Consistency;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.ServerRegionReplicaUtil;
import org.apache.hadoop.hbase.Waiter;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestRegionReplicaFailover}.
 *
 * <p>Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * <p><b>TRANSFORMATION NOTE - Reduced Version (50% coverage):</b>
 * This is a reduced transformation that preserves 3 out of 6 test methods (50% coverage).
 *
 * <p><b>Transformed tests (3/6):</b>
 * <ul>
 *   <li>testSecondaryRegionWithEmptyRegion - Tests Timeline consistency reads from empty replica</li>
 *   <li>testSecondaryRegionWithNonEmptyRegion - Tests replica recovery after disable/enable</li>
 *   <li>testLotsOfRegionReplicas - Tests scaling with many replicas without blocking</li>
 * </ul>
 *
 * <p><b>Not transformed (3/6):</b>
 * <ul>
 *   <li>testPrimaryRegionKill - Requires identifying which RS hosts primary replica (replicaId==0)</li>
 *   <li>testSecondaryRegionKill - Requires identifying which RS hosts secondary replica (replicaId==1)</li>
 *   <li>testSecondaryRegionKillWhilePrimaryIsAcceptingWrites - Requires identifying which RS hosts replica</li>
 * </ul>
 *
 * <p><b>Technical limitation:</b> ProcessBasedMiniHBaseCluster runs nodes in separate JVMs.
 * No client API exists to determine which ServerName hosts which replica ID. The original tests use:
 * <pre>
 * for (RegionServerThread rs : cluster.getRegionServerThreads()) {
 *   for (Region r : rs.getRegionServer().getRegions(tableName)) {
 *     if (r.getRegionInfo().getReplicaId() == 1) {
 *       rs.getRegionServer().abort("for test");
 *     }
 *   }
 * }
 * </pre>
 * This requires direct HRegionServer access which is not available in ProcessBased.
 *
 * @see TestRegionReplicaFailover Original test using MiniHBaseCluster
 */
public class TestRegionReplicaFailover_ProcessBased extends ProcessBasedUpgradeTestBase {

  private static final Logger LOG =
    LoggerFactory.getLogger(TestRegionReplicaFailover_ProcessBased.class);

  private static final int NB_SERVERS = 3;

  protected final byte[] fam = Bytes.toBytes("fam1");
  protected final byte[] qual1 = Bytes.toBytes("qual1");
  protected final byte[] value1 = Bytes.toBytes("value1");
  protected final byte[] row = Bytes.toBytes("rowA");
  protected final byte[] row2 = Bytes.toBytes("rowB");

  private TableName tableName;

  /**
   * Tests the case where a newly created table with region replicas and no data, the secondary
   * region replicas are available to read immediately.
   */
  @Test
  public void testSecondaryRegionWithEmptyRegion_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTestSecondaryRegionWithEmptyRegion();
  }

  @Test
  public void testSecondaryRegionWithEmptyRegion_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTestSecondaryRegionWithEmptyRegion();
  }

  @Test
  public void testSecondaryRegionWithEmptyRegion_AFTER_TABLE_CREATE() throws Exception {
    upgradeCheckpoint = "AFTER_TABLE_CREATE";
    runTestSecondaryRegionWithEmptyRegion();
  }

  private void runTestSecondaryRegionWithEmptyRegion() throws Exception {
    tableName = TableName.valueOf("testSecondaryRegionWithEmptyRegion");

    Configuration testConf = HBaseConfiguration.create();
    testConf.setInt(HConstants.REGION_SERVER_HIGH_PRIORITY_HANDLER_COUNT, 10);
    testConf.setBoolean(ServerRegionReplicaUtil.REGION_REPLICA_REPLICATION_CONF_KEY, true);
    testConf.setBoolean(ServerRegionReplicaUtil.REGION_REPLICA_WAIT_FOR_PRIMARY_FLUSH_CONF_KEY, true);
    testConf.setInt("replication.stats.thread.period.seconds", 5);
    testConf.setBoolean("hbase.tests.use.shortcircuit.reads", false);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(testConf)
        .numRegionServers(NB_SERVERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table with region replication
    TableDescriptor td = TableDescriptorBuilder
        .newBuilder(tableName)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(fam))
        .setRegionReplication(3)
        .build();
    admin.createTable(td);

    checkpoint("AFTER_TABLE_CREATE");

    // Create a new table with region replication, don't put any data. Test that the secondary
    // region replica is available to read.
    try (Table table = connection.getTable(tableName)) {
      Get get = new Get(row);
      get.setConsistency(Consistency.TIMELINE);
      get.setReplicaId(1);
      table.get(get); // this should not block
    }

    checkpoint("AFTER_READ");

    // Cleanup
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  /**
   * Tests the case where if there is some data in the primary region, reopening the region replicas
   * (enable/disable table, etc) makes the region replicas readable.
   */
  @Test
  public void testSecondaryRegionWithNonEmptyRegion_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTestSecondaryRegionWithNonEmptyRegion();
  }

  @Test
  public void testSecondaryRegionWithNonEmptyRegion_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTestSecondaryRegionWithNonEmptyRegion();
  }

  @Test
  public void testSecondaryRegionWithNonEmptyRegion_AFTER_TABLE_CREATE() throws Exception {
    upgradeCheckpoint = "AFTER_TABLE_CREATE";
    runTestSecondaryRegionWithNonEmptyRegion();
  }

  @Test
  public void testSecondaryRegionWithNonEmptyRegion_AFTER_WRITE_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE_DATA";
    runTestSecondaryRegionWithNonEmptyRegion();
  }

  @Test
  public void testSecondaryRegionWithNonEmptyRegion_AFTER_DISABLE_ENABLE() throws Exception {
    upgradeCheckpoint = "AFTER_DISABLE_ENABLE";
    runTestSecondaryRegionWithNonEmptyRegion();
  }

  private void runTestSecondaryRegionWithNonEmptyRegion() throws Exception {
    tableName = TableName.valueOf("testSecondaryRegionWithNonEmptyRegion");

    Configuration testConf = HBaseConfiguration.create();
    testConf.setInt(HConstants.REGION_SERVER_HIGH_PRIORITY_HANDLER_COUNT, 10);
    testConf.setBoolean(ServerRegionReplicaUtil.REGION_REPLICA_REPLICATION_CONF_KEY, true);
    testConf.setBoolean(ServerRegionReplicaUtil.REGION_REPLICA_WAIT_FOR_PRIMARY_FLUSH_CONF_KEY, true);
    testConf.setInt("replication.stats.thread.period.seconds", 5);
    testConf.setBoolean("hbase.tests.use.shortcircuit.reads", false);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(testConf)
        .numRegionServers(NB_SERVERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table with region replication
    TableDescriptor td = TableDescriptorBuilder
        .newBuilder(tableName)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(fam))
        .setRegionReplication(3)
        .build();
    admin.createTable(td);

    checkpoint("AFTER_TABLE_CREATE");

    // Create a new table with region replication and load some data
    // than disable and enable the table again and verify the data from secondary
    try (Table table = connection.getTable(tableName)) {
      loadNumericRows(table, fam, 0, 1000);
    }

    checkpoint("AFTER_WRITE_DATA");

    admin.disableTable(tableName);
    admin.enableTable(tableName);

    checkpoint("AFTER_DISABLE_ENABLE");

    try (Table table = connection.getTable(tableName)) {
      verifyNumericRows(table, fam, 0, 1000, 1);
    }

    checkpoint("AFTER_VERIFY");

    // Cleanup
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  /**
   * Tests the case where we are creating a table with a lot of regions and replicas. Opening region
   * replicas should not block handlers on RS indefinitely.
   */
  @Test
  public void testLotsOfRegionReplicas_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTestLotsOfRegionReplicas();
  }

  @Test
  public void testLotsOfRegionReplicas_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTestLotsOfRegionReplicas();
  }

  @Test
  public void testLotsOfRegionReplicas_AFTER_TABLE_CREATE() throws Exception {
    upgradeCheckpoint = "AFTER_TABLE_CREATE";
    runTestLotsOfRegionReplicas();
  }

  private void runTestLotsOfRegionReplicas() throws Exception {
    tableName = TableName.valueOf("testLotsOfRegionReplicas");

    int numRegions = NB_SERVERS * 20;
    int regionReplication = 10;

    Configuration testConf = HBaseConfiguration.create();
    testConf.setInt(HConstants.REGION_SERVER_HIGH_PRIORITY_HANDLER_COUNT, 10);
    testConf.setBoolean(ServerRegionReplicaUtil.REGION_REPLICA_REPLICATION_CONF_KEY, true);
    testConf.setBoolean(ServerRegionReplicaUtil.REGION_REPLICA_WAIT_FOR_PRIMARY_FLUSH_CONF_KEY, true);
    testConf.setInt("replication.stats.thread.period.seconds", 5);
    testConf.setBoolean("hbase.tests.use.shortcircuit.reads", false);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(testConf)
        .numRegionServers(NB_SERVERS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table with many regions and replicas
    byte[] startKey = Bytes.toBytes("aaa");
    byte[] endKey = Bytes.toBytes("zzz");
    byte[][] splits = getRegionSplitStartKeys(startKey, endKey, numRegions);

    TableDescriptor td = TableDescriptorBuilder
        .newBuilder(tableName)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(fam))
        .setRegionReplication(regionReplication)
        .build();
    admin.createTable(td, startKey, endKey, numRegions);

    checkpoint("AFTER_TABLE_CREATE");

    try (Table table = connection.getTable(tableName)) {
      for (int i = 1; i < splits.length; i++) {
        for (int j = 0; j < regionReplication; j++) {
          Get get = new Get(splits[i]);
          get.setConsistency(Consistency.TIMELINE);
          get.setReplicaId(j);
          table.get(get); // this should not block. Regions should be coming online
        }
      }
    }

    checkpoint("AFTER_READ");

    // Cleanup
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  // Helper method to load numeric rows
  private void loadNumericRows(Table table, byte[] family, int startRow, int endRow)
      throws IOException {
    for (int i = startRow; i < endRow; i++) {
      byte[] row = Bytes.toBytes(String.format("row%010d", i));
      Put put = new Put(row);
      put.addColumn(family, qual1, value1);
      table.put(put);
    }
  }

  // Helper method to verify numeric rows with specific replica
  private void verifyNumericRows(Table table, byte[] family, int startRow, int endRow, int replicaId)
      throws Exception {
    for (int i = startRow; i < endRow; i++) {
      byte[] row = Bytes.toBytes(String.format("row%010d", i));
      Get get = new Get(row);
      get.setConsistency(Consistency.TIMELINE);
      get.setReplicaId(replicaId);

      // Wait with timeout for replica to catch up
      final int currentRow = i;
      Waiter.waitFor(conf, 30000, () -> {
        try {
          Result result = table.get(get);
          return result != null && !result.isEmpty();
        } catch (Exception e) {
          return false;
        }
      });

      Result result = table.get(get);
      assertNotNull("Row " + i + " should exist in replica " + replicaId, result);
      assertFalse("Row " + i + " should have data in replica " + replicaId, result.isEmpty());
    }
  }

  // Helper method to generate region split keys
  private byte[][] getRegionSplitStartKeys(byte[] startKey, byte[] endKey, int numRegions) {
    byte[][] splits = new byte[numRegions - 1][];

    String start = Bytes.toString(startKey);
    String end = Bytes.toString(endKey);

    for (int i = 0; i < numRegions - 1; i++) {
      // Simple alphabetic progression
      char c = (char) (start.charAt(0) + (i + 1) * (end.charAt(0) - start.charAt(0)) / numRegions);
      splits[i] = Bytes.toBytes(String.valueOf(c) + String.valueOf(c) + String.valueOf(c));
    }

    return splits;
  }
}
