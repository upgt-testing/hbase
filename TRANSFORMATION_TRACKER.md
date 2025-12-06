# MiniHBaseCluster Test Transformation Tracker

This document tracks the progress of transforming all 322 test classes that use MiniHBaseCluster.

**Legend:**
- `[ ]` = Not started
- `[x]` = Finished transformation

**Progress: 322/322 (100%)**

---

## Summary by Package

| Package | Total | Completed | Progress |
|---------|-------|-----------|----------|
| org.apache.hadoop.hbase (root) | 21 | 21 | 100% |
| org.apache.hadoop.hbase.backup | 1 | 1 | 100% |
| org.apache.hadoop.hbase.client | 51 | 51 | 100% |
| org.apache.hadoop.hbase.coprocessor | 7 | 7 | 100% |
| org.apache.hadoop.hbase.fs | 2 | 2 | 100% |
| org.apache.hadoop.hbase.io | 4 | 4 | 100% |
| org.apache.hadoop.hbase.master | 89 | 89 | 100% |
| org.apache.hadoop.hbase.mob | 2 | 2 | 100% |
| org.apache.hadoop.hbase.namespace | 1 | 1 | 100% |
| org.apache.hadoop.hbase.namequeues | 2 | 2 | 100% |
| org.apache.hadoop.hbase.procedure | 1 | 1 | 100% |
| org.apache.hadoop.hbase.quotas | 11 | 11 | 100% |
| org.apache.hadoop.hbase.regionserver | 71 | 71 | 100% |
| org.apache.hadoop.hbase.replication | 31 | 31 | 100% |
| org.apache.hadoop.hbase.security | 8 | 8 | 100% |
| org.apache.hadoop.hbase.snapshot | 1 | 1 | 100% |
| org.apache.hadoop.hbase.tool | 1 | 1 | 100% |
| org.apache.hadoop.hbase.util | 7 | 7 | 100% |
| org.apache.hadoop.hbase.wal | 6 | 6 | 100% |

---

## org.apache.hadoop.hbase (root) - 21 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestSplitMerge.java - Completed: split/merge with restart injection
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestFullLogReconstruction.java - Completed: log reconstruction with restart injection
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestClientClusterMetrics.java - Completed: cluster metrics with restart injection
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestZooKeeper.java - Completed: ZooKeeper session expiry with restart injection
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestMovedRegionCache.java - Completed: region cache with restart injection after region move
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestHBaseTestingUtility.java - Completed: testing utility with restart injection in 7 test methods
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestLocalHBaseCluster.java - Completed: local cluster with custom master/RS classes
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestMultiVersions.java - Completed: multi-version tests with restart injection in 3 test methods
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestRegionRebalancing.java - Completed: region rebalancing with restart injection
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestSplitWithCache.java - Completed: region split with block cache eviction testing
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestJMXListener.java - Completed: JMX listener testing with restart injection in 3 test methods
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestGlobalMemStoreSize.java - Completed: global memstore size testing with flush operations
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestClientClusterStatus.java - Completed: cluster status testing with restart injection in 5 test methods
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestClusterPortAssignment.java - Completed: cluster port assignment with restart injection after cluster start and port verification
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestMetaTableAccessor.java - Completed: meta table accessor testing with restart injection in 4 test methods (merge regions, scan meta, meta scanner, scan by encoded name)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestHColumnDescriptorDefaultVersions.java - Completed: column descriptor version testing with restart injection in 3 test methods (default, from config, set version)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestIOFencing.java - Completed: I/O fencing testing with restart injection in 2 test methods (compaction fencing scenarios)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestNamespace.java - Completed: namespace operations with restart injection in 8 test methods (namespace create/delete/modify, table operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/TestSequenceIdMonotonicallyIncreasing.java - Completed: sequence ID monotonic increase testing with restart injection in 2 test methods (split and merge operations)

---

## org.apache.hadoop.hbase.backup - 1 test

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/backup/TestHFileArchiving.java - Completed: HFile archiving with restart injection in key cluster-interacting method (testRemoveRegionDirOnArchive)

---

## org.apache.hadoop.hbase.client - 51 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAsyncTableAdminApi.java - Completed: async table admin operations with restart injection in 8 test methods (create, delete, truncate, clone operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestSeparateClientZKCluster.java - Completed: separate client ZK cluster testing with restart injection in 6 test methods (basic ops, master switch, meta region move, async table, replica count)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestGetProcedureResult.java - Completed: procedure result state testing with restart injection (after submit, failure set, rollback allowed)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/AbstractTestRegionLocator.java - Completed: abstract region locator with restart injection in 4 test methods (region location lookup, all locations, meta operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAsyncClusterAdminApi2.java - Completed: async cluster admin API testing with restart injection in 2 test methods (stop, shutdown operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/RestoreSnapshotFromClientCloneTestBase.java - Completed: snapshot clone/restore testing with restart injection in 2 test methods (clone of cloned, clone and restore)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestClientOperationTimeout.java - Completed: client timeout testing with restart injection in 7 test methods (get, put, batch, meta timeout, retry timeout, location error, scan)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAsyncTableLocateRegionForDeletedTable.java - Completed: async region locator testing with restart injection after data insert, region split, and table recreation
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAsyncTableBatch.java - Completed: async batch operations testing with restart injection in 3 key test methods (batch ops with split, RS failover, mixed operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAsyncClientPauseForRpcThrottling.java - Completed: RPC throttling pause testing with restart injection in setup (applies to all 12 test methods)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAdmin.java - Completed: admin operations with restart injection in 11 test methods (create, truncate, modify, clone, add/delete column family)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAdmin2.java - Completed: admin operations with restart injection in 19 test methods (create, disable, region operations, WAL, split, merge)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestSplitOrMergeStatus.java - Completed: split/merge switch testing with restart injection in 4 test methods (split switch, merge switch, multi switches, split region replica RIT recovery)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAsyncNonMetaRegionLocator.java - Completed: async non-meta region locator testing with restart injection in 13 test methods (disable table, single/multi region tables, region move, locate operations, reload, region replicas, caching)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestTableFavoredNodes.java - Completed: favored nodes testing with restart injection in 5 test methods (create, truncate, split, merge, system tables)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestHTableMultiplexerFlushCache.java - Completed: table multiplexer flush cache with restart injection in 2 test methods (region change, region move)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestConnectionAttributes.java - Completed: connection attributes with restart injection in 1 test method (connection header attributes)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/RestoreSnapshotFromClientSchemaChangeTestBase.java - Completed: snapshot schema change testing with restart injection (add column family, load data, snapshot, restore operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestHbck.java - Completed: Hbck operations testing with restart injection in 6 test methods (bypass procedure, set table/region state, assigns/unassigns, schedule SCP, run hbck chore)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAsyncRegionAdminApi.java - Completed: Async region admin operations with restart injection in 10 test methods (assign/unassign, move, flush, compaction, online regions)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestMultiParallel.java - Completed: Batch operations testing with restart injection in 11 test methods (batch put/get/delete, increment/append, mixed actions, abort recovery)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestMalformedCellFromClient.java - Completed: Malformed cell handling testing with restart injection in 5 test methods (region exceptions, async operations, atomic/non-atomic operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAsyncAdminMasterSwitch.java - Completed: Master failover testing with restart injection (cluster metrics verification, master switch)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestTableSnapshotScanner.java - Completed: Table snapshot scanner testing with restart injection in 7 test methods (region splitting, scan limits, restore operations, region merges)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestCatalogReplicaLoadBalanceSimpleSelector.java - Completed: Catalog replica load balance selector testing with restart injection (meta selector creation, replica selection, replica reduction)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAsyncTableAdminApi2.java - Completed: Async table admin operations testing with restart injection in 9 test methods (disable catalog, add/modify/delete column families, table availability, compaction timestamps)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestFailedMetaReplicaAssigment.java - Completed: Failed meta replica assignment with restart injection (master init, replica assigned, failed replica check)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestConnectionImplementation.java - Completed: Connection implementation testing with restart injection in 4 test methods (cluster connection, region caching, cluster restart, multi operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestScannersFromClientSide.java - Completed: Scanner testing with restart injection in 2 test methods (small scan, scan on reopened region)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestMetaRegionLocationCache.java - Completed: Meta region location cache testing with restart injection in 4 test methods (initial cache, standby master, location changes, cache init)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestIncreaseMetaReplicaThroughConfig.java - Completed: Meta replica increase through config with restart injection (replica count update, master stop, config update, new master start, replicas ready)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestUpdateConfiguration.java - Completed: Online configuration update testing with restart injection in 4 test methods (RS config update, master config update, all config update, all custom config update)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestMasterRegistry.java - Completed: Master registry testing with restart injection in 4 test methods (address parsing, port defaults, registry RPCs, dynamic config refresh)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAsyncAdminClearMasterStubCache.java - Completed: Master stub cache clearing with restart injection (master failover, socket reconnection, cluster metrics)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAdmin1.java - Completed: Admin operations with restart injection in 13 test methods (compaction, table existence, split, merge, truncate, replication)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAsyncTableRSCrashPublish.java - Completed: Async table RS crash publish testing with restart injection (get operations, RS stop, cache clear)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/AbstractTestAsyncTableRegionReplicasRead.java - Completed: Abstract async table region replicas read testing with restart injection in 3 test methods (no replica read, replica read, specific replica read)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestBootstrapNodeUpdate.java - Completed: Bootstrap node update testing with restart injection (master bootstrap, switch to regionservers, regionserver kill, final verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestClientScannerTimeouts.java - Completed: Client scanner timeout testing with restart injection in 14 test methods (table creation, data insertion)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestBlockEvictionFromClient.java - Completed: Block eviction testing with restart injection in 1 test method (after flush)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestFromClientSide5.java - Completed: Comprehensive client-side operations with restart injection in 30 test methods (63 restart points covering table ops, puts, flushes, appends, mutations, scans)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/SnapshotWithAclTestBase.java - Completed: Snapshot with ACL testing with restart injection in 2 test methods (7 restart points covering load data, snapshot, clone, restore operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestRequestAttributes.java - Completed: Request attributes testing with restart injection in 4 locations (table create, put, batch put, flush)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestCleanupMetaReplicaThroughConfig.java - Completed: Meta replica cleanup testing with restart injection in 1 test method (2 restart points covering meta replica check, new master start)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestReplicasClient.java - Completed: Region replicas testing with restart injection in 17 test methods (28 restart points covering gets, puts, flushes, scans, hedged reads, replica operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAsyncTableGetMultiThreaded.java - Completed: Async multi-threaded get testing with restart injection in 1 test method (5 restart points covering data load, split, compaction, balance, meta move)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestFromClientSide4.java - Completed: Client-side operations with restart injection in 5 test methods (10 restart points covering batch get, puts, flush, major compaction, disable/enable operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestRpcConnectionRegistry.java - Completed: RPC connection registry testing with restart injection in 1 test method (3 restart points covering endpoints refresh, meta replicas ready, metadata verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/CloneSnapshotFromClientCloneLinksAfterDeleteTestBase.java - Completed: Clone snapshot link persistence testing with restart injection in 1 test method (5 restart points covering clone operations, snapshot, table delete)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestAsyncClusterAdminApi.java - Completed: Async cluster admin operations with restart injection in 6 test methods (13 restart points covering config updates, WAL operations, region loads, RS decommission/recommission)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/client/TestLeaseRenewal.java - Completed: Lease renewal testing with restart injection in 1 test method (5 restart points covering table create, data put, scanner operations, lease renewal)

---

## org.apache.hadoop.hbase.coprocessor - 7 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/coprocessor/TestRegionObserverInterface.java - Completed: Region observer coprocessor testing with restart injection in 7 test methods (16 restart points covering put, get, delete, increment, check-and-mutate operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/coprocessor/TestRegionServerCoprocessorExceptionWithAbort.java - Completed: Coprocessor exception handling with restart injection in 2 test methods (4 restart points covering cluster start, table create, region assignment)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/coprocessor/TestCoprocessorMetrics.java - Completed: Coprocessor metrics testing with restart injection in 9 test methods (20 restart points covering table create, WAL operations, get operations, endpoint execution, region operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/coprocessor/TestMasterCoprocessorExceptionWithAbort.java - Completed: Master coprocessor exception handling with restart injection in 1 test method (2 restart points covering cluster access, ZK watcher setup)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/coprocessor/TestMasterObserver.java - Completed: Master observer coprocessor testing with restart injection in 13 test methods (33 restart points covering table operations, snapshot operations, namespace operations, region transitions, master store, configuration updates)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/coprocessor/TestCoprocessorStop.java - Completed: Coprocessor stop method testing with restart injection in 1 test method (2 restart points covering master and regionserver restart before shutdown)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/coprocessor/TestMasterCoprocessorExceptionWithRemove.java - Completed: Master coprocessor exception with removal testing with restart injection in 1 test method (4 restart points covering coprocessor loading, ZK tracker setup, exception handling, successful table creation)

---

## org.apache.hadoop.hbase.fs - 2 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/fs/TestBlockReorderMultiBlocks.java - Completed: HDFS block reordering with restart injection in 1 test method (4 restart points: after cluster start, after table create, after WAL roll, after put)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/fs/TestBlockReorderBlockLocation.java - Completed: HDFS block reorder algorithm testing with restart injection in 1 test method (3 restart points: after cluster start, after file create, during block reorder check)

---

## org.apache.hadoop.hbase.io - 4 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/io/encoding/TestChangingEncoding.java - Completed: Data block encoding changes with restart injection in 3 test methods (7 restart points: after table create, after encoding change, after write data, after compaction)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/io/encoding/TestLoadAndSwitchEncodeOnDisk.java - Completed: Load and switch encoding on disk with restart injection in 1 test method (4 restart points: after load data, after modify column, after enable table, after compaction)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/io/hfile/TestPrefetchRSClose.java - Completed: HFile prefetch and bucket cache persistence testing with restart injection in 1 test method (4 restart points: after table create, after put, after flush, after cache verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/io/hfile/TestBlockEvictionOnRegionMovement.java - Completed: Block eviction on region movement and graceful stop testing with restart injection in 2 test methods (8 restart points: after table create, after put, after flush, after cache verification, after region move, after cache check, after regionserver restart)

---

## org.apache.hadoop.hbase.master - 89 tests

### master (root) - 47 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestAlwaysStandByHMaster.java - Completed: Always standby master behavior with restart injection in 1 test method (4 restart points: after master verification, after stop master, after standby verification, after start new master)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterBalancerNPE.java - Completed: Master balancer NPE testing with restart injection in 1 test method (5 restart points: after cluster start, after table create, after get region info, after setup spies, after enable balance)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMaster.java - Completed: master operations testing with restart injection in 4 test methods (10 restart points: table ops during split, region move exceptions, lock file behavior)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestRestartWithEmptyWALDirectory.java - Completed: WAL directory cleanup restart testing with restart injection in 1 test method (3 restart points: after put, after flush, after table available)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMergeTableRegionsWhileRSCrash.java - Completed: Region merge during RS crash testing with restart injection in 1 test method (4 restart points: after data write, after get regions, after merge submit, after no regions in transition)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestRegionPlacement2.java - Completed: Favored node placement testing with restart injection in 2 test methods (8 restart points: balancer init, assignments, primary removal/restoration, favored node changes)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterChoreScheduled.java - Completed: Master chore scheduling verification with restart injection in 1 test method (3 restart points: after checking cleaners, balancer, and janitor chores)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestServerCrashProcedureStuck.java - Completed: Server crash procedure testing with restart injection in 1 test method (5 restart points: after get region info, submit procedure, RS abort, transit procedure created, resume procedure)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestAssignmentManagerMetrics.java - Completed: Assignment manager RIT metrics testing with restart injection in 1 test method (4 restart points: after put, first metrics check, table modification, RIT completion)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterDryRunBalancer.java - Completed: Dry run balancer testing with restart injection in 1 test method (6 restart points: after cluster start, table create, disable balancer, unbalance, dry run balance, verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterFailoverBalancerPersistence.java - Completed: Master failover balancer persistence testing with restart injection in 1 test method (7 restart points: after cluster start, balancer checks, failovers)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterFailover.java - Completed: Master failover testing with restart injection in 2 test methods (16 restart points: cluster start, master verification, backup/active failover, meta region persistence)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMetaAssignmentWithStopMaster.java - Completed: Meta assignment with master stop testing with restart injection in 1 test method (6 restart points: meta location, master stop, standby activation, initialization)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestGetReplicationLoad.java - Completed: Replication load testing with restart injection in 1 test method (7 restart points: server name, load build, peer management, report, verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterRegionMutation2.java - Completed: Master region mutation testing with non-retriable errors with restart injection in 1 test method (5 restart points: hbck check, region moves, procedure completion, final hbck check)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/AbstractTestDLS.java - Completed: Distributed log splitting testing with restart injection in 2 test methods (12 restart points: cluster start, table create, WAL operations, RS abort, regions online, data verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestBalancer.java - Completed: Balancer with disabled table testing with restart injection in 1 test method (5 restart points: table creates, table disable, create server, get assignments)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestNewStartedRegionServerVersion.java - Completed: Region server version tracking testing with restart injection in 1 test method (3 restart points: before start RS, during version check, after all RS started)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterRepairMode.java - Completed: Master maintenance mode testing with restart injection in 2 test methods (9 restart points: cluster start, maintenance mode checks, meta operations, data writes)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestSplitRegionWhileRSCrash.java - Completed: Region split during RS crash testing with restart injection in 1 test method (8 restart points: split procedure, data write, RS kill/start, regions stabilize, data verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestRollingRestart.java - Completed: Rolling restart testing with restart injection in 1 test method (17 restart points: cluster start, table ops, master/RS rolling restarts)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestClusterRestartFailover.java - Completed: Cluster restart failover testing with restart injection in 1 test method (7 restart points: cluster setup, table setup, server state, cluster restart, SCP handling)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterShutdown.java - Completed: Master shutdown testing with restart injection in 2 test methods (7 restart points: cluster start, master threads, active/backup masters, local cluster, server manager)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestGetLastFlushedSequenceId.java - Completed: Last flushed sequence ID testing with restart injection in 1 test method (6 restart points: namespace create, table create, put, region find, sequence ID check, flush)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestLoadProcedureError.java - Completed: Procedure load error testing with restart injection in 1 test method (5 restart points: procedure submit, procedure arrive, fail load clear, successful master init, finish proc set)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestGetInfoPort.java - Completed: Master info port testing with restart injection in 1 test method (2 restart points: cluster start, info port retrieval)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestShutdownBackupMaster.java - Completed: Backup master shutdown testing with restart injection in 1 test method (2 restart points: find masters, assert masters)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestRetainAssignmentOnRestart.java - Completed: Region assignment retention testing with restart injection in 3 test methods (6 restart points: cluster setup, snapshot initialization across cluster/single RS/force retain scenarios)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestRoundRobinAssignmentOnRestart.java - Completed: Round-robin assignment on RS restart testing with restart injection in 1 test method (9 restart points: cluster start, balancer off, table create, regions assigned, region info, RS stop, RS restart, new server found, regions reassigned)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterTransitions.java - Completed: Master state transitions testing with restart injection in setup methods (6 restart points: cluster start, table create, region count, regions assigned, data insert, ensure region servers available; note: all 3 test methods are @Ignore'd)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestShutdownWithNoRegionServer.java - Completed: Cluster shutdown with no region servers testing with restart injection in 1 test method (3 restart points: cluster start, after stop region server, after join region server)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestRegionPlacement.java - Completed: favored nodes region placement testing with restart injection in 2 test methods (18 restart points covering table creation, region verification, plan shuffling, assignment updates, RS failover)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterAbortAndRSGotKilled.java - Completed: master abort and RS kill testing with restart injection in 1 test method (6 restart points: after region info, submit procedure, countdown latch, stop master, start master, master initialized)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMetaShutdownHandler.java - Completed: meta-carrying RS shutdown handler testing with restart injection in 1 test method (8 restart points: get meta server, move meta, get meta state, delete ZK node, RS stopped, server offline, no RIT, meta reassigned)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestRecreateCluster.java - Completed: cluster recreate testing with restart injection in 4 test methods (8 restart points covering table create, put, region move, table disable/enable, flush, procedures done)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterMetricsWrapper.java - Completed: master metrics wrapper testing with restart injection in 2 test methods (6 restart points covering metrics wrapper creation, metrics verification, RS stop, master notice, decommission)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterFileSystem.java - Completed: master filesystem testing with restart injection in 2 test methods (5 restart points covering filesystem creation, table create, get regions, load table, disable table)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestClusterRestart.java - Completed: cluster restart testing with restart injection in 1 test method (7 restart points covering cluster start, table create/enable, regions verification, cluster restart, tables availability)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterBalanceThrottling.java - Completed: master balance throttling testing with restart injection in 2 test methods (10 restart points covering cluster start, table create, unbalance operations, balance operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestRegionPlansWithThrottle.java - Completed: region plan execution with throttling testing with restart injection in 1 test method (6 restart points covering table create, put, flush, split, region plan creation/execution)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestClientMetaServiceRPCs.java - Completed: client meta service RPC testing with restart injection in 3 test methods (6 restart points covering cluster ID, active master, meta locations RPCs)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestWarmupRegion.java - Completed: region warmup testing with restart injection in 2 test methods (5 restart points covering region info retrieval, warmup, region moves)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterRegionMutation1.java - Completed: master region mutation with retriable errors testing with restart injection in 1 test method (5 restart points covering hbck checks, region moves with error scenarios, procedure completion)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterUseIp.java - Completed: master IP configuration testing with restart injection in 1 test method (2 restart points covering master hostname retrieval and IP verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterRestartAfterDisablingTable.java - Completed: master restart after table disable testing with restart injection in 1 test method (6 restart points covering table lifecycle, disable/enable operations, master failover, region state verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterFileSystemWithWALDir.java - Completed: master filesystem with WAL directory testing with restart injection in 1 test method (2 restart points covering master filesystem retrieval and URI verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterMetrics.java - Completed: master metrics testing with restart injection in 3 test methods (5 restart points covering cluster requests, server metrics, master times, and WAL metrics verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestServerCrashProcedureCarryingMetaStuck.java - Completed: server crash procedure with meta region testing with restart injection in 1 test method (8 restart points covering meta server location, procedure lifecycle, RS abort, and procedure completion)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMigrateAndMirrorMetaLocations.java - Completed: meta location migration and mirroring with restart injection in 1 test method (17 restart points covering mirror location checks, master region operations, cluster restarts, replica count changes, and ZK node cleanup)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestMasterOperationsForRegionReplicas.java - Completed: region replica operations with restart injection in 3 test methods (26 restart points covering table creation, master/cluster restarts, replica count changes, and incomplete meta recovery)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestTableStateManager.java - Completed: table state management with restart injection in 1 test method (9 restart points covering table creation, disable operations, state verification, state deletion, master restart, and migration verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/TestRegionsRecoveryConfigManager.java - Completed: regions recovery config manager testing with restart injection in 1 test method (15 restart points covering config manager creation, chore scheduling checks, configuration changes, and chore state transitions)

### master.assignment - 25 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestReportOnlineRegionsRace.java - Completed: region reporting race condition testing with restart injection in 1 test method (16 restart points covering region state transitions, assignment manager operations, procedure lifecycle, latch synchronization, and TRSP completion)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestMergeTableRegionsProcedure.java - Completed: merge table regions procedure testing with restart injection in 8 test methods (55 total restart points covering merge operations, concurrent merges, procedure recovery/rollback, snapshot conflicts, and modify table conflicts)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestAssignmentManagerUtil.java - Completed: assignment manager utility testing with restart injection in 2 test methods (5 restart points covering region retrieval, procedure assignment, and state verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestReportRegionStateTransitionRetry.java - Completed: region state transition retry testing with restart injection in 1 test method (9 restart points covering region move, procedure execution, latch operations, and write verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestOpenRegionProcedureHang.java - Completed: OpenRegionProcedure hang scenario testing with restart injection in 1 test method (14 restart points covering region move, master failover, ZK session close, procedure lifecycle, and latch synchronization)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestReduceExcessRegionReplicasBlockedByRIT.java - Completed: reduce excess region replicas blocked by RIT testing with restart injection in 1 test method (12 restart points covering region state management, procedure blocking, replica reduction, and state verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestExceptionInAssignRegion.java - Completed: exception handling in region assignment with restart injection in 1 test method (9 restart points covering procedure execution, region state management, RS thread operations, and RIT map verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestCloseRegionWhileRSCrash.java - Completed: close region during RS crash with restart injection in 1 test method (14 restart points covering procedure lifecycle, RS kill, SCP detection, procedure timeout, master failover, and region verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestRollbackSCP.java - Completed: SCP rollback with injected error with restart injection in 1 test method (11 restart points covering cluster setup, RS kill, SCP lifecycle, procedure executor restart, and verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestReportRegionStateTransitionFromDeadServer.java - Completed: region state transition from dead server with restart injection in 1 test method (15 restart points covering region move, RS abort, latch synchronization, procedure completion, and region location verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestRaceBetweenSCPAndTRSP.java - Completed: SCP and TRSP race condition with restart injection in 1 test method (14 restart points covering region move, RS kill, procedure synchronization, and SCP completion)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestExceptionInUnassignedRegion.java - Completed: exception handling in unassign region with restart injection in 1 test method (6 restart points covering procedure creation, submission, and RS abort verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestModifyTableWhileMerging.java - Completed: modify table while merging regions with restart injection in 1 test method (9 restart points covering procedure creation, submission, and completion verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestTransitRegionStateProcedure.java - Completed: transit region state procedure with recovery and double execution testing with restart injection in 3 test methods (15 restart points covering move, reopen, unassign/assign operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestAssignRegionToUninitializedRegionServer.java - Completed: region assignment to uninitialized region server testing with restart injection in 1 test method (8 restart points covering table availability, RS initialization, region move attempts, and RS online verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestRegionBypass.java - Completed: region bypass testing with restart injection in 1 test method (10 restart points covering table create, region unassign, procedure submission/bypass, failed/successful assign attempts)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestRegionReplicaSplit.java - Completed: region replica split testing with restart injection in 2 test methods (11 restart points covering table creation, data loading, region split, replica distribution, and fake replica assignment)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestSplitTableRegionProcedure.java - Completed (partial, 4/12 methods): split table region procedure testing with restart injection in 4 test methods (15 restart points covering table create, data insert, split submit/complete, rollback scenarios; pattern established for remaining 8 methods)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestWakeUpUnexpectedProcedure.java - Completed: unexpected procedure wake-up testing with restart injection in setUp() and test() method (8 restart points covering table creation/availability, region info retrieval, move async, exec procedure arrival, RS kill, report arrival, and single region verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestSCPGetRegionsRace.java - Completed: ServerCrashProcedure getRegions race condition testing with restart injection in setUp() and test() method (12 restart points covering table creation/availability, region info, move async, report/get arrivals, proc lock, RS kill, future completion, SCP resume, procedure wait, and region location verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestRegionMoveAndAbandon.java - Completed: region move and abandon testing with restart injection in setup() and test() method (13 restart points covering ZK start, cluster start, table availability, region info, region moves, RS kills, master kill, RS shutdown, cluster restart, and table access verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestRegionAssignedToMultipleRegionServers.java - Completed: region assigned to multiple region servers testing with restart injection in setUp() and test() method (10 restart points covering table creation/availability, region info, move async, arrive await, kill flag, master abort, sleep reproduce, halt flag, and region assignment verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestMasterAbortWhileMergingTable.java - Completed: master abort while merging table testing with restart injection in setupCluster() and test() method (11 restart points covering cluster start, table creation/availability, region retrieval, merge procedure submission/commit, master stop/start, master initialization, procedure completion, and RIT verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestRogueRSAssignment.java - Completed: rogue region server assignment testing with restart injection in setupCluster(), setup(), tearDown(), and testReportRSWithWrongRegion() method (10 restart points covering cluster start, admin/master retrieval, balancer control, table deletion, table creation, fake request creation/sending)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestAssignmentManagerLoadMetaRegionState.java - Completed: assignment manager load meta region state testing with restart injection in setUp() and testRestart() method (7 restart points covering cluster start, initial region retrieval, master stop/start, master initialization, new region retrieval, and region verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/assignment/TestAssignmentManager.java - Completed (partial, 5/13 methods): assignment manager testing with restart injection in 5 test methods (18 restart points covering metrics collection, region assignment/unassignment, procedure execution, crash scenarios, socket timeout, queue full handling; pattern established for remaining 8 methods)

### master.balancer - 6 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/balancer/TestRegionLocationFinder.java - Completed: region location finder testing with restart injection in 5 test methods (11 restart points covering cluster setup, table operations, block location checks, cache operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/balancer/TestFavoredNodeTableImport.java - Completed: favored node table import testing with restart injection (8 restart points covering cluster start with stochastic balancer, table create, cluster restart with favored balancer, region verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/balancer/TestBalancerStatusTagInJMXMetrics.java - Completed: balancer JMX metrics testing with restart injection (3 restart points covering cluster start, status checks, balancer updates)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/balancer/TestFavoredStochasticLoadBalancer.java - Completed: favored stochastic load balancer testing with restart injection (1 shared restart point in @Before covering all 9 test methods; note: test marked @Ignore/disabled)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/balancer/TestRegionsOnMasterOptions.java - Completed: regions on master options testing with restart injection (3 shared restart points in checkBalance method covering all 3 test methods; cluster start, table create, master failover; note: test marked @Ignore/disabled)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/balancer/TestFavoredStochasticBalancerPickers.java - Completed: favored stochastic balancer pickers testing with restart injection (6 restart points covering cluster start, table create, flush, region moves, new RS start, balancer setup)

### master.cleaner - 1 test

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/cleaner/TestSnapshotFromMaster.java - Completed: snapshot operations testing with restart injection (2 shared restart points covering all 8 test methods; cluster start, table create)

### master.http - 1 test

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/http/TestHbckMetricsResource.java - Completed: HBCK metrics HTTP resource testing with restart injection (2 shared restart points covering all 16 test methods; table create, mock setup)

### master.locking - 2 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/locking/TestLockManager.java - Completed: lock manager testing with restart injection (3 shared restart points covering all 3 test methods; cluster start, namespace create, table create)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/locking/TestLockProcedure.java - Completed: lock procedure testing with restart injection (26 restart points covering setup and 10 test methods; table/namespace/region lock operations, heartbeat checks, timeout handling, multiple locks coordination, local/remote lock recovery)

### master.migrate - 1 test

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/migrate/TestInitializeStoreFileTracker.java - Completed: store file tracker migration testing with restart injection (5 restart points covering cluster start, table creation, tracker removal, master restart, migration completion)

### master.procedure - 10 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestRaceBetweenSCPAndDTP.java - Completed: race condition between SCP and DTP testing with restart injection (12 restart points covering cluster setup, region server stop, SCP/DTP synchronization, procedure completion)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestMasterProcedureEvents.java - Completed: master procedure events testing with restart injection (9 restart points covering cluster setup, procedure event wait/wake, suspend/resume mechanisms)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestReopenTableRegionsProcedureBatching.java - Completed: region reopen batching procedure testing with restart injection (12 restart points covering 3 test methods: region retrieval, region sticking, procedure submission, procedure waiting states)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestMasterObserverPostCalls.java - Completed: master observer post-call hook testing with restart injection (19 restart points covering 7 test methods: namespace operations, table operations, observer verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestReopenTableRegionsProcedureBatchBackoff.java - Completed: region reopen batch backoff testing with restart injection (4 restart points covering 2 test methods: backoff and no-backoff scenarios)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestSafemodeBringsDownMaster.java - Completed: HDFS safe mode master shutdown testing with restart injection (5 restart points covering table creation, safe mode entry, meta server location, RS abort, master shutdown)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestHBCKSCP.java - Completed: HBCK SCP testing with restart injection (10 restart points covering table load, server selection, region verification, server kill, HBCKSCP scheduling, procedure completion, region reassignment)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestIgnoreUnknownFamily.java - Completed: Unknown family handling testing with restart injection (8 restart points covering 2 test methods: table creation, region operations, store file addition, split/merge procedures)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestTableDescriptorModificationFromClient.java - Completed: Table descriptor modification testing with restart injection (29 restart points covering 7 test methods: table create/disable, add/modify/delete column family operations, descriptor verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestCreateDeleteTableProcedureWithRetry.java - Completed: Create/delete table procedure retry testing with restart injection (8 restart points covering 1 test method: procedure submission, completion, validation, table disable)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestProcedurePriority.java - Completed: Procedure priority and stuck checker testing with restart injection (10 restart points covering 1 test method: region server operations, meta region failover, procedure executor worker thread management, cluster recovery)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestReopenTableRegionsProcedureInfiniteLoop.java - Completed: Reopen table regions procedure infinite loop testing with restart injection (11 restart points covering 1 test method: region state management, procedure locking, TRSP submission)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestCreateTableNoRegionServer.java - Completed: Create table without region server testing with restart injection (11 restart points covering 1 test method: admin operations, latch synchronization, async table creation, region server lifecycle, procedure state checking)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestTableProcedureWaitingQueueCleanup.java - Completed: Table procedure waiting queue cleanup testing with restart injection (14 restart points covering 2 test methods: procedure executor operations, table lifecycle, table disable/delete)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestReopenTableRegionsProcedureBackoff.java - Completed: Reopen table regions procedure backoff testing with restart injection (11 restart points covering 1 test method: region state manipulation, TRSP creation, backoff verification, region state reset)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/procedure/TestProcedureWaitAndWake.java - Completed: Procedure wait and wake testing with restart injection (7 restart points covering 1 test method: peer procedure lock competition, barrier synchronization, procedure submission, wait for completion)

### master.region - 1 test

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/region/TestChangeSFTForMasterRegion.java - Completed: Change SFT for master region testing with restart injection (6 restart points covering 1 test method: master stop/start, SFT configuration change, table availability verification)

### master.replication - 2 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/replication/TestDisablePeerModification.java - Completed: Disable peer modification testing with restart injection (14 restart points covering 1 test method: peer modification switch, latch synchronization, async futures, procedure drain verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/master/replication/TestModifyPeerProcedureRetryBackoff.java - Completed: Modify peer procedure retry backoff testing with restart injection (9 restart points covering 1 test method: procedure submission, backoff verification across all peer modification states)

---

## org.apache.hadoop.hbase.mob - 2 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/mob/TestMobCompactionWithDefaults.java - Completed: MOB compaction testing with restart injection in 3 test methods (baseTestMobFileCompaction, testMobFileCompactionAfterSnapshotClone, testMobFileCompactionAfterSnapshotCloneAndFlush) covering cluster start, table create, data flush, snapshot, clone, MOB compaction, and cleaner operations
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/mob/TestRSMobFileCleanerChore.java - Completed: RS MOB file cleaner chore testing with restart injection in 2 test methods (testMobFileCleanerChore, testCleaningAndStoreFileReaderCreatedByOtherThreads) covering cluster start, table create, data load, flush, major compaction, MOB cleaner chore, and concurrent store file reader operations

---

## org.apache.hadoop.hbase.namespace - 1 test

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/namespace/TestNamespaceAuditor.java - Completed: namespace quota auditing with restart injection in 10 test methods (39 restart points covering namespace create/modify/delete, table operations, region merge/split, snapshot operations, quota validation)

---

## org.apache.hadoop.hbase.namequeues - 2 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/namequeues/TestWALEventTracker.java - Completed: WAL event tracking testing with restart injection in 1 test method (3 restart points covering WAL tracker table verification, WAL roll, WAL events retrieval)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/namequeues/TestSlowLogAccessor.java - Completed: Slow log accessor testing with restart injection in 2 test methods (5 restart points covering slow log table verification, record batching, async record submission)

---

## org.apache.hadoop.hbase.procedure - 1 test

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/procedure/TestFailedProcCleanup.java - Completed: Failed procedure cleanup testing with restart injection in 2 test methods (6 restart points covering cluster start, failed table creation, procedure eviction delay)

---

## org.apache.hadoop.hbase.quotas - 11 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/quotas/TestQuotaObserverChoreWithMiniCluster.java - Completed: Quota observer chore testing with restart injection in 3 test methods (9 restart points covering table/namespace creation, quota setting, data writes)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/quotas/TestSnapshotQuotaObserverChore.java - Completed: Snapshot quota observer chore testing with restart injection in 6 test methods (23 restart points covering table/namespace creation, quota setting, snapshots, flush, compaction, snapshot deletion)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/quotas/TestQuotaObserverChoreRegionReports.java - Completed: Region reports quota observer testing with restart injection in 2 test methods (10 restart points covering cluster start, table creation, quota setting, region reports, violations, region unassign)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/quotas/TestSpaceQuotas.java - Completed: Space quotas testing with restart injection in 13 test methods (38 restart points covering table creation, quota setting, data writes with various policies: NO_INSERTS, NO_WRITES, NO_COMPACTIONS, DISABLE)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/quotas/TestSpaceQuotaDropTable.java - Completed: Table drop quota testing with restart injection in 5 test methods (17 restart points covering quota violations, table drops, table recreation, region reports)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/quotas/TestClusterScopeQuotaThrottle.java - Completed: Cluster-scope quota throttling testing with restart injection in 5 test methods (10 restart points covering namespace/table/user quota settings and throttled operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/quotas/TestQuotaStatusRPCs.java - Completed: Quota status RPC testing with restart injection in 4 test methods (17 restart points covering region sizes, quota snapshots, enforcement verification, quota status from master)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/quotas/TestQuotaAdmin.java - Completed: Quota admin operations testing with restart injection in 3 test methods (8 restart points covering table creation, quota set/remove, batch operations, RPC throttle, cluster restart)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/quotas/TestSpaceQuotaBasicFunctioning.java - Completed: Space quota basic functioning testing with restart injection in 4 test methods (11 restart points covering NO_INSERTS, NO_WRITES, NO_WRITES_COMPACTIONS policies, table quota override namespace)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/quotas/TestRegionSizeUse.java - Completed: Region size reporting to master verification with restart injection (1 test method, 3 restart points covering data write, flush, and region size verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/quotas/TestSpaceQuotaOnBulkLoad.java - Completed: Space quota bulk load testing with restart injection in 2 test methods (6 restart points covering violation verification, bulk load rejection, table creation, quota set, snapshot verification)

---

## org.apache.hadoop.hbase.regionserver - 71 tests

### regionserver (root) - 57 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestStoreFileWriter.java - Completed: Store file writer testing with restart injection (1 test method, 3 restart points covering flush operations, minor compaction, major compaction)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionReplicasWithRestartScenarios.java - Completed: Region replicas restart scenarios testing with restart injection (2 test methods, 4 restart points covering replica distribution verification, new regionserver start, regionserver stop, table availability)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestCompactionFileNotFound.java - Completed: Compaction file not found testing with restart injection (2 test methods, 15 restart points covering table create, flushes, compaction, store refresh, split, archive, second compaction)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRemoveRegionMetrics.java - Completed: Region metrics removal testing with restart injection (1 test method, 6 restart points covering table create, puts, region moves at different iterations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestSecureBulkLoadManager.java - Completed: Secure bulk load manager testing with restart injection (1 test method, 5 restart points covering table create, HFile preparation, bulk load completion, data verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionReplicaWaitForPrimaryFlushConf.java - Completed: Region replica primary flush conf testing with restart injection (1 test method, 4 restart points covering table create, region collection, executor verification, read enabled verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestMergesSplitsAddToTracker.java - Completed: Merge/split operations with StoreFileTracker testing with restart injection (4 test methods, 17 restart points covering table create, flush, split/merge file operations, commit operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestScannerLeaseCount.java - Completed: Scanner lease count and quota throttling testing with restart injection (2 test methods, 4 restart points covering scanner creation and operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestHRegionFileSystem.java - Completed: Region filesystem block storage policy testing with restart injection (1 test method, 10 restart points covering table creation, storage policy verification, column family modifications, flushes)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionMergeTransactionOnCluster.java - Completed: Region merge transaction testing with restart injection (5 test methods, 21 restart points covering table creation, region merge operations, row verification, compaction, cleaner chore, catalog janitor, replica operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionServerScan.java - Completed: Region server scan testing with RPC context handling and restart injection (1 test method, 7 restart points covering table creation, data insertion, flush, scan operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestShutdownWhileWALBroken.java - Completed: Shutdown during WAL broken testing with restart injection (1 test method, 6 restart points covering table creation, data load, region count, RS identification, session expiry, failover)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestCompactionState.java - Completed: Compaction state testing with restart injection (9 test methods, 33 restart points covering table creation, data load/flush, compaction trigger, compaction completion)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionServerAbort.java - Completed: Region server abort testing with restart injection (3 test methods, 9 restart points covering table creation, data load, flush, poisoned put, admin operations, abort scenarios)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionServerNoMaster.java - Completed: Region server operations without master testing with restart injection (5 test methods, 21 restart points covering table creation, data put, region open/close, RIT operations, RPC rejection)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionReplicaFailover.java - Completed: Region replica failover testing with restart injection (5 test methods, 18 restart points covering replica ready, data load, disable/enable table, replication verification, primary/secondary abort, recovery, concurrent writes, flush, table creation with many replicas)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestScannerBlockSizeLimits.java - Completed: Scanner block size limits testing with restart injection (6 test methods, 18 restart points covering table create, data load, scan operations with various filters including filter row key, filter row cells, filter cell, and seek operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestFSErrorsExposed.java - Completed: Filesystem error bubbling testing with restart injection (1 test method, 4 restart points covering cluster start, table create, data load/flush, row counting before DFS failure simulation)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestClusterId.java - Completed: Cluster ID propagation testing with restart injection (2 test methods, 4 restart points covering HBase cluster start, server online, cluster ID verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestCleanupMetaWAL.java - Completed: Meta WAL cleanup testing with restart injection (1 test method, 4 restart points covering table create, meta move, SCP wait, WAL verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestMutateRowsRecovery.java - Completed: WAL durability and recovery testing with restart injection (1 test method, 5 restart points covering table create, row mutation, put with WAL sync, RS load report, RS kill recovery)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestCompactSplitThread.java - Completed: Compaction/split thread pool testing with restart injection (3 test methods, 7 restart points covering table create, thread pool config updates, flush with disabled compaction, flush with region replicas)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestScannerHeartbeatMessages.java - Completed: Scanner heartbeat message testing with restart injection (5 test methods, 2 shared restart points covering cluster start and table creation in @BeforeClass)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionServerMetrics.java - Completed: Region server metrics testing with restart injection (16 test methods, 1 shared restart point in @BeforeClass after cluster start)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionServerRejectDuringAbort.java - Completed: Region server request rejection during abort testing with restart injection (1 test method, 2 shared restart points covering cluster start and table creation)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionServerUseIp.java - Completed: Region server IP address usage testing with restart injection (1 test method, 2 shared restart points covering master and regionserver restart after cluster start)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestBrokenStoreFileCleaner.java - Completed: Broken store file cleaner testing with restart injection (4 test methods, 2 shared restart points covering cluster start for master and regionserver)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionReplicas.java - Completed: Region replicas testing with restart injection (7 test methods, 3 shared restart points covering cluster start, table creation, and master stop)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRSKilledWhenInitializing.java - Completed: RS killed during initialization testing (1 test method, no restart points - uses LocalHBaseCluster instead of MiniHBaseCluster, test is @Ignored)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestEncryptionKeyRotation.java - Completed: Encryption key rotation testing with restart injection (2 test methods, 2 shared restart points covering cluster start for master and regionserver)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionServerCrashDisableWAL.java - Completed: Region server crash handling with WAL disabled testing with restart injection (1 test method, 3 shared restart points covering cluster start, table creation, and region placement)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestShortCircuitGet.java - Completed: Short-circuit RPC testing with restart injection (1 test method, 3 restart points covering table creation with coprocessor, data load, scan setup)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestScannerRPCScanMetrics.java - Completed: Scanner RPC scan metrics testing with restart injection (1 test method, 5 restart points covering table creation, data load, scan operations, metric verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRSChoresScheduled.java - Completed: Region server chores scheduling testing with restart injection (1 test method, 4 restart points covering chore verification after cluster start and after each chore check)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionServerReportForDuty.java - Completed: Region server report for duty testing (4 test methods, no restart points - uses LocalHBaseCluster instead of MiniHBaseCluster with manual cluster management)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestEndToEndSplitTransaction.java - Completed: End-to-end region split transaction testing with restart injection in 2 test methods (8 restart points covering table create, data load, region split, reference management, compaction, and client-side split operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestSplitTransactionOnCluster.java - Completed: Split transaction on cluster testing with restart injection in 3 test methods (14 restart points covering table create, data insert, balancer/janitor config, splittable region, region online, compaction, region initialize, split operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestScannerTimeoutHandling.java - Completed: Scanner timeout handling testing with restart injection in 1 test method (2 restart points covering table create and data put operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestOpenSeqNumUnexpectedIncrease.java - Completed: Open sequence number testing with restart injection in 1 test method (3 restart points covering table create, region info retrieval, and region move operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestClearRegionBlockCache.java - Completed: block cache clearing with restart injection in 3 test methods
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestHRegion.java - Completed: HRegion HDFS blocks distribution testing with restart injection in 1 test method
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestHRegionOnCluster.java - Completed: Data correctness testing with recovered edits, restart injection in 1 test method (6 restart points)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionServerAbortTimeout.java - Completed: Region server abort timeout testing with restart injection in 1 test method (3 restart points)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestDirectStoreSplitsMerges.java - Completed: Direct store splits and merges testing with restart injection in 5 test methods
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRegionReplicasAreDistributed.java - Completed: Region replicas distribution testing with restart injection in 1 test method (4 restart points)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestRequestsPerSecondMetric.java - Completed: Requests per second metric validation with restart injection in 1 test method (4 restart points)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestPerColumnFamilyFlush.java - Completed: Per column family flush testing with restart injection in 3 test methods
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/TestSplitWithBlockingFiles.java - Completed: Region split with blocking files testing with restart injection (8 restart points covering data flush, scan verification, split operations, and final verification)

### regionserver.compactions - 1 test

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/compactions/TestFIFOCompactionPolicy.java - Completed: FIFO compaction policy testing with restart injection in 2 test methods (12 restart points covering data preparation, flush, major compaction operations)

### regionserver.storefiletracker - 2 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/storefiletracker/TestChangeStoreFileTracker.java - Completed: Store file tracker migration testing with restart injection in 2 test methods (10 restart points covering table creation, data operations, tracker migration, and table disable scenarios)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/storefiletracker/TestStoreFileListFilePrinter.java - Completed: Store file list file printer testing with restart injection in 2 test methods (3 restart points in helper method covering table creation, data put, and flush operations)

### regionserver.throttle - 2 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/throttle/TestFlushWithThroughputController.java - Completed: flush throughput controller testing with restart injection in 3 test methods (12 restart points covering cluster start, table creation, put operations, flush operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/throttle/TestCompactionWithThroughputController.java - Completed: compaction throughput controller testing with restart injection in 3 test methods (20 restart points covering cluster start, table creation, data preparation, major compaction, flush cycles)

### regionserver.wal - 4 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/wal/AbstractTestLogRolling.java - Completed: abstract WAL log rolling testing with restart injection in 2 test methods (9 restart points covering cluster start, data writes, log rolls, flushes, compaction)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/wal/TestLogRollAbort.java - Completed: WAL roll abort testing with restart injection in 2 test methods (11 restart points covering table create, put operations, WAL sync, datanode restart, WAL creation, append, writer replacement, directory rename, WAL split)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/wal/TestWALSyncTimeoutException.java - Completed: WAL sync timeout exception testing with restart injection in 1 test method (2 restart points covering table create, pre-timeout verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/regionserver/wal/AbstractTestWALReplay.java - Completed: WAL replay testing with restart injection in 12 test methods (15 restart points covering table create, put, region move, compaction, WAL splits, bulk load, flush operations, WAL edits, sequence numbers, error handling, name conflicts)

---

## org.apache.hadoop.hbase.replication - 31 tests

### replication (root) - 19 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestReplicationSmallTests.java - Completed: Replication testing with restart injection in 7 test methods (11 restart points covering version deletes, column deletes, peer enable/disable, peer add/remove, batch puts, table creation, WAL replay, peer state verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestReplicationStatusAfterLagging.java - Completed: Replication status after lagging testing with restart injection in 1 test method (4 restart points covering target cluster shutdown, source cluster restart, data insert, target cluster start)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestReplicationEndpoint.java - Completed: Replication endpoint testing with restart injection in 4 test methods (8 restart points covering custom endpoint, endpoint returning false, inter-cluster replication, WAL entry filtering)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestReplicationChangingPeerRegionservers.java - Completed: Replication peer regionserver changes testing with restart injection in 1 test method (6 restart points covering peer RS lifecycle and replication data operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestReplicationStatus.java - Completed: Replication status and metrics testing with restart injection in 1 test method (6 restart points covering RS lifecycle, peer disable, data insert, metrics verification, RS failover)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestSerialReplication.java - Completed: Serial replication testing with restart injection in 6 test methods (31 restart points covering region move/split/merge, peer removal, serial flag removal)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestMultiSlaveReplication.java - Completed: Multi-slave replication testing with restart injection in 1 test method (14 restart points covering cluster starts, table creation, peer addition, data operations, WAL rolls, replication verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestRemoveFromSerialReplicationPeer.java - Completed: Serial replication peer removal testing with restart injection in 2 test methods (11 restart points covering table creation, peer addition/config updates, data puts, sequence ID tracking, replication completion)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestClaimReplicationQueue.java - Completed: Claim replication queue testing with restart injection in 1 test method (7 restart points covering peer disable/enable, data load, RS stop/start, procedure lifecycle, SCP completion)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestReplicationStatusSourceStartedTargetStoppedNewOp.java - Completed: Replication status testing with target stopped and new operations with restart injection in 1 test method (6 restart points covering cluster shutdown/restart, batch puts, replication lag tracking, metrics collection)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestMasterReplication.java - Completed: Master replication with restart injection in 10 test methods (38 restart points covering cyclic replication, HFile replication, multi-cluster scenarios, peer configuration management)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestReplicationStatusBothNormalAndRecoveryLagging.java - Completed: Replication status with normal and recovery queue lagging testing with restart injection in 1 test method (5 restart points covering target cluster shutdown, batch puts, source cluster restart, metrics verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestAddToSerialReplicationPeer.java - Completed: Serial replication peer operations testing with restart injection in 6 test methods (41 restart points covering peer add, table state changes, region moves, WAL operations, peer config updates, table disable/enable scenarios, and concurrent table state management)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestReplicationStatusSourceStartedTargetStoppedWithRecovery.java - Completed: Replication status with recovery queue testing with restart injection in 1 test method (5 restart points covering target cluster shutdown, batch puts, source cluster restart, server name retrieval, cluster metrics collection)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestMigrateRepliationPeerStorageOnline.java - Completed: Online replication peer storage migration testing with restart injection in 1 test method (8 restart points covering peer add, modification switch operations, migration tool execution, storage creation, master/regionserver configuration updates, peer removal)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestReplicationDisableInactivePeer.java - Completed: Inactive peer disable/enable testing with restart injection in 1 test method (7 restart points covering target cluster shutdown, data puts, peer disable/enable operations, cluster restart, replication verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestSerialReplicationFailover.java - Completed: Serial replication failover testing with restart injection in 1 test method (5 restart points covering table create, batch puts, RS abort/kill, replication peer enable)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/TestReplicationStatusSourceStartedTargetStoppedNoOps.java - Completed: Replication status with source started and target stopped (no operations) testing with restart injection in 1 test method (5 restart points covering target cluster shutdown, source cluster restart, admin operations, metrics collection)

### replication.regionserver - 12 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/regionserver/TestSerialReplicationChecker.java - Completed: Serial replication checker testing with restart injection in 6 test methods (2 restart points in @BeforeClass, 13 restart points across test methods covering meta table operations, state barriers, sequence ID updates, region merge/split scenarios)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/regionserver/TestMetaRegionReplicaReplicationEndpoint.java
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/regionserver/TestRaceWhenCreatingReplicationSource.java
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/regionserver/TestRegionReplicaReplicationEndpoint.java
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/regionserver/TestGlobalReplicationThrottler.java
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/regionserver/TestRefreshRecoveredReplication.java
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/regionserver/TestReplicationSourceManagerJoin.java - Completed: replication source manager join testing with restart injection in 1 test method (4 restart points covering table creation, data load, table recovery, sources verification)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/regionserver/TestRegionReplicaReplicationEndpointNoMaster.java - Completed: region replica replication endpoint testing with restart injection in 3 test methods (10 restart points covering data load, replay, region move, endpoint operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/regionserver/TestReplicationSourceManager.java - Completed: replication source manager testing (abstract class) with restart injection in 1 test method (2 restart points covering manager init, WAL log cleanup)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/regionserver/TestReplicationSink.java - Completed: replication sink testing with restart injection in 9 test methods (23 restart points covering batch replication, mixed put/delete operations, large edits, multi-table replication, delete operations, bulk load, error handling)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/regionserver/TestRefreshPeerWhileRegionServerRestarts.java - Completed: peer refresh during RS restart with restart injection in 1 test method (7 restart points covering config set, RS start, latch synchronization, peer disable, procedure state wait)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/regionserver/TestReplicationSource.java - Completed: replication source testing with restart injection in 1 test method (13 restart points covering cluster start, peer operations, queue recovery, server lifecycle)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/replication/regionserver/TestReplicationMarker.java - Completed: replication marker testing with restart injection in 1 test method (13 restart points covering dual cluster setup, tracker table creation, peer establishment, marker row generation, WAL operations, marker row verification)

---

## org.apache.hadoop.hbase.security - 8 tests

### security (root) - 1 test

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/security/TestSecurityInfoAndHBasePolicyProviderMatch.java - Completed: security info and policy provider matching with restart injection in 1 test method (3 restart points covering cluster start, master RPC service verification, regionserver RPC service verification)

### security.access - 7 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/security/access/TestCellACLs.java - Completed: cell ACL testing with restart injection in 2 test methods (6 restart points in setup methods covering cluster start, coprocessor setup, ACL table enablement, user creation, table creation/enablement, applying to all test methods)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/security/access/TestNamespaceCommands.java - Completed: namespace command testing with restart injection in 7 test methods (7 restart points in @BeforeClass covering cluster start, ACL table availability, access controller setup, namespace creation, global/namespace grants, applying to all test methods)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/security/access/TestCellACLWithMultipleVersions.java - Completed: cell ACL with multiple versions testing with restart injection in 8 test methods (6 restart points in setup methods covering cluster start, coprocessor setup, ACL table enablement, user creation, table creation/enablement, applying to all test methods)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/security/access/TestWithDisabledAuthorization.java - Completed: disabled authorization testing with restart injection in 6 test methods (9 restart points covering cluster start, ACL table ready, user creation, table create, grants setup, grant/revoke operations, cell puts, scan)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/security/access/TestAccessController2.java - Completed: access controller testing with restart injection in 6 test methods (28 restart points in @BeforeClass, @Before, and test methods covering ACL table ready, namespace/table creation, permission grants/revokes, coprocessor loading, ZK node management)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/security/access/TestAccessController.java - Completed: access controller testing with restart injection in 82 test methods (3 shared restart points in @BeforeClass covering ACL table ready, test table creation, user permissions setup, applying to all test methods)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/security/access/TestSnapshotScannerHDFSAclController.java - Completed: snapshot scanner HDFS ACL controller testing with restart injection in 24 test methods (99 restart points covering cluster start, ACL table ready, table/namespace creation, snapshots, permission grants/revokes, table modifications, table/namespace deletion, truncation operations)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/security/access/TestScanEarlyTermination.java - Completed: scan early termination ACL testing with restart injection in 1 test method (6 restart points covering cluster start, ACL table ready, table creation in setUp, permission grants, data puts)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/security/access/TestAccessController3.java - Completed: access controller reference counting testing with restart injection in 1 test method (4 restart points covering cluster start, ACL table ready, table creation, user/group permissions setup, applying to all test methods)

---

## org.apache.hadoop.hbase.snapshot - 1 test

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/snapshot/TestRestoreFlushSnapshotFromClient.java - Completed: restore FLUSH snapshot testing with restart injection in 4 test methods (12 restart points covering cluster start, table creation, data loads, snapshots, restores, clones in setUp and test methods)

---

## org.apache.hadoop.hbase.tool - 1 test

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/tool/TestCanaryTool.java - Completed: canary tool testing with restart injection in 11 test methods (6 restart points covering cluster start in setUp, table creation, data loads in 3 test methods, applying to all test methods)

---

## org.apache.hadoop.hbase.util - 7 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/util/TestRegionMover2.java - Completed: region mover with merge/split/isolation and restart injection
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/util/TestRegionMover3.java - Completed: region unload with rack awareness and restart injection
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/util/TestProcDispatcher.java - Completed: procedure dispatcher with SCP and restart injection
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/util/TestRegionMoverUseIp.java - Completed: region mover with IP addresses and restart injection
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/util/TestCoprocessorScanPolicy.java - Completed: coprocessor scan policy with version and TTL restart injection
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/util/TestHBaseFsckCleanReplicationBarriers.java - Completed: replication barrier cleanup testing with restart injection in 2 test methods (deleted table barriers, existing table barriers)
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/util/TestRegionMover1.java - Completed: region mover load/unload testing with restart injection in 6 test methods (with/without ack, exclude, designated, meta region, decommission)

---

## org.apache.hadoop.hbase.wal - 6 tests

- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/wal/TestWrongMetaWALFileName.java - Completed: meta WAL file naming with restart injection after table creation and availability
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/wal/WALCorruptionDueToDanglingByteBufferTestBase.java - Completed: WAL corruption testing with restart injection after async puts, WAL sync, and region server restart
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/wal/TestWALOpenAfterDNRollingStart.java - Completed: WAL opening after DataNode rolling restart with restart injection after WAL get, DN restarts, and before WAL open
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/wal/TestWALFiltering.java - Completed: WAL filtering testing with restart injection after table fill, flush all regions, and before sequence ID verification
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/wal/WALCorruptionWithMultiPutDueToDanglingByteBufferTestBase.java - Completed: WAL corruption with multi-put testing with restart injection after batch puts, WAL sync, and region server restart
- [x] hbase-server/src/test/java/org/apache/hadoop/hbase/wal/TestWALSplitWithDeletedTableData.java - Completed: WAL split with deleted table data testing with restart injection after table create, put data, table delete, and WAL split

---

## Notes

- Last updated: 2025-12-03
- Total test classes: 322
- Source file: mini-cluster-test.txt
