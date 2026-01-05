# HBASE-BUG-GROUP-67: NullPointerException in AbstractRpcClient.createAddr due to missing null check for region location

## Summary

A `NullPointerException` occurs in `AbstractRpcClient.createAddr()` when attempting to split a region after a RegionServer restart. The root cause is a missing null check for `node.getRegionLocation()` in `SplitTableRegionProcedure.checkSplittable()`.

## Failure Stacktrace

```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.ipc.AbstractRpcClient.createAddr(AbstractRpcClient.java:459)
	at org.apache.hadoop.hbase.ipc.AbstractRpcClient.createBlockingRpcChannel(AbstractRpcClient.java:538)
	at org.apache.hadoop.hbase.client.ConnectionImplementation.lambda$getAdmin$7(ConnectionImplementation.java:1440)
	at org.apache.hadoop.hbase.util.ConcurrentMapUtils.computeIfAbsentEx(ConcurrentMapUtils.java:51)
	at org.apache.hadoop.hbase.client.ConnectionImplementation.getAdmin(ConnectionImplementation.java:1438)
	at org.apache.hadoop.hbase.client.ServerConnectionUtils$ShortCircuitingClusterConnection.getAdmin(ServerConnectionUtils.java:88)
	at org.apache.hadoop.hbase.master.assignment.AssignmentManagerUtil.getRegionInfoResponse(AssignmentManagerUtil.java:76)
	at org.apache.hadoop.hbase.master.assignment.SplitTableRegionProcedure.checkSplittable(SplitTableRegionProcedure.java:220)
	at org.apache.hadoop.hbase.master.assignment.SplitTableRegionProcedure.<init>(SplitTableRegionProcedure.java:137)
	at org.apache.hadoop.hbase.regionserver.TestSplitWithBlockingFiles_RestartInjected.testSplitIgnoreBlockingFiles(TestSplitWithBlockingFiles_RestartInjected.java:157)
```

## Root Cause Analysis

### Buggy Code Location

**File:** `hbase-server/src/main/java/org/apache/hadoop/hbase/master/assignment/SplitTableRegionProcedure.java`

**Lines 215-221:**
```java
if (node != null) {
  try {
    GetRegionInfoResponse response;
    if (!hasBestSplitRow()) {
      LOG.info(
        "{} splitKey isn't explicitly specified, will try to find a best split key from RS {}",
        node.getRegionInfo().getRegionNameAsString(), node.getRegionLocation());
      response = AssignmentManagerUtil.getRegionInfoResponse(env, node.getRegionLocation(),  // <-- BUG: node.getRegionLocation() can be null
        node.getRegionInfo(), true);
      bestSplitRow =
        response.hasBestSplitRow() ? response.getBestSplitRow().toByteArray() : null;
    } else {
      response = AssignmentManagerUtil.getRegionInfoResponse(env, node.getRegionLocation(),  // <-- BUG: node.getRegionLocation() can be null
        node.getRegionInfo(), false);
    }
```

### Why This Happens

1. **`checkOnline()` is insufficient:** Before `checkSplittable()` is called, `checkOnline(env, regionToSplit)` is invoked (line 119). However, `checkOnline()` only verifies that the region state is `OPEN`:

   ```java
   // RegionStateNode.checkOnline()
   public void checkOnline() throws DoNotRetryRegionException {
     RegionInfo ri = getRegionInfo();
     State s = state;
     if (s != State.OPEN) {
       throw new DoNotRetryRegionException(ri.getEncodedName() + " is not OPEN; state=" + s);
     }
     // ... other checks but NO check for getRegionLocation() != null
   }
   ```

2. **Region can be OPEN with null location:** After a RegionServer restart, a region can temporarily be in the OPEN state (or transition to OPEN) before a server location is assigned. During this window, `node.getRegionLocation()` returns `null`.

3. **Null propagation:** The null `ServerName` is passed down:
   - `AssignmentManagerUtil.getRegionInfoResponse(env, null, ...)` (line 76)
   - `env.getMasterServices().getClusterConnection().getAdmin(null)` (ConnectionImplementation.java:1440)
   - `this.rpcClient.createBlockingRpcChannel(null, user, rpcTimeout)` (AbstractRpcClient.java:538)
   - `createAddr(null)` which does `null.getHostname()` causing NPE (AbstractRpcClient.java:459)

### Call Flow to NPE

```
SplitTableRegionProcedure.<init>() [line 137]
  -> checkSplittable() [line 220]
    -> AssignmentManagerUtil.getRegionInfoResponse(env, node.getRegionLocation(), ...) [line 76]
      -> env.getMasterServices().getClusterConnection().getAdmin(regionLocation) [null is passed]
        -> ConnectionImplementation.getAdmin(null)
          -> rpcClient.createBlockingRpcChannel(null, user, rpcTimeout)
            -> createAddr(null)
              -> null.getHostname()  // NPE!
```

## Trigger Condition

This bug is triggered when:
1. A RegionServer is restarted (gracefully or forcefully)
2. A split operation is initiated for a region that was hosted on that RegionServer
3. The split is initiated before the region is fully re-assigned to a server

## Impact

- Split operations can fail with an uninformative NPE instead of a meaningful error message
- Users cannot split regions during or immediately after RegionServer restarts
- The NPE provides no indication of the actual problem (region not assigned)

## Suggested Fix

Add null check for `node.getRegionLocation()` in `checkSplittable()`:

**Option 1: Add explicit null check and throw meaningful exception**
```java
private void checkSplittable(final MasterProcedureEnv env, final RegionInfo regionToSplit)
  throws IOException {
  // ... existing code ...

  RegionStateNode node =
    env.getAssignmentManager().getRegionStates().getRegionStateNode(getParentRegion());
  IOException splittableCheckIOE = null;
  boolean splittable = false;
  if (node != null) {
    // ADD THIS NULL CHECK:
    ServerName regionLocation = node.getRegionLocation();
    if (regionLocation == null) {
      throw new DoNotRetryIOException(
        "Region " + regionToSplit.getShortNameToLog() +
        " is not assigned to any server. Cannot check splittability.");
    }

    try {
      GetRegionInfoResponse response;
      if (!hasBestSplitRow()) {
        LOG.info(
          "{} splitKey isn't explicitly specified, will try to find a best split key from RS {}",
          node.getRegionInfo().getRegionNameAsString(), regionLocation);
        response = AssignmentManagerUtil.getRegionInfoResponse(env, regionLocation,
          node.getRegionInfo(), true);
```

**Option 2: Enhance `checkOnline()` to also verify location is assigned**
```java
// In RegionStateNode.java
public void checkOnline() throws DoNotRetryRegionException {
  RegionInfo ri = getRegionInfo();
  State s = state;
  if (s != State.OPEN) {
    throw new DoNotRetryRegionException(ri.getEncodedName() + " is not OPEN; state=" + s);
  }
  // ADD THIS CHECK:
  if (getRegionLocation() == null) {
    throw new DoNotRetryRegionException(
      ri.getEncodedName() + " is OPEN but not assigned to any server");
  }
  // ... rest of existing checks
}
```

## Reproduction

Test: `org.apache.hadoop.hbase.regionserver.TestSplitWithBlockingFiles_RestartInjected::testSplitIgnoreBlockingFiles`
- Position: `after_split_policy_check`
- Target: `regionserver`
- Mode: `GRACEFUL`

Note: The restart testing framework's health check may prevent reproduction by detecting "No regions are assigned to any server" before the test proceeds. This is actually the framework catching the underlying issue that leads to the NPE.

## Related Files

- `hbase-server/src/main/java/org/apache/hadoop/hbase/master/assignment/SplitTableRegionProcedure.java` (buggy code)
- `hbase-server/src/main/java/org/apache/hadoop/hbase/master/assignment/AssignmentManagerUtil.java` (passes null)
- `hbase-server/src/main/java/org/apache/hadoop/hbase/master/assignment/RegionStateNode.java` (checkOnline insufficient)
- `hbase-client/src/main/java/org/apache/hadoop/hbase/ipc/AbstractRpcClient.java` (NPE location)

## Patch

A patch implementing Option 1 (explicit null check in `checkSplittable()`) is available at:

**Patch File:** `dec20/patches/HBASE-BUG-GROUP-67.patch`

### Patch Summary

The patch adds a null check for `node.getRegionLocation()` before attempting to contact the RegionServer. If the location is null, it throws a `DoNotRetryIOException` with a clear error message indicating the region is not assigned to any server.

### How to Apply

```bash
cd /home/shuai/xlab/restart_testing/hbase
git apply dec20/patches/HBASE-BUG-GROUP-67.patch
```

### Patch Contents

```diff
diff --git a/hbase-server/src/main/java/org/apache/hadoop/hbase/master/assignment/SplitTableRegionProcedure.java b/hbase-server/src/main/java/org/apache/hadoop/hbase/master/assignment/SplitTableRegionProcedure.java
--- a/hbase-server/src/main/java/org/apache/hadoop/hbase/master/assignment/SplitTableRegionProcedure.java
+++ b/hbase-server/src/main/java/org/apache/hadoop/hbase/master/assignment/SplitTableRegionProcedure.java
@@ -205,17 +205,24 @@ public class SplitTableRegionProcedure
     IOException splittableCheckIOE = null;
     boolean splittable = false;
     if (node != null) {
+      // Check if region has a valid location before attempting to contact the RegionServer.
+      // After a RegionServer restart, the region may be in OPEN state but not yet assigned
+      // to a server, resulting in null location.
+      ServerName regionLocation = node.getRegionLocation();
+      if (regionLocation == null) {
+        throw new DoNotRetryIOException("Region " + regionToSplit.getShortNameToLog()
+          + " is not assigned to any server. Cannot check splittability.");
+      }
       try {
         GetRegionInfoResponse response;
         if (!hasBestSplitRow()) {
           LOG.info(
             "{} splitKey isn't explicitly specified, will try to find a best split key from RS {}",
-            node.getRegionInfo().getRegionNameAsString(), node.getRegionLocation());
-          response = AssignmentManagerUtil.getRegionInfoResponse(env, node.getRegionLocation(),
+            node.getRegionInfo().getRegionNameAsString(), regionLocation);
+          response = AssignmentManagerUtil.getRegionInfoResponse(env, regionLocation,
             node.getRegionInfo(), true);
           bestSplitRow =
             response.hasBestSplitRow() ? response.getBestSplitRow().toByteArray() : null;
         } else {
-          response = AssignmentManagerUtil.getRegionInfoResponse(env, node.getRegionLocation(),
+          response = AssignmentManagerUtil.getRegionInfoResponse(env, regionLocation,
             node.getRegionInfo(), false);
         }
```
