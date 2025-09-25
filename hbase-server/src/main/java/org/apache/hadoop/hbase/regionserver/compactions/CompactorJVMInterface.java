package org.apache.hadoop.hbase.regionserver.compactions;

public interface CompactorJVMInterface<T> {

    boolean isCompacting();

    org.apache.hadoop.hbase.regionserver.compactions.CompactionProgressJVMInterface getProgress();
}
