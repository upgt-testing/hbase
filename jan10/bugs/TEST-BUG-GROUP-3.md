# TEST-BUG: Group 3 - IndexOutOfBoundsException in Test Code

## Summary

**Classification**: TEST-BUG

**Impact**: 24 test executions fail with IndexOutOfBoundsException when trying to access regions immediately after a region server restart injection.

## Root Cause Analysis

The test code assumes that regions are immediately available after a region server restart. However, after a restart:
1. The region server stops and restarts
2. The restarted RS needs to re-register with the master
3. The master needs to re-assign regions to the RS
4. During this window, `getRegions(tableName)` returns an empty list

### Problematic Code Pattern

Multiple tests use this pattern:
```java
RestartFramework.at("some_position")
    .on(cluster)
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// BUG: Immediately accessing regions without waiting for assignment
HRegion region = HTU.getHBaseCluster().getRegions(tableName).get(0);  // FAILS!
```

## Reproduction

### Test Method
```
org.apache.hadoop.hbase.mob.TestRSMobFileCleanerChore_RestartInjected#testCleaningAndStoreFileReaderCreatedByOtherThreads
```

### Restart Parameters
- Position: `after_flush`
- Target: `regionserver`
- Mode: `GRACEFUL`

### Command to Reproduce
```bash
mvn surefire:test \
  -Dtest=org.apache.hadoop.hbase.mob.TestRSMobFileCleanerChore_RestartInjected#testCleaningAndStoreFileReaderCreatedByOtherThreads \
  -Drestart.position=after_flush \
  -Drestart.target=regionserver \
  -Drestart.mode=GRACEFUL \
  -Drestart.tracking.agent=/path/to/restart-tracking-agent-1.0.0-SNAPSHOT.jar \
  -pl hbase-server
```

### Error Output
```
java.lang.IndexOutOfBoundsException: Index: 0, Size: 0
    at java.util.ArrayList.rangeCheck(ArrayList.java:659)
    at java.util.ArrayList.get(ArrayList.java:435)
    at org.apache.hadoop.hbase.mob.TestRSMobFileCleanerChore_RestartInjected.testCleaningAndStoreFileReaderCreatedByOtherThreads(TestRSMobFileCleanerChore_RestartInjected.java:402)
```

### Debug Evidence

Added debug logging showed:
```
DEBUG: After restart, regions for table testCleaningAndStoreFileReaderCreatedByOtherThreads = [], size = 0
DEBUG: Region server kingsland,33587,1768174436590 has regions: []
```

This confirms that after the restart, the regions list is empty because region assignment hasn't completed yet.

## Affected Tests (Group 3 - 24 failures)

1. TestTransitRegionStateProcedure_RestartInjected.testRecoveryAndDoubleExecutionMove
2. TestRSMobFileCleanerChore_RestartInjected.testCleaningAndStoreFileReaderCreatedByOtherThreads
3. TestRSMobFileCleanerChore_RestartInjected.testCleaningAndStoreFileReaderCreatedByOtherThreads (different position)
4. TestDirectStoreSplitsMerges_RestartInjected.testCommitDaughterRegionWithFiles
5. TestSerialReplication_RestartInjected.testRemovePeerNothingReplicated
6. TestDirectStoreSplitsMerges_RestartInjected.testSplitStoreDir
7. TestDirectStoreSplitsMerges_RestartInjected.testMergeStoreFile
8. TestDirectStoreSplitsMerges_RestartInjected.testCommitMergedRegion
9. TestMergesSplitsAddToTracker_RestartInjected.testCommitDaughterRegion
10. TestMergesSplitsAddToTracker_RestartInjected.testSplitLoadsFromTracker
11. TestMergesSplitsAddToTracker_RestartInjected.testCommitMergedRegion
12. TestMergesSplitsAddToTracker_RestartInjected.testMergeLoadsFromTracker
13. TestBlockEvictionOnRegionMovement_RestartInjected.testBlockEvictionOnRegionMove
14. TestSplitTransactionOnCluster_RestartInjected.testSplitCompactWithPriority
15. TestSplitWithBlockingFiles_RestartInjected.testSplitIgnoreBlockingFiles
16. TestRemoveFromSerialReplicationPeer_RestartInjected.testRemoveSerialFlag
17. TestExceptionInAssignRegion_RestartInjected.testExceptionInAssignRegion
18. TestSplitWithCache_RestartInjected.testEvictOnSplit (multiple positions)
19. TestSplitWithCache_RestartInjected.testDoesntEvictOnSplit (multiple positions)
20. TestSequenceIdMonotonicallyIncreasing_RestartInjected.testMerge
21. TestTransitRegionStateProcedure_RestartInjected.testRecoveryAndDoubleExecutionUnassignAndAssign

## Proposed Fix

After each restart injection that affects region servers, add a wait for the table to become available:

```java
RestartFramework.at("some_position")
    .on(cluster)
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// FIX: Wait for table to be available after restart
HTU.waitTableAvailable(tableName);

// Now safe to access regions
HRegion region = HTU.getHBaseCluster().getRegions(tableName).get(0);
```

### Fix Verification

Adding `HTU.waitTableAvailable(testTable)` before accessing regions was verified to fix the issue:
```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 33.242 s
```

## Why This Is a TEST-BUG (Not a HBase Bug)

1. **Test-specific behavior**: The underlying HBase code is working correctly. After a restart, regions are properly reassigned, just not instantaneously.

2. **Missing synchronization**: The test code doesn't properly wait for the cluster state to stabilize after restart. The original non-restart-injected tests don't need this wait because no restart happens during the test.

3. **Pattern exists in test utilities**: HBase already provides utilities like `waitTableAvailable()` and `waitUntilAllRegionsAssigned()` for exactly this purpose - waiting for cluster operations to complete.

4. **Consistent with other findings**: Similar issues have been identified in Groups 15, 22, and 37, where test code holds stale references after restart without refreshing them.

## Recommendation

For all restart-injected tests, establish a pattern of:
1. After any node restart, refresh any cached references (admin, master, region servers)
2. After region server restart, wait for tables to be available before accessing regions
3. After master restart, wait for master to be active before performing operations
