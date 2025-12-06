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

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.coprocessor.CoprocessorHost;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessor;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.coprocessor.RegionObserver;
import org.apache.hadoop.hbase.procedure2.ProcedureExecutor;
import org.apache.hadoop.hbase.procedure2.ProcedureTestingUtility;
import org.apache.hadoop.hbase.regionserver.HRegionServer;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.JVMClusterUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

@Category({ MasterTests.class, MediumTests.class })
public class TestExceptionInAssignRegion_RestartInjected {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestExceptionInAssignRegion_RestartInjected.class);

  private static final HBaseTestingUtility UTIL = new HBaseTestingUtility();

  private static final TableName TABLE_NAME = TableName.valueOf("test");

  private static final CountDownLatch countDownLatch = new CountDownLatch(2);

  private static final byte[] CF = Bytes.toBytes("cf");

  @BeforeClass
  public static void setUp() throws Exception {
    UTIL.getConfiguration().setStrings(CoprocessorHost.REGION_COPROCESSOR_CONF_KEY,
      ThrowInOpenCP.class.getName());
    UTIL.startMiniCluster(3);
    UTIL.getAdmin().balancerSwitch(false, true);
    UTIL.createTable(TABLE_NAME, CF);
    UTIL.waitTableAvailable(TABLE_NAME);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    UTIL.shutdownMiniCluster();
  }

  @Test
  public void testExceptionInAssignRegion() {
    ProcedureExecutor procedureExecutor =
      UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();

    RestartFramework.at("after_get_procedure_executor")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    JVMClusterUtil.RegionServerThread rsThread = null;
    for (JVMClusterUtil.RegionServerThread t : UTIL.getMiniHBaseCluster()
      .getRegionServerThreads()) {
      if (!t.getRegionServer().getRegions(TABLE_NAME).isEmpty()) {
        rsThread = t;
        break;
      }
    }

    RestartFramework.at("after_find_rs_thread")
        .on(UTIL.getMiniHBaseCluster())
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // find the rs and hri of the table
    HRegionServer rs = rsThread.getRegionServer();
    RegionInfo hri = rs.getRegions(TABLE_NAME).get(0).getRegionInfo();

    RestartFramework.at("after_get_region_info")
        .on(UTIL.getMiniHBaseCluster())
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    TransitRegionStateProcedure assignRegionProcedure = TransitRegionStateProcedure.move(
      UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor().getEnvironment(), hri,
      null);

    RestartFramework.at("after_create_assign_procedure")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    RegionStateNode regionNode = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager()
      .getRegionStates().getOrCreateRegionStateNode(hri);
    regionNode.setProcedure(assignRegionProcedure);

    RestartFramework.at("after_set_procedure")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    countDownLatch.countDown();
    long prodId = procedureExecutor.submitProcedure(assignRegionProcedure);

    RestartFramework.at("after_submit_procedure")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    ProcedureTestingUtility.waitProcedure(procedureExecutor, prodId);

    RestartFramework.at("after_wait_procedure")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    Assert.assertEquals("Should be two RS since other is aborted", 2,
      UTIL.getMiniHBaseCluster().getLiveRegionServerThreads().size());

    RestartFramework.at("after_verify_rs_count")
        .on(UTIL.getMiniHBaseCluster())
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    Assert.assertNull("RIT Map doesn't have correct value",
      getRegionServer(0).getRegionsInTransitionInRS().get(hri.getEncodedNameAsBytes()));
    Assert.assertNull("RIT Map doesn't have correct value",
      getRegionServer(1).getRegionsInTransitionInRS().get(hri.getEncodedNameAsBytes()));
    Assert.assertNull("RIT Map doesn't have correct value",
      getRegionServer(2).getRegionsInTransitionInRS().get(hri.getEncodedNameAsBytes()));

    RestartFramework.at("after_verify_rit_maps")
        .on(UTIL.getMiniHBaseCluster())
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
  }

  private HRegionServer getRegionServer(int index) {
    return UTIL.getMiniHBaseCluster().getRegionServer(index);
  }

  public static class ThrowInOpenCP implements RegionCoprocessor, RegionObserver {
    @Override
    public void preOpen(ObserverContext<RegionCoprocessorEnvironment> c) {
      if (countDownLatch.getCount() == 1) {
        // We want to throw exception only first time in move region call
        // After that RS aborts and we don't want to throw in any other open region
        countDownLatch.countDown();
        throw new RuntimeException();
      }
    }

    @Override
    public Optional<RegionObserver> getRegionObserver() {
      return Optional.of(this);
    }
  }
}
