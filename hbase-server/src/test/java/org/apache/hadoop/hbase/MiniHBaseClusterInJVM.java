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
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import edu.illinois.core.runtime.UpgtException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hbase.client.RegionReplicaUtil;
import org.apache.hadoop.hbase.master.HMaster;
import org.apache.hadoop.hbase.regionserver.HRegion;
import org.apache.hadoop.hbase.regionserver.HRegion.FlushResult;
import org.apache.hadoop.hbase.regionserver.HRegionJVMInterface;
import org.apache.hadoop.hbase.regionserver.HRegionServer;
import org.apache.hadoop.hbase.regionserver.MiniHBaseClusterRegionServer;
import org.apache.hadoop.hbase.regionserver.Region;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.test.MetricsAssertHelper;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.hadoop.hbase.util.JVMClusterUtilInJVM;
import org.apache.hadoop.hbase.util.JVMClusterUtilInJVM.MasterThread;
import org.apache.hadoop.hbase.util.JVMClusterUtilInJVM.RegionServerThread;
import org.apache.hadoop.hbase.util.Threads;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.hbase.shaded.protobuf.generated.AdminProtos.AdminService;
import org.apache.hadoop.hbase.shaded.protobuf.generated.ClientProtos.ClientService;
import org.apache.hadoop.hbase.shaded.protobuf.generated.MasterProtos.MasterService;
import org.apache.hadoop.hbase.shaded.protobuf.generated.RegionServerStatusProtos.RegionServerStartupResponse;
import edu.illinois.core.upgrade.UpgradePlan;
import edu.illinois.core.spi.NodeLoaderFactory;
import org.apache.hadoop.hbase.master.HMasterJVMInterface;
import org.apache.hadoop.hbase.master.HMasterInstance;
import org.apache.hadoop.hbase.regionserver.HRegionServerJVMInterface;
import org.apache.hadoop.hbase.regionserver.HRegionServerInstance;

/**
 * This class creates a single process HBase cluster. each server. The master uses the 'default'
 * FileSystem. The RegionServers, if we are running on DistributedFilesystem, create a FileSystem
 * instance each and will close down their instance on the way out.
 */
@InterfaceAudience.Public
public class MiniHBaseClusterInJVM extends HBaseCluster {

    private static final Logger LOG = LoggerFactory.getLogger(MiniHBaseClusterInJVM.class.getName());

    public LocalHBaseClusterInJVM hbaseCluster;

    private static int index;

    /**
     * Start a MiniHBaseCluster.
     * @param conf             Configuration to be used for cluster     * @param numRegionServers initial number of region servers to start.
     */
    public MiniHBaseClusterInJVM(Configuration conf, int numRegionServers) throws IOException, InterruptedException {
        this(conf, 1, numRegionServers);
    }

    /**
     * Start a MiniHBaseCluster.
     * @param conf             Configuration to be used for cluster
     * @param numMasters       initial number of masters to start.
     * @param numRegionServers initial number of region servers to start.
     */
    public MiniHBaseClusterInJVM(Configuration conf, int numMasters, int numRegionServers) throws IOException, InterruptedException {
        this(conf, numMasters, numRegionServers, null, null);
    }

    /**
     * Start a MiniHBaseCluster.
     * @param conf             Configuration to be used for cluster
     * @param numMasters       initial number of masters to start.
     * @param numRegionServers initial number of region servers to start.
     */
    public MiniHBaseClusterInJVM(Configuration conf, int numMasters, int numRegionServers, Class<?> masterClass, Class<?> regionserverClass) throws IOException, InterruptedException {
        this(conf, numMasters, 0, numRegionServers, null, masterClass, regionserverClass);
    }

    /**
     * @param rsPorts Ports that RegionServer should use; pass ports if you want to test cluster
     *                restart where for sure the regionservers come up on same address+port (but just
     *                with different startcode); by default mini hbase clusters choose new arbitrary
     *                ports on each cluster start.
     */
    public MiniHBaseClusterInJVM(Configuration conf, int numMasters, int numAlwaysStandByMasters, int numRegionServers, List<Integer> rsPorts, Class<?> masterClass, Class<?> regionserverClass) throws IOException, InterruptedException {
        super(conf);
        // Hadoop 2
        CompatibilityFactory.getInstance(MetricsAssertHelper.class).init();
        init(numMasters, numAlwaysStandByMasters, numRegionServers, rsPorts, masterClass, regionserverClass);
        this.initialClusterStatus = getClusterMetrics();
    }

    public Configuration getConfiguration() {
        return this.conf;
    }

    private void init(final int nMasterNodes, final int numAlwaysStandByMasters, final int nRegionNodes, List<Integer> rsPorts, Class<?> masterClassX, Class<?> regionserverClassX) throws IOException, InterruptedException {
        try {
          /*
            if (masterClass == null) {
                masterClass = HMaster.class;
            }
            if (regionserverClass == null) {
                regionserverClass = MiniHBaseCluster.MiniHBaseClusterRegionServer.class;
            }
           */
            Class<?> masterClass = hMasterInstance.getLoader().loadClass("org.apache.hadoop.hbase.master.HMaster");
            Class<?> regionserverClass = hRegionServerInstance.getLoader().loadClass("org.apache.hadoop.hbase.regionserver.MiniHBaseClusterRegionServer");
            // start up a LocalHBaseClusterInJVM
            hbaseCluster = new LocalHBaseClusterInJVM(conf, nMasterNodes, numAlwaysStandByMasters, 0, masterClass, regionserverClass, hMasterInstance, hRegionServerInstance);
            // manually add the regionservers as other users
            for (int i = 0; i < nRegionNodes; i++) {
                Configuration rsConf = HBaseConfiguration.create(conf);
                if (rsPorts != null) {
                    rsConf.setInt(HConstants.REGIONSERVER_PORT, rsPorts.get(i));
                }
                User user = HBaseTestingUtility.getDifferentUser(rsConf, ".hfs." + index++);
                hbaseCluster.addRegionServer(rsConf, i, user, hRegionServerInstance);
            }
            hbaseCluster.startup();
        } catch (IOException e) {
            shutdown();
            throw e;
        } catch (Throwable t) {
            LOG.error("Error starting cluster", t);
            shutdown();
            throw new IOException("Shutting down", t);
        }
    }

    @Override
    public void startRegionServer(String hostname, int port) throws IOException {
        final Configuration newConf = HBaseConfiguration.create(conf);
        newConf.setInt(HConstants.REGIONSERVER_PORT, port);
        startRegionServer(newConf);
    }

    @Override
    public void killRegionServer(ServerName serverName) throws IOException {
        HRegionServerJVMInterface server = getRegionServer(getRegionServerIndex(serverName));

      String fqcn = "org.apache.hadoop.hbase.regionserver.MiniHBaseClusterRegionServer";
      ClassLoader nodeCl = server.getClass().getClassLoader();
      try {
        // get the class token from the *same* loader that loaded `server`
        Class<?> miniRsClass = Class.forName(fqcn, /*initialize*/ false, nodeCl);

        if (miniRsClass.isInstance(server)) {
          LOG.info("Killing " + server.toString());
          // call protected kill() reflectively
          java.lang.reflect.Method kill = miniRsClass.getDeclaredMethod("kill");
          kill.setAccessible(true);
          kill.invoke(server);
          LOG.info("Killed region server {}", server);
        } else {
          abortRegionServer(getRegionServerIndex(serverName));
        }
      } catch (ClassNotFoundException e) {
        // this version may not have that nested class — fall back
        abortRegionServer(getRegionServerIndex(serverName));
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException("Failed to kill region server via reflection", e);
      }
    }

    @Override
    public boolean isKilledRS(ServerName serverName) {
        return MiniHBaseClusterRegionServer.killedServers.contains(serverName);
    }

    @Override
    public void stopRegionServer(ServerName serverName) throws IOException {
        stopRegionServer(getRegionServerIndex(serverName));
    }

    @Override
    public void suspendRegionServer(ServerName serverName) throws IOException {
        suspendRegionServer(getRegionServerIndex(serverName));
    }

    @Override
    public void resumeRegionServer(ServerName serverName) throws IOException {
        resumeRegionServer(getRegionServerIndex(serverName));
    }

    @Override
    public void waitForRegionServerToStop(ServerName serverName, long timeout) throws IOException {
        // ignore timeout for now
        waitOnRegionServer(getRegionServerIndex(serverName));
    }

    @Override
    public void waitForRegionServerToSuspend(ServerName serverName, long timeout) throws IOException {
        LOG.warn("Waiting for regionserver to suspend on mini cluster is not supported");
    }

    @Override
    public void waitForRegionServerToResume(ServerName serverName, long timeout) throws IOException {
        LOG.warn("Waiting for regionserver to resume on mini cluster is not supported");
    }

    @Override
    public void startZkNode(String hostname, int port) throws IOException {
        LOG.warn("Starting zookeeper nodes on mini cluster is not supported");
    }

    @Override
    public void killZkNode(ServerName serverName) throws IOException {
        LOG.warn("Aborting zookeeper nodes on mini cluster is not supported");
    }

    @Override
    public void stopZkNode(ServerName serverName) throws IOException {
        LOG.warn("Stopping zookeeper nodes on mini cluster is not supported");
    }

    @Override
    public void waitForZkNodeToStart(ServerName serverName, long timeout) throws IOException {
        LOG.warn("Waiting for zookeeper nodes to start on mini cluster is not supported");
    }

    @Override
    public void waitForZkNodeToStop(ServerName serverName, long timeout) throws IOException {
        LOG.warn("Waiting for zookeeper nodes to stop on mini cluster is not supported");
    }

    @Override
    public void startDataNode(ServerName serverName) throws IOException {
        LOG.warn("Starting datanodes on mini cluster is not supported");
    }

    @Override
    public void killDataNode(ServerName serverName) throws IOException {
        LOG.warn("Aborting datanodes on mini cluster is not supported");
    }

    @Override
    public void stopDataNode(ServerName serverName) throws IOException {
        LOG.warn("Stopping datanodes on mini cluster is not supported");
    }

    @Override
    public void waitForDataNodeToStart(ServerName serverName, long timeout) throws IOException {
        LOG.warn("Waiting for datanodes to start on mini cluster is not supported");
    }

    @Override
    public void waitForDataNodeToStop(ServerName serverName, long timeout) throws IOException {
        LOG.warn("Waiting for datanodes to stop on mini cluster is not supported");
    }

    @Override
    public void startNameNode(ServerName serverName) throws IOException {
        LOG.warn("Starting namenodes on mini cluster is not supported");
    }

    @Override
    public void killNameNode(ServerName serverName) throws IOException {
        LOG.warn("Aborting namenodes on mini cluster is not supported");
    }

    @Override
    public void stopNameNode(ServerName serverName) throws IOException {
        LOG.warn("Stopping namenodes on mini cluster is not supported");
    }

    @Override
    public void waitForNameNodeToStart(ServerName serverName, long timeout) throws IOException {
        LOG.warn("Waiting for namenodes to start on mini cluster is not supported");
    }

    @Override
    public void waitForNameNodeToStop(ServerName serverName, long timeout) throws IOException {
        LOG.warn("Waiting for namenodes to stop on mini cluster is not supported");
    }

    @Override
    public void startJournalNode(ServerName serverName) {
        LOG.warn("Starting journalnodes on mini cluster is not supported");
    }

    @Override
    public void killJournalNode(ServerName serverName) {
        LOG.warn("Aborting journalnodes on mini cluster is not supported");
    }

    @Override
    public void stopJournalNode(ServerName serverName) {
        LOG.warn("Stopping journalnodes on mini cluster is not supported");
    }

    @Override
    public void waitForJournalNodeToStart(ServerName serverName, long timeout) {
        LOG.warn("Waiting for journalnodes to start on mini cluster is not supported");
    }

    @Override
    public void waitForJournalNodeToStop(ServerName serverName, long timeout) {
        LOG.warn("Waiting for journalnodes to stop on mini cluster is not supported");
    }

    @Override
    public void startMaster(String hostname, int port) throws IOException {
        this.startMaster();
    }

    @Override
    public void killMaster(ServerName serverName) throws IOException {
        abortMaster(getMasterIndex(serverName));
    }

    @Override
    public void stopMaster(ServerName serverName) throws IOException {
        stopMaster(getMasterIndex(serverName));
    }

    @Override
    public void waitForMasterToStop(ServerName serverName, long timeout) throws IOException {
        // ignore timeout for now
        waitOnMaster(getMasterIndex(serverName));
    }

    /**
     * Starts a region server thread running
     * @return New RegionServerThread
     */
    public JVMClusterUtilInJVM.RegionServerThread startRegionServer() throws IOException {
        final Configuration newConf = HBaseConfiguration.create(conf);
        return startRegionServer(newConf);
    }

    private JVMClusterUtilInJVM.RegionServerThread startRegionServer(Configuration configuration) throws IOException {
        User rsUser = HBaseTestingUtility.getDifferentUser(configuration, ".hfs." + index++);
        JVMClusterUtilInJVM.RegionServerThread t = null;
        try {
            t = hbaseCluster.addRegionServer(configuration, hbaseCluster.getRegionServers().size(), rsUser);
            t.start();
            t.waitForServerOnline();
        } catch (InterruptedException ie) {
            throw new IOException("Interrupted adding regionserver to cluster", ie);
        }
        return t;
    }

    /**
     * Starts a region server thread and waits until its processed by master. Throws an exception when
     * it can't start a region server or when the region server is not processed by master within the
     * timeout.
     * @return New RegionServerThread
     */
    public JVMClusterUtilInJVM.RegionServerThread startRegionServerAndWait(long timeout) throws IOException {
        JVMClusterUtilInJVM.RegionServerThread t = startRegionServer();
        ServerNameJVMInterface rsServerName = t.getRegionServer().getServerName();
        long start = EnvironmentEdgeManager.currentTime();
        ClusterStatus clusterStatus = getClusterStatus();
        while ((EnvironmentEdgeManager.currentTime() - start) < timeout) {
            if (clusterStatus != null && clusterStatus.getServers().contains(rsServerName)) {
                return t;
            }
            Threads.sleep(100);
        }
        if (t.getRegionServer().isOnline()) {
            throw new IOException("RS: " + rsServerName + " online, but not processed by master");
        } else {
            throw new IOException("RS: " + rsServerName + " is offline");
        }
    }

    /**
     * Cause a region server to exit doing basic clean up only on its way out.
     * @param serverNumber Used as index into a list.
     */
    public String abortRegionServer(int serverNumber) {
        HRegionServerJVMInterface server = getRegionServer(serverNumber);
        LOG.info("Aborting " + server.toString());
        server.abort("Aborting for tests", new Exception("Trace info"));
        return server.toString();
    }

    /**
     * Shut down the specified region server cleanly
     * @param serverNumber Used as index into a list.
     * @return the region server that was stopped
     */
    public JVMClusterUtilInJVM.RegionServerThread stopRegionServer(int serverNumber) {
        return stopRegionServer(serverNumber, true);
    }

    /**
     * Shut down the specified region server cleanly
     * @param serverNumber Used as index into a list.
     * @param shutdownFS   True is we are to shutdown the filesystem as part of this regionserver's
     *                     shutdown. Usually we do but you do not want to do this if you are running
     *                     multiple regionservers in a test and you shut down one before end of the
     *                     test.
     * @return the region server that was stopped
     */
    public JVMClusterUtilInJVM.RegionServerThread stopRegionServer(int serverNumber, final boolean shutdownFS) {
        JVMClusterUtilInJVM.RegionServerThread server = hbaseCluster.getRegionServers().get(serverNumber);
        LOG.info("Stopping " + server.toString());
        server.getRegionServer().stop("Stopping rs " + serverNumber);
        return server;
    }

    /**
     * Suspend the specified region server
     * @param serverNumber Used as index into a list.
     */
    public JVMClusterUtilInJVM.RegionServerThread suspendRegionServer(int serverNumber) {
        JVMClusterUtilInJVM.RegionServerThread server = hbaseCluster.getRegionServers().get(serverNumber);
        LOG.info("Suspending {}", server.toString());
        server.suspend();
        return server;
    }

    /**
     * Resume the specified region server
     * @param serverNumber Used as index into a list.
     */
    public JVMClusterUtilInJVM.RegionServerThread resumeRegionServer(int serverNumber) {
        JVMClusterUtilInJVM.RegionServerThread server = hbaseCluster.getRegionServers().get(serverNumber);
        LOG.info("Resuming {}", server.toString());
        server.resume();
        return server;
    }

    /**
     * Wait for the specified region server to stop. Removes this thread from list of running threads.
     * @return Name of region server that just went down.
     */
    public String waitOnRegionServer(final int serverNumber) {
        return this.hbaseCluster.waitOnRegionServer(serverNumber);
    }

    /**
     * Starts a master thread running
     * @return New RegionServerThread
     */
    public JVMClusterUtilInJVM.MasterThread startMaster() throws IOException {
        Configuration c = HBaseConfiguration.create(conf);
        User user = HBaseTestingUtility.getDifferentUser(c, ".hfs." + index++);
        JVMClusterUtilInJVM.MasterThread t = null;
        try {
            t = hbaseCluster.addMaster(c, hbaseCluster.getMasters().size(), user);
            t.start();
        } catch (InterruptedException ie) {
            throw new IOException("Interrupted adding master to cluster", ie);
        }
        conf.set(HConstants.MASTER_ADDRS_KEY, hbaseCluster.getConfiguration().get(HConstants.MASTER_ADDRS_KEY));
        return t;
    }

    /**
     * Returns the current active master, if available.
     * @return the active HMaster, null if none is active.
     */
    @Override
    public MasterService.BlockingInterface getMasterAdminService() {
        throw new UpgtException("this.hbaseCluster.getActiveMaster().getMasterRpcServices() not supported in upgradable mini cluster");
        //return this.hbaseCluster.getActiveMaster().getMasterRpcServices();
    }

    /**
     * Returns the current active master, if available.
     * @return the active HMaster, null if none is active.
     */
    public HMasterJVMInterface getMaster() {
        return this.hbaseCluster.getActiveMaster();
    }

    /**
     * Returns the current active master thread, if available.
     * @return the active MasterThread, null if none is active.
     */
    public MasterThread getMasterThread() {
        for (MasterThread mt : hbaseCluster.getLiveMasters()) {
            if (mt.getMaster().isActiveMaster()) {
                return mt;
            }
        }
        return null;
    }

    /**
     * Returns the master at the specified index, if available.
     * @return the active HMaster, null if none is active.
     */
    public HMasterJVMInterface getMaster(final int serverNumber) {
        return this.hbaseCluster.getMaster(serverNumber);
    }

    /**
     * Cause a master to exit without shutting down entire cluster.
     * @param serverNumber Used as index into a list.
     */
    public String abortMaster(int serverNumber) {
        HMasterJVMInterface server = getMaster(serverNumber);
        LOG.info("Aborting " + server.toString());
        server.abort("Aborting for tests", new Exception("Trace info"));
        return server.toString();
    }

    /**
     * Shut down the specified master cleanly
     * @param serverNumber Used as index into a list.
     * @return the region server that was stopped
     */
    public JVMClusterUtilInJVM.MasterThread stopMaster(int serverNumber) {
        return stopMaster(serverNumber, true);
    }

    /**
     * Shut down the specified master cleanly
     * @param serverNumber Used as index into a list.
     * @param shutdownFS   True is we are to shutdown the filesystem as part of this master's
     *                     shutdown. Usually we do but you do not want to do this if you are running
     *                     multiple master in a test and you shut down one before end of the test.
     * @return the master that was stopped
     */
    public JVMClusterUtilInJVM.MasterThread stopMaster(int serverNumber, final boolean shutdownFS) {
        JVMClusterUtilInJVM.MasterThread server = hbaseCluster.getMasters().get(serverNumber);
        LOG.info("Stopping " + server.toString());
        server.getMaster().stop("Stopping master " + serverNumber);
        return server;
    }

    /**
     * Wait for the specified master to stop. Removes this thread from list of running threads.
     * @return Name of master that just went down.
     */
    public String waitOnMaster(final int serverNumber) {
        return this.hbaseCluster.waitOnMaster(serverNumber);
    }

    /**
     * Blocks until there is an active master and that master has completed initialization.
     * @return true if an active master becomes available. false if there are no masters left.
     */
    @Override
    public boolean waitForActiveAndReadyMaster(long timeout) throws IOException {
        List<JVMClusterUtilInJVM.MasterThread> mts;
        long start = EnvironmentEdgeManager.currentTime();
        while (!(mts = getMasterThreads()).isEmpty() && (EnvironmentEdgeManager.currentTime() - start) < timeout) {
            for (JVMClusterUtilInJVM.MasterThread mt : mts) {
                if (mt.getMaster().isActiveMaster() && mt.getMaster().isInitialized()) {
                    return true;
                }
            }
            Threads.sleep(100);
        }
        return false;
    }

    /**
     * Returns List of master threads.
     */
    public List<JVMClusterUtilInJVM.MasterThread> getMasterThreads() {
        return this.hbaseCluster.getMasters();
    }

    /**
     * Returns List of live master threads (skips the aborted and the killed)
     */
    public List<JVMClusterUtilInJVM.MasterThread> getLiveMasterThreads() {
        return this.hbaseCluster.getLiveMasters();
    }

    /**
     * Wait for Mini HBase Cluster to shut down.
     */
    public void join() {
        this.hbaseCluster.join();
    }

    /**
     * Shut down the mini HBase cluster
     */
    @Override
    public void shutdown() throws IOException {
        if (this.hbaseCluster != null) {
            this.hbaseCluster.shutdown();
        }
    }

    @Override
    public void close() throws IOException {
    }

    /**
     * @deprecated As of release 2.0.0, this will be removed in HBase 3.0.0 Use
     *             {@link #getClusterMetrics()} instead.
     */
    @Deprecated
    public ClusterStatus getClusterStatus() throws IOException {
        HMasterJVMInterface master = getMaster();
        return master == null ? null : new ClusterStatus(master.getClusterMetrics());
    }

    @Override
    public ClusterMetrics getClusterMetrics() throws IOException {
        HMasterJVMInterface master = getMaster();
        return master == null ? null : master.getClusterMetrics();
    }

    private void executeFlush(HRegion region) throws IOException {
        if (!RegionReplicaUtil.isDefaultReplica(region.getRegionInfo())) {
            return;
        }
        // retry 5 times if we can not flush
        for (int i = 0; i < 5; i++) {
            FlushResult result = region.flush(true);
            if (result.getResult() != FlushResult.Result.CANNOT_FLUSH) {
                return;
            }
            Threads.sleep(1000);
        }
    }

    /**
     * Call flushCache on all regions on all participating regionservers.
     */

    public void flushcache() throws IOException {
      throw new UpgtException("flushcache not supported in upgradable mini cluster");
      /*
        for (JVMClusterUtilInJVM.RegionServerThread t : this.hbaseCluster.getRegionServers()) {
            for (HRegion r : t.getRegionServer().getOnlineRegionsLocalContext()) {
                executeFlush(r);
            }
        }
       */
    }

    /**
     * Call flushCache on all regions of the specified table.
     */
    public void flushcache(TableName tableName) throws IOException {
      throw new UpgtException("flushcache not supported in upgradable mini cluster");
      /*
        for (JVMClusterUtilInJVM.RegionServerThread t : this.hbaseCluster.getRegionServers()) {
            for (HRegion r : t.getRegionServer().getOnlineRegionsLocalContext()) {
                if (r.getTableDescriptor().getTableName().equals(tableName)) {
                    executeFlush(r);
                }
            }
        }
       */
    }

    /**
     * Call flushCache on all regions on all participating regionservers.
     */
    public void compact(boolean major) throws IOException {
      throw new UpgtException("compact not supported in upgradable mini cluster");
      /*
        for (JVMClusterUtilInJVM.RegionServerThread t : this.hbaseCluster.getRegionServers()) {
            for (HRegion r : t.getRegionServer().getOnlineRegionsLocalContext()) {
                if (RegionReplicaUtil.isDefaultReplica(r.getRegionInfo())) {
                    r.compact(major);
                }
            }
        }
       */
    }

    /**
     * Call flushCache on all regions of the specified table.
     */
    public void compact(TableName tableName, boolean major) throws IOException {
      throw new UpgtException("compact not supported in upgradable mini cluster");
      /*
        for (JVMClusterUtilInJVM.RegionServerThread t : this.hbaseCluster.getRegionServers()) {
            for (HRegion r : t.getRegionServer().getOnlineRegionsLocalContext()) {
                if (r.getTableDescriptor().getTableName().equals(tableName)) {
                    if (RegionReplicaUtil.isDefaultReplica(r.getRegionInfo())) {
                        r.compact(major);
                    }
                }
            }
        }
       */
    }

    /**
     * Returns Number of live region servers in the cluster currently.
     */
    public int getNumLiveRegionServers() {
        return this.hbaseCluster.getLiveRegionServers().size();
    }

    /**
     * @return List of region server threads. Does not return the master even though it is also a
     *         region server.
     */
    public List<JVMClusterUtilInJVM.RegionServerThread> getRegionServerThreads() {
        return this.hbaseCluster.getRegionServers();
    }

    /**
     * Returns List of live region server threads (skips the aborted and the killed)
     */
    public List<JVMClusterUtilInJVM.RegionServerThread> getLiveRegionServerThreads() {
        return this.hbaseCluster.getLiveRegionServers();
    }

    /**
     * Grab a numbered region server of your choice.
     * @return region server
     */
    public HRegionServerJVMInterface getRegionServer(int serverNumber) {
        return hbaseCluster.getRegionServer(serverNumber);
    }

    public HRegionServerJVMInterface getRegionServer(ServerName serverName) {
        return hbaseCluster.getRegionServers().stream().map(t -> t.getRegionServer()).filter(r -> r.getServerName().equals(serverName)).findFirst().orElse(null);
    }


    public List<HRegionJVMInterface> getRegions(byte[] tableName) {
        return getRegions(TableName.valueOf(tableName));
    }

    public List<HRegionJVMInterface> getRegions(TableName tableName) {
        List<HRegionJVMInterface> ret = new ArrayList<>();
        for (JVMClusterUtilInJVM.RegionServerThread rst : getRegionServerThreads()) {
            HRegionServerJVMInterface hrs = rst.getRegionServer();
            for (Object region : hrs.getOnlineRegionsLocalContext()) {
                if (((HRegionJVMInterface) region).equalsTableName(tableName.hashCode(), tableName.getNameAsString())) {
                    ret.add((HRegion) region);
                }
            }
        }
        return ret;
    }


    /**
     * @return Index into List of {@link MiniHBaseCluster#getRegionServerThreads()} of HRS carrying
     *         regionName. Returns -1 if none found.
     */
    public int getServerWithMeta() {
        return getServerWith(HRegionInfo.FIRST_META_REGIONINFO.getRegionName());
    }

    /**
     * Get the location of the specified region
     * @param regionName Name of the region in bytes
     * @return Index into List of {@link MiniHBaseCluster#getRegionServerThreads()} of HRS carrying
     *         hbase:meta. Returns -1 if none found.
     */
    public int getServerWith(byte[] regionName) {
        int index = -1;
        int count = 0;
        for (JVMClusterUtilInJVM.RegionServerThread rst : getRegionServerThreads()) {
            HRegionServerJVMInterface hrs = rst.getRegionServer();
            if (!hrs.isStopped()) {
                HRegionJVMInterface region = hrs.getOnlineRegion(regionName);
                if (region != null) {
                    index = count;
                    break;
                }
            }
            count++;
        }
        return index;
    }

    @Override
    public ServerName getServerHoldingRegion(final TableName tn, byte[] regionName) throws IOException {
        // Assume there is only one master thread which is the active master.
        // If there are multiple master threads, the backup master threads
        // should hold some regions. Please refer to #countServedRegions
        // to see how we find out all regions.
        throw new UpgtException("getServerHoldingRegion not supported in upgradable mini cluster");
        /*
        HMasterJVMInterface master = getMaster();
        HRegionJVMInterface region = master.getOnlineRegion(regionName);
        if (region != null) {
            return master.getServerName();
        }
        int index = getServerWith(regionName);
        if (index < 0) {
            return null;
        }
        return getRegionServer(index).getServerName();
         */
    }

    /**
     * Counts the total numbers of regions being served by the currently online region servers by
     * asking each how many regions they have. Does not look at hbase:meta at all. Count includes
     * catalog tables.
     * @return number of regions being served by all region servers
     */
    public long countServedRegions() {
        long count = 0;
        for (JVMClusterUtilInJVM.RegionServerThread rst : getLiveRegionServerThreads()) {
            count += rst.getRegionServer().getNumberOfOnlineRegions();
        }
        for (JVMClusterUtilInJVM.MasterThread mt : getLiveMasterThreads()) {
            count += mt.getMaster().getNumberOfOnlineRegions();
        }
        return count;
    }

    /**
     * Do a simulated kill all masters and regionservers. Useful when it is impossible to bring the
     * mini-cluster back for clean shutdown.
     */
    public void killAll() {
        // Do backups first.
        MasterThread activeMaster = null;
        for (MasterThread masterThread : getMasterThreads()) {
            if (!masterThread.getMaster().isActiveMaster()) {
                masterThread.getMaster().abort("killAll", null);
            } else {
                activeMaster = masterThread;
            }
        }
        // Do active after.
        if (activeMaster != null) {
            activeMaster.getMaster().abort("killAll", null);
        }
        for (RegionServerThread rst : getRegionServerThreads()) {
            rst.getRegionServer().abort("killAll", null);
        }
    }

    @Override
    public void waitUntilShutDown() {
        this.hbaseCluster.join();
    }

    /*
    public List<HRegion> findRegionsForTable(TableName tableName) {
        ArrayList<HRegion> ret = new ArrayList<>();
        for (JVMClusterUtilInJVM.RegionServerThread rst : getRegionServerThreads()) {
            HRegionServerJVMInterface hrs = rst.getRegionServer();
            for (Region region : hrs.getRegions(tableName)) {
                if (region.getTableDescriptor().getTableName().equals(tableName)) {
                    ret.add((HRegion) region);
                }
            }
        }
        return ret;
    }
     */

    protected int getRegionServerIndex(ServerName serverName) {
        // we have a small number of region servers, this should be fine for now.
        List<RegionServerThread> servers = getRegionServerThreads();
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).getRegionServer().getServerName().equals(serverName)) {
                return i;
            }
        }
        return -1;
    }

    protected int getMasterIndex(ServerName serverName) {
        List<MasterThread> masters = getMasterThreads();
        for (int i = 0; i < masters.size(); i++) {
            if (masters.get(i).getMaster().getServerName().equals(serverName)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public AdminService.BlockingInterface getAdminProtocol(ServerName serverName) throws IOException {
        throw new UpgtException("Admin protocol not supported in MiniHBaseClusterInJVM");
        //return getRegionServer(getRegionServerIndex(serverName)).getRSRpcServices();
    }

    @Override
    public ClientService.BlockingInterface getClientProtocol(ServerName serverName) throws IOException {
        throw new UpgtException("Client protocol not supported in MiniHBaseClusterInJVM");
        //return getRegionServer(getRegionServerIndex(serverName)).getRSRpcServices();
    }

    private final UpgradePlan plan = UpgradePlan.fromSystemPropertyVersions();

    private final NodeLoaderFactory factory = NodeLoaderFactory.createForPlan(plan);

    private HMasterInstance hMasterInstance;

    private HRegionServerInstance hRegionServerInstance;

    {
        this.hMasterInstance = this.factory.createInstance("HMaster", this.plan, UpgradePlan.START_VERSION, HMasterInstance::new);
        this.hRegionServerInstance = this.factory.createInstance("HRegionServer", this.plan, UpgradePlan.START_VERSION, HRegionServerInstance::new);
    }

    HMasterInstance getOrCreateHMasterInstance() {
        if (this.hMasterInstance == null) {
            this.hMasterInstance = this.factory.createInstance("HMaster", this.plan, UpgradePlan.START_VERSION, HMasterInstance::new);
        }
        return this.hMasterInstance;
    }

    HRegionServerInstance getOrCreateHRegionServerInstance() {
        if (this.hRegionServerInstance == null) {
            this.hRegionServerInstance = this.factory.createInstance("HRegionServer", this.plan, UpgradePlan.START_VERSION, HRegionServerInstance::new);
        }
        return this.hRegionServerInstance;
    }
}
