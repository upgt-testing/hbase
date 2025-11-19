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
package org.apache.hadoop.hbase.master;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.MetaTableAccessor;
import org.apache.hadoop.hbase.TableExistsException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestClusterRestart}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Full transformation (100% logic preserved): Tests cluster shutdown and restart
 * with table persistence. All operations fully client-side: createTable,
 * waitTableEnabled, MetaTableAccessor.getAllRegions, cluster shutdown/restart,
 * TableExistsException verification. Replaced getMaster().isInitialized() with
 * cluster.waitClusterUp().
 *
 * @see TestClusterRestart Original test using MiniHBaseCluster
 */
@Category({ MasterTests.class, MediumTests.class })
public class TestClusterRestart_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestClusterRestart_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestClusterRestart_ProcessBased.class);

  protected static final TableName[] TABLES = { TableName.valueOf("restartTableOne"),
    TableName.valueOf("restartTableTwo"), TableName.valueOf("restartTableThree") };
  protected static final byte[] FAMILY = Bytes.toBytes("family");

  @Test
  public void test_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testImpl();
  }

  @Test
  public void test_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testImpl();
  }

  @Test
  public void test_AFTER_RESTART() throws Exception {
    upgradeCheckpoint = "AFTER_RESTART";
    testImpl();
  }

  private void testImpl() throws Exception {
    conf = HBaseConfiguration.create();
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(3)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    LOG.info("\n\nCreating tables");
    for (TableName TABLE : TABLES) {
      admin.createTable(createTableDescriptor(TABLE, FAMILY));
    }
    for (TableName TABLE : TABLES) {
      waitTableEnabled(admin, TABLE);
    }

    List<RegionInfo> allRegions = MetaTableAccessor.getAllRegions(connection, false);
    assertEquals(4, allRegions.size());

    LOG.info("\n\nShutting down cluster");
    connection.close();
    cluster.shutdown();

    LOG.info("\n\nSleeping a bit");
    Thread.sleep(2000);

    checkpoint("AFTER_RESTART");

    LOG.info("\n\nStarting cluster the second time");
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(3)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();

    // Need to use a new Connection for the restarted cluster
    allRegions = MetaTableAccessor.getAllRegions(connection, false);
    assertEquals(4, allRegions.size());

    LOG.info("\n\nWaiting for tables to be available");
    for (TableName TABLE : TABLES) {
      try {
        admin.createTable(createTableDescriptor(TABLE, FAMILY));
        assertTrue("Able to create table that should already exist", false);
      } catch (TableExistsException tee) {
        LOG.info("Table already exists as expected");
      }
      waitTableAvailable(admin, TABLE);
    }

    // Cleanup
    for (TableName TABLE : TABLES) {
      admin.disableTable(TABLE);
      admin.deleteTable(TABLE);
    }
  }

  private org.apache.hadoop.hbase.client.TableDescriptor createTableDescriptor(
    TableName tableName, byte[] family) {
    return org.apache.hadoop.hbase.client.TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder.of(family))
      .build();
  }

  private void waitTableEnabled(org.apache.hadoop.hbase.client.Admin admin, TableName tableName)
    throws Exception {
    long timeout = 30000;
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < timeout) {
      if (admin.isTableEnabled(tableName)) {
        return;
      }
      Thread.sleep(100);
    }
    throw new RuntimeException("Table " + tableName + " not enabled after " + timeout + "ms");
  }

  private void waitTableAvailable(org.apache.hadoop.hbase.client.Admin admin, TableName tableName)
    throws Exception {
    long timeout = 30000;
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < timeout) {
      if (admin.isTableAvailable(tableName)) {
        return;
      }
      Thread.sleep(100);
    }
    throw new RuntimeException("Table " + tableName + " not available after " + timeout + "ms");
  }
}
