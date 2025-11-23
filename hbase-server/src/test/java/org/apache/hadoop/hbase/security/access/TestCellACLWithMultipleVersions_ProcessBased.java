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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.AuthUtil;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.security.access.Permission.Action;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.SecurityTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.hadoop.hbase.util.Threads;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestCellACLWithMultipleVersions}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Tests cell-level ACL functionality with multiple versions using pure client APIs.
 * All ACL verification is done via client operations (Put/Get/Delete/Increment/CheckAndDelete)
 * with different users, not via direct coprocessor access.
 *
 * @see TestCellACLWithMultipleVersions Original test using MiniHBaseCluster
 */
@Category({ SecurityTests.class, MediumTests.class })
public class TestCellACLWithMultipleVersions_ProcessBased extends ProcessBasedUpgradeTestBase {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestCellACLWithMultipleVersions_ProcessBased.class);

  private static final Logger LOG =
    LoggerFactory.getLogger(TestCellACLWithMultipleVersions_ProcessBased.class);

  private static final byte[] TEST_FAMILY1 = Bytes.toBytes("f1");
  private static final byte[] TEST_FAMILY2 = Bytes.toBytes("f2");
  private static final byte[] TEST_ROW = Bytes.toBytes("cellpermtest");
  private static final byte[] TEST_Q1 = Bytes.toBytes("q1");
  private static final byte[] TEST_Q2 = Bytes.toBytes("q2");
  private static final byte[] ZERO = Bytes.toBytes(0L);
  private static final byte[] ONE = Bytes.toBytes(1L);
  private static final byte[] TWO = Bytes.toBytes(2L);

  private static final String GROUP = "group";
  private static User GROUP_USER;
  private static User USER_OWNER;
  private static User USER_OTHER;
  private static User USER_OTHER2;

  private static String[] usersAndGroups;

  private TableName testTableName;

  private void setupUsers() throws Exception {
    // Create test users
    USER_OWNER = User.createUserForTesting(conf, "owner", new String[0]);
    USER_OTHER = User.createUserForTesting(conf, "other", new String[0]);
    USER_OTHER2 = User.createUserForTesting(conf, "other2", new String[0]);
    GROUP_USER = User.createUserForTesting(conf, "group_user", new String[] { GROUP });

    usersAndGroups = new String[] { USER_OTHER.getShortName(), AuthUtil.toGroupEntry(GROUP) };
  }

  private void setupTable() throws Exception {
    testTableName = TableName.valueOf("test_cell_acl_" + System.currentTimeMillis());

    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(testTableName);
    builder.setOwner(USER_OWNER);

    ColumnFamilyDescriptorBuilder cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(TEST_FAMILY1);
    cfBuilder.setMaxVersions(4);
    builder.setColumnFamily(cfBuilder.build());

    cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(TEST_FAMILY2);
    cfBuilder.setMaxVersions(4);
    builder.setColumnFamily(cfBuilder.build());

    TableDescriptor td = builder.build();

    try (Connection connection = ConnectionFactory.createConnection(conf)) {
      try (Admin admin = connection.getAdmin()) {
        admin.createTable(td, new byte[][] { Bytes.toBytes("s") });
      }
    }

    // Wait for table to be enabled
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

  private void verifyAllowed(User user, AccessTestAction action, int expected) throws Exception {
    try {
      Object result = user.runAs(action);
      if (result != null && result instanceof java.util.List) {
        java.util.List<?> list = (java.util.List<?>) result;
        assertEquals("Expected " + expected + " cells", expected, list.size());
      }
    } catch (Exception e) {
      fail(user.getShortName() + " should be allowed, but got exception: " + e.getMessage());
    }
  }

  private void verifyAllowed(AccessTestAction action, User user) throws Exception {
    try {
      user.runAs(action);
    } catch (Exception e) {
      fail(user.getShortName() + " should be allowed, but got exception: " + e.getMessage());
    }
  }

  private void verifyIfNull(AccessTestAction action, User... users) throws Exception {
    for (User user : users) {
      try {
        Object result = user.runAs(action);
        if (result != null && result instanceof java.util.List) {
          java.util.List<?> list = (java.util.List<?>) result;
          assertEquals(
            "Expected empty result for " + user.getShortName() + " but got " + list.size(), 0,
            list.size());
        }
      } catch (Exception e) {
        LOG.info("Caught expected exception for " + user.getShortName() + ": " + e.getMessage());
      }
    }
  }

  private void verifyAllowed(AccessTestAction action, User... users) throws Exception {
    for (User user : users) {
      verifyAllowed(action, user);
    }
  }

  @Test
  public void testCellPermissionwithVersions_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testCellPermissionwithVersionsImpl();
  }

  @Test
  public void testCellPermissionwithVersions_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testCellPermissionwithVersionsImpl();
  }

  @Test
  public void testCellPermissionwithVersions_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    testCellPermissionwithVersionsImpl();
  }

  @Test
  public void testCellPermissionwithVersions_AFTER_FIRST_WRITE() throws Exception {
    upgradeCheckpoint = "AFTER_FIRST_WRITE";
    testCellPermissionwithVersionsImpl();
  }

  private void testCellPermissionwithVersionsImpl() throws Exception {
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

    // Store values with cell level ACLs
    final Map<String, Permission> writePerms =
      prepareCellPermissions(usersAndGroups, Action.WRITE);
    final Map<String, Permission> readPerms = prepareCellPermissions(usersAndGroups, Action.READ);

    verifyAllowed(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          Put p;
          long now = EnvironmentEdgeManager.currentTime();
          p = new Put(TEST_ROW).addColumn(TEST_FAMILY1, TEST_Q1, now, ZERO);
          p.setACL(writePerms);
          t.put(p);
          p = new Put(TEST_ROW).addColumn(TEST_FAMILY1, TEST_Q1, now + 1, ZERO);
          p.setACL(readPerms);
          t.put(p);
          p = new Put(TEST_ROW).addColumn(TEST_FAMILY1, TEST_Q1, now + 2, ZERO);
          p.setACL(writePerms);
          t.put(p);
          p = new Put(TEST_ROW).addColumn(TEST_FAMILY1, TEST_Q1, now + 3, ZERO);
          p.setACL(readPerms);
          t.put(p);
          p = new Put(TEST_ROW).addColumn(TEST_FAMILY1, TEST_Q1, now + 4, ZERO);
          p.setACL(writePerms);
          t.put(p);
        }
        return null;
      }
    }, USER_OWNER);

    checkpoint("AFTER_FIRST_WRITE");

    AccessTestAction getQ1 = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Get get = new Get(TEST_ROW);
        get.setMaxVersions(10);
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          return t.get(get).listCells();
        }
      }
    };

    AccessTestAction get2 = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Get get = new Get(TEST_ROW);
        get.setMaxVersions(10);
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          return t.get(get).listCells();
        }
      }
    };

    verifyAllowed(GROUP_USER, getQ1, 2);
    verifyAllowed(USER_OTHER, getQ1, 2);

    verifyAllowed(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table t = connection.getTable(testTableName)) {
          Put p;
          p = new Put(TEST_ROW).addColumn(TEST_FAMILY1, TEST_Q1, ZERO);
          p.setACL(writePerms);
          t.put(p);
          p = new Put(TEST_ROW).addColumn(TEST_FAMILY1, TEST_Q1, ZERO);
          p.setACL(readPerms);
          t.put(p);
          p = new Put(TEST_ROW).addColumn(TEST_FAMILY1, TEST_Q1, ZERO);
          p.setACL(writePerms);
          t.put(p);
        }
        return null;
      }
    }, USER_OWNER);

    verifyAllowed(USER_OTHER, get2, 1);
    verifyAllowed(GROUP_USER, get2, 1);

    tearDownTable();
  }

  @Test
  public void testCellPermissionsWithDeleteMutipleVersions_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testCellPermissionsWithDeleteMutipleVersionsImpl();
  }

  @Test
  public void testCellPermissionsWithDeleteMutipleVersions_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testCellPermissionsWithDeleteMutipleVersionsImpl();
  }

  private void testCellPermissionsWithDeleteMutipleVersionsImpl() throws Exception {
    SecureTestUtil.enableSecurity(conf);
    SecureTestUtil.verifyConfiguration(conf);
    conf.setBoolean(AccessControlConstants.CF_ATTRIBUTE_EARLY_OUT, false);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(1).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    setupUsers();
    setupTable();

    final byte[] TEST_ROW1 = Bytes.toBytes("r1");
    final byte[] TEST_ROW2 = Bytes.toBytes("r2");
    final byte[] TEST_Q1 = Bytes.toBytes("q1");
    final byte[] TEST_Q2 = Bytes.toBytes("q2");
    final byte[] ZERO = Bytes.toBytes(0L);

    final User user1 = User.createUserForTesting(conf, "user1", new String[0]);
    final User user2 = User.createUserForTesting(conf, "user2", new String[0]);

    verifyAllowed(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf)) {
          try (Table t = connection.getTable(testTableName)) {
            Put p = new Put(TEST_ROW1);
            p.addColumn(TEST_FAMILY1, TEST_Q1, ZERO);
            p.addColumn(TEST_FAMILY1, TEST_Q2, ZERO);
            p.setACL(user1.getShortName(),
              new Permission(Permission.Action.READ, Permission.Action.WRITE));
            t.put(p);
            p = new Put(TEST_ROW2);
            p.addColumn(TEST_FAMILY1, TEST_Q1, ZERO);
            p.addColumn(TEST_FAMILY1, TEST_Q2, ZERO);
            p.setACL(user1.getShortName(),
              new Permission(Permission.Action.READ, Permission.Action.WRITE));
            t.put(p);
          }
        }
        return null;
      }
    }, USER_OWNER);

    verifyAllowed(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf)) {
          try (Table t = connection.getTable(testTableName)) {
            Put p = new Put(TEST_ROW1);
            p.addColumn(TEST_FAMILY1, TEST_Q1, ZERO);
            p.addColumn(TEST_FAMILY1, TEST_Q2, ZERO);
            Map<String, Permission> perms =
              prepareCellPermissions(new String[] { user1.getShortName(), user2.getShortName(),
                AuthUtil.toGroupEntry(GROUP) }, Action.READ, Action.WRITE);
            p.setACL(perms);
            t.put(p);
            p = new Put(TEST_ROW2);
            p.addColumn(TEST_FAMILY1, TEST_Q1, ZERO);
            p.addColumn(TEST_FAMILY1, TEST_Q2, ZERO);
            p.setACL(perms);
            t.put(p);
          }
        }
        return null;
      }
    }, user1);

    user1.runAs(new PrivilegedExceptionAction<Void>() {
      @Override
      public Void run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf)) {
          try (Table t = connection.getTable(testTableName)) {
            Delete d = new Delete(TEST_ROW1);
            d.addColumns(TEST_FAMILY1, TEST_Q1);
            d.addColumns(TEST_FAMILY1, TEST_Q2);
            t.delete(d);
          }
        }
        return null;
      }
    });

    verifyUserDeniedForDeleteMultipleVersions(user2, TEST_ROW2, TEST_Q1, TEST_Q2);
    verifyUserDeniedForDeleteMultipleVersions(GROUP_USER, TEST_ROW2, TEST_Q1, TEST_Q2);

    user1.runAs(new PrivilegedExceptionAction<Void>() {
      @Override
      public Void run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf)) {
          try (Table t = connection.getTable(testTableName)) {
            Delete d = new Delete(TEST_ROW2);
            d.addFamily(TEST_FAMILY1);
            t.delete(d);
          }
        }
        return null;
      }
    });

    tearDownTable();
  }

  private void verifyUserDeniedForDeleteMultipleVersions(final User user, final byte[] row,
    final byte[] q1, final byte[] q2) throws IOException, InterruptedException {
    user.runAs(new PrivilegedExceptionAction<Void>() {
      @Override
      public Void run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf)) {
          try (Table t = connection.getTable(testTableName)) {
            Delete d = new Delete(row);
            d.addColumns(TEST_FAMILY1, q1);
            d.addColumns(TEST_FAMILY1, q2);
            t.delete(d);
            fail(user.getShortName() + " should not be allowed to delete the row");
          } catch (Exception e) {

          }
        }
        return null;
      }
    });
  }

  @Test
  public void testDeleteWithFutureTimestamp_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testDeleteWithFutureTimestampImpl();
  }

  @Test
  public void testDeleteWithFutureTimestamp_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testDeleteWithFutureTimestampImpl();
  }

  private void testDeleteWithFutureTimestampImpl() throws Exception {
    SecureTestUtil.enableSecurity(conf);
    SecureTestUtil.verifyConfiguration(conf);
    conf.setBoolean(AccessControlConstants.CF_ATTRIBUTE_EARLY_OUT, false);

    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).numRegionServers(1).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    setupUsers();
    setupTable();

    verifyAllowed(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf)) {
          try (Table t = connection.getTable(testTableName)) {
            Put p = new Put(TEST_ROW).addColumn(TEST_FAMILY1, TEST_Q2, ONE);
            Map<String, Permission> readAndWritePerms =
              prepareCellPermissions(usersAndGroups, Action.READ, Action.WRITE);
            p.setACL(readAndWritePerms);
            t.put(p);
            p = new Put(TEST_ROW).addColumn(TEST_FAMILY2, TEST_Q2, ONE);
            p.setACL(readAndWritePerms);
            t.put(p);
            LOG.info("Stored at current time");
            p = new Put(TEST_ROW).addColumn(TEST_FAMILY1, TEST_Q1,
              EnvironmentEdgeManager.currentTime() + 1000000, ZERO);
            p.setACL(prepareCellPermissions(
              new String[] { USER_OTHER.getShortName(), AuthUtil.toGroupEntry(GROUP) },
              Action.READ));
            t.put(p);
          }
        }
        return null;
      }
    }, USER_OWNER);

    AccessTestAction getQ1 = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Get get = new Get(TEST_ROW).addColumn(TEST_FAMILY1, TEST_Q1);
        try (Connection connection = ConnectionFactory.createConnection(conf)) {
          try (Table t = connection.getTable(testTableName)) {
            return t.get(get).listCells();
          }
        }
      }
    };

    AccessTestAction getQ2 = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Get get = new Get(TEST_ROW).addColumn(TEST_FAMILY1, TEST_Q2);
        try (Connection connection = ConnectionFactory.createConnection(conf)) {
          try (Table t = connection.getTable(testTableName)) {
            return t.get(get).listCells();
          }
        }
      }
    };

    verifyAllowed(getQ1, USER_OWNER, USER_OTHER, GROUP_USER);
    verifyAllowed(getQ2, USER_OWNER, USER_OTHER, GROUP_USER);

    AccessTestAction deleteFamily1 = getDeleteFamilyAction(TEST_FAMILY1);
    AccessTestAction deleteFamily2 = getDeleteFamilyAction(TEST_FAMILY2);

    verifyAllowed(deleteFamily1, USER_OTHER);
    verifyAllowed(deleteFamily2, GROUP_USER);

    verifyAllowed(getQ1, USER_OWNER, USER_OTHER, GROUP_USER);
    verifyIfNull(getQ2, USER_OTHER, GROUP_USER);

    tearDownTable();
  }

  private AccessTestAction getDeleteFamilyAction(final byte[] fam) {
    AccessTestAction deleteFamilyAction = new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        Delete delete = new Delete(TEST_ROW).addFamily(fam);
        try (Connection connection = ConnectionFactory.createConnection(conf)) {
          try (Table t = connection.getTable(testTableName)) {
            t.delete(delete);
          }
        }
        return null;
      }
    };
    return deleteFamilyAction;
  }

  @After
  @Override
  public void tearDownTest() throws Exception {
    super.tearDownTest();
  }

  private abstract static class AccessTestAction implements PrivilegedExceptionAction<Object> {
  }
}
