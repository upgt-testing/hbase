# TEST-BUG-GROUP-21: NotServingRegionException due to stale server reference after restart

## Summary

After regionserver restart, the test code continues to use stale `HRegionServer` references obtained before the restart. The region may have been reassigned to a different server, causing `NotServingRegionException` when the test attempts to access the region on the old server reference.

## Failure Details

**Exception**: `org.apache.hadoop.hbase.NotServingRegionException`

**Stack Trace**:
```
org.apache.hadoop.hbase.NotServingRegionException: testMoveRegion,,1768935914935.eebe7321142af990faac280a20c68f58. is not online on kingsland,41643,1768935915834
    at org.apache.hadoop.hbase.regionserver.HRegionServer.getRegionByEncodedName(HRegionServer.java:3681)
    at org.apache.hadoop.hbase.regionserver.HRegionServer.getRegion(HRegionServer.java:3659)
    at org.apache.hadoop.hbase.regionserver.TestRemoveRegionMetrics_RestartInjected.testMoveRegion(TestRemoveRegionMetrics_RestartInjected.java:126)
```

**Test Executions**:
1. `TestRegionServerNoMaster_RestartInjected.testOpenCloseRegionRPCIntendedForPreviousServer` - position: `after_put_data`, target: `regionserver` (did not reproduce)
2. `TestRemoveRegionMetrics_RestartInjected.testMoveRegion` - position: `after_first_put`, target: `regionserver` (REPRODUCED)
3. `TestRemoveRegionMetrics_RestartInjected.testMoveRegion` - position: `after_mid_put`, target: `regionserver`

## Root Cause Analysis

### Buggy Code Location

`TestRemoveRegionMetrics_RestartInjected.java` lines 103-126:

```java
for (int i = 0; i < 30; i++) {
  boolean moved = false;
  try (RegionLocator locator = TEST_UTIL.getConnection().getRegionLocator(tableName)) {
    regionInfo = locator.getRegionLocation(row, true).getRegionInfo();  // line 103-105
  }

  int currentServerIdx = cluster.getServerWith(regionInfo.getRegionName());  // line 107
  int destServerIdx = (currentServerIdx + 1) % cluster.getLiveRegionServerThreads().size();
  HRegionServer currentServer = cluster.getRegionServer(currentServerIdx);  // line 109
  HRegionServer destServer = cluster.getRegionServer(destServerIdx);

  // Do a put. The counters should be non-zero now
  Put p = new Put(row);
  p.addColumn(Bytes.toBytes("D"), Bytes.toBytes("Zero"), Bytes.toBytes("VALUE"));
  t.put(p);

  if (i == 0) {
    RestartFramework.at("after_first_put")
        .on(cluster)
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();  // lines 118-123 - RESTART HAPPENS HERE
  }

  // BUG: Using stale currentServer reference after restart
  MetricsRegionAggregateSource currentAgg = currentServer.getRegion(regionInfo.getRegionName())  // line 126
    .getMetrics().getSource().getAggregateSource();
```

### Problem

1. **Before restart** (lines 103-110): The test obtains:
   - `regionInfo` - the region info from the region locator
   - `currentServerIdx` - the index of the server currently hosting the region
   - `currentServer` - the HRegionServer object at that index

2. **Restart occurs** (lines 118-123): When i==0, the test restarts regionserver at index 0
   - The regionserver at index 0 is stopped
   - A new regionserver is started and added to the cluster
   - Regions are reassigned by the master

3. **After restart** (line 126): The test uses stale references:
   - `currentServer` may now be the stopped server (if currentServerIdx was 0)
   - Or `currentServer` may be a running server that no longer hosts the region (due to reassignment)
   - When `currentServer.getRegion(regionInfo.getRegionName())` is called, the region is not found

### Why NotServingRegionException Occurs

In `HRegionServer.getRegionByEncodedName()` (HRegionServer.java:3666-3682):

```java
private HRegion getRegionByEncodedName(byte[] regionName, String encodedRegionName)
  throws NotServingRegionException {
  HRegion region = this.onlineRegions.get(encodedRegionName);
  if (region == null) {
    // ... checks for moved regions and opening regions ...
    throw new NotServingRegionException(
      "" + regionNameStr + " is not online on " + this.serverName);
  }
  return region;
}
```

After restart:
- If the server was stopped, `onlineRegions` is empty
- If the region was reassigned to another server, `onlineRegions` doesn't contain this region
- Either way, `getRegionByEncodedName()` throws `NotServingRegionException`

## Verdict

**TEST-BUG**: The test code doesn't properly handle post-restart state. It should refresh its references after a restart to account for potential region reassignment.

## Proposed Fix

After the restart, refresh the server references and wait for region availability:

```java
if (i == 0) {
    RestartFramework.at("after_first_put")
        .on(cluster)
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // FIX: Wait for table to be available and refresh references
    TEST_UTIL.waitTableAvailable(tableName);

    // Re-query to get the correct server now hosting the region
    try (RegionLocator locator = TEST_UTIL.getConnection().getRegionLocator(tableName)) {
        regionInfo = locator.getRegionLocation(row, true).getRegionInfo();
    }
    currentServerIdx = cluster.getServerWith(regionInfo.getRegionName());
    destServerIdx = (currentServerIdx + 1) % cluster.getLiveRegionServerThreads().size();
    currentServer = cluster.getRegionServer(currentServerIdx);
    destServer = cluster.getRegionServer(destServerIdx);
}
```

## Impact

This is a common pattern in tests that store server/region references before a restart. Similar issues may exist in:
- `TestRegionServerNoMaster_RestartInjected`
- Other tests that access regions by server reference after restart

## Related Groups

This is similar to the pattern in:
- TEST-BUG-GROUP-3: Test doesn't wait for regions after restart
- TEST-BUG-GROUP-4: FailedServerException after restart
- TEST-BUG-GROUP-5: DoNotRetryRegionException after restart
