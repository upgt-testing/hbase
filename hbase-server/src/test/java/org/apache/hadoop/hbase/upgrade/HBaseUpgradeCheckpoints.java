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

import org.apache.yetus.audience.InterfaceAudience;

/**
 * Constants for common upgrade checkpoint names used in parameterized upgrade tests.
 *
 * <p>These checkpoint names represent common points in a test workflow where an upgrade
 * might be triggered. Tests can use these constants to create parameterized test cases
 * that verify functionality across different upgrade scenarios.
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @Parameters(name = "upgrade-at={0}")
 * public static Collection<String> checkpoints() {
 *   return Arrays.asList(
 *     HBaseUpgradeCheckpoints.NO_UPGRADE,
 *     HBaseUpgradeCheckpoints.AFTER_CLUSTER_START,
 *     HBaseUpgradeCheckpoints.AFTER_CREATE_TABLE,
 *     HBaseUpgradeCheckpoints.AFTER_WRITE
 *   );
 * }
 * }</pre>
 */
@InterfaceAudience.Private
public final class HBaseUpgradeCheckpoints {

  /**
   * Special checkpoint indicating no upgrade should be performed.
   * Use this to test baseline functionality without any upgrade.
   */
  public static final String NO_UPGRADE = "NO_UPGRADE";

  /**
   * Checkpoint after cluster has started and is fully operational.
   * The cluster is ready to accept client connections at this point.
   */
  public static final String AFTER_CLUSTER_START = "AFTER_CLUSTER_START";

  /**
   * Checkpoint after a table has been created.
   * Table metadata is committed and regions are assigned.
   */
  public static final String AFTER_CREATE_TABLE = "AFTER_CREATE_TABLE";

  /**
   * Checkpoint after data has been written to a table.
   * Data may still be in memstore (not yet flushed to disk).
   */
  public static final String AFTER_WRITE = "AFTER_WRITE";

  /**
   * Checkpoint after data has been read from a table.
   * Verifies read operations work correctly.
   */
  public static final String AFTER_READ = "AFTER_READ";

  /**
   * Checkpoint after a memstore flush operation.
   * Data has been persisted to HFiles on disk.
   */
  public static final String AFTER_FLUSH = "AFTER_FLUSH";

  /**
   * Checkpoint after a compaction operation.
   * HFiles have been merged and optimized.
   */
  public static final String AFTER_COMPACT = "AFTER_COMPACT";

  /**
   * Checkpoint after a table has been closed/disabled.
   * Table is no longer serving requests.
   */
  public static final String AFTER_CLOSE = "AFTER_CLOSE";

  /**
   * Checkpoint after table has been enabled.
   * Table is back online and serving requests.
   */
  public static final String AFTER_ENABLE = "AFTER_ENABLE";

  /**
   * Checkpoint after a snapshot has been taken.
   * Table state has been captured for potential restore.
   */
  public static final String AFTER_SNAPSHOT = "AFTER_SNAPSHOT";

  /**
   * Checkpoint after a region split operation.
   * A region has been divided into two child regions.
   */
  public static final String AFTER_SPLIT = "AFTER_SPLIT";

  /**
   * Checkpoint after a region merge operation.
   * Two regions have been combined into one.
   */
  public static final String AFTER_MERGE = "AFTER_MERGE";

  private HBaseUpgradeCheckpoints() {
    // Utility class - no instantiation
  }
}
