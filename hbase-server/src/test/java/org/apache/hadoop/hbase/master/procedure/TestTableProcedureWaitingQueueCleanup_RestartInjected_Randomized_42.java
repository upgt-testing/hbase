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
package org.apache.hadoop.hbase.master.procedure;

import static org.junit.Assert.assertTrue;
import java.io.IOException;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionInfoBuilder;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.procedure2.Procedure;
import org.apache.hadoop.hbase.procedure2.ProcedureExecutor;
import org.apache.hadoop.hbase.procedure2.ProcedureStateSerializer;
import org.apache.hadoop.hbase.procedure2.ProcedureSuspendedException;
import org.apache.hadoop.hbase.procedure2.ProcedureTestingUtility;
import org.apache.hadoop.hbase.procedure2.ProcedureYieldException;
import org.apache.hadoop.hbase.testclassification.MasterTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

/**
 * Testcase for HBASE-28876
 */
@Category({ MasterTests.class, MediumTests.class })
public class TestTableProcedureWaitingQueueCleanup_RestartInjected_Randomized_42 {

    @ClassRule
    public static final HBaseClassTestRule CLASS_RULE = HBaseClassTestRule.forClass(TestTableProcedureWaitingQueueCleanup_RestartInjected.class);

    private static final HBaseTestingUtility UTIL = new HBaseTestingUtility();

    private static TableDescriptor TD = TableDescriptorBuilder.newBuilder(TableName.valueOf("test")).setColumnFamily(ColumnFamilyDescriptorBuilder.of("cf")).build();

    // In current HBase code base, we do not use table procedure as sub procedure, so here we need to
    // introduce one for testing
    public static class NonTableProcedure extends Procedure<MasterProcedureEnv> implements PeerProcedureInterface {

        private boolean created = false;

        @Override
        protected Procedure<MasterProcedureEnv>[] execute(MasterProcedureEnv env) throws ProcedureYieldException, ProcedureSuspendedException, InterruptedException {
            if (created) {
                return null;
            }
            created = true;
            return new Procedure[] { new CreateTableProcedure(env, TD, new RegionInfo[] { RegionInfoBuilder.newBuilder(TD.getTableName()).build() }) };
        }

        @Override
        protected void rollback(MasterProcedureEnv env) throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        protected boolean abort(MasterProcedureEnv env) {
            return false;
        }

        @Override
        protected void serializeStateData(ProcedureStateSerializer serializer) throws IOException {
        }

        @Override
        protected void deserializeStateData(ProcedureStateSerializer serializer) throws IOException {
        }

        @Override
        public String getPeerId() {
            return "peer";
        }

        @Override
        public PeerOperationType getPeerOperationType() {
            return PeerOperationType.ENABLE;
        }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        UTIL.startMiniCluster();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        UTIL.shutdownMiniCluster();
    }

    // the root procedure will lock meta but we will schedule a table procedure for other table
    public static class MetaTableProcedure extends Procedure<MasterProcedureEnv> implements TableProcedureInterface {

        private boolean created = false;

        @Override
        public TableName getTableName() {
            return TableName.META_TABLE_NAME;
        }

        @Override
        public TableOperationType getTableOperationType() {
            return TableOperationType.EDIT;
        }

        @Override
        protected Procedure<MasterProcedureEnv>[] execute(MasterProcedureEnv env) throws ProcedureYieldException, ProcedureSuspendedException, InterruptedException {
            if (created) {
                return null;
            }
            created = true;
            return new Procedure[] { new CreateTableProcedure(env, TD, new RegionInfo[] { RegionInfoBuilder.newBuilder(TD.getTableName()).build() }) };
        }

        @Override
        protected void rollback(MasterProcedureEnv env) throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        protected boolean abort(MasterProcedureEnv env) {
            return false;
        }

        @Override
        protected void serializeStateData(ProcedureStateSerializer serializer) throws IOException {
        }

        @Override
        protected void deserializeStateData(ProcedureStateSerializer serializer) throws IOException {
        }
    }

    private void testCreateDelete(Procedure<MasterProcedureEnv> proc) throws Exception {
        RestartFramework.at("after_get_procedure_executor").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        ProcedureExecutor<MasterProcedureEnv> procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        RestartFramework.at("after_verify_table_exists").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_disable_table").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        ProcedureTestingUtility.submitAndWait(procExec, proc);
        RestartFramework.at("after_delete_table").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        assertTrue(UTIL.getAdmin().tableExists(TD.getTableName()));
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        // Without the fix in HBASE-28876, we will hang there forever, as we do not clean up the
        // TableProcedureWaitingQueue
        UTIL.getAdmin().disableTable(TD.getTableName());
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
        UTIL.getAdmin().deleteTable(TD.getTableName());
        RestartFramework.at("after_submit_and_wait_procedure").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        procExec = UTIL.getMiniHBaseCluster().getMaster().getMasterProcedureExecutor();
    }

    @Test
    public void testNonTableProcedure() throws Exception {
        RestartFramework.at("after_test_non_table_procedure_complete").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("before_test_non_table_procedure").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        testCreateDelete(new NonTableProcedure());
    }

    @Test
    public void testNotSameTable() throws Exception {
        RestartFramework.at("before_test_not_same_table").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        testCreateDelete(new MetaTableProcedure());
        RestartFramework.at("after_test_not_same_table_complete").on(UTIL.getMiniHBaseCluster()).restart("master").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }
}
