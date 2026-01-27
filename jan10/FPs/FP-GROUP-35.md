# FP-GROUP-35: UnknownScannerException After RegionServer Restart

## Classification
**FALSE POSITIVE** - Expected behavior during regionserver restart, explicitly documented in the exception message.

## Summary
The `UnknownScannerException` occurs when a regionserver restart is injected mid-scan. This is **not a bug** because:
1. Scanner state is ephemeral (in-memory only) and cannot survive restarts
2. The exception message explicitly documents "RegionServer restart during upgrade" as an expected cause
3. The HBase client has recovery logic for this exception, but test configuration limits retries

## Test Information
- **Test Class**: `TestRegionServerScan_RestartInjected`
- **Test Method**: `testScannWhenRpcCallContextNull`
- **Restart Position**: `after_first_scan_result` / `after_second_scan_result`
- **Restart Target**: `regionserver`
- **Restart Mode**: `GRACEFUL`

## Stack Trace
```
org.apache.hadoop.hbase.client.RetriesExhaustedException: Failed after attempts=2, exceptions:
...ConnectException: Connection refused...  (regionserver still restarting)
...UnknownScannerException: Unknown scanner '5635997841206804489'. This can happen due to any of the following reasons:
   a) Scanner id given is wrong,
   b) Scanner lease expired because of long wait between consecutive client checkins,
   c) Server may be closing down,
   d) RegionServer restart during upgrade.
```

## Technical Analysis

### 1. Scanner State is Ephemeral (By Design)
Scanner IDs are stored in the `RegionScannerHolder` map on the regionserver, which is purely in-memory:
- When regionserver restarts, all scanner state is lost
- Client-held scanner IDs become invalid
- This is not a bug - it's fundamental to how HBase scanners work

### 2. Exception Message Documents This Behavior
The error at `RSRpcServices.java:3191-3196` explicitly states this is expected:
```java
throw new UnknownScannerException(
  "Unknown scanner '" + scannerName + "'. This can happen due to any of the following "
    + "reasons: a) Scanner id given is wrong, b) Scanner lease expired because of "
    + "long wait between consecutive client checkins, c) Server may be closing down, "
    + "d) RegionServer restart during upgrade.\nIf the issue is due to reason (b), a "
    + "possible fix would be increasing the value of"
    + "'hbase.client.scanner.timeout.period' configuration.");
```

### 3. HBase Client Has Recovery Logic
`ClientScanner.handleScanError()` explicitly handles `UnknownScannerException`:
```java
// At ClientScanner.java:394-395
if (
  (cause != null && cause instanceof NotServingRegionException)
    || (cause != null && cause instanceof RegionServerStoppedException)
    || e instanceof OutOfOrderScannerNextException || e instanceof UnknownScannerException
    || e instanceof ScannerResetException || e instanceof LeaseException
) {
  // Pass - these exceptions trigger scanner reset and retry
  if (retriesLeft <= 0) {
    throw e; // no more retries
  }
}
```

### 4. Test Configuration Limits Recovery
The test configuration restricts retries:
```java
conf.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 1);  // Only 1 retry
```

The failure sequence:
1. **First attempt**: `ConnectException` (regionserver still restarting)
2. **Second attempt**: `UnknownScannerException` (scanner lost after restart)
3. **No more retries** → Exception thrown to application

With more retries configured, the client would eventually:
- Wait for the server to come back up
- Receive `UnknownScannerException`
- Reset scanner and continue from last seen row

### 5. Improper Restart Position
The restart is injected at `after_first_scan_result`, which means:
- Scanner is in active use
- Client has partial results
- Server is holding scanner state

Restarting at this point tests the documented edge case, not a bug.

## Why This Is NOT a Bug

1. **Self-Documenting**: The exception explicitly lists "RegionServer restart" as a known cause
2. **By Design**: Scanner state is inherently tied to the server process lifecycle
3. **Client Recovery Exists**: HBase has retry logic for this case (just insufficient retries in test)
4. **Expected Semantics**: After restart, the scanner must be reset - this is fundamental to distributed systems

## Why This Is NOT a TEST-BUG

The original test (`TestRegionServerScan`) was designed to test scanner behavior when RPC call context is null, not to test scanner resilience across restarts. The restart framework injected a restart at an inappropriate position that exercises documented expected behavior.

## Conclusion
This is a **False Positive** caused by injecting a restart at an improper position (mid-scan operation). The `UnknownScannerException` is expected behavior when a regionserver restarts while a scan is in progress, and HBase's error message explicitly documents this scenario.

## Files Examined
- `hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionServerScan_RestartInjected.java`
- `hbase-server/src/main/java/org/apache/hadoop/hbase/regionserver/RSRpcServices.java:3191-3196`
- `hbase-client/src/main/java/org/apache/hadoop/hbase/client/ClientScanner.java:370-405`
