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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.PleaseHoldException;
import org.apache.hadoop.hbase.StartMiniClusterOption;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.master.HMaster;
import org.apache.hadoop.hbase.master.MasterServices;
import org.apache.hadoop.hbase.master.RegionPlan;
import org.apache.hadoop.hbase.master.procedure.MasterProcedureEnv;
import org.apache.hadoop.hbase.master.region.MasterRegion;
import org.apache.hadoop.hbase.procedure2.ProcedureExecutor;
import org.apache.hadoop.hbase.regionserver.HRegionServer;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.JVMClusterUtil.MasterThread;
import org.apache.zookeeper.KeeperException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.hbase.shaded.protobuf.ProtobufUtil;
import org.apache.hadoop.hbase.shaded.protobuf.generated.RegionServerStatusProtos.RegionStateTransition;
import org.apache.hadoop.hbase.shaded.protobuf.generated.RegionServerStatusProtos.RegionStateTransition.TransitionCode;
import org.apache.hadoop.hbase.shaded.protobuf.generated.RegionServerStatusProtos.ReportRegionStateTransitionRequest;
import org.apache.hadoop.hbase.shaded.protobuf.generated.RegionServerStatusProtos.ReportRegionStateTransitionResponse;

/**
 * See HBASE-22060 and HBASE-22074 for more details.
 */
@Category({ MasterTests.class, MediumTests.class })
public class TestOpenRegionProcedureHang_RestartInjected {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestOpenRegionProcedureHang_RestartInjected.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestOpenRegionProcedureHang_RestartInjected.class);

  private static CountDownLatch ARRIVE;
  private static CountDownLatch RESUME;

  private static CountDownLatch FINISH;

  private static CountDownLatch ABORT;

  private static final class AssignmentManagerForTest extends AssignmentManager {

    public AssignmentManagerForTest(MasterServices master, MasterRegion masterRegion) {
      super(master, masterRegion);
    }

    @Override
    public ReportRegionStateTransitionResponse reportRegionStateTransition(
      ReportRegionStateTransitionRequest req) throws PleaseHoldException {
      RegionStateTransition transition = req.getTransition(0);
      if (
        transition.getTransitionCode() == TransitionCode.OPENED
          && ProtobufUtil.toTableName(transition.getRegionInfo(0).getTableName()).equals(NAME)
          && ARRIVE != null
      ) {
        ARRIVE.countDown();
        try {
          RESUME.await();
          RESUME = null;
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        try {
          return super.reportRegionStateTransition(req);
        } finally {
          FINISH.countDown();
        }
      } else {
        return super.reportRegionStateTransition(req);
      }
    }
  }

  public static final class HMasterForTest extends HMaster {

    public HMasterForTest(Configuration conf) throws IOException {
      super(conf);
    }

    @Override
    protected AssignmentManager createAssignmentManager(MasterServices master,
      MasterRegion masterRegion) {
      return new AssignmentManagerForTest(master, masterRegion);
    }

    @Override
    public void abort(String reason, Throwable cause) {
      // hang here so we can finish the reportRegionStateTransition call, which is the most
      // important part to reproduce the bug
      if (ABORT != null) {
        try {
          ABORT.await();
          ABORT = null;
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      super.abort(reason, cause);
    }
  }

  private static final HBaseTestingUtility UTIL = new HBaseTestingUtility();

  private static TableName NAME = TableName.valueOf("Open");

  private static byte[] CF = Bytes.toBytes("cf");

  @BeforeClass
  public static void setUp() throws Exception {
    Configuration conf = UTIL.getConfiguration();
    conf.setClass(HConstants.MASTER_IMPL, HMasterForTest.class, HMaster.class);

    // make sure we do not timeout when caling reportRegionStateTransition
    conf.setInt(HConstants.HBASE_CLIENT_OPERATION_TIMEOUT, 10 * 60 * 1000);
    conf.setInt(HConstants.HBASE_RPC_SHORTOPERATION_TIMEOUT_KEY, 10 * 60 * 1000);
    UTIL
      .startMiniCluster(StartMiniClusterOption.builder().numMasters(2).numRegionServers(3).build());
    UTIL.createTable(NAME, CF);
    UTIL.waitTableAvailable(NAME);
    UTIL.getAdmin().balancerSwitch(false, true);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    UTIL.shutdownMiniCluster();
  }

  @Test
  public void test() throws InterruptedException, KeeperException, IOException {
    RegionInfo region = UTIL.getMiniHBaseCluster().getRegions(NAME).get(0).getRegionInfo();

    RestartFramework.at("after_get_region_info")
        .on(UTIL.getMiniHBaseCluster())
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    AssignmentManager am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();

    HRegionServer rs1 = UTIL.getRSForFirstRegionInTable(NAME);
    HRegionServer rs2 = UTIL.getOtherRegionServer(rs1);

    RestartFramework.at("after_get_region_servers")
        .on(UTIL.getMiniHBaseCluster())
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    rs1 = UTIL.getRSForFirstRegionInTable(NAME);

    ARRIVE = new CountDownLatch(1);
    RESUME = new CountDownLatch(1);
    FINISH = new CountDownLatch(1);
    ABORT = new CountDownLatch(1);

    RestartFramework.at("after_set_latches")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager(); // Refresh after master restart

    am.moveAsync(new RegionPlan(region, rs1.getServerName(), rs2.getServerName()));

    RestartFramework.at("after_submit_move")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager(); // Refresh after master restart

    ARRIVE.await();
    ARRIVE = null;

    RestartFramework.at("after_arrive_latch")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager(); // Refresh after master restart

    HMaster master = UTIL.getMiniHBaseCluster().getMaster();
    master.getZooKeeper().close();

    RestartFramework.at("after_close_zk")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    master = UTIL.getMiniHBaseCluster().getMaster();
    am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager(); // Refresh after master restart

    final HMaster finalMaster = master;
    UTIL.waitFor(30000, () -> {
      for (MasterThread mt : UTIL.getMiniHBaseCluster().getMasterThreads()) {
        if (mt.getMaster() != finalMaster && mt.getMaster().isActiveMaster()) {
          return mt.getMaster().isInitialized();
        }
      }
      return false;
    });

    RestartFramework.at("after_wait_new_master")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    master = UTIL.getMiniHBaseCluster().getMaster();
    am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager(); // Refresh after master restart

    ProcedureExecutor<MasterProcedureEnv> procExec =
      UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
    final ProcedureExecutor<MasterProcedureEnv> finalProcExec = procExec;
    UTIL.waitFor(30000,
      () -> finalProcExec.getProcedures().stream().filter(p -> p instanceof OpenRegionProcedure)
        .map(p -> (OpenRegionProcedure) p).anyMatch(p -> p.region.getTable().equals(NAME)));

    RestartFramework.at("after_wait_open_procedure")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    master = UTIL.getMiniHBaseCluster().getMaster();
    procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor(); // Refresh after master restart
    am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager(); // Refresh after master restart

    OpenRegionProcedure proc = procExec.getProcedures().stream()
      .filter(p -> p instanceof OpenRegionProcedure).map(p -> (OpenRegionProcedure) p)
      .filter(p -> p.region.getTable().equals(NAME)).findFirst().get();

    RestartFramework.at("after_get_procedure")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    master = UTIL.getMiniHBaseCluster().getMaster();
    procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor(); // Refresh after master restart
    am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager(); // Refresh after master restart

    // wait a bit to let the OpenRegionProcedure send out the request
    Thread.sleep(2000);
    RESUME.countDown();

    RestartFramework.at("after_resume_countdown")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    master = UTIL.getMiniHBaseCluster().getMaster();
    procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor(); // Refresh after master restart
    am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager(); // Refresh after master restart

    if (!FINISH.await(15, TimeUnit.SECONDS)) {
      LOG.info("Wait reportRegionStateTransition to finish timed out, this is possible if"
        + " we update the procedure store, as the WALProcedureStore"
        + " will retry forever to roll the writer if it is not closed");
    }
    FINISH = null;

    RestartFramework.at("after_finish_await")
        .on(UTIL.getMiniHBaseCluster())
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    rs1 = UTIL.getRSForFirstRegionInTable(NAME);

    // if the reportRegionTransition is finished, wait a bit to let it return the data to RS
    Thread.sleep(2000);
    ABORT.countDown();

    RestartFramework.at("after_abort_countdown")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    master = UTIL.getMiniHBaseCluster().getMaster();
    procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor(); // Refresh after master restart
    am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager(); // Refresh after master restart

    final ProcedureExecutor<MasterProcedureEnv> finalProcExec2 = procExec;
    UTIL.waitFor(30000, () -> finalProcExec2.isFinished(proc.getProcId()));

    RestartFramework.at("after_wait_proc_finish")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    master = UTIL.getMiniHBaseCluster().getMaster();
    procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor(); // Refresh after master restart
    am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager(); // Refresh after master restart

    final ProcedureExecutor<MasterProcedureEnv> finalProcExec3 = procExec;
    UTIL.waitFor(30000, () -> finalProcExec3.isFinished(proc.getParentProcId()));

    RestartFramework.at("after_wait_parent_proc_finish")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    master = UTIL.getMiniHBaseCluster().getMaster();
    procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor(); // Refresh after master restart
    am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager(); // Refresh after master restart
  }
}
