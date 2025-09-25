package org.apache.hadoop.hbase.client;

import org.apache.hadoop.hbase.CellScannableJVMInterface;
import org.apache.hadoop.hbase.CellScannerJVMInterface;

public interface ResultJVMInterface extends CellScannableJVMInterface, CellScannerJVMInterface {

    java.util.List getColumnCells(byte[] arg0, byte[] arg1);

    boolean containsEmptyColumn(byte[] arg0, int arg1, int arg2, byte[] arg3, int arg4, int arg5);

    boolean containsNonEmptyColumn(byte[] arg0, byte[] arg1);

    java.lang.Object getColumnLatestCell(byte[] arg0, int arg1, int arg2, byte[] arg3, int arg4, int arg5);

    java.util.NavigableMap getNoVersionMap();

    boolean containsColumn(byte[] arg0, byte[] arg1);

    org.apache.hadoop.hbase.client.CursorJVMInterface getCursor();

    java.util.List listCells();

    byte[] value();

    java.lang.Boolean getExists();

    byte[] getRow();

    java.nio.ByteBuffer getValueAsByteBuffer(byte[] arg0, int arg1, int arg2, byte[] arg3, int arg4, int arg5);

    java.util.NavigableMap getFamilyMap(byte[] arg0);

    java.lang.Object cellScanner();

    org.apache.hadoop.hbase.client.RegionLoadStatsJVMInterface getStats();

    java.util.NavigableMap getMap();

    byte[] getValue(byte[] arg0, byte[] arg1);

    java.lang.Object current();

    boolean isEmpty();

    boolean containsEmptyColumn(byte[] arg0, byte[] arg1);

    java.lang.String toString();

    java.lang.Object getColumnLatestCell(byte[] arg0, byte[] arg1);

    int size();

    boolean advance();

    boolean isStale();

    boolean containsNonEmptyColumn(byte[] arg0, int arg1, int arg2, byte[] arg3, int arg4, int arg5);

    boolean containsColumn(byte[] arg0, int arg1, int arg2, byte[] arg3, int arg4, int arg5);

    boolean loadValue(byte[] arg0, int arg1, int arg2, byte[] arg3, int arg4, int arg5, java.nio.ByteBuffer arg6) throws java.nio.BufferOverflowException;

    boolean mayHaveMoreCellsInRow();

    void setExists(java.lang.Boolean arg0);

    java.nio.ByteBuffer getValueAsByteBuffer(byte[] arg0, byte[] arg1);

    boolean loadValue(byte[] arg0, byte[] arg1, java.nio.ByteBuffer arg2) throws java.nio.BufferOverflowException;

    java.lang.Object[] rawCells();

    boolean isPartial();

    boolean isCursor();
}
