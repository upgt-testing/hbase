# Step 3: HBase Test Transformation Guide

## Table of Contents
1. [Introduction & Philosophy](#introduction--philosophy)
2. [Prerequisites & Setup](#prerequisites--setup)
3. [Test Organization and Naming Convention](#test-organization-and-naming-convention)
4. [Core Transformation Rules](#core-transformation-rules)
5. [API Mapping Tables](#api-mapping-tables)
6. [Step-by-Step Transformation Process](#step-by-step-transformation-process)
7. [Common Transformation Patterns](#common-transformation-patterns)
8. [Inserting Cluster Upgrade Method Calls](#inserting-cluster-upgrade-method-calls)
9. [Upgrade Checkpoint Test Methods](#upgrade-checkpoint-test-methods)
10. [When to Comment Out Logic](#when-to-comment-out-logic)
11. [Testing Checklist](#testing-checklist)
12. [Best Practices](#best-practices)
13. [Quick Reference Decision Tree](#quick-reference-decision-tree)

---

## Introduction & Philosophy

### Purpose
Transform existing `MiniHBaseCluster` tests to `ProcessBasedMiniHBaseCluster` to enable:
- **Process-based testing** - Each node runs in separate JVM for realistic testing
- **Multi-version testing** - Test upgrades between different HBase versions (e.g., 2.6.0 → 3.0.0)
- **Rolling upgrade scenarios** - Simulate production upgrade procedures
- **Version compatibility** - Verify protocol compatibility across versions

### Key Principle
**Most server-side operations have client-side RPC equivalents.** The goal is to maximize test logic preservation by finding client-side APIs that provide equivalent functionality.

### Transformation Hierarchy
When encountering server-side operations, try these approaches in order:

1. **Connection/Table API** - High-level client operations (Put, Get, Scan)
2. **Admin API** - Administrative operations (createTable, flush, compact)
3. **Low-level RPC** - Direct RPC protocols (AdminService, ClientService)
4. **ClusterMetrics** - For metrics and runtime statistics
5. **Comment Out** - Only if truly no client-side equivalent exists

### Critical Transformation Mindset

**EVERY REDUCED VERSION IS MEANINGFUL!**

If you cannot transform 100% of a test, transform what you CAN. A test with 30% preserved logic is infinitely better than 0%. Never skip a test just because:
- It uses a custom class (analyze what the class actually does)
- It has some internal access (transform the accessible parts)
- It seems "too complex" (reduce to essential behavior)

**Transform as much as possible, comment out as little as necessary.**

#### Key Principles:
1. **Never skip if ANY logic can be preserved** - Reduced versions are meaningful
2. **Try process access first, then client API** - Threads → processes, use `cluster.killRegionServer()` instead of `thread.stop()`
3. **Track ServerName, not object references** - ServerName is serializable and works across process boundaries
4. **Verify presence, not internal state** - For coprocessors, verify they're loaded, not call counts
5. **Comment out only JVM-internal features** - TaskMonitor, static counters in coprocessors, internal accounting
6. **Honor ProcessBased's behavior** - Don't fight port management, verify it works correctly
7. **Client API covers most operations** - ClusterMetrics provides cluster state, Admin provides operations

### Why ProcessBasedMiniHBaseCluster?

**MiniHBaseCluster limitations:**
- All nodes run in same JVM - cannot test different versions
- Direct object access to HMaster/HRegionServer - not realistic for production scenarios
- In-process - cannot simulate true process failures and restarts

**ProcessBasedMiniHBaseCluster benefits:**
- True process isolation - realistic testing
- Multi-version support - essential for upgrade testing (HBase 2.x → 3.x)
- Client-only access - forces use of public APIs (more realistic)
- Better represents production environments
- Node identity persistence - nodes maintain address/port across restarts

---

## Prerequisites & Setup

### System Properties (Automatic!)
ProcessBasedMiniHBaseCluster automatically reads distributions from system properties:

```bash
# No manual setup needed! Just pass system properties to Maven:
mvn test -Dtest=TestMyFeature_ProcessBased \
  -Dhbase.start.home=/opt/hbase-2.6.0 \
  -Dhbase.upgrade.home=/opt/hbase-3.0.0 \
  -pl hbase-server
```

**Backward compatibility:** Environment variables (`HBASE_HOME`, `HBASE_UPGRADE_HOME`) still work as fallback.

### Test Configuration
```java
import org.apache.hadoop.hbase.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.HBaseConfiguration;

// No @Before setup needed! System properties are read automatically!
// Add timeout 120s to allow for process startup time
@Test(timeout=120000)
public void testSomething() throws Exception {
    // Just build - automatic!
    ProcessBasedMiniHBaseCluster cluster =
        new ProcessBasedMiniHBaseCluster.Builder(HBaseConfiguration.create())
            .numRegionServers(3)
            .build();  // Automatically reads system properties!
}
```

### Running Transformed Tests
```bash
# Run with system properties (recommended)
mvn test -Dtest=TestMyFeature_ProcessBased \
  -Dhbase.start.home=/opt/hbase-2.6.0 \
  -Dhbase.upgrade.home=/opt/hbase-3.0.0 \
  -pl hbase-server

# Run baseline (no upgrade)
mvn test -Dtest='TestMyFeature_ProcessBased#testMethod[upgrade-at=NO_UPGRADE]' \
  -Dhbase.start.home=/opt/hbase-2.6.0 \
  -pl hbase-server
```

---

## Test Organization and Naming Convention

### File Location Strategy
**Place transformed tests in the SAME directory as the original tests**, using a naming suffix to distinguish them.

This approach provides:
- ✅ Side-by-side comparison of original and transformed tests
- ✅ Tests alphabetically adjacent in file listings
- ✅ Clear visual distinction via suffix
- ✅ Original package structure preserved
- ✅ Both versions can coexist long-term

### Naming Convention: `_ProcessBased` Suffix

```
Original Test:     TestCompaction.java
Transformed Test:  TestCompaction_ProcessBased.java

Location:          Same directory, same package
```

### Directory Structure Examples

```
hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/
├── TestCompaction.java                      [ORIGINAL - MiniHBaseCluster]
├── TestCompaction_ProcessBased.java         [TRANSFORMED - ProcessBased]
├── TestFlushFromClient.java                 [ORIGINAL - MiniHBaseCluster]
├── TestFlushFromClient_ProcessBased.java    [TRANSFORMED - ProcessBased]
└── compactions/
    ├── TestCompactor.java                   [ORIGINAL - MiniHBaseCluster]
    └── TestCompactor_ProcessBased.java      [TRANSFORMED - ProcessBased]
```

### Package and Class Declaration

The transformed test uses the **same package** as the original:

```java
// Original: TestCompaction.java
package org.apache.hadoop.hbase.regionserver;

public class TestCompaction {
  // ... MiniHBaseCluster tests
}
```

```java
// Transformed: TestCompaction_ProcessBased.java
package org.apache.hadoop.hbase.regionserver;  // Same package!

/**
 * ProcessBased version of {@link TestCompaction}.
 *
 * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * @see TestCompaction Original test using MiniHBaseCluster
 */
public class TestCompaction_ProcessBased {
  // ... ProcessBasedMiniHBaseCluster tests
}
```

### Test Execution Patterns

```bash
# Run original test only
mvn test -Dtest=TestCompaction

# Run transformed test only
mvn test -Dtest=TestCompaction_ProcessBased

# Run ALL ProcessBased tests across the codebase
mvn test -Dtest="*_ProcessBased"

# Run both versions for comparison
mvn test -Dtest=TestCompaction,TestCompaction_ProcessBased
```

---

## Core Transformation Rules

### Rule 1: Maximize Test Logic Preservation
**Preserve as much of the original test logic as possible** by finding client-side equivalents for server-side operations.

✅ **DO**: Find client API that provides same functionality
❌ **DON'T**: Remove test logic unless absolutely necessary

### Rule 2: Use the API Hierarchy
Always try to find client-side equivalents in this order:
1. Connection/Table API (highest level - Put, Get, Scan, Delete)
2. Admin API (mid-level - createTable, flush, compact, split)
3. ClusterMetrics (for cluster-wide statistics)
4. Low-level RPC (RPC protocols - AdminProtos, ClientProtos)
5. Comment out only when no alternative exists

### Rule 3: Comment Out Only When Necessary
Only comment out operations when:
- No client-side API exists
- Operation accesses internal storage (HStore, HFile internals)
- Operation manipulates JVM-internal state

### Rule 4: Document Minimally
Add comments only for:
- Non-obvious transformations
- Commented-out logic (explain why and what was removed)
- Workarounds or limitations

---

## API Mapping Tables

### Table 1: MiniHBaseCluster → ProcessBasedMiniHBaseCluster

| MiniHBaseCluster Method | ProcessBasedMiniHBaseCluster | Status | Notes |
|-------------------------|------------------------------|--------|-------|
| **Cluster Creation** |
| `new Builder(conf).build()` | `new Builder(conf).build()` | ✓ | Automatically reads system properties |
| **Client Access** |
| `getConnection()` | `getConnection()` | ✓ | Same API |
| `getConfiguration()` | `getConfiguration()` | ✓ | Returns base config |
| **Node Management** |
| `startMaster()` | `startMaster()` | ✓ | Same API |
| `startRegionServer()` | `startRegionServer()` | ✓ | Same API |
| `stopMaster(ServerName)` | `stopMaster(ServerName)` | ✓ | Same API |
| `stopRegionServer(ServerName)` | `stopRegionServer(ServerName)` | ✓ | Same API |
| `killMaster(ServerName)` | `killMaster(ServerName)` | ✓ | Same API |
| `killRegionServer(ServerName)` | `killRegionServer(ServerName)` | ✓ | Same API |
| `waitForActiveAndReadyMaster(timeout)` | `waitForActiveAndReadyMaster(timeout)` | ✓ | Same API |
| **Direct Object Access** |
| `getMaster()` | ❌ | ✗ | Use Admin API instead |
| `getMaster(int i)` | ❌ | ✗ | Use Admin API instead |
| `getRegionServer(int i)` | ❌ | ✗ | Use Admin/Connection APIs |
| `getRegionServer(ServerName)` | ❌ | ✗ | Use Admin/Connection APIs |
| `getRegionServerThreads()` | ❌ | ✗ | Use ClusterMetrics |
| `getMasterThreads()` | ❌ | ✗ | Use ClusterMetrics |
| `getRegions(TableName)` | ❌ | ✗ | Use Admin.getRegions() |
| **Cluster Control** |
| `shutdown()` | `shutdown()` | ✓ | Same API |
| **Cluster State** |
| `getNumLiveRegionServers()` | `getNumLiveRegionServers()` | ✓ | Same API |
| `isClusterUp()` | `isClusterUp()` | ✓ | Same API |
| `getClusterMetrics()` | `getClusterMetrics()` | ✓ | Via Admin API |

### Table 2: HMaster (Server-Side) → Client-Side APIs

**Context**: HMaster is a private, server-side class managing cluster coordination, region assignment, and DDL operations. All its operations should have client-side equivalents through RPC.

| HMaster Method | Client-Side API | API Layer | Code Example |
|----------------|-----------------|-----------|--------------|
| **Table Operations** |
| `createTable(TableDescriptor)` | `Admin.createTable()` | Admin | `admin.createTable(tableDescriptor);` |
| `deleteTable(TableName)` | `Admin.deleteTable()` | Admin | `admin.deleteTable(tableName);` |
| `modifyTable(TableDescriptor)` | `Admin.modifyTable()` | Admin | `admin.modifyTable(tableDescriptor);` |
| `enableTable(TableName)` | `Admin.enableTable()` | Admin | `admin.enableTable(tableName);` |
| `disableTable(TableName)` | `Admin.disableTable()` | Admin | `admin.disableTable(tableName);` |
| `truncateTable(TableName)` | `Admin.truncateTable()` | Admin | `admin.truncateTable(tableName, preserveSplits);` |
| **Region Operations** |
| `assign(RegionInfo)` | `Admin.assign()` | Admin | `admin.assign(regionInfo.getEncodedNameAsBytes());` |
| `unassign(RegionInfo)` | `Admin.unassign()` | Admin | `admin.unassign(regionInfo.getEncodedNameAsBytes());` |
| `move(RegionInfo, ServerName)` | `Admin.move()` | Admin | `admin.move(regionInfo.getEncodedNameAsBytes(), serverName);` |
| `split(TableName)` | `Admin.split()` | Admin | `admin.split(tableName);` |
| `splitRegion(RegionInfo)` | `Admin.splitRegionAsync()` | Admin | `admin.splitRegionAsync(regionInfo.getRegionName());` |
| `mergeRegions(...)` | `Admin.mergeRegionsAsync()` | Admin | `admin.mergeRegionsAsync(regionA, regionB);` |
| **Cluster Management** |
| `balanceSwitch(boolean)` | `Admin.balancerSwitch()` | Admin | `admin.balancerSwitch(on, drainRSs);` |
| `balance()` | `Admin.balance()` | Admin | `admin.balance();` |
| `getClusterMetrics()` | `Admin.getClusterMetrics()` | Admin | `ClusterMetrics metrics = admin.getClusterMetrics();` |
| `getServerName()` | `ClusterMetrics.getMasterName()` | ClusterMetrics | `metrics.getMasterName();` |
| **Snapshot Operations** |
| `snapshot(SnapshotDescription)` | `Admin.snapshot()` | Admin | `admin.snapshot(snapshotName, tableName);` |
| `deleteSnapshot(String)` | `Admin.deleteSnapshot()` | Admin | `admin.deleteSnapshot(snapshotName);` |
| `restoreSnapshot(String)` | `Admin.restoreSnapshot()` | Admin | `admin.restoreSnapshot(snapshotName);` |
| **Namespace Operations** |
| `createNamespace(NamespaceDescriptor)` | `Admin.createNamespace()` | Admin | `admin.createNamespace(namespaceDescriptor);` |
| `deleteNamespace(String)` | `Admin.deleteNamespace()` | Admin | `admin.deleteNamespace(namespaceName);` |
| **Internal Operations** (❌ No client API) |
| `getAssignmentManager()` | ❌ | - | Internal state - comment out |
| `getMasterFileSystem()` | ❌ | - | Internal storage - comment out |
| `getServerManager()` | ❌ | - | Internal state - comment out |

### Table 3: HRegionServer (Server-Side) → Client-Side APIs

**Context**: HRegionServer is a private, server-side class managing regions and data I/O. Most operations have client-side equivalents.

| HRegionServer Method | Client-Side API | API Layer | Code Example |
|----------------------|-----------------|-----------|--------------|
| **Data Operations** |
| `get(Get)` | `Table.get()` | Table | `Result r = table.get(get);` |
| `put(Put)` | `Table.put()` | Table | `table.put(put);` |
| `delete(Delete)` | `Table.delete()` | Table | `table.delete(delete);` |
| `scan(Scan)` | `Table.getScanner()` | Table | `ResultScanner scanner = table.getScanner(scan);` |
| **Region Operations** |
| `getRegions(TableName)` | `Admin.getRegions(TableName)` | Admin | `List<RegionInfo> regions = admin.getRegions(tableName);` |
| `getRegion(byte[])` | `Admin.getRegion(byte[])` | Admin | `RegionInfo info = admin.getRegion(regionName);` |
| `closeRegion(RegionInfo)` | `Admin.unassign()` | Admin | `admin.unassign(regionInfo.getEncodedNameAsBytes());` |
| **Flush/Compact** |
| `flushRegion(RegionInfo)` | `Admin.flushRegion()` | Admin | `admin.flushRegion(regionInfo.getRegionName());` |
| `compactRegion(RegionInfo)` | `Admin.compactRegion()` | Admin | `admin.compactRegion(regionInfo.getRegionName());` |
| **Statistics/Monitoring** |
| `getServerName()` | `ClusterMetrics.getLiveServerMetrics()` | ClusterMetrics | `metrics.getLiveServerMetrics().keySet();` |
| `getMetrics()` | `ClusterMetrics.getLiveServerMetrics()` | ClusterMetrics | `ServerMetrics sm = metrics.getLiveServerMetrics().get(serverName);` |
| `getRegionLoad(RegionInfo)` | `Admin.getRegionMetrics()` | Admin | `admin.getRegionMetrics(serverName);` |
| **Internal Operations** (❌ No client API) |
| `getOnlineRegion(byte[])` | ❌ | - | Internal region instance - comment out |
| `getOnlineRegions()` | ❌ | - | Internal region list - comment out |
| `getRegionServerCoprocessorHost()` | ❌ | - | Internal coprocessor - comment out |
| `getWAL(RegionInfo)` | ❌ | - | Internal WAL - comment out |

### Table 4: Admin Operations

| Operation | Admin API | Code Example | Alternative |
|-----------|-----------|--------------|-------------|
| **Table Management** |
| Create table | `admin.createTable()` | `admin.createTable(tableDescriptor);` | N/A |
| Delete table | `admin.deleteTable()` | `admin.deleteTable(tableName);` | N/A |
| Modify table | `admin.modifyTable()` | `admin.modifyTable(tableDescriptor);` | N/A |
| Enable table | `admin.enableTable()` | `admin.enableTable(tableName);` | N/A |
| Disable table | `admin.disableTable()` | `admin.disableTable(tableName);` | N/A |
| List tables | `admin.listTableDescriptors()` | `List<TableDescriptor> tables = admin.listTableDescriptors();` | N/A |
| **Region Management** |
| Flush table | `admin.flush()` | `admin.flush(tableName);` | N/A |
| Flush region | `admin.flushRegion()` | `admin.flushRegion(regionName);` | N/A |
| Compact table | `admin.compact()` | `admin.compact(tableName);` | N/A |
| Major compact | `admin.majorCompact()` | `admin.majorCompact(tableName);` | N/A |
| Split table | `admin.split()` | `admin.split(tableName);` | N/A |
| Split region | `admin.splitRegionAsync()` | `admin.splitRegionAsync(regionName);` | N/A |
| Merge regions | `admin.mergeRegionsAsync()` | `admin.mergeRegionsAsync(regionA, regionB, forcible);` | N/A |
| **Cluster Management** |
| Balance cluster | `admin.balance()` | `boolean ran = admin.balance();` | N/A |
| Balancer switch | `admin.balancerSwitch()` | `boolean prevState = admin.balancerSwitch(on, drainRSs);` | N/A |
| Normalize | `admin.normalize()` | `boolean ran = admin.normalize();` | N/A |
| **Snapshot** |
| Create snapshot | `admin.snapshot()` | `admin.snapshot(snapshotName, tableName);` | N/A |
| Delete snapshot | `admin.deleteSnapshot()` | `admin.deleteSnapshot(snapshotName);` | N/A |
| Restore snapshot | `admin.restoreSnapshot()` | `admin.restoreSnapshot(snapshotName);` | N/A |
| List snapshots | `admin.listSnapshots()` | `List<SnapshotDescription> snapshots = admin.listSnapshots();` | N/A |

### Table 5: Monitoring/Metrics → ClusterMetrics

| Server-Side Check | Client-Side Alternative | Access Method | Example |
|------------------|-------------------------|---------------|---------|
| **Cluster Metrics** |
| Master info | `ClusterMetrics.getMasterName()` | Admin.getClusterMetrics() | `ServerName master = metrics.getMasterName();` |
| Live servers | `ClusterMetrics.getLiveServerMetrics()` | Admin.getClusterMetrics() | `Map<ServerName, ServerMetrics> servers = metrics.getLiveServerMetrics();` |
| Dead servers | `ClusterMetrics.getDeadServerNames()` | Admin.getClusterMetrics() | `List<ServerName> deadServers = metrics.getDeadServerNames();` |
| Balancer running | `ClusterMetrics.getBalancerOn()` | Admin.getClusterMetrics() | `boolean on = metrics.getBalancerOn();` |
| **Server Metrics** |
| Server load | `ServerMetrics` from ClusterMetrics | Admin.getClusterMetrics() | `ServerMetrics sm = metrics.getLiveServerMetrics().get(serverName);` |
| Region count | `ServerMetrics.getRegionMetrics()` | Admin.getClusterMetrics() | `Map<byte[], RegionMetrics> regions = sm.getRegionMetrics();` |
| Request count | `ServerMetrics.getRequestCount()` | Admin.getClusterMetrics() | `long requests = sm.getRequestCount();` |
| **Region Metrics** |
| Region load | `Admin.getRegionMetrics()` | Admin API | `List<RegionMetrics> regionMetrics = admin.getRegionMetrics(serverName);` |
| Store file count | `RegionMetrics.getStoreFileCount()` | Admin.getRegionMetrics() | `int storeFiles = regionMetrics.getStoreFileCount();` |
| Memstore size | `RegionMetrics.getMemStoreSize()` | Admin.getRegionMetrics() | `Size memstore = regionMetrics.getMemStoreSize();` |

### Table 6: Common Test Utilities

| MiniHBaseCluster Utility | ProcessBasedMiniHBaseCluster Alternative | Notes |
|--------------------------|------------------------------------------|-------|
| `TEST_UTIL.getConnection()` | `cluster.getConnection()` | Same pattern |
| `TEST_UTIL.getAdmin()` | `connection.getAdmin()` | Via connection |
| `TEST_UTIL.createTable()` | `admin.createTable()` | Use Admin API |
| `TEST_UTIL.flush(tableName)` | `admin.flush(tableName)` | Use Admin API |
| `TEST_UTIL.compact(tableName, true)` | `admin.majorCompact(tableName)` | Use Admin API |
| `cluster.getRegionServer(0).flush(regionName)` | `admin.flushRegion(regionName)` | Use Admin API |
| `cluster.getMaster().balance()` | `admin.balance()` | Use Admin API |

---

## Step-by-Step Transformation Process

### Step 0: Create Transformed Test File

**Goal**: Set up the new test file with proper naming and location.

1. **Locate the original test:**
   ```bash
   # Example: Original test
   hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestCompaction.java
   ```

2. **Create new file with `_ProcessBased` suffix in the SAME directory:**
   ```bash
   # New transformed test (same directory!)
   hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestCompaction_ProcessBased.java
   ```

3. **Copy original test content to new file:**
   ```bash
   cp TestCompaction.java TestCompaction_ProcessBased.java
   ```

4. **Update class name and add Javadoc:**
   ```java
   package org.apache.hadoop.hbase.regionserver;  // Same package as original!

   /**
    * ProcessBased version of {@link TestCompaction}.
    *
    * Transformed from MiniHBaseCluster to ProcessBasedMiniHBaseCluster to enable
    * process-based testing and multi-version upgrade scenarios.
    *
    * @see TestCompaction Original test using MiniHBaseCluster
    */
   public class TestCompaction_ProcessBased {  // Note: _ProcessBased suffix
     // ... test methods
   }
   ```

5. **Checklist before proceeding:**
   - [ ] New file created in same directory as original
   - [ ] Class name has `_ProcessBased` suffix
   - [ ] Package declaration is identical to original
   - [ ] Javadoc references original test with `@see` tag
   - [ ] File compiles (even if tests fail)

### Step 1: Analyze Test Dependencies

**Goal**: Understand what server-side operations the test uses.

1. **Scan for direct object access patterns:**
   ```bash
   # Search for common patterns
   grep -E "cluster\.(getMaster|getRegionServer)" TestCompaction.java
   grep -E "\\.getOnlineRegion\(\)" TestCompaction.java
   grep -E "\\.getWAL\(\)" TestCompaction.java
   ```

2. **Categorize operations:**
   - ✅ **Already client-side**: Table.put/get, Admin.createTable
   - ⚠️ **Has client equivalent**: HMaster.balance() → Admin.balance()
   - ❌ **No client equivalent**: getOnlineRegion(), getWAL(), internal storage access

3. **Plan transformation:**
   - List all operations that need transformation
   - Find client equivalents in mapping tables
   - Identify operations that must be commented out

### Step 2: Transform Import Statements

```java
// BEFORE
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.master.HMaster;
import org.apache.hadoop.hbase.regionserver.HRegionServer;
import org.apache.hadoop.hbase.regionserver.HRegion;
import org.apache.hadoop.hbase.regionserver.HStore;

// AFTER
import org.apache.hadoop.hbase.ProcessBasedMiniHBaseCluster;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.ClusterMetrics;
// Remove: MiniHBaseCluster, HMaster, HRegionServer imports
// Keep: HRegion, HStore only if used in type declarations that can't be removed
```

### Step 3: Transform Cluster Setup

#### Basic Cluster Creation

```java
// BEFORE (MiniHBaseCluster)
Configuration conf = HBaseConfiguration.create();
MiniHBaseCluster cluster = TEST_UTIL.startMiniCluster(3);
// or
MiniHBaseCluster cluster = new MiniHBaseCluster(conf, 3);

// AFTER (ProcessBasedMiniHBaseCluster) - AUTOMATIC!
Configuration conf = HBaseConfiguration.create();
// No environment variable checks needed! Automatic!

ProcessBasedMiniHBaseCluster cluster =
    new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .format(true)
        .build();  // Automatically reads system properties!
cluster.waitClusterUp();
```

**Note:** System properties are passed via Maven:
```bash
mvn test -Dtest=TestCompaction_ProcessBased \
  -Dhbase.start.home=/opt/hbase-2.6.0 \
  -Dhbase.upgrade.home=/opt/hbase-3.0.0
```

### Step 4: Transform Operations Using Mapping Tables

#### Example 1: Table Operations (No Change)
```java
// These work identically - no transformation needed
Connection connection = cluster.getConnection();
try (Table table = connection.getTable(tableName)) {
    Put put = new Put(Bytes.toBytes("row1"));
    put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("q"), Bytes.toBytes("value"));
    table.put(put);

    Get get = new Get(Bytes.toBytes("row1"));
    Result result = table.get(get);
}
```

#### Example 2: HMaster Access → Admin API
```java
// BEFORE: Direct master access
HMaster master = cluster.getMaster();
boolean balanced = master.balance();

// AFTER: Client-side Admin API
try (Connection conn = cluster.getConnection();
     Admin admin = conn.getAdmin()) {
    boolean balanced = admin.balance();
}
```

#### Example 3: HRegionServer Access → Admin API
```java
// BEFORE: Direct region server access
HRegionServer rs = cluster.getRegionServer(0);
rs.flushRegion(regionInfo.getEncodedNameAsBytes());

// AFTER: Client-side Admin API
try (Connection conn = cluster.getConnection();
     Admin admin = conn.getAdmin()) {
    admin.flushRegion(regionInfo.getRegionName());
}
```

#### Example 4: Internal Storage Check → Comment Out
```java
// BEFORE: Internal storage verification
HRegionServer rs = cluster.getRegionServer(0);
HRegion region = rs.getOnlineRegion(regionName);
HStore store = region.getStore(Bytes.toBytes("cf"));
int storeFileCount = store.getStorefilesCount();

// AFTER: Comment out with documentation
// TRANSFORMATION NOTE: Internal storage verification removed.
// getOnlineRegion() and getStore() provide access to internal region/store objects,
// which are not available via any client API.
// Original test verified store file count after compaction.
// Alternative: Use Admin.getRegionMetrics() to get approximate store file count:
//
// try (Admin admin = conn.getAdmin()) {
//     List<RegionMetrics> regionMetrics = admin.getRegionMetrics(serverName);
//     for (RegionMetrics rm : regionMetrics) {
//         if (Bytes.equals(rm.getRegionName(), regionName)) {
//             int storeFileCount = rm.getStoreFileCount();
//             // Note: This is approximate and may not match internal count
//         }
//     }
// }
//
// Original code:
// HRegionServer rs = cluster.getRegionServer(0);
// HRegion region = rs.getOnlineRegion(regionName);
// HStore store = region.getStore(Bytes.toBytes("cf"));
// int storeFileCount = store.getStorefilesCount();
```

### Step 5: Transform Cleanup Code

```java
// BEFORE
@After
public void tearDown() throws Exception {
    if (cluster != null) {
        cluster.shutdown();
    }
}

// AFTER (same, but can use try-with-resources if supported)
@After
public void tearDown() throws Exception {
    if (connection != null) {
        connection.close();
    }
    if (cluster != null) {
        cluster.shutdown();
    }
}

// OR use try-with-resources in the test method:
@Test
public void testSomething() throws Exception {
    try (ProcessBasedMiniHBaseCluster cluster =
            new ProcessBasedMiniHBaseCluster.Builder(conf)
                .numRegionServers(3)
                .build();  // Automatic - reads system properties!
         Connection conn = cluster.getConnection()) {
        // Test logic here
    }
}
```

### Step 6: Validate Transformation

Run through this checklist:

- [ ] All imports updated
- [ ] All direct object access either transformed or commented out
- [ ] Cluster creation includes distribution path (via system properties)
- [ ] Test logic preserved as much as possible
- [ ] Only truly internal operations commented out
- [ ] Comments added for non-obvious transformations
- [ ] Test compiles without errors
- [ ] System properties documented in test javadoc

---

## Common Transformation Patterns

### Pattern 1: Table Operations
**Status**: ✅ No transformation needed

```java
// Works identically in both frameworks
try (Connection conn = cluster.getConnection();
     Table table = conn.getTable(tableName)) {
    // Put/Get/Scan/Delete operations
}
```

### Pattern 2: Administrative Operations
```java
// Use Admin API
try (Connection conn = cluster.getConnection();
     Admin admin = conn.getAdmin()) {
    // createTable, flush, compact, split, etc.
}
```

### Pattern 3: Waiting for State Changes
```java
// BEFORE: Trigger immediate state propagation
cluster.getMaster().balance();

// AFTER: Use Admin API and wait for completion
try (Admin admin = conn.getAdmin()) {
    admin.balance();
    // Wait for balance to complete if needed
    Thread.sleep(5000);
    // Or use Waiter utility
    Waiter.waitFor(conf, 30000, () -> {
        ClusterMetrics metrics = admin.getClusterMetrics();
        return !metrics.getBalancerOn() || metrics.getRegionsInTransition().isEmpty();
    });
}
```

### Pattern 4: Node Information via ClusterMetrics
```java
// BEFORE: Direct node access
HRegionServer rs = cluster.getRegionServer(0);
ServerName serverName = rs.getServerName();

// AFTER: Via ClusterMetrics
try (Admin admin = conn.getAdmin()) {
    ClusterMetrics metrics = admin.getClusterMetrics();
    // Get all live servers
    for (ServerName serverName : metrics.getLiveServerMetrics().keySet()) {
        ServerMetrics sm = metrics.getLiveServerMetrics().get(serverName);
        // Use server metrics
    }
}
```

### Pattern 5: Dead Server Tracking (ServerName-based)
```java
// BEFORE: Direct object reference tracking
HRegionServer DEAD = cluster.getRegionServer(0);
DEAD.stop("Test dead servers status");
Assert.assertEquals(DEAD.getServerName(), deadServerName);

// AFTER: ServerName-based tracking (works across processes)
// Store ServerName before killing (via ClusterMetrics)
ServerName deadServerName = admin.getClusterMetrics()
    .getLiveServerMetrics().keySet().iterator().next();
cluster.killRegionServer(deadServerName);

// Later verify via ClusterMetrics:
Waiter.waitFor(conf, 30000, () ->
    admin.getClusterMetrics().getDeadServerNames().contains(deadServerName));
assertTrue(admin.getClusterMetrics().getDeadServerNames().contains(deadServerName));
```

**Key Insight**: Track **ServerName identifiers**, not object references. ServerName is serializable and works across process boundaries.

### Pattern 6: Thread Access → Process/Client API
```java
// BEFORE: Direct thread access
List<RegionServerThread> threads = cluster.getLiveRegionServerThreads();
int numServers = threads.size();
boolean alive = threads.get(0).isAlive();

// AFTER: Two-tier approach:
// 1. Try Process Access First:
int numServers = cluster.getNumLiveRegionServers();

// 2. Fallback to Client API:
int numServers = admin.getClusterMetrics().getLiveServerMetrics().size();

// For checking if server is alive:
ServerName rsName = ...;
boolean alive = admin.getClusterMetrics().getLiveServerMetrics().containsKey(rsName);
```

### Pattern 7: Coprocessor Verification (Presence, not State)
```java
// BEFORE: In-process atomic counters
public static class MyObserver implements MasterCoprocessor {
    private static final AtomicInteger PRE_COUNT = new AtomicInteger(0);
}
Assert.assertEquals(preCount + 1, MyObserver.PRE_COUNT.get());

// AFTER: Verify presence only (can't track call counts across processes)
List<String> coprocessors = admin.getClusterMetrics().getMasterCoprocessorNames();
assertTrue("MyObserver should be loaded", coprocessors.contains("MyObserver"));
// Cannot verify call counts - static counters only work in same JVM
// But presence verification confirms coprocessor is loaded and active
```

**Key Insight**: Verify coprocessor is **registered and active**, not internal call counts.

### Pattern 8: Memstore Testing via RegionMetrics
```java
// BEFORE: Internal accounting access
long globalSize = server.getRegionServerAccounting().getGlobalMemStoreDataSize();
long regionSize = region.getMemStoreDataSize();

// AFTER: Use RegionMetrics API (reduced but meaningful)
// Write data to generate memstore usage
try (Table table = connection.getTable(tableName)) {
    for (int i = 0; i < 100; i++) {
        Put put = new Put(Bytes.toBytes("row" + i));
        put.addColumn(CF, Bytes.toBytes("q"), Bytes.toBytes("value"));
        table.put(put);
    }
}

// Verify via RegionMetrics
List<RegionMetrics> metrics = admin.getRegionMetrics(serverName, tableName);
long totalMemstoreSize = 0;
for (RegionMetrics rm : metrics) {
    totalMemstoreSize += rm.getMemStoreSize().get(Size.Unit.BYTE);
}
assertTrue("Memstore should have data", totalMemstoreSize > 0);

// After flush, verify decrease
admin.flush(tableName);
Thread.sleep(2000);
// Re-fetch metrics and verify decreased
```

**Note**: Can't verify internal GlobalMemStoreSize vs sum consistency, but CAN verify:
- Memstore usage is tracked
- Flush behavior works correctly
- Client-visible metrics are accurate

### Pattern 9: Port Assignment Verification (Reduced Version)
```java
// BEFORE: Forces custom ports then verifies
int masterPort = HBaseTestingUtility.randomFreePort();
conf.setInt(HConstants.MASTER_PORT, masterPort);
cluster = new MiniHBaseCluster(conf, 1);
assertEquals(masterPort, cluster.getMaster().getRpcServer().getListenerAddress().getPort());

// AFTER: Verify ProcessBased's port management works correctly
cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).build();
cluster.waitClusterUp();

ServerName masterName = admin.getClusterMetrics().getMasterName();
assertNotNull("Master should have valid ServerName", masterName);
assertTrue("Master port should be valid", masterName.getPort() > 0);

// Verify RS ports are unique
for (ServerName rsName : admin.getClusterMetrics().getLiveServerMetrics().keySet()) {
    assertTrue("RS port should be valid", rsName.getPort() > 0);
    assertNotEquals("RS port should differ from master", masterName.getPort(), rsName.getPort());
}

// Key: ports are consistent across calls (identity preservation)
ServerName masterName2 = admin.getClusterMetrics().getMasterName();
assertEquals("Master port should be stable", masterName.getPort(), masterName2.getPort());
```

**Key Insight**: Don't fight ProcessBased's port management - verify it works correctly!

---

## Inserting Cluster Upgrade Method Calls

### Overview

When transforming tests to support rolling upgrades, you need to insert `cluster.upgrade()` method calls at appropriate points in the test. This section explains how to identify upgrade points and handle the critical pattern of **closing resources before upgrade and reopening them afterward**.

### Why Resource Management is Critical

During a rolling upgrade, cluster nodes are restarted with new software versions. This restart **breaks active connections** between the client and the nodes.

**Key principle**: Any active connection/table/scanner that spans an upgrade point must be:
1. **Closed** before calling `cluster.upgrade()`
2. **Reopened** after `cluster.upgrade()` completes

### Why Node Identity Preservation is Critical

In addition to closing resources, **node identity must be preserved** during upgrades.

**What is Node Identity?**
- RPC port (hbase.regionserver.port)
- HTTP port (hbase.regionserver.info.port)
- Network address (hostname)
- ServerName object

**Why It Matters:**

If a node's identity changes during restart/upgrade:
- ❌ Other nodes think it's a NEW RegionServer joining
- ❌ Original RegionServer appears DEAD
- ❌ Cluster triggers unnecessary region rebalancing
- ❌ Data may be unnecessarily moved
- ❌ Upgrade test fails to represent production behavior

**How ProcessBasedMiniHBaseCluster Preserves Identity:**

The cluster automatically:
1. **Persists port allocations** to disk before first startup
2. **Reuses persisted ports** during restart/upgrade
3. **Maintains work directory** with configuration
4. **Validates port availability** before restart
5. **Throws error** if identity cannot be preserved

**Example:**

```java
// Initial startup
cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
    .numRegionServers(3)
    .build();
// RS 0 gets RPC port 50001, persisted to disk

// Later: Rolling upgrade
ServerName rs0Before = getRegionServerServerName(0);  // localhost:50001
// rs0Before = localhost:50001:1234567890123

cluster.stopRegionServer(rs0Before);
cluster.changeRegionServerVersion(0, upgradeVersion);
cluster.startRegionServer();
// RS 0 loads persisted port 50001, starts with SAME ServerName

ServerName rs0After = getRegionServerServerName(0);
// rs0After = localhost:50001:9876543210987

assert rs0Before.getHostname().equals(rs0After.getHostname());  // localhost == localhost
assert rs0Before.getPort() == rs0After.getPort();  // 50001 == 50001
// Identity preserved! (Note: startCode will differ, which is expected)
```

**Automatic vs Manual:**

✅ **ProcessBased (Automatic)**:
- Ports persisted automatically
- Identity preserved across restarts
- No manual tracking needed
- Errors if identity cannot be preserved

❌ **Manual Mini Cluster**:
- Must manually track ports
- Easy to accidentally change ports
- No validation of identity preservation
- Silent failures possible

### Identifying Upgrade Points

An upgrade point is a logical location in your test where you want to simulate a rolling upgrade. Common upgrade points include:

1. **Mid-operation** - Testing that data created before upgrade is accessible after upgrade
2. **Between distinct test phases** - After setup operations but before verification
3. **After creating test data** - Testing upgrade with existing data
4. **During long-running operations** - Testing upgrade resilience

### Step-by-Step: Inserting Upgrade Calls

#### Step 1: Identify the Upgrade Point

Look for a logical point in the test where upgrade makes sense:

```java
// BEFORE: Original test without upgrade
try (Table table = connection.getTable(tableName)) {
    Put put1 = new Put(Bytes.toBytes("row1"));
    put1.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("q"), Bytes.toBytes("value1"));
    table.put(put1);
    // <-- Potential upgrade point

    Put put2 = new Put(Bytes.toBytes("row2"));
    put2.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("q"), Bytes.toBytes("value2"));
    table.put(put2);
}
```

#### Step 2: Close Resources Before Upgrade

If a resource is open at the upgrade point, close it first:

```java
// AFTER: With upgrade point inserted
Put put1 = new Put(Bytes.toBytes("row1"));
put1.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("q"), Bytes.toBytes("value1"));

try (Table table = connection.getTable(tableName)) {
    table.put(put1);
}  // CRITICAL: Close the table before upgrade since nodes will be restarted

System.out.println("Closed table before rolling upgrade");
```

#### Step 3: Call cluster.upgrade()

```java
// Perform rolling upgrade
cluster.upgrade();  // Executes full rolling upgrade procedure
System.out.println("Rolling upgrade completed successfully");
```

**Note**: The `cluster.upgrade()` method:
- Automatically follows the HBase rolling upgrade procedure
- Restarts RegionServers one at a time
- Waits for cluster stabilization between restarts
- Ensures regions remain available

#### Step 4: Reopen Resources After Upgrade

If you need to continue operations, reopen the resource:

```java
// Reopen the table after upgrade
try (Table table = connection.getTable(tableName)) {
    System.out.println("Reopened table after upgrade");

    // Continue operations
    Put put2 = new Put(Bytes.toBytes("row2"));
    put2.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("q"), Bytes.toBytes("value2"));
    table.put(put2);
}
```

### Complete Example Pattern

```java
@Test
public void testPutGet() throws Exception {
    Configuration conf = HBaseConfiguration.create();

    ProcessBasedMiniHBaseCluster cluster =
        new ProcessBasedMiniHBaseCluster.Builder(conf)
            .numRegionServers(3)
            .build();

    try (Connection connection = cluster.getConnection();
         Admin admin = connection.getAdmin()) {

        cluster.waitClusterUp();

        // Create table
        TableDescriptor td = TableDescriptorBuilder
            .newBuilder(TableName.valueOf("test"))
            .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf"))
            .build();
        admin.createTable(td);

        // Write data before upgrade
        try (Table table = connection.getTable(TableName.valueOf("test"))) {
            Put put1 = new Put(Bytes.toBytes("row1"));
            put1.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("q"), Bytes.toBytes("value1"));
            table.put(put1);
        }  // Close table before upgrade

        // === ROLLING UPGRADE POINT ===
        System.out.println("Closed table before rolling upgrade");

        // Capture node identities BEFORE upgrade
        Map<Integer, ServerName> preUpgradeServerNames = new HashMap<>();
        ClusterMetrics metricsBeforeUpgrade = admin.getClusterMetrics();
        int index = 0;
        for (ServerName sn : metricsBeforeUpgrade.getLiveServerMetrics().keySet()) {
            preUpgradeServerNames.put(index++, sn);
        }

        // Perform rolling upgrade
        cluster.upgrade();
        System.out.println("Rolling upgrade completed successfully");

        // Verify node identities PRESERVED
        ClusterMetrics metricsAfterUpgrade = admin.getClusterMetrics();
        index = 0;
        for (ServerName postUpgrade : metricsAfterUpgrade.getLiveServerMetrics().keySet()) {
            ServerName preUpgrade = preUpgradeServerNames.get(index);
            if (!postUpgrade.getHostname().equals(preUpgrade.getHostname())) {
                throw new AssertionError(
                    "Node " + index + " hostname changed during upgrade! " +
                    "Before: " + preUpgrade.getHostname() + ", After: " + postUpgrade.getHostname());
            }
            if (postUpgrade.getPort() != preUpgrade.getPort()) {
                throw new AssertionError(
                    "Node " + index + " port changed during upgrade! " +
                    "Before: " + preUpgrade.getPort() + ", After: " + postUpgrade.getPort());
            }
            index++;
        }
        System.out.println("Node identities preserved across upgrade");

        // Reopen table and continue operations
        try (Table table = connection.getTable(TableName.valueOf("test"))) {
            System.out.println("Reopened table after upgrade");

            // Verify old data still accessible
            Get get1 = new Get(Bytes.toBytes("row1"));
            Result result1 = table.get(get1);
            assertNotNull(result1.getValue(Bytes.toBytes("cf"), Bytes.toBytes("q")));

            // Write new data after upgrade
            Put put2 = new Put(Bytes.toBytes("row2"));
            put2.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("q"), Bytes.toBytes("value2"));
            table.put(put2);

            // Verify new data
            Get get2 = new Get(Bytes.toBytes("row2"));
            Result result2 = table.get(get2);
            assertNotNull(result2.getValue(Bytes.toBytes("cf"), Bytes.toBytes("q")));
        }

    } finally {
        cluster.shutdown();
    }
}
```

### Common Mistakes to Avoid

#### ❌ Mistake 1: Not closing resources before upgrade

```java
// WRONG - Table remains open during upgrade
try (Table table = connection.getTable(tableName)) {
    table.put(put1);
    cluster.upgrade();  // Connection will break!
    table.put(put2);  // This will fail!
}
```

#### ✅ Correct Pattern

```java
// CORRECT - Close, upgrade, reopen
Put put1 = new Put(Bytes.toBytes("row1"));
try (Table table = connection.getTable(tableName)) {
    table.put(put1);
}  // Close

cluster.upgrade();

Put put2 = new Put(Bytes.toBytes("row2"));
try (Table table = connection.getTable(tableName)) {
    table.put(put2);
}
```

#### ❌ Mistake 2: Not verifying node identity preservation

```java
// WRONG - Assumes identity preserved without verification
cluster.upgrade();
// Continue testing without checking if nodes maintained their addresses
```

#### ✅ Correct Pattern

```java
// CORRECT - Verify identity preservation
Map<Integer, ServerName> preUpgradeServerNames = captureServerNames(admin);

cluster.upgrade();

// Verify all nodes kept same addresses
verifyServerNamesPreserved(admin, preUpgradeServerNames);
```

---

## Upgrade Checkpoint Test Methods

### Overview

**Recommended Approach**: Instead of hardcoding a single upgrade point in each test, generate multiple test methods with checkpoint suffixes. Each test method tests the same logic but with upgrade at a different checkpoint. This provides comprehensive upgrade coverage with Maven Surefire compatibility.

**Key Benefits**:
- Single test logic → multiple test methods with different checkpoints
- 100% reproducible (deterministic checkpoint execution)
- Comprehensive coverage (standard + test-specific checkpoints)
- Guaranteed cleanup between executions
- Easy Maven execution: can run specific checkpoint with `-Dtest=Test#method_CHECKPOINT`
- Compatible with Maven Surefire single-method execution

### Base Class: ProcessBasedUpgradeTestBase

All ProcessBased tests should extend `ProcessBasedUpgradeTestBase`, which provides:

1. **@Before cleanup**: Kills orphaned processes, cleans old directories
2. **@After cleanup**: Closes connection, shuts down cluster, verifies cleanup
3. **checkpoint(name)**: Performs upgrade if name matches parameter
4. **shouldUpgrade(name)**: Checks if upgrade should happen

**Location**: `org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase`

### Transformation Steps

#### Step 1: Identify Checkpoints for Each Test Method

For each original test method, identify:
1. **Standard checkpoints** (always include):
   - `NO_UPGRADE` - Baseline test without upgrade
   - `AFTER_CLUSTER_START` - Upgrade immediately after cluster starts

2. **Test-specific checkpoints** (from actual checkpoint() calls):
   - Look for all `checkpoint("NAME")` calls in the test method
   - Each unique checkpoint name becomes a test method variant

**Example:**
```java
// Original test method
@Test
public void testCompaction() {
  cluster = ...;
  checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

  admin.createTable(...);
  checkpoint("AFTER_CREATE_TABLE");

  writeData();
  checkpoint("AFTER_WRITE_DATA");

  admin.compact(...);
  checkpoint("AFTER_COMPACT");

  verify();
}
```

**Identified checkpoints:**
- `NO_UPGRADE` (standard)
- `AFTER_CLUSTER_START` (standard + in test)
- `AFTER_CREATE_TABLE` (test-specific)
- `AFTER_WRITE_DATA` (test-specific)
- `AFTER_COMPACT` (test-specific)

#### Step 2: Generate Test Methods

Create one test method per checkpoint with naming pattern `testMethodName_CHECKPOINT_NAME()`:

```java
// Add imports
import org.apache.hadoop.hbase.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.hadoop.hbase.upgrade.HBaseUpgradeCheckpoints;

// Extend base class (NO parameterization annotations)
public class TestCompaction_ProcessBased extends ProcessBasedUpgradeTestBase {

  @Test
  public void testCompaction_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

    // Full test logic
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);

    admin.createTable(...);
    checkpoint("AFTER_CREATE_TABLE");

    writeData();
    checkpoint("AFTER_WRITE_DATA");

    admin.compact(...);
    checkpoint("AFTER_COMPACT");

    verify();
  }

  @Test
  public void testCompaction_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

    // Same full test logic - upgrade happens at AFTER_CLUSTER_START
    cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).build();
    connection = cluster.getConnection();
    admin = connection.getAdmin();
    checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);
    admin.createTable(...);
    checkpoint("AFTER_CREATE_TABLE");
    writeData();
    checkpoint("AFTER_WRITE_DATA");
    admin.compact(...);
    checkpoint("AFTER_COMPACT");
    verify();
  }

  @Test
  public void testCompaction_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";

    // Same full test logic - upgrade happens at AFTER_CREATE_TABLE
    // ... (full duplication)
  }

  @Test
  public void testCompaction_AFTER_WRITE_DATA() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE_DATA";

    // Same full test logic - upgrade happens at AFTER_WRITE_DATA
    // ... (full duplication)
  }

  @Test
  public void testCompaction_AFTER_COMPACT() throws Exception {
    upgradeCheckpoint = "AFTER_COMPACT";

    // Same full test logic - upgrade happens at AFTER_COMPACT
    // ... (full duplication)
  }
}
```

#### Step 3: Code Duplication Note

Note that each test method contains **full duplication** of the test logic. This is intentional:
- Makes each test method independently runnable
- Clear what each checkpoint variant does
- Compatible with Maven Surefire single-method execution
- No shared state between methods (base class handles cleanup)

**BEFORE** (manual cleanup):
```java
@Test
public void testCompaction() throws Exception {
  Configuration conf = HBaseConfiguration.create();
  ProcessBasedMiniHBaseCluster cluster = new Builder(conf).build();
  Connection connection = cluster.getConnection();

  try {
    // test logic
  } finally {
    connection.close();
    cluster.shutdown();
  }
}
```

**AFTER** (automatic cleanup via base class, with checkpoint test methods):
```java
@Test
public void testCompaction_NO_UPGRADE() throws Exception {
  upgradeCheckpoint = HBaseUpgradeCheckpoints.NO_UPGRADE;

  // Use conf, cluster, connection from base class
  cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).build();
  connection = cluster.getConnection();
  admin = connection.getAdmin();

  // test logic with checkpoints
  TableName tableName = TableName.valueOf("test");
  admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf"))
      .build());
  checkpoint("AFTER_CREATE_TABLE");

  // No try-finally needed - @After handles cleanup!
}

@Test
public void testCompaction_AFTER_CLUSTER_START() throws Exception {
  upgradeCheckpoint = HBaseUpgradeCheckpoints.AFTER_CLUSTER_START;

  // Same test logic
  cluster = new ProcessBasedMiniHBaseCluster.Builder(conf).build();
  connection = cluster.getConnection();
  admin = connection.getAdmin();
  TableName tableName = TableName.valueOf("test");
  admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf"))
      .build());
  checkpoint("AFTER_CREATE_TABLE");
}
```

#### Step 4: Replace Hardcoded cluster.upgrade() with checkpoint()

If the original test had a hardcoded `cluster.upgrade()` call, replace it with `checkpoint()` calls at appropriate points. The checkpoint() method in the base class will perform the upgrade only if the current test method's `upgradeCheckpoint` field matches the checkpoint name.

**BEFORE** (hardcoded upgrade point):
```java
try (Table table = connection.getTable(tableName)) {
    table.put(put1);
}

// === ROLLING UPGRADE POINT ===
cluster.upgrade();

try (Table table = connection.getTable(tableName)) {
    table.put(put2);
}
```

**AFTER** (checkpoint-based, same logic in all test methods):
```java
// This code appears in EVERY test method variant (NO_UPGRADE, AFTER_WRITE_DATA, etc.)
try (Table table = connection.getTable(tableName)) {
    table.put(put1);
}
checkpoint("AFTER_WRITE_DATA");

// Close before potential upgrade (handled by checkpoint)
// Reopen after (always needed, regardless of upgrade)
try (Table table = connection.getTable(tableName)) {
    checkpoint("AFTER_REOPEN");

    table.put(put2);
}
checkpoint("AFTER_SECOND_WRITE");

// The upgrade only happens if upgradeCheckpoint matches a checkpoint name
// - In testMethod_NO_UPGRADE(): no upgrade happens
// - In testMethod_AFTER_WRITE_DATA(): upgrade happens at AFTER_WRITE_DATA
// - In testMethod_AFTER_REOPEN(): upgrade happens at AFTER_REOPEN
```

### Checkpoint Selection Guidelines

For each original test method, generate test method variants for:

1. **Standard checkpoints** (always include):
   - `NO_UPGRADE` - Baseline test without upgrade
   - `AFTER_CLUSTER_START` - Upgrade immediately after cluster starts

2. **Test-specific checkpoints** (from actual checkpoint() calls):
   - Scan the test method for all `checkpoint("NAME")` calls
   - Generate a test method variant for each unique checkpoint name

3. **Naming conventions**:
   - Use HBaseUpgradeCheckpoints constants for standard checkpoints
   - Use descriptive strings for test-specific checkpoints
   - Be descriptive: "AFTER_FLUSH" not "CHECKPOINT_7"

**Example checkpoint identification:**
```java
// Original test method with checkpoint() calls
@Test
public void testTableOperations() {
  cluster = ...;
  checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);  // Found #1

  admin.createTable(...);
  checkpoint("AFTER_CREATE_TABLE");                         // Found #2

  writeData();
  checkpoint("AFTER_WRITE");                                // Found #3

  admin.flush(...);
  checkpoint("AFTER_FLUSH");                                // Found #4

  verify();
}
```

**Generated test methods:**
- `testTableOperations_NO_UPGRADE()` - standard
- `testTableOperations_AFTER_CLUSTER_START()` - standard + found in test
- `testTableOperations_AFTER_CREATE_TABLE()` - test-specific
- `testTableOperations_AFTER_WRITE()` - test-specific
- `testTableOperations_AFTER_FLUSH()` - test-specific

**Common checkpoint categories**:
- Cluster lifecycle: `AFTER_CLUSTER_START`
- Table operations: `AFTER_CREATE_TABLE`, `AFTER_DELETE_TABLE`
- Data operations: `AFTER_WRITE_DATA`, `AFTER_READ_DATA`
- Flush/Compact: `AFTER_FLUSH`, `AFTER_COMPACT`, `AFTER_MAJOR_COMPACT`
- Resource lifecycle: `AFTER_CLOSE`, `AFTER_REOPEN`
- Verification: `BEFORE_VERIFICATION`, `AFTER_VERIFICATION`

### Running Checkpoint Test Methods

**Run all test methods (all checkpoints for all tests)**:
```bash
mvn test -Dtest=TestCompaction_ProcessBased \
  -Dhbase.start.home=/opt/hbase-2.6.0 \
  -Dhbase.upgrade.home=/opt/hbase-3.0.0
```

**Run specific checkpoint for specific test**:
```bash
mvn test -Dtest=TestCompaction_ProcessBased#testCompaction_AFTER_FLUSH \
  -Dhbase.start.home=/opt/hbase-2.6.0 \
  -Dhbase.upgrade.home=/opt/hbase-3.0.0
```

**Run all checkpoints for one test method** (using wildcard):
```bash
mvn test -Dtest='TestCompaction_ProcessBased#testCompaction_*' \
  -Dhbase.start.home=/opt/hbase-2.6.0 \
  -Dhbase.upgrade.home=/opt/hbase-3.0.0
```

**Run all baseline (NO_UPGRADE) tests**:
```bash
mvn test -Dtest='TestCompaction_ProcessBased#*_NO_UPGRADE' \
  -Dhbase.start.home=/opt/hbase-2.6.0
```

**Run all tests with specific checkpoint across all methods**:
```bash
mvn test -Dtest='TestCompaction_ProcessBased#*_AFTER_CLUSTER_START' \
  -Dhbase.start.home=/opt/hbase-2.6.0 \
  -Dhbase.upgrade.home=/opt/hbase-3.0.0
```

---

## When to Comment Out Logic

### Only comment out operations that are:

1. **Internal Storage Operations**
   - HRegion.getStore() access
   - HFile internals inspection
   - Storage directory verification
   - WAL internals

2. **In-Process Manipulation**
   - Direct object field modification
   - Mock object injection
   - Reflection on private fields

3. **JVM-Level Operations**
   - Memory manipulation
   - Thread state inspection (beyond public APIs)
   - ClassLoader manipulation

### Comment Template

Use this template when commenting out unsupported logic:

```java
// TRANSFORMATION NOTE: [Brief explanation of what was removed]
// [Why it was removed - what makes it inaccessible via client APIs]
// [What the original code verified/tested]
// [Suggestion for alternative verification if applicable, or "No client-side alternative available"]
//
// Original code:
// [indented commented-out code]
```

### Example: Internal Storage Check

```java
// TRANSFORMATION NOTE: Internal HStore verification removed.
// The HStore class is internal to the HRegionServer process and not
// accessible via any client API. The original test verified store file count
// after compaction to ensure files were compacted correctly.
// Alternative: Use Admin.getRegionMetrics() to get approximate store file count:
//
// try (Admin admin = conn.getAdmin()) {
//     List<RegionMetrics> regionMetrics = admin.getRegionMetrics(serverName);
//     for (RegionMetrics rm : regionMetrics) {
//         if (Bytes.equals(rm.getRegionName(), regionName)) {
//             int storeFileCount = rm.getStoreFileCount();
//             // Note: This is approximate and aggregated across all stores
//         }
//     }
// }
//
// Original code:
// HRegionServer rs = cluster.getRegionServer(0);
// HRegion region = rs.getOnlineRegion(regionName);
// HStore store = region.getStore(Bytes.toBytes("cf"));
// assertEquals(1, store.getStorefilesCount());  // Verify compacted to 1 file
```

### When NOT to Comment Out

Do NOT comment out if there's a client-side equivalent:

❌ **WRONG**:
```java
// TRANSFORMATION NOTE: Cannot access HMaster directly
// Original code:
// boolean balanced = cluster.getMaster().balance();
```

✅ **CORRECT**:
```java
// Use Admin API instead of direct master access
try (Admin admin = connection.getAdmin()) {
    boolean balanced = admin.balance();
}
```

### Custom Class Analysis: Don't Skip Automatically!

When a test uses a custom class (e.g., `MyRegionServer extends MiniHBaseClusterRegionServer`), **don't automatically skip the test**. Instead:

1. **Analyze what the custom class actually does**
2. **Determine if the behavior is achievable via client API**
3. **Transform the accessible parts**

**Example: Custom RegionServer Class**
```java
// BEFORE: Custom RS that forces reporting
public static class MyRegionServer extends MiniHBaseCluster.MiniHBaseClusterRegionServer {
    @Override
    public void tryRegionServerReport(long start, long end) {
        super.tryRegionServerReport(start, end);
    }
}
```

**Analysis**:
- What it does: Forces RS to report metrics to master
- In ProcessBased: RS naturally reports on schedule
- **Solution**: Wait for natural reporting OR verify via ClusterMetrics that metrics are updating

```java
// AFTER: Verify metrics are being reported (don't need custom class)
ServerMetrics initialMetrics = admin.getClusterMetrics()
    .getLiveServerMetrics().values().iterator().next();
long initialRequests = initialMetrics.getRequestCount();

// Do some operations
table.put(somePut);
table.get(someGet);

// Wait for metrics to update (natural reporting cycle)
Waiter.waitFor(conf, 30000, () -> {
    ServerMetrics updatedMetrics = admin.getClusterMetrics()
        .getLiveServerMetrics().values().iterator().next();
    return updatedMetrics.getRequestCount() > initialRequests;
});
```

**Key Insight**: Custom classes often just provide convenience for internal testing. The underlying behavior is usually achievable through standard APIs or waiting for natural processes.

---

## Testing Checklist

### Before Running Test

- [ ] **File organization correct**
  - Transformed test in same directory as original
  - File name has `_ProcessBased` suffix
  - Package declaration identical to original
  - Class name matches file name with `_ProcessBased` suffix
  - Javadoc includes `@see` reference to original test

- [ ] **System properties ready**
  ```bash
  # System properties will be passed when running tests:
  mvn test -Dtest=TestMyFeature_ProcessBased \
    -Dhbase.start.home=/opt/hbase-2.6.0 \
    -Dhbase.upgrade.home=/opt/hbase-3.0.0
  ```

- [ ] **Test compiles without errors**
  ```bash
  mvn test-compile -pl hbase-server
  ```

- [ ] **Imports are correct**
  - ProcessBasedMiniHBaseCluster imported
  - Unnecessary HMaster/HRegionServer imports removed
  - Connection, Admin, Table imports added

### During Test Execution

- [ ] **Cluster starts successfully**
  - Check logs for "Cluster started successfully"
  - Verify all RegionServers are up

- [ ] **Client accessible**
  - Can get Connection instance
  - Can get Admin instance
  - Can perform basic operations

- [ ] **Core assertions pass**
  - Main test logic validates correctly
  - Data integrity checks pass

### After Test Execution

- [ ] **Test passes (or fails as expected)**
  - If original test passed, transformed test should pass
  - If failure, verify it's not due to transformation

- [ ] **Cluster cleans up properly**
  - No orphaned HMaster/HRegionServer processes (check with `jps`)
  - Test directories cleaned up

- [ ] **Review transformation quality**
  - Maximum logic preserved?
  - Only necessary operations commented out?
  - Appropriate documentation added?

---

## Best Practices

### DO ✅

1. **Consult mapping tables first** - Before assuming something is unsupported, check all mapping tables

2. **Use the API hierarchy** - Try Connection/Table → Admin → ClusterMetrics → RPC in order

3. **Preserve test intent** - Even if implementation changes, maintain what the test is verifying

4. **System properties are automatic** - No manual environment checks needed!

5. **Keep transformations minimal** - Change only what's necessary

6. **Document significant changes** - But only non-obvious ones

7. **Test both single-version and multi-version scenarios** when applicable

8. **Verify node identity preservation** - Always check ServerName unchanged after restart/upgrade

9. **Capture identity snapshot before upgrades** - Store ServerName to verify preservation

10. **Use try-with-resources** - Ensures proper cleanup of Table, Scanner, Connection

### DON'T ❌

1. **Don't give up on transformation too early** - Most operations have client equivalents

2. **Don't remove test logic without checking mapping tables**

3. **Don't use MiniHBaseCluster-specific test utilities** - Many have client API equivalents

4. **Don't over-document** - Only comment what's not obvious

5. **Don't mix MiniHBaseCluster and ProcessBasedMiniHBaseCluster** in same test

6. **Don't manually check environment variables** - System properties are handled automatically!

7. **Don't forget to close resources before checkpoint()** - Tables, Scanners must be closed

### Performance Considerations

1. **Process startup is slower** - ProcessBasedMiniHBaseCluster takes longer to start than MiniHBaseCluster
   - Be patient with cluster startup
   - Consider increasing timeouts for slow systems

2. **Node restarts are real** - Can't artificially skip them
   - Use checkpoint() judiciously
   - Account for restart overhead in test timeouts

3. **RPC overhead** - All operations go through RPC
   - Slightly slower than in-process calls
   - Not significant for most tests

---

## Quick Reference Decision Tree

```
Found server-side operation?
    │
    ├─> Is it already client-side? (Table.put/get, Admin.createTable)
    │   └─> ✅ Use as-is, no transformation needed
    │
    ├─> Check HMaster table (Table 2)
    │   ├─> Found equivalent?
    │   │   └─> ✅ Use Admin API
    │   └─> Not found?
    │       └─> Continue...
    │
    ├─> Check HRegionServer table (Table 3)
    │   ├─> Found equivalent?
    │   │   └─> ✅ Use Admin/Table API
    │   └─> Not found?
    │       └─> Continue...
    │
    ├─> Check Admin operations table (Table 4)
    │   ├─> Found equivalent?
    │   │   └─> ✅ Use Admin API
    │   └─> Not found?
    │       └─> Continue...
    │
    ├─> Check Monitoring table (Table 5)
    │   ├─> Found equivalent?
    │   │   └─> ✅ Use ClusterMetrics or Admin.getRegionMetrics()
    │   └─> Not found?
    │       └─> Continue...
    │
    └─> No client-side equivalent exists
        └─> ❌ Comment out with documentation template
```

---

## Summary

### Transformation Success Criteria

A successful transformation:
1. ✅ Compiles without errors
2. ✅ Runs with ProcessBasedMiniHBaseCluster
3. ✅ Preserves maximum test logic
4. ✅ Uses client APIs (Connection, Admin, Table) for all accessible operations
5. ✅ Comments out only truly inaccessible operations (internal storage, WAL internals)
6. ✅ Includes minimal, clear documentation
7. ✅ Passes when original test passed

### Key Takeaways

- **Most operations have client equivalents** - Consult mapping tables thoroughly
- **Use the API hierarchy** - Connection/Table → Admin → ClusterMetrics → RPC
- **Only comment out internal storage/WAL operations** - Everything else has an API
- **Document sparingly** - Only non-obvious transformations
- **Test thoroughly** - Verify core test logic preserved
- **Node identity preservation is critical** - Verify ServerName port/hostname unchanged

---

**End of HBase Test Transformation Guide**

This guide provides comprehensive instructions for transforming HBase tests from MiniHBaseCluster to ProcessBasedMiniHBaseCluster, with complete API mapping tables and concrete examples.
