/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0 (the
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

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.ClusterMetrics.Option;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.io.compress.Compression;
import org.apache.hadoop.hbase.io.hfile.CacheConfig;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.IOTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.MultiThreadedAction;
import org.apache.hadoop.hbase.util.MultiThreadedReader;
import org.apache.hadoop.hbase.util.MultiThreadedWriter;
import org.apache.hadoop.hbase.util.Threads;
import org.apache.hadoop.hbase.util.test.LoadTestDataGenerator;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestLoadAndSwitchEncodeOnDisk}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Tests loading data and switching encoding on disk with compaction.
 *
 * @see TestLoadAndSwitchEncodeOnDisk Original test using MiniHBaseCluster
 */
@Category({ IOTests.class, MediumTests.class })
public class TestLoadAndSwitchEncodeOnDisk_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestLoadAndSwitchEncodeOnDisk_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestLoadAndSwitchEncodeOnDisk_ProcessBased.class);

  protected static final TableName TABLE = TableName.valueOf("load_test_tbl");
  protected static final byte[] CF = Bytes.toBytes("load_test_cf");
  protected static final int NUM_THREADS = 8;
  protected static final int NUM_RS = 2;
  protected static final int TIMEOUT_MS = 180000;

  private static final boolean USE_MULTI_PUT = true;
  private static final int NUM_KEYS = 3000;

  protected MultiThreadedWriter writerThreads;
  protected MultiThreadedReader readerThreads;
  protected Compression.Algorithm compression = Compression.Algorithm.NONE;
  protected DataBlockEncoding dataBlockEncoding = DataBlockEncoding.PREFIX;

  private ColumnFamilyDescriptor getColumnDesc(Admin admin) throws IOException {
    return admin.getDescriptor(TABLE).getColumnFamily(CF);
  }

  private void assertAllOnLine(final Table t) throws IOException {
    List<HRegionLocation> regions;
    try (RegionLocator rl = connection.getRegionLocator(t.getName())) {
      regions = rl.getAllRegionLocations();
    }
    for (HRegionLocation e : regions) {
      byte[] startkey = e.getRegionInfo().getStartKey();
      Scan s = new Scan(startkey);
      ResultScanner scanner = t.getScanner(s);
      Result r = scanner.next();
      org.junit.Assert.assertTrue(r != null && r.size() > 0);
      scanner.close();
    }
  }

  private void prepareForLoadTest() throws IOException, InterruptedException {
    LOG.info(
      "Starting load test: dataBlockEncoding=" + dataBlockEncoding + ", isMultiPut=" + USE_MULTI_PUT);

    // Wait for enough region servers
    while (
      admin.getClusterMetrics(EnumSet.of(Option.LIVE_SERVERS)).getLiveServerMetrics().size()
          < NUM_RS
    ) {
      LOG.info("Sleeping until " + NUM_RS + " RSs are online");
      Threads.sleepWithoutInterrupt(1000);
    }

    HTableDescriptor htd = new HTableDescriptor(TABLE);
    HColumnDescriptor hcd = new HColumnDescriptor(CF).setCompressionType(compression)
      .setDataBlockEncoding(dataBlockEncoding);
    htd.addFamily(hcd);

    // Create pre-split table with multiple regions
    byte[][] splitKeys = new byte[NUM_RS * 2][];
    for (int i = 0; i < NUM_RS * 2; i++) {
      splitKeys[i] = Bytes.toBytes(String.format("%08x", i * (Integer.MAX_VALUE / (NUM_RS * 2))));
    }
    admin.createTable(htd, splitKeys);
    // Wait for regions to be assigned
    Thread.sleep(2000);

    LoadTestDataGenerator dataGen = new MultiThreadedAction.DefaultDataGenerator(CF);
    writerThreads = new MultiThreadedWriter(dataGen, conf, TABLE);
    if (USE_MULTI_PUT) {
      writerThreads.setMultiPut(true);
    }
    readerThreads = new MultiThreadedReader(dataGen, conf, TABLE, 100);
  }

  private void runLoadTestOnExistingTable() throws IOException {
    writerThreads.start(0, NUM_KEYS, NUM_THREADS);
    writerThreads.waitForFinish();
    org.junit.Assert.assertEquals(0, writerThreads.getNumWriteFailures());

    readerThreads.start(0, NUM_KEYS, NUM_THREADS);
    readerThreads.waitForFinish();
    org.junit.Assert.assertEquals(0, readerThreads.getNumReadFailures());
    org.junit.Assert.assertEquals(0, readerThreads.getNumReadErrors());
    org.junit.Assert.assertEquals(NUM_KEYS, readerThreads.getNumKeysVerified());
  }

  @Test
  public void loadTest_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    conf.setInt(HConstants.HREGION_MEMSTORE_FLUSH_SIZE, 1024 * 1024);
    conf.setFloat(HConstants.LOAD_BALANCER_SLOP_KEY, 10.0f);
    conf.setBoolean(CacheConfig.CACHE_BLOCKS_ON_WRITE_KEY, true);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(NUM_RS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    compression = Compression.Algorithm.GZ;
    prepareForLoadTest();
    runLoadTestOnExistingTable();

    ColumnFamilyDescriptor hcd = getColumnDesc(admin);
    System.err.println("\nDisabling encode-on-disk. Old column descriptor: " + hcd + "\n");
    Table t = connection.getTable(TABLE);
    assertAllOnLine(t);

    admin.disableTable(TABLE);
    admin.modifyColumnFamily(TABLE, hcd);

    System.err.println("\nRe-enabling table\n");
    admin.enableTable(TABLE);

    System.err.println("\nNew column descriptor: " + getColumnDesc(admin) + "\n");

    assertAllOnLine(t);

    System.err.println("\nCompacting the table\n");
    admin.majorCompact(TABLE);
    // Wait for compaction - ProcessBased cannot access internal compaction queue
    Thread.sleep(5000);

    System.err.println("\nDone with the test\n");

    admin.disableTable(TABLE);
    admin.deleteTable(TABLE);
    t.close();
  }

  @Test
  public void loadTest_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    conf.setInt(HConstants.HREGION_MEMSTORE_FLUSH_SIZE, 1024 * 1024);
    conf.setFloat(HConstants.LOAD_BALANCER_SLOP_KEY, 10.0f);
    conf.setBoolean(CacheConfig.CACHE_BLOCKS_ON_WRITE_KEY, true);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(NUM_RS)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    compression = Compression.Algorithm.GZ;
    prepareForLoadTest();
    runLoadTestOnExistingTable();

    ColumnFamilyDescriptor hcd = getColumnDesc(admin);
    System.err.println("\nDisabling encode-on-disk. Old column descriptor: " + hcd + "\n");
    Table t = connection.getTable(TABLE);
    assertAllOnLine(t);

    admin.disableTable(TABLE);
    admin.modifyColumnFamily(TABLE, hcd);

    System.err.println("\nRe-enabling table\n");
    admin.enableTable(TABLE);

    System.err.println("\nNew column descriptor: " + getColumnDesc(admin) + "\n");

    assertAllOnLine(t);

    System.err.println("\nCompacting the table\n");
    admin.majorCompact(TABLE);
    // Wait for compaction - ProcessBased cannot access internal compaction queue
    Thread.sleep(5000);

    System.err.println("\nDone with the test\n");

    admin.disableTable(TABLE);
    admin.deleteTable(TABLE);
    t.close();
  }
}
