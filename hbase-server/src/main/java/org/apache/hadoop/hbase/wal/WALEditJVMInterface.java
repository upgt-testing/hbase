package org.apache.hadoop.hbase.wal;

import org.apache.hadoop.hbase.io.HeapSizeJVMInterface;

public interface WALEditJVMInterface extends HeapSizeJVMInterface {

    java.util.Set getFamilies();

    long estimatedSerializedSizeOf();

    boolean isMetaEdit();

    boolean isEmpty();

    java.lang.String toString();

    long heapSize();

    int size();

    boolean isRegionCloseMarker();

    void add(java.util.Map<byte[], java.util.List<org.apache.hadoop.hbase.Cell>> arg0);

    java.util.ArrayList getCells();

    boolean isReplay();
}
