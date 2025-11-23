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

import static org.junit.Assert.fail;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ClientServiceCallable;
import org.apache.hadoop.hbase.client.ClusterConnection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RpcRetryingCaller;
import org.apache.hadoop.hbase.client.RpcRetryingCallerFactory;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MediumTests;
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
 * ProcessBased version of {@link TestSpaceQuotaOnBulkLoad}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Reduced version (50% logic preserved): 1 out of 2 test methods transformed.
 * testNoBulkLoadsWithNoWrites fully client-side via SpaceQuotaHelperForTests.
 * testAtomicBulkLoadUnderQuota removed - requires internal RegionServerSpaceQuotaManager
 * access (lines 125-149) including copyQuotaSnapshots(), getActiveEnforcements(),
 * and MasterQuotaManager.snapshotRegionSizes() (lines 186-194).
 *
 * @see TestSpaceQuotaOnBulkLoad Original test using MiniHBaseCluster
 */
@Category(MediumTests.class)
public class TestSpaceQuotaOnBulkLoad_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestSpaceQuotaOnBulkLoad_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestSpaceQuotaOnBulkLoad_ProcessBased.class);

  @Rule
  public TestName testName = new TestName();
  private SpaceQuotaHelperForTests helper;

  @Test
  public void testNoBulkLoadsWithNoWrites_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    SpaceQuotaHelperForTests.updateConfigForQuotas(conf);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    helper = new SpaceQuotaHelperForTests(cluster, testName, new AtomicLong(0));
    helper.removeAllQuotas();

    testNoBulkLoadsWithNoWrites();
  }

  @Test
  public void testNoBulkLoadsWithNoWrites_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    SpaceQuotaHelperForTests.updateConfigForQuotas(conf);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    helper = new SpaceQuotaHelperForTests(cluster, testName, new AtomicLong(0));
    helper.removeAllQuotas();

    testNoBulkLoadsWithNoWrites();
  }

  private void testNoBulkLoadsWithNoWrites() throws Exception {
    Put p = new Put(Bytes.toBytes("to_reject"));
    p.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));
    TableName tableName =
      helper.writeUntilViolationAndVerifyViolation(SpaceViolationPolicy.NO_WRITES, p);

    // The table is now in violation. Try to do a bulk load
    ClientServiceCallable<Void> callable = helper.generateFileToLoad(tableName, 1, 50);
    ClusterConnection conn = (ClusterConnection) connection;
    RpcRetryingCallerFactory factory =
      new RpcRetryingCallerFactory(cluster.getConfiguration(), conn.getConnectionConfiguration());
    RpcRetryingCaller<Void> caller = factory.<Void> newCaller();
    try {
      caller.callWithRetries(callable, Integer.MAX_VALUE);
      fail("Expected the bulk load call to fail!");
    } catch (SpaceLimitingException e) {
      // Pass
      LOG.trace("Caught expected exception", e);
    }
  }
}
