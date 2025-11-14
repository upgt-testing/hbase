# Step 2: Generate HBase Upgrade Base Test Class

## AI Agent Prompt

Generate a JUnit base test class for parameterized upgrade testing with the following requirements:

### SYSTEM INFORMATION

**Cluster Type**: Distributed column-oriented database (Apache HBase)

**Cluster Class**: org.apache.hadoop.hbase.ProcessBasedMiniHBaseCluster
Full package name: org.apache.hadoop.hbase.ProcessBasedMiniHBaseCluster

**Client/Connection Class**: org.apache.hadoop.hbase.client.Connection
Full package name: org.apache.hadoop.hbase.client.Connection

**Configuration Class**: org.apache.hadoop.conf.Configuration
Full package name: org.apache.hadoop.conf.Configuration

**Package Name**: org.apache.hadoop.hbase.upgrade
Package for upgrade tests

**File Location**: hbase-server/src/test/java/org/apache/hadoop/hbase/upgrade/
Relative path from project root

### CLUSTER LIFECYCLE

**Cluster Initialization Pattern**:
```java
Configuration conf = HBaseConfiguration.create();

// System properties are read automatically - no manual checks needed!
ProcessBasedMiniHBaseCluster cluster =
    new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numRegionServers(3)
        .format(true)
        .build();  // Automatically reads hbase.start.home and hbase.upgrade.home

cluster.waitClusterUp();
```

**Client Initialization Pattern**:
```java
// Get connection from cluster (recommended)
Connection connection = cluster.getConnection();

// Or create new connection
Connection connection = ConnectionFactory.createConnection(conf);
```

**Cluster Shutdown Pattern**:
```java
cluster.shutdown();  // Shuts down all master and region server processes
```

**Client Shutdown Pattern**:
```java
connection.close();  // Close connection
```

### UPGRADE MECHANISM

**Upgrade Method**: Rolling upgrade of RegionServers

**Upgrade Invocation**:
```java
// Perform rolling upgrade
cluster.upgrade();  // Executes full rolling upgrade procedure for all RegionServers
```

**Node Identity Preservation Requirement**:
Critical requirement: Nodes MUST preserve their identity during upgrade:
- Same ports (MASTER_PORT, MASTER_INFO_PORT, REGIONSERVER_PORT, REGIONSERVER_INFO_PORT)
- Same network addresses (hostname:port combinations)
- Same ServerName objects
- Same configuration directory

Example verification:
```java
// Before upgrade
Map<Integer, ServerName> preUpgradeServerNames = new HashMap<>();
for (int i = 0; i < cluster.getNumLiveRegionServers(); i++) {
    preUpgradeServerNames.put(i, cluster.getRegionServerServerName(i));
}

// Perform upgrade
cluster.upgrade();

// After upgrade - verify identity preserved
for (int i = 0; i < cluster.getNumLiveRegionServers(); i++) {
    ServerName postUpgrade = cluster.getRegionServerServerName(i);
    ServerName preUpgrade = preUpgradeServerNames.get(i);

    assert postUpgrade.getHostname().equals(preUpgrade.getHostname()) :
        "Hostname changed during upgrade!";
    assert postUpgrade.getPort() == preUpgrade.getPort() :
        "Port changed during upgrade!";
}
```

**Pre-upgrade Health Check**:
```java
if (!cluster.isClusterUp()) {
    throw new IllegalStateException("Cluster is not healthy before upgrade");
}

// Verify all region servers are online
try (Connection conn = cluster.getConnection()) {
    Admin admin = conn.getAdmin();
    ClusterMetrics metrics = admin.getClusterMetrics();
    int expectedRSCount = cluster.getNumLiveRegionServers();
    int actualRSCount = metrics.getLiveServerMetrics().size();
    if (actualRSCount != expectedRSCount) {
        throw new IllegalStateException(
            "Expected " + expectedRSCount + " region servers, but found " + actualRSCount);
    }
}
```

**Post-upgrade Health Check**:
```java
cluster.waitClusterUp();  // Wait for cluster to stabilize after upgrade

// Verify all region servers are still online
try (Connection conn = cluster.getConnection()) {
    Admin admin = conn.getAdmin();
    ClusterMetrics metrics = admin.getClusterMetrics();
    int expectedRSCount = cluster.getNumLiveRegionServers();
    int actualRSCount = metrics.getLiveServerMetrics().size();
    if (actualRSCount != expectedRSCount) {
        throw new IllegalStateException(
            "After upgrade: Expected " + expectedRSCount + " region servers, but found " + actualRSCount);
    }
}

// CRITICAL: Verify node identities preserved
verifyNodeIdentitiesPreserved();
```

**Node Identity Verification Pattern**:
```java
// Store pre-upgrade node identities (in setupTest)
Map<Integer, ServerName> preUpgradeServerNames = new HashMap<>();

// Before upgrade, capture identities
void captureNodeIdentities() {
    preUpgradeServerNames.clear();
    try (Connection conn = cluster.getConnection()) {
        Admin admin = conn.getAdmin();
        ClusterMetrics metrics = admin.getClusterMetrics();
        int index = 0;
        for (ServerName serverName : metrics.getLiveServerMetrics().keySet()) {
            preUpgradeServerNames.put(index++, serverName);
        }
    } catch (IOException e) {
        throw new RuntimeException("Failed to capture node identities", e);
    }
}

// After upgrade, verify identities match
void verifyNodeIdentitiesPreserved() {
    try (Connection conn = cluster.getConnection()) {
        Admin admin = conn.getAdmin();
        ClusterMetrics metrics = admin.getClusterMetrics();
        int index = 0;
        for (ServerName postUpgrade : metrics.getLiveServerMetrics().keySet()) {
            ServerName preUpgrade = preUpgradeServerNames.get(index);
            if (preUpgrade == null) {
                throw new AssertionError("No pre-upgrade ServerName found for index " + index);
            }

            if (!postUpgrade.getHostname().equals(preUpgrade.getHostname())) {
                throw new AssertionError(
                    "Node " + index + " hostname changed during upgrade: " +
                    preUpgrade.getHostname() + " -> " + postUpgrade.getHostname());
            }

            if (postUpgrade.getPort() != preUpgrade.getPort()) {
                throw new AssertionError(
                    "Node " + index + " port changed during upgrade: " +
                    preUpgrade.getPort() + " -> " + postUpgrade.getPort() + ". " +
                    "This indicates node identity was not preserved!");
            }

            index++;
        }
    } catch (IOException e) {
        throw new RuntimeException("Failed to verify node identities", e);
    }
}
```

### PROCESS/RESOURCE CLEANUP

**Process Pattern to Kill**: HMaster|HRegionServer

**Process Cleanup Command**:
```bash
jps | grep -E 'HMaster|HRegionServer' | awk '{print $1}' | xargs -r kill -9
```

**Temporary Directory Pattern**: process-minihbase-*

**Directory Cleanup Logic**:
```java
File tmpDir = new File(System.getProperty("java.io.tmpdir"));
File[] oldDirs = tmpDir.listFiles((dir, name) ->
    name.startsWith("process-minihbase-") &&
    name.matches(".*\\d{13}$"));  // Match timestamp suffix

if (oldDirs != null) {
    long oneHourAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
    for (File dir : oldDirs) {
        // Only delete directories older than 1 hour
        if (dir.lastModified() < oneHourAgo) {
            try {
                FileUtils.deleteDirectory(dir);
                LOG.info("Deleted old cluster directory: " + dir);
            } catch (IOException e) {
                LOG.warn("Failed to delete old cluster directory: " + dir, e);
            }
        }
    }
}
```

### CHECKPOINT CONFIGURATION

**Checkpoint Constants Class**: HBaseUpgradeCheckpoints

**Common Checkpoint Names**: NO_UPGRADE, AFTER_CLUSTER_START, AFTER_CREATE_TABLE, AFTER_WRITE, AFTER_READ, AFTER_FLUSH, AFTER_COMPACT, AFTER_CLOSE

**Checkpoint No-Upgrade Constant**: NO_UPGRADE

### ADDITIONAL REQUIREMENTS

**Additional Managed Resources**:
- Admin (org.apache.hadoop.hbase.client.Admin) - Administrative client for DDL operations
- Table (org.apache.hadoop.hbase.client.Table) - Open table handles for data operations
- ResultScanner (org.apache.hadoop.hbase.client.ResultScanner) - Open scanner handles
- BufferedMutator (org.apache.hadoop.hbase.client.BufferedMutator) - Buffered mutation handles

**Additional Cleanup Steps**:
1. Close all open ResultScanner handles
2. Close all open Table handles
3. Close all open BufferedMutator handles
4. Close Admin client
5. Close main Connection
6. Shutdown cluster

**Special Considerations**:
- HBase requires ZooKeeper (managed by HBaseTestingUtility/MiniZKCluster)
- Must wait for hbase:meta region to be online before cluster is considered ready
- Region balancer may need time to stabilize after upgrades
- Some tests may need to disable balancer during certain operations
- Table close() must happen before Connection close()

### PLATFORM COMPATIBILITY

**Operating Systems**: Linux, macOS

**Process Management Approach**:
Use jps (Java Virtual Machine Process Status Tool) and kill -9 on Unix platforms.
For platform independence, also support process.destroyForcibly() where applicable.

### GENERATED CLASS STRUCTURE

Please generate a base test class with the following structure:

1. **Class Header**:
   - Apache License header
   - Package declaration: org.apache.hadoop.hbase.upgrade
   - Comprehensive JavaDoc explaining:
     - Purpose of the base class
     - Usage pattern with @RunWith(Parameterized.class)
     - Example test implementation
     - Test isolation guarantees
     - Cleanup guarantees
     - Node identity preservation verification

2. **Protected Fields**:
   - upgradeCheckpoint (String) - Parameter from subclass
   - cluster (ProcessBasedMiniHBaseCluster) - Cluster instance
   - connection (Connection) - Client instance
   - conf (Configuration) - Configuration instance
   - admin (Admin) - Admin client (may be null)
   - preUpgradeServerNames (Map<Integer, ServerName>) - For identity verification

3. **@Before setupTest() Method**:
   - Sync @Parameter field from subclass to base class (use reflection)
   - Log setup start with checkpoint name
   - Clean up orphaned processes from previous failed runs (jps | grep | kill)
   - Clean up old cluster directories (older than 1 hour)
   - Initialize fresh configuration (HBaseConfiguration.create())
   - Set cluster = null, connection = null, admin = null (defensive)
   - Clear preUpgradeServerNames map
   - Log setup completion

4. **@After tearDownTest() Method**:
   - Log teardown start with checkpoint name
   - Close admin (with try-catch, null check, finally block)
   - Close connection (with try-catch, null check, finally block)
   - Shutdown cluster with cleanup (with try-catch, null check, finally block)
   - Wait for processes to terminate (Thread.sleep with 3000ms timeout)
   - Verify cleanup success (no orphaned HMaster/HRegionServer processes)
   - Force cleanup if verification fails (jps | grep | kill -9)
   - Log teardown completion
   - **CRITICAL**: Use independent try-catch blocks for each cleanup step to ensure all cleanup runs even if one step fails

5. **checkpoint(String name) Method**:
   - Check if upgrade should happen at this checkpoint (call shouldUpgrade(name))
   - If no upgrade needed, return immediately
   - Log checkpoint name
   - Verify cluster health before upgrade (cluster.isClusterUp(), verify RS count)
   - **Capture node identities before upgrade**: captureNodeIdentities()
   - Perform upgrade: cluster.upgrade()
   - Verify cluster health after upgrade (cluster.waitClusterUp(), verify RS count)
   - **Verify node identities preserved**: verifyNodeIdentitiesPreserved()
   - Log any identity violations as ERRORS
   - Log upgrade completion
   - **JavaDoc should warn**: "IMPORTANT: Close all Table, Scanner, and BufferedMutator resources before calling checkpoint() to avoid broken connections during node restarts"

6. **shouldUpgrade(String name) Method**:
   - Return false if upgradeCheckpoint is null
   - Return false if upgradeCheckpoint equals HBaseUpgradeCheckpoints.NO_UPGRADE
   - Return true if upgradeCheckpoint.equals(name)
   - Return false otherwise

7. **Private Helper Methods**:
   - syncUpgradeCheckpointFromSubclass(): Use Java reflection to find @Parameter field named "upgradeCheckpoint" in subclass, copy value to base class field
   - cleanupOrphanedProcesses(): Execute `jps | grep -E 'HMaster|HRegionServer' | awk '{print $1}' | xargs -r kill -9`
   - cleanupOldClusterDirectories(): Find and delete process-minihbase-* directories older than 1 hour
   - deleteDirectory(File): Recursive directory deletion utility using FileUtils.deleteDirectory()
   - verifyCleanup(): Execute jps and verify no processes match HMaster|HRegionServer pattern

8. **captureNodeIdentities() Method**:
   - Clear preUpgradeServerNames map
   - Query cluster metrics via Admin.getClusterMetrics()
   - Store each RegionServer's ServerName with index
   - Log captured identities

9. **verifyNodeIdentitiesPreserved() Method**:
   - Query cluster metrics via Admin.getClusterMetrics()
   - Compare each current ServerName with pre-upgrade snapshot
   - Verify hostname and port match exactly
   - Throw AssertionError if any node identity changed
   - Log verification results (success or failure with details)

10. **Best Practices**:
   - Use SLF4J Logger for all logging (LOG.info, LOG.warn, LOG.error)
   - Each cleanup step in @After must be in independent try-catch block
   - Set fields to null in finally blocks after cleanup
   - Log all major steps (setup, cleanup, upgrade, verification, identity checks)
   - Defensive cleanup in @Before (kill orphaned processes from failed previous runs)
   - Comprehensive JavaDoc with usage examples
   - Automatic System property handling for hbase.start.home and hbase.upgrade.home
   - Platform-aware process cleanup (Unix: jps + kill -9)
   - Node identity preservation verification (verify ServerName hostname + port unchanged)
   - Pre-upgrade identity snapshot (store ServerName before upgrade)
   - Post-upgrade identity validation (compare with snapshot)

### OUTPUT FORMAT

Generate:
1. Complete Java source file with Apache license header
2. Necessary import statements (minimize unused imports):
   ```java
   import org.apache.hadoop.conf.Configuration;
   import org.apache.hadoop.hbase.ClusterMetrics;
   import org.apache.hadoop.hbase.HBaseConfiguration;
   import org.apache.hadoop.hbase.ProcessBasedMiniHBaseCluster;
   import org.apache.hadoop.hbase.ServerName;
   import org.apache.hadoop.hbase.client.Admin;
   import org.apache.hadoop.hbase.client.Connection;
   import org.apache.commons.io.FileUtils;
   import org.junit.After;
   import org.junit.Before;
   import org.junit.runners.Parameterized.Parameter;
   import org.slf4j.Logger;
   import org.slf4j.LoggerFactory;
   import java.io.File;
   import java.io.IOException;
   import java.lang.reflect.Field;
   import java.util.HashMap;
   import java.util.Map;
   import java.util.concurrent.TimeUnit;
   ```
3. All methods with comprehensive JavaDoc comments
4. Inline comments for complex logic (especially reflection, process cleanup)
5. Proper exception handling and logging
6. Example usage in class-level JavaDoc showing:
   - How to extend this base class
   - How to define @Parameters method
   - How to use checkpoint() in tests
   - How node identity preservation works

### EXAMPLE USAGE (in class JavaDoc)

```java
/**
 * Base test class for parameterized upgrade testing of ProcessBasedMiniHBaseCluster.
 *
 * <p>This class provides automatic lifecycle management, checkpoint-based upgrade testing,
 * and complete test isolation. Each test execution is fully isolated with guaranteed
 * cleanup between runs.
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @RunWith(Parameterized.class)
 * public class TestMyFeature extends ProcessBasedUpgradeTestBase {
 *
 *   @Parameter
 *   public String upgradeCheckpoint;
 *
 *   @Parameters(name = "upgrade-at={0}")
 *   public static Collection<String> checkpoints() {
 *     return Arrays.asList(
 *       HBaseUpgradeCheckpoints.NO_UPGRADE,
 *       HBaseUpgradeCheckpoints.AFTER_CLUSTER_START,
 *       "AFTER_CREATE_TABLE",
 *       "AFTER_WRITE_DATA",
 *       "AFTER_FLUSH"
 *     );
 *   }
 *
 *   @Test
 *   public void testFeature() throws Exception {
 *     cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
 *         .numRegionServers(3)
 *         .build();
 *     connection = cluster.getConnection();
 *     admin = connection.getAdmin();
 *
 *     checkpoint(HBaseUpgradeCheckpoints.AFTER_CLUSTER_START);
 *
 *     // Create table
 *     TableDescriptor td = TableDescriptorBuilder.newBuilder(TableName.valueOf("test"))
 *         .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf"))
 *         .build();
 *     admin.createTable(td);
 *     checkpoint("AFTER_CREATE_TABLE");
 *
 *     // Write data
 *     try (Table table = connection.getTable(TableName.valueOf("test"))) {
 *       Put put = new Put(Bytes.toBytes("row1"));
 *       put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("q"), Bytes.toBytes("value"));
 *       table.put(put);
 *     }
 *     checkpoint("AFTER_WRITE_DATA");
 *
 *     // Flush (must close table before checkpoint!)
 *     admin.flush(TableName.valueOf("test"));
 *     checkpoint("AFTER_FLUSH");
 *
 *     // No try-finally needed - @After handles cleanup!
 *   }
 * }
 * }</pre>
 *
 * <h3>Node Identity Preservation:</h3>
 * <p>During upgrades, this base class automatically verifies that each RegionServer
 * maintains its identity (hostname + port). If a node's identity changes, the test
 * will fail with a clear error message indicating which node changed and how.
 *
 * <h3>Guarantees:</h3>
 * <ul>
 *   <li>Complete isolation between checkpoint executions</li>
 *   <li>Automatic cleanup of processes and directories</li>
 *   <li>Verification of cleanup success</li>
 *   <li>Force cleanup if verification fails</li>
 *   <li>Node identity preservation during upgrades</li>
 *   <li>Automatic ServerName tracking before/after upgrades</li>
 * </ul>
 */
```

The generated class should be production-ready and follow Java best practices.

---

**End of AI Agent Prompt**

This prompt provides complete specifications for generating ProcessBasedUpgradeTestBase for HBase.
