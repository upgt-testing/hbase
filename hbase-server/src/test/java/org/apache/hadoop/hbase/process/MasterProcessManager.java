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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages an HMaster process running in a separate JVM.
 * Handles master-specific startup, health checks, and shutdown procedures.
 */
@InterfaceAudience.Private
public class MasterProcessManager extends ProcessNodeManager {
  private static final Logger LOG = LoggerFactory.getLogger(MasterProcessManager.class);

  private static final long DEFAULT_STARTUP_TIMEOUT_MS = 300000; // 300 seconds (5 minutes) - increased for slower systems

  /**
   * Constructor for MasterProcessManager.
   *
   * @param nodeConfig Configuration for this master
   * @param hbaseHome Path to HBase installation directory
   * @param workDir Working directory for this master
   * @param nodeIndex Index of this master
   */
  public MasterProcessManager(Configuration nodeConfig, String hbaseHome, File workDir,
      int nodeIndex) {
    super(nodeConfig, hbaseHome, workDir, nodeIndex);
  }

  @Override
  protected String getNodeType() {
    return "Master";
  }

  @Override
  public void start() throws IOException {
    if (process != null && process.isAlive()) {
      LOG.warn("Master process already running");
      return;
    }

    LOG.info("Starting Master process {}: hbaseHome={}, workDir={}", nodeIndex, hbaseHome, workDir);

    // Load persisted ports if this is a restart
    loadPorts();

    // Build classpath
    List<File> classpath = buildClasspath();
    String classpathStr = buildClasspathString(classpath);

    // Build command line
    List<String> command = new ArrayList<>();
    command.add(System.getProperty("java.home") + "/bin/java");

    // JVM options
    command.add("-Xmx1024m");
    command.add("-XX:+HeapDumpOnOutOfMemoryError");
    command.add("-XX:HeapDumpPath=" + new File(workDir, "heap-dump.hprof").getAbsolutePath());

    // System properties
    command.add("-Dhbase.home.dir=" + hbaseHome);
    command.add("-Dhbase.log.dir=" + new File(workDir, "logs").getAbsolutePath());
    command.add("-Dhbase.log.file=hbase-master-" + nodeIndex + ".log");

    // Native library path
    File nativeLibDir = new File(hbaseHome, "lib/native");
    if (nativeLibDir.exists()) {
      command.add("-Djava.library.path=" + nativeLibDir.getAbsolutePath());
    }

    // Classpath
    command.add("-cp");
    command.add(classpathStr);

    // Main class
    command.add("org.apache.hadoop.hbase.process.launcher.MasterProcessLauncher");

    // Arguments
    command.add("--config-dir");
    command.add(new File(workDir, "conf").getAbsolutePath());
    command.add("--master-index");
    command.add(String.valueOf(nodeIndex));

    LOG.info("Starting Master process with command: {}", String.join(" ", command));

    // Start process
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(workDir);
    pb.redirectErrorStream(false);

    try {
      process = pb.start();
      LOG.info("Master process started");

      // Start output monitoring
      startOutputMonitoring();

      // Wait for process to be ready
      waitForProcessReady(DEFAULT_STARTUP_TIMEOUT_MS);

      // Persist ports after successful startup
      if (!ports.isEmpty()) {
        persistPorts();
      }

    } catch (IOException e) {
      LOG.error("Failed to start Master process", e);
      killProcess();
      throw e;
    }
  }

  @Override
  public void stop() throws IOException {
    LOG.info("Stopping Master process {}", nodeIndex);

    if (process == null || !process.isAlive()) {
      LOG.warn("Master process is not running");
      return;
    }

    // Attempt graceful shutdown via RPC
    boolean gracefulShutdown = false;
    try {
      gracefulShutdown = stopViaRpc();
    } catch (Exception e) {
      LOG.warn("Failed to stop Master via RPC", e);
    }

    if (!gracefulShutdown) {
      // Send SIGTERM
      LOG.info("Sending SIGTERM to Master process");
      process.destroy();

      try {
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
          LOG.warn("Master process did not stop gracefully, forcing kill");
          killProcess();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.warn("Interrupted while waiting for Master to stop");
        killProcess();
      }
    }

    // Stop output monitoring
    stopOutputMonitoring();

    LOG.info("Master process {} stopped", nodeIndex);
  }

  /**
   * Attempt to stop the master via RPC.
   * @return true if successful
   */
  private boolean stopViaRpc() {
    try (Connection conn = ConnectionFactory.createConnection(nodeConfig)) {
      Admin admin = conn.getAdmin();
      admin.shutdown();

      // Wait for process to exit
      if (process.waitFor(30, TimeUnit.SECONDS)) {
        LOG.info("Master stopped gracefully via RPC");
        return true;
      }
    } catch (Exception e) {
      LOG.debug("Failed to stop Master via RPC", e);
    }
    return false;
  }

  @Override
  public boolean isHealthy() throws IOException {
    if (!isProcessAlive()) {
      return false;
    }

    // Try to connect to master and get cluster metrics
    try (Connection conn = ConnectionFactory.createConnection(nodeConfig)) {
      Admin admin = conn.getAdmin();
      admin.getClusterMetrics();
      return true;
    } catch (Exception e) {
      LOG.debug("Master health check failed", e);
      return false;
    }
  }

  @Override
  public ServerName getServerName() {
    if (serverName != null) {
      return serverName;
    }

    // Try to get ServerName from running master
    try (Connection conn = ConnectionFactory.createConnection(nodeConfig)) {
      Admin admin = conn.getAdmin();
      serverName = admin.getClusterMetrics().getMasterName();
      return serverName;
    } catch (Exception e) {
      LOG.debug("Failed to get ServerName from Master", e);

      // Fallback: construct from configuration
      String hostname = "localhost";
      int port = nodeConfig.getInt(HConstants.MASTER_PORT, HConstants.DEFAULT_MASTER_PORT);
      // Note: startCode is unknown without connecting to master
      return ServerName.valueOf(hostname, port, System.currentTimeMillis());
    }
  }

  /**
   * Set the port for this master.
   * @param portType Type of port (e.g., "master", "master-info")
   * @param port Port number
   */
  public void setPort(String portType, int port) {
    ports.put(portType, port);
  }

  /**
   * Get the master RPC port.
   */
  public int getMasterPort() {
    return ports.getOrDefault("master",
        nodeConfig.getInt(HConstants.MASTER_PORT, HConstants.DEFAULT_MASTER_PORT));
  }

  /**
   * Get the master info (web UI) port.
   */
  public int getMasterInfoPort() {
    return ports.getOrDefault("master-info",
        nodeConfig.getInt(HConstants.MASTER_INFO_PORT, HConstants.DEFAULT_MASTER_INFOPORT));
  }
}
