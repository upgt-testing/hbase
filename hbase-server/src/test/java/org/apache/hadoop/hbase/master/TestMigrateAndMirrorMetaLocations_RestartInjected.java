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

import static org.apache.hadoop.hbase.shaded.protobuf.ProtobufUtil.lengthOfPBMagic;
import static org.apache.hadoop.hbase.zookeeper.ZKMetadata.removeMetaData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.MetaTableAccessor;
import org.apache.hadoop.hbase.RegionLocations;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.StartMiniClusterOption;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.master.procedure.ServerCrashProcedure;
import org.apache.hadoop.hbase.master.region.MasterRegion;
import org.apache.hadoop.hbase.procedure2.Procedure;
import org.apache.hadoop.hbase.regionserver.RegionScanner;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.zookeeper.ZKUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import org.apache.hadoop.hbase.shaded.protobuf.ProtobufUtil;
import org.apache.hadoop.hbase.shaded.protobuf.generated.ZooKeeperProtos;

/**
 * Testcase for HBASE-26193.
 */
@Category({ MasterTests.class, LargeTests.class })
public class TestMigrateAndMirrorMetaLocations_RestartInjected {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestMigrateAndMirrorMetaLocations_RestartInjected.class);

  private static final HBaseTestingUtility UTIL = new HBaseTestingUtility();

  @BeforeClass
  public static void setUp() throws Exception {
    UTIL.startMiniCluster(3);
    HBaseTestingUtility.setReplicas(UTIL.getAdmin(), TableName.META_TABLE_NAME, 2);
  }

  @AfterClass
  public static void tearDown() throws IOException {
    UTIL.shutdownMiniCluster();
  }

  private void assertLocationEquals(Result result, int replicaCount) throws Exception {
    RegionLocations locs = MetaTableAccessor.getRegionLocations(result);
    assertEquals(replicaCount, locs.size());
    for (int i = 0; i < replicaCount; i++) {
      String znode = UTIL.getZooKeeperWatcher().getZNodePaths().getZNodeForReplica(i);
      byte[] data = ZKUtil.getData(UTIL.getZooKeeperWatcher(), znode);
      data = removeMetaData(data);
      int prefixLen = lengthOfPBMagic();
      ZooKeeperProtos.MetaRegionServer zkProto = ZooKeeperProtos.MetaRegionServer.parser()
        .parseFrom(data, prefixLen, data.length - prefixLen);
      ServerName sn = ProtobufUtil.toServerName(zkProto.getServer());
      assertEquals(locs.getRegionLocation(i).getServerName(), sn);
    }
    assertEquals(replicaCount, UTIL.getZooKeeperWatcher().getMetaReplicaNodes().size());
  }

  private void checkMirrorLocation(int replicaCount) throws Exception {
    MasterRegion masterRegion = UTIL.getMiniHBaseCluster().getMaster().getMasterRegion();
    try (RegionScanner scanner =
      masterRegion.getRegionScanner(new Scan().addFamily(HConstants.CATALOG_FAMILY))) {
      List<Cell> cells = new ArrayList<>();
      boolean moreRows = scanner.next(cells);
      // should only have one row as we have only one meta region, different replicas will be in the
      // same row
      assertFalse(moreRows);
      assertFalse(cells.isEmpty());
      Result result = Result.create(cells);
      // make sure we publish the correct location to zookeeper too
      assertLocationEquals(result, replicaCount);
    }
  }

  private void waitUntilNoSCP() throws IOException {
    UTIL.waitFor(30000, () -> UTIL.getMiniHBaseCluster().getMaster().getProcedures().stream()
      .filter(p -> p instanceof ServerCrashProcedure).allMatch(Procedure::isSuccess));
  }

  @Test
  public void test() throws Exception {
    checkMirrorLocation(2);
    RestartFramework.at("after_check_initial_mirror_location")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    MasterRegion masterRegion = UTIL.getMiniHBaseCluster().getMaster().getMasterRegion();
    try (RegionScanner scanner =
      masterRegion.getRegionScanner(new Scan().addFamily(HConstants.CATALOG_FAMILY))) {
      List<Cell> cells = new ArrayList<>();
      scanner.next(cells);
      Cell cell = cells.get(0);
      // delete the only row
      masterRegion.update(
        r -> r.delete(new Delete(cell.getRowArray(), cell.getRowOffset(), cell.getRowLength())
          .addFamily(HConstants.CATALOG_FAMILY)));
      RestartFramework.at("after_delete_row")
          .on(UTIL.getMiniHBaseCluster())
          .restart("master")
          .withIndex(0)
          .withMode(RestartMode.GRACEFUL)
          .execute();
      masterRegion.flush(true);
    }
    RestartFramework.at("after_flush_master_region")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    // restart the whole cluster, to see if we can migrate the data on zookeeper to master local
    // region
    UTIL.shutdownMiniHBaseCluster();
    UTIL.startMiniHBaseCluster(StartMiniClusterOption.builder().numRegionServers(3).build());
    RestartFramework.at("after_first_cluster_restart")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    masterRegion = UTIL.getMiniHBaseCluster().getMaster().getMasterRegion();
    try (RegionScanner scanner =
      masterRegion.getRegionScanner(new Scan().addFamily(HConstants.CATALOG_FAMILY))) {
      List<Cell> cells = new ArrayList<>();
      boolean moreRows = scanner.next(cells);
      assertFalse(moreRows);
      // should have the migrated data
      assertFalse(cells.isEmpty());
    }
    RestartFramework.at("after_verify_migrated_data")
        .on(UTIL.getMiniHBaseCluster())
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    // wait until all meta regions have been assigned
    UTIL.waitFor(30000,
      () -> UTIL.getMiniHBaseCluster().getRegions(TableName.META_TABLE_NAME).size() == 2);
    RestartFramework.at("after_meta_regions_assigned")
        .on(UTIL.getMiniHBaseCluster())
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    // make sure all the SCPs are finished
    waitUntilNoSCP();
    RestartFramework.at("after_scps_finished")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    checkMirrorLocation(2);
    RestartFramework.at("after_check_mirror_location_2")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // increase replica count to 3
    HBaseTestingUtility.setReplicas(UTIL.getAdmin(), TableName.META_TABLE_NAME, 3);
    RestartFramework.at("after_increase_replica_to_3")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    checkMirrorLocation(3);
    RestartFramework.at("after_check_mirror_location_3")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    byte[] replica2Data = ZKUtil.getData(UTIL.getZooKeeperWatcher(),
      UTIL.getZooKeeperWatcher().getZNodePaths().getZNodeForReplica(2));
    RestartFramework.at("after_get_replica2_data")
        .on(UTIL.getMiniHBaseCluster())
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // decrease replica count to 1
    HBaseTestingUtility.setReplicas(UTIL.getAdmin(), TableName.META_TABLE_NAME, 1);
    RestartFramework.at("after_decrease_replica_to_1")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    checkMirrorLocation(1);
    RestartFramework.at("after_check_mirror_location_1")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // restart the whole cluster, put an extra replica znode on zookeeper, to see if we will remove
    // it
    UTIL.shutdownMiniHBaseCluster();
    ZKUtil.createAndFailSilent(UTIL.getZooKeeperWatcher(),
      UTIL.getZooKeeperWatcher().getZNodePaths().getZNodeForReplica(2), replica2Data);
    RestartFramework.at("after_create_extra_znode")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    UTIL.startMiniHBaseCluster(StartMiniClusterOption.builder().numRegionServers(3).build());
    RestartFramework.at("after_second_cluster_restart")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    // should have removed the extra replica znode as it is part of the start up process, when
    // initializing AM
    assertEquals(1, UTIL.getZooKeeperWatcher().getMetaReplicaNodes().size());
    RestartFramework.at("after_verify_znode_removed")
        .on(UTIL.getMiniHBaseCluster())
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    // make sure all the SCPs are finished
    waitUntilNoSCP();
    RestartFramework.at("after_final_scps_finished")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    checkMirrorLocation(1);
  }
}
