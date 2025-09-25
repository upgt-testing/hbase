package org.apache.hadoop.hbase.client;

public interface RowMutationsJVMInterface extends RowJVMInterface {

    int hashCode();

    java.util.List getMutations();

    int getMaxPriority();

    boolean equals(java.lang.Object arg0);

    byte[] getRow();
}
