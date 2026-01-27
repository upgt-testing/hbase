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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.AsyncAdmin;
import org.apache.hadoop.hbase.client.AsyncConnection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.master.assignment.TransitRegionStateProcedure;
import org.apache.hadoop.hbase.master.procedure.MasterProcedureEnv;
import org.apache.hadoop.hbase.procedure2.ProcedureExecutor;
import org.apache.hadoop.hbase.regionserver.HRegionServer;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.JVMClusterUtil.RegionServerThread;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

@Category({ MasterTests.class, MediumTests.class })
public class TestServerCrashProcedureCarryingMetaStuck_RestartInjected_Randomized_456 {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestServerCrashProcedureCarryingMetaStuck_RestartInjected.class);

    private static final HBaseTestingUtility UTIL = new HBaseTestingUtility();

    @BeforeClass
    public static void setUp() throws Exception {
        UTIL.startMiniCluster(3);
        UTIL.getAdmin().balancerSwitch(false, true);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        UTIL.shutdownMiniCluster();
    }

    @Test
    public void test() throws Exception {
        RegionServerThread rsThread = null;
        for (RegionServerThread t : UTIL.getMiniHBaseCluster().getRegionServerThreads()) {
            if (!t.getRegionServer().getRegions(TableName.META_TABLE_NAME).isEmpty()) {
                rsThread = t;
                break;
            }
        }
        HRegionServer rs = rsThread.getRegionServer();
        RegionInfo hri = rs.getRegions(TableName.META_TABLE_NAME).get(0).getRegionInfo();
        HMaster master = UTIL.getMiniHBaseCluster().getMaster();
        ProcedureExecutor<MasterProcedureEnv> executor = master.getMasterProcedureExecutor();
        DummyRegionProcedure proc = new DummyRegionProcedure(executor.getEnvironment(), hri);
        long procId = master.getMasterProcedureExecutor().submitProcedure(proc);
        // Refresh after master restart
        master = UTIL.getMiniHBaseCluster().getMaster();
        // Refresh after master restart
        executor = master.getMasterProcedureExecutor();
        RestartFramework.at("after_find_meta_server").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        proc.waitUntilArrive();
        RestartFramework.at("after_procedure_arrive").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        master = UTIL.getMiniHBaseCluster().getMaster();
        RestartFramework.at("after_submit_procedure").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Refresh after master restart
        executor = master.getMasterProcedureExecutor();
        RestartFramework.at("after_get_region_info").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        try (AsyncConnection conn = ConnectionFactory.createAsyncConnection(UTIL.getConfiguration()).get()) {
            AsyncAdmin admin = conn.getAdmin();
            CompletableFuture<Void> future = admin.move(hri.getRegionName());
            rs.abort("For testing!");
            RestartFramework.at("after_rs_abort").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            admin = conn.getAdmin();
            // Refresh after master restart
            master = UTIL.getMiniHBaseCluster().getMaster();
            // Refresh after master restart
            executor = master.getMasterProcedureExecutor();
            final ProcedureExecutor<MasterProcedureEnv> execForLambda1 = executor;
            UTIL.waitFor(30000, () -> execForLambda1.getProcedures().stream().filter(p -> p instanceof TransitRegionStateProcedure).map(p -> (TransitRegionStateProcedure) p).anyMatch(p -> Bytes.equals(hri.getRegionName(), p.getRegion().getRegionName())));
            RestartFramework.at("after_transit_procedure_created").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            admin = conn.getAdmin();
            // Refresh after master restart
            master = UTIL.getMiniHBaseCluster().getMaster();
            // Refresh after master restart
            executor = master.getMasterProcedureExecutor();
            proc.resume();
            RestartFramework.at("after_procedure_resume").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            admin = conn.getAdmin();
            // Refresh after master restart
            master = UTIL.getMiniHBaseCluster().getMaster();
            // Refresh after master restart
            executor = master.getMasterProcedureExecutor();
            final ProcedureExecutor<MasterProcedureEnv> execForLambda2 = executor;
            UTIL.waitFor(30000, () -> execForLambda2.isFinished(procId));
            RestartFramework.at("after_procedure_finish").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            admin = conn.getAdmin();
            master = UTIL.getMiniHBaseCluster().getMaster();
            executor = master.getMasterProcedureExecutor();
            future.get(30, TimeUnit.SECONDS);
        }
    }
}
