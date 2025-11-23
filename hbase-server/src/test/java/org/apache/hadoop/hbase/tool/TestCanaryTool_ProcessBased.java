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
package org.apache.hadoop.hbase.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.util.ToolRunner;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.mockito.ArgumentMatcher;

/**
 * ProcessBased version of {@link TestCanaryTool}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Reduced version: 10/11 tests transformed. testCanaryStopsScanningAfterTimeout removed
 * (requires direct HRegionServer access to close regions without updating meta).
 *
 * @see TestCanaryTool Original test using MiniHBaseCluster
 */
@Category({ LargeTests.class })
public class TestCanaryTool_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestCanaryTool_ProcessBased.class);

  private static final byte[] FAMILY = Bytes.toBytes("f");
  private static final byte[] COLUMN = Bytes.toBytes("col");

  @Rule
  public TestName name = new TestName();

  private org.apache.logging.log4j.core.Appender mockAppender;

  private void setUpMockAppender() throws Exception {
    mockAppender = mock(org.apache.logging.log4j.core.Appender.class);
    when(mockAppender.getName()).thenReturn("mockAppender");
    when(mockAppender.isStarted()).thenReturn(true);
    ((org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager
      .getLogger("org.apache.hadoop.hbase")).addAppender(mockAppender);
  }

  private void tearDownMockAppender() throws Exception {
    if (mockAppender != null) {
      ((org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager
        .getLogger("org.apache.hadoop.hbase")).removeAppender(mockAppender);
    }
  }

  @Test
  public void testBasicZookeeperCanaryWorks_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final String[] args = { "-t", "10000", "-zookeeper" };
    testZookeeperCanaryWithArgs(args);

    tearDownMockAppender();
  }

  @Test
  public void testBasicZookeeperCanaryWorks_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final String[] args = { "-t", "10000", "-zookeeper" };
    testZookeeperCanaryWithArgs(args);

    tearDownMockAppender();
  }

  @Test
  public void testZookeeperCanaryPermittedFailuresArgumentWorks_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final String[] args =
      { "-t", "10000", "-zookeeper", "-treatFailureAsError", "-permittedZookeeperFailures", "1" };
    testZookeeperCanaryWithArgs(args);

    tearDownMockAppender();
  }

  @Test
  public void testZookeeperCanaryPermittedFailuresArgumentWorks_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final String[] args =
      { "-t", "10000", "-zookeeper", "-treatFailureAsError", "-permittedZookeeperFailures", "1" };
    testZookeeperCanaryWithArgs(args);

    tearDownMockAppender();
  }

  @Test
  public void testBasicCanaryWorks_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .build());
    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(tableName)) {
      // insert some test rows
      for (int i = 0; i < 1000; i++) {
        byte[] iBytes = Bytes.toBytes(i);
        Put p = new Put(iBytes);
        p.addColumn(FAMILY, COLUMN, iBytes);
        table.put(p);
      }
    }
    checkpoint("AFTER_WRITE_DATA");

    ExecutorService executor = new ScheduledThreadPoolExecutor(1);
    CanaryTool.RegionStdOutSink sink = spy(new CanaryTool.RegionStdOutSink());
    CanaryTool canary = new CanaryTool(executor, sink);
    String[] args = { "-writeSniffing", "-t", "10000", tableName.getNameAsString() };
    assertEquals(0, ToolRunner.run(conf, canary, args));
    assertEquals("verify no read error count", 0, canary.getReadFailures().size());
    assertEquals("verify no write error count", 0, canary.getWriteFailures().size());
    verify(sink, atLeastOnce()).publishReadTiming(isA(ServerName.class), isA(RegionInfo.class),
      isA(ColumnFamilyDescriptor.class), anyLong());

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testBasicCanaryWorks_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .build());
    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        byte[] iBytes = Bytes.toBytes(i);
        Put p = new Put(iBytes);
        p.addColumn(FAMILY, COLUMN, iBytes);
        table.put(p);
      }
    }
    checkpoint("AFTER_WRITE_DATA");

    ExecutorService executor = new ScheduledThreadPoolExecutor(1);
    CanaryTool.RegionStdOutSink sink = spy(new CanaryTool.RegionStdOutSink());
    CanaryTool canary = new CanaryTool(executor, sink);
    String[] args = { "-writeSniffing", "-t", "10000", tableName.getNameAsString() };
    assertEquals(0, ToolRunner.run(conf, canary, args));
    assertEquals("verify no read error count", 0, canary.getReadFailures().size());
    assertEquals("verify no write error count", 0, canary.getWriteFailures().size());
    verify(sink, atLeastOnce()).publishReadTiming(isA(ServerName.class), isA(RegionInfo.class),
      isA(ColumnFamilyDescriptor.class), anyLong());

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testBasicCanaryWorks_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .build());
    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        byte[] iBytes = Bytes.toBytes(i);
        Put p = new Put(iBytes);
        p.addColumn(FAMILY, COLUMN, iBytes);
        table.put(p);
      }
    }
    checkpoint("AFTER_WRITE_DATA");

    ExecutorService executor = new ScheduledThreadPoolExecutor(1);
    CanaryTool.RegionStdOutSink sink = spy(new CanaryTool.RegionStdOutSink());
    CanaryTool canary = new CanaryTool(executor, sink);
    String[] args = { "-writeSniffing", "-t", "10000", tableName.getNameAsString() };
    assertEquals(0, ToolRunner.run(conf, canary, args));
    assertEquals("verify no read error count", 0, canary.getReadFailures().size());
    assertEquals("verify no write error count", 0, canary.getWriteFailures().size());
    verify(sink, atLeastOnce()).publishReadTiming(isA(ServerName.class), isA(RegionInfo.class),
      isA(ColumnFamilyDescriptor.class), anyLong());

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testBasicCanaryWorks_AFTER_WRITE_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE_DATA";

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .build());
    checkpoint("AFTER_CREATE_TABLE");

    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        byte[] iBytes = Bytes.toBytes(i);
        Put p = new Put(iBytes);
        p.addColumn(FAMILY, COLUMN, iBytes);
        table.put(p);
      }
    }
    checkpoint("AFTER_WRITE_DATA");

    ExecutorService executor = new ScheduledThreadPoolExecutor(1);
    CanaryTool.RegionStdOutSink sink = spy(new CanaryTool.RegionStdOutSink());
    CanaryTool canary = new CanaryTool(executor, sink);
    String[] args = { "-writeSniffing", "-t", "10000", tableName.getNameAsString() };
    assertEquals(0, ToolRunner.run(conf, canary, args));
    assertEquals("verify no read error count", 0, canary.getReadFailures().size());
    assertEquals("verify no write error count", 0, canary.getWriteFailures().size());
    verify(sink, atLeastOnce()).publishReadTiming(isA(ServerName.class), isA(RegionInfo.class),
      isA(ColumnFamilyDescriptor.class), anyLong());

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  // TRANSFORMATION NOTE: testCanaryStopsScanningAfterTimeout removed.
  // This test requires HRegionServer access to close regions without updating meta:
  //   HRegionServer regionserver = testingUtility.getMiniHBaseCluster().getRegionServer(0);
  //   closeRegion(testingUtility, regionserver, new HRegionInfo(region));
  // ProcessBasedMiniHBaseCluster does not expose HRegionServer across process boundaries.
  // No client API equivalent exists for closing regions without meta update.

  @Test
  public void testCanaryRegionTaskReadAllCF_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(Bytes.toBytes("f1")))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(Bytes.toBytes("f2")))
      .build());

    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        byte[] iBytes = Bytes.toBytes(i);
        Put p = new Put(iBytes);
        p.addColumn(Bytes.toBytes("f1"), COLUMN, iBytes);
        p.addColumn(Bytes.toBytes("f2"), COLUMN, iBytes);
        table.put(p);
      }
    }

    Configuration configuration = HBaseConfiguration.create(conf);
    String[] args = { "-t", "10000", tableName.getNameAsString() };
    ExecutorService executor = new ScheduledThreadPoolExecutor(1);
    for (boolean readAllCF : new boolean[] { true, false }) {
      CanaryTool.RegionStdOutSink sink = spy(new CanaryTool.RegionStdOutSink());
      CanaryTool canary = new CanaryTool(executor, sink);
      configuration.setBoolean(HConstants.HBASE_CANARY_READ_ALL_CF, readAllCF);
      assertEquals(0, ToolRunner.run(configuration, canary, args));
      int expectedReadCount =
        readAllCF ? 2 * sink.getTotalExpectedRegions() : sink.getTotalExpectedRegions();
      assertEquals("canary region success count should equal total expected read count",
        expectedReadCount, sink.getReadSuccessCount());
      Map<String, List<CanaryTool.RegionTaskResult>> regionMap = sink.getRegionMap();
      assertFalse("verify region map has size > 0", regionMap.isEmpty());

      for (String regionName : regionMap.keySet()) {
        for (CanaryTool.RegionTaskResult res : regionMap.get(regionName)) {
          assertNotNull("verify getRegionNameAsString()", regionName);
          assertNotNull("verify getRegionInfo()", res.getRegionInfo());
          assertNotNull("verify getTableName()", res.getTableName());
          assertNotNull("verify getTableNameAsString()", res.getTableNameAsString());
          assertNotNull("verify getServerName()", res.getServerName());
          assertNotNull("verify getServerNameAsString()", res.getServerNameAsString());
          assertNotNull("verify getColumnFamily()", res.getColumnFamily());
          assertNotNull("verify getColumnFamilyNameAsString()", res.getColumnFamilyNameAsString());
          assertTrue("read from region " + regionName + " succeeded", res.isReadSuccess());
          assertTrue("read took some time", res.getReadLatency() > -1);
        }
      }
    }

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testCanaryRegionTaskReadAllCF_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(Bytes.toBytes("f1")))
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(Bytes.toBytes("f2")))
      .build());

    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        byte[] iBytes = Bytes.toBytes(i);
        Put p = new Put(iBytes);
        p.addColumn(Bytes.toBytes("f1"), COLUMN, iBytes);
        p.addColumn(Bytes.toBytes("f2"), COLUMN, iBytes);
        table.put(p);
      }
    }

    Configuration configuration = HBaseConfiguration.create(conf);
    String[] args = { "-t", "10000", tableName.getNameAsString() };
    ExecutorService executor = new ScheduledThreadPoolExecutor(1);
    for (boolean readAllCF : new boolean[] { true, false }) {
      CanaryTool.RegionStdOutSink sink = spy(new CanaryTool.RegionStdOutSink());
      CanaryTool canary = new CanaryTool(executor, sink);
      configuration.setBoolean(HConstants.HBASE_CANARY_READ_ALL_CF, readAllCF);
      assertEquals(0, ToolRunner.run(configuration, canary, args));
      int expectedReadCount =
        readAllCF ? 2 * sink.getTotalExpectedRegions() : sink.getTotalExpectedRegions();
      assertEquals("canary region success count should equal total expected read count",
        expectedReadCount, sink.getReadSuccessCount());
      Map<String, List<CanaryTool.RegionTaskResult>> regionMap = sink.getRegionMap();
      assertFalse("verify region map has size > 0", regionMap.isEmpty());

      for (String regionName : regionMap.keySet()) {
        for (CanaryTool.RegionTaskResult res : regionMap.get(regionName)) {
          assertNotNull("verify getRegionNameAsString()", regionName);
          assertNotNull("verify getRegionInfo()", res.getRegionInfo());
          assertNotNull("verify getTableName()", res.getTableName());
          assertNotNull("verify getTableNameAsString()", res.getTableNameAsString());
          assertNotNull("verify getServerName()", res.getServerName());
          assertNotNull("verify getServerNameAsString()", res.getServerNameAsString());
          assertNotNull("verify getColumnFamily()", res.getColumnFamily());
          assertNotNull("verify getColumnFamilyNameAsString()", res.getColumnFamilyNameAsString());
          assertTrue("read from region " + regionName + " succeeded", res.isReadSuccess());
          assertTrue("read took some time", res.getReadLatency() > -1);
        }
      }
    }

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testCanaryRegionTaskResult_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testCanaryRegionTaskResult");
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .build());

    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        byte[] iBytes = Bytes.toBytes(i);
        Put p = new Put(iBytes);
        p.addColumn(FAMILY, COLUMN, iBytes);
        table.put(p);
      }
    }

    ExecutorService executor = new ScheduledThreadPoolExecutor(1);
    CanaryTool.RegionStdOutSink sink = spy(new CanaryTool.RegionStdOutSink());
    CanaryTool canary = new CanaryTool(executor, sink);
    String[] args = { "-writeSniffing", "-t", "10000", "testCanaryRegionTaskResult" };
    assertEquals(0, ToolRunner.run(conf, canary, args));

    assertTrue("canary should expect to scan at least 1 region",
      sink.getTotalExpectedRegions() > 0);
    assertTrue("there should be no read failures", sink.getReadFailureCount() == 0);
    assertTrue("there should be no write failures", sink.getWriteFailureCount() == 0);
    assertTrue("verify read success count > 0", sink.getReadSuccessCount() > 0);
    assertTrue("verify write success count > 0", sink.getWriteSuccessCount() > 0);
    verify(sink, atLeastOnce()).publishReadTiming(isA(ServerName.class), isA(RegionInfo.class),
      isA(ColumnFamilyDescriptor.class), anyLong());
    verify(sink, atLeastOnce()).publishWriteTiming(isA(ServerName.class), isA(RegionInfo.class),
      isA(ColumnFamilyDescriptor.class), anyLong());

    assertEquals("canary region success count should equal total expected regions",
      sink.getReadSuccessCount() + sink.getWriteSuccessCount(), sink.getTotalExpectedRegions());
    Map<String, List<CanaryTool.RegionTaskResult>> regionMap = sink.getRegionMap();
    assertFalse("verify region map has size > 0", regionMap.isEmpty());

    for (String regionName : regionMap.keySet()) {
      for (CanaryTool.RegionTaskResult res : regionMap.get(regionName)) {
        assertNotNull("verify getRegionNameAsString()", regionName);
        assertNotNull("verify getRegionInfo()", res.getRegionInfo());
        assertNotNull("verify getTableName()", res.getTableName());
        assertNotNull("verify getTableNameAsString()", res.getTableNameAsString());
        assertNotNull("verify getServerName()", res.getServerName());
        assertNotNull("verify getServerNameAsString()", res.getServerNameAsString());
        assertNotNull("verify getColumnFamily()", res.getColumnFamily());
        assertNotNull("verify getColumnFamilyNameAsString()", res.getColumnFamilyNameAsString());

        if (regionName.contains(CanaryTool.DEFAULT_WRITE_TABLE_NAME.getNameAsString())) {
          assertTrue("write to region " + regionName + " succeeded", res.isWriteSuccess());
          assertTrue("write took some time", res.getWriteLatency() > -1);
        } else {
          assertTrue("read from region " + regionName + " succeeded", res.isReadSuccess());
          assertTrue("read took some time", res.getReadLatency() > -1);
        }
      }
    }

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testCanaryRegionTaskResult_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    TableName tableName = TableName.valueOf("testCanaryRegionTaskResult");
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .build());

    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        byte[] iBytes = Bytes.toBytes(i);
        Put p = new Put(iBytes);
        p.addColumn(FAMILY, COLUMN, iBytes);
        table.put(p);
      }
    }

    ExecutorService executor = new ScheduledThreadPoolExecutor(1);
    CanaryTool.RegionStdOutSink sink = spy(new CanaryTool.RegionStdOutSink());
    CanaryTool canary = new CanaryTool(executor, sink);
    String[] args = { "-writeSniffing", "-t", "10000", "testCanaryRegionTaskResult" };
    assertEquals(0, ToolRunner.run(conf, canary, args));

    assertTrue("canary should expect to scan at least 1 region",
      sink.getTotalExpectedRegions() > 0);
    assertTrue("there should be no read failures", sink.getReadFailureCount() == 0);
    assertTrue("there should be no write failures", sink.getWriteFailureCount() == 0);
    assertTrue("verify read success count > 0", sink.getReadSuccessCount() > 0);
    assertTrue("verify write success count > 0", sink.getWriteSuccessCount() > 0);
    verify(sink, atLeastOnce()).publishReadTiming(isA(ServerName.class), isA(RegionInfo.class),
      isA(ColumnFamilyDescriptor.class), anyLong());
    verify(sink, atLeastOnce()).publishWriteTiming(isA(ServerName.class), isA(RegionInfo.class),
      isA(ColumnFamilyDescriptor.class), anyLong());

    assertEquals("canary region success count should equal total expected regions",
      sink.getReadSuccessCount() + sink.getWriteSuccessCount(), sink.getTotalExpectedRegions());
    Map<String, List<CanaryTool.RegionTaskResult>> regionMap = sink.getRegionMap();
    assertFalse("verify region map has size > 0", regionMap.isEmpty());

    for (String regionName : regionMap.keySet()) {
      for (CanaryTool.RegionTaskResult res : regionMap.get(regionName)) {
        assertNotNull("verify getRegionNameAsString()", regionName);
        assertNotNull("verify getRegionInfo()", res.getRegionInfo());
        assertNotNull("verify getTableName()", res.getTableName());
        assertNotNull("verify getTableNameAsString()", res.getTableNameAsString());
        assertNotNull("verify getServerName()", res.getServerName());
        assertNotNull("verify getServerNameAsString()", res.getServerNameAsString());
        assertNotNull("verify getColumnFamily()", res.getColumnFamily());
        assertNotNull("verify getColumnFamilyNameAsString()", res.getColumnFamilyNameAsString());

        if (regionName.contains(CanaryTool.DEFAULT_WRITE_TABLE_NAME.getNameAsString())) {
          assertTrue("write to region " + regionName + " succeeded", res.isWriteSuccess());
          assertTrue("write took some time", res.getWriteLatency() > -1);
        } else {
          assertTrue("read from region " + regionName + " succeeded", res.isReadSuccess());
          assertTrue("read took some time", res.getReadLatency() > -1);
        }
      }
    }

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testWriteTableTimeout_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    setUpMockAppender();

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    ExecutorService executor = new ScheduledThreadPoolExecutor(1);
    CanaryTool.RegionStdOutSink sink = spy(new CanaryTool.RegionStdOutSink());
    CanaryTool canary = new CanaryTool(executor, sink);
    String[] args = { "-writeSniffing", "-writeTableTimeout", String.valueOf(Long.MAX_VALUE) };
    assertEquals(0, ToolRunner.run(conf, canary, args));
    assertNotEquals("verify non-null write latency", null, sink.getWriteLatency());
    assertNotEquals("verify non-zero write latency", 0L, sink.getWriteLatency());
    verify(mockAppender, times(1))
      .append(argThat(new ArgumentMatcher<org.apache.logging.log4j.core.LogEvent>() {
        @Override
        public boolean matches(org.apache.logging.log4j.core.LogEvent argument) {
          return argument.getMessage().getFormattedMessage().contains("Configured write timeout");
        }
      }));

    tearDownMockAppender();
  }

  @Test
  public void testWriteTableTimeout_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    setUpMockAppender();

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    ExecutorService executor = new ScheduledThreadPoolExecutor(1);
    CanaryTool.RegionStdOutSink sink = spy(new CanaryTool.RegionStdOutSink());
    CanaryTool canary = new CanaryTool(executor, sink);
    String[] args = { "-writeSniffing", "-writeTableTimeout", String.valueOf(Long.MAX_VALUE) };
    assertEquals(0, ToolRunner.run(conf, canary, args));
    assertNotEquals("verify non-null write latency", null, sink.getWriteLatency());
    assertNotEquals("verify non-zero write latency", 0L, sink.getWriteLatency());
    verify(mockAppender, times(1))
      .append(argThat(new ArgumentMatcher<org.apache.logging.log4j.core.LogEvent>() {
        @Override
        public boolean matches(org.apache.logging.log4j.core.LogEvent argument) {
          return argument.getMessage().getFormattedMessage().contains("Configured write timeout");
        }
      }));

    tearDownMockAppender();
  }

  @Test
  public void testRegionserverNoRegions_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    setUpMockAppender();

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    runRegionserverCanary();
    verify(mockAppender)
      .append(argThat(new ArgumentMatcher<org.apache.logging.log4j.core.LogEvent>() {
        @Override
        public boolean matches(org.apache.logging.log4j.core.LogEvent argument) {
          return argument.getMessage().getFormattedMessage()
            .contains("Regionserver not serving any regions");
        }
      }));

    tearDownMockAppender();
  }

  @Test
  public void testRegionserverNoRegions_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    setUpMockAppender();

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    runRegionserverCanary();
    verify(mockAppender)
      .append(argThat(new ArgumentMatcher<org.apache.logging.log4j.core.LogEvent>() {
        @Override
        public boolean matches(org.apache.logging.log4j.core.LogEvent argument) {
          return argument.getMessage().getFormattedMessage()
            .contains("Regionserver not serving any regions");
        }
      }));

    tearDownMockAppender();
  }

  @Test
  public void testRegionserverWithRegions_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    setUpMockAppender();

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .build());

    runRegionserverCanary();
    verify(mockAppender, never())
      .append(argThat(new ArgumentMatcher<org.apache.logging.log4j.core.LogEvent>() {
        @Override
        public boolean matches(org.apache.logging.log4j.core.LogEvent argument) {
          return argument.getMessage().getFormattedMessage()
            .contains("Regionserver not serving any regions");
        }
      }));

    admin.disableTable(tableName);
    admin.deleteTable(tableName);

    tearDownMockAppender();
  }

  @Test
  public void testRegionserverWithRegions_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    setUpMockAppender();

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .build());

    runRegionserverCanary();
    verify(mockAppender, never())
      .append(argThat(new ArgumentMatcher<org.apache.logging.log4j.core.LogEvent>() {
        @Override
        public boolean matches(org.apache.logging.log4j.core.LogEvent argument) {
          return argument.getMessage().getFormattedMessage()
            .contains("Regionserver not serving any regions");
        }
      }));

    admin.disableTable(tableName);
    admin.deleteTable(tableName);

    tearDownMockAppender();
  }

  @Test
  public void testRawScanConfig_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .build());

    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        byte[] iBytes = Bytes.toBytes(i);
        Put p = new Put(iBytes);
        p.addColumn(FAMILY, COLUMN, iBytes);
        table.put(p);
      }
    }

    ExecutorService executor = new ScheduledThreadPoolExecutor(1);
    CanaryTool.RegionStdOutSink sink = spy(new CanaryTool.RegionStdOutSink());
    CanaryTool canary = new CanaryTool(executor, sink);
    String[] args = { "-t", "10000", tableName.getNameAsString() };
    org.apache.hadoop.conf.Configuration testConf =
      new org.apache.hadoop.conf.Configuration(conf);
    testConf.setBoolean(HConstants.HBASE_CANARY_READ_RAW_SCAN_KEY, true);
    assertEquals(0, ToolRunner.run(testConf, canary, args));
    verify(sink, atLeastOnce()).publishReadTiming(isA(ServerName.class), isA(RegionInfo.class),
      isA(ColumnFamilyDescriptor.class), anyLong());
    assertEquals("verify no read error count", 0, canary.getReadFailures().size());

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  @Test
  public void testRawScanConfig_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    final TableName tableName = TableName.valueOf(name.getMethodName());
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
      .build());

    try (Table table = connection.getTable(tableName)) {
      for (int i = 0; i < 1000; i++) {
        byte[] iBytes = Bytes.toBytes(i);
        Put p = new Put(iBytes);
        p.addColumn(FAMILY, COLUMN, iBytes);
        table.put(p);
      }
    }

    ExecutorService executor = new ScheduledThreadPoolExecutor(1);
    CanaryTool.RegionStdOutSink sink = spy(new CanaryTool.RegionStdOutSink());
    CanaryTool canary = new CanaryTool(executor, sink);
    String[] args = { "-t", "10000", tableName.getNameAsString() };
    org.apache.hadoop.conf.Configuration testConf =
      new org.apache.hadoop.conf.Configuration(conf);
    testConf.setBoolean(HConstants.HBASE_CANARY_READ_RAW_SCAN_KEY, true);
    assertEquals(0, ToolRunner.run(testConf, canary, args));
    verify(sink, atLeastOnce()).publishReadTiming(isA(ServerName.class), isA(RegionInfo.class),
      isA(ColumnFamilyDescriptor.class), anyLong());
    assertEquals("verify no read error count", 0, canary.getReadFailures().size());

    admin.disableTable(tableName);
    admin.deleteTable(tableName);
  }

  private void runRegionserverCanary() throws Exception {
    ExecutorService executor = new ScheduledThreadPoolExecutor(1);
    CanaryTool canary = new CanaryTool(executor, new CanaryTool.RegionServerStdOutSink());
    String[] args = { "-t", "10000", "-regionserver" };
    assertEquals(0, ToolRunner.run(conf, canary, args));
    assertEquals("verify no read error count", 0, canary.getReadFailures().size());
  }

  private void testZookeeperCanaryWithArgs(String[] args) throws Exception {
    setUpMockAppender();

    // ProcessBasedMiniHBaseCluster doesn't expose ZK cluster directly
    // but ZK connection info is already configured in conf
    String zkQuorum = conf.get(HConstants.ZOOKEEPER_QUORUM);
    ExecutorService executor = new ScheduledThreadPoolExecutor(2);
    CanaryTool.ZookeeperStdOutSink sink = spy(new CanaryTool.ZookeeperStdOutSink());
    CanaryTool canary = new CanaryTool(executor, sink);
    assertEquals(0, ToolRunner.run(conf, canary, args));

    String baseZnode = conf.get(HConstants.ZOOKEEPER_ZNODE_PARENT,
      HConstants.DEFAULT_ZOOKEEPER_ZNODE_PARENT);
    // Verify that ZK canary completed (timing was published)
    verify(sink, atLeastOnce()).publishReadTiming(eq(baseZnode), isA(String.class), anyLong());
  }
}
