package org.apache.hadoop.hbase.util;

public interface PairJVMInterface<T1, T2> {

    int hashCode();

    T2 getSecond();

    void setSecond(T2 arg0);

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    void setFirst(T1 arg0);

    T1 getFirst();
}
