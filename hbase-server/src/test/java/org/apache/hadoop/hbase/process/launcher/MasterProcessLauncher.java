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
import org.apache.hadoop.hbase.master.HMaster;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for HMaster process launched in a separate JVM.
 * This class is executed as the main class in the subprocess.
 *
 * Usage: java ... MasterProcessLauncher --config-dir <dir> --master-index <index>
 */
@InterfaceAudience.Private
public class MasterProcessLauncher {
  private static final Logger LOG = LoggerFactory.getLogger(MasterProcessLauncher.class);

  public static void main(String[] args) {
    try {
      // Parse command line arguments
      String configDir = null;
      int masterIndex = 0;

      for (int i = 0; i < args.length; i++) {
        if ("--config-dir".equals(args[i]) && i + 1 < args.length) {
          configDir = args[++i];
        } else if ("--master-index".equals(args[i]) && i + 1 < args.length) {
          masterIndex = Integer.parseInt(args[++i]);
        }
      }

      if (configDir == null) {
        System.err.println("Usage: MasterProcessLauncher --config-dir <dir> [--master-index <index>]");
        System.exit(1);
      }

      LOG.info("Starting HMaster process: configDir={}, masterIndex={}", configDir, masterIndex);
      System.out.println("MasterProcessLauncher: Starting HMaster process: configDir=" + configDir + ", masterIndex=" + masterIndex);

      // Load configuration
      Configuration conf = HBaseConfiguration.create();
      File configFile = new File(configDir, "hbase-site.xml");
      if (configFile.exists()) {
        conf.addResource(new Path(configFile.toURI()));
        LOG.info("Loaded configuration from: {}", configFile);
        System.out.println("MasterProcessLauncher: Loaded configuration from: " + configFile);
      } else {
        LOG.warn("Configuration file not found: {}", configFile);
        System.err.println("MasterProcessLauncher: Configuration file not found: " + configFile);
      }

      // Create and start HMaster
      LOG.info("Creating HMaster instance");
      System.out.println("MasterProcessLauncher: Creating HMaster instance");
      final HMaster master = new HMaster(conf);
      System.out.println("MasterProcessLauncher: HMaster instance created");

      // Set up shutdown hook
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        LOG.info("Shutdown hook called, stopping HMaster");
        System.out.println("MasterProcessLauncher: Shutdown hook called");
        try {
          master.stop("Shutdown hook");
          master.join();
        } catch (Exception e) {
          LOG.error("Error during shutdown", e);
        }
      }, "HMaster-shutdown-hook"));

      LOG.info("Starting HMaster");
      System.out.println("MasterProcessLauncher: Starting HMaster");
      master.start();
      System.out.println("MasterProcessLauncher: HMaster.start() returned");

      LOG.info("HMaster started successfully, entering main loop");
      System.out.println("MasterProcessLauncher: HMaster started successfully, entering main loop");
      System.out.println("MasterProcessLauncher: Master is active: " + master.isActiveMaster());
      System.out.println("MasterProcessLauncher: Master is stopped: " + master.isStopped());
      System.out.println("MasterProcessLauncher: Master is aborted: " + master.isAborted());

      // Wait for master to finish
      master.join();

      LOG.info("HMaster process exiting - stopped: {}, aborted: {}", master.isStopped(), master.isAborted());
      System.out.println("MasterProcessLauncher: HMaster process exiting - stopped: " + master.isStopped() + ", aborted: " + master.isAborted());
      System.out.println("MasterProcessLauncher: HMaster process exiting normally");
      System.exit(0);

    } catch (Throwable t) {
      LOG.error("Fatal error in HMaster process", t);
      System.err.println("MasterProcessLauncher: FATAL ERROR:");
      t.printStackTrace();
      System.exit(1);
    }
  }
}
