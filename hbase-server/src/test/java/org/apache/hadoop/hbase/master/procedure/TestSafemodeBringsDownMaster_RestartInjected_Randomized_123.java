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
package org.apache.hadoop.hbase.master.procedure;

import static org.junit.Assert.assertTrue;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.Waiter;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.master.balancer.BaseLoadBalancer;
import org.apache.hadoop.hbase.procedure2.ProcedureExecutor;
import org.apache.hadoop.hbase.procedure2.ProcedureTestingUtility;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.hadoop.hbase.util.JVMClusterUtil;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.protocol.HdfsConstants;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Category(MediumTests.class)
public class TestSafemodeBringsDownMaster_RestartInjected_Randomized_123 {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestSafemodeBringsDownMaster_RestartInjected.class);

    private static final Logger LOG = LoggerFactory.getLogger(TestSafemodeBringsDownMaster_RestartInjected.class);

    protected static final HBaseTestingUtility UTIL = new HBaseTestingUtility();

    private static void setupConf(Configuration conf) {
        conf.setInt(MasterProcedureConstants.MASTER_PROCEDURE_THREADS, 1);
        conf.set(BaseLoadBalancer.TABLES_ON_MASTER, "none");
    }

    @BeforeClass
    public static void setupCluster() throws Exception {
        setupConf(UTIL.getConfiguration());
        UTIL.startMiniCluster(1);
    }

    @AfterClass
    public static void cleanupTest() throws Exception {
        try {
            UTIL.shutdownMiniCluster();
        } catch (Exception e) {
            LOG.warn("failure shutting down cluster", e);
        }
    }

    @Before
    public void setup() throws Exception {
        resetProcExecutorTestingKillFlag();
    }

    private ProcedureExecutor<MasterProcedureEnv> getMasterProcedureExecutor() {
        return UTIL.getHBaseCluster().getMaster().getMasterProcedureExecutor();
    }

    private void resetProcExecutorTestingKillFlag() {
        final ProcedureExecutor<MasterProcedureEnv> procExec = getMasterProcedureExecutor();
        ProcedureTestingUtility.setKillAndToggleBeforeStoreUpdate(procExec, false);
        assertTrue("expected executor to be running", procExec.isRunning());
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testSafemodeBringsDownMaster() throws Exception {
        final TableName tableName = TableName.valueOf("testSafemodeBringsDownMaster");
        final byte[][] splitKeys = new byte[][] { Bytes.toBytes("a"), Bytes.toBytes("b"), Bytes.toBytes("c") };
        RegionInfo[] regions = MasterProcedureTestingUtility.createTable(getMasterProcedureExecutor(), tableName, splitKeys, "f1", "f2");
        MiniDFSCluster dfsCluster = UTIL.getDFSCluster();
        DistributedFileSystem dfs = (DistributedFileSystem) dfsCluster.getFileSystem();
        dfs.setSafeMode(HdfsConstants.SafeModeAction.SAFEMODE_ENTER);
        RestartFramework.at("after_master_shutdown").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        final long timeOut = 180000;
        long startTime = EnvironmentEdgeManager.currentTime();
        int index = -1;
        RestartFramework.at("after_safemode_enter").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        do {
            index = UTIL.getMiniHBaseCluster().getServerWithMeta();
        } while (index == -1 && startTime + timeOut < EnvironmentEdgeManager.currentTime());
        RestartFramework.at("after_find_meta_server").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_table_create").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        if (index != -1) {
            UTIL.getMiniHBaseCluster().abortRegionServer(index);
            UTIL.getMiniHBaseCluster().waitOnRegionServer(index);
            RestartFramework.at("after_abort_regionserver").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        }
        UTIL.waitFor(timeOut, new Waiter.Predicate<Exception>() {

            @Override
            public boolean evaluate() throws Exception {
                List<JVMClusterUtil.MasterThread> threads = UTIL.getMiniHBaseCluster().getLiveMasterThreads();
                return threads == null || threads.isEmpty();
            }
        });
        dfs.setSafeMode(HdfsConstants.SafeModeAction.SAFEMODE_LEAVE);
    }
}
