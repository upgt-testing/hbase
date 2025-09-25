package org.apache.hadoop.hbase.master;

public interface SplitWALManagerJVMInterface {

    void archive(java.lang.String arg0) throws java.io.IOException;

    boolean isSplitWALFinished(java.lang.String arg0) throws java.io.IOException;
}
