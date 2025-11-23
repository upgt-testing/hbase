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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.DoNotRetryIOException;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Append;
import org.apache.hadoop.hbase.client.ClientServiceCallable;
import org.apache.hadoop.hbase.client.ClusterConnection;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.RpcRetryingCaller;
import org.apache.hadoop.hbase.client.RpcRetryingCallerFactory;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.security.AccessDeniedException;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.util.StringUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestSpaceQuotas}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Reduced version (92% logic preserved): 12 out of 13 test methods transformed.
 * All tests fully client-side via SpaceQuotaHelperForTests and Admin API.
 * Skipped testAtomicBulkLoadUnderQuota which requires internal
 * RegionServerSpaceQuotaManager access (copyQuotaSnapshots, getActiveEnforcements
 * lines 269-293) and MasterQuotaManager.snapshotRegionSizes() (lines 354-364).
 *
 * @see TestSpaceQuotas Original test using MiniHBaseCluster
 */
@Category(LargeTests.class)
public class TestSpaceQuotas_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestSpaceQuotas_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestSpaceQuotas_ProcessBased.class);
  private static final AtomicLong COUNTER = new AtomicLong(0);
  private static final int NUM_RETRIES = 10;

  @Rule
  public TestName testName = new TestName();
  private SpaceQuotaHelperForTests helper;

  private void setupCluster() throws Exception {
    SpaceQuotaHelperForTests.updateConfigForQuotas(conf);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    helper = new SpaceQuotaHelperForTests(cluster, testName, COUNTER);

    // Wait for the quota table to be created
    if (!connection.getAdmin().tableExists(QuotaUtil.QUOTA_TABLE_NAME)) {
      helper.waitForQuotaTable(connection);
    } else {
      // Or, clean up any quotas from previous test runs.
      helper.removeAllQuotas(connection);
      assertEquals(0, helper.listNumDefinedQuotas(connection));
    }
  }

  @Test
  public void testNoInsertsWithPut_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_INSERTS, p);
  }

  @Test
  public void testNoInsertsWithPut_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_INSERTS, p);
  }

  @Test
  public void testNoInsertsWithAppend_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Append a = new Append(Bytes.toBytes("to_reject"));
    a.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_INSERTS, a);
  }

  @Test
  public void testNoInsertsWithAppend_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Append a = new Append(Bytes.toBytes("to_reject"));
    a.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_INSERTS, a);
  }

  @Test
  public void testNoInsertsWithIncrement_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Increment i = new Increment(Bytes.toBytes("to_reject"));
    i.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("count"), 0);
    writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_INSERTS, i);
  }

  @Test
  public void testNoInsertsWithIncrement_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Increment i = new Increment(Bytes.toBytes("to_reject"));
    i.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("count"), 0);
    writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_INSERTS, i);
  }

  @Test
  public void testDeletesAfterNoInserts_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tn = writeUntilViolation(SpaceViolationPolicy.NO_INSERTS);
    // Try a couple of times to verify that the quota never gets enforced, same as we
    // do when we're trying to catch the failure.
    Delete d = new Delete(Bytes.toBytes("should_not_be_rejected"));
    for (int i = 0; i < NUM_RETRIES; i++) {
      try (Table t = connection.getTable(tn)) {
        t.delete(d);
      }
    }
  }

  @Test
  public void testDeletesAfterNoInserts_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tn = writeUntilViolation(SpaceViolationPolicy.NO_INSERTS);
    Delete d = new Delete(Bytes.toBytes("should_not_be_rejected"));
    for (int i = 0; i < NUM_RETRIES; i++) {
      try (Table t = connection.getTable(tn)) {
        t.delete(d);
      }
    }
  }

  @Test
  public void testNoWritesWithPut_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, p);
  }

  @Test
  public void testNoWritesWithPut_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, p);
  }

  @Test
  public void testNoWritesWithAppend_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Append a = new Append(Bytes.toBytes("to_reject"));
    a.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, a);
  }

  @Test
  public void testNoWritesWithAppend_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Append a = new Append(Bytes.toBytes("to_reject"));
    a.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, a);
  }

  @Test
  public void testNoWritesWithIncrement_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Increment i = new Increment(Bytes.toBytes("to_reject"));
    i.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("count"), 0);
    writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, i);
  }

  @Test
  public void testNoWritesWithIncrement_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Increment i = new Increment(Bytes.toBytes("to_reject"));
    i.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("count"), 0);
    writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, i);
  }

  @Test
  public void testNoWritesWithDelete_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Delete d = new Delete(Bytes.toBytes("to_reject"));
    writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, d);
  }

  @Test
  public void testNoWritesWithDelete_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Delete d = new Delete(Bytes.toBytes("to_reject"));
    writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, d);
  }

  @Test
  public void testNoCompactions_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    final TableName tn =
      writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES_COMPACTIONS, p);
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
  public void testNoCompactions_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    final TableName tn =
      writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES_COMPACTIONS, p);

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
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    final TableName tn = writeUntilViolation(SpaceViolationPolicy.DISABLE);
    // Disabling a table relies on some external action (over the other policies), so wait a bit
    // more than the other tests.
    for (int i = 0; i < NUM_RETRIES * 2; i++) {
      if (admin.isTableEnabled(tn)) {
        LOG.info(tn + " is still enabled, expecting it to be disabled. Will wait and re-check.");
        Thread.sleep(2000);
      }
    }
    assertFalse(tn + " is still enabled but it should be disabled", admin.isTableEnabled(tn));
    try {
      admin.enableTable(tn);
    } catch (AccessDeniedException e) {
      String exceptionContents = StringUtils.stringifyException(e);
      final String expectedText = "violated space quota";
      assertTrue(
        "Expected the exception to contain " + expectedText + ", but was: " + exceptionContents,
        exceptionContents.contains(expectedText));
    }
  }

  @Test
  public void testNoEnableAfterDisablePolicy_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    final TableName tn = writeUntilViolation(SpaceViolationPolicy.DISABLE);
    for (int i = 0; i < NUM_RETRIES * 2; i++) {
      if (admin.isTableEnabled(tn)) {
        LOG.info(tn + " is still enabled, expecting it to be disabled. Will wait and re-check.");
        Thread.sleep(2000);
      }
    }
    assertFalse(tn + " is still enabled but it should be disabled", admin.isTableEnabled(tn));
    try {
      admin.enableTable(tn);
    } catch (AccessDeniedException e) {
      String exceptionContents = StringUtils.stringifyException(e);
      final String expectedText = "violated space quota";
      assertTrue(
        "Expected the exception to contain " + expectedText + ", but was: " + exceptionContents,
        exceptionContents.contains(expectedText));
    }
  }

  @Test
  public void testNoBulkLoadsWithNoWrites_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    TableName tableName = writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, p);

    // The table is now in violation. Try to do a bulk load
    ClientServiceCallable<Void> callable = helper.generateFileToLoad(tableName, 1, 50);
    ClusterConnection conn = (ClusterConnection) connection;
    RpcRetryingCallerFactory factory =
      new RpcRetryingCallerFactory(cluster.getConfiguration(), conn.getConnectionConfiguration());
    RpcRetryingCaller<Void> caller = factory.newCaller();
    try {
      caller.callWithRetries(callable, Integer.MAX_VALUE);
      fail("Expected the bulk load call to fail!");
    } catch (SpaceLimitingException e) {
      // Pass
      LOG.trace("Caught expected exception", e);
    }
  }

  @Test
  public void testNoBulkLoadsWithNoWrites_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    TableName tableName = writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, p);

    ClientServiceCallable<Void> callable = helper.generateFileToLoad(tableName, 1, 50);
    ClusterConnection conn = (ClusterConnection) connection;
    RpcRetryingCallerFactory factory =
      new RpcRetryingCallerFactory(cluster.getConfiguration(), conn.getConnectionConfiguration());
    RpcRetryingCaller<Void> caller = factory.newCaller();
    try {
      caller.callWithRetries(callable, Integer.MAX_VALUE);
      fail("Expected the bulk load call to fail!");
    } catch (SpaceLimitingException e) {
      // Pass
      LOG.trace("Caught expected exception", e);
    }
  }

  @Test
  public void testTableQuotaOverridesNamespaceQuota_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final SpaceViolationPolicy policy = SpaceViolationPolicy.NO_INSERTS;
    final TableName tn = helper.createTableWithRegions(10);

    // 2MB limit on the table, 1GB limit on the namespace
    final long tableLimit = 2L * SpaceQuotaHelperForTests.ONE_MEGABYTE;
    final long namespaceLimit = 1024L * SpaceQuotaHelperForTests.ONE_MEGABYTE;
    admin.setQuota(QuotaSettingsFactory.limitTableSpace(tn, tableLimit, policy));
    admin.setQuota(
      QuotaSettingsFactory.limitNamespaceSpace(tn.getNamespaceAsString(), namespaceLimit, policy));

    // Write more data than should be allowed and flush it to disk
    helper.writeData(tn, 3L * SpaceQuotaHelperForTests.ONE_MEGABYTE);

    // This should be sufficient time for the chores to run and see the change.
    Thread.sleep(5000);

    // The write should be rejected because the table quota takes priority over the namespace
    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    verifyViolation(policy, tn, p);
  }

  @Test
  public void testTableQuotaOverridesNamespaceQuota_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    setupCluster();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final SpaceViolationPolicy policy = SpaceViolationPolicy.NO_INSERTS;
    final TableName tn = helper.createTableWithRegions(10);

    final long tableLimit = 2L * SpaceQuotaHelperForTests.ONE_MEGABYTE;
    final long namespaceLimit = 1024L * SpaceQuotaHelperForTests.ONE_MEGABYTE;
    admin.setQuota(QuotaSettingsFactory.limitTableSpace(tn, tableLimit, policy));
    admin.setQuota(
      QuotaSettingsFactory.limitNamespaceSpace(tn.getNamespaceAsString(), namespaceLimit, policy));

    helper.writeData(tn, 3L * SpaceQuotaHelperForTests.ONE_MEGABYTE);
    Thread.sleep(5000);

    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    verifyViolation(policy, tn, p);
  }

  private TableName writeUntilViolation(SpaceViolationPolicy policyToViolate) throws Exception {
    TableName tn = helper.createTableWithRegions(10);

    final long sizeLimit = 2L * SpaceQuotaHelperForTests.ONE_MEGABYTE;
    QuotaSettings settings = QuotaSettingsFactory.limitTableSpace(tn, sizeLimit, policyToViolate);
    admin.setQuota(settings);

    // Write more data than should be allowed and flush it to disk
    helper.writeData(tn, 3L * SpaceQuotaHelperForTests.ONE_MEGABYTE);

    // This should be sufficient time for the chores to run and see the change.
    Thread.sleep(5000);

    return tn;
  }

  private TableName writeUntilViolationAndVerifyViolation(SpaceViolationPolicy policyToViolate,
    Mutation m) throws Exception {
    final TableName tn = writeUntilViolation(policyToViolate);
    verifyViolation(policyToViolate, tn, m);
    return tn;
  }

  private void verifyViolation(SpaceViolationPolicy policyToViolate, TableName tn, Mutation m)
    throws Exception {
    // But let's try a few times to get the exception before failing
    boolean sawError = false;
    for (int i = 0; i < NUM_RETRIES && !sawError; i++) {
      try (Table table = connection.getTable(tn)) {
        if (m instanceof Put) {
          table.put((Put) m);
        } else if (m instanceof Delete) {
          table.delete((Delete) m);
        } else if (m instanceof Append) {
          table.append((Append) m);
        } else if (m instanceof Increment) {
          table.increment((Increment) m);
        } else {
          fail(
            "Failed to apply " + m.getClass().getSimpleName() + " to the table. Programming error");
        }
        LOG.info("Did not reject the " + m.getClass().getSimpleName() + ", will sleep and retry");
        Thread.sleep(2000);
      } catch (Exception e) {
        String msg = StringUtils.stringifyException(e);
        assertTrue("Expected exception message to contain the word '" + policyToViolate.name()
          + "', but was " + msg, msg.contains(policyToViolate.name()));
        sawError = true;
      }
    }
    if (!sawError) {
      try (Table quotaTable = connection.getTable(QuotaUtil.QUOTA_TABLE_NAME)) {
        ResultScanner scanner = quotaTable.getScanner(new Scan());
        Result result = null;
        LOG.info("Dumping contents of hbase:quota table");
        while ((result = scanner.next()) != null) {
          LOG.info(Bytes.toString(result.getRow()) + " => " + result.toString());
        }
        scanner.close();
      }
    }
    assertTrue("Expected to see an exception writing data to a table exceeding its quota",
      sawError);
  }
}
