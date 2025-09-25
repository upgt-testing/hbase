package org.apache.hadoop.hbase.master.procedure;

import org.apache.hadoop.hbase.procedure2.AbstractProcedureSchedulerJVMInterface;

public interface MasterProcedureSchedulerJVMInterface extends AbstractProcedureSchedulerJVMInterface {

    java.lang.String dumpLocks() throws java.io.IOException;

    void clear();

    java.lang.String toString();

    java.util.List getLocks();
}
