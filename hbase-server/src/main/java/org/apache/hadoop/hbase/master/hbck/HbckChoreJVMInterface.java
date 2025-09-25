package org.apache.hadoop.hbase.master.hbck;

import org.apache.hadoop.hbase.ScheduledChoreJVMInterface;

public interface HbckChoreJVMInterface extends ScheduledChoreJVMInterface {

    org.apache.hadoop.hbase.master.hbck.HbckReportJVMInterface getLastReport();

    boolean runChore();

    boolean isDisabled();

    boolean isRunning();
}
