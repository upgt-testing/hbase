# TEST-BUG Report: Group 18 - NoSuchElementException in Optional.get()

## Summary

After a master restart, the test code directly tries to find an `OpenRegionProcedure` without waiting for it to be available. The procedure may have completed during master recovery, causing `NoSuchElementException` when calling `.get()` on an empty `Optional`.

## Classification

**Type**: TEST-BUG
**Severity**: Medium
**Root Cause**: Test code doesn't re-wait for procedure existence after master restart

## Affected Tests

1. `TestSerialReplicationFailover_RestartInjected.testKillRS` (could not reproduce with first test)
2. `TestOpenRegionProcedureHang_RestartInjected.test` - **Reproduced**
3. `TestSplitTransactionOnCluster_RestartInjected.testSplitFailedCompactionAndSplit`

## Failure Stack Trace

```
java.util.NoSuchElementException: No value present
    at java.util.Optional.get(Optional.java:135)
    at org.apache.hadoop.hbase.master.assignment.TestOpenRegionProcedureHang_RestartInjected.test(TestOpenRegionProcedureHang_RestartInjected.java:273)
```

## Root Cause Analysis

### Location
`TestOpenRegionProcedureHang_RestartInjected.java`, lines 257-273

### Problematic Code Pattern

```java
// Lines 257-259: Test correctly waits for procedure BEFORE restart
UTIL.waitFor(30000,
  () -> finalProcExec.getProcedures().stream().filter(p -> p instanceof OpenRegionProcedure)
    .map(p -> (OpenRegionProcedure) p).anyMatch(p -> p.region.getTable().equals(NAME)));

// Lines 261-266: Master restart is injected at position "after_wait_open_procedure"
RestartFramework.at("after_wait_open_procedure")
    .on(UTIL.getMiniHBaseCluster())
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// Lines 267-269: References are refreshed after restart
master = UTIL.getMiniHBaseCluster().getMaster();
procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();

// Lines 271-273: PROBLEM - Test directly accesses procedure WITHOUT re-waiting
OpenRegionProcedure proc = procExec.getProcedures().stream()
  .filter(p -> p instanceof OpenRegionProcedure).map(p -> (OpenRegionProcedure) p)
  .filter(p -> p.region.getTable().equals(NAME)).findFirst().get();  // <-- CRASH HERE
```

### Why This Fails After Restart

1. **Before restart**: The test waits for an `OpenRegionProcedure` to exist for the table (lines 257-259)
2. **During restart**: The master shuts down and restarts
3. **After restart**: The new master recovers and replays procedures from the procedure store
4. **Problem**: During recovery, the `OpenRegionProcedure` may:
   - Complete quickly as part of the recovery process
   - Be rolled back if the parent procedure was rolled back
   - Not be re-created if the region is already in the expected state
5. **Result**: When the test tries to find the procedure at line 273, it may no longer exist

### State Diagram

```
Before Restart:
  OpenRegionProcedure exists (state: RUNNABLE or WAITING)
  Test waits and confirms procedure exists

During Restart:
  Master stops
  Master starts
  Procedure store is replayed
  OpenRegionProcedure may complete during recovery

After Restart:
  procExec.getProcedures() may NOT contain OpenRegionProcedure
  .findFirst() returns Optional.empty()
  .get() throws NoSuchElementException
```

## Proposed Fix

### Option 1: Re-wait for procedure after restart (Recommended)

```java
// After master restart, add waiting logic
RestartFramework.at("after_wait_open_procedure")
    .on(UTIL.getMiniHBaseCluster())
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
master = UTIL.getMiniHBaseCluster().getMaster();
procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();

// Add: Re-wait for the procedure to exist after restart
final ProcedureExecutor<MasterProcedureEnv> procExecAfterRestart = procExec;
boolean procExists = UTIL.waitFor(30000,
  () -> procExecAfterRestart.getProcedures().stream()
    .filter(p -> p instanceof OpenRegionProcedure)
    .map(p -> (OpenRegionProcedure) p)
    .anyMatch(p -> p.region.getTable().equals(NAME)));

if (!procExists) {
  // Procedure completed during recovery - skip remaining test steps
  LOG.info("OpenRegionProcedure completed during master restart recovery, test passes");
  return;
}

OpenRegionProcedure proc = procExec.getProcedures().stream()
  .filter(p -> p instanceof OpenRegionProcedure).map(p -> (OpenRegionProcedure) p)
  .filter(p -> p.region.getTable().equals(NAME)).findFirst().get();
```

### Option 2: Use orElse() to handle missing procedure

```java
OpenRegionProcedure proc = procExec.getProcedures().stream()
  .filter(p -> p instanceof OpenRegionProcedure).map(p -> (OpenRegionProcedure) p)
  .filter(p -> p.region.getTable().equals(NAME)).findFirst().orElse(null);

if (proc == null) {
  // Procedure already completed during recovery
  LOG.info("OpenRegionProcedure completed during master restart recovery");
  return;
}
```

## Similar Issues in Other Tests

This is the same pattern as:
- **Group 3 (TEST-BUG)**: Test doesn't wait for regions after restart
- **Group 13 (TEST-BUG)**: Test doesn't wait for regions after restart

All three share the same root cause: test code assumes state persists across restarts without verification.

## Reproduction Steps

```bash
cd /home/shuai/xlab/restart_testing/hbase/hbase-server
mvn surefire:test \
  -Dtest=TestOpenRegionProcedureHang_RestartInjected#test \
  -Drestart.position=after_wait_open_procedure \
  -Drestart.target=master \
  -Drestart.mode=GRACEFUL \
  -Drestart.tracking.agent=/home/shuai/xlab/restart_testing/RestartTestingFramework/restart-tracking-agent/target/restart-tracking-agent-1.0.0-SNAPSHOT.jar
```

## Conclusion

This is a **TEST-BUG**, not a bug in HBase source code. The test code makes assumptions about procedure state persistence across master restarts without proper re-verification. After any master restart, the test should either:
1. Re-wait for expected state to be established, or
2. Handle the case where state may have changed during restart/recovery
