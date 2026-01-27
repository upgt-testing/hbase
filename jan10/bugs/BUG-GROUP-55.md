# BUG-GROUP-55: NullPointerException in RegionRemoteProcedureBase.getParent/afterReplay

## Summary

Missing null check in `RegionRemoteProcedureBase.afterReplay()` causes NullPointerException when the parent procedure is no longer in the procedure executor's active procedures map. This can happen when an orphaned child procedure exists after its parent has completed.

## Stack Trace

```java
java.lang.NullPointerException
    at org.apache.hadoop.hbase.master.assignment.RegionRemoteProcedureBase.getParent(RegionRemoteProcedureBase.java:279)
    at org.apache.hadoop.hbase.master.assignment.RegionRemoteProcedureBase.afterReplay(RegionRemoteProcedureBase.java:392)
    at org.apache.hadoop.hbase.master.assignment.RegionRemoteProcedureBase.afterReplay(RegionRemoteProcedureBase.java:58)
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor.lambda$pushProceduresAfterLoad$5(ProcedureExecutor.java:529)
    at java.util.ArrayList.forEach(ArrayList.java:1259)
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor.pushProceduresAfterLoad(ProcedureExecutor.java:528)
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor.loadProcedures(ProcedureExecutor.java:612)
    ...
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor.init(ProcedureExecutor.java:665)
    at org.apache.hadoop.hbase.procedure2.ProcedureTestingUtility.restart(ProcedureTestingUtility.java:132)
    at org.apache.hadoop.hbase.master.procedure.MasterProcedureTestingUtility.restartMasterProcedureExecutor(MasterProcedureTestingUtility.java:83)
    at org.apache.hadoop.hbase.master.assignment.TestRollbackSCP_RestartInjected.testFailAndRollback(TestRollbackSCP_RestartInjected.java:224)
```

## Detailed Failure Flow

### Step-by-Step Analysis

1. **Initial State**:
   - ServerCrashProcedure (SCP) is running with child TransitRegionStateProcedures (TRSPs)
   - Each TRSP has a child RegionRemoteProcedureBase (e.g., OpenRegionProcedure)
   - Procedure hierarchy: `SCP -> TRSP -> OpenRegionProcedure`

2. **Failure Injection**:
   - Test injects `RuntimeException("inject code bug")` in `AssignmentManagerForTest.persistToMeta()`
   - Test sets `ProcedureTestingUtility.setKillAndToggleBeforeStoreUpdateInRollback(true)`
   - This kills the procedure executor **before** store updates during rollback

3. **Inconsistent State Created**:
   - Parent TRSP may complete (marked `isFinished=true` in store)
   - Child OpenRegionProcedure is NOT cleaned up (still `isFinished=false` in store)
   - This creates an **orphaned child** - child exists but parent is finished

4. **During Procedure Loading** (in `ProcedureExecutor.loadProcedures()`):
   ```java
   if (finished) {
       completed.put(proc.getProcId(), ...);  // Parent TRSP goes HERE
   } else {
       procedures.put(proc.getProcId(), proc); // Child OpenRegionProcedure goes HERE
   }
   ```
   - Parent TRSP: `isFinished=true` → goes to `completed` map
   - Child OpenRegionProcedure: `isFinished=false` → goes to `procedures` map

5. **NPE in afterReplay**:
   ```java
   // In pushProceduresAfterLoad():
   runnableList.forEach(p -> {
       p.afterReplay(getEnvironment());  // Called on child
   });

   // In RegionRemoteProcedureBase.afterReplay():
   getParent(env).attachRemoteProc(this);  // getParent() returns NULL!

   // In getParent():
   return env.getMasterServices().getMasterProcedureExecutor()
       .getProcedure(getParentProcId());  // Looks in `procedures` map, NOT `completed`!
   ```

### Key Insight: `completed` vs `procedures` Map

The `getProcedure(procId)` method **only looks in the `procedures` map**, not the `completed` map:

```java
// ProcedureExecutor.java line 1191-1192
public Procedure<TEnvironment> getProcedure(final long procId) {
    return procedures.get(procId);  // Does NOT check `completed` map!
}
```

So when parent is finished (in `completed` map) but child is active (in `procedures` map), `getParent()` returns null.

## Production Crash Scenario

**This bug can occur in production with the following sequence:**

1. **Master is running procedures**: SCP with child TRSPs and grandchild OpenRegionProcedures
2. **Parent TRSP completes**: Marked as finished, persisted to store
3. **Master crashes/killed**: Before child OpenRegionProcedure cleanup is persisted
4. **Master restarts**:
   - Parent TRSP loaded as "finished" → goes to `completed` map
   - Child OpenRegionProcedure loaded as "active" → goes to `procedures` map
5. **NPE during recovery**: Child's `afterReplay()` can't find parent

**Real-world triggers:**
- `kill -9` on master process during procedure execution
- OOM killer terminating master
- Hardware failure / power loss
- Network partition causing ZK session expiry and master abort

## Buggy Code

```java
// Lines 277-280: getParent method
private TransitRegionStateProcedure getParent(MasterProcedureEnv env) {
    return (TransitRegionStateProcedure) env.getMasterServices().getMasterProcedureExecutor()
      .getProcedure(getParentProcId());  // Returns null if parent is in `completed` map!
}

// Lines 391-393: afterReplay method
@Override
protected void afterReplay(MasterProcedureEnv env) {
    getParent(env).attachRemoteProc(this);  // NPE when getParent() returns null!
}
```

## Response to Developer Question

> "TRSP and SCP can not be rolled back. Could you please check more logs before restarting, that why SCP or TRSP is rolled back?"

**Answer**: The parent is NOT being rolled back. The issue is different:

1. **Parent TRSP completes normally** (success or failure) and is marked `isFinished=true`
2. **Child OpenRegionProcedure is orphaned** - it's still in the procedure store with `isFinished=false`
3. This orphan state is created when **master crashes between parent completion and child cleanup**
4. During recovery, parent goes to `completed` map, child goes to `procedures` map
5. Child's `afterReplay()` looks for parent in `procedures` map (via `getProcedure()`), doesn't find it, NPE

The test utility `setKillAndToggleBeforeStoreUpdateInRollback` simulates this crash scenario by killing the executor at specific points.

## Suggested Fix

```java
@Override
protected void afterReplay(MasterProcedureEnv env) {
    TransitRegionStateProcedure parent = getParent(env);
    if (parent != null) {
        parent.attachRemoteProc(this);
    } else {
        // Parent procedure completed - this child is orphaned
        // This can happen if master crashed between parent completion and child cleanup
        LOG.warn("Parent procedure {} not found for child procedure {} ({}). "
            + "Parent may have completed before crash. Marking child as failed.",
            getParentProcId(), getProcId(), this.getClass().getSimpleName());
        setFailure("afterReplay",
            new IllegalStateException("Orphaned child procedure - parent " + getParentProcId() + " not found"));
    }
}
```

## Test Case

- **Test Class**: `TestRollbackSCP_RestartInjected`
- **Test Method**: `testFailAndRollback`
- **Restart Position**: `after_balance`
- **Restart Target**: `regionserver`
- **Restart Mode**: `GRACEFUL`

Note: This is a **rare race condition** - the NPE only occurs when:
1. Parent completes and is persisted as finished
2. Master crashes before child is cleaned up
3. On recovery, orphaned child exists with finished parent

## Category

**BUG** - This is a source code bug in HBase core. The `afterReplay()` method should handle orphaned children gracefully, as this state can occur during crash recovery.
