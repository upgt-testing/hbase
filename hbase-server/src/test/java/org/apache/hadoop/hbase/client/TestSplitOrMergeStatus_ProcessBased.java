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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.hadoop.hbase.DoNotRetryIOException;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.ClientTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Threads;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * ProcessBased version of {@link TestSplitOrMergeStatus}.
 *
 * Transformed from MiniHBaseCluster/HBaseTestingUtility to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * <p>Reduced version (75% logic preserved): 3 test methods transformed (testSplitSwitch,
 * testMergeSwitch, testMultiSwitches), all pure client-side Admin API testing of split/merge
 * switches. testSplitRegionReplicaRitRecovery removed (requires region replicas, direct
 * ProcedureExecutor access, AssignmentManager access - lines 178-212 in original). Each test
 * method has 2 checkpoint variants (NO_UPGRADE, AFTER_CLUSTER_START).
 *
 * @see TestSplitOrMergeStatus Original test using MiniHBaseCluster
 */
@Category({ MediumTests.class, ClientTests.class })
public class TestSplitOrMergeStatus_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestSplitOrMergeStatus_ProcessBased.class);

  private static byte[] FAMILY = Bytes.toBytes("testFamily");

  @Test
  public void testSplitSwitch_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testSplitSwitchImpl();
  }

  @Test
  public void testSplitSwitch_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testSplitSwitchImpl();
  }

  private void testSplitSwitchImpl() throws Exception {
    conf = HBaseConfiguration.create();
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(2).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf("testSplitSwitch");
    Table t = createTable(tableName, FAMILY);
    loadTable(t, FAMILY, false);

    RegionLocator locator = connection.getRegionLocator(t.getName());
    int originalCount = locator.getAllRegionLocations().size();

    initSwitchStatus(admin);
    assertTrue(admin.splitSwitch(false, false));
    try {
      admin.split(t.getName());
      fail("Shouldn't get here");
    } catch (DoNotRetryIOException dnioe) {
      // Expected
    }
    int count = admin.getTableRegions(tableName).size();
    assertTrue(originalCount == count);
    assertFalse(admin.splitSwitch(true, false));
    admin.split(t.getName());
    while ((count = admin.getTableRegions(tableName).size()) == originalCount) {
      Threads.sleep(1);
      ;
    }
    count = admin.getTableRegions(tableName).size();
    assertTrue(originalCount < count);

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Ignore("Ignored in original test - kept for reference")
  @Test
  public void testMergeSwitch_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testMergeSwitchImpl();
  }

  @Ignore("Ignored in original test - kept for reference")
  @Test
  public void testMergeSwitch_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testMergeSwitchImpl();
  }

  private void testMergeSwitchImpl() throws Exception {
    conf = HBaseConfiguration.create();
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(2).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf("testMergeSwitch");
    Table t = createTable(tableName, FAMILY);
    loadTable(t, FAMILY, false);

    int originalCount = admin.getTableRegions(tableName).size();
    initSwitchStatus(admin);
    admin.split(t.getName());
    int postSplitCount = -1;
    while ((postSplitCount = admin.getTableRegions(tableName).size()) == originalCount) {
      Threads.sleep(1);
      ;
    }
    assertTrue("originalCount=" + originalCount + ", newCount=" + postSplitCount,
      originalCount != postSplitCount);

    // Merge switch is off so merge should NOT succeed.
    boolean[] results = admin.setSplitOrMergeEnabled(false, false, MasterSwitchType.MERGE);
    assertEquals(1, results.length);
    assertTrue(results[0]);
    List<HRegionInfo> regions = admin.getTableRegions(t.getName());
    assertTrue(regions.size() > 1);
    Future<?> f = admin.mergeRegionsAsync(regions.get(0).getEncodedNameAsBytes(),
      regions.get(1).getEncodedNameAsBytes(), true);
    try {
      f.get(10, TimeUnit.SECONDS);
      fail("Should not get here.");
    } catch (ExecutionException ee) {
      // Expected.
    }
    int count = admin.getTableRegions(tableName).size();
    assertTrue("newCount=" + postSplitCount + ", count=" + count, postSplitCount == count);

    results = admin.setSplitOrMergeEnabled(true, false, MasterSwitchType.MERGE);
    regions = admin.getTableRegions(t.getName());
    assertEquals(1, results.length);
    assertFalse(results[0]);
    f = admin.mergeRegionsAsync(regions.get(0).getEncodedNameAsBytes(),
      regions.get(1).getEncodedNameAsBytes(), true);
    f.get(10, TimeUnit.SECONDS);
    count = admin.getTableRegions(tableName).size();
    assertTrue((postSplitCount / 2 /* Merge */) == count);

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testMultiSwitches_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testMultiSwitchesImpl();
  }

  @Test
  public void testMultiSwitches_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testMultiSwitchesImpl();
  }

  private void testMultiSwitchesImpl() throws Exception {
    conf = HBaseConfiguration.create();
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(2).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    boolean[] switches =
      admin.setSplitOrMergeEnabled(false, false, MasterSwitchType.SPLIT, MasterSwitchType.MERGE);
    for (boolean s : switches) {
      assertTrue(s);
    }
    assertFalse(admin.isSplitOrMergeEnabled(MasterSwitchType.SPLIT));
    assertFalse(admin.isSplitOrMergeEnabled(MasterSwitchType.MERGE));
  }

  // TRANSFORMATION NOTE: testSplitRegionReplicaRitRecovery removed.
  // Requires: (1) Region replicas via setRegionReplication(2) not supported by ProcessBased (line
  // 184), (2) Direct ProcedureExecutor access via getMasterProcedureExecutor() (lines 182, 215),
  // (3) Direct procedure submission (lines 193-206), (4) RegionReplicaTestHelper internal access
  // (line 187), (5) AssignmentTestingUtil.killRs() internal access (line 207), (6) Direct
  // AssignmentManager access via getMiniHBaseCluster().getMaster().getAssignmentManager() (lines
  // 209-210).
  // No client API exists to submit custom procedures or verify region-in-transition state during
  // replica recovery.

  // Helper methods

  private Table createTable(TableName tableName, byte[] family) throws IOException {
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tableName);
    builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(family));
    admin.createTable(builder.build());
    return connection.getTable(tableName);
  }

  private void loadTable(Table table, byte[] family, boolean putToFlush) throws IOException {
    for (int i = 0; i < 100; i++) {
      Put put = new Put(Bytes.toBytes("row" + i));
      put.addColumn(family, Bytes.toBytes("q1"), Bytes.toBytes("value" + i));
      table.put(put);
    }
    if (putToFlush) {
      admin.flush(table.getName());
    }
  }

  private void initSwitchStatus(Admin admin) throws IOException {
    if (!admin.isSplitEnabled()) {
      admin.splitSwitch(true, false);
    }
    if (!admin.isMergeEnabled()) {
      admin.mergeSwitch(true, false);
    }
    assertTrue(admin.isSplitEnabled());
    assertTrue(admin.isMergeEnabled());
  }
}
