package org.apache.hadoop.hbase.regionserver;

public interface StoreFileJVMInterface {

    long getModificationTimeStamp() throws java.io.IOException;

    org.apache.hadoop.fs.Path getEncodedPath();

    long getMaxSequenceId();

    long getMaxMemStoreTS();

    java.util.OptionalLong getMinimumTimestamp();

    long getModificationTimestamp() throws java.io.IOException;

    java.util.Optional getFirstKey();

    java.util.OptionalLong getBulkLoadTimestamp();

    java.lang.Object getComparator();

    org.apache.hadoop.fs.Path getQualifiedPath();

    boolean isBulkLoadResult();

    java.util.OptionalLong getMaximumTimestamp();

    org.apache.hadoop.fs.Path getPath();

    boolean isReference();

    java.util.Optional getLastKey();

    boolean isMajorCompactionResult();

    boolean isHFile();

    boolean excludeFromMinorCompaction();

    java.lang.String toStringDetailed();
}
