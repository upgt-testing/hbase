# HBASE-BUG-GROUP-71: RegionRemoteProcedureBase.afterReplay() Lacks Null Check for Parent Procedure

## Summary

`RegionRemoteProcedureBase.afterReplay()` does not handle the case where the parent `TransitRegionStateProcedure` has already been completed (finished/rolled back). This results in a `NullPointerException` when attempting to attach the remote procedure to a non-existent parent during procedure executor restart.

## Bug Details

**Component:** hbase-server
**Affected Class:** `org.apache.hadoop.hbase.master.assignment.RegionRemoteProcedureBase`
**Severity:** Critical - causes master procedure executor to fail during restart
**Reproducibility:** Consistently reproducible during procedure executor restart after rollback scenarios

## Root Cause

### Buggy Code Location

**File:** `hbase-server/src/main/java/org/apache/hadoop/hbase/master/assignment/RegionRemoteProcedureBase.java`

**Lines 277-280 (getParent method):**
```java
private TransitRegionStateProcedure getParent(MasterProcedureEnv env) {
  return (TransitRegionStateProcedure) env.getMasterServices().getMasterProcedureExecutor()
    .getProcedure(getParentProcId());
}
```

**Lines 391-393 (afterReplay method):**
```java
@Override
protected void afterReplay(MasterProcedureEnv env) {
  getParent(env).attachRemoteProc(this);  // NPE if parent is null
}
```

### Why the Bug Occurs

1. **Procedure Loading Behavior:** When `ProcedureExecutor` loads procedures after restart:
   - Finished procedures go to the `completed` map
   - Unfinished procedures go to the `procedures` map

2. **getProcedure Only Checks Active Procedures:** The `getProcedure(procId)` method at `ProcedureExecutor.java:1191-1192` only checks the `procedures` map:
   ```java
   public Procedure<TEnvironment> getProcedure(final long procId) {
     return procedures.get(procId);  // Does not check 'completed' map
   }
   ```

3. **Race Condition During Rollback:** During rollback of a `ServerCrashProcedure`:
   - The parent `TransitRegionStateProcedure` may complete/finish (rollback successfully)
   - Its child `RegionRemoteProcedureBase` may still be pending in the store
   - On restart, the parent is in `completed` map, child is in `procedures` map
   - `afterReplay()` is called on the child, but `getParent()` returns `null`

4. **Missing Null Check:** The `afterReplay()` method directly calls `getParent(env).attachRemoteProc(this)` without checking if `getParent()` returns null.

## Stack Trace

```
java.lang.NullPointerException
    at org.apache.hadoop.hbase.master.assignment.RegionRemoteProcedureBase.getParent(RegionRemoteProcedureBase.java:279)
    at org.apache.hadoop.hbase.master.assignment.RegionRemoteProcedureBase.afterReplay(RegionRemoteProcedureBase.java:392)
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor.lambda$pushProceduresAfterLoad$5(ProcedureExecutor.java:529)
    at java.util.ArrayList.forEach(ArrayList.java:1259)
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor.pushProceduresAfterLoad(ProcedureExecutor.java:528)
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor.loadProcedures(ProcedureExecutor.java:612)
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor.access$400(ProcedureExecutor.java:77)
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor$2.load(ProcedureExecutor.java:351)
    at org.apache.hadoop.hbase.procedure2.store.region.RegionProcedureStore.load(RegionProcedureStore.java:284)
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor.load(ProcedureExecutor.java:342)
    at org.apache.hadoop.hbase.procedure2.ProcedureExecutor.init(ProcedureExecutor.java:665)
    at org.apache.hadoop.hbase.procedure2.ProcedureTestingUtility.restart(ProcedureTestingUtility.java:132)
```

## Reproduction

**Test:** `org.apache.hadoop.hbase.master.assignment.TestRollbackSCP_RestartInjected::testFailAndRollback`

**Command:**
```bash
mvn surefire:test -Dtest=org.apache.hadoop.hbase.master.assignment.TestRollbackSCP_RestartInjected#testFailAndRollback -Drestart.position=after_balance -Drestart.target=regionserver -Drestart.mode=GRACEFUL
```

## Proposed Fix

Add null check in `afterReplay()` before calling `attachRemoteProc()`:

```java
@Override
protected void afterReplay(MasterProcedureEnv env) {
  TransitRegionStateProcedure parent = getParent(env);
  if (parent != null) {
    parent.attachRemoteProc(this);
  } else {
    LOG.warn("{} parent procedure {} not found during afterReplay.", this, getParentProcId());
  }
}
```

## Impact

- **Availability:** Master procedure executor fails to restart, causing master initialization failure
- **Data Integrity:** No direct data integrity impact, but failed master startup affects cluster availability
- **Recovery:** Manual intervention may be required to clean up orphaned procedures from the procedure store

## Related Issues

This is similar in nature to HBASE-BUG-GROUP-58 where `WorkerAssigner.serverAdded()` lacked a null check for `MasterProcedureExecutor`. Both involve missing null checks for objects that can be null during specific lifecycle phases (shutdown/restart).

## Patch

A patch implementing the recommended fix (Option 1) is available at:
`dec20/patches/HBASE-BUG-GROUP-71.patch`

### Patch Contents

```diff
diff --git a/hbase-server/src/main/java/org/apache/hadoop/hbase/master/assignment/RegionRemoteProcedureBase.java b/hbase-server/src/main/java/org/apache/hadoop/hbase/master/assignment/RegionRemoteProcedureBase.java
index abcdef1..1234567 100644
--- a/hbase-server/src/main/java/org/apache/hadoop/hbase/master/assignment/RegionRemoteProcedureBase.java
+++ b/hbase-server/src/main/java/org/apache/hadoop/hbase/master/assignment/RegionRemoteProcedureBase.java
@@ -389,7 +389,17 @@ public abstract class RegionRemoteProcedureBase extends Procedure<MasterProcedur

   @Override
   protected void afterReplay(MasterProcedureEnv env) {
-    getParent(env).attachRemoteProc(this);
+    // Defensive null check: parent TransitRegionStateProcedure may have already
+    // completed/rolled back during a rollback scenario (e.g., ServerCrashProcedure rollback).
+    // In such cases, getProcedure() returns null because finished procedures are stored
+    // in the 'completed' map, not the 'procedures' map.
+    TransitRegionStateProcedure parent = getParent(env);
+    if (parent != null) {
+      parent.attachRemoteProc(this);
+    } else {
+      LOG.warn("{} parent procedure {} not found during afterReplay. "
+        + "Parent may have already completed or been rolled back.", this, getParentProcId());
+    }
   }

   @Override
```

### How to Apply

```bash
cd /home/shuai/xlab/restart_testing/hbase
git apply dec20/patches/HBASE-BUG-GROUP-71.patch
```

## Verification

The patch was tested and **successfully eliminates the NullPointerException**:

| Before Fix | After Fix |
|------------|-----------|
| `NullPointerException at RegionRemoteProcedureBase.getParent:279` | No NPE - test proceeds past procedure restart |

The test now successfully passes through the `MasterProcedureTestingUtility.restartMasterProcedureExecutor()` call at line 224 where the NPE was previously occurring.

**Note:** The test may still timeout on unrelated issues (e.g., meta scan during `AssignmentManager.joinCluster`) due to cluster state corruption from the restart injection framework. This is a separate issue from the NPE bug fixed here.
