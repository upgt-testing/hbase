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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for managing multiple HBase distributions.
 * Allows different nodes to use different HBase versions by referencing
 * different installation directories.
 */
@InterfaceAudience.Private
public class HBaseVersionRegistry {
  private static final Logger LOG = LoggerFactory.getLogger(HBaseVersionRegistry.class);

  private final Map<String, HBaseDistribution> distributions;
  private String defaultVersion;

  /**
   * Create a new HBaseVersionRegistry.
   */
  public HBaseVersionRegistry() {
    this.distributions = new HashMap<>();
  }

  /**
   * Register an HBase distribution.
   *
   * @param version Version identifier (e.g., "2.6.0", "3.0.0")
   * @param hbaseHome Path to HBase installation directory
   * @throws IOException if the distribution is invalid
   */
  public void register(String version, String hbaseHome) throws IOException {
    if (version == null || version.isEmpty()) {
      throw new IllegalArgumentException("Version cannot be null or empty");
    }
    if (hbaseHome == null || hbaseHome.isEmpty()) {
      throw new IllegalArgumentException("HBase home cannot be null or empty");
    }

    LOG.info("Registering HBase distribution: version={}, home={}", version, hbaseHome);

    HBaseDistribution dist = new HBaseDistribution(version, hbaseHome);
    dist.validate();

    distributions.put(version, dist);

    // Set first registered version as default
    if (defaultVersion == null) {
      defaultVersion = version;
      LOG.info("Set default HBase version: {}", version);
    }
  }

  /**
   * Register an HBase distribution using the directory path as the version identifier.
   *
   * @param hbaseHome Path to HBase installation directory
   * @throws IOException if the distribution is invalid
   */
  public void register(String hbaseHome) throws IOException {
    // Use the hbase home path as the version identifier
    register(hbaseHome, hbaseHome);
  }

  /**
   * Get a registered HBase distribution.
   *
   * @param version Version identifier
   * @return HBaseDistribution, or null if not found
   */
  public HBaseDistribution get(String version) {
    return distributions.get(version);
  }

  /**
   * Get the default HBase distribution.
   *
   * @return Default HBaseDistribution, or null if none registered
   */
  public HBaseDistribution getDefault() {
    return defaultVersion != null ? distributions.get(defaultVersion) : null;
  }

  /**
   * Set the default version.
   *
   * @param version Version to set as default
   * @throws IllegalArgumentException if version is not registered
   */
  public void setDefault(String version) {
    if (!distributions.containsKey(version)) {
      throw new IllegalArgumentException("Version not registered: " + version);
    }
    this.defaultVersion = version;
    LOG.info("Set default HBase version: {}", version);
  }

  /**
   * Check if a version is registered.
   */
  public boolean isRegistered(String version) {
    return distributions.containsKey(version);
  }

  /**
   * Get all JAR files for a specific version.
   *
   * @param version Version identifier
   * @return List of JAR files, or null if version not found
   */
  public List<File> getJars(String version) {
    HBaseDistribution dist = distributions.get(version);
    return dist != null ? dist.getAllJars() : null;
  }

  /**
   * Get dependency JAR files for a specific version.
   *
   * @param version Version identifier
   * @return List of dependency JAR files, or null if version not found
   */
  public List<File> getDependencies(String version) {
    HBaseDistribution dist = distributions.get(version);
    return dist != null ? dist.getDependencies() : null;
  }

  /**
   * Build classpath string for a specific version.
   *
   * @param version Version identifier
   * @return Classpath string, or null if version not found
   */
  public String buildClasspathString(String version) {
    return buildClasspathString(version, null);
  }

  /**
   * Build classpath string for a specific version.
   *
   * @param version Version identifier
   * @param additionalPaths Additional paths to include in classpath
   * @return Classpath string, or null if version not found
   */
  public String buildClasspathString(String version, List<File> additionalPaths) {
    HBaseDistribution dist = distributions.get(version);
    return dist != null ? dist.buildClasspathString(additionalPaths) : null;
  }

  /**
   * Get the number of registered distributions.
   */
  public int size() {
    return distributions.size();
  }

  /**
   * Clear all registered distributions.
   */
  public void clear() {
    distributions.clear();
    defaultVersion = null;
    LOG.info("Cleared all registered distributions");
  }

  /**
   * Get all registered version identifiers.
   */
  public String[] getVersions() {
    return distributions.keySet().toArray(new String[0]);
  }

  @Override
  public String toString() {
    return "HBaseVersionRegistry{" +
        "distributions=" + distributions.size() +
        ", defaultVersion='" + defaultVersion + '\'' +
        ", versions=" + String.join(", ", distributions.keySet()) +
        '}';
  }
}
