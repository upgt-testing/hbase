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
package org.apache.hadoop.hbase.master.assignment;

import static org.apache.hadoop.hbase.shaded.protobuf.generated.MasterProcedureProtos.RegionStateTransitionState.REGION_STATE_TRANSITION_CONFIRM_OPENED_VALUE;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.PleaseHoldException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.master.HMaster;
import org.apache.hadoop.hbase.master.MasterServices;
import org.apache.hadoop.hbase.master.RegionPlan;
import org.apache.hadoop.hbase.master.procedure.MasterProcedureEnv;
import org.apache.hadoop.hbase.master.region.MasterRegion;
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
import org.apache.hadoop.hbase.shaded.protobuf.generated.RegionServerStatusProtos.ReportRegionStateTransitionRequest;
import org.apache.hadoop.hbase.shaded.protobuf.generated.RegionServerStatusProtos.ReportRegionStateTransitionResponse;

@Category({ MasterTests.class, MediumTests.class })
public class TestReportRegionStateTransitionRetry_RestartInjected_Randomized_456 {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestReportRegionStateTransitionRetry_RestartInjected.class);

    private static final AtomicReference<CountDownLatch> RESUME_AND_FAIL = new AtomicReference<>();

    private static final class AssignmentManagerForTest extends AssignmentManager {

        public AssignmentManagerForTest(MasterServices master, MasterRegion masterRegion) {
            super(master, masterRegion);
        }

        @Override
        public ReportRegionStateTransitionResponse reportRegionStateTransition(ReportRegionStateTransitionRequest req) throws PleaseHoldException {
            ReportRegionStateTransitionResponse resp = super.reportRegionStateTransition(req);
            CountDownLatch latch = RESUME_AND_FAIL.getAndSet(null);
            if (latch != null) {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                throw new PleaseHoldException("Inject error");
            }
            return resp;
        }
    }

    public static final class HMasterForTest extends HMaster {

        public HMasterForTest(Configuration conf) throws IOException {
            super(conf);
        }

        @Override
        protected AssignmentManager createAssignmentManager(MasterServices master, MasterRegion masterRegion) {
            return new AssignmentManagerForTest(master, masterRegion);
        }
    }

    private static final HBaseTestingUtility UTIL = new HBaseTestingUtility();

    private static TableName NAME = TableName.valueOf("Retry");

    private static byte[] CF = Bytes.toBytes("cf");

    @BeforeClass
    public static void setUp() throws Exception {
        UTIL.getConfiguration().setClass(HConstants.MASTER_IMPL, HMasterForTest.class, HMaster.class);
        UTIL.startMiniCluster(1);
        UTIL.createTable(NAME, CF);
        UTIL.waitTableAvailable(NAME);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        UTIL.shutdownMiniCluster();
    }

    @Test
    public void testRetryOnClose() throws Exception {
        RestartFramework.at("after_verify_write").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RegionInfo region = UTIL.getMiniHBaseCluster().getRegions(NAME).get(0).getRegionInfo();
        RestartFramework.at("after_get_assignment_manager").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        ProcedureExecutor<MasterProcedureEnv> procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        AssignmentManager am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        RestartFramework.at("after_set_latch").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        RegionStateNode rsn = am.getRegionStates().getRegionStateNode(region);
        CountDownLatch latch = new CountDownLatch(1);
        RESUME_AND_FAIL.set(latch);
        RestartFramework.at("after_submit_move").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_get_region_info").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        RestartFramework.at("after_wait_procedure_state").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        Future<byte[]> future = am.moveAsync(new RegionPlan(region, rsn.getRegionLocation(), rsn.getRegionLocation()));
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        final ProcedureExecutor<MasterProcedureEnv> finalProcExec = procExec;
        TransitRegionStateProcedure proc = finalProcExec.getProcedures().stream().filter(p -> p instanceof TransitRegionStateProcedure).filter(p -> !p.isFinished()).map(p -> (TransitRegionStateProcedure) p).findAny().get();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        // wait until we schedule the OpenRegionProcedure
        UTIL.waitFor(10000, () -> proc.getCurrentStateId() == REGION_STATE_TRANSITION_CONFIRM_OPENED_VALUE);
        RestartFramework.at("after_latch_countdown").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_get_procedure").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        // Fail the reportRegionStateTransition for closing
        latch.countDown();
        RestartFramework.at("after_move_complete").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        // Refresh after master restart
        am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();
        future.get();
        // confirm that the region can still be write
        try (Table table = UTIL.getConnection().getTableBuilder(NAME, null).setWriteRpcTimeout(1000).setOperationTimeout(2000).build()) {
            table.put(new Put(Bytes.toBytes("key")).addColumn(CF, Bytes.toBytes("cq"), Bytes.toBytes("val")));
        }
    }
}
