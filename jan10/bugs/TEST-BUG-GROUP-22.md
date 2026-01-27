# TEST-BUG-GROUP-22: Stale Master Reference After Restart in TestGetReplicationLoad_RestartInjected

## Summary
NullPointerException in `HMaster.getReplicationLoad()` caused by the test using a stale `master` reference after master restart.

## Exception Details
```
java.lang.NullPointerException
    at org.apache.hadoop.hbase.master.HMaster.getReplicationLoad(HMaster.java:4195)
    at org.apache.hadoop.hbase.master.TestGetReplicationLoad_RestartInjected.testGetReplicationMetrics(TestGetReplicationLoad_RestartInjected.java:169)
```

## Root Cause Analysis

### Test Code Issue

The test stores a static reference to the master in `@BeforeClass`:

```java
@BeforeClass
public static void startCluster() throws Exception {
  // ... setup code ...
  master = cluster.getMaster();  // Stores reference to initial master
}
```

After the restart injection at position "after_get_server_name" (lines 107-112), the test continues to use this stale `master` reference:

```java
@Test
public void testGetReplicationMetrics() throws Exception {
  // Line 104: Gets serverName from current master (before restart)
  ServerName serverName = cluster.getMaster(0).getServerName();

  // Lines 107-112: RESTART HAPPENS HERE - old master stops, new master starts
  RestartFramework.at("after_get_server_name")
      .on(cluster)
      .restart("master")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();

  // After restart, test still uses OLD 'master' reference!
  // Lines 140, 149, 159, 169 all use the stale 'master' field

  // Line 159: Reports to OLD stopped master
  master.getMasterRpcServices().regionServerReport(null, request.build());

  // Line 169: Calls getReplicationLoad on OLD stopped master -> NPE
  HashMap<String, List<Pair<ServerName, ReplicationLoadSource>>> replicationLoad =
    master.getReplicationLoad(new ServerName[] { serverName });
}
```

### Why NPE Occurs

In `HMaster.getReplicationLoad()` at line 4195:

```java
public HashMap<String, List<Pair<ServerName, ReplicationLoadSource>>>
  getReplicationLoad(ServerName[] serverNames) {
  // ...
  for (ServerName serverName : serverNames) {
    List<ReplicationLoadSource> replicationLoadSources =
      getServerManager().getLoad(serverName).getReplicationLoadSourceList();  // Line 4195 - NPE here
    // ...
  }
}
```

`getServerManager().getLoad(serverName)` returns `null` because:
1. The `master` object is the OLD stopped master
2. The OLD master's `ServerManager.onlineServers` map doesn't contain the `serverName`
3. When `getLoad()` returns `null`, calling `.getReplicationLoadSourceList()` causes NPE

### Source Code Context

`ServerManager.getLoad()` implementation:
```java
public ServerMetrics getLoad(final ServerName serverName) {
  return this.onlineServers.get(serverName);  // Returns null if not found
}
```

## Classification

**TEST-BUG** - The test code doesn't refresh the `master` reference after restart, causing it to operate on a stale/stopped master object.

## Recommended Fix

After any master restart injection, the test should refresh the `master` reference:

```java
// After restart, refresh the master reference
RestartFramework.at("after_get_server_name")
    .on(cluster)
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// Refresh master reference to the NEW active master
cluster.waitForActiveAndReadyMaster();
master = cluster.getMaster();
// Also get the new serverName if needed
serverName = master.getServerName();
```

## Additional Note

While this is primarily a TEST-BUG, there's also a defensive programming opportunity in `HMaster.getReplicationLoad()`. The method could handle null from `getLoad()` gracefully:

```java
for (ServerName serverName : serverNames) {
  ServerMetrics serverMetrics = getServerManager().getLoad(serverName);
  if (serverMetrics == null) {
    LOG.warn("No server metrics found for {}, skipping", serverName);
    continue;
  }
  List<ReplicationLoadSource> replicationLoadSources = serverMetrics.getReplicationLoadSourceList();
  // ...
}
```

This would make the API more robust against stale ServerName references.

## Test Executions Affected
- TestGetReplicationLoad_RestartInjected.testGetReplicationMetrics at positions:
  - after_get_server_name
  - after_add_first_peer
  - after_add_second_peer

## Files Involved
- Test: `hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestGetReplicationLoad_RestartInjected.java`
- Source: `hbase-server/src/main/java/org/apache/hadoop/hbase/master/HMaster.java:4195`
- Source: `hbase-server/src/main/java/org/apache/hadoop/hbase/master/ServerManager.java:508-510`
