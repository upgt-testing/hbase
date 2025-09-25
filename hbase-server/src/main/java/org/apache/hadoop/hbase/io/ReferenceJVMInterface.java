package org.apache.hadoop.hbase.io;

public interface ReferenceJVMInterface {

    int hashCode();

    java.lang.Object getFileRegion();

    java.lang.Object convert();

    byte[] getSplitKey();

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    org.apache.hadoop.fs.Path write(org.apache.hadoop.fs.FileSystem arg0, org.apache.hadoop.fs.Path arg1) throws java.io.IOException;
}
