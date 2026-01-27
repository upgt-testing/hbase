# TEST-BUG-GROUP-15: MiniHBaseCluster Missing Null Check for ServerName Lookup

## JIRA-Style Bug Report

### Summary
MiniHBaseCluster methods that accept ServerName throw confusing ArrayIndexOutOfBoundsException when the server is not found, instead of a meaningful exception.

### Component
hbase-server (test utilities)

### Affects Version
2.6.3

### Priority
Minor (test infrastructure improvement)

### Issue Type
Improvement

---

## Description

Multiple methods in `MiniHBaseCluster` use `getRegionServerIndex(ServerName)` to find a region server by its ServerName, but do not check if the returned index is -1 (server not found). This results in a confusing `ArrayIndexOutOfBoundsException: -1` instead of a meaningful error message.

### Current Behavior

When a ServerName is not found in the cluster (e.g., after a server restart when the old ServerName becomes stale), the following call chain occurs:

```java
// MiniHBaseCluster.java:270
HRegionServer server = getRegionServer(getRegionServerIndex(serverName));
```

1. `getRegionServerIndex(serverName)` returns -1 (server not found)
2. `getRegionServer(-1)` is called
3. `LocalHBaseCluster.getRegionServer(-1)` throws `ArrayIndexOutOfBoundsException: -1`

### Stack Trace
```
java.lang.ArrayIndexOutOfBoundsException: -1
    at java.util.concurrent.CopyOnWriteArrayList.get(CopyOnWriteArrayList.java:388)
    at java.util.concurrent.CopyOnWriteArrayList.get(CopyOnWriteArrayList.java:397)
    at org.apache.hadoop.hbase.LocalHBaseCluster.getRegionServer(LocalHBaseCluster.java:245)
    at org.apache.hadoop.hbase.MiniHBaseCluster.getRegionServer(MiniHBaseCluster.java:814)
    at org.apache.hadoop.hbase.MiniHBaseCluster.killRegionServer(MiniHBaseCluster.java:270)
    ...
```

### Expected Behavior

The methods should check if the server was found and throw a clear, descriptive exception:

```
java.lang.IllegalArgumentException: RegionServer not found: hostname,16020,1234567890
```

---

## Affected Methods

All methods in `MiniHBaseCluster.java` that use `getRegionServerIndex()` without checking for -1:

| Line | Method | Current Code |
|------|--------|--------------|
| 270 | `killRegionServer(ServerName)` | `getRegionServer(getRegionServerIndex(serverName))` |
| 275 | `killRegionServer(ServerName)` | `abortRegionServer(getRegionServerIndex(serverName))` |
| 286 | `stopRegionServer(ServerName)` | `stopRegionServer(getRegionServerIndex(serverName))` |
| 291 | `suspendRegionServer(ServerName)` | `suspendRegionServer(getRegionServerIndex(serverName))` |
| 296 | `resumeRegionServer(ServerName)` | `resumeRegionServer(getRegionServerIndex(serverName))` |
| 302 | `waitForRegionServerToStop(ServerName, long)` | `waitOnRegionServer(getRegionServerIndex(serverName))` |
| 970 | `getAdminProtocol(ServerName)` | `getRegionServer(getRegionServerIndex(serverName))` |
| 976 | `getClientProtocol(ServerName)` | `getRegionServer(getRegionServerIndex(serverName))` |

Similarly, `getMasterIndex(ServerName)` at line 958-966 returns -1 but callers may not check for it.

---

## Proposed Fix

### Option 1: Add Validation in Each Method (Simple but Repetitive)

```java
@Override
public void killRegionServer(ServerName serverName) throws IOException {
  int index = getRegionServerIndex(serverName);
  if (index < 0) {
    throw new IllegalArgumentException(
        "RegionServer not found in cluster: " + serverName +
        ". Available servers: " + getRegionServerThreads().stream()
            .map(t -> t.getRegionServer().getServerName().toString())
            .collect(Collectors.joining(", ")));
  }
  HRegionServer server = getRegionServer(index);
  if (server instanceof MiniHBaseClusterRegionServer) {
    LOG.info("Killing " + server.toString());
    ((MiniHBaseClusterRegionServer) server).kill();
  } else {
    abortRegionServer(index);
  }
}
```

### Option 2: Create a Validated Helper Method (Recommended)

Add a new helper method that validates and returns the index:

```java
/**
 * Get the index of a region server by ServerName.
 * @param serverName the ServerName to look up
 * @return the index of the region server
 * @throws IllegalArgumentException if the server is not found
 */
private int getRegionServerIndexOrThrow(ServerName serverName) {
  int index = getRegionServerIndex(serverName);
  if (index < 0) {
    List<String> available = getRegionServerThreads().stream()
        .map(t -> t.getRegionServer().getServerName().toString())
        .collect(Collectors.toList());
    throw new IllegalArgumentException(
        "RegionServer not found: " + serverName +
        ". Available RegionServers (" + available.size() + "): " + available);
  }
  return index;
}

/**
 * Get the index of a master by ServerName.
 * @param serverName the ServerName to look up
 * @return the index of the master
 * @throws IllegalArgumentException if the master is not found
 */
private int getMasterIndexOrThrow(ServerName serverName) {
  int index = getMasterIndex(serverName);
  if (index < 0) {
    List<String> available = getMasterThreads().stream()
        .map(t -> t.getMaster().getServerName().toString())
        .collect(Collectors.toList());
    throw new IllegalArgumentException(
        "Master not found: " + serverName +
        ". Available Masters (" + available.size() + "): " + available);
  }
  return index;
}
```

Then update all affected methods to use the new helper:

```java
@Override
public void killRegionServer(ServerName serverName) throws IOException {
  int index = getRegionServerIndexOrThrow(serverName);
  HRegionServer server = getRegionServer(index);
  if (server instanceof MiniHBaseClusterRegionServer) {
    LOG.info("Killing " + server.toString());
    ((MiniHBaseClusterRegionServer) server).kill();
  } else {
    abortRegionServer(index);
  }
}

@Override
public void stopRegionServer(ServerName serverName) throws IOException {
  stopRegionServer(getRegionServerIndexOrThrow(serverName));
}

@Override
public void suspendRegionServer(ServerName serverName) throws IOException {
  suspendRegionServer(getRegionServerIndexOrThrow(serverName));
}

@Override
public void resumeRegionServer(ServerName serverName) throws IOException {
  resumeRegionServer(getRegionServerIndexOrThrow(serverName));
}

@Override
public void waitForRegionServerToStop(ServerName serverName, long timeout) throws IOException {
  waitOnRegionServer(getRegionServerIndexOrThrow(serverName));
}

@Override
public AdminService.BlockingInterface getAdminProtocol(ServerName serverName) throws IOException {
  return getRegionServer(getRegionServerIndexOrThrow(serverName)).getRSRpcServices();
}

@Override
public ClientService.BlockingInterface getClientProtocol(ServerName serverName) throws IOException {
  return getRegionServer(getRegionServerIndexOrThrow(serverName)).getRSRpcServices();
}
```

---

## Benefits of the Fix

1. **Clear Error Messages**: Developers immediately understand that the server was not found, rather than debugging a cryptic ArrayIndexOutOfBoundsException.

2. **Debugging Aid**: The error message includes the list of available servers, making it easy to spot issues like stale ServerName references (different startcode).

3. **Fail-Fast**: The error occurs at the point of lookup rather than deep in the call stack.

4. **Consistent Behavior**: All ServerName-based methods will have the same validation behavior.

---

## Example Improved Error Message

Before:
```
java.lang.ArrayIndexOutOfBoundsException: -1
```

After:
```
java.lang.IllegalArgumentException: RegionServer not found: localhost,16020,1704931200000.
Available RegionServers (2): [localhost,16020,1704931500000, localhost,16021,1704931200000]
```

The developer can immediately see that:
- The requested server has startcode `1704931200000`
- The available server on the same port has startcode `1704931500000` (indicating a restart occurred)

---

## Related Information

- **File**: `hbase-server/src/test/java/org/apache/hadoop/hbase/MiniHBaseCluster.java`
- **Discovered via**: Restart testing framework injection (Group 15)
- **Root Cause of Discovery**: Stale ServerName reference after region server restart
