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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.yetus.audience.InterfaceAudience;
import org.junit.After;
import org.junit.Before;
import org.junit.runners.Parameterized.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base test class for parameterized upgrade testing of ProcessBasedMiniHBaseCluster.
 *
 * <p>
 * This class provides automatic lifecycle management, checkpoint-based upgrade testing, and
 * complete test isolation. Each test execution is fully isolated with guaranteed cleanup between
 * runs.
 *
 * <h3>Usage Example:</h3>
 *
 * <pre>
 * {@code
 * &#64;RunWith(Parameterized.class)
 * public class TestMyFeature extends ProcessBasedUpgradeTestBase {
 *
 *   &#64;Parameter
 *   public String upgradeCheckpoint;
 *
 *   &#64;Parameters(name = "upgrade-at={0}")
 *   public static Collection<String> checkpoints() {
 *     return Arrays.asList(
 *       HBaseUpgradeCheckpoints.NO_UPGRADE,
 *       HBaseUpgradeCheckpoints.AFTER_CLUSTER_START,
 *       "AFTER_CREATE_TABLE",
 *       "AFTER_WRITE_DATA",
 *       "AFTER_FLUSH"
 *     );
 *   }
 *
 *   &#64;Test
 *   public void testFeature() throws Exception {
 *     cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
 *         .numRegionServers(3)
 *         .build();
 *     connection = cluster.getConnection();
 *     admin = connection.getAdmin();
 *
 *     checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);
 *
 *     // Create table
 *     TableDescriptor td = TableDescriptorBuilder.newBuilder(TableName.valueOf("test"))
 *         .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf"))
 *         .build();
 *     admin.createTable(td);
 *     checkpoint("AFTER_CREATE_TABLE");
 *
 *     // Write data
 *     try (Table table = connection.getTable(TableName.valueOf("test"))) {
 *       Put put = new Put(Bytes.toBytes("row1"));
 *       put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("q"), Bytes.toBytes("value"));
 *       table.put(put);
 *     }
 *     checkpoint("AFTER_WRITE_DATA");
 *
 *     // Flush (must close table before checkpoint!)
 *     admin.flush(TableName.valueOf("test"));
 *     checkpoint("AFTER_FLUSH");
 *
 *     // No try-finally needed - &#64;After handles cleanup!
 *   }
 * }
 * }
 * </pre>
 *
 * <h3>Node Identity Preservation:</h3>
 * <p>
 * During upgrades, this base class automatically verifies that each RegionServer maintains its
 * identity (hostname + port). If a node's identity changes, the test will fail with a clear error
 * message indicating which node changed and how.
 *
 * <h3>Guarantees:</h3>
 * <ul>
 * <li>Complete isolation between checkpoint executions</li>
 * <li>Automatic cleanup of processes and directories</li>
 * <li>Verification of cleanup success</li>
 * <li>Force cleanup if verification fails</li>
 * <li>Node identity preservation during upgrades</li>
 * <li>Automatic ServerName tracking before/after upgrades</li>
 * </ul>
 *
 * <h3>Important Notes:</h3>
 * <ul>
 * <li>Always close Table, ResultScanner, and BufferedMutator resources before calling
 * checkpoint()</li>
 * <li>The base class handles Connection, Admin, and Cluster cleanup automatically</li>
 * <li>System properties hbase.start.home and hbase.upgrade.home are read automatically by the
 * cluster</li>
 * </ul>
 */
@InterfaceAudience.Private
public abstract class ProcessBasedUpgradeTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessBasedUpgradeTestBase.class);

  /** Pattern to match HBase process names for cleanup */
  private static final String PROCESS_PATTERN = "HMaster|HRegionServer";

  /** Pattern to match temporary cluster directories */
  private static final String TEMP_DIR_PREFIX = "process-minihbase-";

  /** Maximum age for temporary directories before cleanup (1 hour) */
  private static final long MAX_DIR_AGE_MS = TimeUnit.HOURS.toMillis(1);

  /** Wait time after cluster shutdown for processes to terminate */
  private static final int SHUTDOWN_WAIT_MS = 3000;

  /**
   * The upgrade checkpoint parameter. This field is synchronized from the subclass @Parameter
   * field via reflection in setupTest().
   */
  protected String upgradeCheckpoint;

  /** The ProcessBasedMiniHBaseCluster instance. Subclasses should initialize this. */
  protected ProcessBasedMiniHBaseCluster cluster;

  /** The HBase Connection instance. Subclasses should initialize this. */
  protected Connection connection;

  /** The HBase Configuration instance. Initialized in setupTest(). */
  protected Configuration conf;

  /** The HBase Admin client instance. May be null if not needed by subclass. */
  protected Admin admin;

  /** Map to store pre-upgrade ServerNames for identity verification */
  protected Map<Integer, ServerName> preUpgradeServerNames;

  /** HBaseTestingUtility for starting ZooKeeper cluster. */
  protected HBaseTestingUtility testUtil;

  /**
   * Sets up the test environment before each test execution.
   * <p>
   * This method performs the following steps:
   * <ol>
   * <li>Synchronizes the upgradeCheckpoint field from the subclass</li>
   * <li>Cleans up any orphaned processes from previous failed runs</li>
   * <li>Cleans up old temporary cluster directories</li>
   * <li>Initializes a fresh Configuration instance</li>
   * <li>Resets all managed resources to null</li>
   * </ol>
   * @throws Exception if setup fails
   */
  @Before
  public void setupTest() throws Exception {
    // Sync the @Parameter field from subclass to base class
    syncUpgradeCheckpointFromSubclass();

    LOG.info("=== Setting up test with checkpoint: {} ===", upgradeCheckpoint);

    // Clean up orphaned processes from previous failed runs
    LOG.info("Cleaning up orphaned HBase processes");
    cleanupOrphanedProcesses();

    // Clean up old cluster directories
    LOG.info("Cleaning up old cluster directories");
    cleanupOldClusterDirectories();

    // Initialize HBaseTestingUtility and start ZooKeeper
    testUtil = new HBaseTestingUtility();
    LOG.info("Starting MiniZooKeeperCluster");
    testUtil.startMiniZKCluster();
    LOG.info("MiniZooKeeperCluster started at port: {}",
      testUtil.getZkCluster().getClientPort());

    // Start MiniDFSCluster for distributed filesystem support
    LOG.info("Starting MiniDFSCluster");
    testUtil.startMiniDFSCluster(3);
    LOG.info("MiniDFSCluster started with namenode at: {}",
      testUtil.getDFSCluster().getFileSystem().getUri());

    // Set hbase.rootdir to use HDFS instead of local filesystem
    String hdfsRootDir = testUtil.getDefaultRootDirPath().toString();
    testUtil.getConfiguration().set("hbase.rootdir", hdfsRootDir);
    LOG.info("Set hbase.rootdir to HDFS: {}", hdfsRootDir);

    // Initialize configuration from test utility (includes ZK and DFS config)
    conf = HBaseConfiguration.create(testUtil.getConfiguration());
    LOG.info("Created HBase configuration with ZooKeeper quorum and DFS");

    // Reset all managed resources (defensive)
    cluster = null;
    connection = null;
    admin = null;
    preUpgradeServerNames = new HashMap<>();

    LOG.info("=== Test setup completed for checkpoint: {} ===", upgradeCheckpoint);
  }

  /**
   * Tears down the test environment after each test execution.
   * <p>
   * This method ensures complete cleanup of all resources in the following order:
   * <ol>
   * <li>Close Admin client</li>
   * <li>Close Connection</li>
   * <li>Shutdown cluster</li>
   * <li>Wait for processes to terminate</li>
   * <li>Verify cleanup success</li>
   * <li>Force cleanup if verification fails</li>
   * </ol>
   * <p>
   * Each cleanup step is in an independent try-catch block to ensure all cleanup runs even if one
   * step fails.
   * @throws Exception if teardown fails catastrophically
   */
  @After
  public void tearDownTest() throws Exception {
    LOG.info("=== Tearing down test with checkpoint: {} ===", upgradeCheckpoint);

    // Close admin (independent try-catch)
    if (admin != null) {
      try {
        LOG.info("Closing Admin client");
        admin.close();
        LOG.info("Admin client closed successfully");
      } catch (Exception e) {
        LOG.warn("Failed to close Admin client", e);
      } finally {
        admin = null;
      }
    }

    // Close connection (independent try-catch)
    if (connection != null) {
      try {
        LOG.info("Closing Connection");
        connection.close();
        LOG.info("Connection closed successfully");
      } catch (Exception e) {
        LOG.warn("Failed to close Connection", e);
      } finally {
        connection = null;
      }
    }

    // Shutdown cluster (independent try-catch)
    if (cluster != null) {
      try {
        LOG.info("Shutting down cluster");
        cluster.shutdown();
        LOG.info("Cluster shutdown initiated");
      } catch (Exception e) {
        LOG.warn("Failed to shutdown cluster", e);
      } finally {
        cluster = null;
      }
    }

    // Wait for processes to terminate
    try {
      LOG.info("Waiting {}ms for processes to terminate", SHUTDOWN_WAIT_MS);
      Thread.sleep(SHUTDOWN_WAIT_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while waiting for process termination", e);
    }

    // Verify cleanup success
    boolean cleanupSuccessful = verifyCleanup();
    if (!cleanupSuccessful) {
      LOG.error("Cleanup verification failed - forcing cleanup of orphaned processes");
      cleanupOrphanedProcesses();
      // Verify again after force cleanup
      if (!verifyCleanup()) {
        LOG.error("Force cleanup also failed - some HBase processes may still be running");
      } else {
        LOG.info("Force cleanup successful");
      }
    } else {
      LOG.info("Cleanup verification passed - no orphaned processes found");
    }

    // Shutdown DFS cluster (must be before ZooKeeper)
    if (testUtil != null && testUtil.getDFSCluster() != null) {
      try {
        LOG.info("Shutting down MiniDFSCluster");
        testUtil.shutdownMiniDFSCluster();
        LOG.info("MiniDFSCluster shutdown successfully");
      } catch (Exception e) {
        LOG.warn("Failed to shutdown MiniDFSCluster", e);
      }
    }

    // Shutdown ZooKeeper cluster
    if (testUtil != null) {
      try {
        LOG.info("Shutting down MiniZooKeeperCluster");
        testUtil.shutdownMiniZKCluster();
        LOG.info("MiniZooKeeperCluster shutdown successfully");
      } catch (Exception e) {
        LOG.warn("Failed to shutdown MiniZooKeeperCluster", e);
      } finally {
        testUtil = null;
      }
    }

    LOG.info("=== Test teardown completed for checkpoint: {} ===", upgradeCheckpoint);
  }

  /**
   * Performs an upgrade at the specified checkpoint if configured.
   * <p>
   * IMPORTANT: Close all Table, Scanner, and BufferedMutator resources before calling this method
   * to avoid broken connections during node restarts.
   * <p>
   * This method performs the following steps if an upgrade is needed:
   * <ol>
   * <li>Verifies cluster health before upgrade</li>
   * <li>Captures node identities (ServerNames) before upgrade</li>
   * <li>Performs the rolling upgrade</li>
   * <li>Verifies cluster health after upgrade</li>
   * <li>Verifies node identities are preserved</li>
   * </ol>
   * @param name the checkpoint name to check against the configured upgrade checkpoint
   * @throws Exception if the upgrade fails or verification fails
   */
  protected void checkpoint(String name) throws Exception {
    if (!shouldUpgrade(name)) {
      LOG.debug("Checkpoint {} reached, no upgrade needed (configured checkpoint: {})", name,
        upgradeCheckpoint);
      return;
    }

    LOG.info("=== Checkpoint {} reached - performing upgrade ===", name);

    // Verify cluster health before upgrade
    LOG.info("Verifying cluster health before upgrade");
    if (!cluster.isClusterUp()) {
      throw new IllegalStateException("Cluster is not healthy before upgrade at checkpoint " + name);
    }

    int expectedRSCount = cluster.getNumLiveRegionServers();
    try (Connection tempConn = ConnectionFactory.createConnection(conf)) {
      Admin tempAdmin = tempConn.getAdmin();
      ClusterMetrics metrics = tempAdmin.getClusterMetrics();
      int actualRSCount = metrics.getLiveServerMetrics().size();
      if (actualRSCount != expectedRSCount) {
        throw new IllegalStateException("Before upgrade: Expected " + expectedRSCount
          + " region servers, but found " + actualRSCount);
      }
      LOG.info("Pre-upgrade health check passed: {} region servers online", actualRSCount);
    }

    // Capture node identities before upgrade
    LOG.info("Capturing node identities before upgrade");
    captureNodeIdentities();

    // Perform the rolling upgrade
    LOG.info("Starting rolling upgrade at checkpoint {}", name);
    cluster.upgrade();
    LOG.info("Rolling upgrade completed");

    // Verify cluster health after upgrade
    LOG.info("Waiting for cluster to stabilize after upgrade");
    cluster.waitClusterUp();

    try (Connection tempConn = ConnectionFactory.createConnection(conf)) {
      Admin tempAdmin = tempConn.getAdmin();
      ClusterMetrics metrics = tempAdmin.getClusterMetrics();
      int actualRSCount = metrics.getLiveServerMetrics().size();
      if (actualRSCount != expectedRSCount) {
        throw new IllegalStateException("After upgrade: Expected " + expectedRSCount
          + " region servers, but found " + actualRSCount);
      }
      LOG.info("Post-upgrade health check passed: {} region servers online", actualRSCount);
    }

    // Verify node identities are preserved
    LOG.info("Verifying node identities preserved after upgrade");
    verifyNodeIdentitiesPreserved();

    LOG.info("=== Upgrade completed successfully at checkpoint {} ===", name);
  }

  /**
   * Determines if an upgrade should be performed at the given checkpoint.
   * @param name the checkpoint name to check
   * @return true if upgrade should be performed at this checkpoint, false otherwise
   */
  protected boolean shouldUpgrade(String name) {
    // Always get the current value from subclass to handle field shadowing
    String currentCheckpoint = getUpgradeCheckpointValue();
    if (currentCheckpoint == null) {
      return false;
    }
    if (HBaseUpgradeCheckpoints.NO_UPGRADE.equals(currentCheckpoint)) {
      return false;
    }
    return currentCheckpoint.equals(name);
  }

  /**
   * Gets the current upgrade checkpoint value, checking both subclass and base class fields.
   * This method handles the case where the subclass field shadows the base class field.
   * @return the current upgrade checkpoint value
   */
  private String getUpgradeCheckpointValue() {
    // First try to get from subclass @Parameter field via reflection
    try {
      Class<?> clazz = this.getClass();
      for (Field field : clazz.getFields()) {
        if (
          field.getName().equals("upgradeCheckpoint")
            && field.isAnnotationPresent(Parameter.class)
        ) {
          field.setAccessible(true);
          Object value = field.get(this);
          if (value instanceof String) {
            return (String) value;
          }
        }
      }
      for (Field field : clazz.getDeclaredFields()) {
        if (
          field.getName().equals("upgradeCheckpoint")
            && field.isAnnotationPresent(Parameter.class)
        ) {
          field.setAccessible(true);
          Object value = field.get(this);
          if (value instanceof String) {
            return (String) value;
          }
        }
      }
    } catch (Exception e) {
      LOG.debug("Failed to get upgradeCheckpoint from subclass, using base class value", e);
    }
    // Fall back to base class field
    return this.upgradeCheckpoint;
  }

  /**
   * Captures the current node identities (ServerNames) for later verification.
   * <p>
   * This method queries the cluster metrics and stores each RegionServer's ServerName with its
   * index. The captured identities are used by verifyNodeIdentitiesPreserved() to ensure nodes
   * maintain their identity during upgrades.
   * @throws IOException if unable to query cluster metrics
   */
  protected void captureNodeIdentities() throws IOException {
    preUpgradeServerNames.clear();

    // Use a fresh connection to avoid any stale connection issues
    try (Connection tempConn = ConnectionFactory.createConnection(conf)) {
      Admin tempAdmin = tempConn.getAdmin();
      ClusterMetrics metrics = tempAdmin.getClusterMetrics();
      int index = 0;
      for (ServerName serverName : metrics.getLiveServerMetrics().keySet()) {
        preUpgradeServerNames.put(index, serverName);
        LOG.info("Captured node {} identity: {}:{}", index, serverName.getHostname(),
          serverName.getPort());
        index++;
      }
    }
    LOG.info("Captured {} node identities", preUpgradeServerNames.size());
  }

  /**
   * Verifies that node identities are preserved after an upgrade.
   * <p>
   * This method compares the current ServerNames with the pre-upgrade snapshot and verifies that
   * each node's hostname and port remain unchanged.
   * @throws AssertionError if any node identity changed during upgrade
   * @throws IOException if unable to query cluster metrics
   */
  protected void verifyNodeIdentitiesPreserved() throws IOException {
    // Use a fresh connection to avoid any stale connection issues
    try (Connection tempConn = ConnectionFactory.createConnection(conf)) {
      Admin tempAdmin = tempConn.getAdmin();
      ClusterMetrics metrics = tempAdmin.getClusterMetrics();
      int index = 0;
      for (ServerName postUpgrade : metrics.getLiveServerMetrics().keySet()) {
        ServerName preUpgrade = preUpgradeServerNames.get(index);
        if (preUpgrade == null) {
          throw new AssertionError("No pre-upgrade ServerName found for index " + index
            + ". Current ServerName: " + postUpgrade);
        }

        if (!postUpgrade.getHostname().equals(preUpgrade.getHostname())) {
          String errorMsg = String.format(
            "Node %d hostname changed during upgrade: %s -> %s. "
              + "This indicates node identity was not preserved!",
            index, preUpgrade.getHostname(), postUpgrade.getHostname());
          LOG.error(errorMsg);
          throw new AssertionError(errorMsg);
        }

        if (postUpgrade.getPort() != preUpgrade.getPort()) {
          String errorMsg = String.format(
            "Node %d port changed during upgrade: %d -> %d. "
              + "This indicates node identity was not preserved!",
            index, preUpgrade.getPort(), postUpgrade.getPort());
          LOG.error(errorMsg);
          throw new AssertionError(errorMsg);
        }

        LOG.info("Node {} identity verified: {}:{} (unchanged)", index, postUpgrade.getHostname(),
          postUpgrade.getPort());
        index++;
      }
    }
    LOG.info("All {} node identities preserved successfully", preUpgradeServerNames.size());
  }

  /**
   * Synchronizes the upgradeCheckpoint field from the subclass @Parameter field to this base class
   * field using reflection.
   * <p>
   * This method searches for a field named "upgradeCheckpoint" annotated with @Parameter in the
   * concrete subclass and copies its value to the base class field.
   */
  private void syncUpgradeCheckpointFromSubclass() {
    try {
      // Search for the @Parameter field in the subclass
      Class<?> clazz = this.getClass();
      for (Field field : clazz.getFields()) {
        if (
          field.getName().equals("upgradeCheckpoint")
            && field.isAnnotationPresent(Parameter.class)
        ) {
          field.setAccessible(true);
          Object value = field.get(this);
          if (value instanceof String) {
            this.upgradeCheckpoint = (String) value;
            LOG.debug("Synchronized upgradeCheckpoint from subclass: {}", this.upgradeCheckpoint);
            return;
          }
        }
      }
      // If not found in public fields, try declared fields
      for (Field field : clazz.getDeclaredFields()) {
        if (
          field.getName().equals("upgradeCheckpoint")
            && field.isAnnotationPresent(Parameter.class)
        ) {
          field.setAccessible(true);
          Object value = field.get(this);
          if (value instanceof String) {
            this.upgradeCheckpoint = (String) value;
            LOG.debug("Synchronized upgradeCheckpoint from subclass: {}", this.upgradeCheckpoint);
            return;
          }
        }
      }
      LOG.warn("Could not find @Parameter field 'upgradeCheckpoint' in subclass {}",
        clazz.getName());
    } catch (Exception e) {
      LOG.warn("Failed to sync upgradeCheckpoint from subclass", e);
    }
  }

  /**
   * Cleans up orphaned HBase processes from previous failed test runs.
   * <p>
   * This method uses jps to find HMaster and HRegionServer processes and kills them with SIGKILL
   * (-9).
   */
  private void cleanupOrphanedProcesses() {
    try {
      // Use jps to find HBase processes and kill them
      // Note: Using conditional check instead of xargs -r for macOS compatibility
      String[] command = { "/bin/bash", "-c",
        "pids=$(jps | grep -E '" + PROCESS_PATTERN + "' | awk '{print $1}'); "
          + "if [ -n \"$pids\" ]; then kill -9 $pids 2>/dev/null || true; fi" };
      Process process = Runtime.getRuntime().exec(command);
      int exitCode = process.waitFor();
      LOG.debug("Orphaned process cleanup completed with exit code: {}", exitCode);
    } catch (Exception e) {
      LOG.warn("Failed to cleanup orphaned processes", e);
    }
  }

  /**
   * Cleans up old temporary cluster directories.
   * <p>
   * This method finds directories matching the pattern process-minihbase-* in the system temp
   * directory and deletes those older than 1 hour.
   */
  private void cleanupOldClusterDirectories() {
    try {
      File tmpDir = new File(System.getProperty("java.io.tmpdir"));
      File[] oldDirs = tmpDir.listFiles((dir, name) -> name.startsWith(TEMP_DIR_PREFIX)
        && name.matches(".*\\d{13}$")); // Match timestamp suffix

      if (oldDirs != null) {
        long cutoffTime = System.currentTimeMillis() - MAX_DIR_AGE_MS;
        for (File dir : oldDirs) {
          // Only delete directories older than the cutoff time
          if (dir.lastModified() < cutoffTime) {
            try {
              FileUtils.deleteDirectory(dir);
              LOG.info("Deleted old cluster directory: {}", dir.getAbsolutePath());
            } catch (IOException e) {
              LOG.warn("Failed to delete old cluster directory: {}", dir.getAbsolutePath(), e);
            }
          }
        }
      }
    } catch (Exception e) {
      LOG.warn("Failed to cleanup old cluster directories", e);
    }
  }

  /**
   * Verifies that no orphaned HBase processes are running.
   * @return true if no orphaned processes found, false otherwise
   */
  private boolean verifyCleanup() {
    try {
      String[] command = { "/bin/bash", "-c", "jps | grep -E '" + PROCESS_PATTERN + "'" };
      Process process = Runtime.getRuntime().exec(command);

      StringBuilder output = new StringBuilder();
      try (
        BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
        }
      }

      int exitCode = process.waitFor();

      // Exit code 0 means grep found matches (processes still running)
      // Exit code 1 means no matches found (cleanup successful)
      if (exitCode == 0 && output.length() > 0) {
        LOG.warn("Found orphaned HBase processes:\n{}", output.toString().trim());
        return false;
      }

      return true;
    } catch (Exception e) {
      LOG.warn("Failed to verify cleanup", e);
      return false;
    }
  }
}
