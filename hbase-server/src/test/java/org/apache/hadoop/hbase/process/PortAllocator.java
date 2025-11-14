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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allocates and manages ports for HBase processes.
 * Supports port persistence to ensure nodes maintain the same ports across restarts,
 * which is critical for node identity preservation during upgrades.
 */
@InterfaceAudience.Private
public class PortAllocator {
  private static final Logger LOG = LoggerFactory.getLogger(PortAllocator.class);

  private static final int DEFAULT_START_PORT = 50000;
  private static final int DEFAULT_END_PORT = 59999;

  private final File workDir;
  private final Set<Integer> usedPorts;
  private int nextPort;

  /**
   * Create a PortAllocator.
   *
   * @param workDir Directory to store port allocation metadata
   */
  public PortAllocator(File workDir) {
    this(workDir, DEFAULT_START_PORT);
  }

  /**
   * Create a PortAllocator with a custom starting port.
   *
   * @param workDir Directory to store port allocation metadata
   * @param startPort Starting port for allocation
   */
  public PortAllocator(File workDir, int startPort) {
    this.workDir = workDir;
    this.usedPorts = new HashSet<>();
    this.nextPort = startPort;
  }

  /**
   * Allocate a port for a node.
   * If the node has a persisted port allocation, that port is reused if available.
   * Otherwise, a new port is allocated and persisted.
   *
   * @param nodeId Unique identifier for the node (e.g., "master0", "rs0")
   * @param portType Type of port (e.g., "master", "master-info", "regionserver")
   * @return Allocated port number
   * @throws IOException if port allocation fails or persisted port is unavailable
   */
  public int allocatePort(String nodeId, String portType) throws IOException {
    // Check if port already allocated for this node
    Integer existingPort = loadPersistedPort(nodeId, portType);
    if (existingPort != null) {
      if (isPortAvailable(existingPort)) {
        usedPorts.add(existingPort);
        LOG.info("Reusing persisted port for {} {}: {}", nodeId, portType, existingPort);
        return existingPort;
      } else {
        throw new IOException(
            "Cannot restart node " + nodeId + ": " +
            "Port " + existingPort + " is in use. " +
            "Node identity cannot be preserved. " +
            "Other nodes will see this as a new node, not a restart.");
      }
    }

    // Allocate new port
    int port = findNextAvailablePort();
    usedPorts.add(port);

    // Persist port allocation
    persistPort(nodeId, portType, port);

    LOG.info("Allocated new port for {} {}: {}", nodeId, portType, port);
    return port;
  }

  /**
   * Find the next available port.
   *
   * @return Available port number
   * @throws IOException if no ports available
   */
  private int findNextAvailablePort() throws IOException {
    int attempts = 0;
    int maxAttempts = DEFAULT_END_PORT - DEFAULT_START_PORT;

    while (attempts < maxAttempts) {
      if (!usedPorts.contains(nextPort) && isPortAvailable(nextPort)) {
        int allocatedPort = nextPort;
        nextPort++;
        return allocatedPort;
      }
      nextPort++;
      if (nextPort > DEFAULT_END_PORT) {
        nextPort = DEFAULT_START_PORT;
      }
      attempts++;
    }

    throw new IOException("No available ports in range " + DEFAULT_START_PORT +
        "-" + DEFAULT_END_PORT);
  }

  /**
   * Check if a port is available for binding.
   *
   * @param port Port to check
   * @return true if port is available
   */
  public boolean isPortAvailable(int port) {
    try (ServerSocket socket = new ServerSocket(port)) {
      socket.setReuseAddress(true);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Load persisted port for a node.
   *
   * @param nodeId Node identifier
   * @param portType Port type
   * @return Port number, or null if not persisted
   */
  private Integer loadPersistedPort(String nodeId, String portType) {
    File nodeDir = new File(workDir, nodeId);
    File portFile = new File(nodeDir, "ports.properties");

    if (!portFile.exists()) {
      return null;
    }

    Properties props = new Properties();
    try (FileInputStream fis = new FileInputStream(portFile)) {
      props.load(fis);
      String portStr = props.getProperty(portType + ".port");
      if (portStr != null) {
        return Integer.parseInt(portStr);
      }
    } catch (IOException | NumberFormatException e) {
      LOG.warn("Failed to load persisted port for {} {}", nodeId, portType, e);
    }

    return null;
  }

  /**
   * Persist port allocation to disk.
   * Critical for node identity preservation across restarts/upgrades.
   *
   * @param nodeId Node identifier
   * @param portType Port type
   * @param port Port number
   * @throws IOException if persistence fails
   */
  private void persistPort(String nodeId, String portType, int port) throws IOException {
    File nodeDir = new File(workDir, nodeId);
    if (!nodeDir.exists()) {
      if (!nodeDir.mkdirs()) {
        throw new IOException("Failed to create node directory: " + nodeDir);
      }
    }

    File portFile = new File(nodeDir, "ports.properties");
    Properties props = new Properties();

    // Load existing ports
    if (portFile.exists()) {
      try (FileInputStream fis = new FileInputStream(portFile)) {
        props.load(fis);
      }
    }

    // Add/update this port
    props.setProperty(portType + ".port", String.valueOf(port));

    // Save
    try (FileOutputStream fos = new FileOutputStream(portFile)) {
      props.store(fos, "Port allocation for " + nodeId);
    }

    LOG.debug("Persisted port for {} {}: {}", nodeId, portType, port);
  }

  /**
   * Release a port back to the pool.
   *
   * @param port Port to release
   */
  public void releasePort(int port) {
    usedPorts.remove(port);
    LOG.debug("Released port: {}", port);
  }

  /**
   * Get the set of currently used ports.
   */
  public Set<Integer> getUsedPorts() {
    return new HashSet<>(usedPorts);
  }

  /**
   * Clear all port allocations (does not delete persisted files).
   */
  public void clear() {
    usedPorts.clear();
    nextPort = DEFAULT_START_PORT;
  }
}
