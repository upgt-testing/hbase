package org.apache.hadoop.hbase.mob;

public interface MobCellJVMInterface {

    java.lang.Object getCell();

    void close() throws java.io.IOException;
}
