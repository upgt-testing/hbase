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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.Waiter;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * ProcessBased version of {@link TestShutdownWithNoRegionServer}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Testcase to confirm that we will not hang when shutdown a cluster with no live region servers.
 *
 * @see TestShutdownWithNoRegionServer Original test using MiniHBaseCluster
 */
@Category({ MasterTests.class, MediumTests.class })
public class TestShutdownWithNoRegionServer_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestShutdownWithNoRegionServer_ProcessBased.class);

  @Test
  public void test_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Get the ServerName of the only region server
    ClusterMetrics metrics = admin.getClusterMetrics();
    ServerName rsName = metrics.getLiveServerMetrics().keySet().iterator().next();

    // Stop the region server
    cluster.stopRegionServer(rsName);

    // Wait for the region server to be reported as dead
    Waiter.waitFor(conf, 30000, () -> {
      ClusterMetrics m = admin.getClusterMetrics();
      return m.getLiveServerMetrics().isEmpty();
    });

    checkpoint("AFTER_STOP_RS");

    // Cluster shutdown will be handled by @After in base class
    // This tests that shutdown doesn't hang with no live region servers
  }

  @Test
  public void test_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Get the ServerName of the only region server
    ClusterMetrics metrics = admin.getClusterMetrics();
    ServerName rsName = metrics.getLiveServerMetrics().keySet().iterator().next();

    // Stop the region server
    cluster.stopRegionServer(rsName);

    // Wait for the region server to be reported as dead
    Waiter.waitFor(conf, 30000, () -> {
      ClusterMetrics m = admin.getClusterMetrics();
      return m.getLiveServerMetrics().isEmpty();
    });

    checkpoint("AFTER_STOP_RS");

    // Cluster shutdown will be handled by @After in base class
    // This tests that shutdown doesn't hang with no live region servers
  }

  @Test
  public void test_AFTER_STOP_RS() throws Exception {
    upgradeCheckpoint = "AFTER_STOP_RS";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Get the ServerName of the only region server
    ClusterMetrics metrics = admin.getClusterMetrics();
    ServerName rsName = metrics.getLiveServerMetrics().keySet().iterator().next();

    // Stop the region server
    cluster.stopRegionServer(rsName);

    // Wait for the region server to be reported as dead
    Waiter.waitFor(conf, 30000, () -> {
      ClusterMetrics m = admin.getClusterMetrics();
      return m.getLiveServerMetrics().isEmpty();
    });

    checkpoint("AFTER_STOP_RS");

    // Cluster shutdown will be handled by @After in base class
    // This tests that shutdown doesn't hang with no live region servers
  }
}
