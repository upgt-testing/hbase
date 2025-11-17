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
package org.apache.hadoop.hbase.upgrade;

import java.util.EnumSet;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.ClusterMetrics.Option;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple debug test class for verifying ProcessBasedMiniHBaseCluster creation.
 * This is a non-parameterized version for easier debugging.
 */
@Category(MediumTests.class)
public class TestProcessBasedClusterDebug extends ProcessBasedUpgradeTestBase {

  private static final Logger LOG = LoggerFactory.getLogger(TestProcessBasedClusterDebug.class);

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestProcessBasedClusterDebug.class);

  private static final int SLAVES = 3;
  private static final int MASTERS = 2;

  @Test(timeout = 300000)
  public void testClusterCreation() throws Exception {
    LOG.info("Starting testClusterCreation - creating ProcessBasedMiniHBaseCluster");

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(SLAVES)
        .numMasters(MASTERS)
        .build();

    LOG.info("Starting cluster...");
    cluster.startup();

    LOG.info("Getting connection...");
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    LOG.info("Waiting for cluster to be up...");
    cluster.waitClusterUp();

    LOG.info("Cluster is up! Verifying cluster metrics...");

    ClusterMetrics origin = admin.getClusterMetrics();
    ClusterMetrics defaults = admin.getClusterMetrics(EnumSet.allOf(Option.class));

    Assert.assertEquals(origin.getHBaseVersion(), defaults.getHBaseVersion());
    Assert.assertEquals(origin.getClusterId(), defaults.getClusterId());
    Assert.assertEquals(origin.getAverageLoad(), defaults.getAverageLoad(), 0);
    Assert.assertEquals(origin.getBackupMasterNames().size(),
      defaults.getBackupMasterNames().size());
    Assert.assertEquals(origin.getDeadServerNames().size(), defaults.getDeadServerNames().size());
    Assert.assertEquals(origin.getRegionCount(), defaults.getRegionCount());
    Assert.assertEquals(origin.getLiveServerMetrics().size(),
      defaults.getLiveServerMetrics().size());
    Assert.assertEquals(origin.getMasterInfoPort(), defaults.getMasterInfoPort());
    Assert.assertEquals(origin.getServersName().size(), defaults.getServersName().size());
    Assert.assertEquals(admin.getRegionServers().size(), defaults.getServersName().size());

    LOG.info("All assertions passed! Cluster creation verified successfully.");
    LOG.info("HBase Version: {}", origin.getHBaseVersion());
    LOG.info("Cluster ID: {}", origin.getClusterId());
    LOG.info("Live servers: {}", origin.getLiveServerMetrics().size());
    LOG.info("Backup masters: {}", origin.getBackupMasterNames().size());
  }
}
