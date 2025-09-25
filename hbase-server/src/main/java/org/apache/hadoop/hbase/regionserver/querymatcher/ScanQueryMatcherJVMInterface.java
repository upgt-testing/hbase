package org.apache.hadoop.hbase.regionserver.querymatcher;

import org.apache.hadoop.hbase.regionserver.ShipperListenerJVMInterface;

public interface ScanQueryMatcherJVMInterface extends ShipperListenerJVMInterface {

    java.lang.Object getStartKey();

    java.lang.Object currentRow();

    boolean isUserScan();

    org.apache.hadoop.hbase.filter.FilterJVMInterface getFilter();

    void beforeShipped() throws java.io.IOException;

    boolean hasNullColumnInQuery();

    void clearCurrentRow();
}
