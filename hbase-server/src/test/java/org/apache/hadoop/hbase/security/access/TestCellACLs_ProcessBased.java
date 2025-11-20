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

import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.AuthUtil;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.security.access.Permission.Action;
import org.apache.hadoop.hbase.security.access.UserPermission;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.SecurityTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Threads;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.com.google.common.collect.Lists;

/**
 * ProcessBased version of {@link TestCellACLs}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Tests cell-level ACL functionality using pure client APIs. All ACL verification
 * is done via client operations (Put/Get/Delete/Increment/Scan) with different users,
 * not via direct coprocessor access.
 *
 * @see TestCellACLs Original test using MiniHBaseCluster
 */
@Category({ SecurityTests.class, MediumTests.class })
public class TestCellACLs_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestCellACLs_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestCellACLs_ProcessBased.class);

  private static final byte[] TEST_FAMILY = Bytes.toBytes("f1");
  private static final byte[] TEST_ROW = Bytes.toBytes("cellpermtest");
  private static final byte[] TEST_Q1 = Bytes.toBytes("q1");
  private static final byte[] TEST_Q2 = Bytes.toBytes("q2");
  private static final byte[] TEST_Q3 = Bytes.toBytes("q3");
  private static final byte[] TEST_Q4 = Bytes.toBytes("q4");
  private static final byte[] ZERO = Bytes.toBytes(0L);
  private static final byte[] ONE = Bytes.toBytes(1L);

  private static final String GROUP = "group";
  private static User GROUP_USER;
  private static User USER_OWNER;
  private static User USER_OTHER;
  private static String[] usersAndGroups;

  private TableName testTableName;

  private void setupUsers() throws Exception {
    USER_OWNER = User.createUserForTesting(conf, "owner", new String[0]);
    USER_OTHER = User.createUserForTesting(conf, "other", new String[0]);
    GROUP_USER = User.createUserForTesting(conf, "group_user", new String[] { GROUP });

    usersAndGroups = new String[] { USER_OTHER.getShortName(), AuthUtil.toGroupEntry(GROUP) };
  }

  private void setupTable() throws Exception {
    testTableName = TableName.valueOf("test_cell_acl_" + System.currentTimeMillis());

    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(testTableName);
    builder.setOwner(USER_OWNER);

    ColumnFamilyDescriptorBuilder cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(TEST_FAMILY);
    cfBuilder.setMaxVersions(4);
    builder.setColumnFamily(cfBuilder.build());

    TableDescriptor td = builder.build();

    try (Connection connection = ConnectionFactory.createConnection(conf)) {
      try (Admin admin = connection.getAdmin()) {
        admin.createTable(td, new byte[][] { Bytes.toBytes("s") });
      }
    }

    try (Connection connection = ConnectionFactory.createConnection(conf)) {
      try (Admin admin = connection.getAdmin()) {
        admin.getDescriptor(testTableName);
      }
    }

    LOG.info("Sleeping a second because of HBASE-12581");
    Threads.sleep(1000);
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

  private Map<String, Permission> prepareCellPermissions(String[] users, Action... action) {
    Map<String, Permission> perms = new HashMap<>(2);
    for (String user : users) {
      perms.put(user, new Permission(action));
    }
    return perms;
  }

  private void verifyAllowed(User user, AccessTestAction action) throws Exception {
    try {
      user.runAs(action);
    } catch (Exception e) {
      throw new RuntimeException(user.getShortName() + " should be allowed, but got exception", e);
    }
  }

  private void verifyAllowed(AccessTestAction action, User... users) throws Exception {
    for (User user : users) {
      verifyAllowed(user, action);
    }
  }

  private void verifyDenied(AccessTestAction action, User... users) throws Exception {
    for (User user : users) {
      try {
        user.runAs(action);
        throw new RuntimeException(
          user.getShortName() + " should NOT be allowed to perform action");
      } catch (Exception e) {
        // Expected
      }
    }
  }

  private void verifyIfNull(AccessTestAction action, User... users) throws Exception {
    for (User user : users) {
      try {
        Object result = user.runAs(action);
        if (result != null && result instanceof List) {
          List<?> list = (List<?>) result;
          assertEquals(
            "Expected empty result for " + user.getShortName() + " but got " + list.size(), 0,
            list.size());
        }
      } catch (Exception e) {
        LOG.info("Caught expected exception for " + user.getShortName() + ": " + e.getMessage());
      }
    }
  }

  @Test
  public void testCellPermissions_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testCellPermissionsImpl();
  }

  @Test
  public void testCellPermissions_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testCellPermissionsImpl();
  }

  @Test
  public void testCellPermissions_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    testCellPermissionsImpl();
  }

  @Test
  public void testCellPermissions_AFTER_WRITE() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE";
    testCellPermissionsImpl();
  }

  private void testCellPermissionsImpl() throws Exception {
    conf = HBaseConfiguration.create();
    conf.setInt(HConstants.REGION_SERVER_HIGH_PRIORITY_HANDLER_COUNT, 10);
    SecureTestUtil.enableSecurity(conf);
    SecureTestUtil.verifyConfiguration(conf);
    conf.setBoolean(AccessControlConstants.CF_ATTRIBUTE_EARLY_OUT, false);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(1).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    setupUsers();
    setupTable();

    checkpoint("AFTER_CREATE_TABLE");

    // Store two sets of values, one with cell level ACL, one without
    verifyAllowed(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          Put p;
          // with ro ACL
          p = new Put(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q1, ZERO);
          p.setACL(prepareCellPermissions(usersAndGroups, Action.READ));
          t.put(p);
          // with rw ACL
          p = new Put(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q2, ZERO);
          p.setACL(prepareCellPermissions(usersAndGroups, Action.READ, Action.WRITE));
          t.put(p);
          // no ACL
          p = new Put(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q3, ZERO).addColumn(TEST_FAMILY,
            TEST_Q4, ZERO);
          t.put(p);
        }
        return null;
      }
    }, USER_OWNER);

    checkpoint("AFTER_WRITE");

    /* ---- Gets ---- */

    AccessTestAction getQ1 = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Get get = new Get(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q1);
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          return t.get(get).listCells();
        }
      }
    };

    AccessTestAction getQ2 = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Get get = new Get(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q2);
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          return t.get(get).listCells();
        }
      }
    };

    AccessTestAction getQ3 = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Get get = new Get(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q3);
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          return t.get(get).listCells();
        }
      }
    };

    AccessTestAction getQ4 = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Get get = new Get(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q4);
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          return t.get(get).listCells();
        }
      }
    };

    // Confirm special read access set at cell level
    verifyAllowed(getQ1, USER_OTHER, GROUP_USER);
    verifyAllowed(getQ2, USER_OTHER, GROUP_USER);

    // Confirm this access does not extend to other cells
    verifyIfNull(getQ3, USER_OTHER, GROUP_USER);
    verifyIfNull(getQ4, USER_OTHER, GROUP_USER);

    /* ---- Scans ---- */

    final List<Cell> scanResults = Lists.newArrayList();

    AccessTestAction scanAction = new AccessTestAction() {
      @Override
      public List<Cell> run() throws Exception {
        Scan scan = new Scan();
        scan.setStartRow(TEST_ROW);
        scan.setStopRow(Bytes.add(TEST_ROW, new byte[] { 0 }));
        scan.addFamily(TEST_FAMILY);
        Connection connection = ConnectionFactory.createConnection(conf);
        Table t = connection.getTable(testTableName);
        try {
          ResultScanner scanner = t.getScanner(scan);
          Result result = null;
          do {
            result = scanner.next();
            if (result != null) {
              scanResults.addAll(result.listCells());
            }
          } while (result != null);
        } finally {
          t.close();
          connection.close();
        }
        return scanResults;
      }
    };

    // owner will see all values
    scanResults.clear();
    verifyAllowed(scanAction, USER_OWNER);
    assertEquals(4, scanResults.size());

    // other user will see 2 values
    scanResults.clear();
    verifyAllowed(scanAction, USER_OTHER);
    assertEquals(2, scanResults.size());

    scanResults.clear();
    verifyAllowed(scanAction, GROUP_USER);
    assertEquals(2, scanResults.size());

    /* ---- Increments ---- */

    AccessTestAction incrementQ1 = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Increment i = new Increment(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q1, 1L);
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          t.increment(i);
        }
        return null;
      }
    };

    AccessTestAction incrementQ2 = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Increment i = new Increment(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q2, 1L);
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          t.increment(i);
        }
        return null;
      }
    };

    AccessTestAction incrementQ2newDenyACL = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Increment i = new Increment(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q2, 1L);
        i.setACL(prepareCellPermissions(usersAndGroups, Action.READ));
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          t.increment(i);
        }
        return null;
      }
    };

    AccessTestAction incrementQ3 = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Increment i = new Increment(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q3, 1L);
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          t.increment(i);
        }
        return null;
      }
    };

    verifyDenied(incrementQ1, USER_OTHER, GROUP_USER);
    verifyDenied(incrementQ3, USER_OTHER, GROUP_USER);

    verifyAllowed(incrementQ2, USER_OTHER, GROUP_USER);
    verifyAllowed(incrementQ2newDenyACL, USER_OTHER);
    verifyDenied(incrementQ2, USER_OTHER, GROUP_USER);

    /* ---- Deletes ---- */

    AccessTestAction deleteFamily = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Delete delete = new Delete(TEST_ROW).addFamily(TEST_FAMILY);
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          t.delete(delete);
        }
        return null;
      }
    };

    AccessTestAction deleteQ1 = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Delete delete = new Delete(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q1);
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          t.delete(delete);
        }
        return null;
      }
    };

    verifyDenied(deleteFamily, USER_OTHER, GROUP_USER);
    verifyDenied(deleteQ1, USER_OTHER, GROUP_USER);
    verifyAllowed(deleteQ1, USER_OWNER);

    tearDownTable();
  }

  @Test
  public void testCoveringCheck_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testCoveringCheckImpl();
  }

  @Test
  public void testCoveringCheck_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testCoveringCheckImpl();
  }

  private void testCoveringCheckImpl() throws Exception {
    conf = HBaseConfiguration.create();
    conf.setInt(HConstants.REGION_SERVER_HIGH_PRIORITY_HANDLER_COUNT, 10);
    SecureTestUtil.enableSecurity(conf);
    SecureTestUtil.verifyConfiguration(conf);
    conf.setBoolean(AccessControlConstants.CF_ATTRIBUTE_EARLY_OUT, false);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(1).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    setupUsers();
    setupTable();

    // Grant read access to USER_OTHER
    try (Connection connection = ConnectionFactory.createConnection(conf);
      Admin admin = connection.getAdmin()) {
      admin.grant(
        new UserPermission(USER_OTHER.getShortName(),
          Permission.newBuilder(testTableName).withFamily(TEST_FAMILY).withActions(Action.READ)
            .build()),
        false);
      // Grant read access to GROUP
      admin.grant(
        new UserPermission(AuthUtil.toGroupEntry(GROUP),
          Permission.newBuilder(testTableName).withFamily(TEST_FAMILY).withActions(Action.READ)
            .build()),
        false);
    }

    // A write by USER_OTHER should be denied
    verifyUserDeniedForWrite(USER_OTHER, ZERO);
    // A write by GROUP_USER should be denied
    verifyUserDeniedForWrite(GROUP_USER, ZERO);

    // Add the cell
    verifyAllowed(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          Put p;
          p = new Put(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q1, ZERO);
          t.put(p);
        }
        return null;
      }
    }, USER_OWNER);

    // A write by USER_OTHER should still be denied
    verifyUserDeniedForWrite(USER_OTHER, ONE);
    // A write by GROUP_USER should still be denied
    verifyUserDeniedForWrite(GROUP_USER, ONE);

    // A read by USER_OTHER should be allowed
    verifyUserAllowedForRead(USER_OTHER);
    // A read by GROUP_USER should be allowed
    verifyUserAllowedForRead(GROUP_USER);

    tearDownTable();
  }

  private void verifyUserDeniedForWrite(final User user, final byte[] value) throws Exception {
    verifyDenied(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          Put p;
          p = new Put(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q1, value);
          t.put(p);
        }
        return null;
      }
    }, user);
  }

  private void verifyUserAllowedForRead(final User user) throws Exception {
    verifyAllowed(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          return t.get(new Get(TEST_ROW).addColumn(TEST_FAMILY, TEST_Q1));
        }
      }
    }, user);
  }

  @After
  @Override
  public void tearDownTest() throws Exception {
    super.tearDownTest();
  }

  private abstract static class AccessTestAction implements PrivilegedExceptionAction<Object> {
  }
}
