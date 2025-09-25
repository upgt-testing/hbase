package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.coprocessor.CoprocessorHostJVMInterface;

public interface RegionCoprocessorHostJVMInterface extends CoprocessorHostJVMInterface<org.apache.hadoop.hbase.coprocessor.RegionCoprocessor, org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment> {

    void preClose(boolean arg0) throws java.io.IOException;

    void postCommitStoreFile(byte[] arg0, org.apache.hadoop.fs.Path arg1, org.apache.hadoop.fs.Path arg2) throws java.io.IOException;

    boolean hasCustomPostScannerFilterRow();

    void preOpen() throws java.io.IOException;

    void postOpen();

    void postClose(boolean arg0);
}
