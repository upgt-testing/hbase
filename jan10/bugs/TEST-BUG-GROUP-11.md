# TEST-BUG-GROUP-11: Stale ServerName Reference in Replication Queue Test

## Summary
The test `TestSerialReplicationChecker_RestartInjected` fails with `ZooKeeper NoNodeException` because it uses the current ServerName to update WAL positions after a regionserver restart, but the WAL was registered under the old ServerName.

## Exception
```
org.apache.hadoop.hbase.replication.ReplicationException: Failed to set log position (serverName=kingsland,36711,1768935317312, queueId=1, fileName=test.wal, position=10)
Caused by: org.apache.zookeeper.KeeperException$NoNodeException: KeeperErrorCode = NoNode
    at org.apache.zookeeper.KeeperException.create(KeeperException.java:118)
    at org.apache.zookeeper.ZooKeeper.multiInternal(ZooKeeper.java:1778)
    at org.apache.zookeeper.ZooKeeper.multi(ZooKeeper.java:1650)
    at org.apache.hadoop.hbase.zookeeper.RecoverableZooKeeper.multi(RecoverableZooKeeper.java:750)
    at org.apache.hadoop.hbase.zookeeper.ZKUtil.multiOrSequential(ZKUtil.java:1281)
    at org.apache.hadoop.hbase.replication.ZKReplicationQueueStorage.setWALPosition(ZKReplicationQueueStorage.java:245)
```

## Root Cause Analysis

### The Problem

1. **WAL Registration in `@BeforeClass`** (lines 105-108):
   ```java
   QUEUE_STORAGE = ReplicationStorageFactory.getReplicationQueueStorage(UTIL.getZooKeeperWatcher(),
     UTIL.getConfiguration());
   QUEUE_STORAGE.addWAL(UTIL.getMiniHBaseCluster().getRegionServer(0).getServerName(), PEER_ID,
     WAL_FILE_NAME);
   ```
   This registers the WAL file "test.wal" in ZooKeeper under the path:
   `/hbase/replication/rs/{ServerName_OLD}/1/test.wal`
   where `ServerName_OLD` = `hostname,port,timestamp_OLD`

2. **Restart Injection** (lines 208-213):
   ```java
   RestartFramework.at("after_add_state_barrier_first")
       .on(UTIL.getMiniHBaseCluster())
       .restart("regionserver")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```
   After the restart, the regionserver has a new ServerName with a different startcode (timestamp):
   `ServerName_NEW` = `hostname,port,timestamp_NEW`

3. **updatePushedSeqId() after restart** (lines 190-193 and 229):
   ```java
   private void updatePushedSeqId(RegionInfo region, long seqId) throws ReplicationException {
     QUEUE_STORAGE.setWALPosition(UTIL.getMiniHBaseCluster().getRegionServer(0).getServerName(),
       PEER_ID, WAL_FILE_NAME, 10, ImmutableMap.of(region.getEncodedName(), seqId));
   }
   ```
   This method uses `getRegionServer(0).getServerName()` which returns `ServerName_NEW`, not `ServerName_OLD`.

4. **ZooKeeper Path Mismatch**:
   - The `setWALPosition()` method tries to update the node at:
     `/hbase/replication/rs/{ServerName_NEW}/1/test.wal`
   - But this path doesn't exist because the WAL was registered under:
     `/hbase/replication/rs/{ServerName_OLD}/1/test.wal`
   - Result: `NoNodeException`

### ZooKeeper Path Structure (from ZKReplicationQueueStorage.java)
```java
// Lines 119-134:
@Override
public String getRsNode(ServerName serverName) {
  return ZNodePaths.joinZNode(queuesZNode, serverName.getServerName());
}

private String getQueueNode(ServerName serverName, String queueId) {
  return ZNodePaths.joinZNode(getRsNode(serverName), queueId);
}

private String getFileNode(ServerName serverName, String queueId, String fileName) {
  return getFileNode(getQueueNode(serverName, queueId), fileName);
}
```

## Classification: TEST-BUG

This is a TEST-BUG because:
1. The test code doesn't account for ServerName changes after regionserver restart
2. The test should store the original ServerName in a variable and use it consistently
3. In production HBase, replication queues are properly managed during server failover/restart, but this test doesn't simulate that behavior

## Affected Tests
- `TestSerialReplicationChecker_RestartInjected.testLastRegionAndOpeningCanNotPush`
- `TestSerialReplicationChecker_RestartInjected.testCanPushUnder`
- `TestSerialReplicationChecker_RestartInjected.testCanPushIfContinuous`
- `TestSerialReplicationChecker_RestartInjected.testCanPushAfterMerge`
- `TestSerialReplicationChecker_RestartInjected.testCanPushAfterSplit`
- `TestSerialReplicationChecker_RestartInjected.testCanPushEqualsToBarrier`

All tests that call `updatePushedSeqId()` after a restart injection point will fail.

## Proposed Fix

Store the ServerName when registering the WAL and use it consistently:

```java
// Add a static field to store the original ServerName
private static ServerName REPLICATION_SERVER_NAME;

@BeforeClass
public static void setUpBeforeClass() throws Exception {
  UTIL.startMiniCluster(1);
  RestartFramework.at("after_cluster_start")
      .on(UTIL.getMiniHBaseCluster())
      .restart("regionserver")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();

  // Store the ServerName when registering the WAL
  REPLICATION_SERVER_NAME = UTIL.getMiniHBaseCluster().getRegionServer(0).getServerName();

  QUEUE_STORAGE = ReplicationStorageFactory.getReplicationQueueStorage(UTIL.getZooKeeperWatcher(),
    UTIL.getConfiguration());
  QUEUE_STORAGE.addWAL(REPLICATION_SERVER_NAME, PEER_ID, WAL_FILE_NAME);

  RestartFramework.at("after_wal_addition")
      .on(UTIL.getMiniHBaseCluster())
      .restart("regionserver")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();
}

// Modify updatePushedSeqId to use the stored ServerName
private void updatePushedSeqId(RegionInfo region, long seqId) throws ReplicationException {
  QUEUE_STORAGE.setWALPosition(REPLICATION_SERVER_NAME,
    PEER_ID, WAL_FILE_NAME, 10, ImmutableMap.of(region.getEncodedName(), seqId));
}
```

## Alternative Fix

Re-register the WAL with the new ServerName after each restart (simulating real HBase behavior where queue ownership is transferred):

```java
// After each restart that could change the ServerName, re-register the WAL
private void reregisterWALAfterRestart() throws ReplicationException {
  ServerName newServerName = UTIL.getMiniHBaseCluster().getRegionServer(0).getServerName();
  QUEUE_STORAGE.addWAL(newServerName, PEER_ID, WAL_FILE_NAME);
}
```

## Verification

The failure was reproduced using:
```bash
mvn surefire:test -Dtest=TestSerialReplicationChecker_RestartInjected#testLastRegionAndOpeningCanNotPush \
  -Drestart.position=after_add_state_barrier_first \
  -Drestart.target=regionserver \
  -Drestart.mode=GRACEFUL \
  -Drestart.tracking.agent=/home/shuai/xlab/restart_testing/RestartTestingFramework/restart-tracking-agent/target/restart-tracking-agent-1.0.0-SNAPSHOT.jar
```
