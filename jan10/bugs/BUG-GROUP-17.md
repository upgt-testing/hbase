# BUG-GROUP-17: NullPointerException in BucketCache.parsePB

## Summary

Missing null check after `parseDelimitedFrom()` in `BucketCache.retrieveChunkedBackingMap()` causes NPE when reading an incomplete or corrupted persistence file.

## Bug Classification

**Type**: BUG (Source Code Issue)

## Stack Trace

```
Caused by: java.lang.NullPointerException
    at org.apache.hadoop.hbase.io.hfile.bucket.BucketCache.parsePB(BucketCache.java:1593)
    at org.apache.hadoop.hbase.io.hfile.bucket.BucketCache.retrieveChunkedBackingMap(BucketCache.java:1675)
    at org.apache.hadoop.hbase.io.hfile.bucket.BucketCache.retrieveFromFile(BucketCache.java:1495)
```

## Root Cause Analysis

### Buggy Code Location

**File**: `hbase-server/src/main/java/org/apache/hadoop/hbase/io/hfile/bucket/BucketCache.java`

**Method**: `retrieveChunkedBackingMap()` (lines 1648-1676)

### Buggy Code

```java
private void retrieveChunkedBackingMap(FileInputStream in, int[] bucketSizes) throws IOException {
    byte[] bytes = new byte[Long.BYTES];
    int readSize = in.read(bytes);
    if (readSize != Long.BYTES) {
      throw new IOException("Invalid size of chunk-size read from persistence: " + readSize);
    }
    long batchSize = Bytes.toLong(bytes, 0);

    readSize = in.read(bytes);
    if (readSize != Long.BYTES) {
      throw new IOException("Invalid size for number of chunks read from persistence: " + readSize);
    }
    long numChunks = Bytes.toLong(bytes, 0);

    LOG.info("Number of chunks: {}, chunk size: {}", numChunks, batchSize);

    ArrayList<BucketCacheProtos.BackingMap> bucketCacheMaps = new ArrayList<>();
    // Read the first chunk that has all the details.
    BucketCacheProtos.BucketCacheEntry firstChunk =
      BucketCacheProtos.BucketCacheEntry.parseDelimitedFrom(in);  // <-- CAN RETURN NULL

    // Subsequent chunks have the backingMap entries.
    for (int i = 1; i < numChunks; i++) {
      LOG.info("Reading chunk no: {}", i + 1);
      bucketCacheMaps.add(BucketCacheProtos.BackingMap.parseDelimitedFrom(in));  // <-- CAN ALSO RETURN NULL
      LOG.info("Retrieved chunk: {}", i + 1);
    }
    parsePB(firstChunk, bucketCacheMaps);  // <-- NPE when firstChunk is null
}
```

### Why This Happens

1. Protobuf's `parseDelimitedFrom()` method returns `null` when:
   - The input stream has reached EOF
   - The file is truncated or corrupted
   - The file header was written but the actual data was not

2. During a restart scenario:
   - The region server is gracefully stopped
   - The BucketCache tries to persist its state to disk
   - If the server is killed/restarted before persistence completes, the file may be incomplete
   - When the region server starts again, it reads the file header (chunk size, numChunks) but the actual protobuf data is missing/incomplete
   - `parseDelimitedFrom()` returns `null`
   - The code passes `null` to `parsePB()`, causing NPE at line 1593:
     ```java
     BucketProtoUtils.fromPB(firstChunk.getDeserializersMap(), ...)
     ```

### Additional Related Issue

There's also a similar issue at line 1491 in `retrieveFromFile()`:

```java
parsePB(BucketCacheProtos.BucketCacheEntry.parseDelimitedFrom(in));
```

This also doesn't check for null before passing to `parsePB(BucketCacheProtos.BucketCacheEntry proto)`.

## Affected Tests

- `TestPrefetchRSClose_RestartInjected.testPrefetchPersistence` (position: after_put, after_flush)
- `TestBlockEvictionOnRegionMovement_RestartInjected.testBlockEvictionOnRegionMove` (position: after_flush)
- `TestBlockEvictionOnRegionMovement_RestartInjected.testBlockEvictionOnGracefulStop` (position: after_put)

## Proposed Fix

```java
private void retrieveChunkedBackingMap(FileInputStream in, int[] bucketSizes) throws IOException {
    byte[] bytes = new byte[Long.BYTES];
    int readSize = in.read(bytes);
    if (readSize != Long.BYTES) {
      throw new IOException("Invalid size of chunk-size read from persistence: " + readSize);
    }
    long batchSize = Bytes.toLong(bytes, 0);

    readSize = in.read(bytes);
    if (readSize != Long.BYTES) {
      throw new IOException("Invalid size for number of chunks read from persistence: " + readSize);
    }
    long numChunks = Bytes.toLong(bytes, 0);

    LOG.info("Number of chunks: {}, chunk size: {}", numChunks, batchSize);

    ArrayList<BucketCacheProtos.BackingMap> bucketCacheMaps = new ArrayList<>();
    // Read the first chunk that has all the details.
    BucketCacheProtos.BucketCacheEntry firstChunk =
      BucketCacheProtos.BucketCacheEntry.parseDelimitedFrom(in);

    // FIX: Check for null - file may be corrupted or incomplete
    if (firstChunk == null) {
      throw new IOException("Failed to read BucketCacheEntry from persistence file. "
        + "File may be corrupted or incomplete.");
    }

    // Subsequent chunks have the backingMap entries.
    for (int i = 1; i < numChunks; i++) {
      LOG.info("Reading chunk no: {}", i + 1);
      BucketCacheProtos.BackingMap chunk = BucketCacheProtos.BackingMap.parseDelimitedFrom(in);
      // FIX: Also check subsequent chunks for null
      if (chunk == null) {
        throw new IOException("Failed to read BackingMap chunk " + (i + 1)
          + " from persistence file. File may be corrupted or incomplete.");
      }
      bucketCacheMaps.add(chunk);
      LOG.info("Retrieved chunk: {}", i + 1);
    }
    parsePB(firstChunk, bucketCacheMaps);
}
```

Also fix in `retrieveFromFile()` at line 1491:

```java
// Old format of backing map persistence.
BucketCacheProtos.BucketCacheEntry entry =
    BucketCacheProtos.BucketCacheEntry.parseDelimitedFrom(in);
if (entry == null) {
  throw new IOException("Failed to read BucketCacheEntry from persistence file. "
    + "File may be corrupted or incomplete.");
}
parsePB(entry);
```

## Alternative Fix - Graceful Degradation

Instead of throwing an exception, the code could treat a corrupted/incomplete persistence file as if no persistence file exists:

```java
if (firstChunk == null) {
  LOG.warn("Bucket cache persistence file appears to be corrupted or incomplete. "
    + "Starting with empty cache.");
  bucketAllocator = new BucketAllocator(cacheCapacity, bucketSizes, backingMap, realCacheSize);
  blockNumber.add(backingMap.size());
  backingMapValidated.set(true);
  return;
}
```

This approach is more resilient but may silently lose cached data. The choice between throwing an exception vs graceful degradation depends on operational preferences.

## Impact

- **Severity**: Medium-High
- **Impact**: Region server fails to start when bucket cache persistence file is corrupted
- **Frequency**: Occurs when restart happens during bucket cache persistence
- **User Impact**: Region server startup failure, potential data unavailability

## Reproduction Steps

1. Enable bucket cache with persistent IO engine
2. Put data into a table
3. Inject a restart at position "after_put" or "after_flush" targeting the region server
4. The restart causes the bucket cache persistence file to be written incompletely
5. When region server restarts, it fails with NPE

## Test Command

```bash
mvn surefire:test \
  -Dtest=org.apache.hadoop.hbase.io.hfile.TestPrefetchRSClose_RestartInjected#testPrefetchPersistence \
  -Drestart.position=after_put \
  -Drestart.target=regionserver \
  -Drestart.mode=GRACEFUL \
  -pl hbase-server
```
