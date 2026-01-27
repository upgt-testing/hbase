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
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.Waiter;
import org.apache.hadoop.hbase.Waiter.Predicate;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RetriesExhaustedWithDetailsException;
import org.apache.hadoop.hbase.master.HMaster;
import org.apache.hadoop.hbase.quotas.SpaceQuotaSnapshot.SpaceQuotaStatus;
import org.apache.hadoop.hbase.quotas.policies.MissingSnapshotViolationPolicyEnforcement;
import org.apache.hadoop.hbase.regionserver.HRegionServer;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for the quota status RPCs in the master and regionserver.
 */
@Category({ MediumTests.class })
public class TestQuotaStatusRPCs_RestartInjected_Randomized_123 {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestQuotaStatusRPCs_RestartInjected.class);

    private static final Logger LOG = LoggerFactory.getLogger(TestQuotaStatusRPCs_RestartInjected.class);

    private static final HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();

    private static final AtomicLong COUNTER = new AtomicLong(0);

    @Rule
    public TestName testName = new TestName();

    private SpaceQuotaHelperForTests helper;

    @BeforeClass
    public static void setUp() throws Exception {
        Configuration conf = TEST_UTIL.getConfiguration();
        // Increase the frequency of some of the chores for responsiveness of the test
        SpaceQuotaHelperForTests.updateConfigForQuotas(conf);
        TEST_UTIL.startMiniCluster(1);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        TEST_UTIL.shutdownMiniCluster();
    }

    @Before
    public void setupForTest() throws Exception {
        helper = new SpaceQuotaHelperForTests(TEST_UTIL, testName, COUNTER);
    }

    @Test
    public void testRegionSizesFromMaster() throws Exception {
        RestartFramework.at("after_region_sizes_collected").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // 10KB
        final long tableSize = 1024L * 10L;
        final int numRegions = 10;
        final TableName tn = helper.createTableWithRegions(numRegions);
        helper.writeData(tn, tableSize);
        RestartFramework.at("after_table_create").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_write_data").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        final HMaster master = TEST_UTIL.getMiniHBaseCluster().getMaster();
        final MasterQuotaManager quotaManager = master.getMasterQuotaManager();
        Waiter.waitFor(TEST_UTIL.getConfiguration(), 30 * 1000, new Predicate<Exception>() {

            @Override
            public boolean evaluate() throws Exception {
                Map<RegionInfo, Long> regionSizes = quotaManager.snapshotRegionSizes();
                LOG.trace("Region sizes=" + regionSizes);
                return numRegions == countRegionsForTable(tn, regionSizes) && tableSize <= getTableSize(tn, regionSizes);
            }
        });
        Map<TableName, Long> sizes = TEST_UTIL.getAdmin().getSpaceQuotaTableSizes();
        Long size = sizes.get(tn);
        assertNotNull("No reported size for " + tn, size);
        assertTrue("Reported table size was " + size, size.longValue() >= tableSize);
    }

    @Test
    public void testQuotaSnapshotsFromRS() throws Exception {
        // 1MB
        final long sizeLimit = 1024L * 1024L;
        // 10KB
        final long tableSize = 1024L * 10L;
        final int numRegions = 10;
        RestartFramework.at("after_table_create").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        final TableName tn = helper.createTableWithRegions(numRegions);
        QuotaSettings settings = QuotaSettingsFactory.limitTableSpace(tn, sizeLimit, SpaceViolationPolicy.NO_INSERTS);
        TEST_UTIL.getAdmin().setQuota(settings);
        helper.writeData(tn, tableSize);
        final HRegionServer rs = TEST_UTIL.getMiniHBaseCluster().getRegionServer(0);
        final RegionServerSpaceQuotaManager manager = rs.getRegionServerSpaceQuotaManager();
        RestartFramework.at("after_quota_set").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Waiter.waitFor(TEST_UTIL.getConfiguration(), 30 * 1000, new Predicate<Exception>() {

            @Override
            public boolean evaluate() throws Exception {
                SpaceQuotaSnapshot snapshot = manager.copyQuotaSnapshots().get(tn);
                if (snapshot == null) {
                    return false;
                }
                return snapshot.getUsage() >= tableSize;
            }
        });
        RestartFramework.at("after_snapshot_verification").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        @SuppressWarnings("unchecked")
        Map<TableName, SpaceQuotaSnapshot> snapshots = (Map<TableName, SpaceQuotaSnapshot>) TEST_UTIL.getAdmin().getRegionServerSpaceQuotaSnapshots(rs.getServerName());
        SpaceQuotaSnapshot snapshot = snapshots.get(tn);
        assertNotNull("Did not find snapshot for " + tn, snapshot);
        assertTrue("Observed table usage was " + snapshot.getUsage(), snapshot.getUsage() >= tableSize);
        RestartFramework.at("after_write_data").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(sizeLimit, snapshot.getLimit());
        SpaceQuotaStatus pbStatus = snapshot.getQuotaStatus();
        assertFalse(pbStatus.isInViolation());
    }

    @Test
    public void testQuotaEnforcementsFromRS() throws Exception {
        // 8KB
        final long sizeLimit = 1024L * 8L;
        // 10KB
        final long tableSize = 1024L * 10L;
        final int numRegions = 10;
        final TableName tn = helper.createTableWithRegions(numRegions);
        QuotaSettings settings = QuotaSettingsFactory.limitTableSpace(tn, sizeLimit, SpaceViolationPolicy.NO_INSERTS);
        TEST_UTIL.getAdmin().setQuota(settings);
        RestartFramework.at("after_table_create").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        try {
            helper.writeData(tn, tableSize);
        } catch (RetriesExhaustedWithDetailsException | SpaceLimitingException e) {
        }
        final HRegionServer rs = TEST_UTIL.getMiniHBaseCluster().getRegionServer(0);
        final RegionServerSpaceQuotaManager manager = rs.getRegionServerSpaceQuotaManager();
        RestartFramework.at("after_write_data").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Waiter.waitFor(TEST_UTIL.getConfiguration(), 30 * 1000, new Predicate<Exception>() {

            @Override
            public boolean evaluate() throws Exception {
                ActivePolicyEnforcement enforcements = manager.getActiveEnforcements();
                SpaceViolationPolicyEnforcement enforcement = enforcements.getPolicyEnforcement(tn);
                if (enforcement instanceof MissingSnapshotViolationPolicyEnforcement) {
                    return false;
                }
                return enforcement.getQuotaSnapshot().getQuotaStatus().isInViolation();
            }
        });
        @SuppressWarnings("unchecked")
        Map<TableName, SpaceQuotaSnapshot> snapshots = (Map<TableName, SpaceQuotaSnapshot>) TEST_UTIL.getAdmin().getRegionServerSpaceQuotaSnapshots(rs.getServerName());
        SpaceQuotaSnapshot snapshot = snapshots.get(tn);
        RestartFramework.at("after_quota_set").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertNotNull("Did not find snapshot for " + tn, snapshot);
        assertTrue(snapshot.getQuotaStatus().isInViolation());
        RestartFramework.at("after_enforcement_verification").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(SpaceViolationPolicy.NO_INSERTS, snapshot.getQuotaStatus().getPolicy().get());
    }

    @Test
    public void testQuotaStatusFromMaster() throws Exception {
        // 25KB
        final long sizeLimit = 1024L * 25L;
        RestartFramework.at("after_violation_verification").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // 1KB
        final long tableSize = 1024L * 1;
        final long nsLimit = Long.MAX_VALUE;
        final int numRegions = 10;
        final TableName tn = helper.createTableWithRegions(numRegions);
        QuotaSettings settings = QuotaSettingsFactory.limitTableSpace(tn, sizeLimit, SpaceViolationPolicy.NO_INSERTS);
        TEST_UTIL.getAdmin().setQuota(settings);
        QuotaSettings nsSettings = QuotaSettingsFactory.limitNamespaceSpace(tn.getNamespaceAsString(), nsLimit, SpaceViolationPolicy.NO_INSERTS);
        TEST_UTIL.getAdmin().setQuota(nsSettings);
        RestartFramework.at("after_first_write_data").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        helper.writeData(tn, tableSize);
        final Connection conn = TEST_UTIL.getConnection();
        RestartFramework.at("after_first_snapshot_verification").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Waiter.waitFor(TEST_UTIL.getConfiguration(), 30 * 1000, new Predicate<Exception>() {

            @Override
            public boolean evaluate() throws Exception {
                SpaceQuotaSnapshot snapshot = (SpaceQuotaSnapshot) conn.getAdmin().getCurrentSpaceQuotaSnapshot(tn);
                LOG.info("Table snapshot after initial ingest: " + snapshot);
                if (snapshot == null) {
                    return false;
                }
                return snapshot.getLimit() == sizeLimit && snapshot.getUsage() > 0L;
            }
        });
        final AtomicReference<Long> nsUsage = new AtomicReference<>();
        Waiter.waitFor(TEST_UTIL.getConfiguration(), 30 * 1000 * 1000, new Predicate<Exception>() {

            @Override
            public boolean evaluate() throws Exception {
                SpaceQuotaSnapshot snapshot = (SpaceQuotaSnapshot) conn.getAdmin().getCurrentSpaceQuotaSnapshot(tn.getNamespaceAsString());
                LOG.debug("Namespace snapshot after initial ingest: " + snapshot);
                if (snapshot == null) {
                    return false;
                }
                nsUsage.set(snapshot.getUsage());
                return snapshot.getLimit() == nsLimit && snapshot.getUsage() > 0;
            }
        });
        SpaceQuotaSnapshot snapshot = (SpaceQuotaSnapshot) conn.getAdmin().getCurrentSpaceQuotaSnapshot(tn);
        assertTrue("QuotaSnapshot for " + tn + " should be non-null and not in violation", snapshot != null && !snapshot.getQuotaStatus().isInViolation());
        RestartFramework.at("after_table_create").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        try {
            helper.writeData(tn, tableSize * 2L);
        } catch (RetriesExhaustedWithDetailsException | SpaceLimitingException e) {
        }
        RestartFramework.at("after_second_write_data").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Waiter.waitFor(TEST_UTIL.getConfiguration(), 30 * 1000, new Predicate<Exception>() {

            @Override
            public boolean evaluate() throws Exception {
                SpaceQuotaSnapshot snapshot = (SpaceQuotaSnapshot) conn.getAdmin().getCurrentSpaceQuotaSnapshot(tn);
                LOG.info("Table snapshot after second ingest: " + snapshot);
                if (snapshot == null) {
                    return false;
                }
                return snapshot.getQuotaStatus().isInViolation();
            }
        });
        RestartFramework.at("after_quota_set").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Waiter.waitFor(TEST_UTIL.getConfiguration(), 30 * 1000, new Predicate<Exception>() {

            @Override
            public boolean evaluate() throws Exception {
                SpaceQuotaSnapshot snapshot = (SpaceQuotaSnapshot) conn.getAdmin().getCurrentSpaceQuotaSnapshot(tn.getNamespaceAsString());
                LOG.debug("Namespace snapshot after second ingest: " + snapshot);
                if (snapshot == null) {
                    return false;
                }
                return snapshot.getUsage() > nsUsage.get() && !snapshot.getQuotaStatus().isInViolation();
            }
        });
    }

    private int countRegionsForTable(TableName tn, Map<RegionInfo, Long> regionSizes) {
        int size = 0;
        for (RegionInfo regionInfo : regionSizes.keySet()) {
            if (tn.equals(regionInfo.getTable())) {
                size++;
            }
        }
        return size;
    }

    private int getTableSize(TableName tn, Map<RegionInfo, Long> regionSizes) {
        int tableSize = 0;
        for (Entry<RegionInfo, Long> entry : regionSizes.entrySet()) {
            RegionInfo regionInfo = entry.getKey();
            long regionSize = entry.getValue();
            if (tn.equals(regionInfo.getTable())) {
                tableSize += regionSize;
            }
        }
        return tableSize;
    }
}
