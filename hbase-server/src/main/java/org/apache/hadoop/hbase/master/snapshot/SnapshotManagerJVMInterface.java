package org.apache.hadoop.hbase.master.snapshot;

import org.apache.hadoop.hbase.procedure.MasterProcedureManagerJVMInterface;
import org.apache.hadoop.hbase.StoppableJVMInterface;

public interface SnapshotManagerJVMInterface extends MasterProcedureManagerJVMInterface, StoppableJVMInterface {

    void checkSnapshotSupport() throws java.lang.UnsupportedOperationException;

    java.lang.String getProcedureSignature();

    java.util.concurrent.locks.ReadWriteLock getTakingSnapshotLock();

    boolean isStopped();

    boolean isTakingAnySnapshot();

    boolean snapshotProcedureEnabled();

    void stop(java.lang.String arg0);

    java.util.List getCompletedSnapshots() throws java.io.IOException;
}
