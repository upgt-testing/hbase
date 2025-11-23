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
package org.apache.hadoop.hbase.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.regionserver.HRegion;
import org.apache.hadoop.hbase.testclassification.ClientTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestMalformedCellFromClient}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * @see TestMalformedCellFromClient Original test using MiniHBaseCluster
 */
@Category({ MediumTests.class, ClientTests.class })
public class TestMalformedCellFromClient_ProcessBased extends ProcessBasedUpgradeTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(TestMalformedCellFromClient_ProcessBased.class);
  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestMalformedCellFromClient_ProcessBased.class);

  private static final byte[] FAMILY = Bytes.toBytes("testFamily");
  private static final int CELL_SIZE = 100;
  private static final TableName TABLE_NAME = TableName.valueOf("TestMalformedCellFromClient");

  @Test
  public void testRegionException_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testRegionException();
  }

  @Test
  public void testRegionException_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testRegionException();
  }

  @Test
  public void testRegionException_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    testRegionException();
  }

  /**
   * The purpose of this ut is to check the consistency between the exception and results. If the
   * RetriesExhaustedWithDetailsException contains the whole batch, each result should be of IOE.
   * Otherwise, the row operation which is not in the exception should have a true result.
   */
  private void testRegionException() throws Exception {
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    cluster.waitClusterUp();

    // Wait for Master to be fully initialized before creating tables
    // This prevents PleaseHoldException: Master is initializing
    cluster.waitForActiveAndReadyMaster(60000);

    connection = cluster.getConnection();
    admin = connection.getAdmin();

    // Wait for Master initialization by retrying listTables with higher retry count
    // This test intentionally sets retries to 0 later for testing purposes,
    // but we need to wait for Master initialization first
    Configuration tempConf = new Configuration(conf);
    tempConf.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 10);
    try (Connection tempConn = ConnectionFactory.createConnection(tempConf);
         Admin tempAdmin = tempConn.getAdmin()) {
      tempAdmin.listTableDescriptors(); // Will retry until Master is initialized
    }

    // Now set retries to 0 for the actual test
    conf.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 0);

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableDescriptor desc = TableDescriptorBuilder.newBuilder(TABLE_NAME)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .setValue(HRegion.HBASE_MAX_CELL_SIZE_KEY, String.valueOf(CELL_SIZE)).build();
    admin.createTable(desc);

    checkpoint("AFTER_CREATE_TABLE");

    List<Row> batches = new ArrayList<>();
    batches.add(new Put(Bytes.toBytes("good")).addColumn(FAMILY, null, new byte[10]));
    // the rm is used to prompt the region exception.
    // see RSRpcServices#multi
    RowMutations rm = new RowMutations(Bytes.toBytes("fail"));
    rm.add(new Put(rm.getRow()).addColumn(FAMILY, null, new byte[CELL_SIZE]));
    batches.add(rm);
    Object[] results = new Object[batches.size()];

    try (Table table = connection.getTable(TABLE_NAME)) {
      Throwable exceptionByCaught = null;
      try {
        table.batch(batches, results);
        fail("Where is the exception? We put the malformed cells!!!");
      } catch (RetriesExhaustedWithDetailsException e) {
        for (Throwable throwable : e.getCauses()) {
          assertNotNull(throwable);
        }
        assertEquals(1, e.getNumExceptions());
        exceptionByCaught = e.getCause(0);
      }
      for (Object obj : results) {
        assertNotNull(obj);
      }
      assertEquals(Result.class, results[0].getClass());
      assertEquals(exceptionByCaught.getClass(), results[1].getClass());
      Result result = table.get(new Get(Bytes.toBytes("good")));
      assertEquals(1, result.size());
      Cell cell = result.getColumnLatestCell(FAMILY, null);
      assertTrue(Bytes.equals(CellUtil.cloneValue(cell), new byte[10]));
    }

    // Cleanup
    admin.disableTable(TABLE_NAME);
    admin.deleteTable(TABLE_NAME);
  }

  @Test
  public void testRegionExceptionByAsync_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testRegionExceptionByAsync();
  }

  @Test
  public void testRegionExceptionByAsync_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testRegionExceptionByAsync();
  }

  @Test
  public void testRegionExceptionByAsync_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    testRegionExceptionByAsync();
  }

  /**
   * This test verifies region exception doesn't corrupt the results of batch. The prescription is
   * shown below. 1) honor the action result rather than region exception. If the action have both
   * of true result and region exception, the action is fine as the exception is caused by other
   * actions which are in the same region. 2) honor the action exception rather than region
   * exception. If the action have both of action exception and region exception, we deal with the
   * action exception only. If we also handle the region exception for the same action, it will
   * introduce the negative count of actions in progress. The AsyncRequestFuture#waitUntilDone will
   * block forever. If the RetriesExhaustedWithDetailsException contains the whole batch, each
   * result should be of IOE. Otherwise, the row operation which is not in the exception should have
   * a true result. The no-cluster test is in TestAsyncProcessWithRegionException.
   */
  private void testRegionExceptionByAsync() throws Exception {
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    cluster.waitClusterUp();

    // Wait for Master to be fully initialized before creating tables
    // This prevents PleaseHoldException: Master is initializing
    cluster.waitForActiveAndReadyMaster(60000);

    connection = cluster.getConnection();
    admin = connection.getAdmin();

    // Wait for Master initialization by retrying listTables with higher retry count
    // This test intentionally sets retries to 0 later for testing purposes,
    // but we need to wait for Master initialization first
    Configuration tempConf = new Configuration(conf);
    tempConf.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 10);
    try (Connection tempConn = ConnectionFactory.createConnection(tempConf);
         Admin tempAdmin = tempConn.getAdmin()) {
      tempAdmin.listTableDescriptors(); // Will retry until Master is initialized
    }

    // Now set retries to 0 for the actual test
    conf.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 0);

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableDescriptor desc = TableDescriptorBuilder.newBuilder(TABLE_NAME)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .setValue(HRegion.HBASE_MAX_CELL_SIZE_KEY, String.valueOf(CELL_SIZE)).build();
    admin.createTable(desc);

    checkpoint("AFTER_CREATE_TABLE");

    List<Row> batches = new ArrayList<>();
    batches.add(new Put(Bytes.toBytes("good")).addColumn(FAMILY, null, new byte[10]));
    // the rm is used to prompt the region exception.
    // see RSRpcServices#multi
    RowMutations rm = new RowMutations(Bytes.toBytes("fail"));
    rm.add(new Put(rm.getRow()).addColumn(FAMILY, null, new byte[CELL_SIZE]));
    batches.add(rm);
    try (AsyncConnection asyncConnection =
      ConnectionFactory.createAsyncConnection(conf).get()) {
      AsyncTable<AdvancedScanResultConsumer> table = asyncConnection.getTable(TABLE_NAME);
      List<CompletableFuture<AdvancedScanResultConsumer>> results = table.batch(batches);
      assertEquals(2, results.size());
      try {
        results.get(1).get();
        fail("Where is the exception? We put the malformed cells!!!");
      } catch (ExecutionException e) {
        // pass
      }
      Result result = table.get(new Get(Bytes.toBytes("good"))).get();
      assertEquals(1, result.size());
      Cell cell = result.getColumnLatestCell(FAMILY, null);
      assertTrue(Bytes.equals(CellUtil.cloneValue(cell), new byte[10]));
    }

    // Cleanup
    admin.disableTable(TABLE_NAME);
    admin.deleteTable(TABLE_NAME);
  }

  // TRANSFORMATION NOTE: testAtomicOperations removed.
  // This test requires direct internal access to HRegion, HRegionServer, and RSRpcServices
  // to manually construct and submit RPC requests for testing atomic operation error handling.
  // Specifically requires:
  // - TEST_UTIL.getMiniHBaseCluster().getRegions() for HRegion access (line 195)
  // - getMiniHBaseCluster().getRegionServer() for HRegionServer access (lines 214-215)
  // - rs.getRSRpcServices().multi() for direct RPC invocation (line 217)
  // - Mockito mocking of HBaseRpcController (lines 212-213)
  // No client API exists to submit manually-constructed multi-action requests at the
  // RPC protocol level. This test verifies internal server-side batch processing logic.
  //
  // The core batch error handling behavior is still tested by testRegionException(),
  // testRegionExceptionByAsync(), testNonAtomicOperations(), and testRowMutations()
  // which use standard client APIs.

  @Test
  public void testNonAtomicOperations_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testNonAtomicOperations();
  }

  @Test
  public void testNonAtomicOperations_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testNonAtomicOperations();
  }

  @Test
  public void testNonAtomicOperations_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    testNonAtomicOperations();
  }

  /**
   * This test depends on how regionserver process the batch ops. 1) group the put/delete until
   * meeting the increment 2) process the batch of put/delete 3) process the increment see
   * RSRpcServices#doNonAtomicRegionMutation
   */
  private void testNonAtomicOperations() throws Exception {
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    cluster.waitClusterUp();

    // Wait for Master to be fully initialized before creating tables
    // This prevents PleaseHoldException: Master is initializing
    cluster.waitForActiveAndReadyMaster(60000);

    connection = cluster.getConnection();
    admin = connection.getAdmin();

    // Wait for Master initialization by retrying listTables with higher retry count
    // This test intentionally sets retries to 0 later for testing purposes,
    // but we need to wait for Master initialization first
    Configuration tempConf = new Configuration(conf);
    tempConf.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 10);
    try (Connection tempConn = ConnectionFactory.createConnection(tempConf);
         Admin tempAdmin = tempConn.getAdmin()) {
      tempAdmin.listTableDescriptors(); // Will retry until Master is initialized
    }

    // Now set retries to 0 for the actual test
    conf.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 0);

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableDescriptor desc = TableDescriptorBuilder.newBuilder(TABLE_NAME)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .setValue(HRegion.HBASE_MAX_CELL_SIZE_KEY, String.valueOf(CELL_SIZE)).build();
    admin.createTable(desc);

    checkpoint("AFTER_CREATE_TABLE");

    Increment inc = new Increment(Bytes.toBytes("good")).addColumn(FAMILY, null, 100);
    List<Row> batches = new ArrayList<>();
    // the first and second puts will be group by regionserver
    batches.add(new Put(Bytes.toBytes("fail")).addColumn(FAMILY, null, new byte[CELL_SIZE]));
    batches.add(new Put(Bytes.toBytes("fail")).addColumn(FAMILY, null, new byte[CELL_SIZE]));
    // this Increment should succeed
    batches.add(inc);
    // this put should succeed
    batches.add(new Put(Bytes.toBytes("good")).addColumn(FAMILY, null, new byte[1]));
    Object[] objs = new Object[batches.size()];
    try (Table table = connection.getTable(TABLE_NAME)) {
      table.batch(batches, objs);
      fail("Where is the exception? We put the malformed cells!!!");
    } catch (RetriesExhaustedWithDetailsException e) {
      assertEquals(2, e.getNumExceptions());
      for (int i = 0; i != e.getNumExceptions(); ++i) {
        assertNotNull(e.getCause(i));
        assertEquals(e.getCause(i).getClass().getName(), org.apache.hadoop.hbase.DoNotRetryIOException.class, e.getCause(i).getClass());
        assertEquals("fail", Bytes.toString(e.getRow(i).getRow()));
      }
    } finally {
      assertObjects(objs, batches.size());
      assertTrue(objs[0] instanceof IOException);
      assertTrue(objs[1] instanceof IOException);
      assertEquals(Result.class, objs[2].getClass());
      assertEquals(Result.class, objs[3].getClass());
    }

    // Cleanup
    admin.disableTable(TABLE_NAME);
    admin.deleteTable(TABLE_NAME);
  }

  @Test
  public void testRowMutations_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testRowMutations();
  }

  @Test
  public void testRowMutations_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testRowMutations();
  }

  @Test
  public void testRowMutations_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    testRowMutations();
  }

  private void testRowMutations() throws Exception {
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    cluster.waitClusterUp();

    // Wait for Master to be fully initialized before creating tables
    // This prevents PleaseHoldException: Master is initializing
    cluster.waitForActiveAndReadyMaster(60000);

    connection = cluster.getConnection();
    admin = connection.getAdmin();

    // Wait for Master initialization by retrying listTables with higher retry count
    // This test intentionally sets retries to 0 later for testing purposes,
    // but we need to wait for Master initialization first
    Configuration tempConf = new Configuration(conf);
    tempConf.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 10);
    try (Connection tempConn = ConnectionFactory.createConnection(tempConf);
         Admin tempAdmin = tempConn.getAdmin()) {
      tempAdmin.listTableDescriptors(); // Will retry until Master is initialized
    }

    // Now set retries to 0 for the actual test
    conf.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 0);

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableDescriptor desc = TableDescriptorBuilder.newBuilder(TABLE_NAME)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .setValue(HRegion.HBASE_MAX_CELL_SIZE_KEY, String.valueOf(CELL_SIZE)).build();
    admin.createTable(desc);

    checkpoint("AFTER_CREATE_TABLE");

    Put put = new Put(Bytes.toBytes("good")).addColumn(FAMILY, null, new byte[1]);
    List<Row> batches = new ArrayList<>();
    RowMutations mutations = new RowMutations(Bytes.toBytes("fail"));
    // the first and second puts will be group by regionserver
    mutations.add(new Put(Bytes.toBytes("fail")).addColumn(FAMILY, null, new byte[CELL_SIZE]));
    mutations.add(new Put(Bytes.toBytes("fail")).addColumn(FAMILY, null, new byte[CELL_SIZE]));
    batches.add(mutations);
    // this bm should succeed
    mutations = new RowMutations(Bytes.toBytes("good"));
    mutations.add(put);
    mutations.add(put);
    batches.add(mutations);
    Object[] objs = new Object[batches.size()];
    try (Table table = connection.getTable(TABLE_NAME)) {
      table.batch(batches, objs);
      fail("Where is the exception? We put the malformed cells!!!");
    } catch (RetriesExhaustedWithDetailsException e) {
      assertEquals(1, e.getNumExceptions());
      for (int i = 0; i != e.getNumExceptions(); ++i) {
        assertNotNull(e.getCause(i));
        assertTrue(e.getCause(i) instanceof IOException);
        assertEquals("fail", Bytes.toString(e.getRow(i).getRow()));
      }
    } finally {
      assertObjects(objs, batches.size());
      assertTrue(objs[0] instanceof IOException);
      assertEquals(Result.class, objs[1].getClass());
    }

    // Cleanup
    admin.disableTable(TABLE_NAME);
    admin.deleteTable(TABLE_NAME);
  }

  private static void assertObjects(Object[] objs, int expectedSize) {
    int count = 0;
    for (Object obj : objs) {
      assertNotNull(obj);
      ++count;
    }
    assertEquals(expectedSize, count);
  }
}
