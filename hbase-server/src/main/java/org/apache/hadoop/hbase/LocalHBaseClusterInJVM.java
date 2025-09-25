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
package org.apache.hadoop.hbase;

import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import edu.illinois.core.runtime.UpgtException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.master.HMaster;
import org.apache.hadoop.hbase.master.HMasterInstance;
import org.apache.hadoop.hbase.master.HMasterJVMInterface;
import org.apache.hadoop.hbase.regionserver.HRegionServer;
import org.apache.hadoop.hbase.regionserver.HRegionServerInstance;
import org.apache.hadoop.hbase.regionserver.HRegionServerJVMInterface;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.util.JVMClusterUtilInJVM;
import org.apache.hadoop.hbase.util.JVMClusterUtilInJVM.RegionServerThread;
import org.apache.hadoop.hbase.util.Threads;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class creates a single process HBase cluster. One thread is created for a master and one per
 * region server. Call {@link #startup()} to start the cluster running and {@link #shutdown()} to
 * close it all down. {@link #join} the cluster is you want to wait on shutdown completion.
 * <p>
 * Runs master on port 16000 by default. Because we can't just kill the process -- not till
 * HADOOP-1700 gets fixed and even then.... -- we need to be able to find the master with a remote
 * client to run shutdown. To use a port other than 16000, set the hbase.master to a value of
 * 'local:PORT': that is 'local', not 'localhost', and the port number the master should use instead
 * of 16000.
 */
@InterfaceAudience.Public
public class LocalHBaseClusterInJVM {
  private static final Logger LOG = LoggerFactory.getLogger(LocalHBaseClusterInJVM.class);
  private final List<JVMClusterUtilInJVM.MasterThread> masterThreads = new CopyOnWriteArrayList<>();
  private final List<JVMClusterUtilInJVM.RegionServerThread> regionThreads =
    new CopyOnWriteArrayList<>();
  private final static int DEFAULT_NO = 1;
  /** local mode */
  public static final String LOCAL = "local";
  /** 'local:' */
  public static final String LOCAL_COLON = LOCAL + ":";
  public static final String ASSIGN_RANDOM_PORTS = "hbase.localcluster.assign.random.ports";

  private final Configuration conf;
  private final Class<?> masterClass;
  private final Class<?> regionServerClass;

  /**
   * Constructor.
   */
  public LocalHBaseClusterInJVM(final Configuration conf) throws IOException {
    this(conf, DEFAULT_NO);
  }

  /**
   * Constructor.
   * @param conf            Configuration to use. Post construction has the master's address.
   * @param noRegionServers Count of regionservers to start.
   */
  public LocalHBaseClusterInJVM(final Configuration conf, final int noRegionServers) throws IOException {
    this(conf, 1, 0, noRegionServers, getMasterImplementation(conf),
      getRegionServerImplementation(conf));
  }

  /**
   * Constructor.
   * @param conf            Configuration to use. Post construction has the active master address.
   * @param noMasters       Count of masters to start.
   * @param noRegionServers Count of regionservers to start.
   */
  public LocalHBaseClusterInJVM(final Configuration conf, final int noMasters, final int noRegionServers)
    throws IOException {
    this(conf, noMasters, 0, noRegionServers, getMasterImplementation(conf),
      getRegionServerImplementation(conf));
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends HRegionServer>
    getRegionServerImplementation(final Configuration conf) {
    return (Class<? extends HRegionServer>) conf.getClass(HConstants.REGION_SERVER_IMPL,
      HRegionServer.class);
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends HMaster> getMasterImplementation(final Configuration conf) {
    return (Class<? extends HMaster>) conf.getClass(HConstants.MASTER_IMPL, HMaster.class);
  }

  public LocalHBaseClusterInJVM(final Configuration conf, final int noMasters, final int noRegionServers,
    final Class<? extends HMaster> masterClass,
    final Class<? extends HRegionServer> regionServerClass) throws IOException {
    this(conf, noMasters, 0, noRegionServers, masterClass, regionServerClass);
  }

  /**
   * Constructor.
   * @param conf            Configuration to use. Post construction has the master's address.
   * @param noMasters       Count of masters to start.
   * @param noRegionServers Count of regionservers to start.
   */
  @SuppressWarnings("unchecked")
  public LocalHBaseClusterInJVM(final Configuration conf, final int noMasters,
    final int noAlwaysStandByMasters, final int noRegionServers,
    final Class<?> masterClass,
    final Class<?> regionServerClass) throws IOException {
    this.conf = conf;

    // When active, if a port selection is default then we switch to random
    if (conf.getBoolean(ASSIGN_RANDOM_PORTS, false)) {
      if (
        conf.getInt(HConstants.MASTER_PORT, HConstants.DEFAULT_MASTER_PORT)
            == HConstants.DEFAULT_MASTER_PORT
      ) {
        LOG.debug("Setting Master Port to random.");
        conf.set(HConstants.MASTER_PORT, "0");
      }
      if (
        conf.getInt(HConstants.REGIONSERVER_PORT, HConstants.DEFAULT_REGIONSERVER_PORT)
            == HConstants.DEFAULT_REGIONSERVER_PORT
      ) {
        LOG.debug("Setting RegionServer Port to random.");
        conf.set(HConstants.REGIONSERVER_PORT, "0");
      }
      // treat info ports special; expressly don't change '-1' (keep off)
      // in case we make that the default behavior.
      if (
        conf.getInt(HConstants.REGIONSERVER_INFO_PORT, 0) != -1
          && conf.getInt(HConstants.REGIONSERVER_INFO_PORT,
            HConstants.DEFAULT_REGIONSERVER_INFOPORT) == HConstants.DEFAULT_REGIONSERVER_INFOPORT
      ) {
        LOG.debug("Setting RS InfoServer Port to random.");
        conf.set(HConstants.REGIONSERVER_INFO_PORT, "0");
      }
      if (
        conf.getInt(HConstants.MASTER_INFO_PORT, 0) != -1
          && conf.getInt(HConstants.MASTER_INFO_PORT, HConstants.DEFAULT_MASTER_INFOPORT)
              == HConstants.DEFAULT_MASTER_INFOPORT
      ) {
        LOG.debug("Setting Master InfoServer Port to random.");
        conf.set(HConstants.MASTER_INFO_PORT, "0");
      }
    }

    this.masterClass =
      (Class<? extends HMaster>) conf.getClass(HConstants.MASTER_IMPL, masterClass);
    // Start the HMasters.
    int i;
    for (i = 0; i < noMasters; i++) {
      addMaster(new Configuration(conf), i);
    }
    for (int j = 0; j < noAlwaysStandByMasters; j++) {
      Configuration c = new Configuration(conf);
      c.set(HConstants.MASTER_IMPL, "org.apache.hadoop.hbase.master.AlwaysStandByHMaster");
      addMaster(c, i + j);
    }
    // Start the HRegionServers.
    this.regionServerClass = (Class<? extends HRegionServer>) conf
      .getClass(HConstants.REGION_SERVER_IMPL, regionServerClass);

    for (int j = 0; j < noRegionServers; j++) {
      addRegionServer(new Configuration(conf), j);
    }
  }

  public LocalHBaseClusterInJVM(final Configuration conf, final int noMasters,
    final int noAlwaysStandByMasters, final int noRegionServers,
    final Class<?> masterClass,
    final Class<?> regionServerClass,
    HMasterInstance masterInstance,
    HRegionServerInstance regionServerInstance
  ) throws IOException {
    this.conf = conf;

    // When active, if a port selection is default then we switch to random
    if (conf.getBoolean(ASSIGN_RANDOM_PORTS, false)) {
      if (
        conf.getInt(HConstants.MASTER_PORT, HConstants.DEFAULT_MASTER_PORT)
          == HConstants.DEFAULT_MASTER_PORT
      ) {
        LOG.debug("Setting Master Port to random.");
        conf.set(HConstants.MASTER_PORT, "0");
      }
      if (
        conf.getInt(HConstants.REGIONSERVER_PORT, HConstants.DEFAULT_REGIONSERVER_PORT)
          == HConstants.DEFAULT_REGIONSERVER_PORT
      ) {
        LOG.debug("Setting RegionServer Port to random.");
        conf.set(HConstants.REGIONSERVER_PORT, "0");
      }
      // treat info ports special; expressly don't change '-1' (keep off)
      // in case we make that the default behavior.
      if (
        conf.getInt(HConstants.REGIONSERVER_INFO_PORT, 0) != -1
          && conf.getInt(HConstants.REGIONSERVER_INFO_PORT,
          HConstants.DEFAULT_REGIONSERVER_INFOPORT) == HConstants.DEFAULT_REGIONSERVER_INFOPORT
      ) {
        LOG.debug("Setting RS InfoServer Port to random.");
        conf.set(HConstants.REGIONSERVER_INFO_PORT, "0");
      }
      if (
        conf.getInt(HConstants.MASTER_INFO_PORT, 0) != -1
          && conf.getInt(HConstants.MASTER_INFO_PORT, HConstants.DEFAULT_MASTER_INFOPORT)
          == HConstants.DEFAULT_MASTER_INFOPORT
      ) {
        LOG.debug("Setting Master InfoServer Port to random.");
        conf.set(HConstants.MASTER_INFO_PORT, "0");
      }
    }

    masterInstance.enter();
    Class<?> confMasterClass = conf.getClass(HConstants.MASTER_IMPL, masterClass);
    if (!confMasterClass.equals(masterClass)) {
      throw new UpgtException("Configuration's " + HConstants.MASTER_IMPL + " class "
        + confMasterClass.getName() + " does not match the provided masterClass "
        + masterClass.getName());
    }
    this.masterClass = masterClass;
    // Start the HMasters.
    int i;
    for (i = 0; i < noMasters; i++) {
      Configuration c = new Configuration(conf);
      c.setClassLoader(masterClass.getClassLoader());
      addMaster(c, i);
    }
    for (int j = 0; j < noAlwaysStandByMasters; j++) {
      Configuration c = new Configuration(conf);
      c.setClassLoader(masterInstance.getLoader());
      c.set(HConstants.MASTER_IMPL, "org.apache.hadoop.hbase.master.AlwaysStandByHMaster");
      addMaster(c, i + j);
    }
    masterInstance.exit();
    // Start the HRegionServers.
    regionServerInstance.enter();
    this.regionServerClass = conf.getClass(HConstants.REGION_SERVER_IMPL, regionServerClass);

    for (int j = 0; j < noRegionServers; j++) {
      Configuration c = new Configuration(conf);
      c.setClassLoader(regionServerInstance.getLoader());
      addRegionServer(c, j);
    }
    regionServerInstance.exit();
  }

  public JVMClusterUtilInJVM.RegionServerThread addRegionServer() throws IOException {
    return addRegionServer(new Configuration(conf), this.regionThreads.size());
  }

  @SuppressWarnings("unchecked")
  public JVMClusterUtilInJVM.RegionServerThread addRegionServer(Configuration config, final int index)
    throws IOException {
    // Create each regionserver with its own Configuration instance so each has
    // its Connection instance rather than share (see HBASE_INSTANCES down in
    // the guts of ConnectionManager).
    JVMClusterUtilInJVM.RegionServerThread rst =
      JVMClusterUtilInJVM.createRegionServerThread(config, conf.getClass(HConstants.REGION_SERVER_IMPL, this.regionServerClass), index);

    this.regionThreads.add(rst);
    return rst;
  }

  public JVMClusterUtilInJVM.RegionServerThread addRegionServer(final Configuration config,
    final int index, User user) throws IOException, InterruptedException {
    return user.runAs(new PrivilegedExceptionAction<JVMClusterUtilInJVM.RegionServerThread>() {
      @Override
      public JVMClusterUtilInJVM.RegionServerThread run() throws Exception {
        return addRegionServer(config, index);
      }
    });
  }

  public JVMClusterUtilInJVM.MasterThread addMaster() throws IOException {
    return addMaster(new Configuration(conf), this.masterThreads.size());
  }

  public JVMClusterUtilInJVM.MasterThread addMaster(Configuration c, final int index)
    throws IOException {
    // Create each master with its own Configuration instance so each has
    // its Connection instance rather than share (see HBASE_INSTANCES down in
    // the guts of ConnectionManager.
    JVMClusterUtilInJVM.MasterThread mt = JVMClusterUtilInJVM.createMasterThread(c, c.getClass(HConstants.MASTER_IMPL, this.masterClass), index);
    this.masterThreads.add(mt);
    // Refresh the master address config.
    List<String> masterHostPorts = new ArrayList<>();
    getMasters().forEach(masterThread -> masterHostPorts
      .add(masterThread.getMaster().getServerName().getAddress().toString()));
    conf.set(HConstants.MASTER_ADDRS_KEY, String.join(",", masterHostPorts));
    return mt;
  }

  public JVMClusterUtilInJVM.MasterThread addMaster(final Configuration c, final int index, User user)
    throws IOException, InterruptedException {
    return user.runAs(new PrivilegedExceptionAction<JVMClusterUtilInJVM.MasterThread>() {
      @Override
      public JVMClusterUtilInJVM.MasterThread run() throws Exception {
        return addMaster(c, index);
      }
    });
  }

  /** Returns region server */
  public HRegionServerJVMInterface getRegionServer(int serverNumber) {
    return regionThreads.get(serverNumber).getRegionServer();
  }

  /** Returns Read-only list of region server threads. */
  public List<JVMClusterUtilInJVM.RegionServerThread> getRegionServers() {
    return Collections.unmodifiableList(this.regionThreads);
  }

  /**
   * @return List of running servers (Some servers may have been killed or aborted during lifetime
   *         of cluster; these servers are not included in this list).
   */
  public List<JVMClusterUtilInJVM.RegionServerThread> getLiveRegionServers() {
    List<JVMClusterUtilInJVM.RegionServerThread> liveServers = new ArrayList<>();
    List<RegionServerThread> list = getRegionServers();
    for (JVMClusterUtilInJVM.RegionServerThread rst : list) {
      if (rst.isAlive()) liveServers.add(rst);
      else LOG.info("Not alive " + rst.getName());
    }
    return liveServers;
  }

  /** Returns the Configuration used by this LocalHBaseClusterInJVM */
  public Configuration getConfiguration() {
    return this.conf;
  }

  /**
   * Wait for the specified region server to stop. Removes this thread from list of running threads.
   * @return Name of region server that just went down.
   */
  public String waitOnRegionServer(int serverNumber) {
    JVMClusterUtilInJVM.RegionServerThread regionServerThread = this.regionThreads.get(serverNumber);
    return waitOnRegionServer(regionServerThread);
  }

  /**
   * Wait for the specified region server to stop. Removes this thread from list of running threads.
   * @return Name of region server that just went down.
   */
  public String waitOnRegionServer(JVMClusterUtilInJVM.RegionServerThread rst) {
    boolean interrupted = false;
    while (rst.isAlive()) {
      try {
        LOG.info("Waiting on " + rst.getRegionServer().toString());
        rst.join();
      } catch (InterruptedException e) {
        LOG.error("Interrupted while waiting for {} to finish. Retrying join", rst.getName(), e);
        interrupted = true;
      }
    }
    regionThreads.remove(rst);
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
    return rst.getName();
  }

  /** Returns the HMaster thread */
  public HMasterJVMInterface getMaster(int serverNumber) {
    return masterThreads.get(serverNumber).getMaster();
  }

  /**
   * Gets the current active master, if available. If no active master, returns null.
   * @return the HMaster for the active master
   */
  public HMasterJVMInterface getActiveMaster() {
    for (JVMClusterUtilInJVM.MasterThread mt : masterThreads) {
      // Ensure that the current active master is not stopped.
      // We don't want to return a stopping master as an active master.
      if (mt.getMaster().isActiveMaster() && !mt.getMaster().isStopped()) {
        return mt.getMaster();
      }
    }
    return null;
  }

  /** Returns Read-only list of master threads. */
  public List<JVMClusterUtilInJVM.MasterThread> getMasters() {
    return Collections.unmodifiableList(this.masterThreads);
  }

  /**
   * @return List of running master servers (Some servers may have been killed or aborted during
   *         lifetime of cluster; these servers are not included in this list).
   */
  public List<JVMClusterUtilInJVM.MasterThread> getLiveMasters() {
    List<JVMClusterUtilInJVM.MasterThread> liveServers = new ArrayList<>();
    List<JVMClusterUtilInJVM.MasterThread> list = getMasters();
    for (JVMClusterUtilInJVM.MasterThread mt : list) {
      if (mt.isAlive()) {
        liveServers.add(mt);
      }
    }
    return liveServers;
  }

  /**
   * Wait for the specified master to stop. Removes this thread from list of running threads.
   * @return Name of master that just went down.
   */
  public String waitOnMaster(int serverNumber) {
    JVMClusterUtilInJVM.MasterThread masterThread = this.masterThreads.get(serverNumber);
    return waitOnMaster(masterThread);
  }

  /**
   * Wait for the specified master to stop. Removes this thread from list of running threads.
   * @return Name of master that just went down.
   */
  public String waitOnMaster(JVMClusterUtilInJVM.MasterThread masterThread) {
    boolean interrupted = false;
    while (masterThread.isAlive()) {
      try {
        LOG.info("Waiting on " + masterThread.getMaster().getServerName().toString());
        masterThread.join();
      } catch (InterruptedException e) {
        LOG.error("Interrupted while waiting for {} to finish. Retrying join",
          masterThread.getName(), e);
        interrupted = true;
      }
    }
    masterThreads.remove(masterThread);
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
    return masterThread.getName();
  }

  /**
   * Wait for Mini HBase Cluster to shut down. Presumes you've already called {@link #shutdown()}.
   */
  public void join() {
    if (this.regionThreads != null) {
      for (Thread t : this.regionThreads) {
        if (t.isAlive()) {
          try {
            Threads.threadDumpingIsAlive(t);
          } catch (InterruptedException e) {
            LOG.debug("Interrupted", e);
          }
        }
      }
    }
    if (this.masterThreads != null) {
      for (Thread t : this.masterThreads) {
        if (t.isAlive()) {
          try {
            Threads.threadDumpingIsAlive(t);
          } catch (InterruptedException e) {
            LOG.debug("Interrupted", e);
          }
        }
      }
    }
  }

  /**
   * Start the cluster.
   */
  public void startup() throws IOException {
    JVMClusterUtilInJVM.startup(this.masterThreads, this.regionThreads);
  }

  /**
   * Shut down the mini HBase cluster
   */
  public void shutdown() {
    JVMClusterUtilInJVM.shutdown(this.masterThreads, this.regionThreads);
  }

  /**
   * @param c Configuration to check.
   * @return True if a 'local' address in hbase.master value.
   */
  public static boolean isLocal(final Configuration c) {
    boolean mode =
      c.getBoolean(HConstants.CLUSTER_DISTRIBUTED, HConstants.DEFAULT_CLUSTER_DISTRIBUTED);
    return (mode == HConstants.CLUSTER_IS_LOCAL);
  }

  /**
   * Test things basically work.
   */
  public static void main(String[] args) throws IOException {
    Configuration conf = HBaseConfiguration.create();
    LocalHBaseClusterInJVM cluster = new LocalHBaseClusterInJVM(conf);
    cluster.startup();
    Connection connection = ConnectionFactory.createConnection(conf);
    Admin admin = connection.getAdmin();
    try {
      HTableDescriptor htd = new HTableDescriptor(TableName.valueOf(cluster.getClass().getName()));
      admin.createTable(htd);
    } finally {
      admin.close();
    }
    connection.close();
    cluster.shutdown();
  }
}
