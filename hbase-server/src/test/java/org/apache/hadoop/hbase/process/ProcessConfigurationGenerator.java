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
import java.io.FileOutputStream;
import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates configuration files for HBase node processes.
 * Creates node-specific hbase-site.xml files with appropriate settings
 * for Masters and RegionServers.
 */
@InterfaceAudience.Private
public class ProcessConfigurationGenerator {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessConfigurationGenerator.class);

  private final Configuration baseConfig;
  private final PortAllocator portAllocator;

  /**
   * Create a ProcessConfigurationGenerator.
   *
   * @param baseConfig Base configuration to use as template
   * @param portAllocator Port allocator for assigning ports
   */
  public ProcessConfigurationGenerator(Configuration baseConfig, PortAllocator portAllocator) {
    this.baseConfig = baseConfig;
    this.portAllocator = portAllocator;
  }

  /**
   * Generate configuration for a Master node.
   *
   * @param masterIndex Index of the master
   * @param workDir Work directory for this master
   * @return Configuration for the master
   * @throws IOException if configuration generation fails
   */
  public Configuration generateMasterConfig(int masterIndex, File workDir) throws IOException {
    LOG.info("Generating Master configuration: index={}, workDir={}", masterIndex, workDir);

    Configuration conf = HBaseConfiguration.create(baseConfig);

    // Node identifier
    String nodeId = "master" + masterIndex;

    // Allocate ports
    int masterPort = portAllocator.allocatePort(nodeId, "master");
    int masterInfoPort = portAllocator.allocatePort(nodeId, "master-info");

    // Set Master-specific configuration
    conf.setInt(HConstants.MASTER_PORT, masterPort);
    conf.setInt(HConstants.MASTER_INFO_PORT, masterInfoPort);

    // DON'T override hbase.rootdir here - all nodes must share the same rootdir
    // The baseConfig already has the correct shared hbase.rootdir set

    // Set distributed mode
    conf.setBoolean(HConstants.CLUSTER_DISTRIBUTED, true);

    // Explicitly copy ZooKeeper configuration from baseConfig
    // (writeXml only writes explicitly set properties, not inherited ones)
    copyZooKeeperConfig(baseConfig, conf);

    // Write configuration to file
    writeConfigToFile(conf, workDir);

    LOG.info("Generated Master config: masterPort={}, infoPort={}", masterPort, masterInfoPort);
    return conf;
  }

  /**
   * Generate configuration for a RegionServer node.
   *
   * @param rsIndex Index of the region server
   * @param workDir Work directory for this region server
   * @return Configuration for the region server
   * @throws IOException if configuration generation fails
   */
  public Configuration generateRegionServerConfig(int rsIndex, File workDir) throws IOException {
    LOG.info("Generating RegionServer configuration: index={}, workDir={}", rsIndex, workDir);

    Configuration conf = HBaseConfiguration.create(baseConfig);

    // Node identifier
    String nodeId = "rs" + rsIndex;

    // Allocate ports
    int rsPort = portAllocator.allocatePort(nodeId, "regionserver");
    int rsInfoPort = portAllocator.allocatePort(nodeId, "regionserver-info");

    // Set RegionServer-specific configuration
    conf.setInt(HConstants.REGIONSERVER_PORT, rsPort);
    conf.setInt(HConstants.REGIONSERVER_INFO_PORT, rsInfoPort);

    // DON'T override hbase.rootdir here - all nodes must share the same rootdir
    // The baseConfig already has the correct shared hbase.rootdir set

    // Set distributed mode
    conf.setBoolean(HConstants.CLUSTER_DISTRIBUTED, true);

    // Explicitly copy ZooKeeper configuration from baseConfig
    // (writeXml only writes explicitly set properties, not inherited ones)
    copyZooKeeperConfig(baseConfig, conf);

    // Write configuration to file
    writeConfigToFile(conf, workDir);

    LOG.info("Generated RegionServer config: rsPort={}, infoPort={}", rsPort, rsInfoPort);
    return conf;
  }

  /**
   * Write configuration to hbase-site.xml file.
   *
   * @param conf Configuration to write
   * @param workDir Work directory (config will be written to workDir/conf/)
   * @return The configuration file that was written
   * @throws IOException if write fails
   */
  public File writeConfigToFile(Configuration conf, File workDir) throws IOException {
    File confDir = new File(workDir, "conf");
    if (!confDir.exists()) {
      if (!confDir.mkdirs()) {
        throw new IOException("Failed to create config directory: " + confDir);
      }
    }

    File configFile = new File(confDir, "hbase-site.xml");
    try (FileOutputStream fos = new FileOutputStream(configFile)) {
      conf.writeXml(fos);
    }

    LOG.info("Wrote configuration file: {}", configFile);
    return configFile;
  }

  /**
   * Update an existing configuration with new settings.
   *
   * @param conf Configuration to update
   * @param key Configuration key
   * @param value Configuration value
   */
  public void updateConfig(Configuration conf, String key, String value) {
    conf.set(key, value);
  }

  /**
   * Update configuration file on disk.
   *
   * @param conf Configuration to write
   * @param configFile Configuration file to update
   * @throws IOException if write fails
   */
  public void updateConfigFile(Configuration conf, File configFile) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(configFile)) {
      conf.writeXml(fos);
    }
    LOG.info("Updated configuration file: {}", configFile);
  }

  /**
   * Copy ZooKeeper configuration from source to destination.
   * This is necessary because Configuration.writeXml() only writes explicitly set properties,
   * not inherited ones. We need to explicitly copy ZK settings to ensure they are written.
   *
   * @param source Source configuration (has ZK settings)
   * @param dest Destination configuration (will receive ZK settings)
   */
  private void copyZooKeeperConfig(Configuration source, Configuration dest) {
    // Copy ZooKeeper quorum
    String zkQuorum = source.get(HConstants.ZOOKEEPER_QUORUM);
    if (zkQuorum != null) {
      dest.set(HConstants.ZOOKEEPER_QUORUM, zkQuorum);
      LOG.debug("Copied ZooKeeper quorum: {}", zkQuorum);
    }

    // Copy ZooKeeper client port
    String zkClientPort = source.get(HConstants.ZOOKEEPER_CLIENT_PORT);
    if (zkClientPort != null) {
      dest.set(HConstants.ZOOKEEPER_CLIENT_PORT, zkClientPort);
      LOG.debug("Copied ZooKeeper client port: {}", zkClientPort);
    }

    // Copy ZooKeeper znode parent
    String zkZnodeParent = source.get(HConstants.ZOOKEEPER_ZNODE_PARENT);
    if (zkZnodeParent != null) {
      dest.set(HConstants.ZOOKEEPER_ZNODE_PARENT, zkZnodeParent);
      LOG.debug("Copied ZooKeeper znode parent: {}", zkZnodeParent);
    }

    LOG.info("Copied ZooKeeper configuration from source to destination");
  }
}
