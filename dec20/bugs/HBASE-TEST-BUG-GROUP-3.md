# HBASE-TEST-BUG-GROUP-3: TestExceptionInUnassignedRegion_RestartInjected uses stale ProcedureExecutor reference after master restart

## Summary

The test `TestExceptionInUnassignedRegion_RestartInjected.testExceptionInUnassignRegion` caches a reference to `ProcedureExecutor` before a master restart injection point, then incorrectly uses this stale reference after the master has been restarted. This causes an `IllegalArgumentException` because the old executor has been stopped and its `lastProcId` has been reset to `-1`.

## Root Cause Analysis

### Test Code Issue

In `TestExceptionInUnassignedRegion_RestartInjected.java`:

```java
// Lines 76-77: Get procedureExecutor reference from OLD master
ProcedureExecutor procedureExecutor =
  UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();

// Lines 79-84: Restart injection point - master is restarted here
RestartFramework.at("after_get_procedure_executor")
  .on(UTIL.getMiniHBaseCluster())
  .restart("master")
  .withIndex(0)
  .withMode(RestartMode.GRACEFUL)
  .execute();

// ... more code ...

// Line 126: Use STALE procedureExecutor reference
long prodId = procedureExecutor.submitProcedure(moveRegionProcedure);
```

### What Happens During Restart

When the master is gracefully restarted at the injection point `after_get_procedure_executor`:

1. The old master's `ProcedureExecutor.stop()` method is called
2. In `ProcedureExecutor.java` line 732, the stop method resets: `lastProcId.set(-1)`
3. The cached `procedureExecutor` variable now points to the stopped executor with `lastProcId = -1`
4. When `submitProcedure()` is called on line 126, the precondition check at line 1079 fails:
   ```java
   Preconditions.checkArgument(lastProcId.get() >= 0);
   ```

### Production Code (Correct Behavior)

The production code in `ProcedureExecutor.submitProcedure()` is working correctly:

```java
// ProcedureExecutor.java:1078-1079
public long submitProcedure(Procedure<TEnvironment> proc, NonceKey nonceKey) {
  Preconditions.checkArgument(lastProcId.get() >= 0);  // <-- This check is correct
  ...
}
```

This check properly validates that the executor is initialized and running before accepting procedure submissions. The production code is not at fault.

## Exception Details

```
java.lang.IllegalArgumentException
    at org.apache.hbase.thirdparty.com.google.common.base.Preconditions.checkArgument(Preconditions.java:127)
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor.submitProcedure(ProcedureExecutor.java:1079)
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor.submitProcedure(ProcedureExecutor.java:921)
    at org.apache.hadoop.hbase.master.assignment.TestExceptionInUnassignedRegion_RestartInjected.testExceptionInUnassignRegion(TestExceptionInUnassignedRegion_RestartInjected.java:126)
```

## Affected Test Executions

This bug affects all 24 test executions in Group 3 with positions:
- `after_get_procedure_executor` (master restart)
- `after_create_procedure` (master restart)
- `after_set_procedure_on_node` (master restart)
- Other master restart positions that occur after the procedureExecutor reference is cached

## Proposed Fix

The test should not cache the `ProcedureExecutor` reference before restart injection points that restart the master. Instead, it should obtain a fresh reference after any master restart.

### Option 1: Get executor reference after all master restart points

Move the `procedureExecutor` assignment to after the last master restart injection point:

```java
@Test
public void testExceptionInUnassignRegion() {
  // Don't cache procedureExecutor here

  RestartFramework.at("after_get_procedure_executor")
    .on(UTIL.getMiniHBaseCluster())
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

  // ... find rs and region code ...

  TransitRegionStateProcedure moveRegionProcedure = TransitRegionStateProcedure.reopen(
    UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor().getEnvironment(), hri);

  RestartFramework.at("after_create_procedure")
    .on(UTIL.getMiniHBaseCluster())
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

  // ... set procedure on node ...

  RestartFramework.at("after_set_procedure_on_node")
    .on(UTIL.getMiniHBaseCluster())
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

  // Get FRESH executor reference after all master restart points
  ProcedureExecutor procedureExecutor =
    UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();

  long prodId = procedureExecutor.submitProcedure(moveRegionProcedure);

  // ...
}
```

### Option 2: Never cache - always get fresh reference

Replace all uses of cached `procedureExecutor` with fresh calls:

```java
long prodId = UTIL.getMiniHBaseCluster().getMaster()
    .getMasterProcedureExecutor().submitProcedure(moveRegionProcedure);
```

## Classification

**TEST-BUG**: The test code incorrectly caches a reference to `ProcedureExecutor` that becomes stale after a master restart injection. The production code is correct in validating the executor state before accepting procedures.
