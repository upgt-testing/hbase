package org.apache.hadoop.hbase;

import org.apache.hadoop.hbase.io.HeapSizeJVMInterface;

public interface ExtendedCellJVMInterface extends RawCellJVMInterface, HeapSizeJVMInterface {

    int getTagsLength();

    void setSequenceId(long arg0) throws java.io.IOException;

    byte getTypeByte();

    long getSequenceId();

    int getTagsOffset();

    byte[] getTagsArray();

    void setTimestamp(long arg0) throws java.io.IOException;

    void setTimestamp(byte[] arg0) throws java.io.IOException;
}
