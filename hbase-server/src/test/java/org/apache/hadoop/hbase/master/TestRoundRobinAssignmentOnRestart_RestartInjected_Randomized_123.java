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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.util.JVMClusterUtil;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Category({ MasterTests.class, MediumTests.class })
public class TestRoundRobinAssignmentOnRestart_RestartInjected_Randomized_123 extends AbstractTestRestartCluster {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestRoundRobinAssignmentOnRestart_RestartInjected.class);

    private static final Logger LOG = LoggerFactory.getLogger(TestRoundRobinAssignmentOnRestart_RestartInjected.class);

    @Override
    protected boolean splitWALCoordinatedByZk() {
        return true;
    }

    private final int regionNum = 10;

    private final int rsNum = 2;

    /**
     * This tests retaining assignments on a cluster restart
     */
    @Test
    public void test() throws Exception {
        UTIL.startMiniCluster(rsNum);
        // Turn off balancer
        UTIL.getMiniHBaseCluster().getMaster().getMasterRpcServices().synchronousBalanceSwitch(false);
        LOG.info("\n\nCreating tables");
        for (TableName TABLE : TABLES) {
            UTIL.createMultiRegionTable(TABLE, FAMILY, regionNum);
        }
        // Wait until all regions are assigned
        for (TableName TABLE : TABLES) {
            UTIL.waitTableEnabled(TABLE);
        }
        UTIL.waitUntilNoRegionsInTransition(60000);
        MiniHBaseCluster cluster = UTIL.getHBaseCluster();
        List<JVMClusterUtil.RegionServerThread> threads = cluster.getLiveRegionServerThreads();
        RestartFramework.at("after_regions_reassigned").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(2, threads.size());
        ServerName testServer = threads.get(0).getRegionServer().getServerName();
        int port = testServer.getPort();
        List<RegionInfo> regionInfos = cluster.getMaster().getAssignmentManager().getRegionsOnServer(testServer);
        LOG.debug("RegionServer {} has {} regions", testServer, regionInfos.size());
        assertTrue(regionInfos.size() >= (TABLES.length * regionNum / rsNum));
        RestartFramework.at("after_get_region_info").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        cluster = UTIL.getHBaseCluster();
        RestartFramework.at("after_balancer_off").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Restart 1 regionserver
        cluster.stopRegionServer(testServer);
        RestartFramework.at("after_table_create").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        cluster.waitForRegionServerToStop(testServer, 60000);
        cluster = UTIL.getHBaseCluster();
        RestartFramework.at("after_rs_restart").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_rs_stop").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        cluster.getConf().setInt(HConstants.REGIONSERVER_PORT, port);
        cluster.startRegionServer();
        cluster = UTIL.getHBaseCluster();
        HMaster master = UTIL.getMiniHBaseCluster().getMaster();
        List<ServerName> localServers = master.getServerManager().getOnlineServersList();
        ServerName newTestServer = null;
        RestartFramework.at("after_regions_assigned").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        for (ServerName serverName : localServers) {
            if (serverName.getAddress().equals(testServer.getAddress())) {
                newTestServer = serverName;
                break;
            }
        }
        assertNotNull(newTestServer);
        RestartFramework.at("after_new_server_found").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        cluster = UTIL.getHBaseCluster();
        master = UTIL.getMiniHBaseCluster().getMaster();
        // Wait until all regions are assigned
        for (TableName TABLE : TABLES) {
            UTIL.waitTableAvailable(TABLE);
        }
        UTIL.waitUntilNoRegionsInTransition(60000);
        cluster = UTIL.getHBaseCluster();
        RestartFramework.at("after_cluster_start").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        List<RegionInfo> newRegionInfos = cluster.getMaster().getAssignmentManager().getRegionsOnServer(newTestServer);
        LOG.debug("RegionServer {} has {} regions", newTestServer, newRegionInfos.size());
        assertTrue("Should not retain all regions when restart", newRegionInfos.size() < regionInfos.size());
    }
}
