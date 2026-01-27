/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.regionserver;

import static org.apache.hadoop.hbase.regionserver.storefiletracker.StoreFileTrackerFactory.TRACKER_IMPL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNameTestRule;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionInfoBuilder;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.master.procedure.MasterProcedureEnv;
import org.apache.hadoop.hbase.regionserver.storefiletracker.StoreFileTrackerForTest;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.testclassification.RegionServerTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.hadoop.hbase.util.Pair;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

@Category({ RegionServerTests.class, LargeTests.class })
public class TestMergesSplitsAddToTracker_RestartInjected_Randomized_123 {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestMergesSplitsAddToTracker_RestartInjected.class);

    private static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();

    private static final String FAMILY_NAME_STR = "info";

    private static final byte[] FAMILY_NAME = Bytes.toBytes(FAMILY_NAME_STR);

    @Rule
    public TableNameTestRule name = new TableNameTestRule();

    @BeforeClass
    public static void setupClass() throws Exception {
        TEST_UTIL.startMiniCluster();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        TEST_UTIL.shutdownMiniCluster();
    }

    @Before
    public void setup() {
        StoreFileTrackerForTest.clear();
    }

    private TableName createTable(byte[] splitKey) throws IOException {
        TableDescriptor td = TableDescriptorBuilder.newBuilder(name.getTableName()).setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY_NAME)).setValue(TRACKER_IMPL, StoreFileTrackerForTest.class.getName()).build();
        if (splitKey != null) {
            TEST_UTIL.getAdmin().createTable(td, new byte[][] { splitKey });
        } else {
            TEST_UTIL.getAdmin().createTable(td);
        }
        return td.getTableName();
    }

    @Test
    public void testCommitDaughterRegion() throws Exception {
        TableName table = createTable(null);
        putThreeRowsAndFlush(table);
        HRegion region = TEST_UTIL.getHBaseCluster().getRegions(table).get(0);
        HRegionFileSystem regionFS = region.getStores().get(0).getRegionFileSystem();
        RegionInfo daughterA = RegionInfoBuilder.newBuilder(table).setStartKey(region.getRegionInfo().getStartKey()).setEndKey(Bytes.toBytes("002")).setSplit(false).setRegionId(region.getRegionInfo().getRegionId() + EnvironmentEdgeManager.currentTime()).build();
        RegionInfo daughterB = RegionInfoBuilder.newBuilder(table).setStartKey(Bytes.toBytes("002")).setEndKey(region.getRegionInfo().getEndKey()).setSplit(false).setRegionId(region.getRegionInfo().getRegionId()).build();
        HStoreFile file = (HStoreFile) region.getStore(FAMILY_NAME).getStorefiles().toArray()[0];
        List<Path> splitFilesA = new ArrayList<>();
        splitFilesA.add(regionFS.splitStoreFile(daughterA, Bytes.toString(FAMILY_NAME), file, Bytes.toBytes("002"), false, region.getSplitPolicy()));
        List<Path> splitFilesB = new ArrayList<>();
        splitFilesB.add(regionFS.splitStoreFile(daughterB, Bytes.toString(FAMILY_NAME), file, Bytes.toBytes("002"), true, region.getSplitPolicy()));
        RestartFramework.at("after_commit_daughters").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        MasterProcedureEnv env = TEST_UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor().getEnvironment();
        RestartFramework.at("after_table_create").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Path resultA = regionFS.commitDaughterRegion(daughterA, splitFilesA, env);
        Path resultB = regionFS.commitDaughterRegion(daughterB, splitFilesB, env);
        FileSystem fs = regionFS.getFileSystem();
        verifyFilesAreTracked(resultA, fs);
        RestartFramework.at("after_split_files_created").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_flush").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        verifyFilesAreTracked(resultB, fs);
    }

    @Test
    public void testCommitMergedRegion() throws Exception {
        TableName table = createTable(null);
        split(table, Bytes.toBytes("002"));
        RestartFramework.at("after_flush").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        putThreeRowsAndFlush(table);
        List<HRegion> regions = TEST_UTIL.getHBaseCluster().getRegions(table);
        HRegion first = regions.get(0);
        HRegion second = regions.get(1);
        RestartFramework.at("after_split").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        HRegionFileSystem regionFS = first.getRegionFileSystem();
        RegionInfo mergeResult = RegionInfoBuilder.newBuilder(table).setStartKey(first.getRegionInfo().getStartKey()).setEndKey(second.getRegionInfo().getEndKey()).setSplit(false).setRegionId(first.getRegionInfo().getRegionId() + EnvironmentEdgeManager.currentTime()).build();
        RestartFramework.at("after_commit_merged").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        HRegionFileSystem mergeFS = HRegionFileSystem.createRegionOnFileSystem(TEST_UTIL.getHBaseCluster().getMaster().getConfiguration(), regionFS.getFileSystem(), regionFS.getTableDir(), mergeResult);
        List<Path> mergedFiles = new ArrayList<>();
        mergedFiles.add(mergeFileFromRegion(first, mergeFS));
        mergedFiles.add(mergeFileFromRegion(second, mergeFS));
        MasterProcedureEnv env = TEST_UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor().getEnvironment();
        mergeFS.commitMergedRegion(mergedFiles, env);
        FileSystem fs = first.getRegionFileSystem().getFileSystem();
        RestartFramework.at("after_table_create").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Path finalMergeDir = new Path(first.getRegionFileSystem().getTableDir(), mergeResult.getEncodedName());
        RestartFramework.at("after_merge_files_created").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        verifyFilesAreTracked(finalMergeDir, fs);
    }

    @Test
    public void testSplitLoadsFromTracker() throws Exception {
        TableName table = createTable(null);
        putThreeRowsAndFlush(table);
        HRegion region = TEST_UTIL.getHBaseCluster().getRegions(table).get(0);
        Pair<StoreFileInfo, String> copyResult = copyFileInTheStoreDir(region);
        StoreFileInfo fileInfo = copyResult.getFirst();
        String copyName = copyResult.getSecond();
        RestartFramework.at("after_flush").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_split").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        split(table, Bytes.toBytes("002"));
        List<HRegion> regions = TEST_UTIL.getHBaseCluster().getRegions(table);
        RestartFramework.at("after_table_create").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        HRegion first = regions.get(0);
        validateDaughterRegionsFiles(first, fileInfo.getActiveFileName(), copyName);
        RestartFramework.at("after_file_copy").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        HRegion second = regions.get(1);
        validateDaughterRegionsFiles(second, fileInfo.getActiveFileName(), copyName);
    }

    private void split(TableName table, byte[] splitKey) throws IOException {
        TEST_UTIL.getAdmin().split(table, splitKey);
        // wait until split is done
        TEST_UTIL.waitFor(30000, () -> TEST_UTIL.getHBaseCluster().getRegions(table).size() == 2);
    }

    @Test
    public void testMergeLoadsFromTracker() throws Exception {
        TableName table = createTable(Bytes.toBytes("002"));
        putThreeRowsAndFlush(table);
        List<HRegion> regions = TEST_UTIL.getHBaseCluster().getRegions(table);
        RestartFramework.at("after_merge").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_table_create_with_split").on(TEST_UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        HRegion first = regions.get(0);
        RestartFramework.at("after_file_copy").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Pair<StoreFileInfo, String> copyResult = copyFileInTheStoreDir(first);
        RestartFramework.at("after_flush").on(TEST_UTIL.getMiniHBaseCluster()).restart("regionserver").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        StoreFileInfo fileInfo = copyResult.getFirst();
        String copyName = copyResult.getSecond();
        TEST_UTIL.getAdmin().mergeRegionsAsync(new byte[][] { first.getRegionInfo().getEncodedNameAsBytes(), regions.get(1).getRegionInfo().getEncodedNameAsBytes() }, true).get(10, TimeUnit.SECONDS);
        regions = TEST_UTIL.getHBaseCluster().getRegions(table);
        HRegion merged = regions.get(0);
        validateDaughterRegionsFiles(merged, fileInfo.getActiveFileName(), copyName);
    }

    private Pair<StoreFileInfo, String> copyFileInTheStoreDir(HRegion region) throws IOException {
        Path storeDir = region.getRegionFileSystem().getStoreDir("info");
        // gets the single file
        StoreFileInfo fileInfo = region.getRegionFileSystem().getStoreFiles("info").get(0);
        // make a copy of the valid file staight into the store dir, so that it's not tracked.
        String copyName = UUID.randomUUID().toString().replaceAll("-", "");
        Path copy = new Path(storeDir, copyName);
        FileUtil.copy(region.getFilesystem(), fileInfo.getFileStatus(), region.getFilesystem(), copy, false, false, TEST_UTIL.getConfiguration());
        return new Pair<>(fileInfo, copyName);
    }

    private void validateDaughterRegionsFiles(HRegion region, String originalFileName, String untrackedFile) throws IOException {
        // verify there's no link for the untracked, copied file in first region
        List<StoreFileInfo> infos = region.getRegionFileSystem().getStoreFiles("info");
        assertThat(infos, everyItem(hasProperty("activeFileName", not(containsString(untrackedFile)))));
        assertThat(infos, hasItem(hasProperty("activeFileName", containsString(originalFileName))));
    }

    private void verifyFilesAreTracked(Path regionDir, FileSystem fs) throws Exception {
        for (FileStatus f : fs.listStatus(new Path(regionDir, FAMILY_NAME_STR))) {
            assertTrue(StoreFileTrackerForTest.tracked(regionDir.getName(), FAMILY_NAME_STR, f.getPath()));
        }
    }

    private Path mergeFileFromRegion(HRegion regionToMerge, HRegionFileSystem mergeFS) throws IOException {
        HStoreFile file = (HStoreFile) regionToMerge.getStore(FAMILY_NAME).getStorefiles().toArray()[0];
        return mergeFS.mergeStoreFile(regionToMerge.getRegionInfo(), Bytes.toString(FAMILY_NAME), file);
    }

    private void putThreeRowsAndFlush(TableName table) throws IOException {
        Table tbl = TEST_UTIL.getConnection().getTable(table);
        Put put = new Put(Bytes.toBytes("001"));
        byte[] qualifier = Bytes.toBytes("1");
        put.addColumn(FAMILY_NAME, qualifier, Bytes.toBytes(1));
        tbl.put(put);
        put = new Put(Bytes.toBytes("002"));
        put.addColumn(FAMILY_NAME, qualifier, Bytes.toBytes(2));
        tbl.put(put);
        put = new Put(Bytes.toBytes("003"));
        put.addColumn(FAMILY_NAME, qualifier, Bytes.toBytes(2));
        tbl.put(put);
        TEST_UTIL.flush(table);
    }
}
