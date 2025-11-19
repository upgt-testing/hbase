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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.FlakeyTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestMultiParallel}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Note: This is a reduced version containing only pure client-side batch operation tests.
 * Tests requiring internal RegionServer thread access, abort operations, or static coprocessor
 * counters have been removed.
 *
 * @see TestMultiParallel Original test using MiniHBaseCluster
 */
@Category({ MediumTests.class, FlakeyTests.class })
public class TestMultiParallel_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestMultiParallel_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestMultiParallel_ProcessBased.class);

  private static final byte[] VALUE = Bytes.toBytes("value");
  private static final byte[] QUALIFIER = Bytes.toBytes("qual");
  private static final String FAMILY = "family";
  private static final TableName TEST_TABLE = TableName.valueOf("multi_test_table");
  private static final byte[] BYTES_FAMILY = Bytes.toBytes(FAMILY);
  private static final byte[][] KEYS = makeKeys();

  private static byte[][] makeKeys() {
    byte[][] starterKeys = new byte[][] {
      Bytes.toBytes("bbb"),
      Bytes.toBytes("ddd"),
      Bytes.toBytes("fff"),
      Bytes.toBytes("hhh"),
      Bytes.toBytes("jjj")
    };
    // Create a "non-uniform" test set with the following characteristics:
    // a) Unequal number of keys per region
    // Don't use integer as a multiple, so that we have a number of keys that is
    // not a multiple of the number of regions
    int numKeys = (int) (starterKeys.length * 10.33F);

    List<byte[]> keys = new ArrayList<>();
    for (int i = 0; i < numKeys; i++) {
      int kIdx = i % starterKeys.length;
      byte[] k = starterKeys[kIdx];
      byte[] cp = new byte[k.length + 1];
      System.arraycopy(k, 0, cp, 0, k.length);
      cp[k.length] = new Integer(i % 256).byteValue();
      keys.add(cp);
    }

    // b) Same duplicate keys (showing multiple Gets/Puts to the same row, which
    // should work)
    // c) keys are not in sorted order (within a region), to ensure that the
    // sorting code and index mapping doesn't break the functionality
    for (int i = 0; i < 100; i++) {
      int kIdx = i % starterKeys.length;
      byte[] k = starterKeys[kIdx];
      byte[] cp = new byte[k.length + 1];
      System.arraycopy(k, 0, cp, 0, k.length);
      cp[k.length] = new Integer(i % 256).byteValue();
      keys.add(cp);
    }
    return keys.toArray(new byte[][] { new byte[] {} });
  }

  private List<Put> constructPutRequests() {
    List<Put> puts = new ArrayList<>();
    for (byte[] k : KEYS) {
      Put put = new Put(k);
      put.addColumn(BYTES_FAMILY, QUALIFIER, VALUE);
      puts.add(put);
    }
    return puts;
  }

  // TRANSFORMATION NOTE: The following tests removed due to internal access requirements:
  // - testActiveThreadsCount: Requires ThreadPoolExecutor.getLargestPoolSize() verification
  //   against region server count via RegionLocator
  // - testFlushCommitsNoAbort: Requires getMiniHBaseCluster().getLiveRegionServerThreads()
  //   and direct RegionServer.abort() access
  // - testFlushCommitsWithAbort: Same as above
  // - testBatchWithPut: Requires getLiveRegionServerThreads() and RegionServer.abort()
  //
  // @Before balancer logic removed: Depends on MyMasterObserver static counters which
  // don't work across process boundaries, and requires getMaster().getAssignmentManager().
  //
  // The following tests are fully preserved (pure client-side):

  @Test
  public void testBatchWithGet_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testBatchWithGet();
  }

  @Test
  public void testBatchWithGet_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testBatchWithGet();
  }

  @Test
  public void testBatchWithGet_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    testBatchWithGet();
  }

  @Test
  public void testBatchWithGet_AFTER_LOAD_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_LOAD_DATA";
    testBatchWithGet();
  }

  private void testBatchWithGet() throws Exception {
    conf = HBaseConfiguration.create();

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(5)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Create multi-region table
    byte[][] splitKeys = new byte[][] {
      Bytes.toBytes("ccc"),
      Bytes.toBytes("eee"),
      Bytes.toBytes("ggg"),
      Bytes.toBytes("iii")
    };
    admin.createTable(TableDescriptorBuilder.newBuilder(TEST_TABLE)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(BYTES_FAMILY))
      .build(), splitKeys);

    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(TEST_TABLE)) {
      // load test data
      List<Put> puts = constructPutRequests();
      table.batch(puts, null);

      checkpoint("AFTER_LOAD_DATA");

      // create a list of gets and run it
      List<Row> gets = new ArrayList<>();
      for (byte[] k : KEYS) {
        Get get = new Get(k);
        get.addColumn(BYTES_FAMILY, QUALIFIER);
        gets.add(get);
      }
      Result[] multiRes = new Result[gets.size()];
      table.batch(gets, multiRes);

      // Same gets using individual call API
      List<Result> singleRes = new ArrayList<>();
      for (Row get : gets) {
        singleRes.add(table.get((Get) get));
      }
      // Compare results
      Assert.assertEquals(singleRes.size(), multiRes.length);
      for (int i = 0; i < singleRes.size(); i++) {
        Assert.assertTrue(singleRes.get(i).containsColumn(BYTES_FAMILY, QUALIFIER));
        Cell[] singleKvs = singleRes.get(i).rawCells();
        Cell[] multiKvs = multiRes[i].rawCells();
        for (int j = 0; j < singleKvs.length; j++) {
          Assert.assertEquals(singleKvs[j], multiKvs[j]);
          Assert.assertEquals(0,
            Bytes.compareTo(CellUtil.cloneValue(singleKvs[j]), CellUtil.cloneValue(multiKvs[j])));
        }
      }
    }

    // Cleanup
    admin.disableTable(TEST_TABLE);
    admin.deleteTable(TEST_TABLE);
  }

  @Test
  public void testBadFam_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testBadFam();
  }

  @Test
  public void testBadFam_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testBadFam();
  }

  @Test
  public void testBadFam_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    testBadFam();
  }

  private void testBadFam() throws Exception {
    conf = HBaseConfiguration.create();

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(3)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    admin.createTable(TableDescriptorBuilder.newBuilder(TEST_TABLE)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(BYTES_FAMILY))
      .build());

    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(TEST_TABLE)) {
      List<Row> actions = new ArrayList<>();
      Put p = new Put(Bytes.toBytes("row1"));
      p.addColumn(Bytes.toBytes("bad_family"), Bytes.toBytes("qual"), Bytes.toBytes("value"));
      actions.add(p);
      p = new Put(Bytes.toBytes("row2"));
      p.addColumn(BYTES_FAMILY, Bytes.toBytes("qual"), Bytes.toBytes("value"));
      actions.add(p);

      // row1 and row2 should be in the same region.

      Object[] r = new Object[actions.size()];
      try {
        table.batch(actions, r);
        fail();
      } catch (RetriesExhaustedWithDetailsException ex) {
        LOG.debug(ex.toString(), ex);
        // good!
        assertFalse(ex.mayHaveClusterIssues());
      }
      assertEquals(2, r.length);
      assertTrue(r[0] instanceof Throwable);
      assertTrue(r[1] instanceof Result);
    }

    // Cleanup
    admin.disableTable(TEST_TABLE);
    admin.deleteTable(TEST_TABLE);
  }
}
