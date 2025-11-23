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
package org.apache.hadoop.hbase.security.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.security.PrivilegedExceptionAction;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.security.access.Permission;
import org.apache.hadoop.hbase.security.access.Permission.Action;
import org.apache.hadoop.hbase.security.access.UserPermission;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.SecurityTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestScanEarlyTermination}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Tests ACL-based scan early termination using pure client APIs. All ACL verification
 * is done via client operations (Scan behavior, access exceptions) with different users,
 * not via direct coprocessor access.
 *
 * @see TestScanEarlyTermination Original test using MiniHBaseCluster
 */
@Category({ SecurityTests.class, MediumTests.class })
public class TestScanEarlyTermination_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestScanEarlyTermination_ProcessBased.class);

  private static final Logger LOG =
    LoggerFactory.getLogger(TestScanEarlyTermination_ProcessBased.class);

  private static final byte[] TEST_FAMILY1 = Bytes.toBytes("f1");
  private static final byte[] TEST_FAMILY2 = Bytes.toBytes("f2");
  private static final byte[] TEST_ROW = Bytes.toBytes("testrow");
  private static final byte[] TEST_Q1 = Bytes.toBytes("q1");
  private static final byte[] TEST_Q2 = Bytes.toBytes("q2");
  private static final byte[] ZERO = Bytes.toBytes(0L);

  private static User USER_OWNER;
  private static User USER_OTHER;

  private TableName testTableName;

  private void setupUsers() throws Exception {
    USER_OWNER = User.createUserForTesting(conf, "owner", new String[0]);
    USER_OTHER = User.createUserForTesting(conf, "other", new String[0]);
  }

  private void setupTable() throws Exception {
    testTableName = TableName.valueOf("test_scan_early_term_" + System.currentTimeMillis());

    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(testTableName);
    builder.setOwner(USER_OWNER);

    ColumnFamilyDescriptorBuilder cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(TEST_FAMILY1);
    cfBuilder.setMaxVersions(10);
    builder.setColumnFamily(cfBuilder.build());

    cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(TEST_FAMILY2);
    cfBuilder.setMaxVersions(10);
    builder.setColumnFamily(cfBuilder.build());

    // Enable backwards compatible early termination behavior
    builder.setValue(AccessControlConstants.CF_ATTRIBUTE_EARLY_OUT, "true");

    TableDescriptor td = builder.build();

    try (Connection connection = ConnectionFactory.createConnection(conf)) {
      try (Admin admin = connection.getAdmin()) {
        admin.createTable(td);
      }
    }

    try (Connection connection = ConnectionFactory.createConnection(conf)) {
      try (Admin admin = connection.getAdmin()) {
        admin.getDescriptor(testTableName);
      }
    }
  }

  private void tearDownTable() throws Exception {
    try (Connection connection = ConnectionFactory.createConnection(conf)) {
      try (Admin admin = connection.getAdmin()) {
        try {
          admin.disableTable(testTableName);
          admin.deleteTable(testTableName);
        } catch (TableNotFoundException ex) {
          LOG.info("Test deleted table " + testTableName);
        }
      }
    }
    assertEquals(0, PermissionStorage.getTablePermissions(conf, testTableName).size());
  }

  private void verifyAllowed(User user, AccessTestAction action) throws Exception {
    try {
      user.runAs(action);
    } catch (Exception e) {
      throw new RuntimeException(user.getShortName() + " should be allowed, but got exception", e);
    }
  }

  private void verifyAllowed(AccessTestAction action, User user) throws Exception {
    verifyAllowed(user, action);
  }

  private void verifyDenied(AccessTestAction action, User user) throws Exception {
    try {
      user.runAs(action);
      throw new RuntimeException(user.getShortName() + " should NOT be allowed to perform action");
    } catch (Exception e) {
      // Expected
    }
  }

  @Test
  public void testEarlyScanTermination_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testEarlyScanTerminationImpl();
  }

  @Test
  public void testEarlyScanTermination_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testEarlyScanTerminationImpl();
  }

  @Test
  public void testEarlyScanTermination_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    testEarlyScanTerminationImpl();
  }

  @Test
  public void testEarlyScanTermination_AFTER_GRANT() throws Exception {
    upgradeCheckpoint = "AFTER_GRANT";
    testEarlyScanTerminationImpl();
  }

  @Test
  public void testEarlyScanTermination_AFTER_WRITE() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE";
    testEarlyScanTerminationImpl();
  }

  private void testEarlyScanTerminationImpl() throws Exception {
    conf.setInt(HConstants.REGION_SERVER_HIGH_PRIORITY_HANDLER_COUNT, 10);
    SecureTestUtil.enableSecurity(conf);
    SecureTestUtil.verifyConfiguration(conf);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(1).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    setupUsers();
    setupTable();

    checkpoint("AFTER_CREATE_TABLE");

    // Grant USER_OTHER access to TEST_FAMILY1 only
    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      admin.grant(
        new UserPermission(USER_OTHER.getShortName(),
          Permission.newBuilder(testTableName).withFamily(TEST_FAMILY1).withActions(Action.READ)
            .build()),
        false);
    }

    checkpoint("AFTER_GRANT");

    // Set up test data
    verifyAllowed(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Connection connection = ConnectionFactory.createConnection(conf);
        Table t = connection.getTable(testTableName);
        try {
          Put put = new Put(TEST_ROW).addColumn(TEST_FAMILY1, TEST_Q1, ZERO);
          t.put(put);
          // Set a READ cell ACL for USER_OTHER on this value in FAMILY2
          put = new Put(TEST_ROW).addColumn(TEST_FAMILY2, TEST_Q1, ZERO);
          put.setACL(USER_OTHER.getShortName(), new Permission(Action.READ));
          t.put(put);
          // Set an empty cell ACL for USER_OTHER on this other value in FAMILY2
          put = new Put(TEST_ROW).addColumn(TEST_FAMILY2, TEST_Q2, ZERO);
          put.setACL(USER_OTHER.getShortName(), new Permission());
          t.put(put);
        } finally {
          t.close();
          connection.close();
        }
        return null;
      }
    }, USER_OWNER);

    checkpoint("AFTER_WRITE");

    // A scan of FAMILY1 will be allowed
    verifyAllowed(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Connection connection = ConnectionFactory.createConnection(conf);
        Table t = connection.getTable(testTableName);
        try {
          Scan scan = new Scan().addFamily(TEST_FAMILY1);
          Result result = t.getScanner(scan).next();
          if (result != null) {
            assertTrue("Improper exclusion", result.containsColumn(TEST_FAMILY1, TEST_Q1));
            assertFalse("Improper inclusion", result.containsColumn(TEST_FAMILY2, TEST_Q1));
            return result.listCells();
          }
          return null;
        } finally {
          t.close();
          connection.close();
        }
      }
    }, USER_OTHER);

    // A scan of FAMILY1 and FAMILY2 will produce results for FAMILY1 without
    // throwing an exception, however no cells from FAMILY2 will be returned
    // because we early out checks at the CF level.
    verifyAllowed(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Connection connection = ConnectionFactory.createConnection(conf);
        Table t = connection.getTable(testTableName);
        try {
          Scan scan = new Scan();
          Result result = t.getScanner(scan).next();
          if (result != null) {
            assertTrue("Improper exclusion", result.containsColumn(TEST_FAMILY1, TEST_Q1));
            assertFalse("Improper inclusion", result.containsColumn(TEST_FAMILY2, TEST_Q1));
            return result.listCells();
          }
          return null;
        } finally {
          t.close();
          connection.close();
        }
      }
    }, USER_OTHER);

    // A scan of FAMILY2 will throw an AccessDeniedException
    verifyDenied(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Connection connection = ConnectionFactory.createConnection(conf);
        Table t = connection.getTable(testTableName);
        try {
          Scan scan = new Scan().addFamily(TEST_FAMILY2);
          Result result = t.getScanner(scan).next();
          if (result != null) {
            return result.listCells();
          }
          return null;
        } finally {
          t.close();
          connection.close();
        }
      }
    }, USER_OTHER);

    // Now grant USER_OTHER access to TEST_FAMILY2:TEST_Q2
    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      admin.grant(
        new UserPermission(USER_OTHER.getShortName(),
          Permission.newBuilder(testTableName).withFamily(TEST_FAMILY2).withQualifier(TEST_Q2)
            .withActions(Action.READ).build()),
        false);
    }

    // A scan of FAMILY1 and FAMILY2 will produce combined results
    verifyAllowed(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Connection connection = ConnectionFactory.createConnection(conf);
        Table t = connection.getTable(testTableName);
        try {
          Scan scan = new Scan();
          Result result = t.getScanner(scan).next();
          if (result != null) {
            assertTrue("Improper exclusion", result.containsColumn(TEST_FAMILY1, TEST_Q1));
            assertFalse("Improper inclusion", result.containsColumn(TEST_FAMILY2, TEST_Q1));
            assertTrue("Improper exclusion", result.containsColumn(TEST_FAMILY2, TEST_Q2));
            return result.listCells();
          }
          return null;
        } finally {
          t.close();
          connection.close();
        }
      }
    }, USER_OTHER);

    tearDownTable();
  }

  @After
  @Override
  public void tearDownTest() throws Exception {
    super.tearDownTest();
  }

  private abstract static class AccessTestAction implements PrivilegedExceptionAction<Object> {
  }
}
