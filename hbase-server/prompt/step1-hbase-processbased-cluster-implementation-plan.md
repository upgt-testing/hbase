# Step 1: ProcessBasedMiniHBaseCluster Implementation Plan

## Executive Summary

This document outlines the plan to create `ProcessBasedMiniHBaseCluster` that extends the existing `MiniHBaseCluster` functionality but runs each node in separate JVM processes. This enables testing version compatibility and upgrade scenarios by allowing different nodes to run different versions.

**Project Information:**
- **Project Name**: Apache HBase
- **Original Cluster Class**: `MiniHBaseCluster`
- **New Cluster Class**: `ProcessBasedMiniHBaseCluster`
- **Project Root**: `hbase-server`

**Node Types in HBase Cluster:**
- **Master** - Class: `HMaster` - Role: Cluster coordinator, manages regions, handles DDL operations
- **RegionServer** - Class: `HRegionServer` - Role: Data storage node, serves regions, handles data I/O
- *(ZooKeeper is managed separately via existing MiniZKCluster)*

---

## Goals

1. **Process Isolation**: Each node runs in its own JVM process
2. **Version Flexibility**: Support running different HBase versions for different nodes
3. **API Compatibility**: Extend existing `MiniHBaseCluster` API where possible
4. **Client-Side Only**: Support only client-side operations (RPC/HTTP based)
5. **Testing Focus**: Enable version upgrade and compatibility testing
6. **Node Identity Persistence**: Nodes maintain same identity (address, port, configuration) across restarts and upgrades

## Non-Goals (Initial Phase)

- Performance optimization - correctness over speed
- Hot-swap/runtime version changes - versions set at cluster creation
- Cross-version internal state compatibility testing (may be future work)
- Components outside core HBase (Phoenix, external coprocessors, etc.)

---

## Architecture Overview

### Current `MiniHBaseCluster` Architecture

```
┌─────────────────────────────────────────┐
│          Test JVM Process               │
│  ┌─────────────────────────────────┐   │
│  │      MiniHBaseCluster           │   │
│  │  ┌──────────┐  ┌──────────┐    │   │
│  │  │ HMaster  │  │HRegionSrv│    │   │
│  │  │ (thread) │  │ (thread) │    │   │
│  │  └──────────┘  └──────────┘    │   │
│  │                                 │   │
│  │  Direct method calls possible   │   │
│  │  LocalHBaseCluster manages all │   │
│  └─────────────────────────────────┘   │
│  Shared classpath & version            │
└─────────────────────────────────────────┘
```

### New `ProcessBasedMiniHBaseCluster` Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Test JVM Process                         │
│  ┌───────────────────────────────────────────────────┐     │
│  │     ProcessBasedMiniHBaseCluster                  │     │
│  │                                                     │     │
│  │  ┌────────────────┐  ┌────────────────┐          │     │
│  │  │ Master Process │  │  RS Process    │          │     │
│  │  │ Mgr (Control)  │  │ Mgr (Control)  │          │     │
│  │  └────────┬───────┘  └────────┬───────┘          │     │
│  │           │ RPC                 │ RPC             │     │
│  └───────────┼─────────────────────┼─────────────────┘     │
│              │                     │                        │
└──────────────┼─────────────────────┼────────────────────────┘
               │                     │
       ┌───────▼────────┐    ┌──────▼─────────┐
       │  HMaster       │    │  HRegionServer │
       │  Process       │    │  Process       │
       │                │    │                │
       │ HBase 2.6.0    │    │ HBase 3.0.0    │
       │ (Isolated CP)  │    │ (Isolated CP)  │
       └────────────────┘    └────────────────┘
```

**Key Components:**

1. **ProcessBasedMiniHBaseCluster**: Main cluster coordinator (in test JVM)
2. **Process Managers**: Start/stop/monitor individual node processes
3. **Node Launchers**: Entry points for node processes
4. **RPC Clients**: Communicate with nodes via HBase RPC protocols
5. **Configuration Manager**: Generate and distribute configs per node
6. **Classpath Isolator**: Ensure each process uses correct HBase version
7. **Node Identity Manager**: Ensures nodes maintain consistent identity across restarts
   - Persists port allocations
   - Preserves node configurations
   - Maintains address consistency

---

## Detailed Design

### 1. Class Structure

#### 1.1 Main Classes

```
ProcessBasedMiniHBaseCluster (extends MiniHBaseCluster)
├── ProcessNodeManager
│   ├── MasterProcessManager
│   └── RegionServerProcessManager
├── HBaseVersionRegistry
├── ProcessConfigurationGenerator
└── ProcessLauncher
    ├── MasterProcessLauncher (Main class for subprocess)
    └── RegionServerProcessLauncher (Main class for subprocess)
```

#### 1.2 ProcessBasedMiniHBaseCluster

**Responsibilities:**
- HIGHEST PRIORITY: Follow and transform `MiniHBaseCluster` API to support/un-support methods as needed
- Follow the same creation/startup/shutdown patterns as `MiniHBaseCluster`
- Manage lifecycle of all node processes
- Provide client-side API access only. If not possible, then those methods should tag as unsupported and throw `UnsupportedOperationException`.
- Throw UnsupportedOperationException for direct object access methods

**New Builder Options** (extending existing builder):
```java
Builder hbaseDistribution(int nodeIndex, String hbaseHome)
Builder allNodesHBaseDistribution(String hbaseHome)
Builder masterHBaseDistribution(String hbaseHome)
Builder regionServerHBaseDistribution(int index, String hbaseHome)
Builder enableProcessIsolation(boolean enable) // default: true
```

**Unsupported Methods** (will throw UnsupportedOperationException):
```java
// Direct object access methods
HMaster getMaster()                           // returns HMaster object
HMaster getMaster(int serverNumber)           // returns HMaster object
HRegionServer getRegionServer(int serverNumber) // returns HRegionServer object
List<RegionServerThread> getRegionServerThreads() // returns server threads
List<MasterThread> getMasterThreads()         // returns master threads
HRegionServer getRegionServer(ServerName serverName) // direct object access
```

**Supported Methods:**
```java
// Client-side operations
Connection getConnection()                    // returns Connection object
Configuration getConfiguration()              // returns cluster configuration
ClusterMetrics getClusterMetrics()            // via RPC
ServerName getServerHoldingRegion(...)        // via meta lookup
boolean waitForActiveAndReadyMaster(timeout)  // waits via RPC health checks
void shutdown()                               // stops all processes
JVMClusterUtil.MasterThread startMaster()     // starts master process
JVMClusterUtil.RegionServerThread startRegionServer() // starts RS process
void killMaster(ServerName serverName)        // kills process
void killRegionServer(ServerName serverName)  // kills process
void stopMaster(ServerName serverName)        // graceful shutdown
void stopRegionServer(ServerName serverName)  // graceful shutdown
void waitForMasterToStop(ServerName, timeout) // process monitoring
void waitForRegionServerToStop(ServerName, timeout) // process monitoring
int getNumLiveRegionServers()                 // via cluster metrics
```

#### 1.3 ProcessNodeManager

**Base class for managing node processes:**

```java
abstract class ProcessNodeManager {
    protected Process process;
    protected Configuration nodeConfig;
    protected String hbaseHome;
    protected File workDir;
    protected int nodeIndex;
    protected Map<String, Integer> ports; // RPC, INFO, etc.

    abstract void start() throws IOException;
    abstract void stop() throws IOException;
    abstract boolean isHealthy() throws IOException;
    abstract ServerName getServerName();

    protected void waitForProcessReady(long timeoutMs);
    protected void killProcess();
    protected List<String> buildClasspath();
    protected void persistPorts();  // Save port allocations
    protected void loadPorts();     // Restore port allocations
}
```

**MasterProcessManager:**
- Starts HMaster process with isolated classpath
- Monitors health via RPC (getMasterRpcServices())
- Handles master-specific configuration
- Manages ports: MASTER_PORT, MASTER_INFO_PORT

**RegionServerProcessManager:**
- Starts HRegionServer process with isolated classpath
- Monitors health via RPC (getRSRpcServices())
- Handles region server-specific configuration
- Manages ports: REGIONSERVER_PORT, REGIONSERVER_INFO_PORT

#### 1.4 HBaseVersionRegistry

**Manages HBase distribution locations:**

```java
class HBaseVersionRegistry {
    private Map<String, HBaseDistribution> distributions;

    void register(String version, String hbaseHome);
    HBaseDistribution get(String version);
    List<File> getJars(String version);
    List<File> getDependencies(String version);
}

class HBaseDistribution {
    String version;
    File hbaseHome;
    List<File> coreJars;      // hbase-server, hbase-client, hbase-common
    List<File> dependencies;   // all lib/*.jar
}
```

**Classpath Construction Strategy:**
```
For each node process:
1. JVM bootstrap classpath (Java runtime)
2. Test framework jars (JUnit, shared test utilities)
3. Node-specific jars from hbaseHome/lib/
4. Node-specific dependencies from hbaseHome/lib/client-facing-thirdparty/
5. Configuration directory
```

**Dependency Hell Mitigation:**
- Each process gets completely isolated classpath
- No shared classes between processes (except JVM and test framework)
- Communication only via RPC/sockets
- Use separate temp directories for each node
- Version-specific native libraries via java.library.path

#### 1.5 ProcessConfigurationGenerator

**Generates configuration files for each node:**

```java
class ProcessConfigurationGenerator {
    Configuration generateMasterConfig(
        Configuration baseConfig,
        int masterIndex,
        File workDir);

    Configuration generateRegionServerConfig(
        Configuration baseConfig,
        int rsIndex,
        File workDir,
        List<ServerName> masterAddresses);

    File writeConfigToFile(Configuration conf, File dir);
}
```

**Configuration File Strategy:**
- Only set necessary properties that are programmatically set by tests rather than write full config
- Each node gets a unique configuration directory
- Write hbase-site.xml config files
- Auto-assign ports (scan for free ports)
- Set up isolated data directories
- Include version-specific configuration adjustments

**Key Configuration Properties:**
- `hbase.master.port` - Master RPC port (persisted)
- `hbase.master.info.port` - Master web UI port (persisted)
- `hbase.regionserver.port` - RegionServer RPC port (persisted)
- `hbase.regionserver.info.port` - RegionServer web UI port (persisted)
- `hbase.rootdir` - HDFS root directory
- `hbase.cluster.distributed` - true for process-based
- `hbase.zookeeper.quorum` - ZK connection string

#### 1.6 ProcessLauncher (Entry Points)

**MasterProcessLauncher** (runs in subprocess):
```java
package org.apache.hadoop.hbase.master;

public class MasterProcessLauncher {
    public static void main(String[] args) {
        // Parse args: --config-dir, --master-index
        CommandLineParser parser = new CommandLineParser();
        String configDir = parser.getOptionValue("config-dir");
        int masterIndex = Integer.parseInt(parser.getOptionValue("master-index", "0"));

        // Load configuration
        Configuration conf = HBaseConfiguration.create();
        conf.addResource(new Path(configDir, "hbase-site.xml"));

        // Start HMaster
        HMaster master = new HMaster(conf);
        master.start();

        // Set up signal handlers
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            master.stop("Shutdown hook");
        }));

        // Wait/run indefinitely
        master.join();
    }
}
```

**RegionServerProcessLauncher** (runs in subprocess):
```java
package org.apache.hadoop.hbase.regionserver;

public class RegionServerProcessLauncher {
    public static void main(String[] args) {
        // Parse args: --config-dir, --rs-index
        CommandLineParser parser = new CommandLineParser();
        String configDir = parser.getOptionValue("config-dir");
        int rsIndex = Integer.parseInt(parser.getOptionValue("rs-index", "0"));

        // Load configuration
        Configuration conf = HBaseConfiguration.create();
        conf.addResource(new Path(configDir, "hbase-site.xml"));

        // Start HRegionServer
        HRegionServer rs = new HRegionServer(conf);
        rs.start();

        // Set up signal handlers
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            rs.stop("Shutdown hook");
        }));

        // Wait/run indefinitely
        rs.join();
    }
}
```

**Process Launch Command Example:**
```bash
java \
  -cp /opt/hbase-2.6.0/lib/*:/opt/hbase-2.6.0/lib/client-facing-thirdparty/* \
  -Djava.library.path=/opt/hbase-2.6.0/lib/native \
  org.apache.hadoop.hbase.regionserver.RegionServerProcessLauncher \
  --config-dir /tmp/minicluster/rs0/conf \
  --rs-index 0
```

---

### 2. Process Management

#### 2.1 Process Startup Sequence

**CRITICAL REQUIREMENT: Node Identity Persistence**

When restarting or upgrading a node, it MUST preserve its identity so that:
- Other nodes recognize it as the SAME node restarting (not a new node joining)
- Cluster topology remains stable
- No unnecessary rebalancing or region movement occurs

This requires:
- **Port Persistence**: Use the same ports after restart/upgrade
- **Address Consistency**: Bind to the same network addresses
- **Configuration Preservation**: Maintain node-specific settings
- **ServerName Preservation**: Keep internal server identifiers consistent

1. **Validate distributions**
   - Check hbaseHome exists
   - Verify required jars present (hbase-server-*.jar, hbase-common-*.jar)
   - Validate versions if specified

2. **Generate configurations** (First start only)
   - Create work directories
   - Generate hbase-site.xml files
   - Allocate ports and PERSIST to disk

2b. **Restore configurations** (Restart/upgrade)
   - Load persisted port allocations
   - Restore node-specific configurations
   - Verify address bindings available

3. **Start Master**
   - Build classpath from hbaseHome
   - Start process with MasterProcessLauncher
   - Wait for RPC server to be ready
   - Perform health check (getMasterRpcServices())

4. **Start RegionServers**
   - For each RS: build classpath, start process
   - Wait for registration with master
   - Perform health check (getRSRpcServices())

5. **Wait for cluster ready**
   - All region servers registered with master
   - Cluster out of initialization state (isInitialized())
   - hbase:meta region is online
   - Can perform basic operations (Admin.listTables())

#### 2.2 Process Health Monitoring

**Health Check Mechanisms:**
- **Process-level**: Check process.isAlive()
- **RPC-level**: Periodic health check RPC calls
- **Functional**: Basic operations (create table, put/get data)

**Implementation:**
```java
class HealthMonitor {
    boolean checkMasterHealth(ServerName serverName) {
        try (Connection conn = ConnectionFactory.createConnection(conf)) {
            Admin admin = conn.getAdmin();
            admin.getClusterMetrics();  // Master RPC call
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    boolean checkRegionServerHealth(ServerName serverName) {
        try (Connection conn = ConnectionFactory.createConnection(conf)) {
            Admin admin = conn.getAdmin();
            ClusterMetrics metrics = admin.getClusterMetrics();
            return metrics.getLiveServerMetrics().containsKey(serverName);
        } catch (Exception e) {
            return false;
        }
    }
}
```

#### 2.3 Process Shutdown Sequence

1. **Graceful shutdown** (first attempt):
   - Send shutdown command via RPC if possible
   - For Master: call getMasterRpcServices().stopMaster()
   - For RS: call getRSRpcServices().stopServer()
   - Wait for process exit (with timeout)

2. **Force shutdown** (if graceful fails):
   - Send SIGTERM to process
   - Wait for exit (with timeout)

3. **Kill** (last resort):
   - Send SIGKILL (process.destroyForcibly())
   - Clean up resources

4. **Cleanup**:
   - Delete temp directories (optional)
   - Close RPC clients
   - Release ports

---

### 3. Dependency Management

#### 3.1 Classpath Isolation Strategy

**Challenge**: Different HBase versions have overlapping dependencies but potentially different versions (e.g., Guava, Protobuf, Netty, Log4j).

**Solution: Complete Process Isolation**
- Each node process has fully isolated classpath
- No class sharing except JVM and coordination mechanism
- Communication only via RPC/sockets

**Classpath Construction:**
```java
List<File> buildClasspathForNode(HBaseDistribution dist) {
    List<File> classpath = new ArrayList<>();

    // 1. Process launcher classes (minimal, from test classpath)
    classpath.add(findProcessLauncherJar());

    // 2. Core HBase libraries
    classpath.addAll(findJars(dist.hbaseHome, "lib/hbase-server-*.jar"));
    classpath.addAll(findJars(dist.hbaseHome, "lib/hbase-common-*.jar"));
    classpath.addAll(findJars(dist.hbaseHome, "lib/hbase-protocol-*.jar"));
    classpath.addAll(findJars(dist.hbaseHome, "lib/hbase-client-*.jar"));

    // 3. Dependencies
    classpath.addAll(findJars(dist.hbaseHome, "lib/*.jar"));
    classpath.addAll(findJars(dist.hbaseHome, "lib/client-facing-thirdparty/*.jar"));

    // 4. Native library path
    nativeLibPath = new File(dist.hbaseHome, "lib/native");

    return classpath;
}
```

#### 3.2 Dependency Conflicts

**Known Issues**:
- **Guava**: HBase 2.x uses Guava 27, HBase 3.x uses Guava 31
- **Protobuf**: Version incompatibilities between 2.x and 3.x
- **Netty**: Different versions for RPC
- **Log4j**: Log4j 1.x (HBase 2.x) vs Log4j 2.x (HBase 3.x)
- **Hadoop**: Different Hadoop versions may be bundled

**Mitigation:**
- Process isolation prevents conflicts
- Each process loads its own dependency versions
- No shared classloader between test JVM and node JVMs

**Testing:**
- Verify different dependency versions work in different nodes
- Test known version mismatch scenarios (HBase 2.6 + HBase 3.0)

---

### 4. Configuration Management

#### 4.1 Port Allocation and Persistence

**Strategy:**
- Use port range allocation (e.g., 50000-59999)
- Scan for free ports before assignment
- Track assigned ports to avoid conflicts
- **CRITICAL: Persist port allocations to disk for restarts/upgrades**

**Why Port Persistence is Critical:**

When a node restarts or upgrades, it MUST use the same ports. If ports change:
- ❌ Other nodes think a NEW node joined the cluster
- ❌ Original node appears as "dead" or "decommissioned"
- ❌ Cluster may trigger unnecessary region rebalancing
- ❌ Upgrade tests fail due to topology changes

**Per-Node Ports:**
- **Master**:
  - `hbase.master.port` (RPC port, e.g., 50001)
  - `hbase.master.info.port` (Web UI, e.g., 50002)
- **RegionServer**:
  - `hbase.regionserver.port` (RPC port, e.g., 50003)
  - `hbase.regionserver.info.port` (Web UI, e.g., 50004)

**Implementation Pattern:**

```java
class PortAllocator {
    private Set<Integer> usedPorts;
    private int nextPort = 50000;
    private File persistenceFile;  // NEW: Port persistence

    /**
     * Allocate port for first-time node startup.
     * Port is persisted to disk for future restarts.
     */
    int allocatePort(String nodeId, String portType) {
        // Check if port already allocated for this node
        Integer existingPort = loadPersistedPort(nodeId, portType);
        if (existingPort != null && isPortAvailable(existingPort)) {
            usedPorts.add(existingPort);
            return existingPort;
        }

        // Allocate new port
        while (usedPorts.contains(nextPort) || !isPortAvailable(nextPort)) {
            nextPort++;
        }
        usedPorts.add(nextPort);

        // CRITICAL: Persist port allocation
        persistPort(nodeId, portType, nextPort);

        return nextPort++;
    }

    /**
     * Load persisted port for node restart/upgrade.
     * Returns null if this is first startup.
     */
    Integer loadPersistedPort(String nodeId, String portType) {
        File portFile = new File(workDir, nodeId + "/ports.properties");
        if (!portFile.exists()) {
            return null;
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(portFile)) {
            props.load(fis);
            String portStr = props.getProperty(portType + ".port");
            return portStr != null ? Integer.parseInt(portStr) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Persist port allocation to survive restarts/upgrades.
     */
    void persistPort(String nodeId, String portType, int port) {
        File portFile = new File(workDir, nodeId + "/ports.properties");
        portFile.getParentFile().mkdirs();
        Properties props = new Properties();

        // Load existing ports
        if (portFile.exists()) {
            try (FileInputStream fis = new FileInputStream(portFile)) {
                props.load(fis);
            } catch (IOException e) {
                // Ignore, will create new
            }
        }

        // Add/update this port
        props.setProperty(portType + ".port", String.valueOf(port));

        try (FileOutputStream fos = new FileOutputStream(portFile)) {
            props.store(fos, "Port allocation for " + nodeId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist port", e);
        }
    }

    /**
     * Verify port is still available for reuse.
     * If not available, this is a critical error - cannot change ports!
     */
    boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
```

**Port Persistence File Structure:**

```
/tmp/process-minihbase-<timestamp>/
├── master0/
│   ├── ports.properties          # NEW: Persisted port allocations
│   │   # master.port=50001
│   │   # master-info.port=50002
│   ├── conf/
│   │   └── hbase-site.xml
│   └── data/
├── rs0/
│   ├── ports.properties          # NEW: Persisted port allocations
│   │   # regionserver.port=50003
│   │   # regionserver-info.port=50004
│   ├── conf/
│   └── data/
```

**Restart/Upgrade Flow:**

1. **Shutdown node** - Process stops, but port files remain
2. **Load persisted ports** - Read ports.properties
3. **Verify ports available** - Critical: ports MUST be free
4. **Start with same ports** - Node appears as "same node restarting"
5. **Other nodes detect** - "RegionServer X is back online" (not "New RS joined")

**Error Handling:**

If persisted port is not available during restart:
```java
if (!isPortAvailable(persistedPort)) {
    throw new RuntimeException(
        "Cannot restart node " + nodeId + ": " +
        "Port " + persistedPort + " is in use. " +
        "Node identity cannot be preserved. " +
        "Other nodes will see this as a new node, not a restart.");
}
```

#### 4.2 Directory Structure

```
/tmp/process-minihbase-<timestamp>/
├── master0/
│   ├── ports.properties          # Persisted port allocations
│   ├── node-identity.properties  # ServerName and metadata
│   ├── conf/
│   │   ├── hbase-site.xml
│   │   └── log4j.properties
│   ├── data/
│   │   └── hbase/
│   ├── logs/
│   │   └── hbase-master.log
│   └── pid
├── rs0/
│   ├── ports.properties
│   ├── node-identity.properties
│   ├── conf/
│   │   ├── hbase-site.xml
│   │   └── log4j.properties
│   ├── data/
│   │   └── hbase/
│   ├── logs/
│   │   └── hbase-regionserver.log
│   └── pid
├── rs1/
└── cluster.properties (cluster-wide config)
```

#### 4.3 Configuration File Generation

**Base Configuration Template (hbase-site.xml):**
```xml
<configuration>
  <!-- Master Configuration -->
  <property>
    <name>hbase.master.port</name>
    <value>${allocated_master_port}</value>
  </property>
  <property>
    <name>hbase.master.info.port</name>
    <value>${allocated_master_info_port}</value>
  </property>

  <!-- RegionServer Configuration -->
  <property>
    <name>hbase.regionserver.port</name>
    <value>${allocated_rs_port}</value>
  </property>
  <property>
    <name>hbase.regionserver.info.port</name>
    <value>${allocated_rs_info_port}</value>
  </property>

  <!-- Data Directory -->
  <property>
    <name>hbase.rootdir</name>
    <value>file://${work_dir}/data/hbase</value>
  </property>

  <!-- ZooKeeper -->
  <property>
    <name>hbase.zookeeper.quorum</name>
    <value>localhost:${zk_port}</value>
  </property>

  <!-- Cluster Mode -->
  <property>
    <name>hbase.cluster.distributed</name>
    <value>true</value>
  </property>
</configuration>
```

**Version-Specific Adjustments:**
- HBase 2.x vs 3.x config key differences (if any)
- Deprecated property mappings
- Version-specific feature flags

---

### 5. API Design

#### 5.1 Builder API

```java
ProcessBasedMiniHBaseCluster cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
    .numRegionServers(3)
    .masterHBaseDistribution("/opt/hbase-2.6.0")
    .regionServerHBaseDistribution(0, "/opt/hbase-3.0.0")
    .regionServerHBaseDistribution(1, "/opt/hbase-3.0.0")
    .regionServerHBaseDistribution(2, "/opt/hbase-2.6.0")
    .format(true)
    .build();
```

#### 5.2 Supported Operations

**Cluster Management:**
```java
// Startup/shutdown
cluster.startup();
cluster.shutdown();
cluster.waitClusterUp();

// Process management (with identity preservation)
cluster.startMaster();              // Preserves ports, address, config
cluster.startRegionServer();        // Preserves ports, address, config
cluster.stopMaster(serverName);
cluster.stopRegionServer(serverName);
cluster.killMaster(serverName);
cluster.killRegionServer(serverName);

// Status
cluster.isClusterUp();
cluster.getNumLiveRegionServers();
cluster.waitForActiveAndReadyMaster(timeout);

// Verify identity preservation
ServerName rs0Before = cluster.getRegionServer(0).getServerName();
cluster.stopRegionServer(rs0Before);
cluster.startRegionServer();
ServerName rs0After = cluster.getRegionServer(0).getServerName();
assert rs0Before.equals(rs0After) : "RS identity changed after restart!";
```

**Client Operations:**
```java
// Client access
Connection conn = cluster.getConnection();
Admin admin = conn.getAdmin();

// Client configuration
Configuration clientConf = cluster.getConfiguration();
```

#### 5.3 Unsupported Operations

Methods that require direct object access will throw:
```java
throw new UnsupportedOperationException(
    "Direct object access not supported in ProcessBasedMiniHBaseCluster. " +
    "This cluster runs nodes in separate processes. " +
    "Use client-side APIs instead.");
```

**List of Unsupported Methods**:
- `HMaster getMaster()` - returns HMaster object
- `HMaster getMaster(int serverNumber)` - returns HMaster object
- `HRegionServer getRegionServer(int serverNumber)` - returns HRegionServer object
- `HRegionServer getRegionServer(ServerName serverName)` - returns HRegionServer object
- `List<RegionServerThread> getRegionServerThreads()` - returns server thread list
- `List<MasterThread> getMasterThreads()` - returns master thread list
- `List<HRegion> getRegions(TableName)` - requires direct RS access
- Any other method returning server-side objects

---

### 6. Version Upgrade Testing Support

#### 6.1 Upgrade Scenarios

**CRITICAL: Identity Preservation During Upgrades**

During rolling upgrades, each node MUST maintain its identity:

✅ **CORRECT Upgrade Flow:**
1. Stop RegionServer(0) - Preserves ports.properties and configuration
2. Change version for RegionServer(0) - Update software version
3. Start RegionServer(0) - **Uses SAME ports, SAME address**
4. Other nodes detect: "RS 0 restarted with new version" ✓

❌ **INCORRECT Upgrade Flow:**
1. Stop RegionServer(0)
2. Start RegionServer(0) with NEW ports/address
3. Other nodes detect: "RS 0 died, new RS joined" ✗
4. Cluster rebalances regions unnecessarily ✗
5. Upgrade test fails ✗

**Implementation Requirements:**
- `changeRegionServerVersion()` updates software path only
- Port allocations remain unchanged
- Configuration files preserve node identity
- Work directory persists across version change

**Supported Test Scenarios:**

1. **Rolling Upgrade - RegionServers**
   ```java
   // Start with HBase 2.6.0
   cluster.allNodesHBaseDistribution("/opt/hbase-2.6.0");
   cluster.build();

   // Upgrade each RS to HBase 3.0.0
   for (int i = 0; i < 3; i++) {
       ServerName rs = cluster.getRegionServer(i).getServerName();
       cluster.stopRegionServer(rs);
       cluster.changeRegionServerVersion(i, "/opt/hbase-3.0.0");
       cluster.startRegionServer();
       cluster.waitClusterUp();
   }
   ```

2. **Master Upgrade** (if HA setup)
   ```java
   // Upgrade standby master to 3.0.0
   // Perform failover
   // Upgrade old active master
   ```

3. **Mixed Version Cluster**
   ```java
   // Different versions on different nodes
   // Test compatibility matrix
   cluster.masterHBaseDistribution("/opt/hbase-2.6.0");
   cluster.regionServerHBaseDistribution(0, "/opt/hbase-3.0.0");
   cluster.regionServerHBaseDistribution(1, "/opt/hbase-3.0.0");
   cluster.regionServerHBaseDistribution(2, "/opt/hbase-2.6.0");
   ```

#### 6.2 Test Helpers

```java
class UpgradeTestHelper {
    void performRollingUpgrade(
        ProcessBasedMiniHBaseCluster cluster,
        String fromVersion,
        String toVersion);

    void verifyVersionCompatibility(
        String masterVersion,
        String regionServerVersion);

    void assertBasicOperationsWork(Connection conn);
}
```

---

## Implementation Plan

### Phase 1: Core Infrastructure (Weeks 1-2)

#### Task 1.1: Process Management Foundation
**Priority**: P0
**Estimated Effort**: 3-4 days

**Subtasks:**
- [ ] Create `ProcessNodeManager` base class
  - [ ] Implement start(), stop(), isAlive() methods
  - [ ] Add process monitoring thread
  - [ ] Handle process crashes and restarts
- [ ] Create `MasterProcessManager`
  - [ ] Implement master-specific startup
  - [ ] Add RPC health checks (getMasterRpcServices())
- [ ] Create `RegionServerProcessManager`
  - [ ] Implement RS-specific startup
  - [ ] Add health monitoring (getRSRpcServices())
- [ ] Create `ProcessLauncher` base class
  - [ ] Command-line argument parsing
  - [ ] Configuration loading
  - [ ] Logging setup

**Testing:**
- Unit test: Start/stop single Master process
- Unit test: Start/stop single RegionServer process
- Unit test: Process crash detection

**Files to Create:**
```
hbase-server/src/test/java/org/apache/hadoop/hbase/process/
├── ProcessNodeManager.java
├── MasterProcessManager.java
├── RegionServerProcessManager.java
└── launcher/
    ├── ProcessLauncher.java
    ├── MasterProcessLauncher.java
    └── RegionServerProcessLauncher.java
```

#### Task 1.2: Classpath & Version Management
**Priority**: P0
**Estimated Effort**: 3-4 days

**Subtasks:**
- [ ] Create `HBaseDistribution` class
  - [ ] Parse HBase installation directory
  - [ ] Discover JAR files (hbase-server, hbase-common, etc.)
  - [ ] Build classpath string
- [ ] Create `HBaseVersionRegistry`
  - [ ] Register multiple HBase distributions
  - [ ] Validate distribution completeness
- [ ] Implement classpath builder
  - [ ] Collect core JARs (hbase-server, hbase-common)
  - [ ] Collect dependencies from lib/ and lib/client-facing-thirdparty/
  - [ ] Handle native libraries
- [ ] Test dependency isolation

**Testing:**
- Unit test: Parse HBase distribution
- Unit test: Build classpath for different versions
- Integration test: Start node with HBase 2.6.0
- Integration test: Start node with HBase 3.0.0

**Files to Create:**
```
hbase-server/src/test/java/org/apache/hadoop/hbase/process/
├── HBaseDistribution.java
├── HBaseVersionRegistry.java
└── ClasspathBuilder.java
```

#### Task 1.3: Configuration Management
**Priority**: P0
**Estimated Effort**: 2-3 days

**Subtasks:**
- [ ] Create `ProcessConfigurationGenerator`
  - [ ] Generate per-node hbase-site.xml files
  - [ ] Write config files to disk
- [ ] Create `PortAllocator`
  - [ ] Scan for available ports
  - [ ] Track allocated ports
  - [ ] Handle allocation failures
  - [ ] **Persist port allocations to disk**
  - [ ] **Load persisted ports on restart**
  - [ ] **Validate port availability on restart**
- [ ] Create `DirectoryManager`
  - [ ] Set up temp directory structure
  - [ ] Create per-node subdirectories
  - [ ] Implement cleanup on shutdown

**Testing:**
- Unit test: Generate Master configuration
- Unit test: Generate RegionServer configuration
- Unit test: Port allocation and persistence
- Unit test: Directory structure creation

**Files to Create:**
```
hbase-server/src/test/java/org/apache/hadoop/hbase/process/
├── ProcessConfigurationGenerator.java
├── PortAllocator.java
└── DirectoryManager.java
```

### Phase 2: ProcessBasedMiniHBaseCluster Implementation (Weeks 3-4)

#### Task 2.1: Main Cluster Class
**Priority**: P0
**Estimated Effort**: 4-5 days

**Subtasks:**
- [ ] Create `ProcessBasedMiniHBaseCluster` class
  - [ ] Extend MiniHBaseCluster
  - [ ] Implement Builder pattern
  - [ ] Add hbaseDistribution() builder methods
- [ ] Implement cluster startup sequence
  - [ ] Validate distributions
  - [ ] Generate configurations
  - [ ] Start nodes in correct order (master first, then RSs)
  - [ ] Wait for cluster ready
- [ ] Implement cluster shutdown sequence
  - [ ] Graceful shutdown
  - [ ] Force shutdown
  - [ ] Cleanup resources
- [ ] Implement supported methods
  - [ ] getConnection()
  - [ ] getConfiguration()
  - [ ] waitClusterUp()
  - [ ] restart methods
- [ ] Throw UnsupportedOperationException for direct access methods

**Testing:**
- Integration test: Start single-node cluster
- Integration test: Start multi-node cluster
- Integration test: Restart nodes
- Integration test: Verify unsupported methods throw exceptions

**Files to Create:**
```
hbase-server/src/test/java/org/apache/hadoop/hbase/process/
├── ProcessBasedMiniHBaseCluster.java
└── integration/
    ├── TestProcessBasedMiniHBaseCluster.java
    └── TestProcessBasedMiniHBaseClusterAPI.java
```

#### Task 2.2: Health Monitoring & Retry Logic
**Priority**: P1
**Estimated Effort**: 2-3 days

**Subtasks:**
- [ ] Create `HealthMonitor` class
  - [ ] Check process alive status
  - [ ] Verify RPC connectivity
  - [ ] Functional health checks (listTables, etc.)
- [ ] Implement waitForNodeReady()
  - [ ] Wait for RPC server
  - [ ] Retry with exponential backoff
  - [ ] Timeout handling
- [ ] Implement cluster readiness checks
  - [ ] All nodes healthy
  - [ ] Out of initialization state
  - [ ] Can perform basic operations

**Testing:**
- Unit test: Health check for healthy node
- Unit test: Health check for dead node
- Integration test: Wait for cluster ready
- Integration test: Handle node startup failures

**Files to Create:**
```
hbase-server/src/test/java/org/apache/hadoop/hbase/process/
└── HealthMonitor.java
```

### Phase 3: Multi-Version Support (Week 5)

#### Task 3.1: Version-Specific Configuration
**Priority**: P1
**Estimated Effort**: 2-3 days

**Subtasks:**
- [ ] Create `VersionConfigAdapter`
  - [ ] Map config keys between versions (if needed)
  - [ ] Handle deprecated properties
  - [ ] Version-specific defaults
- [ ] Test version compatibility
  - [ ] HBase 2.6.x <-> HBase 3.0.x property mapping
  - [ ] Handle removed/renamed properties
- [ ] Test minor version compatibility

**Testing:**
- Unit test: Config key mapping
- Integration test: Mixed-version cluster (2.6 + 3.0)

**Files to Create:**
```
hbase-server/src/test/java/org/apache/hadoop/hbase/process/
└── VersionConfigAdapter.java
```

#### Task 3.2: Mixed-Version Cluster Testing
**Priority**: P1
**Estimated Effort**: 2-3 days

**Subtasks:**
- [ ] Create test matrix of version combinations
  - [ ] Document supported combinations
  - [ ] Test known compatible versions
  - [ ] Identify incompatible combinations
- [ ] Implement version upgrade test helpers
  - [ ] Rolling upgrade helper
  - [ ] Compatibility verification
- [ ] Create example upgrade tests

**Testing:**
- Integration test: All nodes different versions
- Integration test: Rolling upgrade (2.6.0 → 3.0.0)
- Integration test: Version incompatibility detection

**Files to Create:**
```
hbase-server/src/test/java/org/apache/hadoop/hbase/process/upgrade/
├── TestMixedVersionCluster.java
├── TestRollingUpgrade.java
└── UpgradeTestHelper.java
```

### Phase 4: Testing & Documentation (Week 6)

#### Task 4.1: Comprehensive Test Suite
**Priority**: P0
**Estimated Effort**: 3-4 days

**Subtasks:**
- [ ] Unit tests for all components
- [ ] Integration tests
- [ ] Stress tests
- [ ] Compatibility tests

**Test Categories:**
```
hbase-server/src/test/java/org/apache/hadoop/hbase/process/
├── unit/
│   ├── TestProcessNodeManager.java
│   ├── TestHBaseVersionRegistry.java
│   ├── TestConfigurationGenerator.java
│   └── TestPortAllocator.java
├── integration/
│   ├── TestProcessBasedMiniHBaseClusterBasics.java
│   ├── TestProcessBasedMiniHBaseClusterFailover.java
│   ├── TestProcessBasedMiniHBaseClusterUpgrade.java
│   └── TestMixedVersionOperations.java
└── compatibility/
    └── TestAPICompatibility.java
```

#### Task 4.2: Documentation
**Priority**: P1
**Estimated Effort**: 2 days

**Subtasks:**
- [ ] Create user guide
- [ ] Create developer guide
- [ ] Update project documentation
- [ ] Create JavaDoc

**Documentation Files:**
```
hbase-server/docs/
├── ProcessBasedMiniHBaseCluster-UserGuide.md
├── ProcessBasedMiniHBaseCluster-DeveloperGuide.md
└── VersionUpgradeTestingGuide.md
```

---

## Testing Strategy

### Unit Testing

**Coverage Goals**: >80% line coverage for all new classes

**Test Categories:**

1. **Process Management**
   - Start/stop processes
   - Process crash detection
   - Graceful vs force shutdown
   - PID tracking

2. **Configuration**
   - Config file generation (hbase-site.xml)
   - Port allocation and persistence
   - Directory management
   - Version-specific config

3. **Classpath**
   - JAR discovery
   - Classpath construction
   - Dependency isolation

4. **Health Monitoring**
   - RPC connectivity checks
   - Process health checks
   - Retry logic

### Integration Testing

**Test Environments:**
- Single node cluster (1 master, 1 RS)
- Multi-node cluster (1 master, 3 RSs)
- HA cluster (2 masters, 3 RSs)
- Mixed-version cluster

**Test Scenarios:**

1. **Basic Operations**
   ```java
   @Test
   public void testBasicOperations() throws Exception {
       cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
           .numRegionServers(3)
           .allNodesHBaseDistribution("/opt/hbase-2.6.0")
           .build();

       Connection conn = cluster.getConnection();
       Admin admin = conn.getAdmin();

       // Create table
       TableDescriptor td = TableDescriptorBuilder
           .newBuilder(TableName.valueOf("test"))
           .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf"))
           .build();
       admin.createTable(td);

       // Put data
       try (Table table = conn.getTable(TableName.valueOf("test"))) {
           Put put = new Put(Bytes.toBytes("row1"));
           put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("q"), Bytes.toBytes("value"));
           table.put(put);

           // Get data
           Get get = new Get(Bytes.toBytes("row1"));
           Result result = table.get(get);
           assertNotNull(result.getValue(Bytes.toBytes("cf"), Bytes.toBytes("q")));
       }
   }
   ```

2. **Node Restart**
   ```java
   @Test
   public void testNodeRestart() throws Exception {
       ServerName rs0 = cluster.getRegionServer(0).getServerName();
       cluster.stopRegionServer(rs0);
       cluster.startRegionServer();
       cluster.waitClusterUp();

       // Verify cluster still functional
       try (Connection conn = cluster.getConnection()) {
           Admin admin = conn.getAdmin();
           assertTrue(admin.getClusterMetrics().getLiveServerMetrics().size() == 3);
       }
   }
   ```

3. **Mixed Versions**
   ```java
   @Test
   public void testMixedVersionCluster() throws Exception {
       cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
           .numRegionServers(3)
           .masterHBaseDistribution("/opt/hbase-2.6.0")
           .regionServerHBaseDistribution(0, "/opt/hbase-3.0.0")
           .regionServerHBaseDistribution(1, "/opt/hbase-3.0.0")
           .regionServerHBaseDistribution(2, "/opt/hbase-2.6.0")
           .build();

       // Verify all nodes work together
       Connection conn = cluster.getConnection();
       Admin admin = conn.getAdmin();
       assertEquals(3, admin.getClusterMetrics().getLiveServerMetrics().size());
   }
   ```

4. **Rolling Upgrade**
   ```java
   @Test
   public void testRollingUpgrade() throws Exception {
       // Start cluster with HBase 2.6.0
       cluster = new ProcessBasedMiniHBaseCluster.Builder(conf)
           .numRegionServers(3)
           .allNodesHBaseDistribution("/opt/hbase-2.6.0")
           .build();

       // Write test data
       try (Connection conn = cluster.getConnection();
            Table table = conn.getTable(TableName.valueOf("test"))) {
           // ... write data
       }

       // Upgrade each RS one by one
       for (int i = 0; i < 3; i++) {
           ServerName rs = cluster.getRegionServer(i).getServerName();
           cluster.stopRegionServer(rs);
           cluster.changeRegionServerVersion(i, "/opt/hbase-3.0.0");
           cluster.startRegionServer();
           cluster.waitClusterUp();

           // Verify data still accessible
           try (Connection conn = cluster.getConnection();
                Table table = conn.getTable(TableName.valueOf("test"))) {
               // ... read and verify data
           }
       }
   }
   ```

---

## Dependencies & Prerequisites

### Runtime Dependencies

1. **Multiple HBase Distributions**
   - User must provide built HBase installations
   - Suggested setup: `/opt/hbase-2.6.0/`, `/opt/hbase-3.0.0/`, etc.
   - Each installation must be complete (jars + dependencies)

2. **JDK 8+**
   - Same JDK for all versions recommended
   - JDK 11 for HBase 3.x

3. **Sufficient Resources**
   - Each process ~512MB heap
   - For typical cluster: ~2GB total (1 master + 3 RSs)

4. **Operating System**
   - Linux: Fully supported
   - macOS: Should work (test needed)
   - Windows: May have issues (lower priority)

### Build Dependencies

- Maven 3.3+
- JUnit 4 for parameterized tests
- Mockito for unit tests
- HBase dependencies (already in hbase-server)

---

## Risk Assessment & Mitigation

### High Risk Items

#### Risk 1: Dependency Hell
**Description**: Different HBase versions have conflicting dependencies (Guava, Protobuf, Netty)

**Impact**: High - Could prevent different versions from running together

**Probability**: Medium

**Mitigation**:
- Complete process isolation (separate JVMs)
- No shared classpath except JVM itself
- Extensive testing of known problematic dependencies

**Contingency**:
- If isolation fails, use Docker containers instead
- Fall back to version ranges (only test compatible versions)

#### Risk 2: Configuration Incompatibility
**Description**: Config keys/values differ between HBase 2.x and 3.x

**Impact**: Medium - Nodes won't start with wrong config

**Probability**: Low (HBase maintains good backward compatibility)

**Mitigation**:
- Version-aware config generation
- Test with actual distributions
- Document known incompatibilities

**Contingency**:
- Provide manual config override mechanism
- Version-specific config templates

#### Risk 3: Protocol Incompatibility
**Description**: RPC protocols differ between versions

**Impact**: High - Nodes can't communicate

**Probability**: Low (HBase has protocol compatibility guarantees)

**Mitigation**:
- Test with known compatible versions first
- Document minimum compatible versions
- Check HBase compatibility matrix

**Contingency**:
- Limit support to compatible version ranges
- Provide clear error messages

---

## Success Criteria

### Phase 1 Success Criteria
- [ ] Can start single Master process with specific version
- [ ] Can start single RegionServer process with specific version
- [ ] Process monitoring and health checks work
- [ ] Proper cleanup on shutdown

### Phase 2 Success Criteria
- [ ] Can start full cluster (1 master, 3 RSs)
- [ ] Can perform basic operations via Connection API
- [ ] Can restart nodes without cluster restart
- [ ] Unsupported methods throw clear exceptions

### Phase 3 Success Criteria
- [ ] Can run different nodes with different versions
- [ ] Can perform rolling upgrade (2.6.0 → 3.0.0)
- [ ] Version compatibility matrix documented and tested

### Final Success Criteria
- [ ] All unit tests pass (>80% coverage)
- [ ] All integration tests pass
- [ ] At least 5 version combination tests pass
- [ ] Documentation complete
- [ ] Zero known critical bugs
- [ ] Can run at least one rolling upgrade scenario end-to-end

---

## Timeline

**Total Estimated Duration**: 6 weeks

| Phase | Duration | End Date |
|-------|----------|----------|
| Phase 1: Core Infrastructure | 2 weeks | Week 2 |
| Phase 2: Main Implementation | 2 weeks | Week 4 |
| Phase 3: Multi-Version Support | 1 week | Week 5 |
| Phase 4: Testing & Documentation | 1 week | Week 6 |

**Milestones:**
- Week 2: First process started successfully
- Week 4: First full cluster test passes
- Week 5: First mixed-version test passes
- Week 6: Ready for code review

---

## Example Usage

```java
// Basic usage - all nodes same version
ProcessBasedMiniHBaseCluster cluster =
    new ProcessBasedMiniHBaseCluster.Builder(HBaseConfiguration.create())
        .numRegionServers(3)
        .allNodesHBaseDistribution("/opt/hbase-2.6.0")
        .format(true)
        .build();

Connection conn = cluster.getConnection();
Admin admin = conn.getAdmin();
// Use admin for testing...
cluster.shutdown();

// Mixed version usage
ProcessBasedMiniHBaseCluster mixedCluster =
    new ProcessBasedMiniHBaseCluster.Builder(HBaseConfiguration.create())
        .numRegionServers(3)
        .masterHBaseDistribution("/opt/hbase-2.6.0")
        .regionServerHBaseDistribution(0, "/opt/hbase-3.0.0")
        .regionServerHBaseDistribution(1, "/opt/hbase-3.0.0")
        .regionServerHBaseDistribution(2, "/opt/hbase-2.6.0")
        .format(true)
        .build();

// Upgrade scenario
ServerName rs0 = cluster.getRegionServer(0).getServerName();
cluster.stopRegionServer(rs0);
cluster.changeRegionServerVersion(0, "/opt/hbase-3.0.0");
cluster.startRegionServer();
cluster.waitClusterUp();
```

---

**End of Step 1 Implementation Plan**

This plan provides a comprehensive roadmap for implementing ProcessBasedMiniHBaseCluster for Apache HBase. All placeholders have been filled with HBase-specific information, and the plan is ready for implementation.
