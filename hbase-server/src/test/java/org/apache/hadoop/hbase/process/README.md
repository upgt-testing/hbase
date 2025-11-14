# ProcessBasedMiniHBaseCluster

A process-based HBase cluster implementation for testing version compatibility and upgrade scenarios.

## Overview

`ProcessBasedMiniHBaseCluster` runs each HBase node (Master and RegionServer) in a separate JVM process, enabling:

- **Multi-version testing**: Different nodes can run different HBase versions
- **Upgrade scenario testing**: Simulate rolling upgrades and version compatibility
- **Process isolation**: Each node has its own classpath and dependencies
- **Node identity preservation**: Nodes maintain the same identity (ports, addresses) across restarts

## Key Differences from MiniHBaseCluster

| Feature | MiniHBaseCluster | ProcessBasedMiniHBaseCluster |
|---------|-----------------|------------------------------|
| Node execution | Threads in same JVM | Separate JVM processes |
| Version support | Single version only | Multiple versions per cluster |
| Direct object access | Supported | Not supported (RPC only) |
| Node identity | Changes on restart | Preserved across restarts |
| Use case | Unit tests | Version compatibility & upgrade tests |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Test JVM Process                         │
│  ┌───────────────────────────────────────────────────┐     │
│  │     ProcessBasedMiniHBaseCluster                  │     │
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
       │ HBase 2.6.0    │    │ HBase 3.0.0    │
       └────────────────┘    └────────────────┘
```

## Components

### Core Classes

- **ProcessBasedMiniHBaseCluster**: Main cluster coordinator
- **ProcessNodeManager**: Base class for managing node processes
- **MasterProcessManager**: Manages HMaster processes
- **RegionServerProcessManager**: Manages HRegionServer processes

### Supporting Classes

- **HBaseDistribution**: Represents an HBase installation
- **HBaseVersionRegistry**: Manages multiple HBase versions
- **PortAllocator**: Allocates and persists port assignments
- **ProcessConfigurationGenerator**: Generates node configurations
- **DirectoryManager**: Manages work directories
- **MasterProcessLauncher**: Entry point for Master subprocess
- **RegionServerProcessLauncher**: Entry point for RegionServer subprocess

## Usage Examples

### Basic Single-Version Cluster

```java
Configuration conf = HBaseConfiguration.create();

ProcessBasedMiniHBaseCluster cluster =
    new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numMasters(1)
        .numRegionServers(3)
        .allNodesHBaseDistribution("/opt/hbase-2.6.0")
        .build();

cluster.startup();

try (Connection conn = cluster.getConnection()) {
    Admin admin = conn.getAdmin();

    // Create table
    TableDescriptor td = TableDescriptorBuilder
        .newBuilder(TableName.valueOf("test"))
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf"))
        .build();
    admin.createTable(td);

    // Use cluster...
} finally {
    cluster.shutdown();
}
```

### Mixed-Version Cluster

```java
ProcessBasedMiniHBaseCluster cluster =
    new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numMasters(1)
        .numRegionServers(3)
        .masterHBaseDistribution("/opt/hbase-2.6.0")
        .regionServerHBaseDistribution(0, "/opt/hbase-3.0.0")
        .regionServerHBaseDistribution(1, "/opt/hbase-3.0.0")
        .regionServerHBaseDistribution(2, "/opt/hbase-2.6.0")
        .build();

cluster.startup();
// Test version compatibility...
cluster.shutdown();
```

### Rolling Upgrade Simulation

```java
// Start with all nodes running HBase 2.6.0
ProcessBasedMiniHBaseCluster cluster =
    new ProcessBasedMiniHBaseCluster.Builder(conf)
        .numMasters(1)
        .numRegionServers(3)
        .allNodesHBaseDistribution("/opt/hbase-2.6.0")
        .build();

cluster.startup();

// Upgrade each RegionServer one by one
for (int i = 0; i < 3; i++) {
    ServerName rs = getRegionServerName(i);

    // Stop old version
    cluster.stopRegionServer(rs);
    cluster.waitForRegionServerToStop(rs, 30000);

    // Change version (this preserves node identity)
    cluster.changeRegionServerVersion(i, "/opt/hbase-3.0.0");

    // Start new version
    cluster.startRegionServer(i);
    cluster.waitClusterUp();

    // Verify data still accessible
    verifyData(cluster.getConnection());
}

cluster.shutdown();
```

## Requirements

### Runtime Requirements

1. **Built HBase distributions**: You must provide complete HBase installations
   - Example: `/opt/hbase-2.6.0/`, `/opt/hbase-3.0.0/`
   - Each must be a full build with all JARs in `lib/` directory

2. **Java 8+**: Same JDK for all versions recommended
   - JDK 11 recommended for HBase 3.x

3. **Resources**: Each process requires ~512MB heap
   - Typical cluster (1 master + 3 RSs) needs ~2GB total

4. **ZooKeeper**: External ZooKeeper cluster or MiniZKCluster

### Setting up HBase Distributions

```bash
# Build HBase 2.6.0
cd /path/to/hbase-2.6.0
mvn clean package -DskipTests
# Distribution will be in: hbase-assembly/target/hbase-2.6.0/

# Build HBase 3.0.0
cd /path/to/hbase-3.0.0
mvn clean package -DskipTests
# Distribution will be in: hbase-assembly/target/hbase-3.0.0/
```

## Supported Operations

### Cluster Management
- `startup()` - Start all processes
- `shutdown()` - Stop all processes
- `waitClusterUp()` - Wait for cluster ready
- `waitForActiveAndReadyMaster(timeout)` - Wait for master

### Node Management
- `stopMaster(serverName)` - Graceful master shutdown
- `killMaster(serverName)` - Force kill master
- `stopRegionServer(serverName)` - Graceful RS shutdown
- `killRegionServer(serverName)` - Force kill RS
- `waitForMasterToStop(serverName, timeout)` - Wait for master stop
- `waitForRegionServerToStop(serverName, timeout)` - Wait for RS stop

### Client Operations
- `getConnection()` - Get HBase connection
- `getConfiguration()` - Get cluster configuration
- `getClusterMetrics()` - Get cluster metrics (via RPC)
- `getServerHoldingRegion(...)` - Locate region server (via meta)

## Unsupported Operations

These methods throw `UnsupportedOperationException`:

- `getMaster()` - Direct HMaster object access
- `getRegionServer(int)` - Direct HRegionServer object access
- `getMasterThreads()` - Thread access
- `getRegionServerThreads()` - Thread access
- `getRegions(TableName)` - Requires direct RS access

**Reason**: Nodes run in separate processes, so direct object access is not possible. Use client-side APIs (Connection, Admin, Table) instead.

## Directory Structure

The cluster creates a temporary directory structure:

```
/tmp/process-minihbase-{timestamp}/
├── master0/
│   ├── ports.properties          # Persisted port allocations
│   ├── conf/
│   │   └── hbase-site.xml       # Node-specific config
│   ├── data/
│   │   └── hbase/               # Data files
│   ├── logs/
│   │   └── hbase-master.log     # Process logs
│   └── pid                      # Process ID
├── rs0/
│   ├── ports.properties
│   ├── conf/
│   ├── data/
│   └── logs/
├── rs1/
└── rs2/
```

## Node Identity Preservation

**Critical Feature**: Nodes maintain the same identity across restarts/upgrades.

### Why It Matters

During rolling upgrades, if a node's identity changes:
- ❌ Other nodes see it as a NEW node joining (not a restart)
- ❌ Original node appears as "dead" or "decommissioned"
- ❌ Cluster triggers unnecessary region rebalancing
- ❌ Upgrade tests fail

### How It Works

1. **Port Persistence**: Ports are allocated once and saved to `ports.properties`
2. **Port Reuse**: On restart, the same ports are loaded and reused
3. **Identity Preservation**: Same address + port = same ServerName
4. **Upgrade Support**: Version change doesn't affect node identity

### Example

```java
// Initial startup
cluster.startup();
ServerName rs0 = getRegionServerName(0); // localhost:50003

// Upgrade RS 0
cluster.stopRegionServer(rs0);
cluster.changeVersion(0, "/opt/hbase-3.0.0");
cluster.startRegionServer(0);

ServerName rs0New = getRegionServerName(0); // localhost:50003 (SAME!)

// Other nodes see: "RS 0 restarted" ✓
// NOT: "RS 0 died, new RS joined" ✗
```

## Testing

### Unit Tests

Test individual components:
- `TestPortAllocator` - Port allocation and persistence
- `TestProcessConfigurationGenerator` - Config generation
- `TestHBaseDistribution` - Distribution discovery
- `TestDirectoryManager` - Directory management

### Integration Tests

Test full cluster:
- `TestProcessBasedMiniHBaseClusterBasic` - Basic operations
- `TestProcessBasedMiniHBaseClusterUpgrade` - Rolling upgrades
- `TestMixedVersionCluster` - Version compatibility

### Running Tests

```bash
# Set HBASE_HOME to a built distribution
export HBASE_HOME=/opt/hbase-2.6.0

# Run tests
mvn test -Dtest=TestProcessBasedMiniHBaseClusterBasic
```

## Troubleshooting

### Process Won't Start

1. **Check HBase distribution**: Ensure all JARs are present
   ```bash
   ls $HBASE_HOME/lib/hbase-*.jar
   ```

2. **Check ports**: Ensure ports are available
   ```bash
   netstat -an | grep 50000
   ```

3. **Check logs**: Look in `{workDir}/logs/`

### Connection Timeouts

1. **Wait longer**: Cluster startup can take 1-2 minutes
2. **Check ZooKeeper**: Ensure ZK is running
3. **Check firewall**: Ensure ports are accessible

### Version Compatibility Issues

1. **Check compatibility matrix**: Not all versions are compatible
2. **Check dependencies**: Different versions may have conflicting dependencies
3. **Check configuration**: Some configs may be version-specific

## Limitations

### Current Limitations

- **Same JDK**: All processes must use the same JDK
- **Same OS**: No cross-platform support
- **Local only**: All processes on same machine
- **No hot-swap**: Version changes require restart

### Future Work

- Docker-based isolation for better dependency management
- Cross-JDK version support
- Distributed process management
- Hot-swap version changes without restart

## Contributing

See [HBASE-XXXXX](https://issues.apache.org/jira/browse/HBASE-XXXXX) for the JIRA tracking this feature.

## License

Licensed under the Apache License, Version 2.0.
