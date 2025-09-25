package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.ScheduledChoreJVMInterface;

public interface BrokenStoreFileCleanerJVMInterface extends ScheduledChoreJVMInterface {

    boolean setEnabled(boolean arg0);

    void chore();

    boolean getEnabled();
}
