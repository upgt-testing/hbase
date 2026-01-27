# TEST-BUG: Stale ProcedureExecutor Reference After Master Restart

## Summary

The `_RestartInjected` tests store a `ProcedureExecutor` reference from the master before restart injection, but fail to refresh this reference after the master is restarted. When the test tries to call `submitProcedure()` on the stale reference, it fails because the old (stopped) master's ProcedureExecutor has `lastProcId = -1`.

## Exception

```
java.lang.IllegalArgumentException
    at org.apache.hbase.thirdparty.com.google.common.base.Preconditions.checkArgument(Preconditions.java:127)
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor.submitProcedure(ProcedureExecutor.java:1079)
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor.submitProcedure(ProcedureExecutor.java:921)
```

## Root Cause

### The Check in ProcedureExecutor.submitProcedure()

In `ProcedureExecutor.java` (line 1096):
```java
public long submitProcedure(Procedure<TEnvironment> proc, NonceKey nonceKey) {
    Preconditions.checkArgument(lastProcId.get() >= 0);  // FAILS when lastProcId is -1
    ...
}
```

### How lastProcId Becomes -1

The `lastProcId` is initialized to -1:
```java
private final AtomicLong lastProcId = new AtomicLong(-1);  // line 253
```

And is reset to -1 when the ProcedureExecutor is stopped (line 749):
```java
// reset the in-memory state for testing
completed.clear();
rollbackStack.clear();
procedures.clear();
nonceKeysToProcIdsMap.clear();
scheduler.clear();
lastProcId.set(-1);  // Reset during stop()
```

### The Test Bug

The tests get a `ProcedureExecutor` reference before the restart:
```java
ProcedureExecutor<MasterProcedureEnv> procExec =
    UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
```

After restart injection:
```java
RestartFramework.at("after_get_regions_backoff")
    .on(UTIL.getMiniHBaseCluster())
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
```

The test uses the **stale** reference:
```java
procExec.submitProcedure(proc);  // Uses old/stopped master's procExec!
```

## Affected Tests

All 8 test executions in Group 10:

1. **TestReopenTableRegionsProcedureBatchBackoff_RestartInjected.testRegionBatchBackoff**
   - Position: `after_get_regions_backoff`

2. **TestProcedureWaitAndWake_RestartInjected.testPeerProcedure** (4 failures)
   - Positions: `after_get_procedure_executor`, `after_create_barrier`, `after_create_procedures`, `after_submit_first_procedure`

3. **TestSplitTransactionOnCluster_RestartInjected.testSplitFailedCompactionAndSplit**
   - Position: `after_data_insert`

4. **TestModifyPeerProcedureRetryBackoff_RestartInjected.test**
   - Position: `after_get_proc_exec`

5. **TestSplitRegionWhileRSCrash_RestartInjected.test**
   - Position: `after_create_split_procedure`

## Proposed Fix

After each restart injection that restarts the master, the test must refresh the `ProcedureExecutor` reference:

### Before (Buggy):
```java
ProcedureExecutor<MasterProcedureEnv> procExec =
    UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();

RestartFramework.at("after_get_regions_backoff")
    .on(UTIL.getMiniHBaseCluster())
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// BUG: Using stale procExec reference
procExec.submitProcedure(proc);
```

### After (Fixed):
```java
ProcedureExecutor<MasterProcedureEnv> procExec =
    UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();

RestartFramework.at("after_get_regions_backoff")
    .on(UTIL.getMiniHBaseCluster())
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// FIX: Refresh the reference to use new master's procExec
procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();

procExec.submitProcedure(proc);
```

## Evidence

In the same test file `TestReopenTableRegionsProcedureBatchBackoff_RestartInjected.java`, the method `testRegionBatchNoBackoff` correctly refreshes the reference:

```java
RestartFramework.at("after_get_regions_nobackoff")
    .on(UTIL.getMiniHBaseCluster())
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();  // CORRECT!
```

While `testRegionBatchBackoff` does NOT refresh the reference:
```java
RestartFramework.at("after_get_regions_backoff")
    .on(UTIL.getMiniHBaseCluster())
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
// Missing: procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
procExec.submitProcedure(proc);  // BUG: uses stale reference
```

## Verdict

**TEST-BUG**: The `_RestartInjected` tests were incorrectly written. They obtain a `ProcedureExecutor` reference from the master, then inject a master restart, but fail to refresh the reference before using it. This is not a bug in HBase source code - it's a test authoring error.

## Classification

- **Type**: TEST-BUG
- **Severity**: Low (affects only test code)
- **Impact**: 8 test failures
- **Fix Complexity**: Trivial - add `procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();` after each master restart
