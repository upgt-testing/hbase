# TEST-BUG-GROUP-6: Stale Future Reference After Master Restart

## Summary

The `_RestartInjected` test stores a `Future<byte[]>` reference from `am.moveAsync()` before a master restart, but fails to handle the case where the `Future` becomes stale after the master is restarted. When `future.get()` is called, it throws "The Master is Aborting" because the underlying `ProcedureExecutor` from the old (stopped) master is no longer running.

## Affected Tests

- `TestReportOnlineRegionsRace_RestartInjected.testRace`
- `TestReportRegionStateTransitionFromDeadServer_RestartInjected.test`
- `TestRaceBetweenSCPAndTRSP_RestartInjected.test`
- `TestSCPGetRegionsRace_RestartInjected.test`
- `TestReportRegionStateTransitionRetry_RestartInjected.testRetryOnClose`

## Root Cause Analysis

### Failure Chain

1. Test calls `am.moveAsync(...)` to schedule a region move at line 199:
   ```java
   Future<byte[]> future =
     am.moveAsync(new RegionPlan(region, rsn.getRegionLocation(), rsn.getRegionLocation()));
   ```

2. The restart framework injects a master restart at position `after_schedule_trsp` (lines 200-205):
   ```java
   RestartFramework.at("after_schedule_trsp")
       .on(UTIL.getMiniHBaseCluster())
       .restart("master")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

3. The test correctly refreshes `master`, `am`, and `procExec` after the restart (lines 206-208):
   ```java
   master = UTIL.getMiniHBaseCluster().getMaster(); // Refresh after master restart
   am = master.getAssignmentManager(); // Refresh after master restart
   procExec = master.getMasterProcedureExecutor(); // Refresh after master restart
   ```

4. **BUT the `future` variable is never refreshed** - it still holds a reference to the `ProcedureFuture` that was created with the OLD (now stopped) master's `ProcedureExecutor`.

5. At line 297, `future.get()` is called:
   ```java
   future.get();
   ```

6. Inside `ProcedureFuture.get()`, it calls `waitForProcedureToComplete()` which checks `procExec.isRunning()`:
   ```java
   // ProcedureSyncWait.java lines 156-171
   waitFor(procExec.getEnvironment(), timeout, "pid=" + proc.getProcId(),
     new ProcedureSyncWait.Predicate<Boolean>() {
       @Override
       public Boolean evaluate() throws IOException {
         if (!procExec.isRunning()) {
           return true;  // EXIT the wait loop early
         }
         // ...
       }
     });
   if (!procExec.isRunning()) {
     throw new IOException("The Master is Aborting");  // <-- This is thrown!
   }
   ```

7. Since the old `procExec.isRunning()` returns `false` (because the old master is stopped), the code throws `IOException("The Master is Aborting")`.

### Why This Is A Test Bug

The test code assumes that a `Future` returned by `am.moveAsync()` will remain valid across master restarts. However, this `Future` is tightly coupled to the `ProcedureExecutor` instance from the master that created it. When the master is restarted:

- The old master's `ProcedureExecutor` is stopped (`isRunning()` returns `false`)
- A new master starts with a new `ProcedureExecutor`
- The procedure may continue on the new master (after recovery), but the old `Future` has no connection to it

## Buggy Test Code

```java
// File: TestReportOnlineRegionsRace_RestartInjected.java
// Line 199
Future<byte[]> future =
  am.moveAsync(new RegionPlan(region, rsn.getRegionLocation(), rsn.getRegionLocation()));

// Lines 200-208: Master restart happens here
RestartFramework.at("after_schedule_trsp")
    .on(UTIL.getMiniHBaseCluster())
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
master = UTIL.getMiniHBaseCluster().getMaster(); // Refresh after master restart
am = master.getAssignmentManager(); // Refresh after master restart
procExec = master.getMasterProcedureExecutor(); // Refresh after master restart
// NOTE: `future` is NOT refreshed!

// Line 297: This fails because `future` is tied to the old stopped master
future.get();  // Throws IOException: The Master is Aborting
```

## Potential Fix

### Option 1: Skip future.get() After Master Restart

If a master restart occurred after obtaining the `future`, skip the `future.get()` call and instead wait for the procedure to complete on the new master:

```java
Future<byte[]> future =
  am.moveAsync(new RegionPlan(region, rsn.getRegionLocation(), rsn.getRegionLocation()));

boolean masterRestarted = RestartFramework.at("after_schedule_trsp")
    .on(UTIL.getMiniHBaseCluster())
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

master = UTIL.getMiniHBaseCluster().getMaster();
am = master.getAssignmentManager();
procExec = master.getMasterProcedureExecutor();

// ... rest of test ...

// Instead of future.get(), wait for region to be available
if (masterRestarted) {
  // Wait for the procedure to complete on the new master
  UTIL.waitFor(30000, () -> {
    RegionStateNode rsn = am.getRegionStates().getRegionStateNode(region);
    return rsn != null && rsn.isInState(RegionState.State.OPEN);
  });
} else {
  future.get();
}
```

### Option 2: Track Procedure ID and Get New Future

After restart, find the procedure by ID on the new master and create a new way to wait for it:

```java
long procId = proc.getProcId();  // Save the procedure ID before restart

// After master restart...
master = UTIL.getMiniHBaseCluster().getMaster();
procExec = master.getMasterProcedureExecutor();

// Wait for procedure to complete on new master
UTIL.waitFor(30000, () -> procExec.isFinished(procId));
```

### Option 3: Wrap Future.get() with Exception Handling

```java
try {
  future.get();
} catch (ExecutionException e) {
  if (e.getCause() instanceof IOException &&
      e.getCause().getMessage().contains("Master is Aborting")) {
    // Master was restarted, wait for region to be available instead
    UTIL.waitTableAvailable(NAME);
  } else {
    throw e;
  }
}
```

## Impact

This is a test-only bug that does not affect production HBase code. The `ProcedureSyncWait.waitForProcedureToComplete()` correctly throws an exception when the master is stopped - this is expected behavior to prevent waiting on a stopped system indefinitely.

## Verdict

**TEST-BUG**: The test code does not handle the case where a `Future` becomes stale after a master restart. The test should either:
1. Not use the old `Future` after a master restart
2. Wait for the operation to complete using the new master's `ProcedureExecutor`
3. Use table availability checks instead of procedure futures after restarts
