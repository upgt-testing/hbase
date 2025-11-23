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

import static org.apache.hadoop.hbase.AuthUtil.toGroupEntry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.process.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.TableNameTestRule;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.coprocessor.CoprocessorHost;
import org.apache.hadoop.hbase.io.hfile.HFile;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.security.access.Permission.Action;
import org.apache.hadoop.hbase.security.access.SecureTestUtil.AccessTestAction;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.SecurityTests;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessBased version of {@link TestAccessController2}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * Tests transformed (4/6):
 * - testCreateWithCorrectOwner
 * - testCreateTableWithGroupPermissions
 * - testACLTableAccess
 * - testPostGrantAndRevokeScanAction
 *
 * Tests removed (2/6):
 * - testCoprocessorLoading: Requires direct access to MasterCoprocessorHost/RegionServerCoprocessorHost
 * - testACLZNodeDeletion: Requires direct access to ZKWatcher and ZKUtil
 *
 * @see TestAccessController2 Original test using MiniHBaseCluster
 */
@Category({ SecurityTests.class, MediumTests.class })
public class TestAccessController2_ProcessBased extends ProcessBasedUpgradeTestBase {

  // Helper methods for ProcessBased tests (simplified versions without MasterSyncObserver latches)

  private static void grantGlobal(Admin admin, Connection connection, String user,
      Action... actions) throws Exception {
    admin.grant(new UserPermission(user, Permission.newBuilder().withActions(actions).build()), false);
    // Give time for ACL cache updates across processes
    Thread.sleep(1000);
  }

  private static void revokeGlobal(Admin admin, Connection connection, String user,
      Action... actions) throws Exception {
    admin.revoke(new UserPermission(user, Permission.newBuilder().withActions(actions).build()));
    Thread.sleep(1000);
  }

  private static void grantOnNamespace(Admin admin, Connection connection, String user,
      String namespace, Action... actions) throws Exception {
    admin.grant(new UserPermission(user, Permission.newBuilder(namespace).withActions(actions).build()), false);
    Thread.sleep(1000);
  }

  private static void grantOnTable(Admin admin, Connection connection, String user,
      TableName table, byte[] family, byte[] qualifier, Action... actions) throws Exception {
    admin.grant(new UserPermission(user, Permission.newBuilder(table)
      .withFamily(family).withQualifier(qualifier).withActions(actions).build()), false);
    Thread.sleep(1000);
  }

  private static void revokeFromTable(Admin admin, Connection connection, String user,
      TableName table, byte[] family, byte[] qualifier) throws Exception {
    admin.revoke(new UserPermission(user, Permission.newBuilder(table)
      .withFamily(family).withQualifier(qualifier).build()));
    Thread.sleep(1000);
  }

  private static Table createTable(Admin admin, Connection connection, TableName tableName,
      byte[][] families) throws Exception {
    HTableDescriptor descriptor = new HTableDescriptor(tableName);
    for (byte[] family : families) {
      descriptor.addFamily(new HColumnDescriptor(family));
    }
    admin.createTable(descriptor);
    // Wait for table to be enabled
    for (int i = 0; i < 30; i++) {
      if (admin.isTableEnabled(tableName)) {
        break;
      }
      Thread.sleep(1000);
    }
    return connection.getTable(tableName);
  }

  private static void createTable(Admin admin, Connection connection, HTableDescriptor desc)
      throws Exception {
    admin.createTable(desc);
    // Wait for table to be enabled
    for (int i = 0; i < 30; i++) {
      if (admin.isTableEnabled(desc.getTableName())) {
        break;
      }
      Thread.sleep(1000);
    }
  }

  private static void deleteTable(Admin admin, Connection connection, TableName tableName)
      throws Exception {
    try {
      admin.disableTable(tableName);
    } catch (Exception e) {
      // Table might already be disabled
    }
    admin.deleteTable(tableName);
  }

  private static void createNamespace(Admin admin, Connection connection, NamespaceDescriptor desc)
      throws Exception {
    admin.createNamespace(desc);
  }

  private static void deleteNamespace(Admin admin, String namespace) throws Exception {
    admin.deleteNamespace(namespace);
  }

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestAccessController2_ProcessBased.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestAccessController2_ProcessBased.class);

  private static final byte[] TEST_ROW = Bytes.toBytes("test");
  private static final byte[] TEST_FAMILY = Bytes.toBytes("f");
  private static final byte[] TEST_QUALIFIER = Bytes.toBytes("q");
  private static final byte[] TEST_VALUE = Bytes.toBytes("value");

  private static Configuration staticConf;

  /**
   * The systemUserConnection created here is tied to the system user. In case, you are planning to
   * create AccessTestAction, DON'T use this systemUserConnection as the 'doAs' user gets eclipsed
   * by the system user.
   */
  private static Connection systemUserConnection;

  private final static byte[] Q1 = Bytes.toBytes("q1");
  private final static byte[] value1 = Bytes.toBytes("value1");

  private static byte[] TEST_FAMILY_2 = Bytes.toBytes("f2");
  private static byte[] TEST_ROW_2 = Bytes.toBytes("r2");
  private final static byte[] Q2 = Bytes.toBytes("q2");
  private final static byte[] value2 = Bytes.toBytes("value2");

  private static byte[] TEST_ROW_3 = Bytes.toBytes("r3");

  private static final String TESTGROUP_1 = "testgroup_1";
  private static final String TESTGROUP_2 = "testgroup_2";

  private static User TESTGROUP1_USER1;
  private static User TESTGROUP2_USER1;

  @Rule
  public TableNameTestRule testTable = new TableNameTestRule();
  private String namespace = "testNamespace";
  private String tname = namespace + ":testtable1";
  private TableName tableName = TableName.valueOf(tname);
  private static String TESTGROUP_1_NAME;

  @BeforeClass
  public static void setupBeforeClass() throws Exception {
    staticConf = HBaseConfiguration.create();
    // Up the handlers; this test needs more than usual.
    staticConf.setInt(HConstants.REGION_SERVER_HIGH_PRIORITY_HANDLER_COUNT, 10);
    // Enable security
    SecureTestUtil.enableSecurity(staticConf);
    // Verify enableSecurity sets up what we require
    SecureTestUtil.verifyConfiguration(staticConf);

    TESTGROUP_1_NAME = toGroupEntry(TESTGROUP_1);
    TESTGROUP1_USER1 =
      User.createUserForTesting(staticConf, "testgroup1_user1", new String[] { TESTGROUP_1 });
    TESTGROUP2_USER1 =
      User.createUserForTesting(staticConf, "testgroup2_user2", new String[] { TESTGROUP_2 });
  }

  @Before
  public void setUp() throws Exception {
    // CRITICAL FIX: Update staticConf's ZK configuration to match the dynamic port from base conf
    // staticConf has hardcoded ZK port (21818) from test resources, but conf has correct dynamic port
    String zkQuorum = conf.get(HConstants.ZOOKEEPER_QUORUM);
    String zkPort = conf.get(HConstants.ZOOKEEPER_CLIENT_PORT);
    if (zkQuorum != null) {
      staticConf.set(HConstants.ZOOKEEPER_QUORUM, zkQuorum);
    }
    if (zkPort != null) {
      staticConf.set(HConstants.ZOOKEEPER_CLIENT_PORT, zkPort);
    }

    // Copy security settings from staticConf to the base conf
    // DO NOT replace conf - it already has correct ZK port from base class
    // Copy security coprocessor settings from staticConf
    String masterCoprocs = staticConf.get(CoprocessorHost.MASTER_COPROCESSOR_CONF_KEY);
    if (masterCoprocs != null) {
      conf.set(CoprocessorHost.MASTER_COPROCESSOR_CONF_KEY, masterCoprocs);
    }
    String regionCoprocs = staticConf.get(CoprocessorHost.REGION_COPROCESSOR_CONF_KEY);
    if (regionCoprocs != null) {
      conf.set(CoprocessorHost.REGION_COPROCESSOR_CONF_KEY, regionCoprocs);
    }
    String rsCoprocs = staticConf.get(CoprocessorHost.REGIONSERVER_COPROCESSOR_CONF_KEY);
    if (rsCoprocs != null) {
      conf.set(CoprocessorHost.REGIONSERVER_COPROCESSOR_CONF_KEY, rsCoprocs);
    }
    // Copy other security settings
    conf.setInt(HConstants.REGION_SERVER_HIGH_PRIORITY_HANDLER_COUNT, 10);
    conf.setInt(HFile.FORMAT_VERSION_KEY, 3);
    conf.set(User.HBASE_SECURITY_AUTHORIZATION_CONF_KEY, "true");
    conf.set("hadoop.security.authorization", "false");
    conf.set("hadoop.security.authentication", "simple");
    SecureTestUtil.configureSuperuser(conf);
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    if (systemUserConnection != null) {
      systemUserConnection.close();
    }
  }

  @After
  public void tearDown() throws Exception {
    // Clean the _acl_ table
    try {
      if (admin != null && tableName != null) {
        deleteTable(admin, connection, tableName);
      }
    } catch (TableNotFoundException ex) {
      // Test deleted the table, no problem
      LOG.info("Test deleted table " + tableName);
    }
    try {
      if (admin != null && namespace != null) {
        deleteNamespace(admin, namespace);
      }
    } catch (Exception e) {
      LOG.info("Namespace already deleted: " + namespace);
    }
    // Verify all table/namespace permissions are erased
    if (staticConf != null && tableName != null && namespace != null) {
      assertEquals(0, PermissionStorage.getTablePermissions(staticConf, tableName).size());
      assertEquals(0, PermissionStorage.getNamespacePermissions(staticConf, namespace).size());
    }
  }

  @Test
  public void testCreateWithCorrectOwner_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testCreateWithCorrectOwnerImpl();
  }

  @Test
  public void testCreateWithCorrectOwner_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testCreateWithCorrectOwnerImpl();
  }

  @Test
  public void testCreateWithCorrectOwner_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";
    testCreateWithCorrectOwnerImpl();
  }

  private void testCreateWithCorrectOwnerImpl() throws Exception {
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Wait for the ACL table to become available
    for (int i = 0; i < 60; i++) {
      if (admin.isTableAvailable(PermissionStorage.ACL_TABLE_NAME)) {
        break;
      }
      Thread.sleep(1000);
    }

    systemUserConnection = ConnectionFactory.createConnection(conf);

    // Create a test user
    final User testUser =
      User.createUserForTesting(conf, "TestUser", new String[0]);
    // Grant the test user the ability to create tables
    grantGlobal(admin, connection, testUser.getShortName(), Action.CREATE);

    SecureTestUtil.verifyAllowed(new AccessTestAction() {
      @Override
      public Object run() throws Exception {
        HTableDescriptor desc = new HTableDescriptor(testTable.getTableName());
        desc.addFamily(new HColumnDescriptor(TEST_FAMILY));
        try (Connection connection = ConnectionFactory.createConnection(conf, testUser)) {
          try (Admin admin = connection.getAdmin()) {
            createTable(admin, connection, desc);
          }
        }
        return null;
      }
    }, testUser);

    checkpoint("AFTER_CREATE_TABLE");

    for (int i = 0; i < 60; i++) {
      if (admin.isTableAvailable(testTable.getTableName())) {
        break;
      }
      Thread.sleep(1000);
    }

    // Verify that owner permissions have been granted to the test user on the
    // table just created
    List<UserPermission> perms = PermissionStorage
      .getTablePermissions(staticConf, testTable.getTableName()).get(testUser.getShortName());
    assertNotNull(perms);
    assertFalse(perms.isEmpty());
    // Should be RWXCA
    assertTrue(perms.get(0).getPermission().implies(Permission.Action.READ));
    assertTrue(perms.get(0).getPermission().implies(Permission.Action.WRITE));
    assertTrue(perms.get(0).getPermission().implies(Permission.Action.EXEC));
    assertTrue(perms.get(0).getPermission().implies(Permission.Action.CREATE));
    assertTrue(perms.get(0).getPermission().implies(Permission.Action.ADMIN));
  }

  @Test
  public void testCreateTableWithGroupPermissions_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testCreateTableWithGroupPermissionsImpl();
  }

  @Test
  public void testCreateTableWithGroupPermissions_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testCreateTableWithGroupPermissionsImpl();
  }

  @Test
  public void testCreateTableWithGroupPermissions_AFTER_GRANT() throws Exception {
    upgradeCheckpoint = "AFTER_GRANT";
    testCreateTableWithGroupPermissionsImpl();
  }

  private void testCreateTableWithGroupPermissionsImpl() throws Exception {
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Wait for the ACL table to become available
    for (int i = 0; i < 60; i++) {
      if (admin.isTableAvailable(PermissionStorage.ACL_TABLE_NAME)) {
        break;
      }
      Thread.sleep(1000);
    }

    systemUserConnection = ConnectionFactory.createConnection(conf);

    grantGlobal(admin, connection, TESTGROUP_1_NAME, Action.CREATE);
    checkpoint("AFTER_GRANT");

    try {
      AccessTestAction createAction = new AccessTestAction() {
        @Override
        public Object run() throws Exception {
          HTableDescriptor desc = new HTableDescriptor(testTable.getTableName());
          desc.addFamily(new HColumnDescriptor(TEST_FAMILY));
          try (Connection connection = ConnectionFactory.createConnection(conf)) {
            try (Admin admin = connection.getAdmin()) {
              admin.createTable(desc);
            }
          }
          return null;
        }
      };
      SecureTestUtil.verifyAllowed(createAction, TESTGROUP1_USER1);
      SecureTestUtil.verifyDenied(createAction, TESTGROUP2_USER1);
    } finally {
      revokeGlobal(admin, connection, TESTGROUP_1_NAME, Action.CREATE);
    }
  }

  @Test
  public void testACLTableAccess_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testACLTableAccessImpl();
  }

  @Test
  public void testACLTableAccess_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testACLTableAccessImpl();
  }

  @Test
  public void testACLTableAccess_AFTER_GRANT_USERS() throws Exception {
    upgradeCheckpoint = "AFTER_GRANT_USERS";
    testACLTableAccessImpl();
  }

  private void testACLTableAccessImpl() throws Exception {
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Wait for the ACL table to become available
    for (int i = 0; i < 60; i++) {
      if (admin.isTableAvailable(PermissionStorage.ACL_TABLE_NAME)) {
        break;
      }
      Thread.sleep(1000);
    }

    systemUserConnection = ConnectionFactory.createConnection(conf);

    // Superuser
    User superUser = User.createUserForTesting(conf, "admin", new String[] { "supergroup" });

    // Global users
    User globalRead = User.createUserForTesting(conf, "globalRead", new String[0]);
    User globalWrite = User.createUserForTesting(conf, "globalWrite", new String[0]);
    User globalCreate = User.createUserForTesting(conf, "globalCreate", new String[0]);
    User globalAdmin = User.createUserForTesting(conf, "globalAdmin", new String[0]);
    grantGlobal(admin, connection, globalRead.getShortName(), Action.READ);
    grantGlobal(admin, connection, globalWrite.getShortName(), Action.WRITE);
    grantGlobal(admin, connection, globalCreate.getShortName(), Action.CREATE);
    grantGlobal(admin, connection, globalAdmin.getShortName(), Action.ADMIN);

    // Create namespace for namespace users
    createNamespace(admin, connection, NamespaceDescriptor.create(namespace).build());

    // Namespace users
    User nsRead = User.createUserForTesting(conf, "nsRead", new String[0]);
    User nsWrite = User.createUserForTesting(conf, "nsWrite", new String[0]);
    User nsCreate = User.createUserForTesting(conf, "nsCreate", new String[0]);
    User nsAdmin = User.createUserForTesting(conf, "nsAdmin", new String[0]);
    grantOnNamespace(admin, connection, nsRead.getShortName(), namespace, Action.READ);
    grantOnNamespace(admin, connection, nsWrite.getShortName(), namespace, Action.WRITE);
    grantOnNamespace(admin, connection, nsCreate.getShortName(), namespace, Action.CREATE);
    grantOnNamespace(admin, connection, nsAdmin.getShortName(), namespace, Action.ADMIN);

    // Create table for table users
    try (Table table = createTable(admin, connection, tableName,
        new byte[][] { TEST_FAMILY, TEST_FAMILY_2 })) {
      for (int i = 0; i < 60; i++) {
        if (admin.isTableEnabled(tableName)) {
          break;
        }
        Thread.sleep(1000);
      }

      // Ingesting test data
      table.put(Arrays.asList(new Put(TEST_ROW).addColumn(TEST_FAMILY, Q1, value1),
        new Put(TEST_ROW_2).addColumn(TEST_FAMILY, Q2, value2),
        new Put(TEST_ROW_3).addColumn(TEST_FAMILY_2, Q1, value1)));
    }

    // Table users
    User tableRead = User.createUserForTesting(conf, "tableRead", new String[0]);
    User tableWrite = User.createUserForTesting(conf, "tableWrite", new String[0]);
    User tableCreate = User.createUserForTesting(conf, "tableCreate", new String[0]);
    User tableAdmin = User.createUserForTesting(conf, "tableAdmin", new String[0]);
    grantOnTable(admin, connection, tableRead.getShortName(), tableName, null, null, Action.READ);
    grantOnTable(admin, connection, tableWrite.getShortName(), tableName, null, null, Action.WRITE);
    grantOnTable(admin, connection, tableCreate.getShortName(), tableName, null, null, Action.CREATE);
    grantOnTable(admin, connection, tableAdmin.getShortName(), tableName, null, null, Action.ADMIN);

    checkpoint("AFTER_GRANT_USERS");

    grantGlobal(admin, connection, TESTGROUP_1_NAME, Action.WRITE);
    try {
      // Write tests

      AccessTestAction writeAction = new AccessTestAction() {
        @Override
        public Object run() throws Exception {
          try (Connection conn = ConnectionFactory.createConnection(conf);
            Table t = conn.getTable(PermissionStorage.ACL_TABLE_NAME)) {
            t.put(new Put(TEST_ROW).addColumn(PermissionStorage.ACL_LIST_FAMILY, TEST_QUALIFIER,
              TEST_VALUE));
            return null;
          }
        }
      };

      // All writes to ACL table denied except for GLOBAL WRITE permission and superuser
      SecureTestUtil.verifyDenied(writeAction, globalAdmin, globalCreate, globalRead, TESTGROUP2_USER1);
      SecureTestUtil.verifyDenied(writeAction, nsAdmin, nsCreate, nsRead, nsWrite);
      SecureTestUtil.verifyDenied(writeAction, tableAdmin, tableCreate, tableRead, tableWrite);
      SecureTestUtil.verifyAllowed(writeAction, superUser, globalWrite, TESTGROUP1_USER1);
    } finally {
      revokeGlobal(admin, connection, TESTGROUP_1_NAME, Action.WRITE);
    }

    grantGlobal(admin, connection, TESTGROUP_1_NAME, Action.READ);
    try {
      // Read tests

      AccessTestAction scanAction = new AccessTestAction() {
        @Override
        public Object run() throws Exception {
          try (Connection conn = ConnectionFactory.createConnection(conf);
            Table t = conn.getTable(PermissionStorage.ACL_TABLE_NAME)) {
            ResultScanner s = t.getScanner(new Scan());
            try {
              for (Result r = s.next(); r != null; r = s.next()) {
                // do nothing
              }
            } finally {
              s.close();
            }
            return null;
          }
        }
      };

      // All reads from ACL table denied except for GLOBAL READ and superuser
      SecureTestUtil.verifyDenied(scanAction, globalAdmin, globalCreate, globalWrite, TESTGROUP2_USER1);
      SecureTestUtil.verifyDenied(scanAction, nsCreate, nsAdmin, nsRead, nsWrite);
      SecureTestUtil.verifyDenied(scanAction, tableCreate, tableAdmin, tableRead, tableWrite);
      SecureTestUtil.verifyAllowed(scanAction, superUser, globalRead, TESTGROUP1_USER1);
    } finally {
      revokeGlobal(admin, connection, TESTGROUP_1_NAME, Action.READ);
    }
  }

  @Test
  public void testPostGrantAndRevokeScanAction_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;
    testPostGrantAndRevokeScanActionImpl();
  }

  @Test
  public void testPostGrantAndRevokeScanAction_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;
    testPostGrantAndRevokeScanActionImpl();
  }

  @Test
  public void testPostGrantAndRevokeScanAction_AFTER_WRITE_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE_DATA";
    testPostGrantAndRevokeScanActionImpl();
  }

  @Test
  public void testPostGrantAndRevokeScanAction_AFTER_TABLE_GRANT() throws Exception {
    upgradeCheckpoint = "AFTER_TABLE_GRANT";
    testPostGrantAndRevokeScanActionImpl();
  }

  private void testPostGrantAndRevokeScanActionImpl() throws Exception {
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
      .numRegionServers(1)
      .build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();

    cluster.waitClusterUp();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    // Wait for the ACL table to become available
    for (int i = 0; i < 60; i++) {
      if (admin.isTableAvailable(PermissionStorage.ACL_TABLE_NAME)) {
        break;
      }
      Thread.sleep(1000);
    }

    systemUserConnection = ConnectionFactory.createConnection(conf);

    createNamespace(admin, connection, NamespaceDescriptor.create(namespace).build());
    try (Table table = createTable(admin, connection, tableName,
        new byte[][] { TEST_FAMILY, TEST_FAMILY_2 })) {
      for (int i = 0; i < 60; i++) {
        if (admin.isTableEnabled(tableName)) {
          break;
        }
        Thread.sleep(1000);
      }

      // Ingesting test data
      table.put(Arrays.asList(new Put(TEST_ROW).addColumn(TEST_FAMILY, Q1, value1),
        new Put(TEST_ROW_2).addColumn(TEST_FAMILY, Q2, value2),
        new Put(TEST_ROW_3).addColumn(TEST_FAMILY_2, Q1, value1)));
    }
    checkpoint("AFTER_WRITE_DATA");

    assertEquals(1, PermissionStorage.getTablePermissions(staticConf, tableName).size());

    AccessTestAction scanTableActionForGroupWithTableLevelAccess = new AccessTestAction() {
      @Override
      public Void run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table table = connection.getTable(tableName);) {
          Scan s1 = new Scan();
          try (ResultScanner scanner1 = table.getScanner(s1);) {
            Result[] next1 = scanner1.next(5);
            assertTrue("User having table level access should be able to scan all "
              + "the data in the table.", next1.length == 3);
          }
        }
        return null;
      }
    };

    AccessTestAction scanTableActionForGroupWithFamilyLevelAccess = new AccessTestAction() {
      @Override
      public Void run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table table = connection.getTable(tableName);) {
          Scan s1 = new Scan();
          try (ResultScanner scanner1 = table.getScanner(s1);) {
            Result[] next1 = scanner1.next(5);
            assertTrue("User having column family level access should be able to scan all "
              + "the data belonging to that family.", next1.length == 2);
          }
        }
        return null;
      }
    };

    AccessTestAction scanFamilyActionForGroupWithFamilyLevelAccess = new AccessTestAction() {
      @Override
      public Void run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table table = connection.getTable(tableName);) {
          Scan s1 = new Scan();
          s1.addFamily(TEST_FAMILY_2);
          try (ResultScanner scanner1 = table.getScanner(s1);) {
            scanner1.next();
          }
        }
        return null;
      }
    };

    AccessTestAction scanTableActionForGroupWithQualifierLevelAccess = new AccessTestAction() {
      @Override
      public Void run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table table = connection.getTable(tableName);) {
          Scan s1 = new Scan();
          try (ResultScanner scanner1 = table.getScanner(s1);) {
            Result[] next1 = scanner1.next(5);
            assertTrue("User having column qualifier level access should be able to scan "
              + "that column family qualifier data.", next1.length == 1);
          }
        }
        return null;
      }
    };

    AccessTestAction scanFamilyActionForGroupWithQualifierLevelAccess = new AccessTestAction() {
      @Override
      public Void run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table table = connection.getTable(tableName);) {
          Scan s1 = new Scan();
          s1.addFamily(TEST_FAMILY_2);
          try (ResultScanner scanner1 = table.getScanner(s1);) {
            scanner1.next();
          }
        }
        return null;
      }
    };

    AccessTestAction scanQualifierActionForGroupWithQualifierLevelAccess = new AccessTestAction() {
      @Override
      public Void run() throws Exception {
        try (Connection connection = ConnectionFactory.createConnection(conf);
          Table table = connection.getTable(tableName);) {
          Scan s1 = new Scan();
          s1.addColumn(TEST_FAMILY, Q2);
          try (ResultScanner scanner1 = table.getScanner(s1);) {
            scanner1.next();
          }
        }
        return null;
      }
    };

    // Verify user from a group which has table level access can read all the data and group which
    // has no access can't read any data.
    grantOnTable(admin, connection, TESTGROUP_1_NAME, tableName, null, null, Action.READ);
    checkpoint("AFTER_TABLE_GRANT");
    SecureTestUtil.verifyAllowed(TESTGROUP1_USER1, scanTableActionForGroupWithTableLevelAccess);
    SecureTestUtil.verifyDenied(TESTGROUP2_USER1, scanTableActionForGroupWithTableLevelAccess);

    // Verify user from a group whose table level access has been revoked can't read any data.
    revokeFromTable(admin, connection, TESTGROUP_1_NAME, tableName, null, null);
    SecureTestUtil.verifyDenied(TESTGROUP1_USER1, scanTableActionForGroupWithTableLevelAccess);

    // Verify user from a group which has column family level access can read all the data
    // belonging to that family and group which has no access can't read any data.
    grantOnTable(admin, connection, TESTGROUP_1_NAME, tableName, TEST_FAMILY, null, Permission.Action.READ);
    SecureTestUtil.verifyAllowed(TESTGROUP1_USER1, scanTableActionForGroupWithFamilyLevelAccess);
    SecureTestUtil.verifyDenied(TESTGROUP1_USER1, scanFamilyActionForGroupWithFamilyLevelAccess);
    SecureTestUtil.verifyDenied(TESTGROUP2_USER1, scanTableActionForGroupWithFamilyLevelAccess);
    SecureTestUtil.verifyDenied(TESTGROUP2_USER1, scanFamilyActionForGroupWithFamilyLevelAccess);

    // Verify user from a group whose column family level access has been revoked can't read any
    // data from that family.
    revokeFromTable(admin, connection, TESTGROUP_1_NAME, tableName, TEST_FAMILY, null);
    SecureTestUtil.verifyDenied(TESTGROUP1_USER1, scanTableActionForGroupWithFamilyLevelAccess);

    // Verify user from a group which has column qualifier level access can read data that has this
    // family and qualifier, and group which has no access can't read any data.
    grantOnTable(admin, connection, TESTGROUP_1_NAME, tableName, TEST_FAMILY, Q1, Action.READ);
    SecureTestUtil.verifyAllowed(TESTGROUP1_USER1, scanTableActionForGroupWithQualifierLevelAccess);
    SecureTestUtil.verifyDenied(TESTGROUP1_USER1, scanFamilyActionForGroupWithQualifierLevelAccess);
    SecureTestUtil.verifyDenied(TESTGROUP1_USER1, scanQualifierActionForGroupWithQualifierLevelAccess);
    SecureTestUtil.verifyDenied(TESTGROUP2_USER1, scanTableActionForGroupWithQualifierLevelAccess);
    SecureTestUtil.verifyDenied(TESTGROUP2_USER1, scanFamilyActionForGroupWithQualifierLevelAccess);
    SecureTestUtil.verifyDenied(TESTGROUP2_USER1, scanQualifierActionForGroupWithQualifierLevelAccess);

    // Verify user from a group whose column qualifier level access has been revoked can't read the
    // data having this column family and qualifier.
    revokeFromTable(admin, connection, TESTGROUP_1_NAME, tableName, TEST_FAMILY, Q1);
    SecureTestUtil.verifyDenied(TESTGROUP1_USER1, scanTableActionForGroupWithQualifierLevelAccess);
  }

  // TRANSFORMATION NOTE: testCoprocessorLoading removed
  // Requires direct access to MasterCoprocessorHost via getMaster().getMasterCoprocessorHost()
  // and RegionServerCoprocessorHost via getRegionServer(0).getRegionServerCoprocessorHost().
  // ProcessBasedMiniHBaseCluster does not provide getMaster() or getRegionServer() methods.
  // Original test verified coprocessor loading and environment creation - internal validation
  // with no client-side alternative.

  // TRANSFORMATION NOTE: testACLZNodeDeletion removed
  // Requires direct access to ZKWatcher via getMaster().getZooKeeper() and ZKUtil.checkExists()
  // to verify ZooKeeper ACL node creation/deletion.
  // ProcessBasedMiniHBaseCluster does not expose ZooKeeper state.
  // Original test verified ZK cleanup after table/namespace deletion - internal validation
  // with no client-side alternative.
}
