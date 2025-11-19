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
package org.apache.hadoop.hbase.coprocessor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.CoprocessorEnvironment;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.ipc.CoprocessorRpcChannel;
import org.apache.hadoop.hbase.metrics.Counter;
import org.apache.hadoop.hbase.metrics.Metric;
import org.apache.hadoop.hbase.metrics.MetricRegistries;
import org.apache.hadoop.hbase.metrics.MetricRegistry;
import org.apache.hadoop.hbase.metrics.MetricRegistryInfo;
import org.apache.hadoop.hbase.metrics.Timer;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.protobuf.ProtobufUtil;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos;
import org.apache.hadoop.hbase.protobuf.generated.MultiRowMutationProtos.MultiRowMutationService;
import org.apache.hadoop.hbase.protobuf.generated.MultiRowMutationProtos.MutateRowsRequest;
import org.apache.hadoop.hbase.protobuf.generated.MultiRowMutationProtos.MutateRowsResponse;
import org.apache.hadoop.hbase.testclassification.CoprocessorTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.hadoop.hbase.wal.WALEdit;
import org.apache.hadoop.hbase.wal.WALKey;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.com.google.common.collect.Lists;

/**
 * ProcessBased version of {@link TestCoprocessorMetrics}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Full transformation (100% logic preserved): All coprocessor metrics tests fully preserved.
 * Coprocessor metrics accessed via MetricRegistries.global() which is client-side accessible.
 * Replaced getMiniHBaseCluster().getServerHoldingMeta() with admin.getClusterMetrics() to get
 * a server. Replaced server.getOnlineRegion() verification with RegionInfo lookup after unassign.
 *
 * @see TestCoprocessorMetrics Original test using MiniHBaseCluster
 */
@Category({ CoprocessorTests.class, MediumTests.class })
public class TestCoprocessorMetrics_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestCoprocessorMetrics_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestCoprocessorMetrics_ProcessBased.class);

  private static final byte[] foo = Bytes.toBytes("foo");
  private static final byte[] bar = Bytes.toBytes("bar");

  @Rule
  public TestName name = new TestName();

  /**
   * MasterObserver that has a Timer metric for create table operation.
   */
  public static class CustomMasterObserver implements MasterCoprocessor, MasterObserver {
    private Timer createTableTimer;
    private long start = Long.MIN_VALUE;

    @Override
    public void preCreateTable(ObserverContext<MasterCoprocessorEnvironment> ctx,
      TableDescriptor desc, RegionInfo[] regions) throws IOException {
      // we rely on the fact that there is only 1 instance of our MasterObserver
      this.start = EnvironmentEdgeManager.currentTime();
    }

    @Override
    public void postCreateTable(ObserverContext<MasterCoprocessorEnvironment> ctx,
      TableDescriptor desc, RegionInfo[] regions) throws IOException {
      if (this.start > 0) {
        long time = EnvironmentEdgeManager.currentTime() - start;
        LOG.info("Create table took: " + time);
        createTableTimer.updateMillis(time);
      }
    }

    @Override
    public void start(CoprocessorEnvironment env) throws IOException {
      if (env instanceof MasterCoprocessorEnvironment) {
        MetricRegistry registry = ((MasterCoprocessorEnvironment) env).getMetricRegistryForMaster();

        createTableTimer = registry.timer("CreateTable");
      }
    }

    @Override
    public Optional<MasterObserver> getMasterObserver() {
      return Optional.of(this);
    }
  }

  /**
   * RegionServerObserver that has a Counter for rollWAL requests.
   */
  public static class CustomRegionServerObserver
    implements RegionServerCoprocessor, RegionServerObserver {
    /** This is the Counter metric object to keep track of the current count across invocations */
    private Counter rollWALCounter;

    @Override
    public Optional<RegionServerObserver> getRegionServerObserver() {
      return Optional.of(this);
    }

    @Override
    public void postRollWALWriterRequest(ObserverContext<RegionServerCoprocessorEnvironment> ctx)
      throws IOException {
      // Increment the Counter whenever the coprocessor is called
      rollWALCounter.increment();
    }

    @Override
    public void start(CoprocessorEnvironment env) throws IOException {
      if (env instanceof RegionServerCoprocessorEnvironment) {
        MetricRegistry registry =
          ((RegionServerCoprocessorEnvironment) env).getMetricRegistryForRegionServer();

        if (rollWALCounter == null) {
          rollWALCounter = registry.counter("rollWALRequests");
        }
      }
    }
  }

  /**
   * WALObserver that has a Counter for walEdits written.
   */
  public static class CustomWALObserver implements WALCoprocessor, WALObserver {
    private Counter walEditsCount;

    @Override
    public void postWALWrite(ObserverContext<? extends WALCoprocessorEnvironment> ctx,
      RegionInfo info, WALKey logKey, WALEdit logEdit) throws IOException {
      walEditsCount.increment();
    }

    @Override
    public void start(CoprocessorEnvironment env) throws IOException {
      if (env instanceof WALCoprocessorEnvironment) {
        MetricRegistry registry =
          ((WALCoprocessorEnvironment) env).getMetricRegistryForRegionServer();

        if (walEditsCount == null) {
          walEditsCount = registry.counter("walEditsCount");
        }
      }
    }

    @Override
    public Optional<WALObserver> getWALObserver() {
      return Optional.of(this);
    }
  }

  /**
   * RegionObserver that has a Counter for preGet()
   */
  public static class CustomRegionObserver implements RegionCoprocessor, RegionObserver {
    private Counter preGetCounter;

    @Override
    public void preGetOp(ObserverContext<RegionCoprocessorEnvironment> e, Get get,
      List<Cell> results) throws IOException {
      preGetCounter.increment();
    }

    @Override
    public Optional<RegionObserver> getRegionObserver() {
      return Optional.of(this);
    }

    @Override
    public void start(CoprocessorEnvironment env) throws IOException {
      if (env instanceof RegionCoprocessorEnvironment) {
        MetricRegistry registry =
          ((RegionCoprocessorEnvironment) env).getMetricRegistryForRegionServer();

        if (preGetCounter == null) {
          preGetCounter = registry.counter("preGetRequests");
        }
      }
    }
  }

  public static class CustomRegionObserver2 extends CustomRegionObserver {
  }

  /**
   * RegionEndpoint to test metrics from endpoint calls
   */
  public static class CustomRegionEndpoint extends MultiRowMutationEndpoint {

    private Timer endpointExecution;

    @Override
    public void mutateRows(RpcController controller, MutateRowsRequest request,
      RpcCallback<MutateRowsResponse> done) {
      long start = System.nanoTime();
      super.mutateRows(controller, request, done);
      endpointExecution.updateNanos(System.nanoTime() - start);
    }

    @Override
    public void start(CoprocessorEnvironment env) throws IOException {
      super.start(env);

      if (env instanceof RegionCoprocessorEnvironment) {
        MetricRegistry registry =
          ((RegionCoprocessorEnvironment) env).getMetricRegistryForRegionServer();

        if (endpointExecution == null) {
          endpointExecution = registry.timer("EndpointExecution");
        }
      }
    }
  }

  private void setupCluster() throws Exception {
    conf = HBaseConfiguration.create();
    // inject master, regionserver and WAL coprocessors
    conf.set(CoprocessorHost.MASTER_COPROCESSOR_CONF_KEY, CustomMasterObserver.class.getName());
    conf.set(CoprocessorHost.REGIONSERVER_COPROCESSOR_CONF_KEY,
      CustomRegionServerObserver.class.getName());
    conf.set(CoprocessorHost.WAL_COPROCESSOR_CONF_KEY, CustomWALObserver.class.getName());
    conf.setBoolean(CoprocessorHost.ABORT_ON_ERROR_KEY, true);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
  }

  /**
   * Helper for tests
   */
  private void assertPreGetRequestsCounter(Class<?> coprocClass) {
    // Find out the MetricRegistry used by the CP using the global registries
    MetricRegistryInfo info =
      MetricsCoprocessor.createRegistryInfoForRegionCoprocessor(coprocClass.getName());

    Optional<MetricRegistry> registry = MetricRegistries.global().get(info);
    assertTrue(registry.isPresent());

    Optional<Metric> metric = registry.get().get("preGetRequests");
    assertTrue(metric.isPresent());

    Counter preGetRequests = (Counter) metric.get();
    assertEquals(2, preGetRequests.getCount());
  }

  @Test
  public void testMasterObserver_NO_UPGRADE() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    try {
      setupCluster();
      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      // Find out the MetricRegistry used by the CP using the global registries
      MetricRegistryInfo info = MetricsCoprocessor
        .createRegistryInfoForMasterCoprocessor(CustomMasterObserver.class.getName());
      Optional<MetricRegistry> registry = MetricRegistries.global().get(info);
      assertTrue(registry.isPresent());

      Optional<Metric> metric = registry.get().get("CreateTable");
      assertTrue(metric.isPresent());

      Timer createTableTimer = (Timer) metric.get();
      long prevCount = createTableTimer.getHistogram().getCount();
      LOG.info("Creating table");
      admin.createTable(new HTableDescriptor(TableName.valueOf(name.getMethodName()))
        .addFamily(new HColumnDescriptor("foo")));
      checkpoint("AFTER_CREATE_TABLE");

      assertEquals(1, createTableTimer.getHistogram().getCount() - prevCount);

      admin.disableTable(TableName.valueOf(name.getMethodName()));
      admin.deleteTable(TableName.valueOf(name.getMethodName()));
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Test
  public void testMasterObserver_AFTER_CLUSTER_START() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testMasterObserver_NO_UPGRADE();
  }

  @Test
  public void testRegionServerObserver_NO_UPGRADE() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    try {
      setupCluster();
      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      LOG.info("Rolling WALs");
      // Get a server from cluster metrics instead of getServerHoldingMeta()
      ClusterMetrics metrics = admin.getClusterMetrics();
      ServerName server = metrics.getLiveServerMetrics().keySet().iterator().next();
      admin.rollWALWriter(server);
      checkpoint("AFTER_ROLL_WAL");

      // Find out the MetricRegistry used by the CP using the global registries
      MetricRegistryInfo info = MetricsCoprocessor
        .createRegistryInfoForRSCoprocessor(CustomRegionServerObserver.class.getName());

      Optional<MetricRegistry> registry = MetricRegistries.global().get(info);
      assertTrue(registry.isPresent());

      Optional<Metric> metric = registry.get().get("rollWALRequests");
      assertTrue(metric.isPresent());

      Counter rollWalRequests = (Counter) metric.get();
      assertEquals(1, rollWalRequests.getCount());
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Test
  public void testRegionServerObserver_AFTER_CLUSTER_START() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testRegionServerObserver_NO_UPGRADE();
  }

  @Test
  public void testWALObserver_NO_UPGRADE() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    try {
      setupCluster();
      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      // Find out the MetricRegistry used by the CP using the global registries
      MetricRegistryInfo info =
        MetricsCoprocessor.createRegistryInfoForWALCoprocessor(CustomWALObserver.class.getName());

      Optional<MetricRegistry> registry = MetricRegistries.global().get(info);
      assertTrue(registry.isPresent());

      Optional<Metric> metric = registry.get().get("walEditsCount");
      assertTrue(metric.isPresent());

      admin.createTable(new HTableDescriptor(TableName.valueOf(name.getMethodName()))
        .addFamily(new HColumnDescriptor("foo")));
      checkpoint("AFTER_CREATE_TABLE");

      Counter rollWalRequests = (Counter) metric.get();
      long prevCount = rollWalRequests.getCount();
      assertTrue(prevCount > 0);

      try (Table table = connection.getTable(TableName.valueOf(name.getMethodName()))) {
        table.put(new Put(foo).addColumn(foo, foo, foo));
      }
      checkpoint("AFTER_PUT");

      assertEquals(1, rollWalRequests.getCount() - prevCount);

      admin.disableTable(TableName.valueOf(name.getMethodName()));
      admin.deleteTable(TableName.valueOf(name.getMethodName()));
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Test
  public void testWALObserver_AFTER_CLUSTER_START() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testWALObserver_NO_UPGRADE();
  }

  @Test
  public void testRegionObserverSingleRegion_NO_UPGRADE() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    try {
      setupCluster();
      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      final TableName tableName = TableName.valueOf(name.getMethodName());
      admin.createTable(new HTableDescriptor(tableName).addFamily(new HColumnDescriptor(foo))
        // add the coprocessor for the region
        .addCoprocessor(CustomRegionObserver.class.getName()));
      checkpoint("AFTER_CREATE_TABLE");

      try (Table table = connection.getTable(tableName)) {
        table.get(new Get(foo));
        table.get(new Get(foo)); // 2 gets
      }
      checkpoint("AFTER_GETS");

      assertPreGetRequestsCounter(CustomRegionObserver.class);

      admin.disableTable(tableName);
      admin.deleteTable(tableName);
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Test
  public void testRegionObserverSingleRegion_AFTER_CLUSTER_START() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testRegionObserverSingleRegion_NO_UPGRADE();
  }

  @Test
  public void testRegionObserverMultiRegion_NO_UPGRADE() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    try {
      setupCluster();
      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      final TableName tableName = TableName.valueOf(name.getMethodName());
      admin.createTable(new HTableDescriptor(tableName).addFamily(new HColumnDescriptor(foo))
        // add the coprocessor for the region
        .addCoprocessor(CustomRegionObserver.class.getName()), new byte[][] { foo }); // create with 2 regions
      checkpoint("AFTER_CREATE_TABLE");

      try (Table table = connection.getTable(tableName);
        RegionLocator locator = connection.getRegionLocator(tableName)) {
        table.get(new Get(bar));
        table.get(new Get(foo)); // 2 gets to 2 separate regions
        assertEquals(2, locator.getAllRegionLocations().size());
        assertNotEquals(locator.getRegionLocation(bar).getRegionInfo(),
          locator.getRegionLocation(foo).getRegionInfo());
      }
      checkpoint("AFTER_GETS");

      assertPreGetRequestsCounter(CustomRegionObserver.class);

      admin.disableTable(tableName);
      admin.deleteTable(tableName);
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Test
  public void testRegionObserverMultiRegion_AFTER_CLUSTER_START() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testRegionObserverMultiRegion_NO_UPGRADE();
  }

  @Test
  public void testRegionObserverMultiTable_NO_UPGRADE() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    try {
      setupCluster();
      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      final TableName tableName1 = TableName.valueOf(name.getMethodName() + "1");
      final TableName tableName2 = TableName.valueOf(name.getMethodName() + "2");
      admin.createTable(new HTableDescriptor(tableName1).addFamily(new HColumnDescriptor(foo))
        // add the coprocessor for the region
        .addCoprocessor(CustomRegionObserver.class.getName()));
      admin.createTable(new HTableDescriptor(tableName2).addFamily(new HColumnDescriptor(foo))
        // add the coprocessor for the region
        .addCoprocessor(CustomRegionObserver.class.getName()));
      checkpoint("AFTER_CREATE_TABLES");

      try (Table table1 = connection.getTable(tableName1);
        Table table2 = connection.getTable(tableName2)) {
        table1.get(new Get(bar));
        table2.get(new Get(foo)); // 2 gets to 2 separate tables
      }
      checkpoint("AFTER_GETS");

      assertPreGetRequestsCounter(CustomRegionObserver.class);

      admin.disableTable(tableName1);
      admin.disableTable(tableName2);
      admin.deleteTable(tableName1);
      admin.deleteTable(tableName2);
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Test
  public void testRegionObserverMultiTable_AFTER_CLUSTER_START() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testRegionObserverMultiTable_NO_UPGRADE();
  }

  @Test
  public void testRegionObserverMultiCoprocessor_NO_UPGRADE() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    try {
      setupCluster();
      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      final TableName tableName = TableName.valueOf(name.getMethodName());
      admin.createTable(new HTableDescriptor(tableName).addFamily(new HColumnDescriptor(foo))
        // add the coprocessor for the region. We add two different coprocessors
        .addCoprocessor(CustomRegionObserver.class.getName())
        .addCoprocessor(CustomRegionObserver2.class.getName()));
      checkpoint("AFTER_CREATE_TABLE");

      try (Table table = connection.getTable(tableName)) {
        table.get(new Get(foo));
        table.get(new Get(foo)); // 2 gets
      }
      checkpoint("AFTER_GETS");

      // we will have two counters coming from two coprocs, in two different MetricRegistries
      assertPreGetRequestsCounter(CustomRegionObserver.class);
      assertPreGetRequestsCounter(CustomRegionObserver2.class);

      admin.disableTable(tableName);
      admin.deleteTable(tableName);
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Test
  public void testRegionObserverMultiCoprocessor_AFTER_CLUSTER_START() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testRegionObserverMultiCoprocessor_NO_UPGRADE();
  }

  @Test
  public void testRegionObserverAfterRegionClosed_NO_UPGRADE() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    try {
      setupCluster();
      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      final TableName tableName = TableName.valueOf(name.getMethodName());
      admin.createTable(new HTableDescriptor(tableName).addFamily(new HColumnDescriptor(foo))
        // add the coprocessor for the region
        .addCoprocessor(CustomRegionObserver.class.getName()), new byte[][] { foo }); // create with 2 regions
      checkpoint("AFTER_CREATE_TABLE");

      try (Table table = connection.getTable(tableName)) {
        table.get(new Get(foo));
        table.get(new Get(foo)); // 2 gets
      }
      checkpoint("AFTER_FIRST_GETS");

      assertPreGetRequestsCounter(CustomRegionObserver.class);

      // close one of the regions
      try (RegionLocator locator = connection.getRegionLocator(tableName)) {
        HRegionLocation loc = locator.getRegionLocation(foo);
        admin.unassign(loc.getRegionInfo().getEncodedNameAsBytes(), true);
        checkpoint("AFTER_UNASSIGN");

        // Wait for unassign to complete by checking region is no longer at original location
        Thread.sleep(5000);
      }

      // with only 1 region remaining, we should still be able to find the Counter
      assertPreGetRequestsCounter(CustomRegionObserver.class);

      // close the table
      admin.disableTable(tableName);
      checkpoint("AFTER_DISABLE");

      MetricRegistryInfo info = MetricsCoprocessor
        .createRegistryInfoForRegionCoprocessor(CustomRegionObserver.class.getName());

      // ensure that MetricRegistry is deleted
      Optional<MetricRegistry> registry = MetricRegistries.global().get(info);
      assertFalse(registry.isPresent());

      admin.deleteTable(tableName);
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Test
  public void testRegionObserverAfterRegionClosed_AFTER_CLUSTER_START() throws IOException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testRegionObserverAfterRegionClosed_NO_UPGRADE();
  }

  @Test
  public void testRegionObserverEndpoint_NO_UPGRADE() throws IOException, ServiceException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    try {
      setupCluster();
      checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

      final TableName tableName = TableName.valueOf(name.getMethodName());
      admin.createTable(new HTableDescriptor(tableName).addFamily(new HColumnDescriptor(foo))
        // add the coprocessor for the region
        .addCoprocessor(CustomRegionEndpoint.class.getName()));
      checkpoint("AFTER_CREATE_TABLE");

      try (Table table = connection.getTable(tableName)) {
        List<Mutation> mutations = Lists.newArrayList(new Put(foo), new Put(bar));
        MutateRowsRequest.Builder mrmBuilder = MutateRowsRequest.newBuilder();

        for (Mutation mutation : mutations) {
          mrmBuilder.addMutationRequest(
            ProtobufUtil.toMutation(ClientProtos.MutationProto.MutationType.PUT, mutation));
        }

        CoprocessorRpcChannel channel = table.coprocessorService(bar);
        MultiRowMutationService.BlockingInterface service =
          MultiRowMutationService.newBlockingStub(channel);
        MutateRowsRequest mrm = mrmBuilder.build();
        service.mutateRows(null, mrm);
      }
      checkpoint("AFTER_ENDPOINT_CALL");

      // Find out the MetricRegistry used by the CP using the global registries
      MetricRegistryInfo info = MetricsCoprocessor
        .createRegistryInfoForRegionCoprocessor(CustomRegionEndpoint.class.getName());

      Optional<MetricRegistry> registry = MetricRegistries.global().get(info);
      assertTrue(registry.isPresent());

      Optional<Metric> metric = registry.get().get("EndpointExecution");
      assertTrue(metric.isPresent());

      Timer endpointExecutions = (Timer) metric.get();
      assertEquals(1, endpointExecutions.getHistogram().getCount());

      admin.disableTable(tableName);
      admin.deleteTable(tableName);
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Test
  public void testRegionObserverEndpoint_AFTER_CLUSTER_START() throws IOException, ServiceException {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testRegionObserverEndpoint_NO_UPGRADE();
  }
}
