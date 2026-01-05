# HBASE-BUG-GROUP-58: NullPointerException in WorkerAssigner.serverAdded during Master Shutdown

## Summary

`WorkerAssigner.serverAdded()` throws NullPointerException when called during or after Master shutdown because it accesses `MasterProcedureExecutor` without null checks, but `MasterProcedureExecutor` is explicitly set to null during the shutdown process.

## Affected Component

- **File:** `hbase-server/src/main/java/org/apache/hadoop/hbase/master/WorkerAssigner.java`
- **Method:** `serverAdded(ServerName worker)` at line 83

## Root Cause Analysis

### The Bug

The `WorkerAssigner` class implements `ServerListener` and registers itself with `ServerManager` to receive server event notifications. In the `serverAdded()` callback method, it accesses a chain of objects without null checks:

```java
@Override
public void serverAdded(ServerName worker) {
  this.wake(master.getMasterProcedureExecutor().getEnvironment().getProcedureScheduler());
}
```

### Why NPE Occurs

During Master shutdown, the `procedureExecutor` is explicitly set to `null` in `HMaster.stopProcedureExecutor()`:

```java
// HMaster.java:1849-1856
private void stopProcedureExecutor() {
  if (procedureExecutor != null) {
    configurationManager.deregisterObserver(procedureExecutor.getEnvironment());
    procedureExecutor.getEnvironment().getRemoteDispatcher().stop();
    procedureExecutor.stop();
    procedureExecutor.join();
    procedureExecutor = null;  // <-- Set to null here
  }
  // ...
}
```

However, `WorkerAssigner` is **never unregistered** from `ServerManager` during shutdown. If any `serverAdded()` event is triggered during or after the shutdown process (while `WorkerAssigner` is still registered as a listener), it will cause an NPE because `getMasterProcedureExecutor()` returns `null`.

### Call Chain When Failure Occurs

1. `ServerManager.regionServerReport()` (line 295)
2. `ServerManager.checkAndRecordNewServer()` (line 377)
3. `ServerListener.serverAdded()` callback for all registered listeners
4. `WorkerAssigner.serverAdded()` (line 83) - **NPE occurs here**

## Stacktrace

```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.master.WorkerAssigner.serverAdded(WorkerAssigner.java:83)
	at org.apache.hadoop.hbase.master.ServerManager.checkAndRecordNewServer(ServerManager.java:377)
	at org.apache.hadoop.hbase.master.ServerManager.regionServerReport(ServerManager.java:295)
	at org.apache.hadoop.hbase.master.MasterRpcServices.regionServerReport(MasterRpcServices.java:573)
```

## Buggy Code

**File:** `WorkerAssigner.java:81-84`
```java
@Override
public void serverAdded(ServerName worker) {
  this.wake(master.getMasterProcedureExecutor().getEnvironment().getProcedureScheduler());
}
```

## Potential Fix

### Option 1: Add Null Check in serverAdded() (Recommended)

```java
@Override
public void serverAdded(ServerName worker) {
  ProcedureExecutor<MasterProcedureEnv> executor = master.getMasterProcedureExecutor();
  if (executor != null && executor.getEnvironment() != null) {
    this.wake(executor.getEnvironment().getProcedureScheduler());
  }
}
```

### Option 2: Unregister WorkerAssigner During Shutdown

Add a `close()` or `stop()` method to `WorkerAssigner` that unregisters it from `ServerManager`, and call it during master shutdown in `SplitWALManager` and `SnapshotManager`.

```java
public void stop() {
  ServerManager sm = this.master.getServerManager();
  if (sm != null) {
    sm.unregisterListener(this);
  }
}
```

### Option 3: Both (Most Robust)

Implement both the null check (defensive programming) AND proper unregistration (proper lifecycle management).

## Reproduction

```bash
mvn surefire:test \
  -Dtest=org.apache.hadoop.hbase.master.TestGetReplicationLoad_RestartInjected#testGetReplicationMetrics \
  -Drestart.position=after_add_second_peer \
  -Drestart.target=master \
  -Drestart.mode=GRACEFUL
```

## Classification

**Type:** BUG (Production Code Issue)

**Severity:** Medium - Can cause NPE during master restart/shutdown scenarios when region servers are reporting.

## Related Classes

- `SplitWALManager.java:80` - Creates a `WorkerAssigner` instance
- `SnapshotManager.java:1391` - Creates a `WorkerAssigner` instance
- `ServerManager.java` - Manages server listeners
- `HMaster.java` - Sets `procedureExecutor = null` during shutdown

## Patch

**Patch File:** `dec20/patches/HBASE-BUG-GROUP-58.patch`

### Apply Patch

```bash
cd /home/shuai/xlab/restart_testing/hbase
git apply dec20/patches/HBASE-BUG-GROUP-58.patch
```

### Patch Contents

```diff
diff --git a/hbase-server/src/main/java/org/apache/hadoop/hbase/master/WorkerAssigner.java b/hbase-server/src/main/java/org/apache/hadoop/hbase/master/WorkerAssigner.java
index abcdef1..1234567 100644
--- a/hbase-server/src/main/java/org/apache/hadoop/hbase/master/WorkerAssigner.java
+++ b/hbase-server/src/main/java/org/apache/hadoop/hbase/master/WorkerAssigner.java
@@ -24,6 +24,8 @@ import java.util.Map;
 import java.util.Optional;
 import org.apache.hadoop.hbase.ServerName;
 import org.apache.hadoop.hbase.master.procedure.MasterProcedureScheduler;
+import org.apache.hadoop.hbase.master.procedure.MasterProcedureEnv;
+import org.apache.hadoop.hbase.procedure2.ProcedureExecutor;
 import org.apache.hadoop.hbase.procedure2.Procedure;
 import org.apache.hadoop.hbase.procedure2.ProcedureEvent;
 import org.apache.yetus.audience.InterfaceAudience;
@@ -79,7 +81,13 @@ public class WorkerAssigner implements ServerListener {

   @Override
   public void serverAdded(ServerName worker) {
-    this.wake(master.getMasterProcedureExecutor().getEnvironment().getProcedureScheduler());
+    // Defensive null check: MasterProcedureExecutor may be null during master shutdown
+    // as it is explicitly set to null in HMaster.stopProcedureExecutor()
+    ProcedureExecutor<MasterProcedureEnv> executor = master.getMasterProcedureExecutor();
+    if (executor != null && executor.getEnvironment() != null) {
+      MasterProcedureScheduler scheduler = executor.getEnvironment().getProcedureScheduler();
+      this.wake(scheduler);
+    }
   }

   public synchronized void addUsedWorker(ServerName worker) {
```

### Changes Summary

1. **Added imports:** `MasterProcedureEnv` and `ProcedureExecutor`
2. **Modified `serverAdded()` method:** Added defensive null checks before accessing the procedure executor chain to prevent NPE during master shutdown
