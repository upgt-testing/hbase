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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestLeaseRenewal}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * @see TestLeaseRenewal Original test using MiniHBaseCluster
 */
@Category(LargeTests.class)
public class TestLeaseRenewal_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestLeaseRenewal_ProcessBased.class);

  final Logger LOG = LoggerFactory.getLogger(getClass());
  private static byte[] FAMILY = Bytes.toBytes("testFamily");
  private static final byte[] ANOTHERROW = Bytes.toBytes("anotherrow");
  private final static byte[] COL_QUAL = Bytes.toBytes("f1");
  private final static byte[] VAL_BYTES = Bytes.toBytes("v1");
  private final static byte[] ROW_BYTES = Bytes.toBytes("r1");
  private final static int leaseTimeout =
    HConstants.DEFAULT_HBASE_CLIENT_SCANNER_TIMEOUT_PERIOD / 4;

  @Test
  public void testLeaseRenewal_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testLeaseRenewal();
  }

  @Test
  public void testLeaseRenewal_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testLeaseRenewal();
  }

  @Test
  public void testLeaseRenewal_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    testLeaseRenewal();
  }

  @Test
  public void testLeaseRenewal_AFTER_WRITE_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE_DATA";
    testLeaseRenewal();
  }

  private void testLeaseRenewal() throws Exception {
    conf = HBaseConfiguration.create();
    conf.setInt(HConstants.HBASE_CLIENT_SCANNER_TIMEOUT_PERIOD, leaseTimeout);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testLeaseRenewal");
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .build());

    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(tableName)) {
      Put p = new Put(ROW_BYTES);
      p.addColumn(FAMILY, COL_QUAL, VAL_BYTES);
      table.put(p);
      p = new Put(ANOTHERROW);
      p.addColumn(FAMILY, COL_QUAL, VAL_BYTES);
      table.put(p);
    }

    checkpoint("AFTER_WRITE_DATA");

    try (Table table = connection.getTable(tableName)) {
      Scan s = new Scan();
      s.setCaching(1);
      ResultScanner rs = table.getScanner(s);
      // we haven't open the scanner yet so nothing happens
      assertFalse(rs.renewLease());
      assertTrue(Arrays.equals(rs.next().getRow(), ANOTHERROW));
      // renew the lease a few times, long enough to be sure
      // the lease would have expired otherwise
      Thread.sleep(leaseTimeout / 2);
      assertTrue(rs.renewLease());
      Thread.sleep(leaseTimeout / 2);
      assertTrue(rs.renewLease());
      Thread.sleep(leaseTimeout / 2);
      assertTrue(rs.renewLease());
      // make sure we haven't advanced the scanner
      assertTrue(Arrays.equals(rs.next().getRow(), ROW_BYTES));
      // renewLease should return false now as we have read all the data already
      assertFalse(rs.renewLease());
      // make sure scanner is exhausted now
      assertNull(rs.next());
      // renewLease should return false now
      assertFalse(rs.renewLease());
      rs.close();

      // TRANSFORMATION NOTE: Internal RPC server metrics verification removed.
      // The original test verified OutOfOrderScannerNextException count via
      // getMiniHBaseCluster().getRegionServer(0).getRpcServer().getMetrics().getMetricsSource().
      // This requires direct RegionServer and RpcServer access which are not available
      // in ProcessBasedMiniHBaseCluster. The core lease renewal functionality is fully
      // tested by the client-side assertions above.
      //
      // Original code:
      // MetricsHBaseServerSource serverSource = TEST_UTIL.getMiniHBaseCluster().getRegionServer(0)
      //   .getRpcServer().getMetrics().getMetricsSource();
      // HELPER.assertCounter("exceptions.OutOfOrderScannerNextException", 0, serverSource);
    }

    // Cleanup
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }
}
