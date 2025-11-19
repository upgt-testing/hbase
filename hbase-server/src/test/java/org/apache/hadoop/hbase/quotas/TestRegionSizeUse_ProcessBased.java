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
package org.apache.hadoop.hbase.quotas;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestRegionSizeUse}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * @see TestRegionSizeUse Original test using MiniHBaseCluster
 */
@Category(MediumTests.class)
public class TestRegionSizeUse_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestRegionSizeUse_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestRegionSizeUse_ProcessBased.class);
  private static final int SIZE_PER_VALUE = 256;
  private static final int NUM_SPLITS = 10;
  private static final String F1 = "f1";

  @Rule
  public TestName testName = new TestName();

  @Test
  public void testBasicRegionSizeReports_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testBasicRegionSizeReportsImpl();
  }

  @Test
  public void testBasicRegionSizeReports_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testBasicRegionSizeReportsImpl();
  }

  @Test
  public void testBasicRegionSizeReports_AFTER_WRITE_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE_DATA";
    testBasicRegionSizeReportsImpl();
  }

  @Test
  public void testBasicRegionSizeReports_AFTER_FLUSH() throws Exception {
    upgradeCheckpoint = "AFTER_FLUSH";
    testBasicRegionSizeReportsImpl();
  }

  private void testBasicRegionSizeReportsImpl() throws Exception {
    Configuration testConf = HBaseConfiguration.create(conf);
    SpaceQuotaHelperForTests.updateConfigForQuotas(testConf);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(testConf)
        .numRegionServers(2)
        .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final long bytesWritten = 5L * 1024L * 1024L; // 5MB
    final TableName tn = writeData(bytesWritten);
    LOG.debug("Data was written to HBase");

    checkpoint("AFTER_WRITE_DATA");

    // Push the data to disk.
    admin.flush(tn);
    LOG.debug("Data flushed to disk");

    checkpoint("AFTER_FLUSH");

    // Get the final region distribution
    final List<RegionInfo> regions = admin.getRegions(tn);

    // TRANSFORMATION NOTE: Removed internal MasterQuotaManager access.
    // Original code waited for quotaManager.snapshotRegionSizes() to have all region reports.
    // Replaced with direct client API polling: admin.getSpaceQuotaTableSizes().
    // The client API provides the same aggregated information visible to external clients.

    // Wait until we get the table size report from master via client API
    long totalRegionSize = 0L;
    int attempts = 0;
    while (totalRegionSize == 0L && attempts < 30) {
      Map<TableName, Long> tableSizes = admin.getSpaceQuotaTableSizes();
      Long tableSize = tableSizes.get(tn);
      if (tableSize != null) {
        totalRegionSize = tableSize;
      }
      if (totalRegionSize == 0L) {
        LOG.debug("Waiting for region size reports. Attempt " + (attempts + 1));
        Thread.sleep(1000);
      }
      attempts++;
    }

    LOG.debug("Observed table size by the HMaster: " + totalRegionSize);
    assertTrue("Expected region size report to exceed " + bytesWritten + ", but was "
      + totalRegionSize, bytesWritten < totalRegionSize);
  }

  /**
   * Writes at least {@code sizeInBytes} bytes of data to HBase and returns the TableName used.
   * @param sizeInBytes The amount of data to write in bytes.
   * @return The table the data was written to
   */
  private TableName writeData(long sizeInBytes) throws IOException {
    final TableName tn = TableName.valueOf(testName.getMethodName());

    // Delete the old table
    if (admin.tableExists(tn)) {
      admin.disableTable(tn);
      admin.deleteTable(tn);
    }

    // Create the table
    admin.createTable(TableDescriptorBuilder.newBuilder(tn)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(F1))
        .build(), Bytes.toBytes("1"), Bytes.toBytes("9"), NUM_SPLITS);

    try (Table table = connection.getTable(tn)) {
      List<Put> updates = new ArrayList<>();
      long bytesToWrite = sizeInBytes;
      long rowKeyId = 0L;
      final StringBuilder sb = new StringBuilder();
      while (bytesToWrite > 0L) {
        sb.setLength(0);
        sb.append(Long.toString(rowKeyId));
        // Use the reverse counter as the rowKey to get even spread across all regions
        Put p = new Put(Bytes.toBytes(sb.reverse().toString()));
        byte[] value = new byte[SIZE_PER_VALUE];
        Bytes.random(value);
        p.addColumn(Bytes.toBytes(F1), Bytes.toBytes("q1"), value);
        updates.add(p);

        // Batch 50K worth of updates
        if (updates.size() > 50) {
          table.put(updates);
          updates.clear();
        }

        // Just count the value size, ignore the size of rowkey + column
        bytesToWrite -= SIZE_PER_VALUE;
        rowKeyId++;
      }

      // Write the final batch
      if (!updates.isEmpty()) {
        table.put(updates);
      }

      return tn;
    }
  }
}
