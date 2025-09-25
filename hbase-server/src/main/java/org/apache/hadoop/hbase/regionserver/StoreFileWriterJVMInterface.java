package org.apache.hadoop.hbase.regionserver;

public interface StoreFileWriterJVMInterface extends CellSinkJVMInterface, ShipperListenerJVMInterface {

    long getPos() throws java.io.IOException;

    org.apache.hadoop.fs.Path getPath();

    void appendTrackedTimestampsToMetadata() throws java.io.IOException;

    boolean hasGeneralBloom();

    void appendFileInfo(byte[] arg0, byte[] arg1) throws java.io.IOException;

    void appendMetadata(long arg0, boolean arg1, long arg2) throws java.io.IOException;

    java.util.List getPaths();

    void appendMetadata(long arg0, boolean arg1) throws java.io.IOException;

    void beforeShipped() throws java.io.IOException;

    void close() throws java.io.IOException;
}
