package org.apache.hadoop.hbase.master;

public interface DeadServerJVMInterface {

    java.lang.String toString();

    int size();

    java.util.Set copyServerNames();
}
