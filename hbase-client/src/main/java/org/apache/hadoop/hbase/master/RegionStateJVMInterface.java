package org.apache.hadoop.hbase.master;

public interface RegionStateJVMInterface {

    boolean isReadyToOnline();

    boolean isMerged();

    java.lang.String toDescriptiveString();

    boolean isUnassignable();

    boolean isSplitting();

    boolean isOpened();

    boolean isOffline();

    boolean isFailedOpen();

    java.lang.Object getRegion();

    boolean isOpening();

    boolean isClosedOrAbnormallyClosed();

    boolean isClosed();

    long getRitDuration();

    boolean isReadyToOffline();

    int hashCode();

    java.lang.Object getState();

    boolean equals(java.lang.Object arg0);

    java.lang.Object convert();

    java.lang.String toString();

    boolean isMerging();

    boolean isClosing();

    boolean isSplittingNew();

    long getStamp();

    boolean isSplit();

    org.apache.hadoop.hbase.ServerNameJVMInterface getServerName();

    boolean isMergingNew();

    boolean isFailedClose();
}
