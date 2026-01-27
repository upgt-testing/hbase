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

import java.io.IOException;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.master.HMaster;
import org.apache.hadoop.hbase.master.ServerManager;
import org.apache.hadoop.hbase.master.assignment.AssignmentManager;
import org.apache.hadoop.hbase.master.assignment.RegionStateNode;
import org.apache.hadoop.hbase.master.assignment.TransitRegionStateProcedure;
import org.apache.hadoop.hbase.procedure2.ProcedureExecutor;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

/**
 * Testcase for HBASE-21330.
 */
@Category({ MasterTests.class, MediumTests.class })
public class TestReopenTableRegionsProcedureInfiniteLoop_RestartInjected_Randomized_123 {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestReopenTableRegionsProcedureInfiniteLoop_RestartInjected.class);

    private static final HBaseTestingUtility UTIL = new HBaseTestingUtility();

    private static TableName TABLE_NAME = TableName.valueOf("InfiniteLoop");

    private static byte[] CF = Bytes.toBytes("cf");

    @BeforeClass
    public static void setUp() throws Exception {
        UTIL.getConfiguration().setInt(ServerManager.WAIT_ON_REGIONSERVERS_MINTOSTART, 1);
        UTIL.startMiniCluster(1);
        UTIL.createTable(TABLE_NAME, CF);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        UTIL.shutdownMiniCluster();
    }

    @Test
    public void testInfiniteLoop() throws IOException {
        HMaster master = UTIL.getMiniHBaseCluster().getMaster();
        AssignmentManager am = master.getAssignmentManager();
        ProcedureExecutor<MasterProcedureEnv> exec = master.getMasterProcedureExecutor();
        RestartFramework.at("after_get_region_info").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        master = UTIL.getMiniHBaseCluster().getMaster();
        // Refresh after master restart
        am = master.getAssignmentManager();
        // Refresh after master restart
        exec = master.getMasterProcedureExecutor();
        RegionInfo regionInfo = UTIL.getAdmin().getRegions(TABLE_NAME).get(0);
        // Refresh after master restart
        master = UTIL.getMiniHBaseCluster().getMaster();
        // Refresh after master restart
        am = master.getAssignmentManager();
        // Refresh after master restart
        exec = master.getMasterProcedureExecutor();
        RestartFramework.at("after_lock_region_node").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RegionStateNode regionNode = am.getRegionStates().getRegionStateNode(regionInfo);
        RestartFramework.at("after_create_reopen_procedure").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        master = UTIL.getMiniHBaseCluster().getMaster();
        // Refresh after master restart
        am = master.getAssignmentManager();
        // Refresh after master restart
        exec = master.getMasterProcedureExecutor();
        // Refresh after master restart
        regionNode = am.getRegionStates().getRegionStateNode(regionInfo);
        long procId;
        ReopenTableRegionsProcedure proc = new ReopenTableRegionsProcedure(TABLE_NAME);
        RestartFramework.at("after_get_master_am_exec").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        master = UTIL.getMiniHBaseCluster().getMaster();
        RestartFramework.at("after_get_region_state_node").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        am = master.getAssignmentManager();
        // Refresh after master restart
        exec = master.getMasterProcedureExecutor();
        // Refresh after master restart
        regionNode = am.getRegionStates().getRegionStateNode(regionInfo);
        regionNode.lock();
        // Refresh after master restart
        master = UTIL.getMiniHBaseCluster().getMaster();
        // Refresh after master restart
        am = master.getAssignmentManager();
        // Refresh after master restart
        exec = master.getMasterProcedureExecutor();
        // Refresh after master restart
        regionNode = am.getRegionStates().getRegionStateNode(regionInfo);
        try {
            procId = exec.submitProcedure(proc);
            RestartFramework.at("after_submit_reopen_procedure").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            // Refresh after master restart
            master = UTIL.getMiniHBaseCluster().getMaster();
            // Refresh after master restart
            am = master.getAssignmentManager();
            // Refresh after master restart
            exec = master.getMasterProcedureExecutor();
            // Refresh after master restart
            regionNode = am.getRegionStates().getRegionStateNode(regionInfo);
            UTIL.waitFor(30000, () -> proc.hasLock());
            RestartFramework.at("after_wait_proc_has_lock").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            // Refresh after master restart
            master = UTIL.getMiniHBaseCluster().getMaster();
            // Refresh after master restart
            am = master.getAssignmentManager();
            // Refresh after master restart
            exec = master.getMasterProcedureExecutor();
            // Refresh after master restart
            regionNode = am.getRegionStates().getRegionStateNode(regionInfo);
            TransitRegionStateProcedure trsp = TransitRegionStateProcedure.reopen(exec.getEnvironment(), regionInfo);
            RestartFramework.at("after_create_trsp").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            // Refresh after master restart
            master = UTIL.getMiniHBaseCluster().getMaster();
            // Refresh after master restart
            am = master.getAssignmentManager();
            // Refresh after master restart
            exec = master.getMasterProcedureExecutor();
            // Refresh after master restart
            regionNode = am.getRegionStates().getRegionStateNode(regionInfo);
            regionNode.setProcedure(trsp);
            RestartFramework.at("after_set_trsp_on_region_node").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            // Refresh after master restart
            master = UTIL.getMiniHBaseCluster().getMaster();
            // Refresh after master restart
            am = master.getAssignmentManager();
            // Refresh after master restart
            exec = master.getMasterProcedureExecutor();
            // Refresh after master restart
            regionNode = am.getRegionStates().getRegionStateNode(regionInfo);
            exec.submitProcedure(trsp);
            RestartFramework.at("after_submit_trsp").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            // Refresh after master restart
            master = UTIL.getMiniHBaseCluster().getMaster();
            // Refresh after master restart
            am = master.getAssignmentManager();
            // Refresh after master restart
            exec = master.getMasterProcedureExecutor();
            // Refresh after master restart
            regionNode = am.getRegionStates().getRegionStateNode(regionInfo);
        } finally {
            regionNode.unlock();
        }
        // Refresh after master restart
        master = UTIL.getMiniHBaseCluster().getMaster();
        RestartFramework.at("after_unlock_region_node").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        am = master.getAssignmentManager();
        // Refresh after master restart
        exec = master.getMasterProcedureExecutor();
        final ProcedureExecutor<MasterProcedureEnv> finalExec = exec;
        UTIL.waitFor(60000, () -> finalExec.isFinished(procId));
    }
}
