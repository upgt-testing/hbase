# TEST-BUG-GROUP-37: NullPointerException in MasterCoprocessorHost.createEnvironment

## Summary

The test `TestAccessController2_RestartInjected.testCoprocessorLoading()` passes a null coprocessor instance to `MasterCoprocessorHost.createEnvironment()` after a master restart, causing a NullPointerException.

## Root Cause

The test dynamically loads a coprocessor using `cpHost.load()`, which only stores the coprocessor in memory. When the master is restarted:
1. The new master creates a fresh `MasterCoprocessorHost`
2. Only coprocessors specified in configuration are loaded
3. The programmatically loaded coprocessor is lost
4. `findCoprocessor()` returns `null`
5. The test passes this null value to `createEnvironment()`, causing NPE

## Reproduction

```bash
cd /home/shuai/xlab/restart_testing/hbase/hbase-server
mvn surefire:test -Dtest=TestAccessController2_RestartInjected#testCoprocessorLoading \
  -Drestart.position=after_load_coprocessor \
  -Drestart.target=master \
  -Drestart.mode=GRACEFUL \
  -Drestart.tracking.agent=/home/shuai/xlab/restart_testing/RestartTestingFramework/restart-tracking-agent/target/restart-tracking-agent-1.0.0-SNAPSHOT.jar
```

## Stack Trace

```
java.lang.NullPointerException
    at org.apache.hadoop.hbase.master.MasterCoprocessorHost.createEnvironment(MasterCoprocessorHost.java:164)
    at org.apache.hadoop.hbase.security.access.TestAccessController2_RestartInjected.testCoprocessorLoading(TestAccessController2_RestartInjected.java:668)
```

## Buggy Test Code

**File**: `hbase-server/src/test/java/org/apache/hadoop/hbase/security/access/TestAccessController2_RestartInjected.java`

```java
@Test
public void testCoprocessorLoading() throws Exception {
  MasterCoprocessorHost cpHost =
    TEST_UTIL.getMiniHBaseCluster().getMaster().getMasterCoprocessorHost();
  cpHost.load(MyAccessController.class, Coprocessor.PRIORITY_HIGHEST, conf);  // Line 656: Load coprocessor in-memory

  RestartFramework.at("after_load_coprocessor")
    .on(cluster)
    .restart("master")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();                                                                 // Lines 658-663: Master restart - coprocessor is LOST
  cpHost = TEST_UTIL.getMiniHBaseCluster().getMaster().getMasterCoprocessorHost();

  AccessController ACCESS_CONTROLLER = cpHost.findCoprocessor(MyAccessController.class);  // Line 666: Returns NULL after restart
  MasterCoprocessorEnvironment CP_ENV =
    cpHost.createEnvironment(ACCESS_CONTROLLER, Coprocessor.PRIORITY_HIGHEST, 1, conf);   // Line 668: NPE - ACCESS_CONTROLLER is null!
  // ...
}
```

## Why This Is a TEST-BUG

1. **Coprocessors loaded via `cpHost.load()` are not persisted** - They exist only in memory on the master process
2. **After master restart, a new `MasterCoprocessorHost` is created** - It only loads coprocessors from configuration
3. **The test doesn't account for this behavior** - It assumes the coprocessor will survive the restart
4. **The test should either:**
   - Re-load the coprocessor after restart
   - Add a null check before calling `createEnvironment()`
   - Not inject a restart at this position

## Source Code Reference

**MasterCoprocessorHost.createEnvironment()** (Line 164):
```java
@Override
public MasterEnvironment createEnvironment(final MasterCoprocessor instance, final int priority,
  final int seq, final Configuration conf) {
  // If coprocessor exposes any services, register them.
  for (Service service : instance.getServices()) {  // <-- NPE here when instance is null
    masterServices.registerService(service);
  }
  // ...
}
```

**CoprocessorHost.findCoprocessor()** (Lines 330-337):
```java
public <T extends C> T findCoprocessor(Class<T> cls) {
  for (E env : coprocEnvironments) {
    if (cls.isAssignableFrom(env.getInstance().getClass())) {
      return (T) env.getInstance();
    }
  }
  return null;  // <-- Returns null when coprocessor not found
}
```

## Suggested Fix for Test Code

Option 1: Re-load the coprocessor after restart
```java
RestartFramework.at("after_load_coprocessor")
  .on(cluster)
  .restart("master")
  .withIndex(0)
  .withMode(RestartMode.GRACEFUL)
  .execute();
cpHost = TEST_UTIL.getMiniHBaseCluster().getMaster().getMasterCoprocessorHost();

// Re-load the coprocessor after restart since it was only in-memory
cpHost.load(MyAccessController.class, Coprocessor.PRIORITY_HIGHEST, conf);

AccessController ACCESS_CONTROLLER = cpHost.findCoprocessor(MyAccessController.class);
```

Option 2: Add null check before using the coprocessor
```java
AccessController ACCESS_CONTROLLER = cpHost.findCoprocessor(MyAccessController.class);
if (ACCESS_CONTROLLER == null) {
  // Re-load or skip the test at this position
  cpHost.load(MyAccessController.class, Coprocessor.PRIORITY_HIGHEST, conf);
  ACCESS_CONTROLLER = cpHost.findCoprocessor(MyAccessController.class);
}
MasterCoprocessorEnvironment CP_ENV =
  cpHost.createEnvironment(ACCESS_CONTROLLER, Coprocessor.PRIORITY_HIGHEST, 1, conf);
```

## Classification

| Category | Value |
|----------|-------|
| Type | TEST-BUG |
| Severity | Medium |
| Component | Test Code |
| Root Cause | Test doesn't handle coprocessor loss after restart |
