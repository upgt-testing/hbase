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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.StartMiniClusterOption;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

/**
 * Test for Regions Recovery Config Manager
 */
@Category({ MasterTests.class, MediumTests.class })
public class TestRegionsRecoveryConfigManager_RestartInjected_Randomized_456 {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestRegionsRecoveryConfigManager_RestartInjected.class);

    private static final HBaseTestingUtility HBASE_TESTING_UTILITY = new HBaseTestingUtility();

    private MiniHBaseCluster cluster;

    private HMaster hMaster;

    private RegionsRecoveryConfigManager regionsRecoveryConfigManager;

    private Configuration conf;

    @Before
    public void setup() throws Exception {
        conf = HBASE_TESTING_UTILITY.getConfiguration();
        conf.unset("hbase.regions.recovery.store.file.ref.count");
        conf.unset("hbase.master.regions.recovery.check.interval");
        StartMiniClusterOption option = StartMiniClusterOption.builder().masterClass(TestHMaster.class).numRegionServers(1).numDataNodes(1).build();
        HBASE_TESTING_UTILITY.startMiniCluster(option);
        cluster = HBASE_TESTING_UTILITY.getMiniHBaseCluster();
    }

    @After
    public void tearDown() throws Exception {
        HBASE_TESTING_UTILITY.shutdownMiniCluster();
    }

    @Test
    public void testChoreSchedule() throws Exception {
        RestartFramework.at("after_chore_untouched_check").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        this.hMaster = cluster.getMaster();
        RestartFramework.at("after_get_master").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_set_check_interval").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        this.regionsRecoveryConfigManager = new RegionsRecoveryConfigManager(this.hMaster);
        // not yet scheduled
        assertFalse(hMaster.getChoreService().isChoreScheduled(regionsRecoveryConfigManager.getChore()));
        RestartFramework.at("after_config_change_same_value").on(cluster).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_create_config_manager").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        this.regionsRecoveryConfigManager.onConfigurationChange(conf);
        // not yet scheduled
        assertFalse(hMaster.getChoreService().isChoreScheduled(regionsRecoveryConfigManager.getChore()));
        RestartFramework.at("after_chore_rescheduled").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_first_chore_check").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        conf.setInt("hbase.master.regions.recovery.check.interval", 10);
        RestartFramework.at("after_set_store_file_ref_count").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        this.regionsRecoveryConfigManager.onConfigurationChange(conf);
        // not yet scheduled - missing config: hbase.regions.recovery.store.file.ref.count
        assertFalse(hMaster.getChoreService().isChoreScheduled(regionsRecoveryConfigManager.getChore()));
        conf.setInt("hbase.regions.recovery.store.file.ref.count", 10);
        this.regionsRecoveryConfigManager.onConfigurationChange(conf);
        // chore scheduled
        assertTrue(hMaster.getChoreService().isChoreScheduled(regionsRecoveryConfigManager.getChore()));
        RestartFramework.at("after_chore_scheduled").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_unset_config").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        conf.setInt("hbase.regions.recovery.store.file.ref.count", 20);
        this.regionsRecoveryConfigManager.onConfigurationChange(conf);
        RestartFramework.at("after_third_chore_check").on(cluster).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // chore re-scheduled
        assertTrue(hMaster.getChoreService().isChoreScheduled(regionsRecoveryConfigManager.getChore()));
        conf.setInt("hbase.regions.recovery.store.file.ref.count", 20);
        RestartFramework.at("after_second_chore_check").on(cluster).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        this.regionsRecoveryConfigManager.onConfigurationChange(conf);
        // chore scheduling untouched
        assertTrue(hMaster.getChoreService().isChoreScheduled(regionsRecoveryConfigManager.getChore()));
        conf.unset("hbase.regions.recovery.store.file.ref.count");
        RestartFramework.at("after_change_store_file_ref_count_to_20").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        this.regionsRecoveryConfigManager.onConfigurationChange(conf);
        RestartFramework.at("after_chore_unscheduled").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_first_config_change").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // chore un-scheduled
        assertFalse(hMaster.getChoreService().isChoreScheduled(regionsRecoveryConfigManager.getChore()));
    }

    // Make it public so that JVMClusterUtil can access it.
    public static class TestHMaster extends HMaster {

        public TestHMaster(Configuration conf) throws IOException {
            super(conf);
        }
    }
}
