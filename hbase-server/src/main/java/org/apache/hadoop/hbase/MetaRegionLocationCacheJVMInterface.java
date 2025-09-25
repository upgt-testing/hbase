package org.apache.hadoop.hbase;

import org.apache.hadoop.hbase.zookeeper.ZKListenerJVMInterface;

public interface MetaRegionLocationCacheJVMInterface extends ZKListenerJVMInterface {

    void nodeChildrenChanged(java.lang.String arg0);

    void nodeDataChanged(java.lang.String arg0);

    void nodeCreated(java.lang.String arg0);

    java.util.Optional getMetaRegionLocations();

    void nodeDeleted(java.lang.String arg0);
}
