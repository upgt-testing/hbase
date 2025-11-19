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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.Waiter;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.ClientTests;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestHTableMultiplexerFlushCache}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Tests HTableMultiplexer behavior during region server failures and region moves.
 * All operations use client APIs (HTableMultiplexer, Admin, RegionLocator).
 *
 * @see TestHTableMultiplexerFlushCache Original test using MiniHBaseCluster
 */
@Category({ LargeTests.class, ClientTests.class })
public class TestHTableMultiplexerFlushCache_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestHTableMultiplexerFlushCache_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestHTableMultiplexerFlushCache_ProcessBased.class);

  private static final byte[] FAMILY = Bytes.toBytes("testFamily");
  private static final byte[] QUALIFIER1 = Bytes.toBytes("testQualifier_1");
  private static final byte[] QUALIFIER2 = Bytes.toBytes("testQualifier_2");
  private static final byte[] VALUE1 = Bytes.toBytes("testValue1");
  private static final byte[] VALUE2 = Bytes.toBytes("testValue2");
  private static final int PER_REGIONSERVER_QUEUE_SIZE = 100000;

  private void checkExistence(final Table htable, final byte[] row, final byte[] family,
    final byte[] quality, final byte[] value) throws Exception {
    // verify that the Get returns the correct result
    Waiter.waitFor(conf, 30000, new Waiter.Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        Result r;
        Get get = new Get(row);
        get.addColumn(family, quality);
        r = htable.get(get);
        return r != null && r.getValue(family, quality) != null
          && Bytes.toStringBinary(value).equals(Bytes.toStringBinary(r.getValue(family, quality)));
      }
    });
  }

  @Test
  public void testOnRegionChange_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    final TableName tableName = TableName.valueOf("testOnRegionChange");
    final int NUM_REGIONS = 10;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Table htable = admin.getConnection().getTableBuilder(tableName, null)
        .setOperationTimeout(60000)
        .build();
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
        .build(),
        Bytes.toBytes("aaaaa"), Bytes.toBytes("zzzzz"), NUM_REGIONS);
    checkpoint("AFTER_CREATE_TABLE");

    HTableMultiplexer multiplexer =
      new HTableMultiplexer(conf, PER_REGIONSERVER_QUEUE_SIZE);

    try (RegionLocator r = connection.getRegionLocator(tableName)) {
      byte[][] startRows = r.getStartKeys();
      byte[] row = startRows[1];
      assertTrue("2nd region should not start with empty row", row != null && row.length > 0);

      Put put = new Put(row).addColumn(FAMILY, QUALIFIER1, VALUE1);
      assertTrue("multiplexer.put returns", multiplexer.put(tableName, put));

      checkExistence(htable, row, FAMILY, QUALIFIER1, VALUE1);
      checkpoint("AFTER_FIRST_PUT");

      // Now let's shutdown the regionserver and let regions moved to other servers.
      HRegionLocation loc = r.getRegionLocation(row);
      ServerName serverToStop = loc.getServerName();

      // Stop the region server using ProcessBased API
      cluster.stopRegionServer(serverToStop);

      // Wait for regions to be reassigned
      Waiter.waitFor(conf, 60000, new Waiter.Predicate<Exception>() {
        @Override
        public boolean evaluate() throws Exception {
          return admin.getRegions(tableName).size() == NUM_REGIONS;
        }
      });
      checkpoint("AFTER_RS_STOP");

      // put with multiplexer.
      put = new Put(row).addColumn(FAMILY, QUALIFIER2, VALUE2);
      assertTrue("multiplexer.put returns", multiplexer.put(tableName, put));

      checkExistence(htable, row, FAMILY, QUALIFIER2, VALUE2);
      checkpoint("AFTER_SECOND_PUT");
    } finally {
      htable.close();
    }

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testOnRegionChange_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testOnRegionChange_NO_UPGRADE();
  }

  @Test
  public void testOnRegionChange_AFTER_FIRST_PUT() throws Exception {
    upgradeCheckpoint = "AFTER_FIRST_PUT";
    testOnRegionChange_NO_UPGRADE();
  }

  @Test
  public void testOnRegionMove_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    // This test is doing near exactly the same thing that testOnRegionChange but avoiding the
    // potential to get a ConnectionClosingException. By moving the region, we can be certain that
    // the connection is still valid and that the implementation is correctly handling an invalid
    // Region cache (and not just tearing down the entire connection).
    final TableName tableName = TableName.valueOf("testOnRegionMove");
    final int NUM_REGIONS = 10;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Table htable = admin.getConnection().getTableBuilder(tableName, null)
        .setOperationTimeout(60000)
        .build();
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
        .build(),
        Bytes.toBytes("aaaaa"), Bytes.toBytes("zzzzz"), NUM_REGIONS);
    checkpoint("AFTER_CREATE_TABLE");

    HTableMultiplexer multiplexer =
      new HTableMultiplexer(conf, PER_REGIONSERVER_QUEUE_SIZE);

    final RegionLocator regionLocator = connection.getRegionLocator(tableName);
    Pair<byte[][], byte[][]> startEndRows = regionLocator.getStartEndKeys();
    byte[] row = startEndRows.getFirst()[1];
    assertTrue("2nd region should not start with empty row", row != null && row.length > 0);

    Put put = new Put(row).addColumn(FAMILY, QUALIFIER1, VALUE1);
    assertTrue("multiplexer.put returns", multiplexer.put(tableName, put));

    checkExistence(htable, row, FAMILY, QUALIFIER1, VALUE1);
    checkpoint("AFTER_FIRST_PUT");

    final HRegionLocation loc = regionLocator.getRegionLocation(row);
    // The current server for the region we're writing to
    final ServerName originalServer = loc.getServerName();
    ServerName newServer = null;

    // Find a new server to move that region to using ClusterMetrics
    ClusterMetrics clusterMetrics = admin.getClusterMetrics();
    Set<ServerName> liveServers = clusterMetrics.getLiveServerMetrics().keySet();
    for (ServerName sn : liveServers) {
      if (!sn.equals(originalServer)) {
        newServer = sn;
        break;
      }
    }
    assertNotNull("Did not find a new RegionServer to use", newServer);

    // Move the region
    LOG.info("Moving " + loc.getRegionInfo().getEncodedName() + " from " + originalServer + " to "
      + newServer);
    admin.move(loc.getRegionInfo().getEncodedNameAsBytes(),
      Bytes.toBytes(newServer.getServerName()));

    // Wait for regions to be reassigned
    Waiter.waitFor(conf, 60000, new Waiter.Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        return admin.getRegions(tableName).size() == NUM_REGIONS;
      }
    });
    checkpoint("AFTER_REGION_MOVE");

    // Send a new Put
    put = new Put(row).addColumn(FAMILY, QUALIFIER2, VALUE2);
    assertTrue("multiplexer.put returns", multiplexer.put(tableName, put));

    // We should see the update make it to the new server eventually
    checkExistence(htable, row, FAMILY, QUALIFIER2, VALUE2);
    checkpoint("AFTER_SECOND_PUT");

    htable.close();
    regionLocator.close();

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testOnRegionMove_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testOnRegionMove_NO_UPGRADE();
  }

  @Test
  public void testOnRegionMove_AFTER_FIRST_PUT() throws Exception {
    upgradeCheckpoint = "AFTER_FIRST_PUT";
    testOnRegionMove_NO_UPGRADE();
  }

  @Test
  public void testOnRegionMove_AFTER_REGION_MOVE() throws Exception {
    upgradeCheckpoint = "AFTER_REGION_MOVE";
    testOnRegionMove_NO_UPGRADE();
  }
}
