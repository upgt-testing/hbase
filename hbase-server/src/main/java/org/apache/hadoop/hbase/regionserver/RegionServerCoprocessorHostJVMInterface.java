package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.coprocessor.CoprocessorHostJVMInterface;

public interface RegionServerCoprocessorHostJVMInterface extends CoprocessorHostJVMInterface<org.apache.hadoop.hbase.coprocessor.RegionServerCoprocessor, org.apache.hadoop.hbase.coprocessor.RegionServerCoprocessorEnvironment> {

    void preRollWALWriterRequest() throws java.io.IOException;

    void preClearCompactionQueues() throws java.io.IOException;

    void postClearCompactionQueues() throws java.io.IOException;

    void preClearRegionBlockCache() throws java.io.IOException;

    void postUpdateConfiguration(org.apache.hadoop.conf.Configuration arg0) throws java.io.IOException;

    void postReplicateLogEntries() throws java.io.IOException;

    void preUpdateConfiguration(org.apache.hadoop.conf.Configuration arg0) throws java.io.IOException;

    void postRollWALWriterRequest() throws java.io.IOException;

    void preExecuteProcedures() throws java.io.IOException;

    void postExecuteProcedures() throws java.io.IOException;

    void preReplicateLogEntries() throws java.io.IOException;
}
