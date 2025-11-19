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
package org.apache.hadoop.hbase.coprocessor;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.CoprocessorEnvironment;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.CoprocessorTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.CommonFSUtils;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestCoprocessorStop}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Full transformation (100% logic preserved): Tests coprocessor stop() method
 * called during cluster shutdown. Verification via HDFS flag files fully client-side.
 * Replaced MiniHBaseCluster.shutdown()/waitUntilShutDown() with
 * ProcessBasedMiniHBaseCluster.shutdown().
 *
 * @see TestCoprocessorStop Original test using MiniHBaseCluster
 */
@Category({ CoprocessorTests.class, MediumTests.class })
public class TestCoprocessorStop_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestCoprocessorStop_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestCoprocessorStop_ProcessBased.class);
  private static final String MASTER_FILE = "master" + EnvironmentEdgeManager.currentTime();
  private static final String REGIONSERVER_FILE =
    "regionserver" + EnvironmentEdgeManager.currentTime();

  public static class FooCoprocessor implements MasterCoprocessor, RegionServerCoprocessor {
    @Override
    public void start(CoprocessorEnvironment env) throws IOException {
      String where = null;

      if (env instanceof MasterCoprocessorEnvironment) {
        // if running on HMaster
        where = "master";
      } else if (env instanceof RegionServerCoprocessorEnvironment) {
        where = "regionserver";
      } else if (env instanceof RegionCoprocessorEnvironment) {
        LOG.error("on RegionCoprocessorEnvironment!!");
      }
      LOG.info("start coprocessor on " + where);
    }

    @Override
    public void stop(CoprocessorEnvironment env) throws IOException {
      String fileName = null;

      if (env instanceof MasterCoprocessorEnvironment) {
        // if running on HMaster
        fileName = MASTER_FILE;
      } else if (env instanceof RegionServerCoprocessorEnvironment) {
        fileName = REGIONSERVER_FILE;
      } else if (env instanceof RegionCoprocessorEnvironment) {
        LOG.error("on RegionCoprocessorEnvironment!!");
      }

      Configuration conf = HBaseConfiguration.create();
      Path testDir = new Path(CommonFSUtils.getRootDir(conf).getParent(), "test-data");
      Path resultFile = new Path(testDir, fileName);
      FileSystem fs = FileSystem.get(conf);

      boolean result = fs.createNewFile(resultFile);
      LOG.info("create file " + resultFile + " return rc " + result);
    }
  }

  @Test
  public void testStopped_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    conf = HBaseConfiguration.create();
    conf.set(CoprocessorHost.MASTER_COPROCESSOR_CONF_KEY, FooCoprocessor.class.getName());
    conf.set(CoprocessorHost.REGIONSERVER_COPROCESSOR_CONF_KEY, FooCoprocessor.class.getName());

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // shutdown hbase only. then check flag file.
    LOG.info("shutdown hbase cluster...");
    connection.close();
    cluster.shutdown();
    checkpoint("AFTER_SHUTDOWN");

    FileSystem fs = FileSystem.get(conf);
    Path testDir = new Path(CommonFSUtils.getRootDir(conf).getParent(), "test-data");

    Path resultFile = new Path(testDir, MASTER_FILE);
    assertTrue("Master flag file should have been created", fs.exists(resultFile));

    resultFile = new Path(testDir, REGIONSERVER_FILE);
    assertTrue("RegionServer flag file should have been created", fs.exists(resultFile));
  }

  @Test
  public void testStopped_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testStopped_NO_UPGRADE();
  }
}
