package org.apache.hadoop.hbase.regionserver.compactions;

public interface CompactionContextJVMInterface {

    org.apache.hadoop.hbase.regionserver.compactions.CompactionRequestImplJVMInterface getRequest();

    boolean hasSelection();
}
