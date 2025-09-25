package org.apache.hadoop.hbase;

public interface KeyValueJVMInterface extends ExtendedCellJVMInterface {

    byte getFamilyLength();

    java.util.Map toStringMap();

    long getSequenceId();

    byte[] getQualifierArray();

    int getSerializedSize(boolean arg0);

    boolean updateLatestStamp(byte[] arg0);

    org.apache.hadoop.hbase.KeyValueJVMInterface shallowCopy();

    org.apache.hadoop.hbase.KeyValueJVMInterface createKeyOnly(boolean arg0);

    int getFamilyOffset();

    byte getFamilyLength(int arg0);

    org.apache.hadoop.hbase.KeyValueJVMInterface clone() throws java.lang.CloneNotSupportedException;

    long heapSize();

    int getValueOffset();

    java.lang.String getKeyString();

    int getQualifierOffset();

    byte[] getTagsArray();

    int getOffset();

    int getLength();

    int hashCode();

    int getKeyLength();

    int getTagsLength();

    boolean equals(java.lang.Object arg0);

    java.lang.String toString();

    int getValueLength();

    byte getTypeByte();

    short getRowLength();

    byte[] getBuffer();

    int getKeyOffset();

    int getTagsOffset();

    int getTimestampOffset();

    java.lang.Object deepClone();

    int getSerializedSize();

    void setTimestamp(byte[] arg0);

    int getQualifierLength();

    int getRowOffset();

    void setSequenceId(long arg0);

    byte[] getFamilyArray();

    byte[] getRowArray();

    byte[] getKey();

    void write(java.nio.ByteBuffer arg0, int arg1);

    void setTimestamp(long arg0);

    byte[] getValueArray();

    long getTimestamp();

    int write(java.io.OutputStream arg0, boolean arg1) throws java.io.IOException;

    boolean isLatestTimestamp();
}
