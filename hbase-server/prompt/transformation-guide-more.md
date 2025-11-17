# Additional Transformation Patterns for ProcessBased Tests

This document explores transformation approaches for tests that were initially skipped due to MiniHBaseCluster-specific dependencies.

## Analysis of Skipped Tests

---

### 1. TestClientClusterMetrics

**Original Skip Reason:**
- Uses custom RegionServer class (MiniHBaseCluster.MiniHBaseClusterRegionServer)
- Direct thread access (getMasterThreads, getLiveRegionServerThreads)
- JVM singleton (TaskMonitor)
- In-process coprocessor counters

**Detailed Analysis:**

#### Issue 1: Custom RegionServer Class
```java
public static class MyRegionServer extends MiniHBaseCluster.MiniHBaseClusterRegionServer {
    @Override
    public void tryRegionServerReport(long reportStartTime, long reportEndTime) {
        super.tryRegionServerReport(reportStartTime, reportEndTime);
    }
}
```
**Problem**: Custom RS extends MiniHBaseCluster-specific class
**Potential Solution**: ???

#### Issue 2: Direct Thread Access
```java
List<RegionServerThread> regionserverThreads = CLUSTER.getLiveRegionServerThreads();
List<MasterThread> masterThreads = CLUSTER.getMasterThreads();
```
**Problem**: Accesses JVM threads directly
**Potential Solution**: ???

#### Issue 3: JVM Singleton (TaskMonitor)
```java
TaskMonitor.get().createStatus(testTaskName);
// TaskMonitor is per-JVM singleton
```
**Problem**: TaskMonitor is shared in test JVM but not with separate node processes
**Potential Solution**: ???

#### Issue 4: In-Process Coprocessor Counters
```java
public static class MyObserver implements MasterCoprocessor {
    private static final AtomicInteger PRE_COUNT = new AtomicInteger(0);
    // Atomic counters only work in same JVM
}
```
**Problem**: Static counters only increment in same JVM
**Potential Solution**: ???

**TRANSFORMATION STRATEGY:**

#### Key Principle: Maximize Test Logic Preservation
Don't skip just because of custom class - skip only if the **specific API/method** used is truly inaccessible.

#### Solution 1: Custom RegionServer Class
**Approach**: Analyze what the custom class actually does
- `tryRegionServerReport()` forces RS to report to master
- In ProcessBased, RS naturally reports on schedule
- **Solution**: Wait for natural reporting cycle OR use Admin API to trigger metrics refresh
- If custom behavior is critical to test logic, may need to comment out that specific part

#### Solution 2: Thread Access → Process Access OR Client API
**Two-tier approach:**
1. **Try Process Access First**: Since threads are now separate processes, translate thread operations to process operations:
   - `getRegionServerThreads().size()` → count running RS processes OR `cluster.getNumLiveRegionServers()`
   - `thread.isAlive()` → check if process is running

2. **Fallback to Client API**: If process access doesn't fit:
   ```java
   // Instead of: cluster.getLiveRegionServerThreads().size()
   // Use: admin.getClusterMetrics().getLiveServerMetrics().size()

   // Instead of: cluster.getMasterThreads()
   // Use: admin.getClusterMetrics().getMasterName()
   //      admin.getClusterMetrics().getBackupMasterNames()
   ```

#### Solution 3: JVM Singleton (TaskMonitor)
**Approach**: Not upgrade-relevant feature
- TaskMonitor is JVM-internal monitoring tool
- Not accessible across process boundaries
- **Solution**: Comment out TaskMonitor-specific test methods with explanation
- Keep all other test logic intact

#### Solution 4: In-Process Coprocessor Counters
**Approach**: Verify presence, not internal state
```java
// Instead of checking atomic counters (impossible across processes):
// Assert.assertEquals(preCount + 1, MyObserver.PRE_COUNT.get());

// Verify coprocessor is registered and active:
List<String> coprocessors = admin.getClusterMetrics().getMasterCoprocessorNames();
assertTrue(coprocessors.contains("MyObserver"));
// This confirms coprocessor loaded and working, even if we can't track call counts
```

#### Recommended Transformation Approach
Create a "reduced" version that:
1. ✅ Keeps: testDefaults(), testAsyncClient(), testLiveAndDeadServersStatus(), testRegionStatesCount(), testOtherStatusInfos()
2. ⚠️ Modifies: testMasterAndBackupMastersStatus() - use Client API instead of thread access
3. ❌ Removes/Comments: testServerTasks() - TaskMonitor is JVM-internal
4. ⚠️ Modifies: testObserver() - verify presence only, not call counts

**Estimated Test Logic Preservation: ~80%**

---

### 2. TestClientClusterStatus

**Original Skip Reason:**
Similar to TestClientClusterMetrics - direct thread access, in-process coprocessor counters, direct server object tracking

**TRANSFORMATION STRATEGY:**

Follows same pattern as TestClientClusterMetrics, with one additional pattern:

#### Dead Server Tracking Pattern
```java
// BEFORE (direct object reference):
HRegionServer DEAD = rst.getRegionServer();
DEAD.stop("Test dead servers status");
Assert.assertEquals(DEAD.getServerName(), deadServerName);

// AFTER (ServerName-based tracking):
// Store ServerName before killing (via ClusterMetrics)
ServerName deadServerName = getLastLiveServerName();  // from cluster metrics
cluster.killRegionServer(deadServerName);
// Later verify:
assertTrue(admin.getClusterMetrics().getDeadServerNames().contains(deadServerName));
```

**Key Insight**: Don't track object references, track **ServerName** identifiers. ServerName is a client-accessible, serializable identifier that works across process boundaries.

**Estimated Test Logic Preservation: ~85%**

---

### 3. TestClusterPortAssignment

**Original Skip Reason:**
Tests MiniHBaseCluster-specific port assignment mechanism, direct server object access (getMaster().getRpcServer(), getRegionServer(0).getRpcServer())

**Question for Discussion:**
This test verifies that ports configured in Configuration are actually used by cluster nodes.

```java
assertEquals("Master RPC port is incorrect", masterPort,
  cluster.getMaster().getRpcServer().getListenerAddress().getPort());
assertEquals("RS RPC port is incorrect", rsPort,
  cluster.getRegionServer(0).getRpcServer().getListenerAddress().getPort());
```

In ProcessBased, nodes run in separate processes. How to verify port assignment?

**TRANSFORMATION STRATEGY:**

#### "Reduced" Version Approach
Don't fight ProcessBasedMiniHBaseCluster's port management - honor it and verify it works correctly.

```java
// BEFORE (sets custom ports then verifies):
int masterPort = HBaseTestingUtility.randomFreePort();
conf.setInt(HConstants.MASTER_PORT, masterPort);
// ... start cluster ...
assertEquals(masterPort, cluster.getMaster().getRpcServer().getListenerAddress().getPort());

// AFTER (verify ProcessBased's port assignment is consistent):
// Don't set custom ports - let ProcessBasedMiniHBaseCluster manage
ProcessBasedMiniHBaseCluster cluster = new Builder(conf).build();
cluster.waitClusterUp();

// Verify ports are assigned and accessible via ServerName
ServerName masterName = admin.getClusterMetrics().getMasterName();
assertNotNull("Master should have valid ServerName", masterName);
assertTrue("Master port should be valid", masterName.getPort() > 0);

// Verify RS ports
for (ServerName rsName : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
    assertTrue("RS port should be valid", rsName.getPort() > 0);
    assertNotEquals("RS port should differ from master", masterName.getPort(), rsName.getPort());
}

// Key verification: ports are consistent across calls (identity preservation)
ServerName masterName2 = admin.getClusterMetrics().getMasterName();
assertEquals("Master port should be stable", masterName.getPort(), masterName2.getPort());
```

**Key Insight**: Remove the "forcing custom ports" logic, keep the "verify ports are correctly assigned and stable" logic. This tests that ProcessBasedMiniHBaseCluster's port management works correctly.

**What's Removed**: Random port generation and forcing custom ports
**What's Preserved**: Port validity verification, port stability verification, port uniqueness verification

**Estimated Test Logic Preservation: ~60%** (reduced but still meaningful)

---

### 4. TestFullLogReconstruction

**Original Skip Reason:**
Uses TEST_UTIL.expireRegionServerSession() (MiniHBaseCluster-specific), direct RS thread access (rsThread.isAlive(), rsThread.getRegionServer())

**Question for Discussion:**

The test does:
1. Create multi-region table
2. Load data
3. Kill RS via `expireRegionServerSession()`
4. Wait for RS thread to die
5. Verify data still accessible (WAL replay worked)

The core test logic (data survival after RS death) is upgrade-relevant!

**Potential Transformation:**
```java
// BEFORE:
TEST_UTIL.expireRegionServerSession(index);
TEST_UTIL.waitFor(30000, () -> !rsThread.isAlive());

// AFTER:
ServerName rsToKill = getRegionServerServerName(index);
cluster.killRegionServer(rsToKill);
// Wait for cluster to detect death and reassign regions
Waiter.waitFor(conf, 30000, () ->
    admin.getClusterMetrics().getDeadServerNames().contains(rsToKill));
```

**TRANSFORMATION STRATEGY:**

Both methods achieve the same result: RS appears dead to the cluster.
- `expireRegionServerSession()` - ZK session expires, master thinks RS died
- `killRegionServer()` - Process dies, ZK session expires naturally, master detects

The core test intent (verify data survives RS death via WAL replay) is preserved!

```java
// AFTER:
// Create table and load data
Table table = createMultiRegionTable(tableName, family);
int initialCount = loadTable(table, family);

// Kill an RS
ServerName rsToKill = admin.getClusterMetrics().getLiveServerMetrics().keySet().iterator().next();
cluster.killRegionServer(rsToKill);

// Wait for cluster to detect death
Waiter.waitFor(conf, 30000, () ->
    admin.getClusterMetrics().getDeadServerNames().contains(rsToKill));

// Verify data still accessible (WAL replay worked)
int newCount = countRows(table);
assertEquals("Data should survive RS death", initialCount, newCount);
```

**Estimated Test Logic Preservation: ~90%**

---

### 5. TestGlobalMemStoreSize

**Original Skip Reason:**
Tests internal memory store accounting (getRegionServerAccounting().getGlobalMemStoreDataSize()), direct HRegion access (server.getRegion().getMemStoreDataSize())

**Question for Discussion:**

This test verifies:
1. GlobalMemStoreSize equals sum of all region memstore sizes
2. After flush, memstore size goes to zero

These are **internal server metrics** not exposed via client API.

**Potential Approaches:**
1. **Skip entirely** - Internal memory accounting is not upgrade-relevant
2. **Reduced version** - Test what IS accessible:
   - RegionMetrics.getMemStoreSize() via Admin.getRegionMetrics()
   - Verify memstore decreases after flush (approximate test)

```java
// Can't access internal accounting, but can verify via RegionMetrics
List<RegionMetrics> metrics = admin.getRegionMetrics(serverName);
for (RegionMetrics rm : metrics) {
    Size memstoreSize = rm.getMemStoreSize();
    // Verify it's reported (but can't verify internal consistency)
}

// After flush
admin.flush(tableName);
// Wait and verify memstore decreased
```

**TRANSFORMATION STRATEGY:**

**Key Principle: Every reduced version is meaningful!** If we don't reduce, some tests are not transformable at all. A 30% preserved test is infinitely better than 0%.

```java
// AFTER (Reduced but Meaningful):
// Create table and write data
TableName tableName = TableName.valueOf("test");
admin.createTable(tableDesc);
try (Table table = connection.getTable(tableName)) {
    // Write data to generate memstore usage
    for (int i = 0; i < 100; i++) {
        Put put = new Put(Bytes.toBytes("row" + i));
        put.addColumn(CF, Bytes.toBytes("q"), Bytes.toBytes("value" + i));
        table.put(put);
    }
}

// Verify memstore has data (via RegionMetrics)
List<RegionMetrics> metrics = admin.getRegionMetrics(serverName, tableName);
long totalMemstoreSize = 0;
for (RegionMetrics rm : metrics) {
    totalMemstoreSize += rm.getMemStoreSize().get(Size.Unit.BYTE);
}
assertTrue("Memstore should have data before flush", totalMemstoreSize > 0);

// Flush and verify memstore decreased
admin.flush(tableName);
Thread.sleep(2000); // Allow flush to complete

metrics = admin.getRegionMetrics(serverName, tableName);
long afterFlushSize = 0;
for (RegionMetrics rm : metrics) {
    afterFlushSize += rm.getMemStoreSize().get(Size.Unit.BYTE);
}
assertTrue("Memstore should decrease after flush", afterFlushSize < totalMemstoreSize);
```

**What's Removed**: Internal GlobalMemStoreSize vs sum verification (internal consistency check)
**What's Preserved**: Memstore usage tracking, flush behavior verification, client-visible metrics

**Estimated Test Logic Preservation: ~50%** - Reduced but still tests meaningful memstore behavior across versions

---

### 6. TestHBaseTestingUtility

**Original Skip Reason:**
Tests HBaseTestingUtility internals with direct HMaster/HRegionServer access

**TRANSFORMATION STRATEGY:**

**Key Principle: ALWAYS try reduced version before skip!**

Even though this tests HBaseTestingUtility framework, we can create a reduced version that tests **equivalent functionality** in ProcessBasedMiniHBaseCluster.

**Identify what HBaseTestingUtility tests:**
- Cluster startup/shutdown
- Table creation helpers
- Data loading utilities
- Region manipulation
- Configuration management

**Transform to test ProcessBasedMiniHBaseCluster equivalent functionality:**
```java
// Test cluster lifecycle
@Test
public void testClusterStartupShutdown() throws Exception {
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .build();
    cluster.waitClusterUp();
    assertTrue("Cluster should be up", cluster.isClusterUp());

    // Verify expected number of RSes
    assertEquals(3, admin.getClusterMetrics().getLiveServerMetrics().size());

    // Test shutdown
    cluster.shutdown();
    // Verify processes stopped
}

// Test table creation
@Test
public void testTableCreation() throws Exception {
    // Similar to HBaseTestingUtility.createTable()
    TableDescriptor td = TableDescriptorBuilder.newBuilder(tableName)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf"))
        .build();
    admin.createTable(td);
    assertTrue(admin.tableExists(tableName));
}
```

**What's Transformed**: Testing framework internals → Testing ProcessBasedMiniHBaseCluster functionality
**What's Preserved**: Core operations (startup, table creation, data loading) verification

**Estimated Test Logic Preservation: ~40%** - Different framework but equivalent functionality

---

### 7. TestHColumnDescriptorDefaultVersions

**Original Skip Reason:**
Uses direct getMaster().getMasterRpcServices() and getRegionServer() access

**Question for Discussion:**

Looking at the test name, it likely tests column family version defaults. The actual column descriptor behavior should be client-accessible via:
- TableDescriptor/ColumnFamilyDescriptor APIs
- Admin.getDescriptor() to verify defaults

What specific internal access does it use, and can we test the same default behavior via client API?

---

