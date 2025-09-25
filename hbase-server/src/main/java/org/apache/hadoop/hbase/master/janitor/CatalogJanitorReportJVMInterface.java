package org.apache.hadoop.hbase.master.janitor;

public interface CatalogJanitorReportJVMInterface {

    java.util.List getOverlaps();

    java.util.Map getMergedRegions();

    boolean isEmpty();

    java.lang.String toString();

    java.util.List getEmptyRegionInfo();

    java.util.List getUnknownServers();

    java.util.List getHoles();

    long getCreateTime();
}
