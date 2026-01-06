# HBASE-TEST-BUG-GROUP-9: Stale ProcedureFuture Reference After Master Restart

## Summary

Multiple test classes use a stale `ProcedureFuture` reference after master restart. The tests create a `Future` object from `AssignmentManager.moveAsync()` before a restart injection point, but continue to use this `Future` after master restarts. Since `ProcedureFuture` internally holds a reference to the old `ProcedureExecutor`, calling `future.get()` on a stale `Future` causes "The Master is Aborting" error.

## Error Message

```
java.util.concurrent.ExecutionException: java.io.IOException: The Master is Aborting
	at org.apache.hadoop.hbase.master.procedure.ProcedureSyncWait$ProcedureFuture.get(ProcedureSyncWait.java:100)
	at org.apache.hadoop.hbase.master.procedure.ProcedureSyncWait$ProcedureFuture.get(ProcedureSyncWait.java:65)
Caused by: java.io.IOException: The Master is Aborting
	at org.apache.hadoop.hbase.master.procedure.ProcedureSyncWait.waitForProcedureToComplete(ProcedureSyncWait.java:171)
```

## Affected Tests

- `TestReportOnlineRegionsRace_RestartInjected::testRace`
- `TestRaceBetweenSCPAndTRSP_RestartInjected::test`
- `TestReportRegionStateTransitionFromDeadServer_RestartInjected::test`
- `TestSCPGetRegionsRace_RestartInjected::test`
- `TestReportRegionStateTransitionRetry_RestartInjected::testRetryOnClose`

## Root Cause Analysis

### The ProcedureFuture Class

In `ProcedureSyncWait.java`, the `ProcedureFuture` class holds references to `procExec` and `proc`:

```java
// hbase-server/src/main/java/org/apache/hadoop/hbase/master/procedure/ProcedureSyncWait.java
private static class ProcedureFuture implements Future<byte[]> {
    private final ProcedureExecutor<MasterProcedureEnv> procExec;  // Reference to old executor
    private final Procedure<?> proc;

    // ...

    @Override
    public byte[] get() throws InterruptedException, ExecutionException {
        if (hasResult) {
            return result;
        }
        try {
            return waitForProcedureToComplete(procExec, proc, Long.MAX_VALUE);  // Uses stale procExec
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }
}
```

### The Error Trigger

In `waitForProcedureToComplete()`:

```java
// hbase-server/src/main/java/org/apache/hadoop/hbase/master/procedure/ProcedureSyncWait.java:150-172
public static byte[] waitForProcedureToComplete(
    final ProcedureExecutor<MasterProcedureEnv> procExec, final Procedure<?> proc,
    final long timeout) throws IOException {
    waitFor(procExec.getEnvironment(), timeout, "pid=" + proc.getProcId(),
        new ProcedureSyncWait.Predicate<Boolean>() {
            @Override
            public Boolean evaluate() throws IOException {
                if (!procExec.isRunning()) {  // Stale procExec returns false
                    return true;
                }
                // ...
            }
        });
    if (!procExec.isRunning()) {  // Stale procExec returns false
        throw new IOException("The Master is Aborting");  // LINE 171 - Error thrown here
    }
    // ...
}
```

When `procExec.isRunning()` is checked on the stale (old master's) `ProcedureExecutor`, it returns `false` because the old master has stopped, triggering the "The Master is Aborting" exception.

### Buggy Test Code Pattern

Example from `TestRaceBetweenSCPAndTRSP_RestartInjected.java`:

```java
// hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestRaceBetweenSCPAndTRSP_RestartInjected.java

@Test
public void test() throws Exception {
    // ...

    // LINE 175: Future is created with OLD am's ProcedureExecutor
    Future<byte[]> moveFuture = am.moveAsync(new RegionPlan(region, sn, sn));

    // ... more restart points ...

    // LINE 183-188: Master restarts here!
    RestartFramework.at("after_region_opening_arrive")
        .on(UTIL.getMiniHBaseCluster())
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // LINE 189: Test correctly refreshes am after restart
    am = UTIL.getMiniHBaseCluster().getMaster().getAssignmentManager();

    // ... more code and restart points ...

    // LINE 235: BUG - moveFuture is NEVER updated, still references OLD procExec!
    moveFuture.get();  // THROWS "The Master is Aborting"
}
```

The test correctly refreshes `master`, `am`, and `procExec` variables after each restart, but the `moveFuture` variable is **never updated** - it still holds a reference to the OLD `ProcedureExecutor` from before the master restart.

## Potential Fix

### Option 1: Avoid restart between moveAsync() and future.get()

Don't inject restart points between the creation of the `Future` and its consumption.

### Option 2: Re-obtain procedure status from new master

After master restart, instead of using the stale `Future`, query the new `ProcedureExecutor` for the procedure status:

```java
// After master restart
master = UTIL.getMiniHBaseCluster().getMaster();
ProcedureExecutor<MasterProcedureEnv> newProcExec = master.getMasterProcedureExecutor();

// Find the procedure by some identifier and wait for it
// Or re-submit if necessary
```

### Option 3: Create a restart-resilient Future wrapper

Create a wrapper that re-obtains the `ProcedureExecutor` reference after restart:

```java
// In the test, after restart:
moveFuture = ProcedureSyncWait.submitProcedure(
    UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor(),
    existingProc  // Need to find the procedure in new executor
);
```

## Classification

This is a **TEST-BUG** because:
1. The source code (`ProcedureSyncWait` and `ProcedureFuture`) behaves correctly - it's designed to work with a valid `ProcedureExecutor`
2. The test code incorrectly uses a stale `Future` object that references a dead master's `ProcedureExecutor`
3. This is similar to Group 3 (TEST-BUG) where stale references are used after restart

## Reproduction

The bug can be reproduced with:
```bash
mvn surefire:test -Dtest=org.apache.hadoop.hbase.master.assignment.TestRaceBetweenSCPAndTRSP_RestartInjected#test -Drestart.position=after_region_opening_arrive -Drestart.target=master -Drestart.mode=GRACEFUL
```

Output:
```
java.util.concurrent.ExecutionException: java.io.IOException: The Master is Aborting
	at org.apache.hadoop.hbase.master.procedure.ProcedureSyncWait$ProcedureFuture.get(ProcedureSyncWait.java:100)
```
