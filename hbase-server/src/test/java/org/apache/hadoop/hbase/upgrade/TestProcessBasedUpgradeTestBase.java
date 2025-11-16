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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests for {@link ProcessBasedUpgradeTestBase} base class functionality.
 * This test verifies the core logic of the base class without requiring
 * a full cluster setup.
 */
@Category(MediumTests.class)
@RunWith(Parameterized.class)
public class TestProcessBasedUpgradeTestBase extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
      HBaseClassTestRule.forClass(TestProcessBasedUpgradeTestBase.class);

  @Parameter
  public String upgradeCheckpoint;

  @Parameters(name = "upgrade-at={0}")
  public static Collection<String> checkpoints() {
    return Arrays.asList(
        HBaseUpgradeCheckpoints.NO_UPGRADE,
        HBaseUpgradeCheckpoints.AFTER_CLUSTER_START,
        HBaseUpgradeCheckpoints.AFTER_CREATE_TABLE,
        HBaseUpgradeCheckpoints.AFTER_WRITE,
        "CUSTOM_CHECKPOINT"
    );
  }

  @Test
  public void testSetupInitializesConfiguration() throws Exception {
    // After setup, conf should be initialized
    assertNotNull("Configuration should be initialized after setup", conf);
  }

  @Test
  public void testSetupClearsClusterReference() throws Exception {
    // After setup, cluster should be null (not yet initialized)
    assertNull("Cluster should be null after setup", cluster);
  }

  @Test
  public void testSetupClearsConnectionReference() throws Exception {
    // After setup, connection should be null
    assertNull("Connection should be null after setup", connection);
  }

  @Test
  public void testSetupClearsAdminReference() throws Exception {
    // After setup, admin should be null
    assertNull("Admin should be null after setup", admin);
  }

  @Test
  public void testSetupClearsPreUpgradeServerNames() throws Exception {
    // After setup, preUpgradeServerNames should be empty
    assertNotNull("preUpgradeServerNames should not be null", preUpgradeServerNames);
    assertTrue("preUpgradeServerNames should be empty after setup",
        preUpgradeServerNames.isEmpty());
  }

  @Test
  public void testShouldUpgradeReturnsFalseForNullCheckpoint() {
    // Test that a null checkpoint in base class returns false
    // We can't modify the @Parameter field to null since JUnit manages it
    // Instead, test by temporarily modifying the base class field via superclass
    String originalBase = super.upgradeCheckpoint;
    super.upgradeCheckpoint = null;

    // Save and clear the subclass field too
    String originalSubclass = this.upgradeCheckpoint;
    this.upgradeCheckpoint = null;

    try {
      assertFalse("shouldUpgrade should return false when upgradeCheckpoint is null",
          shouldUpgrade("AFTER_CLUSTER_START"));
    } finally {
      super.upgradeCheckpoint = originalBase;
      this.upgradeCheckpoint = originalSubclass;
    }
  }

  @Test
  public void testShouldUpgradeReturnsFalseForNoUpgrade() {
    // Temporarily set upgradeCheckpoint to NO_UPGRADE
    String original = this.upgradeCheckpoint;
    this.upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    assertFalse("shouldUpgrade should return false when upgradeCheckpoint is NO_UPGRADE",
        shouldUpgrade("AFTER_CLUSTER_START"));
    assertFalse("shouldUpgrade should return false when upgradeCheckpoint is NO_UPGRADE",
        shouldUpgrade("AFTER_WRITE"));

    this.upgradeCheckpoint = original;
  }

  @Test
  public void testShouldUpgradeReturnsTrueForMatchingCheckpoint() {
    // Temporarily set upgradeCheckpoint to a specific value
    String original = this.upgradeCheckpoint;
    this.upgradeCheckpoint = "AFTER_WRITE";

    assertTrue("shouldUpgrade should return true for matching checkpoint",
        shouldUpgrade("AFTER_WRITE"));

    this.upgradeCheckpoint = original;
  }

  @Test
  public void testShouldUpgradeReturnsFalseForNonMatchingCheckpoint() {
    // Temporarily set upgradeCheckpoint to a specific value
    String original = this.upgradeCheckpoint;
    this.upgradeCheckpoint = "AFTER_WRITE";

    assertFalse("shouldUpgrade should return false for non-matching checkpoint",
        shouldUpgrade("AFTER_CLUSTER_START"));
    assertFalse("shouldUpgrade should return false for non-matching checkpoint",
        shouldUpgrade("AFTER_FLUSH"));

    this.upgradeCheckpoint = original;
  }

  @Test
  public void testCheckpointParameterSynchronization() throws Exception {
    // The upgradeCheckpoint should be synchronized from the @Parameter field
    // This test verifies the parameterized setup works correctly
    assertNotNull("upgradeCheckpoint should be synchronized from @Parameter field",
        this.upgradeCheckpoint);

    // Verify it's one of our expected values
    Collection<String> expectedCheckpoints = checkpoints();
    assertTrue("upgradeCheckpoint should be one of the parameterized values",
        expectedCheckpoints.contains(this.upgradeCheckpoint));
  }

  @Test
  public void testUpgradeCheckpointMatchesParameter() {
    // The base class upgradeCheckpoint should match the @Parameter field
    String expectedCheckpoint = this.upgradeCheckpoint;

    // Test shouldUpgrade logic with current checkpoint
    if (HBaseUpgradeCheckpoints.NO_UPGRADE.equals(expectedCheckpoint)) {
      assertFalse("NO_UPGRADE should never trigger an upgrade",
          shouldUpgrade("AFTER_CLUSTER_START"));
    } else {
      assertTrue("Checkpoint should match when same name is passed",
          shouldUpgrade(expectedCheckpoint));
    }
  }

  @Test
  public void testPreUpgradeServerNamesMapIsModifiable() {
    // The preUpgradeServerNames map should be modifiable
    assertNotNull("preUpgradeServerNames should not be null", preUpgradeServerNames);

    // Should be able to add entries
    preUpgradeServerNames.put(0, null);
    assertEquals("Should be able to add entries to preUpgradeServerNames",
        1, preUpgradeServerNames.size());

    // Clean up
    preUpgradeServerNames.clear();
  }

  @Test
  public void testConfigurationIsHBaseConfiguration() throws Exception {
    // Verify the configuration is properly initialized
    assertNotNull("Configuration should not be null", conf);

    // Should be an HBase configuration (can set HBase-specific properties)
    String testValue = "test-value-" + System.currentTimeMillis();
    conf.set("hbase.test.property", testValue);
    assertEquals("Configuration should store HBase properties",
        testValue, conf.get("hbase.test.property"));
  }

  @Test
  public void testMultipleCheckpointsWithDifferentNames() {
    // Test that different checkpoint names are handled correctly
    String original = this.upgradeCheckpoint;

    this.upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    assertTrue(shouldUpgrade(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START));
    assertFalse(shouldUpgrade(HBaseUpgradeCheckpoints.AFTER_WRITE));
    assertFalse(shouldUpgrade(HBaseUpgradeCheckpoints.AFTER_FLUSH));

    this.upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_WRITE;
    assertFalse(shouldUpgrade(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START));
    assertTrue(shouldUpgrade(HBaseUpgradeCheckpoints.AFTER_WRITE));
    assertFalse(shouldUpgrade(HBaseUpgradeCheckpoints.AFTER_FLUSH));

    this.upgradeCheckpoint = original;
  }

  @Test
  public void testCustomCheckpointName() {
    // Test that custom checkpoint names work
    String original = this.upgradeCheckpoint;

    this.upgradeCheckpoint = "MY_CUSTOM_CHECKPOINT";
    assertTrue(shouldUpgrade("MY_CUSTOM_CHECKPOINT"));
    assertFalse(shouldUpgrade("OTHER_CHECKPOINT"));

    this.upgradeCheckpoint = original;
  }
}
