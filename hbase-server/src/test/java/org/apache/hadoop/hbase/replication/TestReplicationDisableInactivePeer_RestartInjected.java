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
package org.apache.hadoop.hbase.replication;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.testclassification.ReplicationTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Category({ ReplicationTests.class, LargeTests.class })
public class TestReplicationDisableInactivePeer_RestartInjected extends TestReplicationBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestReplicationDisableInactivePeer_RestartInjected.class);

  private static final Logger LOG =
    LoggerFactory.getLogger(TestReplicationDisableInactivePeer.class);

  /**
   * Test disabling an inactive peer. Add a peer which is inactive, trying to insert, disable the
   * peer, then activate the peer and make sure nothing is replicated. In Addition, enable the peer
   * and check the updates are replicated.
   */
  @Test
  public void testDisableInactivePeer() throws Exception {
    UTIL2.shutdownMiniHBaseCluster();

    RestartFramework.at("after_target_cluster_shutdown")
      .on(UTIL1.getMiniHBaseCluster())
      .restart("regionserver")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();

    byte[] rowkey = Bytes.toBytes("disable inactive peer");
    Put put = new Put(rowkey);
    put.addColumn(famName, row, row);
    htable1.put(put);

    RestartFramework.at("after_first_put")
      .on(UTIL1.getMiniHBaseCluster())
      .restart("regionserver")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();

    // wait for the sleep interval of the master cluster to become long
    Thread.sleep(SLEEP_TIME * NB_RETRIES);

    RestartFramework.at("after_sleep_interval")
      .on(UTIL1.getMiniHBaseCluster())
      .restart("regionserver")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();

    // disable and start the peer
    hbaseAdmin.disableReplicationPeer("2");

    RestartFramework.at("after_peer_disable")
      .on(UTIL1.getMiniHBaseCluster())
      .restart("master")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();

    restartTargetHBaseCluster(2);

    RestartFramework.at("after_target_cluster_restart")
      .on(UTIL1.getMiniHBaseCluster())
      .restart("regionserver")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();

    Get get = new Get(rowkey);
    for (int i = 0; i < NB_RETRIES; i++) {
      Result res = htable2.get(get);
      if (res.size() >= 1) {
        fail("Replication wasn't disabled");
      } else {
        LOG.info("Row not replicated, let's wait a bit more...");
        Thread.sleep(SLEEP_TIME);
      }
    }

    RestartFramework.at("after_replication_check")
      .on(UTIL1.getMiniHBaseCluster())
      .restart("regionserver")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();

    // Test enable replication
    admin.enablePeer("2");

    RestartFramework.at("after_peer_enable")
      .on(UTIL1.getMiniHBaseCluster())
      .restart("master")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();

    // wait since the sleep interval would be long
    Thread.sleep(SLEEP_TIME * NB_RETRIES);
    for (int i = 0; i < NB_RETRIES; i++) {
      Result res = htable2.get(get);
      if (res.isEmpty()) {
        LOG.info("Row not available");
        Thread.sleep(SLEEP_TIME * NB_RETRIES);
      } else {
        assertArrayEquals(row, res.value());
        return;
      }
    }
    fail("Waited too much time for put replication");
  }
}
