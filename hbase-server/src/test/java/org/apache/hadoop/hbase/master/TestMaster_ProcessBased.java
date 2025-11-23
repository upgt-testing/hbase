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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.UnknownRegionException;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionInfoBuilder;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * ProcessBased version of {@link TestMaster}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Reduced version: Only includes testMoveThrowsUnknownRegionException which is fully
 * client-side. Other tests in TestMaster require internal HMaster state manipulation
 * (master.setInitialized(), master.getTableStateManager(), etc.) which are not
 * available via any client API.
 *
 * @see TestMaster Original test using MiniHBaseCluster
 */
@Category({ MasterTests.class, MediumTests.class })
public class TestMaster_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestMaster_ProcessBased.class);

  private static final byte[] FAMILYNAME = Bytes.toBytes("value");

  @Test
  public void testMoveThrowsUnknownRegionException_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTestMoveThrowsUnknownRegionException();
  }

  @Test
  public void testMoveThrowsUnknownRegionException_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTestMoveThrowsUnknownRegionException();
  }

  @Test
  public void testMoveThrowsUnknownRegionException_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    runTestMoveThrowsUnknownRegionException();
  }

  private void runTestMoveThrowsUnknownRegionException() throws Exception {

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(2)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf("testMoveThrowsUnknownRegionException");

    // Create table
    admin.createTable(
        TableDescriptorBuilder.newBuilder(tableName)
            .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILYNAME))
            .build());
    checkpoint("AFTER_CREATE_TABLE");

    try {
      // Create a fake region (doesn't actually exist in the table)
      RegionInfo fakeRegion = RegionInfoBuilder.newBuilder(tableName)
          .setStartKey(Bytes.toBytes("A"))
          .setEndKey(Bytes.toBytes("Z"))
          .build();

      // Try to move the fake region - should fail with UnknownRegionException
      admin.move(fakeRegion.getEncodedNameAsBytes());
      fail("Region should not be moved since it is fake");
    } catch (IOException ioe) {
      assertTrue("Expected UnknownRegionException but got: " + ioe.getClass().getName(),
                 ioe instanceof UnknownRegionException);
    } finally {
      // Clean up
      admin.disableTable(tableName);
      admin.deleteTable(tableName);
    }
  }
}
