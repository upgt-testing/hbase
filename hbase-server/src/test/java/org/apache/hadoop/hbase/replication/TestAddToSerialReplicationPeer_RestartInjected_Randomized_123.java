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

import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.util.Collections;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.Waiter.ExplainingPredicate;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableState;
import org.apache.hadoop.hbase.master.TableStateManager;
import org.apache.hadoop.hbase.regionserver.HRegionServer;
import org.apache.hadoop.hbase.regionserver.wal.AbstractFSWAL;
import org.apache.hadoop.hbase.replication.regionserver.Replication;
import org.apache.hadoop.hbase.replication.regionserver.ReplicationSourceManager;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.testclassification.ReplicationTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.CommonFSUtils.StreamLacksCapabilityException;
import org.apache.hadoop.hbase.wal.AbstractFSWALProvider;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import org.apache.hbase.thirdparty.com.google.common.collect.ImmutableMap;

/**
 * Testcase for HBASE-20147.
 */
@Category({ ReplicationTests.class, LargeTests.class })
public class TestAddToSerialReplicationPeer_RestartInjected_Randomized_123 extends SerialReplicationTestBase {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestAddToSerialReplicationPeer_RestartInjected.class);

    @Before
    public void setUp() throws IOException, StreamLacksCapabilityException {
        setupWALWriter();
    }

    // make sure that we will start replication for the sequence id after move, that's what we want to
    // test here.
    private void moveRegionAndArchiveOldWals(RegionInfo region, HRegionServer rs) throws Exception {
        moveRegion(region, rs);
        rollAllWALs();
    }

    private void waitUntilReplicatedToTheCurrentWALFile(HRegionServer rs, final String oldWalName) throws Exception {
        Path path = ((AbstractFSWAL<?>) rs.getWAL(null)).getCurrentFileName();
        String logPrefix = AbstractFSWALProvider.getWALPrefixFromWALName(path.getName());
        UTIL.waitFor(30000, new ExplainingPredicate<Exception>() {

            @Override
            public boolean evaluate() throws Exception {
                ReplicationSourceManager manager = ((Replication) rs.getReplicationSourceService()).getReplicationManager();
                // Make sure replication moves to the new file.
                return (manager.getWALs().get(PEER_ID).get(logPrefix).size() == 1) && !oldWalName.equals(manager.getWALs().get(PEER_ID).get(logPrefix).first());
            }

            @Override
            public String explainFailure() throws Exception {
                return "Still not replicated to the current WAL file yet";
            }
        });
    }

    @Test
    public void testAddPeer() throws Exception {
        RestartFramework.at("after_second_batch_puts").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        TableName tableName = createTable();
        try (Table table = UTIL.getConnection().getTable(tableName)) {
            for (int i = 0; i < 100; i++) {
                table.put(new Put(Bytes.toBytes(i)).addColumn(CF, CQ, Bytes.toBytes(i)));
            }
        }
        RegionInfo region = UTIL.getAdmin().getRegions(tableName).get(0);
        RestartFramework.at("after_region_move_and_wal_archive").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_first_batch_puts").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_peer_add").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        HRegionServer rs = UTIL.getOtherRegionServer(UTIL.getRSForFirstRegionInTable(tableName));
        moveRegionAndArchiveOldWals(region, rs);
        addPeer(true);
        RestartFramework.at("after_table_create").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        try (Table table = UTIL.getConnection().getTable(tableName)) {
            for (int i = 0; i < 100; i++) {
                table.put(new Put(Bytes.toBytes(i)).addColumn(CF, CQ, Bytes.toBytes(i)));
            }
        }
        waitUntilReplicationDone(100);
        checkOrder(100);
    }

    @Test
    public void testChangeToSerial() throws Exception {
        ReplicationPeerConfig peerConfig = ReplicationPeerConfig.newBuilder().setClusterKey("127.0.0.1:2181:/hbase").setReplicationEndpointImpl(LocalReplicationEndpoint.class.getName()).build();
        UTIL.getAdmin().addReplicationPeer(PEER_ID, peerConfig, true);
        TableName tableName = createTable();
        try (Table table = UTIL.getConnection().getTable(tableName)) {
            for (int i = 0; i < 100; i++) {
                table.put(new Put(Bytes.toBytes(i)).addColumn(CF, CQ, Bytes.toBytes(i)));
            }
        }
        RegionInfo region = UTIL.getAdmin().getRegions(tableName).get(0);
        RestartFramework.at("after_peer_add").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        HRegionServer srcRs = UTIL.getRSForFirstRegionInTable(tableName);
        // Get the current wal file name
        String walFileNameBeforeRollover = ((AbstractFSWAL<?>) srcRs.getWAL(null)).getCurrentFileName().getName();
        HRegionServer rs = UTIL.getOtherRegionServer(srcRs);
        moveRegionAndArchiveOldWals(region, rs);
        RestartFramework.at("after_replication_to_current_wal").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_first_batch_puts").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        waitUntilReplicationDone(100);
        waitUntilReplicatedToTheCurrentWALFile(srcRs, walFileNameBeforeRollover);
        UTIL.getAdmin().disableReplicationPeer(PEER_ID);
        RestartFramework.at("after_region_move_and_wal_archive").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_second_batch_puts").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_peer_config_update").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        UTIL.getAdmin().updateReplicationPeerConfig(PEER_ID, ReplicationPeerConfig.newBuilder(peerConfig).setSerial(true).build());
        UTIL.getAdmin().enableReplicationPeer(PEER_ID);
        try (Table table = UTIL.getConnection().getTable(tableName)) {
            for (int i = 0; i < 100; i++) {
                table.put(new Put(Bytes.toBytes(i)).addColumn(CF, CQ, Bytes.toBytes(i)));
            }
        }
        waitUntilReplicationDone(200);
        RestartFramework.at("after_table_create").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        checkOrder(200);
    }

    @Test
    public void testAddToSerialPeer() throws Exception {
        ReplicationPeerConfig peerConfig = ReplicationPeerConfig.newBuilder().setClusterKey("127.0.0.1:2181:/hbase").setReplicationEndpointImpl(LocalReplicationEndpoint.class.getName()).setReplicateAllUserTables(false).setSerial(true).build();
        UTIL.getAdmin().addReplicationPeer(PEER_ID, peerConfig, true);
        TableName tableName = createTable();
        try (Table table = UTIL.getConnection().getTable(tableName)) {
            for (int i = 0; i < 100; i++) {
                table.put(new Put(Bytes.toBytes(i)).addColumn(CF, CQ, Bytes.toBytes(i)));
            }
        }
        RegionInfo region = UTIL.getAdmin().getRegions(tableName).get(0);
        RestartFramework.at("after_first_batch_puts").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        HRegionServer srcRs = UTIL.getRSForFirstRegionInTable(tableName);
        HRegionServer rs = UTIL.getOtherRegionServer(srcRs);
        // Get the current wal file name
        String walFileNameBeforeRollover = ((AbstractFSWAL<?>) srcRs.getWAL(null)).getCurrentFileName().getName();
        RestartFramework.at("after_second_batch_puts").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        moveRegionAndArchiveOldWals(region, rs);
        srcRs = UTIL.getRSForFirstRegionInTable(tableName);
        // Make sure that the replication done for the oldWal at source rs.
        waitUntilReplicatedToTheCurrentWALFile(srcRs, walFileNameBeforeRollover);
        RestartFramework.at("after_replication_to_current_wal").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        srcRs = UTIL.getRSForFirstRegionInTable(tableName);
        RestartFramework.at("after_region_move_and_wal_archive").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        UTIL.getAdmin().disableReplicationPeer(PEER_ID);
        UTIL.getAdmin().updateReplicationPeerConfig(PEER_ID, ReplicationPeerConfig.newBuilder(peerConfig).setTableCFsMap(ImmutableMap.of(tableName, Collections.emptyList())).build());
        UTIL.getAdmin().enableReplicationPeer(PEER_ID);
        srcRs = UTIL.getRSForFirstRegionInTable(tableName);
        RestartFramework.at("after_peer_add").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        try (Table table = UTIL.getConnection().getTable(tableName)) {
            for (int i = 0; i < 100; i++) {
                table.put(new Put(Bytes.toBytes(i)).addColumn(CF, CQ, Bytes.toBytes(i)));
            }
        }
        RestartFramework.at("after_table_create").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        srcRs = UTIL.getRSForFirstRegionInTable(tableName);
        waitUntilReplicationDone(100);
        RestartFramework.at("after_peer_config_update").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        checkOrder(100);
    }

    @Test
    public void testDisabledTable() throws Exception {
        TableName tableName = createTable();
        RestartFramework.at("after_table_enable").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        try (Table table = UTIL.getConnection().getTable(tableName)) {
            for (int i = 0; i < 100; i++) {
                table.put(new Put(Bytes.toBytes(i)).addColumn(CF, CQ, Bytes.toBytes(i)));
            }
        }
        RestartFramework.at("after_first_batch_puts").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        UTIL.getAdmin().disableTable(tableName);
        RestartFramework.at("after_table_disable").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_second_batch_puts").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_table_create").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        rollAllWALs();
        addPeer(true);
        UTIL.getAdmin().enableTable(tableName);
        RestartFramework.at("after_peer_add").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        try (Table table = UTIL.getConnection().getTable(tableName)) {
            for (int i = 0; i < 100; i++) {
                table.put(new Put(Bytes.toBytes(i)).addColumn(CF, CQ, Bytes.toBytes(i)));
            }
        }
        waitUntilReplicationDone(100);
        checkOrder(100);
    }

    @Test
    public void testDisablingTable() throws Exception {
        TableName tableName = createTable();
        RestartFramework.at("after_thread_alive_check").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        try (Table table = UTIL.getConnection().getTable(tableName)) {
            for (int i = 0; i < 100; i++) {
                table.put(new Put(Bytes.toBytes(i)).addColumn(CF, CQ, Bytes.toBytes(i)));
            }
        }
        RestartFramework.at("after_table_state_set_to_disabling").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_thread_join").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        UTIL.getAdmin().disableTable(tableName);
        RestartFramework.at("after_second_batch_puts").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        rollAllWALs();
        TableStateManager tsm = UTIL.getMiniHBaseCluster().getMaster().getTableStateManager();
        tsm.setTableState(tableName, TableState.State.DISABLING);
        RestartFramework.at("after_table_disable").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Thread t = new Thread(() -> {
            try {
                addPeer(true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        Thread.sleep(5000);
        // we will wait on the disabling table so the thread should still be alive.
        assertTrue(t.isAlive());
        RestartFramework.at("after_table_enable").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        tsm.setTableState(tableName, TableState.State.DISABLED);
        RestartFramework.at("after_table_create").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        t.join();
        RestartFramework.at("after_first_batch_puts").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        UTIL.getAdmin().enableTable(tableName);
        try (Table table = UTIL.getConnection().getTable(tableName)) {
            for (int i = 0; i < 100; i++) {
                table.put(new Put(Bytes.toBytes(i)).addColumn(CF, CQ, Bytes.toBytes(i)));
            }
        }
        waitUntilReplicationDone(100);
        checkOrder(100);
    }

    @Test
    public void testEnablingTable() throws Exception {
        RestartFramework.at("after_thread_alive_check").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        TableName tableName = createTable();
        RestartFramework.at("after_table_state_set_to_enabling").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_thread_join").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        try (Table table = UTIL.getConnection().getTable(tableName)) {
            for (int i = 0; i < 100; i++) {
                table.put(new Put(Bytes.toBytes(i)).addColumn(CF, CQ, Bytes.toBytes(i)));
            }
        }
        RegionInfo region = UTIL.getAdmin().getRegions(tableName).get(0);
        HRegionServer rs = UTIL.getOtherRegionServer(UTIL.getRSForFirstRegionInTable(tableName));
        RestartFramework.at("after_region_move_and_wal_archive").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_first_batch_puts").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        moveRegionAndArchiveOldWals(region, rs);
        RestartFramework.at("after_table_create").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_second_batch_puts").on(UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        TableStateManager tsm = UTIL.getMiniHBaseCluster().getMaster().getTableStateManager();
        tsm.setTableState(tableName, TableState.State.ENABLING);
        tsm = UTIL.getMiniHBaseCluster().getMaster().getTableStateManager();
        Thread t = new Thread(() -> {
            try {
                addPeer(true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        Thread.sleep(5000);
        // we will wait on the disabling table so the thread should still be alive.
        assertTrue(t.isAlive());
        tsm.setTableState(tableName, TableState.State.ENABLED);
        t.join();
        tsm = UTIL.getMiniHBaseCluster().getMaster().getTableStateManager();
        try (Table table = UTIL.getConnection().getTable(tableName)) {
            for (int i = 0; i < 100; i++) {
                table.put(new Put(Bytes.toBytes(i)).addColumn(CF, CQ, Bytes.toBytes(i)));
            }
        }
        waitUntilReplicationDone(100);
        checkOrder(100);
    }
}
