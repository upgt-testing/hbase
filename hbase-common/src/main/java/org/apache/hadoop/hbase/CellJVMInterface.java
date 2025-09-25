package org.apache.hadoop.hbase;

import org.apache.hadoop.hbase.io.HeapSizeJVMInterface;

public interface CellJVMInterface extends HeapSizeJVMInterface {

    byte getFamilyLength();

    int getTagsLength();

    short getRowLength();

    int getValueLength();

    byte getTypeByte();

    long getSequenceId();

    int getTagsOffset();

    byte[] getQualifierArray();

    int getFamilyOffset();

    int getSerializedSize();

    int getQualifierLength();

    int getRowOffset();

    byte[] getFamilyArray();

    int getValueOffset();

    byte[] getRowArray();

    int getQualifierOffset();

    byte[] getTagsArray();

    byte[] getValueArray();

    long getTimestamp();
}
