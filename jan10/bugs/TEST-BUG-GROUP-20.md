# TEST-BUG Report: Group 20 - IOException at MetaTableAccessor.getMetaHTable

## Summary

The test `TestMaster_RestartInjected.testMasterOpsWhileSplitting` fails with `IOException: connection is closed` because it uses a stale HMaster reference after a master restart.

## Failure Details

- **Test Class**: `org.apache.hadoop.hbase.master.TestMaster_RestartInjected`
- **Test Method**: `testMasterOpsWhileSplitting`
- **Restart Position**: `after_table_create`
- **Restart Target**: `master`
- **Restart Mode**: `GRACEFUL`

## Stack Trace

```
java.io.IOException: connection is closed
	at org.apache.hadoop.hbase.MetaTableAccessor.getMetaHTable(MetaTableAccessor.java:236)
	at org.apache.hadoop.hbase.MetaTableAccessor.scanMeta(MetaTableAccessor.java:785)
	at org.apache.hadoop.hbase.MetaTableAccessor.scanMeta(MetaTableAccessor.java:756)
	at org.apache.hadoop.hbase.MetaTableAccessor.scanMeta(MetaTableAccessor.java:717)
	at org.apache.hadoop.hbase.MetaTableAccessor.getTableRegionsAndLocations(MetaTableAccessor.java:638)
	at org.apache.hadoop.hbase.MetaTableAccessor.getTableRegionsAndLocations(MetaTableAccessor.java:590)
	at org.apache.hadoop.hbase.master.TestMaster_RestartInjected.testMasterOpsWhileSplitting(TestMaster_RestartInjected.java:155)
```

## Root Cause Analysis

### Buggy Test Code

**File**: `hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMaster_RestartInjected.java`

```java
@Test
@SuppressWarnings("deprecation")
public void testMasterOpsWhileSplitting() throws Exception {
  MiniHBaseCluster cluster = TEST_UTIL.getHBaseCluster();
  HMaster m = cluster.getMaster();  // Line 135: Store original master reference

  try (Table ht = TEST_UTIL.createTable(TABLENAME, FAMILYNAME)) {
    assertTrue(m.getTableStateManager().isTableState(TABLENAME, TableState.State.ENABLED));
    RestartFramework.at("after_table_create")
      .on(cluster)
      .restart("master")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();  // Lines 139-144: Master restart - 'm' becomes stale!
    TEST_UTIL.loadTable(ht, FAMILYNAME, false);
    // ... more restart points ...
  }

  List<Pair<RegionInfo, ServerName>> tableRegions =
    MetaTableAccessor.getTableRegionsAndLocations(m.getConnection(), TABLENAME);  // Line 155: Uses stale 'm'!
  // ...
}
```

### Analysis

1. **Line 135**: The test stores `HMaster m = cluster.getMaster()` - getting the original master reference
2. **Lines 139-144**: A master restart is injected at position `after_table_create`
3. After restart, the original master `m` is stopped and its connection is closed
4. **Line 155**: The test calls `m.getConnection()` which returns the **closed connection** from the old (stopped) master
5. `MetaTableAccessor.getMetaHTable()` checks if the connection is closed and throws the exception

### MetaTableAccessor Error Check

**File**: `hbase-client/src/main/java/org/apache/hadoop/hbase/MetaTableAccessor.java`

```java
public static Table getMetaHTable(final Connection connection) throws IOException {
  if (connection == null) {
    throw new NullPointerException("No connection");
  } else if (connection.isClosed()) {
    throw new IOException("connection is closed");  // Line 236
  }
  return connection.getTable(TableName.META_TABLE_NAME);
}
```

The `MetaTableAccessor` correctly detects and reports the closed connection. The issue is in the test code, not in the HBase source.

## Why This is a TEST-BUG (Not a Source Code Bug)

1. The HBase source code behaves correctly - `MetaTableAccessor` properly detects and throws an exception when given a closed connection
2. The test code incorrectly stores a master reference before restart and continues to use it after restart
3. This is a common pattern issue in the `_RestartInjected` tests where object references become stale after restarts

## Proposed Fix

The test should refresh the master reference after the restart:

```java
@Test
@SuppressWarnings("deprecation")
public void testMasterOpsWhileSplitting() throws Exception {
  MiniHBaseCluster cluster = TEST_UTIL.getHBaseCluster();
  HMaster m = cluster.getMaster();

  try (Table ht = TEST_UTIL.createTable(TABLENAME, FAMILYNAME)) {
    assertTrue(m.getTableStateManager().isTableState(TABLENAME, TableState.State.ENABLED));
    RestartFramework.at("after_table_create")
      .on(cluster)
      .restart("master")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();
    m = cluster.getMaster();  // FIX: Refresh master reference after restart
    TEST_UTIL.loadTable(ht, FAMILYNAME, false);
    RestartFramework.at("after_load_table")
      .on(cluster)
      .restart("regionserver")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();
  }

  // Now m.getConnection() will return the new master's connection
  List<Pair<RegionInfo, ServerName>> tableRegions =
    MetaTableAccessor.getTableRegionsAndLocations(m.getConnection(), TABLENAME);
  // ...
}
```

Additionally, `m` should also be refreshed after other master restart points in the test (e.g., at `before_split` position on lines 161-166).

## Similar Issue Pattern

This issue is similar to other TEST-BUGs documented in this project:
- **TEST-BUG-GROUP-22**: Stale master reference after restart
- **TEST-BUG-GROUP-10**: Stale ProcedureExecutor reference after master restart
- **TEST-BUG-GROUP-6**: Stale Future reference after master restart

## Affected Test Executions

1. `TestMaster_RestartInjected.testMasterOpsWhileSplitting` with position=`after_table_create`, target=`master`
2. `TestMaster_RestartInjected.testMasterOpsWhileSplitting` with position=`before_split`, target=`master`
3. `TestRegionMergeTransactionOnCluster_RestartInjected.testCleanMergeReference` with position=`after_merge`, target=`master`
