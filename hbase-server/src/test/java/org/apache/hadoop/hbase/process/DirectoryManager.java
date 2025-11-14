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
package org.apache.hadoop.hbase.process;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages directory structure for process-based cluster.
 * Creates and maintains work directories for each node (master/regionserver).
 *
 * Directory structure:
 * <pre>
 * /tmp/process-minihbase-{timestamp}/
 * ├── master0/
 * │   ├── ports.properties
 * │   ├── conf/
 * │   │   └── hbase-site.xml
 * │   ├── data/
 * │   │   └── hbase/
 * │   ├── logs/
 * │   └── pid
 * ├── rs0/
 * │   ├── ports.properties
 * │   ├── conf/
 * │   ├── data/
 * │   └── logs/
 * └── cluster.properties
 * </pre>
 */
@InterfaceAudience.Private
public class DirectoryManager {
  private static final Logger LOG = LoggerFactory.getLogger(DirectoryManager.class);

  private static final String DIR_PREFIX = "process-minihbase-";

  private final File clusterRoot;
  private boolean cleanupOnExit;

  /**
   * Create a DirectoryManager with a new temporary cluster directory.
   *
   * @throws IOException if directory creation fails
   */
  public DirectoryManager() throws IOException {
    this(createClusterRootDir(), false);  // Temporarily disable cleanup for debugging
  }

  /**
   * Create a DirectoryManager with a specific cluster directory.
   *
   * @param clusterRoot Root directory for the cluster
   * @param cleanupOnExit Whether to delete directories on JVM exit
   */
  public DirectoryManager(File clusterRoot, boolean cleanupOnExit) {
    this.clusterRoot = clusterRoot;
    this.cleanupOnExit = cleanupOnExit;

    if (cleanupOnExit) {
      registerShutdownHook();
    }

    LOG.info("Directory manager created: root={}, cleanupOnExit={}", clusterRoot, cleanupOnExit);
    System.out.println("DirectoryManager: Cluster root directory: " + clusterRoot.getAbsolutePath());
    System.out.println("DirectoryManager: Cleanup on exit: " + cleanupOnExit);
  }

  /**
   * Create a temporary cluster root directory.
   */
  private static File createClusterRootDir() throws IOException {
    String tmpDir = System.getProperty("java.io.tmpdir");
    long timestamp = System.currentTimeMillis();
    File dir = new File(tmpDir, DIR_PREFIX + timestamp);

    if (!dir.mkdirs()) {
      throw new IOException("Failed to create cluster root directory: " + dir);
    }

    LOG.info("Created cluster root directory: {}", dir);
    return dir;
  }

  /**
   * Create a work directory for a Master node.
   *
   * @param masterIndex Index of the master
   * @return Work directory for the master
   * @throws IOException if directory creation fails
   */
  public File createMasterWorkDir(int masterIndex) throws IOException {
    File masterDir = new File(clusterRoot, "master" + masterIndex);
    createNodeWorkDir(masterDir);
    LOG.info("Created Master work directory: {}", masterDir);
    return masterDir;
  }

  /**
   * Create a work directory for a RegionServer node.
   *
   * @param rsIndex Index of the region server
   * @return Work directory for the region server
   * @throws IOException if directory creation fails
   */
  public File createRegionServerWorkDir(int rsIndex) throws IOException {
    File rsDir = new File(clusterRoot, "rs" + rsIndex);
    createNodeWorkDir(rsDir);
    LOG.info("Created RegionServer work directory: {}", rsDir);
    return rsDir;
  }

  /**
   * Create subdirectories for a node work directory.
   */
  private void createNodeWorkDir(File nodeDir) throws IOException {
    if (!nodeDir.exists() && !nodeDir.mkdirs()) {
      throw new IOException("Failed to create node directory: " + nodeDir);
    }

    // Create subdirectories
    createSubdir(nodeDir, "conf");
    createSubdir(nodeDir, "data");
    createSubdir(nodeDir, "data/hbase");
    createSubdir(nodeDir, "logs");
  }

  /**
   * Create a subdirectory.
   */
  private void createSubdir(File parent, String name) throws IOException {
    File subdir = new File(parent, name);
    if (!subdir.exists() && !subdir.mkdirs()) {
      throw new IOException("Failed to create subdirectory: " + subdir);
    }
  }

  /**
   * Get the cluster root directory.
   */
  public File getClusterRoot() {
    return clusterRoot;
  }

  /**
   * Get the work directory for a specific master.
   *
   * @param masterIndex Index of the master
   * @return Work directory
   */
  public File getMasterWorkDir(int masterIndex) {
    return new File(clusterRoot, "master" + masterIndex);
  }

  /**
   * Get the work directory for a specific region server.
   *
   * @param rsIndex Index of the region server
   * @return Work directory
   */
  public File getRegionServerWorkDir(int rsIndex) {
    return new File(clusterRoot, "rs" + rsIndex);
  }

  /**
   * Check if cleanup on exit is enabled.
   */
  public boolean isCleanupOnExit() {
    return cleanupOnExit;
  }

  /**
   * Set whether to cleanup on exit.
   */
  public void setCleanupOnExit(boolean cleanupOnExit) {
    this.cleanupOnExit = cleanupOnExit;
  }

  /**
   * Clean up all directories.
   *
   * @throws IOException if cleanup fails
   */
  public void cleanup() throws IOException {
    if (clusterRoot == null || !clusterRoot.exists()) {
      return;
    }

    if (!cleanupOnExit) {
      LOG.info("Skipping cleanup of cluster directory (cleanupOnExit=false): {}", clusterRoot);
      System.out.println("DirectoryManager: Skipping cleanup (cleanupOnExit=false): " + clusterRoot.getAbsolutePath());
      return;
    }

    LOG.info("Cleaning up cluster directory: {}", clusterRoot);
    deleteRecursively(clusterRoot.toPath());
  }

  /**
   * Delete a directory recursively.
   */
  private void deleteRecursively(Path path) throws IOException {
    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Register shutdown hook for cleanup.
   */
  private void registerShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      if (cleanupOnExit) {
        try {
          cleanup();
        } catch (IOException e) {
          LOG.warn("Failed to cleanup directories on exit", e);
        }
      }
    }, "DirectoryManager-cleanup"));
  }
}
