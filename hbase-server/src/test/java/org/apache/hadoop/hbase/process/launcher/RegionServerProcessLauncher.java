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
package org.apache.hadoop.hbase.process.launcher;

import java.io.File;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.regionserver.HRegionServer;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for HRegionServer process launched in a separate JVM.
 * This class is executed as the main class in the subprocess.
 *
 * Usage: java ... RegionServerProcessLauncher --config-dir <dir> --rs-index <index>
 */
@InterfaceAudience.Private
public class RegionServerProcessLauncher {
  private static final Logger LOG = LoggerFactory.getLogger(RegionServerProcessLauncher.class);

  public static void main(String[] args) {
    try {
      // Parse command line arguments
      String configDir = null;
      int rsIndex = 0;

      for (int i = 0; i < args.length; i++) {
        if ("--config-dir".equals(args[i]) && i + 1 < args.length) {
          configDir = args[++i];
        } else if ("--rs-index".equals(args[i]) && i + 1 < args.length) {
          rsIndex = Integer.parseInt(args[++i]);
        }
      }

      if (configDir == null) {
        System.err.println("Usage: RegionServerProcessLauncher --config-dir <dir> [--rs-index <index>]");
        System.exit(1);
      }

      LOG.info("Starting HRegionServer process: configDir={}, rsIndex={}", configDir, rsIndex);

      // Load configuration
      Configuration conf = HBaseConfiguration.create();
      File configFile = new File(configDir, "hbase-site.xml");
      if (configFile.exists()) {
        conf.addResource(new Path(configFile.toURI()));
        LOG.info("Loaded configuration from: {}", configFile);
      } else {
        LOG.warn("Configuration file not found: {}", configFile);
      }

      // Create and start HRegionServer
      LOG.info("Creating HRegionServer instance");
      final HRegionServer rs = new HRegionServer(conf);

      // Set up shutdown hook
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        LOG.info("Shutdown hook called, stopping HRegionServer");
        try {
          rs.stop("Shutdown hook");
          rs.join();
        } catch (Exception e) {
          LOG.error("Error during shutdown", e);
        }
      }, "HRegionServer-shutdown-hook"));

      LOG.info("Starting HRegionServer");
      rs.start();

      LOG.info("HRegionServer started successfully, entering main loop");

      // Wait for region server to finish
      rs.join();

      LOG.info("HRegionServer process exiting normally");
      System.exit(0);

    } catch (Throwable t) {
      LOG.error("Fatal error in HRegionServer process", t);
      t.printStackTrace();
      System.exit(1);
    }
  }
}
