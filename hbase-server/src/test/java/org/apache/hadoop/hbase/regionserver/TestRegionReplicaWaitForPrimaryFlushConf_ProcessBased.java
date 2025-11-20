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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
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
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestRegionReplicaWaitForPrimaryFlushConf}.
 *
 * <p>Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * <p><b>TRANSFORMATION NOTE - Reduced Version:</b>
 * This is a reduced transformation that verifies the client-visible behavior of HBASE-26811.
 *
 * <p><b>Original test validation:</b>
 * <ul>
 *   <li>When REGION_REPLICA_WAIT_FOR_PRIMARY_FLUSH_CONF_KEY is false</li>
 *   <li>And setRegionMemStoreReplication is true</li>
 *   <li>Secondary replica should be enabled for read after open (post-HBASE-26811 fix)</li>
 * </ul>
 *
 * <p><b>Original test approach (not transformable):</b>
 * <ul>
 *   <li>Get HRegionServer objects via getMiniHBaseCluster().getRegionServer(i)</li>
 *   <li>Get HRegion list via rs.getRegions(tableName)</li>
 *   <li>Identify replicas via region.getRegionInfo().getReplicaId()</li>
 *   <li>Check executor service: rs.getExecutorService().getExecutorThreadPool(ExecutorType.RS_REGION_REPLICA_FLUSH_OPS)</li>
 *   <li>Check internal region state: region.conf and region.isReadsEnabled()</li>
 * </ul>
 *
 * <p><b>Reduced test approach (client-visible):</b>
 * <ul>
 *   <li>Create table with same configuration (regionReplication=2, regionMemStoreReplication=true)</li>
 *   <li>Write data to primary replica</li>
 *   <li>Attempt to read from secondary replica (replicaId=1)</li>
 *   <li>If read succeeds, secondary replica is enabled for read (HBASE-26811 fix working)</li>
 * </ul>
 *
 * <p><b>What we preserve:</b> The client-visible behavior that secondary replicas with
 * regionMemStoreReplication=true are readable when REGION_REPLICA_WAIT_FOR_PRIMARY_FLUSH is false.
 *
 * <p><b>What we cannot verify:</b> Internal implementation details like executor service
 * configuration and internal HRegion state flags.
 *
 * @see TestRegionReplicaWaitForPrimaryFlushConf Original test using MiniHBaseCluster
 */
public class TestRegionReplicaWaitForPrimaryFlushConf_ProcessBased extends ProcessBasedUpgradeTestBase {

  private static final Logger LOG =
    LoggerFactory.getLogger(TestRegionReplicaWaitForPrimaryFlushConf_ProcessBased.class);

  private static final byte[] FAMILY = Bytes.toBytes("family_test");
  private static final byte[] ROW = Bytes.toBytes("row1");
  private static final byte[] QUALIFIER = Bytes.toBytes("q1");
  private static final byte[] VALUE = Bytes.toBytes("value1");

  /**
   * Tests that secondary replica is enabled for read when:
   * - REGION_REPLICA_WAIT_FOR_PRIMARY_FLUSH_CONF_KEY is false
   * - setRegionMemStoreReplication is true
   *
   * This validates the fix for HBASE-26811.
   */
  @Test
  public void testSecondaryReplicaReadEnabled_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTestSecondaryReplicaReadEnabled();
  }

  @Test
  public void testSecondaryReplicaReadEnabled_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTestSecondaryReplicaReadEnabled();
  }

  @Test
  public void testSecondaryReplicaReadEnabled_AFTER_TABLE_CREATE() throws Exception {
    upgradeCheckpoint = "AFTER_TABLE_CREATE";
    runTestSecondaryReplicaReadEnabled();
  }

  @Test
  public void testSecondaryReplicaReadEnabled_AFTER_WRITE_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE_DATA";
    runTestSecondaryReplicaReadEnabled();
  }

  private void runTestSecondaryReplicaReadEnabled() throws Exception {
    TableName tableName = TableName.valueOf("testSecondaryReplicaReadEnabled");

    Configuration testConf = HBaseConfiguration.create();
    // Key configuration: REGION_REPLICA_REPLICATION_CONF_KEY is true (enables replica replication)
    testConf.setBoolean(ServerRegionReplicaUtil.REGION_REPLICA_REPLICATION_CONF_KEY, true);
    // Key configuration: REGION_REPLICA_WAIT_FOR_PRIMARY_FLUSH_CONF_KEY is false
    // Before HBASE-26811: this would disable secondary replica reads
    // After HBASE-26811: secondary replica should still be enabled for reads
    testConf.setBoolean(ServerRegionReplicaUtil.REGION_REPLICA_WAIT_FOR_PRIMARY_FLUSH_CONF_KEY, false);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(testConf)
        .numRegionServers(2)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table with:
    // - regionReplication=2 (primary + 1 secondary replica)
    // - regionMemStoreReplication=true (replicate writes to replica memstore)
    TableDescriptor td = TableDescriptorBuilder
        .newBuilder(tableName)
        .setRegionReplication(2)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
        .setRegionMemStoreReplication(true)
        .build();
    admin.createTable(td);

    LOG.info("Created table {} with regionReplication=2, regionMemStoreReplication=true", tableName);

    checkpoint("AFTER_TABLE_CREATE");

    // Write data to primary replica
    try (Table table = connection.getTable(tableName)) {
      Put put = new Put(ROW);
      put.addColumn(FAMILY, QUALIFIER, VALUE);
      table.put(put);
      LOG.info("Wrote data to primary replica");
    }

    checkpoint("AFTER_WRITE_DATA");

    // Test: Read from secondary replica (replicaId=1)
    // If HBASE-26811 fix is working, secondary replica should be enabled for read
    // and this read should succeed (eventually, after async replication catches up)
    try (Table table = connection.getTable(tableName)) {
      Get get = new Get(ROW);
      get.setConsistency(Consistency.TIMELINE);
      get.setReplicaId(1); // Read from secondary replica

      // Wait for replication to catch up (async process)
      long startTime = System.currentTimeMillis();
      long timeout = 30000; // 30 seconds timeout
      Result result = null;
      boolean readSuccessful = false;

      while (System.currentTimeMillis() - startTime < timeout) {
        try {
          result = table.get(get);
          if (result != null && !result.isEmpty()) {
            readSuccessful = true;
            break;
          }
        } catch (Exception e) {
          // If secondary replica is not enabled for read, this would throw exception
          // or return empty result
          LOG.debug("Read from secondary replica not yet available: {}", e.getMessage());
        }
        Thread.sleep(1000);
      }

      // HBASE-26811 fix validation:
      // Secondary replica should be enabled for read, so we should eventually read the data
      assertTrue("Secondary replica should be enabled for read (HBASE-26811 fix)",
          readSuccessful);
      assertNotNull("Should read data from secondary replica", result);
      assertTrue("Result should not be empty", !result.isEmpty());

      byte[] readValue = result.getValue(FAMILY, QUALIFIER);
      assertNotNull("Should read the value from secondary replica", readValue);
      assertTrue("Value should match", Bytes.equals(VALUE, readValue));

      LOG.info("Successfully read from secondary replica - HBASE-26811 fix validated");
    }

    checkpoint("AFTER_READ");

    // Cleanup
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }
}
