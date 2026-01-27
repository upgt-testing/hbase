# TEST-BUG-GROUP-34: Stale Server Reference After Regionserver Restart

## Summary
The test `TestRemoveRegionMetrics_RestartInjected.testMoveRegion` uses stale `destServer` reference after a regionserver restart, causing `RegionMovedException`.

## Exception
```
org.apache.hadoop.hbase.exceptions.RegionMovedException: Region moved to: hostname=KingsLand port=39197 startCode=1768936869498. As of locationSeqNum=117.
    at org.apache.hadoop.hbase.regionserver.HRegionServer.getRegionByEncodedName(HRegionServer.java:3672)
    at org.apache.hadoop.hbase.regionserver.HRegionServer.getRegion(HRegionServer.java:3659)
    at org.apache.hadoop.hbase.regionserver.TestRemoveRegionMetrics_RestartInjected.testMoveRegion(TestRemoveRegionMetrics_RestartInjected.java:169)
```

## Root Cause Analysis

### Buggy Test Code Location
File: `hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRemoveRegionMetrics_RestartInjected.java`

### Flow of Events
1. **Line 107-110**: The test stores server references at the start of each loop iteration:
   ```java
   int currentServerIdx = cluster.getServerWith(regionInfo.getRegionName());
   int destServerIdx = (currentServerIdx + 1) % cluster.getLiveRegionServerThreads().size();
   HRegionServer currentServer = cluster.getRegionServer(currentServerIdx);
   HRegionServer destServer = cluster.getRegionServer(destServerIdx);
   ```

2. **Line 135**: The test moves the region to `destServer`:
   ```java
   TEST_UTIL.moveRegionAndWait(regionInfo, destServer.getServerName());
   ```

3. **Lines 159-166**: After `i==15` and the region moved successfully, a restart is injected:
   ```java
   if (i == 15 && moved) {
     RestartFramework.at("after_mid_region_move")
         .on(cluster)
         .restart("regionserver")
         .withIndex(0)
         .withMode(RestartMode.GRACEFUL)
         .execute();
   }
   ```

4. **Lines 168-172**: The test tries to use the stale `destServer` reference:
   ```java
   if (moved) {
     MetricsRegionAggregateSource destAgg = destServer.getRegion(regionInfo.getRegionName())
       .getMetrics().getSource().getAggregateSource();
     metricsHelper.assertCounter(prefix + "_putCount", 0, destAgg);
   }
   ```

### Why This Fails
After the regionserver restart at `after_mid_region_move`:
1. If the restarted server (`index 0`) was the `destServer`, the region is no longer on that server
2. During the restart, the region gets reassigned by the master to a different server
3. When `destServer.getRegion(regionInfo.getRegionName())` is called:
   - The region is not in `destServer.onlineRegions`
   - But there's a `MovedRegionInfo` entry for this region (indicating it was moved)
   - `HRegionServer.getRegionByEncodedName()` throws `RegionMovedException`

### Source Code Where Exception is Thrown
File: `HRegionServer.java` (lines 3666-3672):
```java
private HRegion getRegionByEncodedName(byte[] regionName, String encodedRegionName)
  throws NotServingRegionException {
  HRegion region = this.onlineRegions.get(encodedRegionName);
  if (region == null) {
    MovedRegionInfo moveInfo = getMovedRegion(encodedRegionName);
    if (moveInfo != null) {
      throw new RegionMovedException(moveInfo.getServerName(), moveInfo.getSeqNum());
    }
    // ... more code
  }
}
```

## Why This is a TEST-BUG (Not a Source Code Bug)
1. The HBase source code is behaving correctly - it detects that the region has moved and throws an appropriate exception
2. The test code incorrectly assumes that `destServer` still hosts the region after a restart
3. The test does not re-fetch server references after the restart
4. The test does not wait for the region to be reassigned and available after the restart

## Proposed Fix
After the restart injection, the test should:
1. Wait for the table to be available
2. Re-fetch the region location and server references
3. Skip the metrics assertion if the region moved to a different server

### Fixed Code
```java
if (i == 15 && moved) {
  RestartFramework.at("after_mid_region_move")
      .on(cluster)
      .restart("regionserver")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();

  // After restart, wait for table availability and skip this iteration's assertion
  // since the region location may have changed
  TEST_UTIL.waitTableAvailable(tableName);
  continue;  // Skip the metrics assertion for this iteration
}
```

Alternatively, re-fetch the server reference:
```java
if (i == 15 && moved) {
  RestartFramework.at("after_mid_region_move")
      .on(cluster)
      .restart("regionserver")
      .withIndex(0)
      .withMode(RestartMode.GRACEFUL)
      .execute();

  // Re-fetch the region location and server after restart
  TEST_UTIL.waitTableAvailable(tableName);
  try (RegionLocator locator = TEST_UTIL.getConnection().getRegionLocator(tableName)) {
    regionInfo = locator.getRegionLocation(row, true).getRegionInfo();
  }
  int newDestServerIdx = cluster.getServerWith(regionInfo.getRegionName());
  destServer = cluster.getRegionServer(newDestServerIdx);
}
```

## Test Executions That Failed
1. `position=after_first_region_move, target=regionserver, mode=GRACEFUL` (did not reproduce)
2. `position=after_mid_region_move, target=regionserver, mode=GRACEFUL` (reproduced)

## Verdict
**TEST-BUG**: The test code maintains stale `destServer` reference after regionserver restart. The HBase source code correctly throws `RegionMovedException` when the region is no longer on the expected server. The test should wait for table availability and re-fetch server references after restart.
