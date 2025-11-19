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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.Waiter;
import org.apache.hadoop.hbase.Waiter.Predicate;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.RetriesExhaustedWithDetailsException;
import org.apache.hadoop.hbase.quotas.SpaceQuotaSnapshot.SpaceQuotaStatus;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestQuotaStatusRPCs}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * @see TestQuotaStatusRPCs Original test using MiniHBaseCluster
 */
@Category({ MediumTests.class })
public class TestQuotaStatusRPCs_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestQuotaStatusRPCs_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestQuotaStatusRPCs_ProcessBased.class);
  private static final AtomicLong COUNTER = new AtomicLong(0);

  @Rule
  public TestName testName = new TestName();
  private SpaceQuotaHelperForTests helper;

  @Test
  public void testRegionSizesFromMaster_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testRegionSizesFromMasterImpl();
  }

  @Test
  public void testRegionSizesFromMaster_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testRegionSizesFromMasterImpl();
  }

  @Test
  public void testRegionSizesFromMaster_AFTER_WRITE_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE_DATA";
    testRegionSizesFromMasterImpl();
  }

  private void testRegionSizesFromMasterImpl() throws Exception {
    Configuration testConf = HBaseConfiguration.create(conf);
    SpaceQuotaHelperForTests.updateConfigForQuotas(testConf);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(testConf)
        .numRegionServers(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    helper = new SpaceQuotaHelperForTests(cluster, testName, COUNTER);

    final long tableSize = 1024L * 10L; // 10KB
    final int numRegions = 10;
    final TableName tn = helper.createTableWithRegions(numRegions);
    // Will write at least `tableSize` data
    helper.writeData(tn, tableSize);

    checkpoint("AFTER_WRITE_DATA");

    // TRANSFORMATION NOTE: Removed internal MasterQuotaManager access.
    // Original code waited for quotaManager.snapshotRegionSizes() to have all regions.
    // Replaced with direct client API polling: admin.getSpaceQuotaTableSizes().
    // The client API provides the same information visible to external clients.

    // Wait for master to report table size via client API
    Waiter.waitFor(testConf, 30 * 1000, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        Map<TableName, Long> sizes = admin.getSpaceQuotaTableSizes();
        Long size = sizes.get(tn);
        LOG.trace("Table size from client API: " + size);
        return size != null && size.longValue() >= tableSize;
      }
    });

    Map<TableName, Long> sizes = admin.getSpaceQuotaTableSizes();
    Long size = sizes.get(tn);
    assertNotNull("No reported size for " + tn, size);
    assertTrue("Reported table size was " + size, size.longValue() >= tableSize);
  }

  @Test
  public void testQuotaSnapshotsFromRS_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testQuotaSnapshotsFromRSImpl();
  }

  @Test
  public void testQuotaSnapshotsFromRS_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testQuotaSnapshotsFromRSImpl();
  }

  @Test
  public void testQuotaSnapshotsFromRS_AFTER_WRITE_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE_DATA";
    testQuotaSnapshotsFromRSImpl();
  }

  private void testQuotaSnapshotsFromRSImpl() throws Exception {
    Configuration testConf = HBaseConfiguration.create(conf);
    SpaceQuotaHelperForTests.updateConfigForQuotas(testConf);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(testConf)
        .numRegionServers(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    helper = new SpaceQuotaHelperForTests(cluster, testName, COUNTER);

    final long sizeLimit = 1024L * 1024L; // 1MB
    final long tableSize = 1024L * 10L; // 10KB
    final int numRegions = 10;
    final TableName tn = helper.createTableWithRegions(numRegions);

    // Define the quota
    QuotaSettings settings =
      QuotaSettingsFactory.limitTableSpace(tn, sizeLimit, SpaceViolationPolicy.NO_INSERTS);
    admin.setQuota(settings);

    // Write at least `tableSize` data
    helper.writeData(tn, tableSize);

    checkpoint("AFTER_WRITE_DATA");

    // TRANSFORMATION NOTE: Removed internal RegionServerSpaceQuotaManager access.
    // Original code waited for manager.copyQuotaSnapshots() to have table usage.
    // Replaced with direct client API: admin.getRegionServerSpaceQuotaSnapshots().
    // The client API provides the same snapshot information.

    // Get region server name via ClusterMetrics
    final ServerName rsName = admin.getClusterMetrics().getLiveServerMetrics().keySet().iterator().next();

    // Wait for RS to report snapshot via client API
    Waiter.waitFor(testConf, 30 * 1000, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        @SuppressWarnings("unchecked")
        Map<TableName, SpaceQuotaSnapshot> snapshots =
          (Map<TableName, SpaceQuotaSnapshot>) admin.getRegionServerSpaceQuotaSnapshots(rsName);
        SpaceQuotaSnapshot snapshot = snapshots.get(tn);
        LOG.trace("RS snapshot from client API: " + snapshot);
        if (snapshot == null) {
          return false;
        }
        return snapshot.getUsage() >= tableSize;
      }
    });

    @SuppressWarnings("unchecked")
    Map<TableName, SpaceQuotaSnapshot> snapshots =
      (Map<TableName, SpaceQuotaSnapshot>) admin.getRegionServerSpaceQuotaSnapshots(rsName);
    SpaceQuotaSnapshot snapshot = snapshots.get(tn);
    assertNotNull("Did not find snapshot for " + tn, snapshot);
    assertTrue("Observed table usage was " + snapshot.getUsage(), snapshot.getUsage() >= tableSize);
    assertEquals(sizeLimit, snapshot.getLimit());
    SpaceQuotaStatus pbStatus = snapshot.getQuotaStatus();
    assertFalse(pbStatus.isInViolation());
  }

  @Test
  public void testQuotaEnforcementsFromRS_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testQuotaEnforcementsFromRSImpl();
  }

  @Test
  public void testQuotaEnforcementsFromRS_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testQuotaEnforcementsFromRSImpl();
  }

  @Test
  public void testQuotaEnforcementsFromRS_AFTER_WRITE_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE_DATA";
    testQuotaEnforcementsFromRSImpl();
  }

  private void testQuotaEnforcementsFromRSImpl() throws Exception {
    Configuration testConf = HBaseConfiguration.create(conf);
    SpaceQuotaHelperForTests.updateConfigForQuotas(testConf);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(testConf)
        .numRegionServers(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    helper = new SpaceQuotaHelperForTests(cluster, testName, COUNTER);

    final long sizeLimit = 1024L * 8L; // 8KB
    final long tableSize = 1024L * 10L; // 10KB
    final int numRegions = 10;
    final TableName tn = helper.createTableWithRegions(numRegions);

    // Define the quota
    QuotaSettings settings =
      QuotaSettingsFactory.limitTableSpace(tn, sizeLimit, SpaceViolationPolicy.NO_INSERTS);
    admin.setQuota(settings);

    // Write at least `tableSize` data
    try {
      helper.writeData(tn, tableSize);
    } catch (RetriesExhaustedWithDetailsException | SpaceLimitingException e) {
      // Pass
    }

    checkpoint("AFTER_WRITE_DATA");

    // TRANSFORMATION NOTE: Removed internal RegionServerSpaceQuotaManager.getActiveEnforcements() access.
    // Original code waited for manager.getActiveEnforcements() to show violation.
    // Replaced with direct client API: admin.getRegionServerSpaceQuotaSnapshots().
    // Enforcement status is visible via snapshot.getQuotaStatus().isInViolation().

    // Get region server name via ClusterMetrics
    final ServerName rsName = admin.getClusterMetrics().getLiveServerMetrics().keySet().iterator().next();

    // Wait for enforcement to be active (visible via snapshot violation status)
    Waiter.waitFor(testConf, 30 * 1000, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        @SuppressWarnings("unchecked")
        Map<TableName, SpaceQuotaSnapshot> snapshots =
          (Map<TableName, SpaceQuotaSnapshot>) admin.getRegionServerSpaceQuotaSnapshots(rsName);
        SpaceQuotaSnapshot snapshot = snapshots.get(tn);
        LOG.trace("RS snapshot for enforcement: " + snapshot);
        if (snapshot == null) {
          return false;
        }
        return snapshot.getQuotaStatus().isInViolation();
      }
    });

    // We obtain the violations for a RegionServer by observing the snapshots
    @SuppressWarnings("unchecked")
    Map<TableName, SpaceQuotaSnapshot> snapshots =
      (Map<TableName, SpaceQuotaSnapshot>) admin.getRegionServerSpaceQuotaSnapshots(rsName);
    SpaceQuotaSnapshot snapshot = snapshots.get(tn);
    assertNotNull("Did not find snapshot for " + tn, snapshot);
    assertTrue(snapshot.getQuotaStatus().isInViolation());
    assertEquals(SpaceViolationPolicy.NO_INSERTS, snapshot.getQuotaStatus().getPolicy().get());
  }

  @Test
  public void testQuotaStatusFromMaster_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testQuotaStatusFromMasterImpl();
  }

  @Test
  public void testQuotaStatusFromMaster_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testQuotaStatusFromMasterImpl();
  }

  @Test
  public void testQuotaStatusFromMaster_AFTER_FIRST_WRITE() throws Exception {
    upgradeCheckpoint = "AFTER_FIRST_WRITE";
    testQuotaStatusFromMasterImpl();
  }

  @Test
  public void testQuotaStatusFromMaster_AFTER_SECOND_WRITE() throws Exception {
    upgradeCheckpoint = "AFTER_SECOND_WRITE";
    testQuotaStatusFromMasterImpl();
  }

  private void testQuotaStatusFromMasterImpl() throws Exception {
    Configuration testConf = HBaseConfiguration.create(conf);
    SpaceQuotaHelperForTests.updateConfigForQuotas(testConf);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(testConf)
        .numRegionServers(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    helper = new SpaceQuotaHelperForTests(cluster, testName, COUNTER);

    final long sizeLimit = 1024L * 25L; // 25KB
    final long tableSize = 1024L * 1; // 1KB
    final long nsLimit = Long.MAX_VALUE;
    final int numRegions = 10;
    final TableName tn = helper.createTableWithRegions(numRegions);

    // Define the quota
    QuotaSettings settings =
      QuotaSettingsFactory.limitTableSpace(tn, sizeLimit, SpaceViolationPolicy.NO_INSERTS);
    admin.setQuota(settings);
    QuotaSettings nsSettings = QuotaSettingsFactory.limitNamespaceSpace(tn.getNamespaceAsString(),
      nsLimit, SpaceViolationPolicy.NO_INSERTS);
    admin.setQuota(nsSettings);

    // Write at least `tableSize` data
    helper.writeData(tn, tableSize);

    checkpoint("AFTER_FIRST_WRITE");

    // TRANSFORMATION NOTE: Original test is 100% client-side.
    // All operations use conn.getAdmin().getCurrentSpaceQuotaSnapshot().
    // No transformation needed - kept as-is.

    // Make sure the master has a snapshot for our table
    Waiter.waitFor(testConf, 30 * 1000, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        SpaceQuotaSnapshot snapshot =
          (SpaceQuotaSnapshot) admin.getCurrentSpaceQuotaSnapshot(tn);
        LOG.info("Table snapshot after initial ingest: " + snapshot);
        if (snapshot == null) {
          return false;
        }
        return snapshot.getLimit() == sizeLimit && snapshot.getUsage() > 0L;
      }
    });
    final AtomicReference<Long> nsUsage = new AtomicReference<>();
    // If we saw the table snapshot, we should also see the namespace snapshot
    Waiter.waitFor(testConf, 30 * 1000, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        SpaceQuotaSnapshot snapshot = (SpaceQuotaSnapshot) admin
          .getCurrentSpaceQuotaSnapshot(tn.getNamespaceAsString());
        LOG.debug("Namespace snapshot after initial ingest: " + snapshot);
        if (snapshot == null) {
          return false;
        }
        nsUsage.set(snapshot.getUsage());
        return snapshot.getLimit() == nsLimit && snapshot.getUsage() > 0;
      }
    });

    // Sanity check: the below assertions will fail if we somehow write too much data
    // and force the table to move into violation before we write the second bit of data.
    SpaceQuotaSnapshot snapshot =
      (SpaceQuotaSnapshot) admin.getCurrentSpaceQuotaSnapshot(tn);
    assertTrue("QuotaSnapshot for " + tn + " should be non-null and not in violation",
      snapshot != null && !snapshot.getQuotaStatus().isInViolation());

    try {
      helper.writeData(tn, tableSize * 2L);
    } catch (RetriesExhaustedWithDetailsException | SpaceLimitingException e) {
      // Pass
    }

    checkpoint("AFTER_SECOND_WRITE");

    // Wait for the status to move to violation
    Waiter.waitFor(testConf, 30 * 1000, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        SpaceQuotaSnapshot snapshot =
          (SpaceQuotaSnapshot) admin.getCurrentSpaceQuotaSnapshot(tn);
        LOG.info("Table snapshot after second ingest: " + snapshot);
        if (snapshot == null) {
          return false;
        }
        return snapshot.getQuotaStatus().isInViolation();
      }
    });
    // The namespace should still not be in violation, but have a larger usage than previously
    Waiter.waitFor(testConf, 30 * 1000, new Predicate<Exception>() {
      @Override
      public boolean evaluate() throws Exception {
        SpaceQuotaSnapshot snapshot = (SpaceQuotaSnapshot) admin
          .getCurrentSpaceQuotaSnapshot(tn.getNamespaceAsString());
        LOG.debug("Namespace snapshot after second ingest: " + snapshot);
        if (snapshot == null) {
          return false;
        }
        return snapshot.getUsage() > nsUsage.get() && !snapshot.getQuotaStatus().isInViolation();
      }
    });
  }
}
