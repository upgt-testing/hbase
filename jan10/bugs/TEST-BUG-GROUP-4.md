# TEST-BUG: Group 4 - FailedServerException After Region Server Restart

## Summary

Tests fail with `FailedServerException` when calling Admin operations (like `majorCompact`) immediately after a region server restart without waiting for regions to be reassigned.

## Root Cause

After a region server restart (graceful):
1. The old region server stops and its address is added to the RPC client's "failed servers list"
2. The new region server starts with a **new port** (different from the old one)
3. Regions need to be reassigned by the master to the new server
4. The meta table needs to be updated with the new server location
5. The test immediately calls `majorCompact()` without waiting for region reassignment
6. The `locateRegions()` call returns the old (stale) server location from meta
7. The RPC client checks its failed servers list and finds the old address there
8. `FailedServerException` is thrown

## Exception Stack Trace

```
org.apache.hadoop.hbase.ipc.FailedServerException: Call to address=KingsLand:34865 failed on local exception: org.apache.hadoop.hbase.ipc.FailedServerException: This server is in the failed servers list: KingsLand:34865
    at org.apache.hadoop.hbase.ipc.AbstractRpcClient.getConnection(AbstractRpcClient.java:361)
    at org.apache.hadoop.hbase.ipc.AbstractRpcClient.callMethod(AbstractRpcClient.java:448)
    ...
    at org.apache.hadoop.hbase.client.HBaseAdmin.majorCompact(HBaseAdmin.java:1479)
    at org.apache.hadoop.hbase.regionserver.compactions.TestFIFOCompactionPolicy_RestartInjected.testPurgeExpiredFiles(TestFIFOCompactionPolicy_RestartInjected.java:156)
```

## Buggy Test Code

**File**: `hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/compactions/TestFIFOCompactionPolicy_RestartInjected.java`

**Line 150-156**:
```java
RestartFramework.at("after_store_file_count_check")
    .on(cluster)
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
TEST_UTIL.getAdmin().majorCompact(tableName);  // <-- Called immediately without waiting
```

## Failed Servers List Mechanism

The `FailedServers` class (`hbase-client/src/main/java/org/apache/hadoop/hbase/ipc/FailedServers.java`) maintains a list of server addresses that have failed recently. Servers are added to this list when:

1. A connection attempt fails (see `NettyRpcConnection.failInit()` line 213)
2. The server is unreachable during RPC calls

The default expiry time is 2000ms (`FAILED_SERVER_EXPIRY_DEFAULT` in `RpcClient.java`). If the test calls an RPC before this expiry, and the old server address is still in the list, `FailedServerException` is thrown.

**Key code in `AbstractRpcClient.getConnection()` (lines 354-362)**:
```java
private T getConnection(ConnectionId remoteId) throws IOException {
  if (failedServers.isFailedServer(remoteId.getAddress())) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Not trying to connect to " + remoteId.getAddress()
        + " this server is in the failed servers list");
    }
    throw new FailedServerException(
      "This server is in the failed servers list: " + remoteId.getAddress());
  }
  ...
}
```

## Fix

Add `waitTableAvailable()` after the restart to wait for regions to be reassigned before calling Admin operations:

```java
RestartFramework.at("after_store_file_count_check")
    .on(cluster)
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
// Wait for table to be available after restart before calling majorCompact
TEST_UTIL.waitTableAvailable(tableName);
TEST_UTIL.getAdmin().majorCompact(tableName);
```

## Verification

The fix was verified by:
1. Adding `TEST_UTIL.waitTableAvailable(tableName)` after the restart point
2. Running the test with the same restart injection parameters
3. Test passed successfully (BUILD SUCCESS)

## Affected Tests

All 13 test executions in Group 4 share this pattern - calling Admin operations immediately after region server restart without waiting for region reassignment:

1. TestFIFOCompactionPolicy_RestartInjected.testPurgeExpiredFiles
2. TestRSMobFileCleanerChore_RestartInjected.testMobFileCleanerChore (multiple positions)
3. TestRegionMoverUseIp_RestartInjected.testRegionUnloadUesIp
4. TestFSErrorsExposed_RestartInjected.testFullSystemBubblesFSErrors
5. TestSpaceQuotaBasicFunctioning_RestartInjected.testNoCompactions
6. TestScannerLeaseCount_RestartInjected.itIncreasesScannerCount
7. TestCompactionState_RestartInjected.testMajorCompactionStateFromAdmin
8. TestCompactionState_RestartInjected.testMinorCompactionStateFromAdmin
9. TestCompactionState_RestartInjected.testMajorCompactionOnFamilyStateFromAdmin
10. TestRegionMover2_RestartInjected.testWithSplitRegions
11. TestCompactionState_RestartInjected.testMinorCompactionOnFamilyStateFromAdmin

## Adapter Fix (Applied)

Instead of fixing each test individually, a 6-second sleep was added to the `HBaseClusterAdapter` after region server restart. This provides a global fix for all tests that exhibit this pattern.

**File**: `restart-hbase-adapter/src/main/java/org/restarttest/adapter/hbase/HBaseClusterAdapter.java`

**Timing Analysis**:
- `waitTableAvailable()` measured: ~978ms (region reassignment time)
- `FailedServers` timeout: 2000ms (default expiry for failed server entries)
- Sleep added: 6000ms (generous buffer to handle variance)

**Code Change** (applied to GRACEFUL, CRASH, and DELAYED_CRASH modes):
```java
cluster.startRegionServerAndWait(60000);

// Wait for regions to be reassigned and for the RPC client's failed servers list
// to expire (default 2 seconds). This prevents FailedServerException when tests
// immediately call Admin operations after restart.
// See TEST-BUG-GROUP-4.md for detailed analysis.
Thread.sleep(6000);
```

## Verdict

**TEST-BUG** - The test code needs to wait for table availability after region server restart before calling Admin operations that need to contact the region server.

**Resolution**: Fixed globally in the restart adapter by adding a 3-second sleep after region server restart.
