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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.client.AsyncAdmin;
import org.apache.hadoop.hbase.client.AsyncConnection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.replication.DummyReplicationEndpoint;
import org.apache.hadoop.hbase.replication.FSReplicationPeerStorage;
import org.apache.hadoop.hbase.replication.ReplicationException;
import org.apache.hadoop.hbase.replication.ReplicationPeerConfig;
import org.apache.hadoop.hbase.replication.ReplicationPeerStorage;
import org.apache.hadoop.hbase.replication.ReplicationStorageFactory;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import org.apache.hbase.thirdparty.com.google.common.io.Closeables;

@RunWith(Parameterized.class)
@Category({ MasterTests.class, LargeTests.class })
public class TestDisablePeerModification_RestartInjected {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestDisablePeerModification_RestartInjected.class);

  private static final HBaseTestingUtility UTIL = new HBaseTestingUtility();

  private static volatile CountDownLatch ARRIVE;

  private static volatile CountDownLatch RESUME;

  public static final class MockPeerStorage extends FSReplicationPeerStorage {

    public MockPeerStorage(FileSystem fs, Configuration conf) throws IOException {
      super(fs, conf);
    }

    @Override
    public void addPeer(String peerId, ReplicationPeerConfig peerConfig, boolean enabled)
      throws ReplicationException {
      ARRIVE.countDown();
      try {
        RESUME.await();
      } catch (InterruptedException e) {
        throw new ReplicationException(e);
      }
      super.addPeer(peerId, peerConfig, enabled);
    }
  }

  private static AsyncConnection CONN;

  @Parameter
  public boolean async;

  @Parameters(name = "{index}: async={0}")
  public static List<Object[]> params() {
    return Arrays.asList(new Object[] { true }, new Object[] { false });
  }

  @BeforeClass
  public static void setUp() throws Exception {
    UTIL.getConfiguration().setClass(ReplicationStorageFactory.REPLICATION_PEER_STORAGE_IMPL,
      MockPeerStorage.class, ReplicationPeerStorage.class);
    UTIL.startMiniCluster(1);
    CONN = ConnectionFactory.createAsyncConnection(UTIL.getConfiguration()).get();
  }

  @AfterClass
  public static void tearDown() throws IOException {
    Closeables.close(CONN, true);
    UTIL.shutdownMiniCluster();
  }

  @Before
  public void setUpBeforeTest() throws IOException {
    UTIL.getAdmin().replicationPeerModificationSwitch(true, true);
  }

  @Test
  public void testDrainProcs() throws Exception {
    ARRIVE = new CountDownLatch(1);
    RESUME = new CountDownLatch(1);

    RestartFramework.at("after_create_latches")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    AsyncAdmin admin = CONN.getAdmin();

    RestartFramework.at("after_get_admin")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    ReplicationPeerConfig rpc =
      ReplicationPeerConfig.newBuilder().setClusterKey(UTIL.getClusterKey() + "-test")
        .setReplicationEndpointImpl(DummyReplicationEndpoint.class.getName()).build();

    RestartFramework.at("after_build_peer_config")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    CompletableFuture<Void> addFuture = admin.addReplicationPeer("test_peer_" + async, rpc);

    RestartFramework.at("after_submit_add_peer_future")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    ARRIVE.await();

    RestartFramework.at("after_arrive_latch_wait")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // we have a pending add peer procedure which has already passed the first state, let's issue a
    // peer modification switch request to disable peer modification and set drainProcs to true
    CompletableFuture<Boolean> switchFuture;
    if (async) {
      switchFuture = admin.replicationPeerModificationSwitch(false, true);
    } else {
      switchFuture = new CompletableFuture<>();
      ForkJoinPool.commonPool().submit(() -> {
        try {
          switchFuture.complete(UTIL.getAdmin().replicationPeerModificationSwitch(false, true));
        } catch (IOException e) {
          switchFuture.completeExceptionally(e);
        }
      });
    }

    RestartFramework.at("after_submit_switch_future")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // sleep a while, the switchFuture should not finish yet
    // the sleep is necessary as we can not join on the switchFuture, so there is no stable way to
    // make sure we have already changed the flag at master side, sleep a while is the most suitable
    // way here
    Thread.sleep(5000);
    assertFalse(switchFuture.isDone());

    RestartFramework.at("after_first_sleep_verify_not_done")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // also verify that we can not schedule a new peer modification procedure
    AddPeerProcedure proc = new AddPeerProcedure("failure", rpc, true);
    UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor().submitProcedure(proc);

    RestartFramework.at("after_submit_failure_proc")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    UTIL.waitFor(15000, () -> proc.isFinished());

    RestartFramework.at("after_wait_failure_proc_finish")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // make sure the procedure is failed because of peer modification disabled
    assertTrue(proc.isFailed());
    assertThat(proc.getException().getCause().getMessage(),
      containsString("Replication peer modification disabled"));

    RestartFramework.at("after_verify_failure_proc")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // sleep a while and check again, make sure the switchFuture is still not done
    Thread.sleep(5000);
    assertFalse(switchFuture.isDone());

    RestartFramework.at("after_second_sleep_verify_not_done")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // resume the add peer procedure and wait it done
    RESUME.countDown();

    RestartFramework.at("after_resume_latch_countdown")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    addFuture.get();

    RestartFramework.at("after_add_future_complete")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // this time the switchFuture should be able to finish
    assertTrue(switchFuture.get());

    RestartFramework.at("after_switch_future_complete")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
  }
}
