# TEST-BUG-GROUP-5: Test doesn't wait for region to be OPEN after regionserver restart before split operation

## Summary

After regionserver restart, tests immediately try to split regions without waiting for the regions to transition from OPENING to OPEN state. The split procedure requires the region to be OPEN, causing `DoNotRetryRegionException`.

## Exception

```
org.apache.hadoop.hbase.client.DoNotRetryRegionException: 91a2bbf104df97e1a1a3088575d07f4a is not OPEN; state=OPENING
    at org.apache.hadoop.hbase.master.assignment.RegionStateNode.checkOnline(RegionStateNode.java:315)
    at org.apache.hadoop.hbase.master.procedure.AbstractStateMachineTableProcedure.checkOnline(AbstractStateMachineTableProcedure.java:193)
    at org.apache.hadoop.hbase.master.assignment.SplitTableRegionProcedure.<init>(SplitTableRegionProcedure.java:119)
```

## Root Cause

When a regionserver restarts, the regions it was hosting go through a recovery process:
1. Master detects the regionserver restart
2. Master initiates region assignment to the new regionserver
3. Regions transition: CLOSED -> OPENING -> OPEN

The test code doesn't wait for this transition to complete before attempting operations that require the region to be OPEN.

## Affected Tests (12 failures)

All tests in this group follow a similar pattern - they restart a regionserver and then immediately try to split a region:

1. `TestMergesSplitsAddToTracker_RestartInjected.testSplitLoadsFromTracker` (after_file_copy)
2. `TestMaster_RestartInjected.testMasterOpsWhileSplitting` (after_load_table)
3. `TestIgnoreUnknownFamily_RestartInjected.testSplit` (after_get_region_split, after_add_storefile_split)
4. `TestSplitTransactionOnCluster_RestartInjected.testRITStateForRollback` (after_find_splittable_region)
5. `TestEndToEndSplitTransaction_RestartInjected.testCanSplitJustAfterASplit` (after_data_load)
6. `TestRegionPlansWithThrottle_RestartInjected.testExecuteRegionPlansWithThrottling` (after_flush)
7. `TestAsyncTableGetMultiThreaded_RestartInjected.test` (after_table_create_and_data_load)
8. `TestSplitWithCache_RestartInjected.testEvictOnSplit` (before_region_split)
9. `TestSplitWithCache_RestartInjected.testDoesntEvictOnSplit` (before_region_split)
10. `TestRegionMover2_RestartInjected.testWithSplitRegions` (after_unload)
11. `TestRegionReplicaSplit_RestartInjected.testRegionReplicaSplitRegionAssignment` (after_load_numeric_rows)

## Buggy Test Code Pattern

Example from `TestMergesSplitsAddToTracker_RestartInjected.java`:

```java
// Line 263-273: Gets region and restarts regionserver
HRegion region = TEST_UTIL.getHBaseCluster().getRegions(table).get(0);
Pair<StoreFileInfo, String> copyResult = copyFileInTheStoreDir(region);
StoreFileInfo fileInfo = copyResult.getFirst();
String copyName = copyResult.getSecond();

RestartFramework.at("after_file_copy")
    .on(TEST_UTIL.getMiniHBaseCluster())
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// Line 275: PROBLEM - Immediately calls split without waiting for region to be OPEN
split(table, Bytes.toBytes("002"));
```

## Source Code Check

The check happens at `RegionStateNode.checkOnline()` (RegionStateNode.java:311-316):

```java
public void checkOnline() throws DoNotRetryRegionException {
  RegionInfo ri = getRegionInfo();
  State s = state;
  if (s != State.OPEN) {
    throw new DoNotRetryRegionException(ri.getEncodedName() + " is not OPEN; state=" + s);
  }
  // ... additional checks
}
```

This is called from `SplitTableRegionProcedure` constructor (SplitTableRegionProcedure.java:119):

```java
public SplitTableRegionProcedure(...) {
  // ...
  checkOnline(env, getRegion());  // Calls RegionStateNode.checkOnline()
}
```

## Proposed Fix

Add `TEST_UTIL.waitTableAvailable(table)` after the regionserver restart and before the split operation:

```java
RestartFramework.at("after_file_copy")
    .on(TEST_UTIL.getMiniHBaseCluster())
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// FIX: Wait for table to be available after restart
TEST_UTIL.waitTableAvailable(table);

split(table, Bytes.toBytes("002"));
```

This ensures that:
1. Regions have completed the OPENING -> OPEN transition
2. The split procedure can find the region in OPEN state

## Classification

**TEST-BUG**: The test code doesn't account for the time needed for region recovery after a regionserver restart. The HBase core code correctly rejects the split operation when the region is not OPEN - this is expected behavior. The test needs to wait for the cluster to stabilize after the restart.

## Related Groups

This is similar to:
- TEST-BUG-GROUP-3: IndexOutOfBoundsException - test doesn't wait for regions to be available
- TEST-BUG-GROUP-4: FailedServerException - test doesn't wait for table availability
- TEST-BUG-GROUP-13: NoSuchElementException - test doesn't wait for regions after restart
