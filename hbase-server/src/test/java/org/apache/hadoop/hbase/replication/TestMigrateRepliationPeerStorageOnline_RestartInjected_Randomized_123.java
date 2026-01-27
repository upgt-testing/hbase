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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.testclassification.ReplicationTests;
import org.apache.hadoop.hbase.util.JVMClusterUtil.MasterThread;
import org.apache.hadoop.hbase.util.JVMClusterUtil.RegionServerThread;
import org.apache.hadoop.util.ToolRunner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

@Category({ ReplicationTests.class, LargeTests.class })
public class TestMigrateRepliationPeerStorageOnline_RestartInjected_Randomized_123 {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestMigrateRepliationPeerStorageOnline_RestartInjected.class);

    private static final HBaseTestingUtility UTIL = new HBaseTestingUtility();

    @BeforeClass
    public static void setUp() throws Exception {
        // use zookeeper first, and then migrate to filesystem
        UTIL.getConfiguration().set(ReplicationStorageFactory.REPLICATION_PEER_STORAGE_IMPL, ReplicationPeerStorageType.ZOOKEEPER.name());
        UTIL.startMiniCluster(1);
    }

    @AfterClass
    public static void tearDown() throws IOException {
        UTIL.shutdownMiniCluster();
    }

    @Test
    public void testMigrate() throws Exception {
        RestartFramework.at("after_peer_modification_switch_enable").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_regionserver_config_updates").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Admin admin = UTIL.getAdmin();
        ReplicationPeerConfig rpc = ReplicationPeerConfig.newBuilder().setClusterKey(UTIL.getClusterKey() + "-test").setReplicationEndpointImpl(DummyReplicationEndpoint.class.getName()).build();
        admin.addReplicationPeer("1", rpc);
        // disable peer modification
        admin.replicationPeerModificationSwitch(false, true);
        RestartFramework.at("after_migration_tool_run").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_peer_modification_switch_disable").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        admin = UTIL.getAdmin();
        RestartFramework.at("after_peer_removal").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // migrate replication peer data
        Configuration conf = new Configuration(UTIL.getConfiguration());
        RestartFramework.at("after_peer_add").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(0, ToolRunner.run(conf, new CopyReplicationPeers(conf), new String[] { "zookeeper", "filesystem" }));
        conf.set(ReplicationStorageFactory.REPLICATION_PEER_STORAGE_IMPL, ReplicationPeerStorageType.FILESYSTEM.name());
        RestartFramework.at("after_master_config_updates").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // confirm that we have copied the data
        ReplicationPeerStorage fsPeerStorage = ReplicationStorageFactory.getReplicationPeerStorage(UTIL.getTestFileSystem(), UTIL.getZooKeeperWatcher(), conf);
        assertNotNull(fsPeerStorage.getPeerConfig("1"));
        for (MasterThread mt : UTIL.getMiniHBaseCluster().getMasterThreads()) {
            Configuration newConf = new Configuration(mt.getMaster().getConfiguration());
            newConf.set(ReplicationStorageFactory.REPLICATION_PEER_STORAGE_IMPL, ReplicationPeerStorageType.FILESYSTEM.name());
            mt.getMaster().getConfigurationManager().notifyAllObservers(newConf);
        }
        admin = UTIL.getAdmin();
        RestartFramework.at("after_peer_storage_creation").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        for (RegionServerThread rt : UTIL.getMiniHBaseCluster().getRegionServerThreads()) {
            Configuration newConf = new Configuration(rt.getRegionServer().getConfiguration());
            newConf.set(ReplicationStorageFactory.REPLICATION_PEER_STORAGE_IMPL, ReplicationPeerStorageType.FILESYSTEM.name());
            rt.getRegionServer().getConfigurationManager().notifyAllObservers(newConf);
        }
        admin.replicationPeerModificationSwitch(true);
        admin = UTIL.getAdmin();
        admin.removeReplicationPeer("1");
        // confirm that we will operation on the new peer storage
        assertThat(fsPeerStorage.listPeerIds(), empty());
    }
}
