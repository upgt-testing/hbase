package org.apache.hadoop.hbase.zookeeper;

public interface MasterAddressTrackerJVMInterface extends ZKNodeTrackerJVMInterface {

    void nodeChildrenChanged(java.lang.String arg0);

    org.apache.hadoop.hbase.ServerNameJVMInterface getMasterAddress(boolean arg0);

    org.apache.hadoop.hbase.ServerNameJVMInterface getMasterAddress();

    java.util.List getBackupMasters();

    int getMasterInfoPort();

    boolean hasMaster();
}
