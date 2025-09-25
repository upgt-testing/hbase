package org.apache.hadoop.hbase.master.janitor;

import org.apache.hadoop.hbase.ScheduledChoreJVMInterface;

public interface CatalogJanitorJVMInterface extends ScheduledChoreJVMInterface {

    org.apache.hadoop.hbase.master.janitor.CatalogJanitorReportJVMInterface getLastReport();

    int scan() throws java.io.IOException;

    boolean setEnabled(boolean arg0);

    boolean getEnabled();
}
