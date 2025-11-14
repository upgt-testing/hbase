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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.ServerName;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for managing HBase node processes (Master or RegionServer).
 * Each instance manages a single node process, handling its lifecycle,
 * monitoring, and configuration.
 */
@InterfaceAudience.Private
public abstract class ProcessNodeManager {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessNodeManager.class);

  protected Process process;
  protected Configuration nodeConfig;
  protected String hbaseHome;
  protected File workDir;
  protected int nodeIndex;
  protected Map<String, Integer> ports;
  protected ServerName serverName;

  // Process monitoring thread
  private Thread outputMonitor;
  private volatile boolean stopped = false;

  /**
   * Constructor for ProcessNodeManager.
   *
   * @param nodeConfig Configuration for this node
   * @param hbaseHome Path to HBase installation directory
   * @param workDir Working directory for this node (config, logs, data)
   * @param nodeIndex Index of this node
   */
  public ProcessNodeManager(Configuration nodeConfig, String hbaseHome, File workDir,
      int nodeIndex) {
    this.nodeConfig = nodeConfig;
    this.hbaseHome = hbaseHome;
    this.workDir = workDir;
    this.nodeIndex = nodeIndex;
    this.ports = new HashMap<>();
  }

  /**
   * Start the node process.
   * Implementations should:
   * 1. Build the classpath
   * 2. Build the command line
   * 3. Start the process
   * 4. Wait for the process to be ready
   */
  public abstract void start() throws IOException;

  /**
   * Stop the node process gracefully.
   * Should attempt RPC-based shutdown first, then SIGTERM if needed.
   */
  public abstract void stop() throws IOException;

  /**
   * Check if the node process is healthy.
   * Should verify both process-level and RPC-level health.
   */
  public abstract boolean isHealthy() throws IOException;

  /**
   * Get the ServerName for this node.
   * @return ServerName of this node
   */
  public abstract ServerName getServerName();

  /**
   * Get the node type name for logging.
   */
  protected abstract String getNodeType();

  /**
   * Wait for the process to be ready to accept connections.
   * @param timeoutMs Maximum time to wait in milliseconds
   * @throws IOException if process fails to start or timeout occurs
   */
  protected void waitForProcessReady(long timeoutMs) throws IOException {
    long startTime = System.currentTimeMillis();
    long deadline = startTime + timeoutMs;

    while (System.currentTimeMillis() < deadline) {
      if (!isProcessAlive()) {
        throw new IOException(getNodeType() + " process died during startup");
      }

      try {
        if (isHealthy()) {
          LOG.info("{} process is ready: {}", getNodeType(), getServerName());
          return;
        }
      } catch (IOException e) {
        // Expected during startup, keep waiting
        LOG.debug("{} not ready yet: {}", getNodeType(), e.getMessage());
      }

      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while waiting for " + getNodeType() + " to start", e);
      }
    }

    throw new IOException(
        getNodeType() + " failed to start within " + timeoutMs + "ms");
  }

  /**
   * Check if the process is alive.
   */
  public boolean isProcessAlive() {
    return process != null && process.isAlive();
  }

  /**
   * Kill the process forcefully.
   */
  protected void killProcess() {
    if (process != null && process.isAlive()) {
      LOG.warn("Force killing {} process", getNodeType());
      process.destroyForcibly();
      try {
        process.waitFor(30, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.warn("Interrupted while waiting for process to die");
      }
    }
  }

  /**
   * Build the classpath for this node process.
   * @return List of JAR files and directories to include in classpath
   */
  protected List<File> buildClasspath() throws IOException {
    List<File> classpath = new ArrayList<>();

    File hbaseHomeDir = new File(hbaseHome);
    if (!hbaseHomeDir.exists() || !hbaseHomeDir.isDirectory()) {
      throw new IOException("HBase home directory does not exist: " + hbaseHome);
    }

    // Add HBase JARs from lib/
    File libDir = new File(hbaseHomeDir, "lib");
    if (libDir.exists() && libDir.isDirectory()) {
      File[] jars = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
      if (jars != null) {
        for (File jar : jars) {
          classpath.add(jar);
        }
      }
    }

    // Add client-facing third-party dependencies
    File clientFacingThirdPartyDir = new File(libDir, "client-facing-thirdparty");
    if (clientFacingThirdPartyDir.exists() && clientFacingThirdPartyDir.isDirectory()) {
      File[] jars = clientFacingThirdPartyDir.listFiles((dir, name) -> name.endsWith(".jar"));
      if (jars != null) {
        for (File jar : jars) {
          classpath.add(jar);
        }
      }
    }

    // Add configuration directory
    File confDir = new File(workDir, "conf");
    if (confDir.exists()) {
      classpath.add(confDir);
    }

    // Add test-classes directory for launcher classes (MasterProcessLauncher, RegionServerProcessLauncher)
    // These are test-only classes that act as entry points for the subprocess
    String testClassesPath = System.getProperty("project.build.testOutputDirectory");
    if (testClassesPath != null) {
      File testClassesDir = new File(testClassesPath);
      if (testClassesDir.exists()) {
        classpath.add(testClassesDir);
        LOG.debug("Added test-classes directory to classpath: {}", testClassesDir);
      }
    } else {
      // Fallback: try to find test-classes directory relative to current working directory
      File testClassesDir = new File("target/test-classes");
      if (testClassesDir.exists()) {
        classpath.add(testClassesDir);
        LOG.debug("Added test-classes directory to classpath (fallback): {}", testClassesDir.getAbsolutePath());
      } else {
        LOG.warn("Could not find test-classes directory - launcher classes may not be available");
      }
    }

    LOG.debug("Built classpath with {} entries for {}", classpath.size(), getNodeType());
    return classpath;
  }

  /**
   * Build classpath string from list of files.
   */
  protected String buildClasspathString(List<File> classpath) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < classpath.size(); i++) {
      if (i > 0) {
        sb.append(File.pathSeparator);
      }
      sb.append(classpath.get(i).getAbsolutePath());
    }
    return sb.toString();
  }

  /**
   * Persist port allocations to disk.
   * Critical for node identity preservation across restarts/upgrades.
   */
  protected void persistPorts() throws IOException {
    File portFile = new File(workDir, "ports.properties");
    portFile.getParentFile().mkdirs();

    Properties props = new Properties();
    for (Map.Entry<String, Integer> entry : ports.entrySet()) {
      props.setProperty(entry.getKey() + ".port", String.valueOf(entry.getValue()));
    }

    try (FileOutputStream fos = new FileOutputStream(portFile)) {
      props.store(fos, "Port allocation for " + getNodeType() + " " + nodeIndex);
    }
    LOG.info("Persisted port allocations for {} {}: {}", getNodeType(), nodeIndex, ports);
  }

  /**
   * Load persisted port allocations from disk.
   * Used during node restart/upgrade to maintain identity.
   */
  protected void loadPorts() throws IOException {
    File portFile = new File(workDir, "ports.properties");
    if (!portFile.exists()) {
      LOG.debug("No persisted ports found for {} {}", getNodeType(), nodeIndex);
      return;
    }

    Properties props = new Properties();
    try (FileInputStream fis = new FileInputStream(portFile)) {
      props.load(fis);
    }

    for (String key : props.stringPropertyNames()) {
      if (key.endsWith(".port")) {
        String portType = key.substring(0, key.length() - ".port".length());
        int port = Integer.parseInt(props.getProperty(key));
        ports.put(portType, port);
      }
    }

    LOG.info("Loaded persisted ports for {} {}: {}", getNodeType(), nodeIndex, ports);
  }

  /**
   * Start output monitoring thread for the process.
   * Redirects stdout/stderr to log files and console.
   */
  protected void startOutputMonitoring() {
    if (process == null) {
      return;
    }

    // Monitor stdout
    outputMonitor = new Thread(() -> {
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while (!stopped && (line = reader.readLine()) != null) {
          LOG.info("[{}-{}] {}", getNodeType(), nodeIndex, line);
        }
      } catch (IOException e) {
        if (!stopped) {
          LOG.warn("Error reading {} output", getNodeType(), e);
        }
      }
    }, getNodeType() + "-" + nodeIndex + "-output");
    outputMonitor.setDaemon(true);
    outputMonitor.start();

    // Monitor stderr
    Thread errorMonitor = new Thread(() -> {
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
        String line;
        while (!stopped && (line = reader.readLine()) != null) {
          LOG.warn("[{}-{}] {}", getNodeType(), nodeIndex, line);
        }
      } catch (IOException e) {
        if (!stopped) {
          LOG.warn("Error reading {} error output", getNodeType(), e);
        }
      }
    }, getNodeType() + "-" + nodeIndex + "-error");
    errorMonitor.setDaemon(true);
    errorMonitor.start();
  }

  /**
   * Stop output monitoring.
   */
  protected void stopOutputMonitoring() {
    stopped = true;
    if (outputMonitor != null) {
      try {
        outputMonitor.join(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Get the work directory for this node.
   */
  public File getWorkDir() {
    return workDir;
  }

  /**
   * Get the node index.
   */
  public int getNodeIndex() {
    return nodeIndex;
  }

  /**
   * Get the configuration for this node.
   */
  public Configuration getConfiguration() {
    return nodeConfig;
  }

  /**
   * Get the allocated ports for this node.
   */
  public Map<String, Integer> getPorts() {
    return new HashMap<>(ports);
  }
}
