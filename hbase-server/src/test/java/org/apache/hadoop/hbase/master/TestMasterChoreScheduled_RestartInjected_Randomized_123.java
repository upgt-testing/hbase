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

import java.lang.reflect.Field;
import java.util.ArrayList;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.ScheduledChore;
import org.apache.hadoop.hbase.StartMiniClusterOption;
import org.apache.hadoop.hbase.master.balancer.BalancerChore;
import org.apache.hadoop.hbase.master.balancer.ClusterStatusChore;
import org.apache.hadoop.hbase.master.cleaner.HFileCleaner;
import org.apache.hadoop.hbase.master.cleaner.LogCleaner;
import org.apache.hadoop.hbase.master.cleaner.ReplicationBarrierCleaner;
import org.apache.hadoop.hbase.master.hbck.HbckChore;
import org.apache.hadoop.hbase.master.janitor.CatalogJanitor;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

/**
 * Tests to validate if HMaster default chores are scheduled
 */
@Category({ MasterTests.class, MediumTests.class })
public class TestMasterChoreScheduled_RestartInjected_Randomized_123 {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestMasterChoreScheduled_RestartInjected.class);

    private static HMaster hMaster;

    private static final HBaseTestingUtility UTIL = new HBaseTestingUtility();

    private static MiniHBaseCluster cluster;

    @BeforeClass
    public static void setUp() throws Exception {
        UTIL.startMiniCluster(StartMiniClusterOption.builder().numRegionServers(1).build());
        cluster = UTIL.getMiniHBaseCluster();
        hMaster = cluster.getMaster();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        UTIL.shutdownMiniCluster();
    }

    @Test
    public void testDefaultScheduledChores() throws Exception {
        // test if logCleaner chore is scheduled by default in HMaster init
        TestChoreField<LogCleaner> logCleanerTestChoreField = new TestChoreField<>();
        RestartFramework.at("after_check_catalog_janitor").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        LogCleaner logCleaner = logCleanerTestChoreField.getChoreObj("logCleaner");
        logCleanerTestChoreField.testIfChoreScheduled(logCleaner);
        // test if hfileCleaner chore is scheduled by default in HMaster init
        TestChoreField<HFileCleaner> hFileCleanerTestChoreField = new TestChoreField<>();
        Field masterField = HMaster.class.getDeclaredField("hfileCleaners");
        masterField.setAccessible(true);
        HFileCleaner hFileCleaner = ((ArrayList<HFileCleaner>) masterField.get(hMaster)).get(0);
        hFileCleanerTestChoreField.testIfChoreScheduled(hFileCleaner);
        // test if replicationBarrierCleaner chore is scheduled by default in HMaster init
        TestChoreField<ReplicationBarrierCleaner> replicationBarrierCleanerTestChoreField = new TestChoreField<>();
        ReplicationBarrierCleaner replicationBarrierCleaner = replicationBarrierCleanerTestChoreField.getChoreObj("replicationBarrierCleaner");
        replicationBarrierCleanerTestChoreField.testIfChoreScheduled(replicationBarrierCleaner);
        // test if clusterStatusChore chore is scheduled by default in HMaster init
        TestChoreField<ClusterStatusChore> clusterStatusChoreTestChoreField = new TestChoreField<>();
        ClusterStatusChore clusterStatusChore = clusterStatusChoreTestChoreField.getChoreObj("clusterStatusChore");
        clusterStatusChoreTestChoreField.testIfChoreScheduled(clusterStatusChore);
        // test if balancerChore chore is scheduled by default in HMaster init
        TestChoreField<BalancerChore> balancerChoreTestChoreField = new TestChoreField<>();
        BalancerChore balancerChore = balancerChoreTestChoreField.getChoreObj("balancerChore");
        balancerChoreTestChoreField.testIfChoreScheduled(balancerChore);
        // test if normalizerChore chore is scheduled by default in HMaster init
        ScheduledChore regionNormalizerChore = hMaster.getRegionNormalizerManager().getRegionNormalizerChore();
        TestChoreField<ScheduledChore> regionNormalizerChoreTestChoreField = new TestChoreField<>();
        regionNormalizerChoreTestChoreField.testIfChoreScheduled(regionNormalizerChore);
        // test if catalogJanitorChore chore is scheduled by default in HMaster init
        TestChoreField<CatalogJanitor> catalogJanitorTestChoreField = new TestChoreField<>();
        CatalogJanitor catalogJanitor = catalogJanitorTestChoreField.getChoreObj("catalogJanitorChore");
        catalogJanitorTestChoreField.testIfChoreScheduled(catalogJanitor);
        // test if hbckChore chore is scheduled by default in HMaster init
        TestChoreField<HbckChore> hbckChoreTestChoreField = new TestChoreField<>();
        RestartFramework.at("after_check_balancer_chore").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        HbckChore hbckChore = hbckChoreTestChoreField.getChoreObj("hbckChore");
        RestartFramework.at("after_check_hfile_cleaner").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        hbckChoreTestChoreField.testIfChoreScheduled(hbckChore);
    }

    /**
     * Reflect into the {@link HMaster} instance and find by field name a specified instance of
     * {@link ScheduledChore}.
     */
    private static class TestChoreField<E extends ScheduledChore> {

        @SuppressWarnings("unchecked")
        private E getChoreObj(String fieldName) {
            try {
                Field masterField = HMaster.class.getDeclaredField(fieldName);
                masterField.setAccessible(true);
                return (E) masterField.get(hMaster);
            } catch (Exception e) {
                throw new AssertionError("Unable to retrieve field '" + fieldName + "' from HMaster instance.", e);
            }
        }

        private void testIfChoreScheduled(E choreObj) {
            Assert.assertNotNull(choreObj);
            Assert.assertTrue(hMaster.getChoreService().isChoreScheduled(choreObj));
        }
    }
}
