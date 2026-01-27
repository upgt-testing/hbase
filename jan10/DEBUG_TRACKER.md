# HBase Debug Tracker - Jan10

## Priority Order Rationale

Groups are ordered by likelihood of being actual bugs:
1. **Highest Priority**: NPE/IOOB/ArrayIndexOutOfBounds from non-test, non-restarttest code
2. **High Priority**: Exceptions indicating state inconsistency from HBase core code
3. **Medium Priority**: Exceptions from test code or transient errors
4. **Lowest Priority**: Timeouts and exceptions directly from restarttest framework (FALSE POSITIVES)

---

## HIGH PRIORITY - Likely Bugs

### Group 15: ArrayIndexOutOfBoundsException in CopyOnWriteArrayList

[x] FP + TEST-BUG - Stale ServerName reference (FP), but poor error handling in MiniHBaseCluster (TEST-BUG)
    - See FPs/FP-GROUP-15.md for FP analysis
    - See bugs/TEST-BUG-GROUP-15.md for error handling improvement proposal

**Test Executions**: 5 failures

**Generalized Stack Trace**:
```
java.lang.ArrayIndexOutOfBoundsException
	at java.util.concurrent.CopyOnWriteArrayList.get(CopyOnWriteArrayList.java)
```

**Raw Stack Trace Sample**:
```
java.lang.ArrayIndexOutOfBoundsException
	at java.util.concurrent.CopyOnWriteArrayList.get(CopyOnWriteArrayList.java)
```

**Analysis**: FALSE POSITIVE. The restart framework injects a restart between when the test obtains a ServerName reference and when it uses it. After restart, the region server has a new ServerName (with new startcode), making the old reference stale. `getRegionServerIndex` correctly returns -1 for the unknown server, causing the ArrayIndexOutOfBoundsException.

**Test Executions (Examples)**:

1. Test: Check execution details in step3_grouped_failures.json for group_id 15
   - Stale ServerName reference after region server restart

---

### Group 17: NullPointerException in BucketCache.parsePB

[x] BUG - Missing null check after parseDelimitedFrom() in BucketCache.retrieveChunkedBackingMap()
    - See bugs/BUG-GROUP-17.md for detailed analysis

**Test Executions**: 4 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.NullPointerException
	at org.apache.hadoop.hbase.io.hfile.bucket.BucketCache.parsePB(BucketCache.java)
```

**Analysis**: BUG. The `retrieveChunkedBackingMap()` method calls `parseDelimitedFrom()` which returns null when the persistence file is incomplete/corrupted (e.g., restart happened during write). The code passes null to `parsePB()` without checking, causing NPE when accessing `firstChunk.getDeserializersMap()`.

---

### Group 22: NullPointerException in HMaster.getReplicationLoad

[x] TEST-BUG - Stale master reference after restart
    - See bugs/TEST-BUG-GROUP-22.md for detailed analysis

**Test Executions**: 3 failures

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.master.HMaster.getReplicationLoad(HMaster.java)
```

**Analysis**: TEST-BUG. The test stores a static `master` reference in `@BeforeClass`. After master restart injection, the test continues to use the stale/stopped master reference instead of refreshing it via `cluster.getMaster()`. When `master.getReplicationLoad()` is called on the stopped master, `getServerManager().getLoad(serverName)` returns null, causing NPE.

---

### Group 37: NullPointerException in MasterCoprocessorHost.createEnvironment

[x] TEST-BUG - Test passes null coprocessor to createEnvironment() after master restart
    - See bugs/TEST-BUG-GROUP-37.md for detailed analysis

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.master.MasterCoprocessorHost.createEnvironment(MasterCoprocessorHost.java)
```

**Analysis**: TEST-BUG. The test dynamically loads a coprocessor using `cpHost.load()` (in-memory only), then restarts the master. After restart, the coprocessor is lost because it wasn't persisted. The test then calls `findCoprocessor()` which returns null, and passes this null to `createEnvironment()`, causing NPE.

---

### Group 55: NullPointerException in RegionRemoteProcedureBase.getParent

[x] BUG - Missing null check in RegionRemoteProcedureBase.afterReplay()
    - See bugs/BUG-GROUP-55.md for detailed analysis

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.master.assignment.RegionRemoteProcedureBase.getParent(RegionRemoteProcedureBase.java)
```

**Analysis**: BUG. The `afterReplay()` method in RegionRemoteProcedureBase calls `getParent(env).attachRemoteProc(this)` without checking if `getParent()` returns null. When the parent TransitRegionStateProcedure has completed/rolled back and is no longer in the active procedures map, `getProcedure(getParentProcId())` returns null, causing NPE when calling `.attachRemoteProc()` on the null value.

---

### Group 3: IndexOutOfBoundsException in Test Code

[x] TEST-BUG - Test code doesn't wait for regions to be available after restart
    - See bugs/TEST-BUG-GROUP-3.md for detailed analysis

**Test Executions**: 24 failures

**Generalized Stack Trace**:
```
java.lang.IndexOutOfBoundsException
	at java.util.ArrayList.rangeCheck(ArrayList.java)
```

**Raw Stack Trace Sample**:
```
java.lang.IndexOutOfBoundsException: Index: 0, Size: 0
	at java.util.ArrayList.rangeCheck(ArrayList.java:659)
	at java.util.ArrayList.get(ArrayList.java:435)
	at org.apache.hadoop.hbase.master.assignment.TestTransitRegionStateProcedure_RestartInjected.testRecoveryAndDoubleExecutionMove(TestTransitRegionStateProcedure_RestartInjected.java:157)
```

**Analysis**: TEST-BUG. After region server restart, the test code immediately tries to access regions via `getRegions(tableName).get(0)` without waiting for region reassignment to complete. The `getRegions()` method returns regions that are currently online, but after restart there's a window during which regions are being reassigned and are not yet available. The fix is to add `HTU.waitTableAvailable(tableName)` after the restart before accessing regions.

**Test Executions (Examples)**:

1. Test: `TestTransitRegionStateProcedure_RestartInjected.testRecoveryAndDoubleExecutionMove`
   - "position": "after_recovery_and_double_execution"
   - "target": "regionserver"
   - "mode": "GRACEFUL"
   - "executionDir": "059-66987883"

2. Test: `TestRSMobFileCleanerChore_RestartInjected.testCleaningAndStoreFileReaderCreatedByOtherThreads`
   - "position": "after_flush"
   - "target": "regionserver"
   - "mode": "GRACEFUL"
   - "executionDir": "008-608e7721"

3. Test: `TestDirectStoreSplitsMerges_RestartInjected.testCommitDaughterRegionWithFiles`
   - "position": "after_data_flush"
   - "target": "regionserver"
   - "mode": "GRACEFUL"
   - "executionDir": "085-8316b62f"

---

### Group 13: NoSuchElementException in ArrayList Iterator

[x] TEST-BUG - Test code doesn't wait for regions to be available after restart
    - See bugs/TEST-BUG-GROUP-13.md for detailed analysis

**Test Executions**: 6 failures

**Generalized Stack Trace**:
```
java.util.NoSuchElementException
	at java.util.ArrayList$Itr.next(ArrayList.java)
```

**Analysis**: TEST-BUG. After region server restart, the test code immediately tries to access regions via `getRegions(table)` and then calls `Iterables.getOnlyElement()` on the empty list. The `UTIL.flush(tn)` call silently does nothing when no regions are online. The fix is to add `UTIL.waitTableAvailable(tn)` after the restart before accessing regions.

---

### Group 18: NoSuchElementException in Optional.get

[x] TEST-BUG - Test doesn't re-wait for procedure after master restart
    - See bugs/TEST-BUG-GROUP-18.md for detailed analysis

**Test Executions**: 4 failures

**Generalized Stack Trace**:
```
java.util.NoSuchElementException
	at java.util.Optional.get(Optional.java)
```

**Analysis**: TEST-BUG. After master restart, the test code directly tries to find an `OpenRegionProcedure` without waiting for it to be available. The procedure may have completed during master recovery, causing `NoSuchElementException` when calling `.get()` on an empty `Optional`. The test correctly waits for the procedure before the restart (lines 257-259), but does not re-wait after the restart (lines 271-273). The fix is to add waiting logic or handle the case where the procedure may have completed during recovery.

---

### Group 61: ArrayIndexOutOfBoundsException (Caused by)

[x] FP - Invalid restart position after test has removed all regionservers
    - See FPs/FP-GROUP-61.md for detailed analysis

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
Caused by: java.lang.ArrayIndexOutOfBoundsException
	at java.util.concurrent.CopyOnWriteArrayList.get(CopyOnWriteArrayList.java)
```

**Analysis**: FALSE POSITIVE. The test (`TestSafemodeBringsDownMaster_RestartInjected`) explicitly aborts and removes the only regionserver from the cluster at lines 142-143 before the restart injection point "after_master_shutdown". When the restart framework tries to restart regionserver index 0, the regionserver list is empty, causing ArrayIndexOutOfBoundsException in `MiniHBaseCluster.stopRegionServer()`. This is not an HBase bug - it's an invalid restart position where no regionservers exist to restart.

---

## MEDIUM-HIGH PRIORITY - State Issues

### Group 10: IllegalArgumentException in Preconditions.checkArgument

[x] TEST-BUG - Stale ProcedureExecutor reference after master restart
    - See bugs/TEST-BUG-GROUP-10.md for detailed analysis

**Test Executions**: 8 failures

**Generalized Stack Trace**:
```
java.lang.IllegalArgumentException
	at org.apache.hbase.thirdparty.com.google.common.base.Preconditions.checkArgument(Preconditions.java)
```

**Analysis**: TEST-BUG. The `_RestartInjected` tests store a `ProcedureExecutor` reference from the master before restart injection, but fail to refresh this reference after the master is restarted. When the test calls `submitProcedure()` on the stale reference, it fails because the old (stopped) master's ProcedureExecutor has `lastProcId = -1` (reset during `stop()` method). The check `Preconditions.checkArgument(lastProcId.get() >= 0)` at line 1096 throws IllegalArgumentException. The fix is to refresh the procExec reference after each master restart: `procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();`

---

### Group 4: FailedServerException

[x] TEST-BUG - Test doesn't wait for table availability after region server restart
    - See bugs/TEST-BUG-GROUP-4.md for detailed analysis

**Test Executions**: 13 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.hadoop.hbase.ipc.FailedServerException
	at org.apache.hadoop.hbase.ipc.AbstractRpcClient.getConnection(AbstractRpcClient.java)
```

**Analysis**: TEST-BUG. After region server restart, the test immediately calls Admin operations (like `majorCompact()`) without waiting for regions to be reassigned. The old server address is in the RPC client's "failed servers list" and the meta table still has the old server location. The fix is to add `TEST_UTIL.waitTableAvailable(tableName)` after the restart point before calling Admin operations.

---

### Group 5: DoNotRetryRegionException - Region Not Online

[x] TEST-BUG - Test doesn't wait for region to be OPEN after regionserver restart before split
    - See bugs/TEST-BUG-GROUP-5.md for detailed analysis

**Test Executions**: 12 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.hadoop.hbase.ipc.RemoteWithExtrasException(org.apache.hadoop.hbase.client.DoNotRetryRegionException)
	at org.apache.hadoop.hbase.master.assignment.RegionStateNode.checkOnline(RegionStateNode.java)
```

**Analysis**: TEST-BUG. After regionserver restart, the test immediately tries to split regions without waiting for them to transition from OPENING to OPEN state. The `SplitTableRegionProcedure` constructor calls `checkOnline()` which correctly throws `DoNotRetryRegionException` when the region is not OPEN. The fix is to add `TEST_UTIL.waitTableAvailable(tableName)` after the restart and before the split operation.

---

### Group 12: DoNotRetryRegionException

[x] TEST-BUG - Test doesn't wait for regions to be OPEN after restart during ModifyTableProcedure
    - See bugs/TEST-BUG-GROUP-12.md for detailed analysis

**Test Executions**: 7 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.hadoop.hbase.client.DoNotRetryRegionException
	at org.apache.hadoop.hbase.master.assignment.RegionStateNode.checkOnline(RegionStateNode.java)
```

**Analysis**: TEST-BUG. After a master restart during which a `ModifyTableProcedure` is in progress (e.g., changing region replication), the regions transition through CLOSING state. The test code immediately creates a `MergeTableRegionsProcedure` without waiting for regions to return to OPEN state. The `MergeTableRegionsProcedure` constructor calls `checkOnline()` which correctly throws `DoNotRetryRegionException` when the region is not OPEN. The fix is to add `UTIL.waitTableAvailable(tableName)` after the restart before attempting merge operations.

---

### Group 6: IOException in ProcedureSyncWait

[x] TEST-BUG - Stale Future reference after master restart
    - See bugs/TEST-BUG-GROUP-6.md for detailed analysis

**Test Executions**: 12 failures

**Generalized Stack Trace**:
```
Caused by: java.io.IOException
	at org.apache.hadoop.hbase.master.procedure.ProcedureSyncWait.waitForProcedureToComplete(ProcedureSyncWait.java)
```

**Analysis**: TEST-BUG. The test stores a `Future<byte[]>` reference from `am.moveAsync()` before a master restart, but the `Future` is tied to the old (stopped) master's `ProcedureExecutor`. After master restart, when `future.get()` is called, it checks `procExec.isRunning()` on the old (stopped) ProcedureExecutor which returns false, causing the code to throw `IOException("The Master is Aborting")`. The fix is to either skip using the old future after restart, wait for the procedure on the new master, or use table availability checks instead.

---

### Group 11: ZooKeeper NoNodeException

[x] TEST-BUG - Stale ServerName reference after regionserver restart
    - See bugs/TEST-BUG-GROUP-11.md for detailed analysis

**Test Executions**: 8 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.zookeeper.KeeperException$NoNodeException
	at org.apache.zookeeper.KeeperException.create(KeeperException.java)
```

**Analysis**: TEST-BUG. The test registers a WAL in ZooKeeper using the ServerName at registration time. After regionserver restart, the test's `updatePushedSeqId()` method uses `getRegionServer(0).getServerName()` which returns the NEW ServerName (with different startcode/timestamp). The ZooKeeper path for the new ServerName doesn't exist, causing NoNodeException. The fix is to store the original ServerName and use it consistently throughout the test.

---

### Group 20: IOException at MetaTableAccessor

[x] TEST-BUG - Stale HMaster reference after master restart
    - See bugs/TEST-BUG-GROUP-20.md for detailed analysis

**Test Executions**: 3 failures

**Generalized Stack Trace**:
```
java.io.IOException
	at org.apache.hadoop.hbase.MetaTableAccessor.getMetaHTable(MetaTableAccessor.java)
```

**Analysis**: TEST-BUG. The test stores `HMaster m = cluster.getMaster()` at the beginning of the test, then injects a master restart. After restart, the test continues to use `m.getConnection()` which returns the closed connection from the old (stopped) master. The `MetaTableAccessor.getMetaHTable()` correctly checks if the connection is closed and throws `IOException("connection is closed")`. The fix is to refresh the master reference after restart: `m = cluster.getMaster();`

---

### Group 21: NotServingRegionException

[x] TEST-BUG - Stale HRegionServer reference after regionserver restart
    - See bugs/TEST-BUG-GROUP-21.md for detailed analysis

**Test Executions**: 3 failures

**Generalized Stack Trace**:
```
org.apache.hadoop.hbase.NotServingRegionException
	at org.apache.hadoop.hbase.regionserver.HRegionServer.getRegionByEncodedName(HRegionServer.java)
```

**Analysis**: TEST-BUG. The test stores `currentServer` reference (line 109) before the restart. After regionserver restart, the test uses this stale reference to call `currentServer.getRegion(regionInfo.getRegionName())` (line 126). The region may have been reassigned to a different server during restart, so it's no longer on the expected server. The fix is to refresh server references after restart by re-querying `cluster.getServerWith()` and `cluster.getRegionServer()`, and add `TEST_UTIL.waitTableAvailable()` before accessing regions.

---

### Group 29: NotServingRegionException (Caused by)

[x] NOT REPRODUCIBLE - Both test executions passed on re-run
    - Tested: TestRegionServerNoMaster_RestartInjected.testCloseByRegionServer (position=after_put_data, target=regionserver, mode=GRACEFUL) - PASSED
    - Tested: TestRegionServerNoMaster_RestartInjected.testCancelOpeningWithoutZK (position=after_put_data, target=regionserver, mode=GRACEFUL) - PASSED

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.hadoop.hbase.NotServingRegionException
	at org.apache.hadoop.hbase.regionserver.HRegionServer.closeRegion(HRegionServer.java)
```

**Analysis**: Could not reproduce. The failure may have been due to timing issues in the original test environment.

---

### Group 34: RegionMovedException

[x] TEST-BUG - Stale destServer reference after regionserver restart
    - See bugs/TEST-BUG-GROUP-34.md for detailed analysis

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
org.apache.hadoop.hbase.exceptions.RegionMovedException
	at org.apache.hadoop.hbase.regionserver.HRegionServer.getRegionByEncodedName(HRegionServer.java)
```

**Analysis**: TEST-BUG. The test `TestRemoveRegionMetrics_RestartInjected.testMoveRegion` stores a `destServer` reference before the restart at `after_mid_region_move`. After the regionserver restart, the region may be reassigned to a different server. When the test calls `destServer.getRegion(regionInfo.getRegionName())`, the region is no longer on `destServer`, causing `RegionMovedException`. The fix is to wait for table availability and re-fetch server references after the restart.

---

### Group 35: UnknownScannerException

[x] FP - Expected behavior during regionserver restart (scanner state is ephemeral)
    - See FPs/FP-GROUP-35.md for detailed analysis

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.hadoop.hbase.UnknownScannerException
	at org.apache.hadoop.hbase.regionserver.RSRpcServices.getRegionScanner(RSRpcServices.java)
```

**Analysis**: FALSE POSITIVE. The `UnknownScannerException` occurs when a regionserver restart is injected mid-scan at `after_first_scan_result` or `after_second_scan_result` positions. Scanner state is stored in-memory and cannot survive restarts - this is by design. The exception message explicitly documents "d) RegionServer restart during upgrade" as an expected cause. The HBase client has recovery logic for this exception in `ClientScanner.handleScanError()`, but the test configuration limits retries (`HBASE_CLIENT_RETRIES_NUMBER=1`), preventing recovery.

---

## MEDIUM PRIORITY - Test Code NPEs

### Group 9: NullPointerException in Test (TestRSMobFileCleanerChore)

[x] TEST-BUG - Stale ServerName reference after regionserver restart
    - See bugs/TEST-BUG-GROUP-9.md for detailed analysis

**Test Executions**: 9 failures

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.mob.TestRSMobFileCleanerChore_RestartInjected.testMobFileCleanerChore(TestRSMobFileCleanerChore_RestartInjected.java)
```

**Analysis**: TEST-BUG. The test stores a `ServerName` reference (`serverUsed`) at lines 242-256 before the restart. After regionserver restart, `ServerName` becomes stale because it includes a startcode (timestamp) that changes on restart. When `getRegionServer(serverUsed)` is called at line 264, it returns `null` because no server matches the old `ServerName`. Calling `.getRSMobFileCleanerChore()` on `null` causes NPE. The fix is to re-query for the server reference after the restart.

---

### Group 16: ClassCastException in Test

[x] FP - Restart framework causes impossible JVM behavior (iterator returns wrong object type)
    - See FPs/FP-GROUP-16.md for detailed analysis

**Test Executions**: 5 failures

**Generalized Stack Trace**:
```
java.lang.ClassCastException
	at org.apache.hadoop.hbase.master.assignment.TestRegionBypass_RestartInjected.testBypass(TestRegionBypass_RestartInjected.java)
```

**Analysis**: FALSE POSITIVE. The restart framework corrupts local variable or iterator state. Debug investigation showed that `regions.get(0)` returns a MutableRegionInfo but `regions.iterator().next()` returns an InitMetaProcedure - two completely different objects. This behavior is impossible for a standard ArrayList and indicates interference from the restart framework's bytecode instrumentation.

---

### Group 19: NullPointerException in Test (TestFlushWithThroughputController)

[x] TEST-BUG - Test doesn't wait for table availability after regionserver restart
    - See bugs/TEST-BUG-GROUP-19.md for detailed analysis

**Test Executions**: 4 failures

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.regionserver.throttle.TestFlushWithThroughputController_RestartInjected.generateAndFlushData(TestFlushWithThroughputController_RestartInjected.java)
```

**Analysis**: TEST-BUG. After regionserver restart at `after_put_iteration_2` or `after_flush_iteration_2`, the test immediately calls `getStoreWithName(tableName)` without waiting for the region to be reassigned. If the region hasn't been assigned yet, the method returns `null`, and calling `store.getStorefilesCount()` causes NPE. The fix is to add `hbtu.waitTableAvailable(tableName)` before accessing region data after restart.

---

### Group 27: NullPointerException in Test (TestCompactSplitThread)

[x] TEST-BUG - Stale HRegionServer reference after regionserver restart
    - See bugs/TEST-BUG-GROUP-27.md for detailed analysis

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.regionserver.TestCompactSplitThread_RestartInjected.testThreadPoolSizeTuning(TestCompactSplitThread_RestartInjected.java)
```

**Analysis**: TEST-BUG. The test stores a `HRegionServer` reference at line 123 before the restart. After regionserver restart at `after_config_update_bigger` or `after_config_update_smaller`, the test continues to use the stale reference. The old (stopped) regionserver has `isStopped=true` and its `CompactSplitThread` has been cleaned up (returns null). Calling `getCompactSplitThread().getLargeCompactionThreadNum()` on null causes NPE. The fix is to refresh the regionServer reference after restart: `regionServer = TEST_UTIL.getRSForFirstRegionInTable(tableName);`

---

### Group 28: NullPointerException in Test (TestRegionMover2)

[x] TEST-BUG - Stale ServerName reference after regionserver restart
    - See bugs/TEST-BUG-GROUP-28.md for detailed analysis

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.util.TestRegionMover2_RestartInjected.regionIsolationOperation(TestRegionMover2_RestartInjected.java)
```

**Analysis**: TEST-BUG. The test stores `ServerName` references (e.g., `metaServerSource`, `metaServerDestination`) BEFORE the restart injection at `before_meta_isolate`. After the regionserver restart, the `ServerName` has a new startcode (timestamp), so `cluster.getRegionServer(sourceServerName)` returns `null` because the old `ServerName` doesn't match any running server. Calling `sourceRS.getRegions()` on the null reference causes NPE at line 529. The fix is to either move the restart point, or re-fetch the `ServerName` references after the restart.

---

### Group 32: ClassCastException in Test (TestRegionReplicasAreDistributed)

[x] FP - Restart framework corrupts HashMap/collection state
    - See FPs/FP-GROUP-32.md for detailed analysis

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
java.lang.ClassCastException: org.apache.hadoop.hbase.regionserver.HRegion cannot be cast to org.apache.hadoop.hbase.client.RegionInfo
	at org.apache.hadoop.hbase.regionserver.TestRegionReplicasAreDistributed_RestartInjected.checkAndAssertRegionDistribution(TestRegionReplicasAreDistributed_RestartInjected.java:179)
```

**Analysis**: FALSE POSITIVE. The test stores `MutableRegionInfo` objects in a `Map<ServerName, Collection<RegionInfo>>` before the master restart. After restart, retrieving the same collection from the same key returns `HRegion` objects instead - this is impossible in normal Java. Debug investigation confirmed:
- Before restart: All 21 elements are `MutableRegionInfo` with specific identity hashes
- After restart: All 21 elements are `HRegion` with completely different identity hashes

This is the same pattern as FP-GROUP-16 where the restart framework corrupts collection/iterator state.

---

### Group 36: NullPointerException in Test (TestQuotaObserverChoreWithMiniCluster)

[x] NOT REPRODUCIBLE - Both test executions passed on re-run
    - Tested: TestQuotaObserverChoreWithMiniCluster_RestartInjected.testTableQuotaOverridesNamespaceQuota (position=after_namespace_create, target=master, mode=GRACEFUL) - PASSED
    - Tested: TestQuotaObserverChoreWithMiniCluster_RestartInjected.testTableQuotaOverridesNamespaceQuota (position=after_tables_create, target=master, mode=GRACEFUL) - PASSED

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.quotas.TestQuotaObserverChoreWithMiniCluster_RestartInjected.testTableQuotaOverridesNamespaceQuota(TestQuotaObserverChoreWithMiniCluster_RestartInjected.java:335)
```

**Analysis**: Could not reproduce. The failure may have been due to timing issues in the original test environment. The test code already refreshes `admin` reference after each master restart (lines 308, 318, 331), but the `snapshotNotifier` field obtained in `@Before` could potentially become null if the old master's notifier was garbage collected. However, both executions passed during reproduction attempts.

---

### Group 38: NullPointerException in Test (TestRSMobFileCleanerChore) - Another Method

[x] TEST-BUG - Stale ServerName reference after regionserver restart
    - See bugs/TEST-BUG-GROUP-38.md for detailed analysis

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.mob.TestRSMobFileCleanerChore_RestartInjected.testCleaningAndStoreFileReaderCreatedByOtherThreads(TestRSMobFileCleanerChore_RestartInjected.java:446)
```

**Analysis**: TEST-BUG. The test stores a `ServerName` reference at lines 429-437 before the restart at position `after_get_server_name`. After regionserver restart, the `ServerName` becomes stale because it includes a startcode (timestamp) that changes on restart. When `getRegionServer(serverName)` is called at line 446, it returns `null` because no server matches the old `ServerName`. Calling `.getRSMobFileCleanerChore()` on `null` causes NPE. The fix is to re-query for the server reference after the restart.

---

## LOW PRIORITY - Connection/Network Issues

### Group 8: ConnectException

[ ] Not started

**Test Executions**: 9 failures

**Generalized Stack Trace**:
```
Caused by: java.net.ConnectException
	at sun.nio.ch.SocketChannelImpl.checkConnect(Native Method)
```

---

### Group 14: ZooKeeper ConnectionLossException

[ ] Not started

**Test Executions**: 5 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.zookeeper.KeeperException$ConnectionLossException
	at org.apache.zookeeper.KeeperException.create(KeeperException.java)
```

---

### Group 30: Netty NativeIoException

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.hbase.thirdparty.io.netty.channel.unix.Errors$NativeIoException
```

---

### Group 31: DFSClient IOException

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
java.io.IOException
	at org.apache.hadoop.hdfs.DFSClient.checkOpen(DFSClient.java)
```

---

### Group 33: TimeoutException in ProcedureFuture

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: java.util.concurrent.TimeoutException
	at org.apache.hadoop.hbase.client.HBaseAdmin$ProcedureFuture.waitProcedureResult(HBaseAdmin.java)
```

---

### Group 40: ZooKeeper SessionExpiredException

[ ] Not started

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
org.apache.zookeeper.KeeperException$SessionExpiredException
	at org.apache.zookeeper.KeeperException.create(KeeperException.java)
```

---

### Group 49: Another ConnectException

[ ] Not started

**Test Executions**: 1 failure

---

## FALSE POSITIVE - Restart Framework Issues

### Group 1: TestTimedOutException - waitForServerOnline (FALSE POSITIVE)

[ ] Not started

**Test Executions**: 93 failures

**Generalized Stack Trace**:
```
org.junit.runners.model.TestTimedOutException
	at java.lang.Object.wait(Native Method)
```

**Raw Stack Trace Sample**:
```
org.junit.runners.model.TestTimedOutException: test timed out after 780 seconds
	at java.lang.Object.wait(Native Method)
	at org.apache.hadoop.hbase.regionserver.HRegionServer.waitForServerOnline(HRegionServer.java:2593)
	at org.apache.hadoop.hbase.util.JVMClusterUtil$RegionServerThread.waitForServerOnline(JVMClusterUtil.java:68)
	at org.apache.hadoop.hbase.MiniHBaseCluster.startRegionServer(MiniHBaseCluster.java:453)
	at org.restarttest.adapter.hbase.HBaseClusterAdapter.restartRegionServer(HBaseClusterAdapter.java:227)
	at org.restarttest.adapter.hbase.HBaseClusterAdapter.restartNode(HBaseClusterAdapter.java:67)
```

**Analysis**: Timeout during restart framework's region server restart - FALSE POSITIVE.

**Test Executions (Examples)**:

1. Test: `TestRegionReplicas_RestartInjected.testGetOnTargetRegionReplica`
   - "position": "after_master_stop"
   - "target": "regionserver"
   - "mode": "GRACEFUL"
   - "executionDir": "088-3a0ff12d"

2. Test: `TestReplicasClient_RestartInjected.testUseRegionWithReplica`
   - "position": "after_put_replica"
   - "target": "regionserver"
   - "mode": "GRACEFUL"
   - "executionDir": "097-48aa55c8"

3. Test: `TestChangeSFTForMasterRegion_RestartInjected.test`
   - "position": "after_stop_master"
   - "target": "regionserver"
   - "mode": "GRACEFUL"
   - "executionDir": "041-5ff8785b"

---

### Group 2: Master Failed to Become Active (FALSE POSITIVE)

[ ] Not started

**Test Executions**: 83 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.Exception
	at org.restarttest.adapter.hbase.HBaseClusterAdapter.restartMaster(HBaseClusterAdapter.java)
```

**Raw Stack Trace Sample**:
```
org.restarttest.core.RestartException: Restart failed at position after_replica_assigned
Caused by: java.lang.Exception: Master failed to become active after restart
	at org.restarttest.adapter.hbase.HBaseClusterAdapter.restartMaster(HBaseClusterAdapter.java:201)
	at org.restarttest.adapter.hbase.HBaseClusterAdapter.restartNode(HBaseClusterAdapter.java:65)
```

**Analysis**: Master restart timeout from restarttest framework - FALSE POSITIVE.

**Test Executions (Examples)**:

1. Test: `TestFailedMetaReplicaAssigment_RestartInjected.testFailedReplicaAssignment`
   - "position": "after_replica_assigned"
   - "target": "master"
   - "mode": "GRACEFUL"
   - "executionDir": "092-ca117b4c"

2. Test: `TestReportRegionStateTransitionFromDeadServer_RestartInjected.test`
   - "position": "after_rs0_abort"
   - "target": "master"
   - "mode": "GRACEFUL"
   - "executionDir": "018-554bee24"

3. Test: `TestAdmin1_RestartInjected.testForceSplitMultiFamily`
   - "position": "after_region_split"
   - "target": "master"
   - "mode": "GRACEFUL"
   - "executionDir": "077-da573d57"

---

### Group 7: TestTimedOutException at Thread.sleep (FALSE POSITIVE)

[ ] Not started

**Test Executions**: 11 failures

**Generalized Stack Trace**:
```
org.junit.runners.model.TestTimedOutException
	at java.lang.Thread.sleep(Native Method)
```

---

### Group 23: TestTimedOutException at Unsafe.park (FALSE POSITIVE)

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
org.junit.runners.model.TestTimedOutException
	at sun.misc.Unsafe.park(Native Method)
```

---

### Group 24: MasterStoppedException (FALSE POSITIVE)

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.hadoop.hbase.exceptions.MasterStoppedException
	at org.apache.hadoop.hbase.master.HMaster.checkInitialized(HMaster.java)
```

**Analysis**: Master stopped during test - likely intentional test scenario.

---

### Group 25: IOException at startRegionServerAndWait (FALSE POSITIVE)

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: java.io.IOException
	at org.apache.hadoop.hbase.MiniHBaseCluster.startRegionServerAndWait(MiniHBaseCluster.java)
```

---

## Single-Failure Groups (Remaining)

The following groups have only 1 failure each and are sorted by exception type:

### NPEs in Test Code:
- Group 38, 42, 43, 45, 46, 50, 51, 52, 53, 54, 65, 67, 68, 69

### Other Exceptions:
- Group 39: RetriesExhaustedWithDetailsException
- Group 41: DoNotRetryRegionException
- Group 44: IOException at ProcedureSyncWait
- Group 47: RemoteWithExtrasException (MasterStoppedException)
- Group 56: IllegalMonitorStateException
- Group 57: InterruptedException
- Group 58: LocalConnectionClosedException
- Group 59: MasterStoppedException
- Group 60: NotServingRegionException
- Group 62: NullPointerException in TestReplicationStatus
- Group 63: DoNotRetryIOException at SplitTableRegionProcedure
- Group 64: NullPointerException in TestReplicationStatus (another)
- Group 66: NullPointerException at Preconditions.checkNotNull

---

## Summary Statistics

| Priority | Group IDs | Exception Category | Total Failures | Verdict |
|----------|-----------|-------------------|----------------|---------|
| HIGH | 15, 17, 22, 37, 55 | NPE/IOOB in HBase Core | 15 | Likely Bugs |
| HIGH | 3, 13, 18, 61 | IOOB/NoSuchElement | 35 | State Issues |
| MEDIUM-HIGH | 4, 5, 6, 10, 11, 12, 20, 21, 29, 34, 35 | State/Region Issues | 69 | Needs Inspection |
| MEDIUM | 9, 16, 19, 27, 28, 32, 36, 38+ | Test Code NPEs | ~30 | Test Issues |
| LOW | 8, 14, 30, 31, 33, 40, 49 | Connection Issues | ~25 | Transient |
| FALSE POSITIVE | 1, 2, 7, 23, 24, 25 | Restart Framework | 195 | Framework Issue |

**Total Groups**: 69
**Total Failures**: Approximately 400+ (non-assertion)
