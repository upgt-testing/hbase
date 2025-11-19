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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.master.ServerManager;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.RegionServerTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.wal.WALFactory;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * ProcessBased version of {@link TestRegionServerCrashDisableWAL}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Testcase for HBASE-20742 - RS crash recovery with WAL disabled.
 *
 * @see TestRegionServerCrashDisableWAL Original test using MiniHBaseCluster
 */
@Category({ RegionServerTests.class, MediumTests.class })
public class TestRegionServerCrashDisableWAL_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestRegionServerCrashDisableWAL_ProcessBased.class);

  private static final TableName TABLE_NAME = TableName.valueOf("test");

  private static final byte[] CF = Bytes.toBytes("cf");

  private static final byte[] CQ = Bytes.toBytes("cq");

  @Test(timeout = 120000)
  public void test_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTest();
  }

  @Test(timeout = 120000)
  public void test_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTest();
  }

  @Test(timeout = 120000)
  public void test_AFTER_TABLE_CREATE() throws Exception {
    upgradeCheckpoint = "AFTER_TABLE_CREATE";
    runTest();
  }

  @Test(timeout = 120000)
  public void test_AFTER_MASTER_STOP() throws Exception {
    upgradeCheckpoint = "AFTER_MASTER_STOP";
    runTest();
  }

  @Test(timeout = 120000)
  public void test_AFTER_RS_CRASH() throws Exception {
    upgradeCheckpoint = "AFTER_RS_CRASH";
    runTest();
  }

  private void runTest() throws Exception {
    conf = HBaseConfiguration.create();
    conf.setInt(ServerManager.WAIT_ON_REGIONSERVERS_MINTOSTART, 1);
    conf.setBoolean(WALFactory.WAL_ENABLED, false);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(2)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create table
    TableDescriptorBuilder tableDescriptorBuilder = TableDescriptorBuilder.newBuilder(TABLE_NAME)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF));
    admin.createTable(tableDescriptorBuilder.build());
    admin.balancerSwitch(false, true);
    checkpoint("AFTER_TABLE_CREATE");

    // Get master ServerName before stopping
    ServerName masterServerName = admin.getClusterMetrics().getMasterName();

    // Stop master
    cluster.stopMaster(masterServerName);
    checkpoint("AFTER_MASTER_STOP");

    // Find a RegionServer hosting regions for the table
    // Since we have 2 RSs and balancer is off, at least one RS should host regions
    ServerName rsToKill = null;
    for (ServerName sn : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
      rsToKill = sn;
      break; // Just take the first one
    }

    // Kill the RegionServer
    if (rsToKill != null) {
      cluster.killRegionServer(rsToKill);
    }
    checkpoint("AFTER_RS_CRASH");

    // Restart master
    cluster.restartMaster(0);

    // Wait for cluster to stabilize - master needs to come up and process SCP
    cluster.waitForActiveAndReadyMaster(60000);

    // Verify table is accessible after RS crash with WAL disabled
    // This verifies SCP can handle crashed server with WAL disabled
    try (Table table = connection.getTable(TABLE_NAME)) {
      table.put(new Put(Bytes.toBytes(1)).addColumn(CF, CQ, Bytes.toBytes(1)));
      assertEquals(1, Bytes.toInt(table.get(new Get(Bytes.toBytes(1))).getValue(CF, CQ)));
    }
  }
}
