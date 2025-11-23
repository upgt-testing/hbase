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
package org.apache.hadoop.hbase.regionserver;

import static org.junit.Assert.assertEquals;

import java.net.InetAddress;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.RegionServerTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestRegionServerUseIp}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * @see TestRegionServerUseIp Original test using MiniHBaseCluster
 */
@Category({ RegionServerTests.class, MediumTests.class })
public class TestRegionServerUseIp_ProcessBased extends ProcessBasedUpgradeTestBase {
  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestRegionServerUseIp_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestRegionServerUseIp_ProcessBased.class);

  private static final int NUM_RS = 1;

  @Test
  public void testRegionServerUseIp_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    runTestRegionServerUseIp();
  }

  @Test
  public void testRegionServerUseIp_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    runTestRegionServerUseIp();
  }

  private void runTestRegionServerUseIp() throws Exception {
    conf.setBoolean(HConstants.HBASE_SERVER_USEIP_ENABLED_KEY, true);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(NUM_RS)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Get RegionServer ServerName via ClusterMetrics (client API)
    ServerName rsServerName = admin.getClusterMetrics()
      .getLiveServerMetrics().keySet().iterator().next();
    String hostname = rsServerName.getHostname();
    String ip = InetAddress.getByName(hostname).getHostAddress();
    LOG.info("hostname= " + hostname + " ,ip=" + ip);
    assertEquals(ip, hostname);
  }
}
