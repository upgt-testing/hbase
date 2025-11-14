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
package org.apache.hadoop.hbase.process.integration;

import static org.junit.Assert.*;

import java.io.File;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.process.HBaseDistribution;
import org.apache.hadoop.hbase.process.HBaseVersionRegistry;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test to validate that HBase distributions are properly set up.
 * Run this first to verify your test environment is correctly configured.
 */
@Category(MediumTests.class)
public class TestDistributionValidation {
  private static final Logger LOG = LoggerFactory.getLogger(TestDistributionValidation.class);

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
      HBaseClassTestRule.forClass(TestDistributionValidation.class);

  private static final String DIST_DIR = "/Users/allenwang/xlab/hbase-test-distributions";

  @Test
  public void testHBase_2_5_11_Distribution() throws Exception {
    String hbaseHome = DIST_DIR + "/hbase-2.5.11";
    LOG.info("Testing HBase 2.5.11 distribution: {}", hbaseHome);

    HBaseDistribution dist = new HBaseDistribution("2.5.11", hbaseHome);
    dist.validate();

    LOG.info("Distribution validated: {}", dist);
    LOG.info("Core JARs: {}", dist.getCoreJars().size());
    LOG.info("Dependencies: {}", dist.getDependencies().size());

    assertTrue("Should have core JARs", dist.getCoreJars().size() > 0);
    assertTrue("Should have dependencies", dist.getDependencies().size() > 0);

    // Check for critical JARs
    boolean hasServer = false;
    boolean hasCommon = false;
    boolean hasClient = false;

    for (File jar : dist.getCoreJars()) {
      String name = jar.getName();
      LOG.debug("Core JAR: {}", name);
      if (name.startsWith("hbase-server-")) hasServer = true;
      if (name.startsWith("hbase-common-")) hasCommon = true;
      if (name.startsWith("hbase-client-")) hasClient = true;
    }

    assertTrue("Should have hbase-server JAR", hasServer);
    assertTrue("Should have hbase-common JAR", hasCommon);
    assertTrue("Should have hbase-client JAR", hasClient);

    LOG.info("HBase 2.5.11 distribution test PASSED");
  }

  @Test
  public void testHBase_2_6_2_Distribution() throws Exception {
    String hbaseHome = DIST_DIR + "/hbase-2.6.2";
    LOG.info("Testing HBase 2.6.2 distribution: {}", hbaseHome);

    HBaseDistribution dist = new HBaseDistribution("2.6.2", hbaseHome);
    dist.validate();

    LOG.info("Distribution validated: {}", dist);
    LOG.info("Core JARs: {}", dist.getCoreJars().size());
    LOG.info("Dependencies: {}", dist.getDependencies().size());

    assertTrue("Should have core JARs", dist.getCoreJars().size() > 0);
    assertTrue("Should have dependencies", dist.getDependencies().size() > 0);

    LOG.info("HBase 2.6.2 distribution test PASSED");
  }

  @Test
  public void testVersionRegistry() throws Exception {
    LOG.info("Testing HBaseVersionRegistry with multiple versions");

    HBaseVersionRegistry registry = new HBaseVersionRegistry();

    // Register multiple versions
    registry.register("2.5.11", DIST_DIR + "/hbase-2.5.11");
    registry.register("2.6.2", DIST_DIR + "/hbase-2.6.2");

    assertEquals(2, registry.size());
    assertTrue(registry.isRegistered("2.5.11"));
    assertTrue(registry.isRegistered("2.6.2"));

    // Test retrieval
    HBaseDistribution dist1 = registry.get("2.5.11");
    assertNotNull(dist1);
    assertEquals("2.5.11", dist1.getVersion());

    HBaseDistribution dist2 = registry.get("2.6.2");
    assertNotNull(dist2);
    assertEquals("2.6.2", dist2.getVersion());

    LOG.info("Version registry test PASSED");
  }

  @Test
  public void testAllAvailableDistributions() throws Exception {
    LOG.info("Scanning for all available HBase distributions in: {}", DIST_DIR);

    File distDir = new File(DIST_DIR);
    if (!distDir.exists()) {
      LOG.warn("Distribution directory not found: {}", DIST_DIR);
      return;
    }

    File[] subdirs = distDir.listFiles(File::isDirectory);
    if (subdirs == null || subdirs.length == 0) {
      LOG.warn("No subdirectories found in: {}", DIST_DIR);
      return;
    }

    int validDistributions = 0;
    for (File dir : subdirs) {
      if (dir.getName().startsWith("hbase-")) {
        String version = dir.getName().substring("hbase-".length());
        LOG.info("Found distribution: {} at {}", version, dir.getPath());

        try {
          HBaseDistribution dist = new HBaseDistribution(version, dir.getAbsolutePath());
          dist.validate();
          validDistributions++;
          LOG.info("  ✓ Valid: {} core JARs, {} dependencies",
              dist.getCoreJars().size(), dist.getDependencies().size());
        } catch (Exception e) {
          LOG.warn("  ✗ Invalid: {}", e.getMessage());
        }
      }
    }

    LOG.info("Found {} valid HBase distributions", validDistributions);
    assertTrue("Should have at least one valid distribution", validDistributions > 0);
  }
}
