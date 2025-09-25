package org.apache.hadoop.hbase.master.assignment;

public interface RegionStatesJVMInterface {

    void clear();

    boolean hasRegionsInTransition();

    java.util.Map getRegionAssignments();

    java.util.SortedSet getRegionsInTransitionOrderedByTimestamp();

    java.util.List getRegionFailedOpen();

    java.util.Collection getRegionStateNodes();

    int getRegionsInTransitionCount();

    java.util.List getAssignedRegions();

    java.util.ArrayList getRegionStates();

    org.apache.hadoop.hbase.master.assignment.RegionStateNodeJVMInterface getRegionStateNodeFromName(byte[] arg0);

    java.util.List getRegionsInTransition();

    org.apache.hadoop.hbase.master.RegionStateJVMInterface getRegionState(java.lang.String arg0);

    double getAverageLoad();

    org.apache.hadoop.hbase.master.assignment.RegionStateNodeJVMInterface getRegionStateNodeFromEncodedRegionName(java.lang.String arg0);

    java.util.List getRegionsStateInTransition();
}
