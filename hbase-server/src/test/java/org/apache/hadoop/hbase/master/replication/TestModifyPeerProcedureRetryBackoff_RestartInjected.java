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
package org.apache.hadoop.hbase.master.replication;

import java.io.IOException;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.ProcedureTestUtil;
import org.apache.hadoop.hbase.master.procedure.MasterProcedureEnv;
import org.apache.hadoop.hbase.procedure2.Procedure;
import org.apache.hadoop.hbase.procedure2.ProcedureExecutor;
import org.apache.hadoop.hbase.replication.ReplicationException;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import org.apache.hadoop.hbase.shaded.protobuf.generated.MasterProcedureProtos.PeerModificationState;

@Category({ MasterTests.class, LargeTests.class })
public class TestModifyPeerProcedureRetryBackoff_RestartInjected {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestModifyPeerProcedureRetryBackoff_RestartInjected.class);

  private static final HBaseTestingUtility UTIL = new HBaseTestingUtility();

  private static boolean FAIL = true;

  public static class TestModifyPeerProcedure extends ModifyPeerProcedure {

    public TestModifyPeerProcedure() {
    }

    public TestModifyPeerProcedure(String peerId) {
      super(peerId);
    }

    @Override
    public PeerOperationType getPeerOperationType() {
      return PeerOperationType.ADD;
    }

    private void tryFail() throws ReplicationException {
      synchronized (TestModifyPeerProcedureRetryBackoff_RestartInjected.class) {
        if (FAIL) {
          throw new ReplicationException("Inject error");
        }
        FAIL = true;
      }
    }

    @Override
    protected <T extends Procedure<MasterProcedureEnv>> void
      addChildProcedure(@SuppressWarnings("unchecked") T... subProcedure) {
      // Make it a no-op
    }

    @Override
    protected PeerModificationState nextStateAfterRefresh() {
      return PeerModificationState.SERIAL_PEER_REOPEN_REGIONS;
    }

    @Override
    protected boolean enablePeerBeforeFinish() {
      return true;
    }

    @Override
    protected void updateLastPushedSequenceIdForSerialPeer(MasterProcedureEnv env)
      throws IOException, ReplicationException {
      tryFail();
    }

    @Override
    protected void reopenRegions(MasterProcedureEnv env) throws IOException {
      try {
        tryFail();
      } catch (ReplicationException e) {
        throw new IOException(e);
      }
    }

    @Override
    protected void enablePeer(MasterProcedureEnv env) throws ReplicationException {
      tryFail();
    }

    @Override
    protected void prePeerModification(MasterProcedureEnv env)
      throws IOException, ReplicationException {
      tryFail();
    }

    @Override
    protected void updatePeerStorage(MasterProcedureEnv env) throws ReplicationException {
      tryFail();
    }

    @Override
    protected void postPeerModification(MasterProcedureEnv env)
      throws IOException, ReplicationException {
      tryFail();
    }
  }

  @BeforeClass
  public static void setUp() throws Exception {
    UTIL.startMiniCluster(1);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    UTIL.shutdownMiniCluster();
  }

  private void assertBackoffIncrease() throws IOException, InterruptedException {
    ProcedureTestUtil.waitUntilProcedureWaitingTimeout(UTIL, TestModifyPeerProcedure.class, 30000);
    ProcedureTestUtil.waitUntilProcedureTimeoutIncrease(UTIL, TestModifyPeerProcedure.class, 2);
    synchronized (TestModifyPeerProcedureRetryBackoff_RestartInjected.class) {
      FAIL = false;
    }
    UTIL.waitFor(30000, () -> FAIL);
  }

  @Test
  public void test() throws IOException, InterruptedException {
    ProcedureExecutor<MasterProcedureEnv> procExec =
      UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();

    RestartFramework.at("after_get_proc_exec")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    long procId = procExec.submitProcedure(new TestModifyPeerProcedure("1"));

    RestartFramework.at("after_submit_proc")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // PRE_PEER_MODIFICATION
    assertBackoffIncrease();

    RestartFramework.at("after_pre_peer_modification_backoff")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // UPDATE_PEER_STORAGE
    assertBackoffIncrease();

    RestartFramework.at("after_update_peer_storage_backoff")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // No retry for REFRESH_PEER_ON_RS
    // SERIAL_PEER_REOPEN_REGIONS
    assertBackoffIncrease();

    RestartFramework.at("after_serial_peer_reopen_regions_backoff")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // SERIAL_PEER_UPDATE_LAST_PUSHED_SEQ_ID
    assertBackoffIncrease();

    RestartFramework.at("after_serial_peer_update_last_pushed_seq_id_backoff")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // SERIAL_PEER_SET_PEER_ENABLED
    assertBackoffIncrease();

    RestartFramework.at("after_serial_peer_set_peer_enabled_backoff")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // No retry for SERIAL_PEER_ENABLE_PEER_REFRESH_PEER_ON_RS
    // POST_PEER_MODIFICATION
    assertBackoffIncrease();

    RestartFramework.at("after_post_peer_modification_backoff")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    UTIL.waitFor(30000, () -> procExec.isFinished(procId));

    RestartFramework.at("after_wait_proc_finish")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
  }
}
