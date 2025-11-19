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
package org.apache.hadoop.hbase.io.encoding;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.testclassification.IOTests;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Threads;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestChangingEncoding}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Tests changing data block encoding settings of a column family.
 *
 * @see TestChangingEncoding Original test using MiniHBaseCluster
 */
@Category({ IOTests.class, LargeTests.class })
public class TestChangingEncoding_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestChangingEncoding_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestChangingEncoding_ProcessBased.class);
  static final String CF = "EncodingTestCF";
  static final byte[] CF_BYTES = Bytes.toBytes(CF);

  private static final int NUM_ROWS_PER_BATCH = 100;
  private static final int NUM_COLS_PER_ROW = 20;

  private static final int TIMEOUT_MS = 600000;

  private HColumnDescriptor hcd;

  private TableName tableName;
  private static final List<DataBlockEncoding> ENCODINGS_TO_ITERATE = createEncodingsToIterate();

  private static final List<DataBlockEncoding> createEncodingsToIterate() {
    List<DataBlockEncoding> encodings = new ArrayList<>(Arrays.asList(DataBlockEncoding.values()));
    encodings.add(DataBlockEncoding.NONE);
    return Collections.unmodifiableList(encodings);
  }

  /** A zero-based index of the current batch of test data being written */
  private int numBatchesWritten;

  private void prepareTest(String testId) throws IOException {
    tableName = TableName.valueOf("test_table_" + testId);
    HTableDescriptor htd = new HTableDescriptor(tableName);
    hcd = new HColumnDescriptor(CF);
    htd.addFamily(hcd);
    admin.createTable(htd);
    numBatchesWritten = 0;
  }

  private static byte[] getRowKey(int batchId, int i) {
    return Bytes.toBytes("batch" + batchId + "_row" + i);
  }

  private static byte[] getQualifier(int j) {
    return Bytes.toBytes("col" + j);
  }

  private static byte[] getValue(int batchId, int i, int j) {
    return Bytes.toBytes("value_for_" + Bytes.toString(getRowKey(batchId, i)) + "_col" + j);
  }

  private void writeTestDataBatch(TableName tableName, int batchId) throws Exception {
    LOG.debug("Writing test data batch " + batchId);
    List<Put> puts = new ArrayList<>();
    for (int i = 0; i < NUM_ROWS_PER_BATCH; ++i) {
      Put put = new Put(getRowKey(batchId, i));
      for (int j = 0; j < NUM_COLS_PER_ROW; ++j) {
        put.addColumn(CF_BYTES, getQualifier(j), getValue(batchId, i, j));
      }
      put.setDurability(Durability.SKIP_WAL);
      puts.add(put);
    }
    try (Table table = connection.getTable(tableName)) {
      table.put(puts);
    }
  }

  private void verifyTestDataBatch(TableName tableName, int batchId) throws Exception {
    LOG.debug("Verifying test data batch " + batchId);
    Table table = connection.getTable(tableName);
    for (int i = 0; i < NUM_ROWS_PER_BATCH; ++i) {
      Get get = new Get(getRowKey(batchId, i));
      Result result = table.get(get);
      for (int j = 0; j < NUM_COLS_PER_ROW; ++j) {
        Cell kv = result.getColumnLatestCell(CF_BYTES, getQualifier(j));
        if (kv == null) {
          continue;
        }
        assertTrue(CellUtil.matchingValue(kv, getValue(batchId, i, j)));
      }
    }
    table.close();
  }

  private void writeSomeNewData() throws Exception {
    writeTestDataBatch(tableName, numBatchesWritten);
    ++numBatchesWritten;
  }

  private void verifyAllData() throws Exception {
    for (int i = 0; i < numBatchesWritten; ++i) {
      verifyTestDataBatch(tableName, i);
    }
  }

  private void setEncodingConf(DataBlockEncoding encoding, boolean onlineChange) throws Exception {
    LOG.debug("Setting CF encoding to " + encoding + " (ordinal=" + encoding.ordinal()
      + "), onlineChange=" + onlineChange);
    hcd.setDataBlockEncoding(encoding);
    if (!onlineChange) {
      admin.disableTable(tableName);
    }
    admin.modifyColumnFamily(tableName, hcd);
    if (!onlineChange) {
      admin.enableTable(tableName);
    }
    // Wait for regions out of transition
    // ProcessBased doesn't have waitUntilNoRegionsInTransition, use sleep
    Thread.sleep(1000);
  }

  private void compactAndWait() throws IOException, InterruptedException {
    LOG.debug("Compacting table " + tableName);
    admin.majorCompact(tableName);

    // Wait for compaction to complete
    // In ProcessBased we cannot access internal compaction queue
    // so we wait via client API
    Thread.sleep(5000);

    LOG.debug("Compaction wait completed, continuing");
  }

  @Test
  public void testChangingEncoding_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    conf = HBaseConfiguration.create();
    conf.setInt(HConstants.HREGION_MEMSTORE_FLUSH_SIZE, 1024 * 1024);
    conf.set(HConstants.HBASE_REGION_SPLIT_POLICY_KEY,
      "org.apache.hadoop.hbase.regionserver.DisabledRegionSplitPolicy");

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    prepareTest("ChangingEncoding");
    for (boolean onlineChange : new boolean[] { false, true }) {
      for (DataBlockEncoding encoding : ENCODINGS_TO_ITERATE) {
        setEncodingConf(encoding, onlineChange);
        writeSomeNewData();
        verifyAllData();
      }
    }

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testChangingEncoding_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    conf = HBaseConfiguration.create();
    conf.setInt(HConstants.HREGION_MEMSTORE_FLUSH_SIZE, 1024 * 1024);
    conf.set(HConstants.HBASE_REGION_SPLIT_POLICY_KEY,
      "org.apache.hadoop.hbase.regionserver.DisabledRegionSplitPolicy");

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    prepareTest("ChangingEncoding");
    for (boolean onlineChange : new boolean[] { false, true }) {
      for (DataBlockEncoding encoding : ENCODINGS_TO_ITERATE) {
        setEncodingConf(encoding, onlineChange);
        writeSomeNewData();
        verifyAllData();
      }
    }

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testChangingEncodingWithCompaction_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    conf = HBaseConfiguration.create();
    conf.setInt(HConstants.HREGION_MEMSTORE_FLUSH_SIZE, 1024 * 1024);
    conf.set(HConstants.HBASE_REGION_SPLIT_POLICY_KEY,
      "org.apache.hadoop.hbase.regionserver.DisabledRegionSplitPolicy");

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    prepareTest("ChangingEncodingWithCompaction");
    for (boolean onlineChange : new boolean[] { false, true }) {
      for (DataBlockEncoding encoding : ENCODINGS_TO_ITERATE) {
        setEncodingConf(encoding, onlineChange);
        writeSomeNewData();
        verifyAllData();
        compactAndWait();
        verifyAllData();
      }
    }

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testChangingEncodingWithCompaction_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    conf = HBaseConfiguration.create();
    conf.setInt(HConstants.HREGION_MEMSTORE_FLUSH_SIZE, 1024 * 1024);
    conf.set(HConstants.HBASE_REGION_SPLIT_POLICY_KEY,
      "org.apache.hadoop.hbase.regionserver.DisabledRegionSplitPolicy");

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    prepareTest("ChangingEncodingWithCompaction");
    for (boolean onlineChange : new boolean[] { false, true }) {
      for (DataBlockEncoding encoding : ENCODINGS_TO_ITERATE) {
        setEncodingConf(encoding, onlineChange);
        writeSomeNewData();
        verifyAllData();
        compactAndWait();
        verifyAllData();
      }
    }

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testCrazyRandomChanges_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    conf = HBaseConfiguration.create();
    conf.setInt(HConstants.HREGION_MEMSTORE_FLUSH_SIZE, 1024 * 1024);
    conf.set(HConstants.HBASE_REGION_SPLIT_POLICY_KEY,
      "org.apache.hadoop.hbase.regionserver.DisabledRegionSplitPolicy");

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    prepareTest("RandomChanges");
    Random rand = ThreadLocalRandom.current();
    for (int i = 0; i < 10; ++i) {
      int encodingOrdinal = rand.nextInt(DataBlockEncoding.values().length);
      DataBlockEncoding encoding = DataBlockEncoding.values()[encodingOrdinal];
      setEncodingConf(encoding, rand.nextBoolean());
      writeSomeNewData();
      verifyAllData();
    }

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testCrazyRandomChanges_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    conf = HBaseConfiguration.create();
    conf.setInt(HConstants.HREGION_MEMSTORE_FLUSH_SIZE, 1024 * 1024);
    conf.set(HConstants.HBASE_REGION_SPLIT_POLICY_KEY,
      "org.apache.hadoop.hbase.regionserver.DisabledRegionSplitPolicy");

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    prepareTest("RandomChanges");
    Random rand = ThreadLocalRandom.current();
    for (int i = 0; i < 10; ++i) {
      int encodingOrdinal = rand.nextInt(DataBlockEncoding.values().length);
      DataBlockEncoding encoding = DataBlockEncoding.values()[encodingOrdinal];
      setEncodingConf(encoding, rand.nextBoolean());
      writeSomeNewData();
      verifyAllData();
    }

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }
}
