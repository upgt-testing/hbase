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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.EnumSet;
import java.util.List;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.ClusterMetrics.Option;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.ReplicationTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

@Category({ ReplicationTests.class, MediumTests.class })
public class TestReplicationStatusSourceStartedTargetStoppedNewOp_RestartInjected_Randomized_456 extends TestReplicationBase {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestReplicationStatusSourceStartedTargetStoppedNewOp_RestartInjected.class);

    @Test
    public void testReplicationStatusSourceStartedTargetStoppedNewOp() throws Exception {
        UTIL2.shutdownMiniHBaseCluster();
        restartSourceCluster(1);
        RestartFramework.at("after_source_cluster_restart").on(UTIL1.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Admin hbaseAdmin = UTIL1.getAdmin();
        RestartFramework.at("after_get_metrics").on(UTIL1.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        hbaseAdmin = UTIL1.getAdmin();
        // add some values to source cluster
        for (int i = 0; i < NB_ROWS_IN_BATCH; i++) {
            Put p = new Put(Bytes.toBytes("row" + i));
            p.addColumn(famName, Bytes.toBytes("col1"), Bytes.toBytes("val" + i));
            htable1.put(p);
        }
        Thread.sleep(10000);
        ServerName serverName = UTIL1.getHBaseCluster().getRegionServer(0).getServerName();
        RestartFramework.at("after_sleep_wait_lag").on(UTIL1.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        ClusterMetrics metrics = hbaseAdmin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS));
        RestartFramework.at("after_batch_puts").on(UTIL1.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_get_admin").on(UTIL1.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        List<ReplicationLoadSource> loadSources = metrics.getLiveServerMetrics().get(serverName).getReplicationLoadSourceList();
        assertEquals(1, loadSources.size());
        ReplicationLoadSource loadSource = loadSources.get(0);
        assertTrue(loadSource.hasEditsSinceRestart());
        RestartFramework.at("after_target_cluster_shutdown").on(UTIL1.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(0, loadSource.getTimestampOfLastShippedOp());
        assertTrue(loadSource.getReplicationLag() > 0);
        assertFalse(loadSource.isRecovered());
    }
}
