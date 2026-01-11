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

[ ] Not started

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

**Analysis**: ArrayIndexOutOfBoundsException in CopyOnWriteArrayList suggests a potential concurrency bug where the list is being modified during iteration/access after restart.

**Test Executions (Examples)**:

1. Test: Check execution details in step3_grouped_failures.json for group_id 15
   - Likely involves concurrent access during region server restart

---

### Group 17: NullPointerException in BucketCache.parsePB

[ ] Not started

**Test Executions**: 4 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.NullPointerException
	at org.apache.hadoop.hbase.io.hfile.bucket.BucketCache.parsePB(BucketCache.java)
```

**Analysis**: NPE in BucketCache.parsePB indicates potential uninitialized state in HBase's block cache implementation after restart.

---

### Group 22: NullPointerException in HMaster.getReplicationLoad

[ ] Not started

**Test Executions**: 3 failures

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.master.HMaster.getReplicationLoad(HMaster.java)
```

**Analysis**: NPE in HMaster.getReplicationLoad suggests master not fully initialized when replication load is queried after restart.

---

### Group 37: NullPointerException in MasterCoprocessorHost.createEnvironment

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.master.MasterCoprocessorHost.createEnvironment(MasterCoprocessorHost.java)
```

**Analysis**: NPE during coprocessor environment creation indicates initialization order issue.

---

### Group 55: NullPointerException in RegionRemoteProcedureBase.getParent

[ ] Not started

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.master.assignment.RegionRemoteProcedureBase.getParent(RegionRemoteProcedureBase.java)
```

**Analysis**: NPE in procedure framework suggests parent procedure reference lost after restart.

---

### Group 3: IndexOutOfBoundsException in Test Code

[ ] Not started

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

**Analysis**: Although thrown from test code, IOOB on empty list suggests region list is empty when it shouldn't be after restart. Could indicate region assignment issue.

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

[ ] Not started

**Test Executions**: 6 failures

**Generalized Stack Trace**:
```
java.util.NoSuchElementException
	at java.util.ArrayList$Itr.next(ArrayList.java)
```

**Analysis**: Iterator running past end of list - potential concurrent modification or state inconsistency.

---

### Group 18: NoSuchElementException in Optional.get

[ ] Not started

**Test Executions**: 4 failures

**Generalized Stack Trace**:
```
java.util.NoSuchElementException
	at java.util.Optional.get(Optional.java)
```

**Analysis**: Calling Optional.get() on empty optional - expected value missing after restart.

---

### Group 61: ArrayIndexOutOfBoundsException (Caused by)

[ ] Not started

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
Caused by: java.lang.ArrayIndexOutOfBoundsException
	at java.util.concurrent.CopyOnWriteArrayList.get(CopyOnWriteArrayList.java)
```

**Analysis**: Another concurrency issue with CopyOnWriteArrayList.

---

## MEDIUM-HIGH PRIORITY - State Issues

### Group 10: IllegalArgumentException in Preconditions.checkArgument

[ ] Not started

**Test Executions**: 8 failures

**Generalized Stack Trace**:
```
java.lang.IllegalArgumentException
	at org.apache.hbase.thirdparty.com.google.common.base.Preconditions.checkArgument(Preconditions.java)
```

**Analysis**: Precondition violation suggests invalid state or arguments after restart.

---

### Group 4: FailedServerException

[ ] Not started

**Test Executions**: 13 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.hadoop.hbase.ipc.FailedServerException
	at org.apache.hadoop.hbase.ipc.AbstractRpcClient.getConnection(AbstractRpcClient.java)
```

**Analysis**: RPC client failing to connect to server marked as failed - may indicate stale server state.

---

### Group 5: DoNotRetryRegionException - Region Not Online

[ ] Not started

**Test Executions**: 12 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.hadoop.hbase.ipc.RemoteWithExtrasException(org.apache.hadoop.hbase.client.DoNotRetryRegionException)
	at org.apache.hadoop.hbase.master.assignment.RegionStateNode.checkOnline(RegionStateNode.java)
```

**Analysis**: Region not online when expected - potential region assignment issue after restart.

---

### Group 12: DoNotRetryRegionException

[ ] Not started

**Test Executions**: 7 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.hadoop.hbase.client.DoNotRetryRegionException
	at org.apache.hadoop.hbase.master.assignment.RegionStateNode.checkOnline(RegionStateNode.java)
```

---

### Group 6: IOException in ProcedureSyncWait

[ ] Not started

**Test Executions**: 12 failures

**Generalized Stack Trace**:
```
Caused by: java.io.IOException
	at org.apache.hadoop.hbase.master.procedure.ProcedureSyncWait.waitForProcedureToComplete(ProcedureSyncWait.java)
```

---

### Group 11: ZooKeeper NoNodeException

[ ] Not started

**Test Executions**: 8 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.zookeeper.KeeperException$NoNodeException
	at org.apache.zookeeper.KeeperException.create(KeeperException.java)
```

**Analysis**: ZooKeeper node missing after restart - potential coordination issue.

---

### Group 20: IOException at MetaTableAccessor

[ ] Not started

**Test Executions**: 3 failures

**Generalized Stack Trace**:
```
java.io.IOException
	at org.apache.hadoop.hbase.MetaTableAccessor.getMetaHTable(MetaTableAccessor.java)
```

---

### Group 21: NotServingRegionException

[ ] Not started

**Test Executions**: 3 failures

**Generalized Stack Trace**:
```
org.apache.hadoop.hbase.NotServingRegionException
	at org.apache.hadoop.hbase.regionserver.HRegionServer.getRegionByEncodedName(HRegionServer.java)
```

---

### Group 29: NotServingRegionException (Caused by)

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.hadoop.hbase.NotServingRegionException
	at org.apache.hadoop.hbase.regionserver.HRegionServer.closeRegion(HRegionServer.java)
```

---

### Group 34: RegionMovedException

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
org.apache.hadoop.hbase.exceptions.RegionMovedException
	at org.apache.hadoop.hbase.regionserver.HRegionServer.getRegionByEncodedName(HRegionServer.java)
```

---

### Group 35: UnknownScannerException

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.hadoop.hbase.UnknownScannerException
	at org.apache.hadoop.hbase.regionserver.RSRpcServices.getRegionScanner(RSRpcServices.java)
```

---

## MEDIUM PRIORITY - Test Code NPEs

### Group 9: NullPointerException in Test (TestRSMobFileCleanerChore)

[ ] Not started

**Test Executions**: 9 failures

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.mob.TestRSMobFileCleanerChore_RestartInjected.testMobFileCleanerChore(TestRSMobFileCleanerChore_RestartInjected.java)
```

---

### Group 16: ClassCastException in Test

[ ] Not started

**Test Executions**: 5 failures

**Generalized Stack Trace**:
```
java.lang.ClassCastException
	at org.apache.hadoop.hbase.master.assignment.TestRegionBypass_RestartInjected.testBypass(TestRegionBypass_RestartInjected.java)
```

---

### Group 19: NullPointerException in Test (TestFlushWithThroughputController)

[ ] Not started

**Test Executions**: 4 failures

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.regionserver.throttle.TestFlushWithThroughputController_RestartInjected.generateAndFlushData(TestFlushWithThroughputController_RestartInjected.java)
```

---

### Group 27: NullPointerException in Test (TestCompactSplitThread)

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.regionserver.TestCompactSplitThread_RestartInjected.testThreadPoolSizeTuning(TestCompactSplitThread_RestartInjected.java)
```

---

### Group 28: NullPointerException in Test (TestRegionMover2)

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.hadoop.hbase.util.TestRegionMover2_RestartInjected.regionIsolationOperation(TestRegionMover2_RestartInjected.java)
```

---

### Group 32: ClassCastException in Test (TestRegionReplicasAreDistributed)

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
java.lang.ClassCastException
	at org.apache.hadoop.hbase.regionserver.TestRegionReplicasAreDistributed_RestartInjected.checkAndAssertRegionDistribution(TestRegionReplicasAreDistributed_RestartInjected.java)
```

---

### Group 36: NullPointerException in Test (TestQuotaObserverChoreWithMiniCluster)

[ ] Not started

**Test Executions**: 2 failures

---

### Group 38: NullPointerException in Test (TestRSMobFileCleanerChore) - Another Method

[ ] Not started

**Test Executions**: 1 failure

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
