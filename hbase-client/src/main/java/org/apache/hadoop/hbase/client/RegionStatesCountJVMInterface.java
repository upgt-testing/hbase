package org.apache.hadoop.hbase.client;

public interface RegionStatesCountJVMInterface {

    int hashCode();

    int getSplitRegions();

    boolean equals(java.lang.Object arg0);

    int getRegionsInTransition();

    java.lang.String toString();

    int getClosedRegions();

    int getTotalRegions();

    int getOpenRegions();
}
