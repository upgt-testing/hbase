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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.net.Socket;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.testclassification.ClientTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.util.FutureUtils;
import org.apache.hadoop.hbase.util.JVMClusterUtil.MasterThread;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

/**
 * Testcase for HBASE-29214
 */
@RunWith(Parameterized.class)
@Category({ ClientTests.class, MediumTests.class })
public class TestAsyncAdminClearMasterStubCache_RestartInjected_Randomized_456 extends TestAsyncAdminBase {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestAsyncAdminClearMasterStubCache_RestartInjected.class);

    @Before
    public void waitMasterReady() throws Exception {
        assertTrue(TEST_UTIL.getHBaseCluster().waitForActiveAndReadyMaster(30000));
    }

    @After
    public void clearPortConfig() {
        TEST_UTIL.getHBaseCluster().getConf().setInt(HConstants.MASTER_PORT, 0);
    }

    @Test
    public void testClearMasterStubCache() throws Exception {
        // cache master stub
        assertNotNull(FutureUtils.get(admin.getClusterMetrics()));
        MiniHBaseCluster cluster = TEST_UTIL.getHBaseCluster();
        cluster = TEST_UTIL.getHBaseCluster();
        RestartFramework.at("after_master_restart_same_port").on(cluster).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // stop the active master
        MasterThread mt = cluster.getMasterThread();
        ServerName sn = mt.getMaster().getServerName();
        mt.getMaster().abort("for testing");
        RestartFramework.at("after_socket_ready").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        mt.join();
        RestartFramework.at("after_new_master_active").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        cluster = TEST_UTIL.getHBaseCluster();
        RestartFramework.at("after_cache_master_stub").on(cluster).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // wait for new active master
        assertTrue(TEST_UTIL.getHBaseCluster().waitForActiveAndReadyMaster(30000));
        RestartFramework.at("after_master_abort").on(cluster).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        cluster = TEST_UTIL.getHBaseCluster();
        // restart master on the same port, this is important for getting a RemoteException
        cluster.getConf().setInt(HConstants.MASTER_PORT, sn.getPort());
        cluster.startMaster();
        cluster = TEST_UTIL.getHBaseCluster();
        // make sure the master is up so we will not get a connect exception
        TEST_UTIL.waitFor(30000, () -> {
            try (Socket socket = new Socket(sn.getHostname(), sn.getPort())) {
                return true;
            } catch (IOException e) {
                return false;
            }
        });
        cluster = TEST_UTIL.getHBaseCluster();
        // we should switch to the new active master
        assertNotNull(FutureUtils.get(admin.getClusterMetrics()));
    }
}
