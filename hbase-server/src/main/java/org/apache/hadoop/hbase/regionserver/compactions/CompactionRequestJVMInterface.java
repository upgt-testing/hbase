package org.apache.hadoop.hbase.regionserver.compactions;

public interface CompactionRequestJVMInterface {

    boolean isOffPeak();

    boolean isMajor();

    long getSize();

    boolean isAllFiles();

    long getSelectionTime();

    int getPriority();

    java.util.Collection getFiles();
}
