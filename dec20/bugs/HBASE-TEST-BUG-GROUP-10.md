# HBASE-TEST-BUG-GROUP-10: Test uses stale ServerName after RegionServer restart

## Summary

`TestSerialReplicationChecker_RestartInjected::testLastRegionAndOpeningCanNotPush` fails with `KeeperException$NoNodeException` because the test uses a stale ServerName reference after RegionServer restart. The ZK-based replication queue storage paths include the ServerName's startcode (timestamp), which changes when the server restarts.

## Root Cause Analysis

### How ServerName Works

HBase's `ServerName` class (hbase-common/src/main/java/org/apache/hadoop/hbase/ServerName.java:36-46) uniquely identifies a server instance using three components:
- hostname
- port
- **startcode** (typically the server startup timestamp)

From the javadoc:
> "The startcode distinguishes restarted servers on same hostname and port (startcode is usually timestamp of server startup)"

### The Test Bug

1. **In `@BeforeClass` (lines 107-108)**, the test adds a WAL to the replication queue storage:
```java
QUEUE_STORAGE.addWAL(UTIL.getMiniHBaseCluster().getRegionServer(0).getServerName(), PEER_ID, WAL_FILE_NAME);
```
This creates a ZK node at path: `/hbase/replication/rs/<hostname>,<port>,<OLD_STARTCODE>/1/test.wal`

2. **In the test method `testLastRegionAndOpeningCanNotPush()` (lines 208-213)**, a restart injection occurs:
```java
RestartFramework.at("after_add_state_barrier_first")
    .on(UTIL.getMiniHBaseCluster())
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
```

3. **After restart, at line 229**, `updatePushedSeqId()` is called which calls:
```java
QUEUE_STORAGE.setWALPosition(UTIL.getMiniHBaseCluster().getRegionServer(0).getServerName(),
  PEER_ID, WAL_FILE_NAME, 10, ImmutableMap.of(region.getEncodedName(), seqId));
```

After restart, `getRegionServer(0).getServerName()` returns a **NEW** ServerName with a **NEW** startcode (new timestamp), e.g., `<hostname>,<port>,<NEW_STARTCODE>`.

4. **`ZKReplicationQueueStorage.setWALPosition()`** (hbase-replication/src/main/java/org/apache/hadoop/hbase/replication/ZKReplicationQueueStorage.java:236) tries to update:
```
/hbase/replication/rs/<hostname>,<port>,<NEW_STARTCODE>/1/test.wal
```

But this node doesn't exist because the WAL was added with the OLD startcode!

## Buggy Code Location

**File:** `hbase-server/src/test/java/org/apache/hadoop/hbase/replication/regionserver/TestSerialReplicationChecker_RestartInjected.java`

**Buggy method `updatePushedSeqId()` (line 190-193):**
```java
private void updatePushedSeqId(RegionInfo region, long seqId) throws ReplicationException {
  QUEUE_STORAGE.setWALPosition(UTIL.getMiniHBaseCluster().getRegionServer(0).getServerName(),
    PEER_ID, WAL_FILE_NAME, 10, ImmutableMap.of(region.getEncodedName(), seqId));
}
```

The method always gets the **current** ServerName from the cluster, but the WAL was registered with the **original** ServerName in `@BeforeClass`.

## Stack Trace

```
org.apache.hadoop.hbase.replication.ReplicationException: Failed to set log position (serverName=kingsland,46767,1767660978144, queueId=1, fileName=test.wal, position=10)
	at org.apache.hadoop.hbase.replication.ZKReplicationQueueStorage.setWALPosition(ZKReplicationQueueStorage.java:255)
	at org.apache.hadoop.hbase.replication.regionserver.TestSerialReplicationChecker_RestartInjected.updatePushedSeqId(TestSerialReplicationChecker_RestartInjected.java:191)
	at org.apache.hadoop.hbase.replication.regionserver.TestSerialReplicationChecker_RestartInjected.testLastRegionAndOpeningCanNotPush(TestSerialReplicationChecker_RestartInjected.java:229)
	...
Caused by: org.apache.zookeeper.KeeperException$NoNodeException: KeeperErrorCode = NoNode
	at org.apache.zookeeper.KeeperException.create(KeeperException.java:118)
	at org.apache.zookeeper.ZooKeeper.multiInternal(ZooKeeper.java:1778)
	at org.apache.zookeeper.ZooKeeper.multi(ZooKeeper.java:1650)
	at org.apache.hadoop.hbase.zookeeper.RecoverableZooKeeper.multi(RecoverableZooKeeper.java:750)
	at org.apache.hadoop.hbase.zookeeper.ZKUtil.multiOrSequential(ZKUtil.java:1281)
	at org.apache.hadoop.hbase.replication.ZKReplicationQueueStorage.setWALPosition(ZKReplicationQueueStorage.java:245)
	... 28 more
```

## Potential Fix

### Option 1: Cache the original ServerName

Store the original ServerName in a static field and use it consistently:

```java
private static ServerName ORIGINAL_SERVER_NAME;

@BeforeClass
public static void setUpBeforeClass() throws Exception {
  UTIL.startMiniCluster(1);
  // ... restart injection ...
  ORIGINAL_SERVER_NAME = UTIL.getMiniHBaseCluster().getRegionServer(0).getServerName();
  QUEUE_STORAGE = ReplicationStorageFactory.getReplicationQueueStorage(UTIL.getZooKeeperWatcher(),
    UTIL.getConfiguration());
  QUEUE_STORAGE.addWAL(ORIGINAL_SERVER_NAME, PEER_ID, WAL_FILE_NAME);
  // ... restart injection ...
}

private void updatePushedSeqId(RegionInfo region, long seqId) throws ReplicationException {
  // Use the cached original ServerName instead of getting the current one
  QUEUE_STORAGE.setWALPosition(ORIGINAL_SERVER_NAME,
    PEER_ID, WAL_FILE_NAME, 10, ImmutableMap.of(region.getEncodedName(), seqId));
}
```

### Option 2: Re-register WAL after restart

After any restart that affects the RegionServer identity, re-add the WAL with the new ServerName:

```java
// After restart, re-add WAL with new ServerName
ServerName newServerName = UTIL.getMiniHBaseCluster().getRegionServer(0).getServerName();
QUEUE_STORAGE.addWAL(newServerName, PEER_ID, WAL_FILE_NAME);
```

## Classification

**Type:** TEST-BUG

**Reason:** This is not a bug in HBase production code. The ZK-based replication queue storage correctly uses ServerName (including startcode) to uniquely identify server instances. The test code incorrectly assumes that `getRegionServer(0).getServerName()` will return the same value before and after a restart, which is not true because startcode changes.

## Affected Tests

All test methods in `TestSerialReplicationChecker_RestartInjected` that:
1. Add WAL in `@BeforeClass` with original ServerName
2. Inject restart before calling `updatePushedSeqId()`
3. Call `updatePushedSeqId()` which uses current (post-restart) ServerName

Test executions in this group: 8
