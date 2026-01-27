# TEST-BUG-GROUP-28: Stale ServerName Reference After Regionserver Restart

## Summary

The test `TestRegionMover2_RestartInjected` stores `ServerName` references before a regionserver restart injection and continues to use them after the restart. Since `ServerName` includes a startcode (timestamp) that changes on restart, `cluster.getRegionServer(serverName)` returns `null`, causing NullPointerException when calling methods on the null reference.

## Affected Tests

1. `TestRegionMover2_RestartInjected.testIsolateMetaOnTheSameSever`
2. `TestRegionMover2_RestartInjected.testIsolateMetaOnTheDifferentServer`

Both use:
- Position: `before_meta_isolate`
- Target: `regionserver`
- Mode: `GRACEFUL`

## Stack Trace

```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.util.TestRegionMover2_RestartInjected.regionIsolationOperation(TestRegionMover2_RestartInjected.java:529)
	at org.apache.hadoop.hbase.util.TestRegionMover2_RestartInjected.testIsolateMetaOnTheDifferentServer(TestRegionMover2_RestartInjected.java:430)
```

## Root Cause Analysis

### Buggy Test Code

In `testIsolateMetaOnTheDifferentServer` (lines 419-431):

```java
@Test
public void testIsolateMetaOnTheDifferentServer() throws Exception {
  MiniHBaseCluster cluster = TEST_UTIL.getHBaseCluster();
  ServerName metaServerSource = findMetaRSLocation();  // Line 422 - BEFORE restart
  ServerName metaServerDestination = findDestinationServerName(metaServerSource);  // Line 423 - BEFORE restart
  RestartFramework.at("before_meta_isolate")
      .on(cluster)
      .restart("regionserver")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();  // Lines 424-429 - RESTART HAPPENS HERE
  regionIsolationOperation(metaServerSource, metaServerDestination, 1, true);  // Line 430 - STALE references used
}
```

Similarly, in `testIsolateMetaOnTheSameSever` (lines 406-417):

```java
@Test
public void testIsolateMetaOnTheSameSever() throws Exception {
  MiniHBaseCluster cluster = TEST_UTIL.getHBaseCluster();
  ServerName metaServerSource = findMetaRSLocation();  // Line 409 - BEFORE restart
  RestartFramework.at("before_meta_isolate")
      .on(cluster)
      .restart("regionserver")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();  // Lines 410-415 - RESTART HAPPENS HERE
  regionIsolationOperation(metaServerSource, metaServerSource, 1, true);  // Line 416 - STALE reference used
}
```

### Failure Point

In `regionIsolationOperation` (lines 522-531):

```java
public void regionIsolationOperation(ServerName sourceServerName,
  ServerName destinationServerName, int numRegionsToIsolate, boolean isolateMetaAlso)
  throws Exception {
  final TableName tableName = TableName.valueOf(name.getMethodName());
  MiniHBaseCluster cluster = TEST_UTIL.getHBaseCluster();
  Admin admin = TEST_UTIL.getAdmin();
  HRegionServer sourceRS = cluster.getRegionServer(sourceServerName);  // Line 528 - returns NULL
  List<HRegion> hRegions = sourceRS.getRegions().stream()  // Line 529 - NPE here!
    .filter(hRegion -> hRegion.getRegionInfo().getTable().equals(tableName))
    .collect(Collectors.toList());
  // ...
}
```

### Why `getRegionServer()` Returns Null

From `MiniHBaseCluster.java` (lines 817-820):

```java
public HRegionServer getRegionServer(ServerName serverName) {
  return hbaseCluster.getRegionServers().stream().map(t -> t.getRegionServer())
    .filter(r -> r.getServerName().equals(serverName)).findFirst().orElse(null);
}
```

`ServerName` in HBase has three components:
- Hostname
- Port
- **Startcode** (timestamp from when the server started)

After a regionserver restart:
- The hostname and port may remain the same (or change)
- The **startcode changes** to the new start timestamp
- `ServerName.equals()` returns `false` because startcodes don't match
- The method returns `null`

## Why This Is a TEST-BUG (Not a Source Code Bug)

1. **Test-Specific Issue**: The problem only occurs because the restart injection point (`before_meta_isolate`) is placed AFTER the `ServerName` references are obtained but BEFORE they are used.

2. **Source Code Behaves Correctly**:
   - `MiniHBaseCluster.getRegionServer(ServerName)` correctly returns `null` for a non-existent server
   - The `ServerName` equality check is correct - servers with different startcodes ARE different servers
   - No null pointer dereference exists in HBase core code

3. **Test Design Flaw**: The test assumes the `ServerName` remains valid across restarts, which violates HBase's `ServerName` contract. This is a common pattern issue in restart-injected tests.

## Proposed Fix

### Option 1: Move Restart Point After Server Name Retrieval (Recommended)

Move the restart injection point to after the `regionIsolationOperation` call begins:

```java
@Test
public void testIsolateMetaOnTheDifferentServer() throws Exception {
  MiniHBaseCluster cluster = TEST_UTIL.getHBaseCluster();
  ServerName metaServerSource = findMetaRSLocation();
  ServerName metaServerDestination = findDestinationServerName(metaServerSource);
  // Restart moved to inside regionIsolationOperation or after it
  regionIsolationOperation(metaServerSource, metaServerDestination, 1, true);
}
```

### Option 2: Re-fetch ServerName After Restart

```java
@Test
public void testIsolateMetaOnTheDifferentServer() throws Exception {
  MiniHBaseCluster cluster = TEST_UTIL.getHBaseCluster();
  ServerName metaServerSource = findMetaRSLocation();
  ServerName metaServerDestination = findDestinationServerName(metaServerSource);
  RestartFramework.at("before_meta_isolate")
      .on(cluster)
      .restart("regionserver")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();
  // Re-fetch server names after restart
  metaServerSource = findMetaRSLocation();
  metaServerDestination = findDestinationServerName(metaServerSource);
  regionIsolationOperation(metaServerSource, metaServerDestination, 1, true);
}
```

### Option 3: Add Null Check in regionIsolationOperation

```java
public void regionIsolationOperation(ServerName sourceServerName,
  ServerName destinationServerName, int numRegionsToIsolate, boolean isolateMetaAlso)
  throws Exception {
  final TableName tableName = TableName.valueOf(name.getMethodName());
  MiniHBaseCluster cluster = TEST_UTIL.getHBaseCluster();
  Admin admin = TEST_UTIL.getAdmin();
  HRegionServer sourceRS = cluster.getRegionServer(sourceServerName);
  if (sourceRS == null) {
    // Server was restarted, find it by address
    sourceRS = findRegionServerByAddress(sourceServerName.getAddress());
    if (sourceRS == null) {
      throw new IllegalStateException("Cannot find region server: " + sourceServerName);
    }
  }
  // ... rest of method
}
```

## Verdict

**TEST-BUG** - The test uses stale `ServerName` references after a regionserver restart. The restart injection point is placed between when the references are obtained and when they are used, but the test doesn't account for the fact that `ServerName` includes a startcode that changes on restart.
