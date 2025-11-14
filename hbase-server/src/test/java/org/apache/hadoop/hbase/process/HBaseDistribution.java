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
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an HBase distribution installation.
 * Provides methods to discover and access JARs, dependencies, and configuration.
 */
@InterfaceAudience.Private
public class HBaseDistribution {
  private static final Logger LOG = LoggerFactory.getLogger(HBaseDistribution.class);

  private final String version;
  private final File hbaseHome;
  private final List<File> coreJars;
  private final List<File> dependencies;
  private final File nativeLibDir;

  /**
   * Create an HBaseDistribution from an HBase installation directory.
   *
   * @param version Version identifier (e.g., "2.6.0", "3.0.0")
   * @param hbaseHome Path to HBase installation directory
   * @throws IOException if the directory is invalid or missing required files
   */
  public HBaseDistribution(String version, String hbaseHome) throws IOException {
    this.version = version;
    this.hbaseHome = new File(hbaseHome);

    if (!this.hbaseHome.exists() || !this.hbaseHome.isDirectory()) {
      throw new IOException("HBase home directory does not exist: " + hbaseHome);
    }

    // Discover core JARs
    this.coreJars = discoverCoreJars();
    if (this.coreJars.isEmpty()) {
      throw new IOException("No HBase core JARs found in: " + hbaseHome);
    }

    // Discover dependencies
    this.dependencies = discoverDependencies();

    // Find native library directory
    this.nativeLibDir = new File(this.hbaseHome, "lib/native");

    LOG.info("Loaded HBase distribution: version={}, home={}, coreJars={}, dependencies={}",
        version, hbaseHome, coreJars.size(), dependencies.size());
  }

  /**
   * Discover core HBase JAR files.
   */
  private List<File> discoverCoreJars() {
    List<File> jars = new ArrayList<>();

    File libDir = new File(hbaseHome, "lib");
    if (!libDir.exists() || !libDir.isDirectory()) {
      LOG.warn("lib directory not found: {}", libDir);
      return jars;
    }

    // Look for core HBase JARs
    String[] corePatterns = {
        "hbase-server-*.jar",
        "hbase-common-*.jar",
        "hbase-protocol-*.jar",
        "hbase-protocol-shaded-*.jar",
        "hbase-client-*.jar",
        "hbase-hadoop-compat-*.jar",
        "hbase-hadoop2-compat-*.jar",
        "hbase-metrics-*.jar",
        "hbase-metrics-api-*.jar",
        "hbase-zookeeper-*.jar"
    };

    for (String pattern : corePatterns) {
      File[] matchingJars = findMatchingJars(libDir, pattern);
      if (matchingJars != null) {
        for (File jar : matchingJars) {
          jars.add(jar);
          LOG.debug("Found core JAR: {}", jar.getName());
        }
      }
    }

    return jars;
  }

  /**
   * Discover dependency JAR files.
   */
  private List<File> discoverDependencies() {
    List<File> deps = new ArrayList<>();

    File libDir = new File(hbaseHome, "lib");
    if (libDir.exists() && libDir.isDirectory()) {
      // Add all JARs from lib/ directory (excluding core JARs already added)
      File[] allJars = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
      if (allJars != null) {
        for (File jar : allJars) {
          if (!coreJars.contains(jar)) {
            deps.add(jar);
          }
        }
      }
    }

    // Add client-facing third-party dependencies
    File clientThirdPartyDir = new File(libDir, "client-facing-thirdparty");
    if (clientThirdPartyDir.exists() && clientThirdPartyDir.isDirectory()) {
      File[] thirdPartyJars = clientThirdPartyDir.listFiles((dir, name) -> name.endsWith(".jar"));
      if (thirdPartyJars != null) {
        for (File jar : thirdPartyJars) {
          deps.add(jar);
        }
      }
    }

    LOG.debug("Found {} dependency JARs", deps.size());
    return deps;
  }

  /**
   * Find JAR files matching a pattern.
   * Pattern supports simple wildcard (*) matching.
   */
  private File[] findMatchingJars(File dir, String pattern) {
    // Replace . first (to escape it), then * (to convert to .*)
    final String regex = pattern.replace(".", "\\.").replace("*", ".*");
    return dir.listFiles((d, name) -> name.matches(regex));
  }

  /**
   * Get all JARs for this distribution (core + dependencies).
   */
  public List<File> getAllJars() {
    List<File> allJars = new ArrayList<>();
    allJars.addAll(coreJars);
    allJars.addAll(dependencies);
    return allJars;
  }

  /**
   * Build classpath string for this distribution.
   */
  public String buildClasspathString() {
    return buildClasspathString(null);
  }

  /**
   * Build classpath string for this distribution.
   * @param additionalPaths Additional paths to include (e.g., config directory)
   */
  public String buildClasspathString(List<File> additionalPaths) {
    StringBuilder cp = new StringBuilder();

    // Add core JARs first
    for (File jar : coreJars) {
      if (cp.length() > 0) {
        cp.append(File.pathSeparator);
      }
      cp.append(jar.getAbsolutePath());
    }

    // Add dependencies
    for (File jar : dependencies) {
      cp.append(File.pathSeparator);
      cp.append(jar.getAbsolutePath());
    }

    // Add additional paths
    if (additionalPaths != null) {
      for (File path : additionalPaths) {
        cp.append(File.pathSeparator);
        cp.append(path.getAbsolutePath());
      }
    }

    return cp.toString();
  }

  /**
   * Validate this distribution has all required components.
   */
  public void validate() throws IOException {
    // Check for required core JARs
    boolean hasServer = false;
    boolean hasCommon = false;
    boolean hasClient = false;

    for (File jar : coreJars) {
      String name = jar.getName();
      if (name.startsWith("hbase-server-")) {
        hasServer = true;
      } else if (name.startsWith("hbase-common-")) {
        hasCommon = true;
      } else if (name.startsWith("hbase-client-")) {
        hasClient = true;
      }
    }

    if (!hasServer) {
      throw new IOException("Missing required hbase-server JAR in: " + hbaseHome);
    }
    if (!hasCommon) {
      throw new IOException("Missing required hbase-common JAR in: " + hbaseHome);
    }
    if (!hasClient) {
      throw new IOException("Missing required hbase-client JAR in: " + hbaseHome);
    }

    LOG.info("HBase distribution validation passed: {}", version);
  }

  public String getVersion() {
    return version;
  }

  public File getHbaseHome() {
    return hbaseHome;
  }

  public List<File> getCoreJars() {
    return new ArrayList<>(coreJars);
  }

  public List<File> getDependencies() {
    return new ArrayList<>(dependencies);
  }

  public File getNativeLibDir() {
    return nativeLibDir;
  }

  @Override
  public String toString() {
    return "HBaseDistribution{" +
        "version='" + version + '\'' +
        ", hbaseHome=" + hbaseHome +
        ", coreJars=" + coreJars.size() +
        ", dependencies=" + dependencies.size() +
        '}';
  }
}
