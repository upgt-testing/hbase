# HBase Restart Testing Adapter

Adapter for testing HBase MiniHBaseCluster with the RestartTestingFramework.

## Overview

This adapter enables systematic restart testing of HBase clusters by providing:
- Restart capabilities for HBase Masters and RegionServers
- State capture and verification (tables, regions, data integrity)
- Health checks (master active, regionservers registered, meta table accessible)
- Support for GRACEFUL, CRASH, and DELAYED_CRASH restart modes

## Quick Start

### 1. Add Dependency

Add to your test dependencies:

```xml
<dependency>
    <groupId>org.restarttest</groupId>
    <artifactId>restart-hbase-adapter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

### 2. Add Restart Points to Your Test

```java
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

@Test
public void testTableWriteWithRestart() throws Exception {
    HBaseTestingUtility testUtil = new HBaseTestingUtility();
    MiniHBaseCluster cluster = testUtil.startMiniCluster(1, 3);

    // Create table and write data
    TableName table = TableName.valueOf("test");
    testUtil.createTable(table, Bytes.toBytes("cf"));

    // Add restart point
    RestartFramework.at("after_write")
        .on(cluster)
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.CRASH)
        .execute();

    // Verify data persisted
    // ...
}
```

### 3. Run Test With Restart Injection

```bash
# Run normally (no restart)
mvn test -Dtest=MyTest

# Run with restart injection
mvn test -Dtest=MyTest \
  -Drestart.position=after_write \
  -Drestart.target=regionserver \
  -Drestart.mode=CRASH
```

## Supported Node Roles

The HBase adapter supports the following node roles:

- `"master"` or `"hmaster"` → HMaster
- `"regionserver"` or `"worker"` → RegionServer
- `"all"` → All node types (both masters and regionservers)

## Restart Modes

- **GRACEFUL**: Clean shutdown followed by restart (calls `stopMaster`/`stopRegionServer`)
- **CRASH**: Abrupt shutdown simulating crash (calls `abortMaster`/`abortRegionServer`)
- **DELAYED_CRASH**: Crash with 500ms delay before restart (tests state propagation)

## State Capture

The adapter captures and verifies:

**Captured State**:
- Active master status
- Number of masters and regionservers
- Live/dead server counts
- Table names and enabled status
- Region counts per table
- Meta table accessibility

**Verified Invariants**:
- Active master exists after restart
- RegionServer count preserved
- No tables lost
- Meta table remains accessible
- Regions properly reassigned
- No data loss

## Health Checks

After restart, the adapter verifies:

1. **HBaseMasterActiveCheck**: Master is active and initialized
2. **HBaseRegionServersRegisteredCheck**: All regionservers registered with master
3. **HBaseMetaTableAccessibleCheck**: Meta table exists and is accessible
4. **HBaseRegionsAssignedCheck**: Regions are assigned (not stuck in transition)

## Example Tests

### Basic RegionServer Restart

```java
@Test
public void testRegionServerRestart() throws Exception {
    // Setup cluster
    HBaseTestingUtility testUtil = new HBaseTestingUtility();
    MiniHBaseCluster cluster = testUtil.startMiniCluster(1, 3);

    // Restart regionserver
    RestartFramework.at("test-restart")
        .on(cluster)
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // Verify cluster health
    assertTrue(cluster.getMaster().isActiveMaster());
}
```

### Master Failover

```java
@Test
public void testMasterFailover() throws Exception {
    // Start with 2 masters (1 active + 1 standby)
    HBaseTestingUtility testUtil = new HBaseTestingUtility();
    MiniHBaseCluster cluster = testUtil.startMiniCluster(2, 2);

    // Crash active master
    RestartFramework.at("master-failover")
        .on(cluster)
        .restart("master")
        .withIndex(0)
        .withMode(RestartMode.CRASH)
        .execute();

    // Standby should become active
    cluster.waitForActiveAndReadyMaster(60000);
    assertTrue(cluster.getMaster().isActiveMaster());
}
```

### Table Operations with Restart

```java
@Test
public void testTableWriteAndRestart() throws Exception {
    HBaseTestingUtility testUtil = new HBaseTestingUtility();
    MiniHBaseCluster cluster = testUtil.startMiniCluster(1, 2);

    TableName table = TableName.valueOf("test_table");
    testUtil.createTable(table, Bytes.toBytes("cf"));

    // Write data
    try (Table t = testUtil.getConnection().getTable(table)) {
        Put put = new Put(Bytes.toBytes("row1"));
        put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("col1"),
                     Bytes.toBytes("value1"));
        t.put(put);
    }

    // Restart regionserver
    RestartFramework.at("after-write")
        .on(cluster)
        .restart("regionserver")
        .withIndex(0)
        .withMode(RestartMode.CRASH)
        .execute();

    // Verify data persists
    try (Table t = testUtil.getConnection().getTable(table)) {
        Get get = new Get(Bytes.toBytes("row1"));
        Result result = t.get(get);
        assertFalse(result.isEmpty());
    }
}
```

## Running Tests

### Unit Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=HBaseAdapterIntegrationTest

# Run specific test method
mvn test -Dtest=HBaseAdapterIntegrationTest#testRegionServerGracefulRestart
```

### With Restart Injection

```bash
# Run with specific restart configuration
mvn test -Dtest=HBaseTableOperationsRestartTest#testTableWriteWithRegionServerRestart \
  -Drestart.position=after-write \
  -Drestart.target=regionserver \
  -Drestart.index=0 \
  -Drestart.mode=CRASH
```

## Integration with Maven Plugin

Create `restart-tests.json`:

```json
{
  "tests": [
    {
      "testClass": "org.example.HBaseTest",
      "testMethod": "testWrite",
      "restartPoints": [
        {
          "position": "after_write",
          "targets": ["regionserver", "master"],
          "modes": ["GRACEFUL", "CRASH"]
        }
      ]
    }
  ]
}
```

Run test matrix:

```bash
mvn restart-test:run
```

This generates and runs all combinations: 2 targets × 2 modes = 4 test executions.

## Requirements

- Java 8 or higher
- HBase 2.6.4-SNAPSHOT (or compatible version)
- Maven 3.x
- RestartTestingFramework 1.0.0-SNAPSHOT

## Architecture

The adapter implements the following framework interfaces:

- **ClusterAdapter<MiniHBaseCluster>**: Core adapter interface
  - `restartNode()`: Restart single node
  - `restartAllNodes()`: Restart all nodes of a role
  - `waitActive()`: Wait for cluster readiness
  - `getNodeCount()`: Get count of nodes

- **StateCapture<MiniHBaseCluster>**: State management
  - `captureState()`: Capture cluster state
  - `verifyState()`: Verify invariants

- **HealthCheck<MiniHBaseCluster>**: Health verification
  - Composite of 4 specific health checks
  - Runs after each restart

## Troubleshooting

### Tests Fail with "No active master"

**Solution**: Increase timeout for `waitForActiveAndReadyMaster`:

```java
cluster.waitForActiveAndReadyMaster(120000); // 2 minutes
```

### State Verification Fails

**Solution**: Disable state capture if testing specific scenarios:

```java
RestartFramework.at("test")
    .on(cluster)
    .restart("regionserver")
    .captureState(false)  // Disable state capture
    .execute();
```

### Health Checks Timeout

**Solution**: Disable health checks for faster testing:

```java
RestartFramework.at("test")
    .on(cluster)
    .restart("regionserver")
    .healthChecks(false)  // Disable health checks
    .execute();
```

## Contributing

To extend the adapter:

1. Add new health checks in `org.restarttest.adapter.hbase.health`
2. Enhance state capture in `HBaseStateCapture.java`
3. Add tests in `src/test/java/org/restarttest/adapter/hbase/`

## License

Apache License 2.0

## Related Documentation

- [RestartTestingFramework Documentation](https://github.com/restarttest/RestartTestingFramework)
- [HBase Documentation](https://hbase.apache.org/)
- [MiniHBaseCluster API](https://hbase.apache.org/apidocs/org/apache/hadoop/hbase/MiniHBaseCluster.html)
