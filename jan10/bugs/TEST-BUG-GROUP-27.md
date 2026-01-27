# TEST-BUG-GROUP-27: Stale HRegionServer Reference After Restart

## Summary

The test `TestCompactSplitThread_RestartInjected.testThreadPoolSizeTuning` uses a stale `HRegionServer` reference after a regionserver restart, causing a `NullPointerException` when calling `getCompactSplitThread()` on the stopped server.

## Root Cause

The test stores a `HRegionServer` reference at line 123:
```java
HRegionServer regionServer = TEST_UTIL.getRSForFirstRegionInTable(tableName);
```

After the restart injection at lines 140-145:
```java
RestartFramework.at("after_config_update_bigger")
    .on(TEST_UTIL.getMiniHBaseCluster())
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
```

The test continues to use the same `regionServer` reference at line 148:
```java
assertEquals(4, regionServer.getCompactSplitThread().getLargeCompactionThreadNum());
```

However, after the restart:
- The original `regionServer` instance has been **stopped** (`isStopped=true`)
- Its `CompactSplitThread` has been cleaned up and set to `null`
- Calling `getCompactSplitThread()` returns `null`, causing NPE when calling `.getLargeCompactionThreadNum()`

## Debug Evidence

Added debug logging confirmed the issue:
```
DEBUG: After restart, regionServer isOnline=true
DEBUG: After restart, regionServer isStopped=true
DEBUG: After restart, regionServer.getCompactSplitThread()=null
```

## Affected Test Executions

1. **Test**: `TestCompactSplitThread_RestartInjected.testThreadPoolSizeTuning`
   - **Position**: `after_config_update_bigger`
   - **Target**: `regionserver`
   - **Mode**: `GRACEFUL`

2. **Test**: `TestCompactSplitThread_RestartInjected.testThreadPoolSizeTuning`
   - **Position**: `after_config_update_smaller`
   - **Target**: `regionserver`
   - **Mode**: `GRACEFUL`

## Buggy Code Location

**File**: `hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestCompactSplitThread_RestartInjected.java`

**Lines**: 123-172

```java
// Line 123: Reference stored before restart
HRegionServer regionServer = TEST_UTIL.getRSForFirstRegionInTable(tableName);

// ... configuration changes ...

// Lines 140-145: Restart injected
RestartFramework.at("after_config_update_bigger")
    .on(TEST_UTIL.getMiniHBaseCluster())
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// Line 148: STALE reference used - causes NPE
assertEquals(4, regionServer.getCompactSplitThread().getLargeCompactionThreadNum());
```

## Proposed Fix

After each regionserver restart, the test should refresh the `regionServer` reference and wait for the table to be available:

```java
RestartFramework.at("after_config_update_bigger")
    .on(TEST_UTIL.getMiniHBaseCluster())
    .restart("regionserver")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// FIX: Refresh regionServer reference after restart
TEST_UTIL.waitTableAvailable(tableName);
regionServer = TEST_UTIL.getRSForFirstRegionInTable(tableName);

// Now safe to use the fresh reference
assertEquals(4, regionServer.getCompactSplitThread().getLargeCompactionThreadNum());
```

Similarly, the same fix should be applied after the second restart at `after_config_update_smaller`.

## Classification

**Type**: TEST-BUG

**Reason**: The HBase source code is correct. The issue is that the test code fails to refresh object references after injecting a restart. This is a common pattern issue in restart-injected tests where stored references to regionservers become stale after restart.

## Related Patterns

This is the same pattern as other TEST-BUGs:
- Group 9: Stale `ServerName` reference after regionserver restart
- Group 21: Stale `HRegionServer` reference after regionserver restart
- Group 34: Stale `destServer` reference after regionserver restart

The general rule is: **After any restart injection, all references to the restarted component must be refreshed.**
