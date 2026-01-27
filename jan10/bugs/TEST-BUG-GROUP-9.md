# TEST-BUG-GROUP-9: NullPointerException due to Stale ServerName Reference in TestRSMobFileCleanerChore

## Summary

After regionserver restart, the test uses a stale `ServerName` reference to look up a region server. `ServerName` includes a startcode (timestamp) that changes on restart, causing the lookup to return null and resulting in a NullPointerException.

## Failure Details

- **Test Class**: `org.apache.hadoop.hbase.mob.TestRSMobFileCleanerChore_RestartInjected`
- **Test Method**: `testMobFileCleanerChore`
- **Failure Line**: 264
- **Exception**: `java.lang.NullPointerException`
- **Reproduced With**:
  - Position: `after_get_server_regions`
  - Target: `regionserver`
  - Mode: `GRACEFUL`

## Stack Trace

```
java.lang.NullPointerException
    at org.apache.hadoop.hbase.mob.TestRSMobFileCleanerChore_RestartInjected.testMobFileCleanerChore(TestRSMobFileCleanerChore_RestartInjected.java:264)
```

## Root Cause Analysis

### Test Code Flow

1. **Lines 242-256**: The test finds a `ServerName` that hosts regions for the test table:
```java
ServerName serverUsed = null;
List<RegionInfo> serverRegions = null;
for (ServerName sn : admin.getRegionServers()) {
  serverRegions = admin.getRegions(sn);
  if (serverRegions != null && serverRegions.size() > 0) {
    serverRegions = serverRegions.stream().filter(r -> r.getTable() == table.getName())
      .collect(Collectors.toList());
    if (serverRegions.size() > 0) {
      serverUsed = sn;  // <-- stores ServerName with current startcode
    }
    break;
  }
}
```

2. **Lines 257-262**: Restart is injected at "after_get_server_regions":
```java
RestartFramework.at("after_get_server_regions")
    .on(HTU.getMiniHBaseCluster())
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
```

3. **Line 264**: The test uses the stale `serverUsed` to look up the region server:
```java
chore = HTU.getMiniHBaseCluster().getRegionServer(serverUsed).getRSMobFileCleanerChore();
//                                               ^^^^^^^^^^ stale ServerName
//                                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ returns null
```

### Why `getRegionServer(ServerName)` Returns Null

The `MiniHBaseCluster.getRegionServer(ServerName)` method (MiniHBaseCluster.java:817-820):
```java
public HRegionServer getRegionServer(ServerName serverName) {
  return hbaseCluster.getRegionServers().stream().map(t -> t.getRegionServer())
    .filter(r -> r.getServerName().equals(serverName)).findFirst().orElse(null);
}
```

`ServerName` in HBase includes three components:
- Hostname
- Port
- **Startcode** (a timestamp from when the server started)

After a regionserver restart:
- The hostname and port may be the same
- The **startcode changes** to the new start time
- `ServerName.equals()` returns `false` because startcodes don't match
- The method returns `null`
- Calling `.getRSMobFileCleanerChore()` on `null` throws NPE

## Why This Is a TEST-BUG

This is a **TEST-BUG**, not a bug in HBase source code, because:

1. The test stores a `ServerName` reference before a restart
2. After restart, `ServerName` becomes stale because the startcode changed
3. The test code assumes `serverUsed` is still valid after restart, which is incorrect
4. In production, clients would discover the new server via meta/zookeeper lookups, not cached references

## Proposed Fix

The test should re-query for the server after the restart injection point:

```java
RestartFramework.at("after_get_server_regions")
    .on(HTU.getMiniHBaseCluster())
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// Fix: Re-find the server that hosts regions for the test table after restart
serverUsed = null;
for (ServerName sn : admin.getRegionServers()) {
  serverRegions = admin.getRegions(sn);
  if (serverRegions != null && serverRegions.size() > 0) {
    serverRegions = serverRegions.stream().filter(r -> r.getTable() == table.getName())
      .collect(Collectors.toList());
    if (serverRegions.size() > 0) {
      serverUsed = sn;  // Now uses new ServerName with correct startcode
      break;
    }
  }
}

chore = HTU.getMiniHBaseCluster().getRegionServer(serverUsed).getRSMobFileCleanerChore();
```

Alternatively, the test could wait for table availability and re-query:

```java
RestartFramework.at("after_get_server_regions")
    .on(HTU.getMiniHBaseCluster())
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// Wait for regions to be reassigned after restart
HTU.waitTableAvailable(table.getName());

// Re-find the server (same lookup logic as above)
```

## Similar Issues

This pattern is similar to:
- **Group 11**: ZooKeeper NoNodeException due to stale ServerName
- **Group 21**: NotServingRegionException due to stale HRegionServer reference
- **Group 34**: RegionMovedException due to stale destServer reference

All involve test code storing server references that become stale after restart.

## Test Executions Affected

All 9 test executions in this group are affected by this same root cause, occurring at different restart positions:
- `after_wait_archive`
- `after_get_server_regions`
- `after_mob_cleaner`
- `after_mob_file_check_cleaner`
- `after_scan`
- `after_generate_mob_file`
- `after_mob_file_check_generate`
- `after_extra_file_check`
- `after_wait_archive_second`

## Conclusion

This is a **TEST-BUG** where test code uses stale `ServerName` references after regionserver restart. The fix is to re-query for server references after each restart injection point that occurs between getting the `ServerName` and using it.
