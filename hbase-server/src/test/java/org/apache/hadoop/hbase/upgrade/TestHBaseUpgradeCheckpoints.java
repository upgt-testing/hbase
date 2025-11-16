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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.testclassification.SmallTests;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Tests for {@link HBaseUpgradeCheckpoints} constants class.
 */
@Category(SmallTests.class)
public class TestHBaseUpgradeCheckpoints {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
      HBaseClassTestRule.forClass(TestHBaseUpgradeCheckpoints.class);

  @Test
  public void testNoUpgradeConstant() {
    assertNotNull("NO_UPGRADE should not be null", HBaseUpgradeCheckpoints.NO_UPGRADE);
    assertEquals("NO_UPGRADE", HBaseUpgradeCheckpoints.NO_UPGRADE);
  }

  @Test
  public void testAfterClusterStartConstant() {
    assertNotNull("AFTER_CLUSTER_START should not be null",
        HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);
    assertEquals("AFTER_CLUSTER_START", HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);
  }

  @Test
  public void testAfterCreateTableConstant() {
    assertNotNull("AFTER_CREATE_TABLE should not be null",
        HBaseUpgradeCheckpoints.AFTER_CREATE_TABLE);
    assertEquals("AFTER_CREATE_TABLE", HBaseUpgradeCheckpoints.AFTER_CREATE_TABLE);
  }

  @Test
  public void testAfterWriteConstant() {
    assertNotNull("AFTER_WRITE should not be null", HBaseUpgradeCheckpoints.AFTER_WRITE);
    assertEquals("AFTER_WRITE", HBaseUpgradeCheckpoints.AFTER_WRITE);
  }

  @Test
  public void testAfterReadConstant() {
    assertNotNull("AFTER_READ should not be null", HBaseUpgradeCheckpoints.AFTER_READ);
    assertEquals("AFTER_READ", HBaseUpgradeCheckpoints.AFTER_READ);
  }

  @Test
  public void testAfterFlushConstant() {
    assertNotNull("AFTER_FLUSH should not be null", HBaseUpgradeCheckpoints.AFTER_FLUSH);
    assertEquals("AFTER_FLUSH", HBaseUpgradeCheckpoints.AFTER_FLUSH);
  }

  @Test
  public void testAfterCompactConstant() {
    assertNotNull("AFTER_COMPACT should not be null", HBaseUpgradeCheckpoints.AFTER_COMPACT);
    assertEquals("AFTER_COMPACT", HBaseUpgradeCheckpoints.AFTER_COMPACT);
  }

  @Test
  public void testAfterCloseConstant() {
    assertNotNull("AFTER_CLOSE should not be null", HBaseUpgradeCheckpoints.AFTER_CLOSE);
    assertEquals("AFTER_CLOSE", HBaseUpgradeCheckpoints.AFTER_CLOSE);
  }

  @Test
  public void testAfterEnableConstant() {
    assertNotNull("AFTER_ENABLE should not be null", HBaseUpgradeCheckpoints.AFTER_ENABLE);
    assertEquals("AFTER_ENABLE", HBaseUpgradeCheckpoints.AFTER_ENABLE);
  }

  @Test
  public void testAfterSnapshotConstant() {
    assertNotNull("AFTER_SNAPSHOT should not be null", HBaseUpgradeCheckpoints.AFTER_SNAPSHOT);
    assertEquals("AFTER_SNAPSHOT", HBaseUpgradeCheckpoints.AFTER_SNAPSHOT);
  }

  @Test
  public void testAfterSplitConstant() {
    assertNotNull("AFTER_SPLIT should not be null", HBaseUpgradeCheckpoints.AFTER_SPLIT);
    assertEquals("AFTER_SPLIT", HBaseUpgradeCheckpoints.AFTER_SPLIT);
  }

  @Test
  public void testAfterMergeConstant() {
    assertNotNull("AFTER_MERGE should not be null", HBaseUpgradeCheckpoints.AFTER_MERGE);
    assertEquals("AFTER_MERGE", HBaseUpgradeCheckpoints.AFTER_MERGE);
  }

  @Test
  public void testAllConstantsUnique() {
    Set<String> constants = new HashSet<>();
    constants.add(HBaseUpgradeCheckpoints.NO_UPGRADE);
    constants.add(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);
    constants.add(HBaseUpgradeCheckpoints.AFTER_CREATE_TABLE);
    constants.add(HBaseUpgradeCheckpoints.AFTER_WRITE);
    constants.add(HBaseUpgradeCheckpoints.AFTER_READ);
    constants.add(HBaseUpgradeCheckpoints.AFTER_FLUSH);
    constants.add(HBaseUpgradeCheckpoints.AFTER_COMPACT);
    constants.add(HBaseUpgradeCheckpoints.AFTER_CLOSE);
    constants.add(HBaseUpgradeCheckpoints.AFTER_ENABLE);
    constants.add(HBaseUpgradeCheckpoints.AFTER_SNAPSHOT);
    constants.add(HBaseUpgradeCheckpoints.AFTER_SPLIT);
    constants.add(HBaseUpgradeCheckpoints.AFTER_MERGE);

    // All constants should be unique
    assertEquals("All checkpoint constants should be unique", 12, constants.size());
  }

  @Test
  public void testConstantsAreNotEmpty() {
    assertTrue("NO_UPGRADE should not be empty",
        !HBaseUpgradeCheckpoints.NO_UPGRADE.isEmpty());
    assertTrue("AFTER_CLUSTER_START should not be empty",
        !HBaseUpgradeCheckpoints.AFTER_CLUSTER_START.isEmpty());
    assertTrue("AFTER_CREATE_TABLE should not be empty",
        !HBaseUpgradeCheckpoints.AFTER_CREATE_TABLE.isEmpty());
    assertTrue("AFTER_WRITE should not be empty",
        !HBaseUpgradeCheckpoints.AFTER_WRITE.isEmpty());
    assertTrue("AFTER_READ should not be empty",
        !HBaseUpgradeCheckpoints.AFTER_READ.isEmpty());
    assertTrue("AFTER_FLUSH should not be empty",
        !HBaseUpgradeCheckpoints.AFTER_FLUSH.isEmpty());
    assertTrue("AFTER_COMPACT should not be empty",
        !HBaseUpgradeCheckpoints.AFTER_COMPACT.isEmpty());
    assertTrue("AFTER_CLOSE should not be empty",
        !HBaseUpgradeCheckpoints.AFTER_CLOSE.isEmpty());
  }

  @Test
  public void testNoUpgradeIsSpecialValue() {
    // NO_UPGRADE should be easily distinguishable and indicate no upgrade
    assertTrue("NO_UPGRADE should contain 'NO' or 'UPGRADE'",
        HBaseUpgradeCheckpoints.NO_UPGRADE.contains("NO") ||
        HBaseUpgradeCheckpoints.NO_UPGRADE.contains("UPGRADE"));
  }
}
