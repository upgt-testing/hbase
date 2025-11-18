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
package org.apache.hadoop.hbase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * ProcessBased version of {@link TestClusterPortAssignment}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Note: ProcessBasedMiniHBaseCluster manages ports automatically, so we cannot
 * test forced custom port assignment. Instead, we verify that:
 * 1. Ports are valid and assigned correctly
 * 2. Ports are unique across nodes
 * 3. Ports are stable across queries (identity preservation)
 *
 * @see TestClusterPortAssignment Original test using MiniHBaseCluster
 */
@Category(MediumTests.class)
public class TestClusterPortAssignment_ProcessBased extends ProcessBasedUpgradeTestBase {
  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestClusterPortAssignment_ProcessBased.class);

  /**
   * Check that ProcessBasedMiniHBaseCluster properly assigns valid, unique, and stable ports.
   *
   * TRANSFORMATION NOTE: The original test forced specific ports via configuration and then
   * verified those exact ports were used by accessing internal RPC server objects. In
   * ProcessBasedMiniHBaseCluster, we cannot access internal server objects. Instead, we verify
   * that ProcessBased's automatic port management works correctly:
   * - Master and RS have valid RPC ports (accessible via ClusterMetrics)
   * - All ports are unique (no conflicts)
   * - Ports are stable across queries (identity preservation)
   *
   * Info server ports cannot be verified directly as they are internal to the process.
   */
  @Test(timeout = 300000)
  public void testClusterPortAssignment_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Verify Master port assignment
    ServerName masterName = admin.getClusterMetrics().getMasterName();
    assertNotNull("Master should have valid ServerName", masterName);
    assertTrue("Master RPC port should be valid (> 0)", masterName.getPort() > 0);
    assertTrue("Master RPC port should be in valid range (< 65536)", masterName.getPort() < 65536);
    checkpoint("AFTER_VERIFY_MASTER_PORT");

    // Verify RS port assignments are valid and unique
    Set<Integer> usedPorts = new HashSet<>();
    usedPorts.add(masterName.getPort());

    for (ServerName rsName : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
      assertTrue("RS RPC port should be valid (> 0)", rsName.getPort() > 0);
      assertTrue("RS RPC port should be in valid range (< 65536)", rsName.getPort() < 65536);
      assertNotEquals("RS port should differ from master port",
          masterName.getPort(), rsName.getPort());

      // Verify port uniqueness
      assertTrue("RS port " + rsName.getPort() + " should be unique (not already used by "
          + "another RS)", !usedPorts.contains(rsName.getPort()) ||
          usedPorts.add(rsName.getPort()));
      usedPorts.add(rsName.getPort());
    }
    checkpoint("AFTER_VERIFY_RS_PORTS");

    // Verify port stability (identity preservation)
    checkpoint("BEFORE_STABILITY_CHECK");
    ServerName masterName2 = admin.getClusterMetrics().getMasterName();
    assertEquals("Master port should be stable across queries",
        masterName.getPort(), masterName2.getPort());
    assertEquals("Master hostname should be stable across queries",
        masterName.getHostname(), masterName2.getHostname());

    // Verify all RS ports are stable
    Set<Integer> rsPorts2 = new HashSet<>();
    for (ServerName rsName : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
      rsPorts2.add(rsName.getPort());
    }
    // Verify same set of RS ports (minus master port)
    usedPorts.remove(masterName.getPort());
    assertEquals("RS ports should be stable across queries", usedPorts, rsPorts2);
  }

  @Test(timeout = 300000)
  public void testClusterPortAssignment_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Verify Master port assignment
    ServerName masterName = admin.getClusterMetrics().getMasterName();
    assertNotNull("Master should have valid ServerName", masterName);
    assertTrue("Master RPC port should be valid (> 0)", masterName.getPort() > 0);
    assertTrue("Master RPC port should be in valid range (< 65536)", masterName.getPort() < 65536);
    checkpoint("AFTER_VERIFY_MASTER_PORT");

    // Verify RS port assignments are valid and unique
    Set<Integer> usedPorts = new HashSet<>();
    usedPorts.add(masterName.getPort());

    for (ServerName rsName : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
      assertTrue("RS RPC port should be valid (> 0)", rsName.getPort() > 0);
      assertTrue("RS RPC port should be in valid range (< 65536)", rsName.getPort() < 65536);
      assertNotEquals("RS port should differ from master port",
          masterName.getPort(), rsName.getPort());

      // Verify port uniqueness
      assertTrue("RS port " + rsName.getPort() + " should be unique (not already used by "
          + "another RS)", !usedPorts.contains(rsName.getPort()) ||
          usedPorts.add(rsName.getPort()));
      usedPorts.add(rsName.getPort());
    }
    checkpoint("AFTER_VERIFY_RS_PORTS");

    // Verify port stability (identity preservation)
    checkpoint("BEFORE_STABILITY_CHECK");
    ServerName masterName2 = admin.getClusterMetrics().getMasterName();
    assertEquals("Master port should be stable across queries",
        masterName.getPort(), masterName2.getPort());
    assertEquals("Master hostname should be stable across queries",
        masterName.getHostname(), masterName2.getHostname());

    // Verify all RS ports are stable
    Set<Integer> rsPorts2 = new HashSet<>();
    for (ServerName rsName : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
      rsPorts2.add(rsName.getPort());
    }
    // Verify same set of RS ports (minus master port)
    usedPorts.remove(masterName.getPort());
    assertEquals("RS ports should be stable across queries", usedPorts, rsPorts2);
  }

  @Test(timeout = 300000)
  public void testClusterPortAssignment_AFTER_VERIFY_MASTER_PORT() throws Exception {
    upgradeCheckpoint = "AFTER_VERIFY_MASTER_PORT";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Verify Master port assignment
    ServerName masterName = admin.getClusterMetrics().getMasterName();
    assertNotNull("Master should have valid ServerName", masterName);
    assertTrue("Master RPC port should be valid (> 0)", masterName.getPort() > 0);
    assertTrue("Master RPC port should be in valid range (< 65536)", masterName.getPort() < 65536);
    checkpoint("AFTER_VERIFY_MASTER_PORT");

    // Verify RS port assignments are valid and unique
    Set<Integer> usedPorts = new HashSet<>();
    usedPorts.add(masterName.getPort());

    for (ServerName rsName : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
      assertTrue("RS RPC port should be valid (> 0)", rsName.getPort() > 0);
      assertTrue("RS RPC port should be in valid range (< 65536)", rsName.getPort() < 65536);
      assertNotEquals("RS port should differ from master port",
          masterName.getPort(), rsName.getPort());

      // Verify port uniqueness
      assertTrue("RS port " + rsName.getPort() + " should be unique (not already used by "
          + "another RS)", !usedPorts.contains(rsName.getPort()) ||
          usedPorts.add(rsName.getPort()));
      usedPorts.add(rsName.getPort());
    }
    checkpoint("AFTER_VERIFY_RS_PORTS");

    // Verify port stability (identity preservation)
    checkpoint("BEFORE_STABILITY_CHECK");
    ServerName masterName2 = admin.getClusterMetrics().getMasterName();
    assertEquals("Master port should be stable across queries",
        masterName.getPort(), masterName2.getPort());
    assertEquals("Master hostname should be stable across queries",
        masterName.getHostname(), masterName2.getHostname());

    // Verify all RS ports are stable
    Set<Integer> rsPorts2 = new HashSet<>();
    for (ServerName rsName : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
      rsPorts2.add(rsName.getPort());
    }
    // Verify same set of RS ports (minus master port)
    usedPorts.remove(masterName.getPort());
    assertEquals("RS ports should be stable across queries", usedPorts, rsPorts2);
  }

  @Test(timeout = 300000)
  public void testClusterPortAssignment_AFTER_VERIFY_RS_PORTS() throws Exception {
    upgradeCheckpoint = "AFTER_VERIFY_RS_PORTS";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Verify Master port assignment
    ServerName masterName = admin.getClusterMetrics().getMasterName();
    assertNotNull("Master should have valid ServerName", masterName);
    assertTrue("Master RPC port should be valid (> 0)", masterName.getPort() > 0);
    assertTrue("Master RPC port should be in valid range (< 65536)", masterName.getPort() < 65536);
    checkpoint("AFTER_VERIFY_MASTER_PORT");

    // Verify RS port assignments are valid and unique
    Set<Integer> usedPorts = new HashSet<>();
    usedPorts.add(masterName.getPort());

    for (ServerName rsName : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
      assertTrue("RS RPC port should be valid (> 0)", rsName.getPort() > 0);
      assertTrue("RS RPC port should be in valid range (< 65536)", rsName.getPort() < 65536);
      assertNotEquals("RS port should differ from master port",
          masterName.getPort(), rsName.getPort());

      // Verify port uniqueness
      assertTrue("RS port " + rsName.getPort() + " should be unique (not already used by "
          + "another RS)", !usedPorts.contains(rsName.getPort()) ||
          usedPorts.add(rsName.getPort()));
      usedPorts.add(rsName.getPort());
    }
    checkpoint("AFTER_VERIFY_RS_PORTS");

    // Verify port stability (identity preservation)
    checkpoint("BEFORE_STABILITY_CHECK");
    ServerName masterName2 = admin.getClusterMetrics().getMasterName();
    assertEquals("Master port should be stable across queries",
        masterName.getPort(), masterName2.getPort());
    assertEquals("Master hostname should be stable across queries",
        masterName.getHostname(), masterName2.getHostname());

    // Verify all RS ports are stable
    Set<Integer> rsPorts2 = new HashSet<>();
    for (ServerName rsName : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
      rsPorts2.add(rsName.getPort());
    }
    // Verify same set of RS ports (minus master port)
    usedPorts.remove(masterName.getPort());
    assertEquals("RS ports should be stable across queries", usedPorts, rsPorts2);
  }

  @Test(timeout = 300000)
  public void testClusterPortAssignment_BEFORE_STABILITY_CHECK() throws Exception {
    upgradeCheckpoint = "BEFORE_STABILITY_CHECK";
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .numMasters(1)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Verify Master port assignment
    ServerName masterName = admin.getClusterMetrics().getMasterName();
    assertNotNull("Master should have valid ServerName", masterName);
    assertTrue("Master RPC port should be valid (> 0)", masterName.getPort() > 0);
    assertTrue("Master RPC port should be in valid range (< 65536)", masterName.getPort() < 65536);
    checkpoint("AFTER_VERIFY_MASTER_PORT");

    // Verify RS port assignments are valid and unique
    Set<Integer> usedPorts = new HashSet<>();
    usedPorts.add(masterName.getPort());

    for (ServerName rsName : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
      assertTrue("RS RPC port should be valid (> 0)", rsName.getPort() > 0);
      assertTrue("RS RPC port should be in valid range (< 65536)", rsName.getPort() < 65536);
      assertNotEquals("RS port should differ from master port",
          masterName.getPort(), rsName.getPort());

      // Verify port uniqueness
      assertTrue("RS port " + rsName.getPort() + " should be unique (not already used by "
          + "another RS)", !usedPorts.contains(rsName.getPort()) ||
          usedPorts.add(rsName.getPort()));
      usedPorts.add(rsName.getPort());
    }
    checkpoint("AFTER_VERIFY_RS_PORTS");

    // Verify port stability (identity preservation)
    checkpoint("BEFORE_STABILITY_CHECK");
    ServerName masterName2 = admin.getClusterMetrics().getMasterName();
    assertEquals("Master port should be stable across queries",
        masterName.getPort(), masterName2.getPort());
    assertEquals("Master hostname should be stable across queries",
        masterName.getHostname(), masterName2.getHostname());

    // Verify all RS ports are stable
    Set<Integer> rsPorts2 = new HashSet<>();
    for (ServerName rsName : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
      rsPorts2.add(rsName.getPort());
    }
    // Verify same set of RS ports (minus master port)
    usedPorts.remove(masterName.getPort());
    assertEquals("RS ports should be stable across queries", usedPorts, rsPorts2);
  }
}
