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

import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * ProcessBased version of {@link TestGetInfoPort}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Confirms that we do not get 0 infoPort when MASTER_INFO_PORT=0 (random port).
 * See HBASE-12863.
 *
 * @see TestGetInfoPort Original test using MiniHBaseCluster
 */
@Category({ MasterTests.class, MediumTests.class })
public class TestGetInfoPort_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestGetInfoPort_ProcessBased.class);

  @Test
  public void test_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTest();
  }

  @Test
  public void test_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTest();
  }

  private void runTest() throws Exception {
    conf.setInt(HConstants.MASTER_INFO_PORT, 0);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Verify master info port is non-zero via ClusterMetrics
    ClusterMetrics metrics = admin.getClusterMetrics();
    int masterInfoPort = metrics.getMasterInfoPort();
    assertTrue("Master info port should be non-zero when MASTER_INFO_PORT=0",
               masterInfoPort > 0);
  }
}
