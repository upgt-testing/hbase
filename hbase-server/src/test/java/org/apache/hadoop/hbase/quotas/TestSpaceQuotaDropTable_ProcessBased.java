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

import java.util.concurrent.atomic.AtomicLong;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.client.Put;
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
 * ProcessBased version of {@link TestSpaceQuotaDropTable}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Reduced version (80% logic preserved): 4 out of 5 test methods transformed.
 * testSetQuotaAndThenDropTableWithRegionReport removed - requires internal
 * MasterQuotaManager.snapshotRegionSizes() access (lines 104-141 in original).
 * All other tests fully client-side via SpaceQuotaHelperForTests.
 *
 * @see TestSpaceQuotaDropTable Original test using MiniHBaseCluster
 */
@Category(LargeTests.class)
public class TestSpaceQuotaDropTable_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestSpaceQuotaDropTable_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestSpaceQuotaDropTable_ProcessBased.class);

  @Rule
  public TestName testName = new TestName();
  private SpaceQuotaHelperForTests helper;

  @Test
  public void testSetQuotaAndThenDropTableWithNoInserts_NO_UPGRADE() throws Exception {
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

    setQuotaAndThenDropTable(SpaceViolationPolicy.NO_INSERTS);
  }

  @Test
  public void testSetQuotaAndThenDropTableWithNoInserts_AFTER_CLUSTER_START() throws Exception {
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

    setQuotaAndThenDropTable(SpaceViolationPolicy.NO_INSERTS);
  }

  @Test
  public void testSetQuotaAndThenDropTableWithNoWrite_NO_UPGRADE() throws Exception {
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

    setQuotaAndThenDropTable(SpaceViolationPolicy.NO_WRITES);
  }

  @Test
  public void testSetQuotaAndThenDropTableWithNoWrite_AFTER_CLUSTER_START() throws Exception {
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

    setQuotaAndThenDropTable(SpaceViolationPolicy.NO_WRITES);
  }

  @Test
  public void testSetQuotaAndThenDropTableWithNoWritesCompactions_NO_UPGRADE() throws Exception {
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

    setQuotaAndThenDropTable(SpaceViolationPolicy.NO_WRITES_COMPACTIONS);
  }

  @Test
  public void testSetQuotaAndThenDropTableWithNoWritesCompactions_AFTER_CLUSTER_START() throws Exception {
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

    setQuotaAndThenDropTable(SpaceViolationPolicy.NO_WRITES_COMPACTIONS);
  }

  @Test
  public void testSetQuotaAndThenDropTableWithDisable_NO_UPGRADE() throws Exception {
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

    setQuotaAndThenDropTable(SpaceViolationPolicy.DISABLE);
  }

  @Test
  public void testSetQuotaAndThenDropTableWithDisable_AFTER_CLUSTER_START() throws Exception {
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

    setQuotaAndThenDropTable(SpaceViolationPolicy.DISABLE);
  }

  private void setQuotaAndThenDropTable(SpaceViolationPolicy policy) throws Exception {
    Put put = new Put(Bytes.toBytes("to_reject"));
    put.addColumn(Bytes.toBytes(SpaceQuotaHelperForTests.F1), Bytes.toBytes("to"),
      Bytes.toBytes("reject"));

    // Do puts until we violate space policy
    final TableName tn = helper.writeUntilViolationAndVerifyViolation(policy, put);

    // Now, drop the table
    admin.disableTable(tn);
    admin.deleteTable(tn);
    LOG.debug("Successfully deleted table ", tn);

    // Now re-create the table
    org.apache.hadoop.hbase.client.TableDescriptorBuilder builder =
        org.apache.hadoop.hbase.client.TableDescriptorBuilder.newBuilder(tn);
    builder.setColumnFamily(org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder
        .of(Bytes.toBytes(SpaceQuotaHelperForTests.F1)));
    admin.createTable(builder.build());
    LOG.debug("Successfully re-created table ", tn);

    // Put some rows now: should not violate as table/quota was dropped
    helper.verifyNoViolation(tn, put);
  }
}
