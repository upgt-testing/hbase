package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.ScheduledChoreJVMInterface;

public interface CompactedHFilesDischargerJVMInterface extends ScheduledChoreJVMInterface {

    void chore();

    boolean setUseExecutor(boolean arg0);
}
