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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.HBaseCluster;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.hadoop.hbase.util.Threads;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.hbase.shaded.protobuf.generated.AdminProtos.AdminService;
import org.apache.hadoop.hbase.shaded.protobuf.generated.ClientProtos.ClientService;
import org.apache.hadoop.hbase.shaded.protobuf.generated.MasterProtos.MasterService;

/**
 * ProcessBasedMiniHBaseCluster runs HBase cluster nodes in separate JVM processes.
 * This enables testing version compatibility and upgrade scenarios by allowing
 * different nodes to run different HBase versions.
 *
 * <p>Key differences from MiniHBaseCluster:
 * <ul>
 *   <li>Each node runs in its own JVM process (vs threads in same JVM)</li>
 *   <li>Nodes can run different HBase versions</li>
 *   <li>Only client-side APIs are supported (RPC-based operations)</li>
 *   <li>Direct object access methods throw UnsupportedOperationException</li>
 *   <li>Node identity is preserved across restarts/upgrades</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * ProcessBasedMiniHBaseCluster cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
 *     .numRegionServers(3)
 *     .allNodesHBaseDistribution("/opt/hbase-2.6.0")
 *     .build();
 *
 * cluster.startup();
 * Connection conn = cluster.getConnection();
 * // Use conn for testing...
 * cluster.shutdown();
 * </pre>
 */
@InterfaceAudience.Public
public class ProcessBasedMiniHBaseCluster extends HBaseCluster {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessBasedMiniHBaseCluster.class);

  private final HBaseVersionRegistry versionRegistry;
  private final DirectoryManager directoryManager;
  private final PortAllocator portAllocator;
  private final ProcessConfigurationGenerator configGenerator;

  private final List<MasterProcessManager> masterProcesses;
  private final List<RegionServerProcessManager> regionServerProcesses;

  // Track version assignments for each node
  private final Map<Integer, String> masterVersions;
  private final Map<Integer, String> rsVersions;

  private Connection connection;
  private volatile boolean started = false;

  /**
   * Private constructor. Use Builder to create instances.
   */
  private ProcessBasedMiniHBaseCluster(Configuration conf, Builder builder) throws IOException {
    super(conf);

    this.versionRegistry = new HBaseVersionRegistry();
    this.directoryManager = new DirectoryManager();
    this.portAllocator = new PortAllocator(directoryManager.getClusterRoot());
    this.configGenerator = new ProcessConfigurationGenerator(conf, portAllocator);

    this.masterProcesses = new ArrayList<>();
    this.regionServerProcesses = new ArrayList<>();

    this.masterVersions = new HashMap<>();
    this.rsVersions = new HashMap<>();

    // Register HBase distributions
    if (builder.defaultHBaseHome != null) {
      versionRegistry.register(builder.defaultHBaseHome);
      versionRegistry.setDefault(builder.defaultHBaseHome);
    }

    for (Map.Entry<String, String> entry : builder.versionRegistry.entrySet()) {
      versionRegistry.register(entry.getKey(), entry.getValue());
    }

    // Auto-register distributions specified via per-node methods
    for (String hbaseHome : builder.masterVersions.values()) {
      if (hbaseHome != null && !hbaseHome.equals(builder.defaultHBaseHome)) {
        versionRegistry.register(hbaseHome);
      }
    }
    for (String hbaseHome : builder.rsVersions.values()) {
      if (hbaseHome != null && !hbaseHome.equals(builder.defaultHBaseHome)) {
        versionRegistry.register(hbaseHome);
      }
    }

    // Initialize master processes
    for (int i = 0; i < builder.numMasters; i++) {
      String version = builder.masterVersions.getOrDefault(i, builder.defaultHBaseHome);
      if (version == null) {
        throw new IOException("No HBase distribution specified for master " + i);
      }
      masterVersions.put(i, version);
    }

    // Initialize region server processes
    for (int i = 0; i < builder.numRegionServers; i++) {
      String version = builder.rsVersions.getOrDefault(i, builder.defaultHBaseHome);
      if (version == null) {
        throw new IOException("No HBase distribution specified for region server " + i);
      }
      rsVersions.put(i, version);
    }

    LOG.info("Created ProcessBasedMiniHBaseCluster: masters={}, regionservers={}, root={}",
        builder.numMasters, builder.numRegionServers, directoryManager.getClusterRoot());
  }

  /**
   * Start the cluster.
   * Starts all master and region server processes.
   */
  public void startup() throws IOException {
    if (started) {
      LOG.warn("Cluster already started");
      return;
    }

    LOG.info("Starting ProcessBasedMiniHBaseCluster");

    try {
      // Start masters
      for (int i = 0; i < masterVersions.size(); i++) {
        startMasterProcess(i);
      }

      // Wait for active master
      waitForActiveAndReadyMaster(60000);

      // Start region servers
      for (int i = 0; i < rsVersions.size(); i++) {
        startRegionServerProcess(i);
      }

      // Wait for cluster to be ready
      waitClusterUp();

      // Create connection
      connection = ConnectionFactory.createConnection(conf);

      // Get initial cluster status
      this.initialClusterStatus = getClusterMetrics();

      started = true;
      LOG.info("ProcessBasedMiniHBaseCluster started successfully");

    } catch (Exception e) {
      LOG.error("Failed to start cluster", e);
      shutdown();
      throw new IOException("Failed to start cluster", e);
    }
  }

  /**
   * Shutdown the cluster.
   * Stops all processes and cleans up resources.
   */
  public void shutdown() throws IOException {
    LOG.info("Shutting down ProcessBasedMiniHBaseCluster");

    // Close connection
    if (connection != null) {
      try {
        connection.close();
      } catch (IOException e) {
        LOG.warn("Error closing connection", e);
      }
      connection = null;
    }

    // Stop region servers
    for (RegionServerProcessManager rs : regionServerProcesses) {
      try {
        rs.stop();
      } catch (IOException e) {
        LOG.warn("Error stopping region server", e);
      }
    }
    regionServerProcesses.clear();

    // Stop masters
    for (MasterProcessManager master : masterProcesses) {
      try {
        master.stop();
      } catch (IOException e) {
        LOG.warn("Error stopping master", e);
      }
    }
    masterProcesses.clear();

    // Cleanup directories
    try {
      directoryManager.cleanup();
    } catch (IOException e) {
      LOG.warn("Error cleaning up directories", e);
    }

    started = false;
    LOG.info("ProcessBasedMiniHBaseCluster shut down");
  }

  @Override
  public void close() throws IOException {
    shutdown();
  }

  /**
   * Start a master process.
   */
  private void startMasterProcess(int masterIndex) throws IOException {
    String version = masterVersions.get(masterIndex);
    HBaseDistribution dist = versionRegistry.get(version);

    if (dist == null) {
      throw new IOException("HBase distribution not found: " + version);
    }

    File workDir = directoryManager.createMasterWorkDir(masterIndex);
    Configuration masterConf = configGenerator.generateMasterConfig(masterIndex, workDir);

    MasterProcessManager master =
        new MasterProcessManager(masterConf, dist.getHbaseHome().getAbsolutePath(), workDir,
            masterIndex);

    master.start();
    masterProcesses.add(master);

    LOG.info("Started master {}: version={}, workDir={}", masterIndex, version, workDir);
  }

  /**
   * Start a region server process.
   */
  private void startRegionServerProcess(int rsIndex) throws IOException {
    String version = rsVersions.get(rsIndex);
    HBaseDistribution dist = versionRegistry.get(version);

    if (dist == null) {
      throw new IOException("HBase distribution not found: " + version);
    }

    File workDir = directoryManager.createRegionServerWorkDir(rsIndex);
    Configuration rsConf = configGenerator.generateRegionServerConfig(rsIndex, workDir);

    RegionServerProcessManager rs =
        new RegionServerProcessManager(rsConf, dist.getHbaseHome().getAbsolutePath(), workDir,
            rsIndex);

    rs.start();
    regionServerProcesses.add(rs);

    LOG.info("Started region server {}: version={}, workDir={}", rsIndex, version, workDir);
  }

  /**
   * Wait for cluster to be ready.
   * Checks that all nodes are running and cluster is operational.
   */
  public void waitClusterUp() throws IOException {
    LOG.info("Waiting for cluster to be ready");

    long timeout = 120000; // 2 minutes
    long deadline = System.currentTimeMillis() + timeout;

    while (System.currentTimeMillis() < deadline) {
      try (Connection conn = ConnectionFactory.createConnection(conf)) {
        Admin admin = conn.getAdmin();

        // Check master is initialized
        ClusterMetrics metrics = admin.getClusterMetrics();
        ServerName masterName = metrics.getMasterName();
        if (masterName != null && !masterName.getHostname().isEmpty()) {
          // Check all region servers are online
          int expectedRs = rsVersions.size();
          int actualRs = metrics.getLiveServerMetrics().size();

          if (actualRs >= expectedRs) {
            LOG.info("Cluster is ready: {} region servers online", actualRs);
            return;
          }

          LOG.debug("Waiting for region servers: {}/{} online", actualRs, expectedRs);
        }
      } catch (Exception e) {
        LOG.debug("Cluster not ready yet", e);
      }

      Threads.sleep(1000);
    }

    throw new IOException("Cluster failed to become ready within timeout");
  }

  /**
   * Wait for an active and ready master.
   */
  public boolean waitForActiveAndReadyMaster(long timeout) throws IOException {
    long deadline = System.currentTimeMillis() + timeout;

    while (System.currentTimeMillis() < deadline) {
      for (MasterProcessManager master : masterProcesses) {
        if (master.isProcessAlive() && master.isHealthy()) {
          LOG.info("Active master is ready");
          return true;
        }
      }
      Threads.sleep(100);
    }

    return false;
  }

  /**
   * Get a connection to the cluster.
   */
  public Connection getConnection() throws IOException {
    if (connection == null) {
      connection = ConnectionFactory.createConnection(conf);
    }
    return connection;
  }

  @Override
  public ClusterMetrics getClusterMetrics() throws IOException {
    Connection conn = getConnection();
    Admin admin = conn.getAdmin();
    return admin.getClusterMetrics();
  }

  @Override
  public MasterService.BlockingInterface getMasterAdminService() throws IOException {
    throw new UnsupportedOperationException(
        "Direct master service access not supported in ProcessBasedMiniHBaseCluster. " +
        "This cluster runs nodes in separate processes. Use client-side APIs instead.");
  }

  @Override
  public AdminService.BlockingInterface getAdminProtocol(ServerName serverName) throws IOException {
    throw new UnsupportedOperationException(
        "Direct admin protocol access not supported in ProcessBasedMiniHBaseCluster. " +
        "This cluster runs nodes in separate processes. Use client-side APIs instead.");
  }

  @Override
  public ClientService.BlockingInterface getClientProtocol(ServerName serverName)
      throws IOException {
    throw new UnsupportedOperationException(
        "Direct client protocol access not supported in ProcessBasedMiniHBaseCluster. " +
        "This cluster runs nodes in separate processes. Use client-side APIs instead.");
  }

  @Override
  public void startRegionServer(String hostname, int port) throws IOException {
    throw new UnsupportedOperationException(
        "startRegionServer(hostname, port) not supported. Use startRegionServer() instead.");
  }

  @Override
  public void killRegionServer(ServerName serverName) throws IOException {
    for (RegionServerProcessManager rs : regionServerProcesses) {
      if (serverName.equals(rs.getServerName())) {
        rs.killProcess();
        LOG.info("Killed region server: {}", serverName);
        return;
      }
    }
    throw new IOException("Region server not found: " + serverName);
  }

  @Override
  public void stopRegionServer(ServerName serverName) throws IOException {
    for (RegionServerProcessManager rs : regionServerProcesses) {
      if (serverName.equals(rs.getServerName())) {
        rs.stop();
        LOG.info("Stopped region server: {}", serverName);
        return;
      }
    }
    throw new IOException("Region server not found: " + serverName);
  }

  @Override
  public void waitForRegionServerToStop(ServerName serverName, long timeout) throws IOException {
    long deadline = System.currentTimeMillis() + timeout;

    for (RegionServerProcessManager rs : regionServerProcesses) {
      if (serverName.equals(rs.getServerName())) {
        while (System.currentTimeMillis() < deadline) {
          if (!rs.isProcessAlive()) {
            LOG.info("Region server stopped: {}", serverName);
            return;
          }
          Threads.sleep(100);
        }
        throw new IOException("Region server did not stop within timeout: " + serverName);
      }
    }
  }

  @Override
  public void startMaster(String hostname, int port) throws IOException {
    throw new UnsupportedOperationException(
        "startMaster(hostname, port) not supported. Use startMaster() instead.");
  }

  @Override
  public void killMaster(ServerName serverName) throws IOException {
    for (MasterProcessManager master : masterProcesses) {
      if (serverName.equals(master.getServerName())) {
        master.killProcess();
        LOG.info("Killed master: {}", serverName);
        return;
      }
    }
    throw new IOException("Master not found: " + serverName);
  }

  @Override
  public void stopMaster(ServerName serverName) throws IOException {
    for (MasterProcessManager master : masterProcesses) {
      if (serverName.equals(master.getServerName())) {
        master.stop();
        LOG.info("Stopped master: {}", serverName);
        return;
      }
    }
    throw new IOException("Master not found: " + serverName);
  }

  @Override
  public void waitForMasterToStop(ServerName serverName, long timeout) throws IOException {
    long deadline = System.currentTimeMillis() + timeout;

    for (MasterProcessManager master : masterProcesses) {
      if (serverName.equals(master.getServerName())) {
        while (System.currentTimeMillis() < deadline) {
          if (!master.isProcessAlive()) {
            LOG.info("Master stopped: {}", serverName);
            return;
          }
          Threads.sleep(100);
        }
        throw new IOException("Master did not stop within timeout: " + serverName);
      }
    }
  }

  @Override
  public ServerName getServerHoldingRegion(TableName tn, byte[] regionName) throws IOException {
    try (Connection conn = getConnection()) {
      return conn.getRegionLocator(tn).getRegionLocation(regionName).getServerName();
    }
  }

  @Override
  public void waitUntilShutDown() {
    while (started) {
      for (MasterProcessManager master : masterProcesses) {
        if (master.isProcessAlive()) {
          Threads.sleep(1000);
          continue;
        }
      }
      break;
    }
  }

  // ========== Node Restart/Upgrade Methods ==========

  /**
   * Restart a RegionServer with the same version.
   * Node identity (ports, address) is preserved.
   *
   * @param rsIndex Index of the region server to restart
   * @throws IOException if restart fails
   */
  public void restartRegionServer(int rsIndex) throws IOException {
    if (rsIndex < 0 || rsIndex >= regionServerProcesses.size()) {
      throw new IOException("Invalid region server index: " + rsIndex);
    }

    LOG.info("Restarting RegionServer {}", rsIndex);

    RegionServerProcessManager rs = regionServerProcesses.get(rsIndex);
    ServerName oldServerName = rs.getServerName();

    // Stop the process
    rs.stop();
    waitForProcessToStop(rs, 30000);

    // Start with the same version (identity preserved)
    String version = rsVersions.get(rsIndex);
    HBaseDistribution dist = versionRegistry.get(version);

    if (dist == null) {
      throw new IOException("HBase distribution not found: " + version);
    }

    File workDir = rs.getWorkDir();
    Configuration rsConf = configGenerator.generateRegionServerConfig(rsIndex, workDir);

    RegionServerProcessManager newRs =
        new RegionServerProcessManager(rsConf, dist.getHbaseHome().getAbsolutePath(), workDir,
            rsIndex);

    newRs.start();
    regionServerProcesses.set(rsIndex, newRs);

    LOG.info("RegionServer {} restarted: old={}, new={}", rsIndex, oldServerName,
        newRs.getServerName());
  }

  /**
   * Restart a Master with the same version.
   * Node identity (ports, address) is preserved.
   *
   * @param masterIndex Index of the master to restart
   * @throws IOException if restart fails
   */
  public void restartMaster(int masterIndex) throws IOException {
    if (masterIndex < 0 || masterIndex >= masterProcesses.size()) {
      throw new IOException("Invalid master index: " + masterIndex);
    }

    LOG.info("Restarting Master {}", masterIndex);

    MasterProcessManager master = masterProcesses.get(masterIndex);
    ServerName oldServerName = master.getServerName();

    // Stop the process
    master.stop();
    waitForProcessToStop(master, 30000);

    // Start with the same version (identity preserved)
    String version = masterVersions.get(masterIndex);
    HBaseDistribution dist = versionRegistry.get(version);

    if (dist == null) {
      throw new IOException("HBase distribution not found: " + version);
    }

    File workDir = master.getWorkDir();
    Configuration masterConf = configGenerator.generateMasterConfig(masterIndex, workDir);

    MasterProcessManager newMaster =
        new MasterProcessManager(masterConf, dist.getHbaseHome().getAbsolutePath(), workDir,
            masterIndex);

    newMaster.start();
    masterProcesses.set(masterIndex, newMaster);

    LOG.info("Master {} restarted: old={}, new={}", masterIndex, oldServerName,
        newMaster.getServerName());
  }

  /**
   * Change the HBase version for a RegionServer and restart it.
   * Node identity (ports, address) is preserved.
   * This is the key method for rolling upgrade testing.
   *
   * @param rsIndex Index of the region server
   * @param newVersion New HBase version (must be registered)
   * @throws IOException if version change fails
   */
  public void changeRegionServerVersion(int rsIndex, String newVersion) throws IOException {
    if (rsIndex < 0 || rsIndex >= regionServerProcesses.size()) {
      throw new IOException("Invalid region server index: " + rsIndex);
    }

    // Auto-register the new version if not already registered
    if (!versionRegistry.isRegistered(newVersion)) {
      LOG.info("Auto-registering HBase distribution: {}", newVersion);
      versionRegistry.register(newVersion);
    }

    String oldVersion = rsVersions.get(rsIndex);
    LOG.info("Changing RegionServer {} version: {} -> {}", rsIndex, oldVersion, newVersion);

    RegionServerProcessManager rs = regionServerProcesses.get(rsIndex);
    ServerName oldServerName = rs.getServerName();

    // Stop the old version
    rs.stop();
    waitForProcessToStop(rs, 30000);

    // Update version mapping
    rsVersions.put(rsIndex, newVersion);

    // Start with new version (identity preserved via port persistence)
    HBaseDistribution dist = versionRegistry.get(newVersion);
    File workDir = rs.getWorkDir();
    Configuration rsConf = configGenerator.generateRegionServerConfig(rsIndex, workDir);

    RegionServerProcessManager newRs =
        new RegionServerProcessManager(rsConf, dist.getHbaseHome().getAbsolutePath(), workDir,
            rsIndex);

    newRs.start();
    regionServerProcesses.set(rsIndex, newRs);

    LOG.info("RegionServer {} version changed successfully: {} -> {}, ServerName: {} -> {}",
        rsIndex, oldVersion, newVersion, oldServerName, newRs.getServerName());
  }

  /**
   * Change the HBase version for a Master and restart it.
   * Node identity (ports, address) is preserved.
   *
   * @param masterIndex Index of the master
   * @param newVersion New HBase version (must be registered)
   * @throws IOException if version change fails
   */
  public void changeMasterVersion(int masterIndex, String newVersion) throws IOException {
    if (masterIndex < 0 || masterIndex >= masterProcesses.size()) {
      throw new IOException("Invalid master index: " + masterIndex);
    }

    // Auto-register the new version if not already registered
    if (!versionRegistry.isRegistered(newVersion)) {
      LOG.info("Auto-registering HBase distribution: {}", newVersion);
      versionRegistry.register(newVersion);
    }

    String oldVersion = masterVersions.get(masterIndex);
    LOG.info("Changing Master {} version: {} -> {}", masterIndex, oldVersion, newVersion);

    MasterProcessManager master = masterProcesses.get(masterIndex);
    ServerName oldServerName = master.getServerName();

    // Stop the old version
    master.stop();
    waitForProcessToStop(master, 30000);

    // Update version mapping
    masterVersions.put(masterIndex, newVersion);

    // Start with new version (identity preserved via port persistence)
    HBaseDistribution dist = versionRegistry.get(newVersion);
    File workDir = master.getWorkDir();
    Configuration masterConf = configGenerator.generateMasterConfig(masterIndex, workDir);

    MasterProcessManager newMaster =
        new MasterProcessManager(masterConf, dist.getHbaseHome().getAbsolutePath(), workDir,
            masterIndex);

    newMaster.start();
    masterProcesses.set(masterIndex, newMaster);

    LOG.info("Master {} version changed successfully: {} -> {}, ServerName: {} -> {}",
        masterIndex, oldVersion, newVersion, oldServerName, newMaster.getServerName());
  }

  /**
   * Get the current version for a RegionServer.
   */
  public String getRegionServerVersion(int rsIndex) {
    return rsVersions.get(rsIndex);
  }

  /**
   * Get the current version for a Master.
   */
  public String getMasterVersion(int masterIndex) {
    return masterVersions.get(masterIndex);
  }

  /**
   * Get the number of RegionServers in the cluster.
   */
  public int getNumRegionServers() {
    return regionServerProcesses.size();
  }

  /**
   * Get the number of Masters in the cluster.
   */
  public int getNumMasters() {
    return masterProcesses.size();
  }

  /**
   * Get ServerName for a specific RegionServer.
   */
  public ServerName getRegionServerName(int rsIndex) throws IOException {
    if (rsIndex < 0 || rsIndex >= regionServerProcesses.size()) {
      throw new IOException("Invalid region server index: " + rsIndex);
    }
    return regionServerProcesses.get(rsIndex).getServerName();
  }

  /**
   * Get ServerName for a specific Master.
   */
  public ServerName getMasterName(int masterIndex) throws IOException {
    if (masterIndex < 0 || masterIndex >= masterProcesses.size()) {
      throw new IOException("Invalid master index: " + masterIndex);
    }
    return masterProcesses.get(masterIndex).getServerName();
  }

  /**
   * Wait for a process to stop.
   */
  private void waitForProcessToStop(ProcessNodeManager process, long timeoutMs)
      throws IOException {
    long deadline = System.currentTimeMillis() + timeoutMs;

    while (System.currentTimeMillis() < deadline) {
      if (!process.isProcessAlive()) {
        return;
      }
      Threads.sleep(100);
    }

    throw new IOException(
        "Process did not stop within timeout: " + process.getNodeType() + " " +
        process.getNodeIndex());
  }

  // Unsupported methods - require direct process access
  @Override
  public void startZkNode(String hostname, int port) throws IOException {
    throw new UnsupportedOperationException(
        "ZooKeeper node management not supported. Use external ZooKeeper.");
  }

  @Override
  public void killZkNode(ServerName serverName) throws IOException {
    throw new UnsupportedOperationException(
        "ZooKeeper node management not supported. Use external ZooKeeper.");
  }

  @Override
  public void stopZkNode(ServerName serverName) throws IOException {
    throw new UnsupportedOperationException(
        "ZooKeeper node management not supported. Use external ZooKeeper.");
  }

  @Override
  public void waitForZkNodeToStart(ServerName serverName, long timeout) throws IOException {
    throw new UnsupportedOperationException(
        "ZooKeeper node management not supported. Use external ZooKeeper.");
  }

  @Override
  public void waitForZkNodeToStop(ServerName serverName, long timeout) throws IOException {
    throw new UnsupportedOperationException(
        "ZooKeeper node management not supported. Use external ZooKeeper.");
  }

  @Override
  public void startDataNode(ServerName serverName) throws IOException {
    throw new UnsupportedOperationException("DataNode management not supported.");
  }

  @Override
  public void killDataNode(ServerName serverName) throws IOException {
    throw new UnsupportedOperationException("DataNode management not supported.");
  }

  @Override
  public void stopDataNode(ServerName serverName) throws IOException {
    throw new UnsupportedOperationException("DataNode management not supported.");
  }

  @Override
  public void waitForDataNodeToStart(ServerName serverName, long timeout) throws IOException {
    throw new UnsupportedOperationException("DataNode management not supported.");
  }

  @Override
  public void waitForDataNodeToStop(ServerName serverName, long timeout) throws IOException {
    throw new UnsupportedOperationException("DataNode management not supported.");
  }

  @Override
  public void startNameNode(ServerName serverName) throws IOException {
    throw new UnsupportedOperationException("NameNode management not supported.");
  }

  @Override
  public void killNameNode(ServerName serverName) throws IOException {
    throw new UnsupportedOperationException("NameNode management not supported.");
  }

  @Override
  public void stopNameNode(ServerName serverName) throws IOException {
    throw new UnsupportedOperationException("NameNode management not supported.");
  }

  @Override
  public void waitForNameNodeToStart(ServerName serverName, long timeout) throws IOException {
    throw new UnsupportedOperationException("NameNode management not supported.");
  }

  @Override
  public void waitForNameNodeToStop(ServerName serverName, long timeout) throws IOException {
    throw new UnsupportedOperationException("NameNode management not supported.");
  }

  @Override
  public void startJournalNode(ServerName serverName) {
    throw new UnsupportedOperationException("JournalNode management not supported.");
  }

  @Override
  public void killJournalNode(ServerName serverName) {
    throw new UnsupportedOperationException("JournalNode management not supported.");
  }

  @Override
  public void stopJournalNode(ServerName serverName) {
    throw new UnsupportedOperationException("JournalNode management not supported.");
  }

  @Override
  public void waitForJournalNodeToStart(ServerName serverName, long timeout) {
    throw new UnsupportedOperationException("JournalNode management not supported.");
  }

  @Override
  public void waitForJournalNodeToStop(ServerName serverName, long timeout) {
    throw new UnsupportedOperationException("JournalNode management not supported.");
  }

  @Override
  public boolean isKilledRS(ServerName serverName) {
    // Not applicable for process-based cluster
    return false;
  }

  /**
   * Check if the cluster is up and healthy.
   * @return true if cluster is operational, false otherwise
   */
  public boolean isClusterUp() {
    if (!started) {
      return false;
    }

    try (Connection conn = ConnectionFactory.createConnection(conf)) {
      Admin admin = conn.getAdmin();
      ClusterMetrics metrics = admin.getClusterMetrics();

      // Check master is available
      ServerName masterName = metrics.getMasterName();
      if (masterName == null || masterName.getHostname().isEmpty()) {
        return false;
      }

      // Check all region servers are online
      int expectedRs = rsVersions.size();
      int actualRs = metrics.getLiveServerMetrics().size();

      return actualRs >= expectedRs;
    } catch (Exception e) {
      LOG.debug("Cluster health check failed", e);
      return false;
    }
  }

  /**
   * Get the number of live region servers.
   * @return number of live region servers
   * @throws IOException if unable to get cluster metrics
   */
  public int getNumLiveRegionServers() throws IOException {
    try (Connection conn = ConnectionFactory.createConnection(conf)) {
      Admin admin = conn.getAdmin();
      ClusterMetrics metrics = admin.getClusterMetrics();
      return metrics.getLiveServerMetrics().size();
    }
  }

  /**
   * Perform a rolling upgrade of all RegionServers.
   * This method reads the target version from the system property "hbase.upgrade.home"
   * and performs a rolling upgrade of all region servers one by one.
   *
   * <p>Each RegionServer is stopped, restarted with the new version, and verified
   * to be healthy before moving to the next. Node identity (hostname:port) is
   * preserved during the upgrade.
   *
   * @throws IOException if the upgrade fails
   */
  public void upgrade() throws IOException {
    String upgradeHome = System.getProperty("hbase.upgrade.home");
    if (upgradeHome == null || upgradeHome.isEmpty()) {
      throw new IOException("System property 'hbase.upgrade.home' is not set. "
          + "Please set it to the HBase distribution to upgrade to.");
    }

    LOG.info("Starting rolling upgrade to version: {}", upgradeHome);

    // Register the upgrade version if not already registered
    if (!versionRegistry.isRegistered(upgradeHome)) {
      versionRegistry.register(upgradeHome);
    }

    // Perform rolling upgrade of region servers
    for (int i = 0; i < regionServerProcesses.size(); i++) {
      LOG.info("Upgrading RegionServer {}/{}", i + 1, regionServerProcesses.size());

      // Change version (this stops, updates version, and restarts)
      changeRegionServerVersion(i, upgradeHome);

      // Wait for cluster to stabilize
      waitClusterUp();

      LOG.info("RegionServer {} upgraded successfully", i);
    }

    // Optionally upgrade masters (if specified)
    String upgradeMasters = System.getProperty("hbase.upgrade.masters", "false");
    if (Boolean.parseBoolean(upgradeMasters)) {
      LOG.info("Also upgrading masters");
      for (int i = 0; i < masterProcesses.size(); i++) {
        LOG.info("Upgrading Master {}/{}", i + 1, masterProcesses.size());
        changeMasterVersion(i, upgradeHome);
        waitForActiveAndReadyMaster(60000);
        LOG.info("Master {} upgraded successfully", i);
      }
    }

    LOG.info("Rolling upgrade completed successfully");
  }

  @Override
  public void suspendRegionServer(ServerName serverName) throws IOException {
    throw new UnsupportedOperationException(
        "Region server suspension not supported in ProcessBasedMiniHBaseCluster.");
  }

  @Override
  public void resumeRegionServer(ServerName serverName) throws IOException {
    throw new UnsupportedOperationException(
        "Region server resume not supported in ProcessBasedMiniHBaseCluster.");
  }

  @Override
  public void waitForRegionServerToSuspend(ServerName serverName, long timeout) throws IOException {
    throw new UnsupportedOperationException(
        "Region server suspension not supported in ProcessBasedMiniHBaseCluster.");
  }

  @Override
  public void waitForRegionServerToResume(ServerName serverName, long timeout) throws IOException {
    throw new UnsupportedOperationException(
        "Region server resume not supported in ProcessBasedMiniHBaseCluster.");
  }

  /**
   * Builder for ProcessBasedMiniHBaseCluster.
   */
  public static class Builder {
    private final Configuration conf;
    private int numMasters = 1;
    private int numRegionServers = 0;
    private String defaultHBaseHome = null;
    private final Map<String, String> versionRegistry = new HashMap<>();
    private final Map<Integer, String> masterVersions = new HashMap<>();
    private final Map<Integer, String> rsVersions = new HashMap<>();

    public Builder(Configuration conf) {
      this.conf = HBaseConfiguration.create(conf);
    }

    public Builder numMasters(int numMasters) {
      this.numMasters = numMasters;
      return this;
    }

    public Builder numRegionServers(int numRegionServers) {
      this.numRegionServers = numRegionServers;
      return this;
    }

    public Builder allNodesHBaseDistribution(String hbaseHome) {
      this.defaultHBaseHome = hbaseHome;
      return this;
    }

    public Builder registerHBaseVersion(String version, String hbaseHome) {
      this.versionRegistry.put(version, hbaseHome);
      return this;
    }

    public Builder masterHBaseDistribution(String hbaseHome) {
      return masterHBaseDistribution(0, hbaseHome);
    }

    public Builder masterHBaseDistribution(int masterIndex, String hbaseHome) {
      this.masterVersions.put(masterIndex, hbaseHome);
      return this;
    }

    public Builder regionServerHBaseDistribution(int rsIndex, String hbaseHome) {
      this.rsVersions.put(rsIndex, hbaseHome);
      return this;
    }

    public ProcessBasedMiniHBaseCluster build() throws IOException {
      // Auto-read hbase.start.home system property if no default is set
      if (defaultHBaseHome == null) {
        String hbaseHome = System.getProperty("hbase.start.home");
        if (hbaseHome != null && !hbaseHome.isEmpty()) {
          this.defaultHBaseHome = hbaseHome;
        }
      }
      return new ProcessBasedMiniHBaseCluster(conf, this);
    }
  }
}
