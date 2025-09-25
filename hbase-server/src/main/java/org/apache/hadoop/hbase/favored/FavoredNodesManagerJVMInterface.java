package org.apache.hadoop.hbase.favored;

public interface FavoredNodesManagerJVMInterface {

    org.apache.hadoop.hbase.master.RackManagerJVMInterface getRackManager();

    int getDataNodePort();
}
