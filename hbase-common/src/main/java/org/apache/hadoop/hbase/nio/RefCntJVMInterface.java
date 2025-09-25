package org.apache.hadoop.hbase.nio;

public interface RefCntJVMInterface {

    boolean hasRecycler();

    org.apache.hadoop.hbase.nio.RefCntJVMInterface touch();

    boolean release();

    java.lang.Object touch(java.lang.Object arg0);

    java.lang.Object getRecycler();

    java.lang.Object retain(int arg0);

    java.lang.Object retain();

    boolean release(int arg0);
}
