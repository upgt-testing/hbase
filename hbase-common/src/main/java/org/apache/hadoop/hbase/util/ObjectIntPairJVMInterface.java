package org.apache.hadoop.hbase.util;

public interface ObjectIntPairJVMInterface<T> {

    int hashCode();

    void setFirst(T arg0);

    int getSecond();

    boolean equals(java.lang.Object arg0);

    void setSecond(int arg0);

    java.lang.String toString();

    T getFirst();
}
