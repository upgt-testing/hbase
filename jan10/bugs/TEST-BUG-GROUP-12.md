# TEST-BUG-GROUP-12: DoNotRetryRegionException - Test doesn't wait for regions to be OPEN after restart during ModifyTableProcedure

## Summary

The test code creates a `MergeTableRegionsProcedure` immediately after a master restart during which a `ModifyTableProcedure` is in progress. The `ModifyTableProcedure` causes regions to transition through CLOSING state, but the test doesn't wait for regions to return to OPEN state before attempting the merge operation.

## Exception

```
org.apache.hadoop.hbase.exceptions.MergeRegionException: org.apache.hadoop.hbase.client.DoNotRetryRegionException: 90101fb1492ba5f554e945902e69b670 is not OPEN; state=CLOSING
	at org.apache.hadoop.hbase.master.assignment.MergeTableRegionsProcedure.checkRegionsToMerge(MergeTableRegionsProcedure.java:152)
	at org.apache.hadoop.hbase.master.assignment.MergeTableRegionsProcedure.<init>(MergeTableRegionsProcedure.java:107)
	...
Caused by: org.apache.hadoop.hbase.client.DoNotRetryRegionException: 90101fb1492ba5f554e945902e69b670 is not OPEN; state=CLOSING
	at org.apache.hadoop.hbase.master.assignment.RegionStateNode.checkOnline(RegionStateNode.java:315)
	at org.apache.hadoop.hbase.master.procedure.AbstractStateMachineTableProcedure.checkOnline(AbstractStateMachineTableProcedure.java:193)
	at org.apache.hadoop.hbase.master.assignment.MergeTableRegionsProcedure.checkRegionsToMerge(MergeTableRegionsProcedure.java:150)
```

## Affected Tests

7 test executions failed with this issue:

1. `TestMergeTableRegionsWhileRSCrash_RestartInjected.test`
2. `TestMergeTableRegionsProcedure_RestartInjected.testMergeDetectsModifyTableProcedure`
3. `TestMasterAbortWhileMergingTable_RestartInjected.test`
4. `TestNamespaceAuditor_RestartInjected.testRegionMerge`
5. `TestIgnoreUnknownFamily_RestartInjected.testMerge`
6. `TestRegionMergeTransactionOnCluster_RestartInjected.testWholesomeMerge`

## Root Cause Analysis

### Test Flow (testMergeDetectsModifyTableProcedure as example)

1. **Line 676**: Test creates a table and gets `regions`:
   ```java
   List<RegionInfo> regions = createTable(tableName);
   ```

2. **Lines 686-689**: Test submits a `ModifyTableProcedure` to change region replication to 2:
   ```java
   TableDescriptor td = TableDescriptorBuilder.newBuilder(admin.getDescriptor(tableName))
     .setRegionReplication(2).build();
   long modifyProcId =
     procExec.submitProcedure(new ModifyTableProcedure(procExec.getEnvironment(), td));
   ```

3. **Lines 690-695**: **Restart injection point** - Master restart at "after_submit_modify_proc":
   ```java
   RestartFramework.at("after_submit_modify_proc")
       .on(UTIL.getMiniHBaseCluster())
       .restart("master")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

4. **Line 696**: Test refreshes `procExec` reference after restart:
   ```java
   procExec = getMasterProcedureExecutor(); // Refresh after master restart
   ```

5. **Lines 700-702**: Test creates `MergeTableRegionsProcedure` using the **stale** `regions` list:
   ```java
   MergeTableRegionsProcedure mergeProcedure = new MergeTableRegionsProcedure(
     procExec.getEnvironment(), regions.toArray(new RegionInfo[0]), false);
   ```

### The Problem

After the master restart at step 3:
- The `ModifyTableProcedure` is recovered and continues executing
- Changing region replication causes regions to transition: OPEN -> CLOSING -> CLOSED -> OPENING -> OPEN
- When the test creates the `MergeTableRegionsProcedure` at step 5, the regions are in CLOSING state
- The `MergeTableRegionsProcedure` constructor calls `checkRegionsToMerge()` which internally calls `checkOnline()`:

```java
// MergeTableRegionsProcedure.java:149-153
try {
  checkOnline(env, ri);
} catch (DoNotRetryRegionException dnrre) {
  throw new MergeRegionException(dnrre);
}
```

```java
// RegionStateNode.java:311-316
public void checkOnline() throws DoNotRetryRegionException {
  RegionInfo ri = getRegionInfo();
  State s = state;
  if (s != State.OPEN) {
    throw new DoNotRetryRegionException(ri.getEncodedName() + " is not OPEN; state=" + s);
  }
  ...
}
```

## Why This is a TEST-BUG (Not a Source Code BUG)

1. **The HBase source code is correct**: The `checkOnline()` method correctly verifies that regions are in OPEN state before allowing a merge. This is a valid precondition check.

2. **The test code is incorrect**: The test doesn't account for the fact that after a master restart during an ongoing `ModifyTableProcedure`, regions may be in transition states (CLOSING, OPENING, etc.).

3. **Similar to Group 5**: This follows the same pattern as Group 5 (DoNotRetryRegionException for split operations), where tests don't wait for regions to be OPEN after a regionserver restart.

## Recommended Fix

Add waiting logic after the restart to ensure regions are back in OPEN state before attempting the merge:

```java
RestartFramework.at("after_submit_modify_proc")
    .on(UTIL.getMiniHBaseCluster())
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
procExec = getMasterProcedureExecutor(); // Refresh after master restart

// FIX: Wait for table to be available (regions to be OPEN)
UTIL.waitTableAvailable(tableName);
// Or alternatively, wait for no regions in transition:
// UTIL.waitUntilNoRegionsInTransition();

// Then proceed with merge
MergeTableRegionsProcedure mergeProcedure = new MergeTableRegionsProcedure(
  procExec.getEnvironment(), regions.toArray(new RegionInfo[0]), false);
```

Alternatively, use the Admin API which has built-in retry logic:
```java
admin.mergeRegionsAsync(regions.get(0).getEncodedNameAsBytes(),
                        regions.get(1).getEncodedNameAsBytes(), false);
```

## Classification

**TEST-BUG**: The test code doesn't properly wait for regions to be in OPEN state after a master restart during which a `ModifyTableProcedure` is actively transitioning regions through various states.
