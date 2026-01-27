# TEST-BUG-GROUP-38: Stale ServerName Reference After Regionserver Restart

## Summary

NullPointerException in `TestRSMobFileCleanerChore_RestartInjected.testCleaningAndStoreFileReaderCreatedByOtherThreads` at line 446 caused by using a stale `ServerName` reference after regionserver restart.

## Stack Trace

```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.mob.TestRSMobFileCleanerChore_RestartInjected.testCleaningAndStoreFileReaderCreatedByOtherThreads(TestRSMobFileCleanerChore_RestartInjected.java:446)
```

## Failure Details

- **Test Class**: `org.apache.hadoop.hbase.mob.TestRSMobFileCleanerChore_RestartInjected`
- **Test Method**: `testCleaningAndStoreFileReaderCreatedByOtherThreads`
- **Restart Position**: `after_get_server_name`
- **Restart Target**: `regionserver`
- **Restart Mode**: `GRACEFUL`

## Root Cause Analysis

### Buggy Code Location

**File**: `TestRSMobFileCleanerChore_RestartInjected.java`

**Lines 429-446**:

```java
// Lines 429-437: Get the ServerName hosting the test table's region
ServerName serverName = null;
for (ServerName sn : admin.getRegionServers()) {
  boolean flag = admin.getRegions(sn).stream().anyMatch(
    r -> r.getRegionNameAsString().equals(region.getRegionInfo().getRegionNameAsString()));
  if (flag) {
    serverName = sn;
    break;
  }
}
assertNotNull(serverName);

// Lines 439-444: Restart injection happens HERE
RestartFramework.at("after_get_server_name")
    .on(HTU.getMiniHBaseCluster())
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// Lines 445-446: Use stale serverName - THIS FAILS
RSMobFileCleanerChore cleanerChore =
  HTU.getHBaseCluster().getRegionServer(serverName).getRSMobFileCleanerChore();
```

### Problem Explanation

1. **ServerName includes startcode**: A `ServerName` object contains three components:
   - `hostName`
   - `port`
   - `startcode` (a timestamp when the server started)

2. **Restart changes startcode**: When a regionserver is restarted, it gets a new `startcode` because it starts at a different timestamp.

3. **Stale reference**: After the restart at `after_get_server_name`, the `serverName` variable holds the OLD `ServerName` with the old `startcode`.

4. **Lookup fails**: `HTU.getHBaseCluster().getRegionServer(serverName)` compares `ServerName` objects (which includes `startcode`). Since no currently running server has the old `startcode`, it returns `null`.

5. **NPE**: Calling `.getRSMobFileCleanerChore()` on `null` causes NullPointerException.

## Why This Is a TEST-BUG (Not an HBase Bug)

This is a test code issue, not an HBase source code bug:

1. **HBase behavior is correct**: `getRegionServer(ServerName)` correctly returns `null` when no server matches the exact `ServerName` (including startcode).

2. **Test assumption is incorrect**: The test assumes that `serverName` remains valid across a regionserver restart, which is fundamentally incorrect because ServerName includes startcode.

3. **Pattern match**: This is the same issue as GROUP-9 - stale `ServerName` reference after restart.

## Proposed Fix

Re-fetch the `ServerName` after the restart:

```java
RestartFramework.at("after_get_server_name")
    .on(HTU.getMiniHBaseCluster())
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// FIX: Wait for table availability and re-fetch serverName after restart
HTU.waitTableAvailable(testTable);

// Re-query for the server hosting the region
serverName = null;
for (ServerName sn : admin.getRegionServers()) {
  boolean flag = admin.getRegions(sn).stream().anyMatch(
    r -> r.getRegionNameAsString().equals(region.getRegionInfo().getRegionNameAsString()));
  if (flag) {
    serverName = sn;
    break;
  }
}
assertNotNull(serverName);

RSMobFileCleanerChore cleanerChore =
  HTU.getHBaseCluster().getRegionServer(serverName).getRSMobFileCleanerChore();
```

Alternatively, use index-based access (simpler if there's only one regionserver):

```java
RestartFramework.at("after_get_server_name")
    .on(HTU.getMiniHBaseCluster())
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// FIX: Use index-based access instead of ServerName-based
RSMobFileCleanerChore cleanerChore =
  HTU.getHBaseCluster().getRegionServer(0).getRSMobFileCleanerChore();
```

## Reproduction

```bash
cd /home/shuai/xlab/restart_testing/hbase/hbase-server
mvn surefire:test \
  -Dtest=TestRSMobFileCleanerChore_RestartInjected#testCleaningAndStoreFileReaderCreatedByOtherThreads \
  -Drestart.position=after_get_server_name \
  -Drestart.target=regionserver \
  -Drestart.mode=GRACEFUL \
  -Drestart.tracking.agent=/home/shuai/xlab/restart_testing/RestartTestingFramework/restart-tracking-agent/target/restart-tracking-agent-1.0.0-SNAPSHOT.jar
```

## Conclusion

**Verdict**: TEST-BUG

The test code does not handle the case where `ServerName` changes after a regionserver restart. The fix requires refreshing the `serverName` reference after the restart to get the new `ServerName` with the updated startcode.
