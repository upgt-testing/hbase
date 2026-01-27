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
import java.io.IOException;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.master.RegionState.State;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.hbase.shaded.protobuf.generated.ProcedureProtos.ProcedureState;

/**
 * Confirm that we will do backoff when retrying on reopening table regions, to avoid consuming all
 * the CPUs.
 */
@Category({ MasterTests.class, MediumTests.class })
public class TestReopenTableRegionsProcedureBackoff_RestartInjected_Randomized_123 {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestReopenTableRegionsProcedureBackoff_RestartInjected.class);

    private static final Logger LOG = LoggerFactory.getLogger(TestReopenTableRegionsProcedureBackoff_RestartInjected.class);

    private static final HBaseTestingUtility UTIL = new HBaseTestingUtility();

    private static TableName TABLE_NAME = TableName.valueOf("Backoff");

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
    public void testRetryBackoff() throws IOException, InterruptedException {
        RestartFramework.at("after_wait_for_waiting_timeout_state").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        AssignmentManager am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        ProcedureExecutor<MasterProcedureEnv> procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        RegionInfo regionInfo = UTIL.getAdmin().getRegions(TABLE_NAME).get(0);
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        RestartFramework.at("after_set_fake_state_and_lock").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_verify_region_reopened").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        RegionStateNode regionNode = am.getRegionStates().getRegionStateNode(regionInfo);
        RestartFramework.at("after_get_region_info").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        RestartFramework.at("after_verify_timeout_increments").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_get_region_state_node").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // just a dummy one
        TransitRegionStateProcedure trsp = TransitRegionStateProcedure.unassign(procExec.getEnvironment(), regionInfo);
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        long openSeqNum;
        regionNode.lock();
        try {
            openSeqNum = regionNode.getOpenSeqNum();
            // make a fake state to let the procedure wait.
            regionNode.setState(State.OPENING);
            regionNode.setOpenSeqNum(-1L);
            regionNode.setProcedure(trsp);
        } finally {
            regionNode.unlock();
        }
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        ReopenTableRegionsProcedure proc = new ReopenTableRegionsProcedure(TABLE_NAME);
        RestartFramework.at("after_get_am_and_proc_exec").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        procExec.submitProcedure(proc);
        RestartFramework.at("after_create_dummy_trsp").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        RestartFramework.at("after_reset_region_state").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        UTIL.waitFor(10000, () -> proc.getState() == ProcedureState.WAITING_TIMEOUT);
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        long oldTimeout = 0;
        int timeoutIncrements = 0;
        for (; ; ) {
            long timeout = proc.getTimeout();
            if (timeout > oldTimeout) {
                LOG.info("Timeout incremented, was {}, now is {}, increments={}", timeout, oldTimeout, timeoutIncrements);
                oldTimeout = timeout;
                timeoutIncrements++;
                if (timeoutIncrements > 3) {
                    // If we incremented at least twice, break; the backoff is working.
                    break;
                }
            }
            Thread.sleep(1000);
        }
        RestartFramework.at("after_submit_reopen_procedure").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        regionNode.lock();
        try {
            // reset to the correct state
            regionNode.setState(State.OPEN);
            regionNode.setOpenSeqNum(openSeqNum);
            regionNode.unsetProcedure(trsp);
        } finally {
            regionNode.unlock();
        }
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        ProcedureSyncWait.waitForProcedureToComplete(procExec, proc, 60000);
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        RestartFramework.at("after_wait_for_procedure_complete").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        assertTrue(regionNode.getOpenSeqNum() > openSeqNum);
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
    }
}
