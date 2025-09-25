package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.ipc.RpcCallbackJVMInterface;

public interface RegionScannerImplJVMInterface extends RegionScannerJVMInterface, ShipperJVMInterface, RpcCallbackJVMInterface {

    boolean reseek(byte[] arg0) throws java.io.IOException;

    int getBatch();

    void run() throws java.io.IOException;

    boolean isFilterDone() throws java.io.IOException;

    void shipped() throws java.io.IOException;

    java.lang.Object getRegionInfo();

    long getMaxResultSize();

    java.lang.String getOperationId();

    void close();

    long getMvccReadPoint();
}
