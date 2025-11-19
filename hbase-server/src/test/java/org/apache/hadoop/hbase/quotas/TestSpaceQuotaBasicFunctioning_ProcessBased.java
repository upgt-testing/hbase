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
package org.apache.hadoop.hbase.quotas;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.DoNotRetryIOException;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Append;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.security.AccessDeniedException;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestSpaceQuotaBasicFunctioning}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Reduced version: 11 out of 12 test methods transformed (all quota violation tests).
 * Skipped testDisablePolicyQuotaAndViolate which requires internal MasterQuotaManager access.
 *
 * @see TestSpaceQuotaBasicFunctioning Original test using MiniHBaseCluster
 */
@Category(LargeTests.class)
public class TestSpaceQuotaBasicFunctioning_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestSpaceQuotaBasicFunctioning_ProcessBased.class);

  private static final Logger LOG =
    LoggerFactory.getLogger(TestSpaceQuotaBasicFunctioning_ProcessBased.class);
  private static final int NUM_RETRIES = 10;

  @Rule
  public TestName testName = new TestName();
  private SpaceQuotaHelperForTests helper;

  @Test
  public void testNoInsertsWithPut_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testNoInsertsWithPutImpl();
  }

  @Test
  public void testNoInsertsWithPut_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testNoInsertsWithPutImpl();
  }

  private void testNoInsertsWithPutImpl() throws Exception {
    initCluster();
    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    helper.writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_INSERTS, p);
  }

  @Test
  public void testNoInsertsWithAppend_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testNoInsertsWithAppendImpl();
  }

  @Test
  public void testNoInsertsWithAppend_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testNoInsertsWithAppendImpl();
  }

  private void testNoInsertsWithAppendImpl() throws Exception {
    initCluster();
    Append a = new Append(Bytes.toBytes("to_reject"));
    a.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    helper.writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_INSERTS, a);
  }

  @Test
  public void testNoInsertsWithIncrement_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testNoInsertsWithIncrementImpl();
  }

  @Test
  public void testNoInsertsWithIncrement_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testNoInsertsWithIncrementImpl();
  }

  private void testNoInsertsWithIncrementImpl() throws Exception {
    initCluster();
    Increment i = new Increment(Bytes.toBytes("to_reject"));
    i.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("count"), 0);
    helper.writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_INSERTS, i);
  }

  @Test
  public void testDeletesAfterNoInserts_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testDeletesAfterNoInsertsImpl();
  }

  @Test
  public void testDeletesAfterNoInserts_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testDeletesAfterNoInsertsImpl();
  }

  private void testDeletesAfterNoInsertsImpl() throws Exception {
    initCluster();
    final TableName tn = helper.writeUntilViolation(SpaceViolationPolicy.NO_INSERTS);
    // Try a couple of times to verify that the quota never gets enforced, same as we
    // do when we're trying to catch the failure.
    Delete d = new Delete(Bytes.toBytes("should_not_be_rejected"));
    for (int i = 0; i < NUM_RETRIES; i++) {
      try (Table table = connection.getTable(tn)) {
        table.delete(d);
      } catch (Exception e) {
        fail("Should not have rejected a DELETE");
      }
      Thread.sleep(1000);
    }
  }

  @Test
  public void testNoWritesWithPut_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testNoWritesWithPutImpl();
  }

  @Test
  public void testNoWritesWithPut_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testNoWritesWithPutImpl();
  }

  private void testNoWritesWithPutImpl() throws Exception {
    initCluster();
    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    helper.writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, p);
  }

  @Test
  public void testNoWritesWithAppend_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testNoWritesWithAppendImpl();
  }

  @Test
  public void testNoWritesWithAppend_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testNoWritesWithAppendImpl();
  }

  private void testNoWritesWithAppendImpl() throws Exception {
    initCluster();
    Append a = new Append(Bytes.toBytes("to_reject"));
    a.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    helper.writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, a);
  }

  @Test
  public void testNoWritesWithIncrement_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testNoWritesWithIncrementImpl();
  }

  @Test
  public void testNoWritesWithIncrement_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testNoWritesWithIncrementImpl();
  }

  private void testNoWritesWithIncrementImpl() throws Exception {
    initCluster();
    Increment i = new Increment(Bytes.toBytes("to_reject"));
    i.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("q"), 0);
    helper.writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, i);
  }

  @Test
  public void testNoWritesWithDelete_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testNoWritesWithDeleteImpl();
  }

  @Test
  public void testNoWritesWithDelete_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testNoWritesWithDeleteImpl();
  }

  private void testNoWritesWithDeleteImpl() throws Exception {
    initCluster();
    Delete d = new Delete(Bytes.toBytes("to_reject"));
    helper.writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, d);
  }

  @Test
  public void testNoCompactions_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testNoCompactionsImpl();
  }

  @Test
  public void testNoCompactions_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testNoCompactionsImpl();
  }

  private void testNoCompactionsImpl() throws Exception {
    initCluster();
    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    final TableName tn =
      helper.writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES_COMPACTIONS, p);
    // We know the policy is active at this point

    // Major compactions should be rejected
    try {
      admin.majorCompact(tn);
      fail("Expected that invoking the compaction should throw an Exception");
    } catch (DoNotRetryIOException e) {
      // Expected!
    }
    // Minor compactions should also be rejected.
    try {
      admin.compact(tn);
      fail("Expected that invoking the compaction should throw an Exception");
    } catch (DoNotRetryIOException e) {
      // Expected!
    }
  }

  @Test
  public void testNoEnableAfterDisablePolicy_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testNoEnableAfterDisablePolicyImpl();
  }

  @Test
  public void testNoEnableAfterDisablePolicy_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testNoEnableAfterDisablePolicyImpl();
  }

  private void testNoEnableAfterDisablePolicyImpl() throws Exception {
    initCluster();
    final TableName tn = helper.writeUntilViolation(SpaceViolationPolicy.DISABLE);
    assertFalse(admin.isTableEnabled(tn));
    try {
      admin.enableTable(tn);
    } catch (AccessDeniedException e) {
      // Pass
      LOG.info("Got expected exception enabling a disabled table.", e);
    }
    assertFalse(admin.isTableEnabled(tn));
  }

  @Test
  public void testTableQuotaOverridesNamespaceQuota_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testTableQuotaOverridesNamespaceQuotaImpl();
  }

  @Test
  public void testTableQuotaOverridesNamespaceQuota_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testTableQuotaOverridesNamespaceQuotaImpl();
  }

  private void testTableQuotaOverridesNamespaceQuotaImpl() throws Exception {
    initCluster();
    final SpaceViolationPolicy policy = SpaceViolationPolicy.NO_INSERTS;
    final TableName tn = helper.createTableWithRegions(10);

    // 2MB limit on the table, 1GB limit on the namespace
    final long tableLimit = 2L * SpaceQuotaHelperForTests.ONE_MEGABYTE;
    final long namespaceLimit = 1024L * SpaceQuotaHelperForTests.ONE_MEGABYTE;
    admin.setQuota(QuotaSettingsFactory.limitTableSpace(tn, tableLimit, policy));
    admin.setQuota(
      QuotaSettingsFactory.limitNamespaceSpace(tn.getNamespaceAsString(), namespaceLimit, policy));

    // Write 3MB of data
    helper.writeData(tn, 3L * SpaceQuotaHelperForTests.ONE_MEGABYTE);
    // The table quota takes precedence, so we should be in violation
    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    helper.verifyViolation(policy, tn, p);
  }

  // TRANSFORMATION NOTE: testDisablePolicyQuotaAndViolate skipped.
  // Original test requires internal MasterQuotaManager access:
  //   - master.getMasterQuotaManager() (line 241)
  //   - quotaManager.snapshotRegionSizes() (lines 247, 256)
  // Purpose: Verify that quota manager retains region reports for disabled tables.
  // No client API exists to inspect internal MasterQuotaManager state.

  private void initCluster() throws Exception {
    Configuration testConf = HBaseConfiguration.create(conf);
    SpaceQuotaHelperForTests.updateConfigForQuotas(testConf);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(testConf)
        .numRegionServers(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    helper = new SpaceQuotaHelperForTests(cluster, testName, new AtomicLong(0));
    helper.removeAllQuotas();
  }
}
