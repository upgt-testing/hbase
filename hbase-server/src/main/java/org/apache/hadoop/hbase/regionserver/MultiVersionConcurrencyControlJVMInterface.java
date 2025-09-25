package org.apache.hadoop.hbase.regionserver;

public interface MultiVersionConcurrencyControlJVMInterface {

    long getWritePoint();

    java.lang.Object begin();

    java.lang.String toString();

    long getReadPoint();

    void await();

    void advanceTo(long arg0);

    java.lang.Object begin(java.lang.Runnable arg0);
}
