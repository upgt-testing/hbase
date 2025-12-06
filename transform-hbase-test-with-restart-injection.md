# Prompt: Transform HBase Mini Cluster Test with Restart Position Injection

## Objective

Transform an existing HBase mini cluster test to inject restart positions for distributed system restart testing. The transformation will generate:
1. A new test file with `_RestartInjected` suffix
2. A restart configuration file for the Maven plugin

## Input

- **Test File Path**: Path to the original test file (e.g., `/path/to/TestHBaseOperations.java`)
- **Test Class**: Fully-qualified class name (e.g., `org.apache.hadoop.hbase.TestHBaseOperations`)

## Output

1. **Generated Test File**: `{OriginalFileName}_RestartInjected.java` at the same directory as the input file
2. **Restart Configuration**: `restart-config.json` in the `restarts-config/` directory under the same module directory as the test file, if not exist create it.

## Transformation Instructions

### Step 1: Analyze the Original Test

Read the input test file and identify:

1. **Cluster Setup**: Find the HBase cluster instance variable (e.g., `MiniHBaseCluster cluster`)
2. **Test Methods**: Identify all `@Test` annotated methods
3. **Critical Operations**: Look for operations that involve state transitions, such as:
   - Table operations: `createTable()`, `deleteTable()`, `enableTable()`, `disableTable()`, `modifyTable()`
   - Data operations: `put()`, `get()`, `delete()`, `scan()`, `append()`, `increment()`
   - Flush operations: `flush()`, `flushRegion()`, `flushcache()`
   - Compaction operations: `compact()`, `majorCompact()`
   - Region operations: Region split, region merge, region assignment, region move
   - WAL operations: WAL sync, WAL roll
   - Replication operations: Replication setup, replication sync
   - Snapshot operations: `snapshot()`, `cloneSnapshot()`, `restoreSnapshot()`
   - Bulk load operations: `bulkLoadHFiles()`

### Step 2: Identify Restart Points

For each test method, identify potential restart points based on these criteria:

**Good Restart Points** (inject here):
- After table creation but before data insertion
- After data put operations (especially with WAL sync)
- After flush operations
- During compaction operations
- After region split/merge
- During scan operations (for consistency testing)
- After snapshot operations
- Before/after region server failover
- During bulk load operations
- After append/increment operations
- After WAL sync

**Poor Restart Points** (avoid):
- Before cluster setup (no cluster exists yet)
- After cluster teardown (cluster already destroyed)
- During trivial operations (simple reads with no state changes)
- Operations that are too fast to test meaningful state

**Naming Convention for Restart Positions**:
- Use descriptive, lowercase names with underscores
- Pattern: `{operation}_{context}`
- Examples:
  - `after_table_create`
  - `after_put`
  - `after_flush`
  - `during_scan`
  - `after_region_split`
  - `before_compact`
  - `after_wal_sync`
  - `after_snapshot`
  - `before_bulk_load`
  - `after_increment`

### Step 3: Generate the Restart-Injected Test File

Create a new test file with the following transformations:

#### 3.1 Package and Imports

```java
// Keep original package declaration
package org.apache.hadoop.hbase;

// Add these imports at the top (if not already present)
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

// Keep all original imports
```

#### 3.2 Class Declaration

```java
// Original class name: TestHBaseOperations
// New class name: TestHBaseOperations_RestartInjected
public class TestHBaseOperations_RestartInjected {
    // Keep all original fields and variables
}
```

#### 3.3 Cluster Setup and Teardown

Keep the `@Before` and `@After` methods unchanged:

```java
@Before
public void setUp() throws Exception {
    // Keep original setup code unchanged
}

@After
public void tearDown() throws Exception {
    // Keep original teardown code unchanged
}
```

#### 3.4 Transform Test Methods

For each `@Test` method, apply the following transformations:

**Original Test Method**:
```java
@Test
public void testTableOperations() throws Exception {
    TableName tableName = TableName.valueOf("test");
    HTableDescriptor desc = new HTableDescriptor(tableName);
    desc.addFamily(new HColumnDescriptor("cf"));

    admin.createTable(desc);

    Table table = connection.getTable(tableName);
    Put put = new Put(Bytes.toBytes("row1"));
    put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("col"), Bytes.toBytes("value"));
    table.put(put);
    table.close();

    admin.flush(tableName);

    assertTrue(admin.tableExists(tableName));
}
```

**Transformed Test Method**:
```java
@Test
public void testTableOperations() throws Exception {
    TableName tableName = TableName.valueOf("test");
    HTableDescriptor desc = new HTableDescriptor(tableName);
    desc.addFamily(new HColumnDescriptor("cf"));

    admin.createTable(desc);

    // RESTART POINT 1: after_table_create
    RestartFramework.at("after_table_create")
        .on(cluster)  // Use the cluster instance from setUp()
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    Table table = connection.getTable(tableName);
    Put put = new Put(Bytes.toBytes("row1"));
    put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("col"), Bytes.toBytes("value"));
    table.put(put);

    // RESTART POINT 2: after_put
    RestartFramework.at("after_put")
        .on(cluster)
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    table.close();

    admin.flush(tableName);

    // RESTART POINT 3: after_flush
    RestartFramework.at("after_flush")
        .on(cluster)
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    assertTrue(admin.tableExists(tableName));
}
```

**Injection Pattern**:

1. **After Table Operations**:
   ```java
   admin.createTable(desc);

   // Inject restart point
   RestartFramework.at("after_table_create")
       .on(cluster)
       .restart("master")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

2. **After Data Operations**:
   ```java
   table.put(put);

   // Inject restart point
   RestartFramework.at("after_put")
       .on(cluster)
       .restart("regionserver")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

3. **Before Critical Operations**:
   ```java
   // Inject restart point before compact
   RestartFramework.at("before_compact")
       .on(cluster)
       .restart("regionserver")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();

   admin.majorCompact(tableName);
   ```

4. **During Long Operations**:
   ```java
   // Start large scan
   Scan scan = new Scan();
   ResultScanner scanner = table.getScanner(scan);

   int count = 0;
   for (Result result : scanner) {
       count++;

       // Inject restart point in the middle
       if (count == 500) {
           RestartFramework.at("during_scan")
               .on(cluster)
               .restart("regionserver")
               .withIndex(0)
               .withMode(RestartMode.GRACEFUL)
               .execute();
       }
   }
   scanner.close();
   ```

#### 3.5 HBase-Specific Node Roles

HBase has two primary node types:

- **`master`** (or `"hmaster"`): The master node that manages region assignment, table operations, and cluster coordination
- **`regionserver`** (or `"rs"`): Worker nodes that serve regions and handle data read/write operations

**Default Restart Configuration**:
Use these defaults for all injected restart points:
- **For table/metadata operations**: `"master"` (manages table schema, region assignment)
- **For data operations**: `"regionserver"` (serves data, handles reads/writes)
- **For cluster-wide operations**: Both `"master"` and `"regionserver"`
- **Node Index**: `0` (first node)
- **Restart Mode**: `RestartMode.GRACEFUL` (default, safest)

#### 3.6 Node Role Selection Guidelines

| Operation Type | Primary Node Role | Secondary Node Role | Reason |
|----------------|-------------------|---------------------|--------|
| Table creation/deletion | `master` | - | Master manages table metadata |
| Table enable/disable | `master` | `regionserver` | Master coordinates, RS serves regions |
| Region assignment | `master` | - | Master assigns regions |
| Put/Get/Delete operations | `regionserver` | - | RS serves data |
| Flush operations | `regionserver` | - | RS flushes memstore to disk |
| Compaction operations | `regionserver` | - | RS compacts HFiles |
| Region split/merge | `master` | `regionserver` | Master coordinates, RS executes |
| WAL operations | `regionserver` | - | RS manages WAL |
| Snapshot operations | `master` | `regionserver` | Master coordinates, RS participates |
| Scan operations | `regionserver` | - | RS serves scan requests |
| Bulk load | `regionserver` | `master` | RS loads data, Master assigns regions |
| Replication | `regionserver` | `master` | RS replicates, Master coordinates |

### Step 4: Generate Restart Configuration File

Create `restarts-config/restart-config.json` with the following structure:

```json
{
  "tests": [
    {
      "testClass": "org.apache.hadoop.hbase.TestHBaseOperations_RestartInjected",
      "testMethod": "testTableOperations",
      "restartPoints": [
        {
          "position": "after_table_create",
          "targets": ["master", "regionserver"],
          "modes": ["GRACEFUL", "CRASH"]
        },
        {
          "position": "after_put",
          "targets": ["regionserver"],
          "modes": ["GRACEFUL", "CRASH", "DELAYED_CRASH"]
        },
        {
          "position": "after_flush",
          "targets": ["regionserver"],
          "modes": ["GRACEFUL", "CRASH"]
        }
      ]
    }
  ]
}
```

#### Configuration Generation Rules

For each test method in the transformed test:

1. **Create a test specification** with:
   - `testClass`: The fully-qualified name of the generated test class
   - `testMethod`: The test method name (same as original)
   - `restartPoints`: Array of restart point configurations

2. **For each restart point** injected in the test method:
   - `position`: The position identifier used in `.at("...")`
   - `targets`: Array of node roles to test
   - `modes`: Array of restart modes to test

#### Target Selection for HBase Operations

**Master-only operations** (`["master"]`):
- Table creation, deletion, modification
- Region assignment decisions
- Load balancing
- Schema changes
- Namespace operations
- Cluster state management

**RegionServer-only operations** (`["regionserver"]`):
- Data read/write (Put, Get, Delete)
- Memstore flush
- Compaction (minor and major)
- WAL management
- Block cache operations
- Local data serving

**Both Master and RegionServer** (`["master", "regionserver"]`):
- Table enable/disable
- Region split/merge
- Snapshot operations
- Bulk load operations
- Replication setup
- Failover scenarios
- Cluster-wide consistency operations

#### Mode Selection Guidelines

- **`["GRACEFUL"]`**: Basic test, verify restart works
  - Use for: Initial testing, simple state transitions

- **`["GRACEFUL", "CRASH"]`**: Standard test, verify crash recovery
  - Use for: Table operations, data operations, region operations

- **`["GRACEFUL", "CRASH", "DELAYED_CRASH"]`**: Advanced test, verify timing-sensitive operations
  - Use for: WAL sync, flush operations, compaction, replication, distributed coordination

### Step 5: File Placement

1. **Generated Test File**:
   - Location: Same directory as original test file
   - Name: `{OriginalClassName}_RestartInjected.java`
   - Example: `TestHBaseOps.java` → `TestHBaseOps_RestartInjected.java`

2. **Restart Configuration**:
   - Location: `restarts-config/` directory under the same module directory as the test file
   - Name: `restart-config.json`
   - If file exists, append to the `tests` array (avoid duplicates)
   - If file doesn't exist, create new file

## Example Transformation

### Input: `TestPutFlush.java`

```java
package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TestPutFlush {
    private HBaseTestingUtility testUtil;
    private MiniHBaseCluster cluster;
    private Admin admin;
    private Connection connection;

    @Before
    public void setUp() throws Exception {
        testUtil = new HBaseTestingUtility();
        cluster = testUtil.startMiniCluster(1, 3);
        connection = testUtil.getConnection();
        admin = connection.getAdmin();
    }

    @After
    public void tearDown() throws Exception {
        if (admin != null) {
            admin.close();
        }
        if (connection != null) {
            connection.close();
        }
        if (testUtil != null) {
            testUtil.shutdownMiniCluster();
        }
    }

    @Test
    public void testFlushAfterPut() throws Exception {
        TableName tableName = TableName.valueOf("test");
        testUtil.createTable(tableName, Bytes.toBytes("cf"));

        Table table = connection.getTable(tableName);
        Put put = new Put(Bytes.toBytes("row1"));
        put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("col"), Bytes.toBytes("data"));
        table.put(put);

        admin.flush(tableName);

        table.close();
        assertTrue(admin.tableExists(tableName));
    }
}
```

### Output 1: `TestPutFlush_RestartInjected.java`

```java
package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import static org.junit.Assert.*;

public class TestPutFlush_RestartInjected {
    private HBaseTestingUtility testUtil;
    private MiniHBaseCluster cluster;
    private Admin admin;
    private Connection connection;

    @Before
    public void setUp() throws Exception {
        testUtil = new HBaseTestingUtility();
        cluster = testUtil.startMiniCluster(1, 3);
        connection = testUtil.getConnection();
        admin = connection.getAdmin();
    }

    @After
    public void tearDown() throws Exception {
        if (admin != null) {
            admin.close();
        }
        if (connection != null) {
            connection.close();
        }
        if (testUtil != null) {
            testUtil.shutdownMiniCluster();
        }
    }

    @Test
    public void testFlushAfterPut() throws Exception {
        TableName tableName = TableName.valueOf("test");
        testUtil.createTable(tableName, Bytes.toBytes("cf"));

        // RESTART POINT 1: after_table_create
        RestartFramework.at("after_table_create")
            .on(cluster)
            .restart("master")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        Table table = connection.getTable(tableName);
        Put put = new Put(Bytes.toBytes("row1"));
        put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("col"), Bytes.toBytes("data"));
        table.put(put);

        // RESTART POINT 2: after_put
        RestartFramework.at("after_put")
            .on(cluster)
            .restart("regionserver")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        admin.flush(tableName);

        // RESTART POINT 3: after_flush
        RestartFramework.at("after_flush")
            .on(cluster)
            .restart("regionserver")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        table.close();
        assertTrue(admin.tableExists(tableName));
    }
}
```

### Output 2: `restarts-config/restart-config.json`

```json
{
  "tests": [
    {
      "testClass": "org.apache.hadoop.hbase.regionserver.TestPutFlush_RestartInjected",
      "testMethod": "testFlushAfterPut",
      "restartPoints": [
        {
          "position": "after_table_create",
          "targets": ["master", "regionserver"],
          "modes": ["GRACEFUL", "CRASH"]
        },
        {
          "position": "after_put",
          "targets": ["regionserver"],
          "modes": ["GRACEFUL", "CRASH", "DELAYED_CRASH"]
        },
        {
          "position": "after_flush",
          "targets": ["regionserver"],
          "modes": ["GRACEFUL", "CRASH"]
        }
      ]
    }
  ]
}
```

## HBase-Specific Patterns

### Pattern 1: Table Lifecycle Testing

```java
@Test
public void testTableLifecycle() throws Exception {
    TableName tableName = TableName.valueOf("test");

    // Create
    testUtil.createTable(tableName, Bytes.toBytes("cf"));

    RestartFramework.at("after_create")
        .on(cluster)
        .restart("master")
        .execute();

    // Disable
    admin.disableTable(tableName);

    RestartFramework.at("after_disable")
        .on(cluster)
        .restart("master")
        .execute();

    // Enable
    admin.enableTable(tableName);

    RestartFramework.at("after_enable")
        .on(cluster)
        .restart("regionserver")
        .execute();

    // Delete
    admin.deleteTable(tableName);
}
```

### Pattern 2: Data Persistence Testing

```java
@Test
public void testDataPersistence() throws Exception {
    Table table = connection.getTable(tableName);

    // Write data
    Put put = new Put(Bytes.toBytes("row1"));
    put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("col"), Bytes.toBytes("value"));
    table.put(put);

    RestartFramework.at("after_put")
        .on(cluster)
        .restart("regionserver")
        .execute();

    // Flush to disk
    admin.flush(tableName);

    RestartFramework.at("after_flush")
        .on(cluster)
        .restart("regionserver")
        .execute();

    // Verify data persists after restart
    Get get = new Get(Bytes.toBytes("row1"));
    Result result = table.get(get);
    assertNotNull(result.getValue(Bytes.toBytes("cf"), Bytes.toBytes("col")));
}
```

### Pattern 3: Region Split Testing

```java
@Test
public void testRegionSplit() throws Exception {
    // Load data to trigger split
    loadData(table, 10000);

    RestartFramework.at("before_split")
        .on(cluster)
        .restart("regionserver")
        .execute();

    // Split region
    admin.split(tableName, Bytes.toBytes("row5000"));

    RestartFramework.at("after_split")
        .on(cluster)
        .restart("master")
        .execute();

    // Verify regions
    List<HRegionInfo> regions = admin.getTableRegions(tableName);
    assertTrue(regions.size() > 1);
}
```

### Pattern 4: Compaction Testing

```java
@Test
public void testMajorCompaction() throws Exception {
    // Create multiple HFiles
    for (int i = 0; i < 5; i++) {
        loadData(table, 1000);
        admin.flush(tableName);
    }

    RestartFramework.at("before_compact")
        .on(cluster)
        .restart("regionserver")
        .execute();

    // Trigger major compaction
    admin.majorCompact(tableName);

    // Wait for compaction
    Thread.sleep(5000);

    RestartFramework.at("after_compact")
        .on(cluster)
        .restart("regionserver")
        .execute();

    // Verify compaction completed
    verifyCompaction(tableName);
}
```

### Pattern 5: WAL and Durability Testing

```java
@Test
public void testWALDurability() throws Exception {
    Table table = connection.getTable(tableName);

    // Write with WAL
    Put put = new Put(Bytes.toBytes("row1"));
    put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("col"), Bytes.toBytes("value"));
    put.setDurability(Durability.SYNC_WAL);
    table.put(put);

    // Restart after WAL sync
    RestartFramework.at("after_wal_sync")
        .on(cluster)
        .restart("regionserver")
        .withMode(RestartMode.CRASH)
        .execute();

    // Verify data survived crash
    Get get = new Get(Bytes.toBytes("row1"));
    Result result = table.get(get);
    assertNotNull(result);
}
```

### Pattern 6: Snapshot Testing

```java
@Test
public void testSnapshot() throws Exception {
    String snapshotName = "test-snapshot";

    // Load data
    loadData(table, 1000);

    // Take snapshot
    admin.snapshot(snapshotName, tableName);

    RestartFramework.at("after_snapshot")
        .on(cluster)
        .restart("master")
        .execute();

    // Verify snapshot exists
    assertTrue(admin.listSnapshots().stream()
        .anyMatch(s -> s.getName().equals(snapshotName)));

    // Clone snapshot
    TableName cloneName = TableName.valueOf("clone");
    admin.cloneSnapshot(snapshotName, cloneName);

    RestartFramework.at("after_clone")
        .on(cluster)
        .restart("regionserver")
        .execute();
}
```

## Validation Checklist

After transformation, verify:

- [ ] Generated test file compiles without errors
- [ ] All original test logic is preserved
- [ ] Restart points are placed at meaningful HBase operations
- [ ] Restart position names are descriptive and HBase-specific
- [ ] Node roles (master/regionserver) are correctly chosen
- [ ] Configuration file has correct fully-qualified class names
- [ ] Configuration file includes all restart points from the test
- [ ] Target arrays match the operation type (Master vs RS vs both)
- [ ] Mode arrays are appropriate for timing sensitivity
- [ ] Files are placed in correct locations
- [ ] Original test file is not modified (only new files created)

## Advanced Scenarios

### Multiple Test Methods

If the original test has multiple `@Test` methods:

1. Transform each method independently
2. Inject restart points in each method
3. Create a separate test specification for each method in the configuration

Example configuration:
```json
{
  "tests": [
    {
      "testClass": "org.apache.hadoop.hbase.TestHBase_RestartInjected",
      "testMethod": "testPut",
      "restartPoints": [...]
    },
    {
      "testClass": "org.apache.hadoop.hbase.TestHBase_RestartInjected",
      "testMethod": "testFlush",
      "restartPoints": [...]
    }
  ]
}
```

### Helper Methods

If the test has helper methods:

1. **Do not inject restart points in helper methods**
2. Only inject in `@Test` annotated methods
3. Keep helper methods unchanged

### High Availability (HA) Configurations

For tests with multiple HMasters:

```java
// Restart active master
RestartFramework.at("after_failover")
    .on(cluster)
    .restart("master")
    .withIndex(0)  // Active master
    .execute();

// Or restart all masters
RestartFramework.at("restart_all_masters")
    .on(cluster)
    .restart("master")
    .withIndex("all")
    .execute();
```

Configuration:
```json
{
  "position": "after_failover",
  "targets": ["master"],
  "modes": ["GRACEFUL", "CRASH"]
}
```

### Tests Without Obvious Restart Points

If a test has no clear state transitions:

1. Inject restart points within the range of (a) after cluster setup and (b) before cluster teardown
2. Evenly distribute restart points to cover the test execution
3. You MUST Use percentage-based positions (e.g., `at_25_percent`, `at_50_percent`) to at least cover 4 points during the test execution
4. Find cluster operations to place restart points around

**IMPORTANT**: You are NOT allowed to skip any test transformation due to lack of restart points. Always inject at least one restart point per test method.

## Common HBase Operations and Suggested Restart Points

| HBase Operation | Suggested Restart Point Name | Node Role | Timing |
|-----------------|------------------------------|-----------|--------|
| `createTable()` | `after_table_create` | `master` | After |
| `deleteTable()` | `before_table_delete` | `master` | Before |
| `disableTable()` | `after_table_disable` | `master` | After |
| `enableTable()` | `after_table_enable` | `master` | After |
| `put()` | `after_put` | `regionserver` | After |
| `get()` | `during_get` | `regionserver` | During |
| `delete()` | `after_delete` | `regionserver` | After |
| `scan()` | `during_scan` | `regionserver` | During |
| `flush()` | `after_flush` | `regionserver` | After |
| `compact()` | `before_compact`, `after_compact` | `regionserver` | Before/After |
| `majorCompact()` | `before_major_compact`, `after_major_compact` | `regionserver` | Before/After |
| `split()` | `before_region_split`, `after_region_split` | `master`, `regionserver` | Before/After |
| `merge()` | `before_region_merge`, `after_region_merge` | `master`, `regionserver` | Before/After |
| `snapshot()` | `after_snapshot` | `master` | After |
| `cloneSnapshot()` | `after_clone_snapshot` | `master`, `regionserver` | After |
| `restoreSnapshot()` | `after_restore_snapshot` | `master`, `regionserver` | After |
| `bulkLoadHFiles()` | `after_bulk_load` | `regionserver` | After |
| `append()` | `after_append` | `regionserver` | After |
| `increment()` | `after_increment` | `regionserver` | After |
| WAL sync | `after_wal_sync` | `regionserver` | After |

## Notes

- **Non-invasive**: Original test file is never modified
- **Incremental**: Can transform tests one at a time
- **Compatible**: Generated tests can run both with and without restart injection
- **Configurable**: Configuration file allows easy adjustment of test matrix
- **HBase-aware**: Node role selection is specific to HBase architecture (Master vs RegionServer)

## Dependencies

Ensure the following dependencies are included in the test's module to use the Restart Testing Framework:

```xml
<dependencies>
    <!-- Existing dependencies... -->

    <!-- Restart Testing Framework - Core -->
    <dependency>
        <groupId>org.restarttest</groupId>
        <artifactId>restart-core</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>

    <!-- Restart Testing Framework - HBase Adapter -->
    <dependency>
        <groupId>org.restarttest</groupId>
        <artifactId>restart-hbase-adapter</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```
