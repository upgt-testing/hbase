package org.apache.hadoop.hbase.nio;

public interface SingleByteBuffJVMInterface extends ByteBuffJVMInterface {

    int write(java.nio.channels.FileChannel arg0, long arg1) throws java.io.IOException;

    void get(byte[] arg0, int arg1, int arg2);

    int getIntAfterPosition(int arg0);

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface position(int arg0);

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface putInt(int arg0);

    int read(java.nio.channels.FileChannel arg0, long arg1) throws java.io.IOException;

    byte[] array();

    void get(java.nio.ByteBuffer arg0, int arg1, int arg2);

    byte get(int arg0);

    short getShort(int arg0);

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface slice();

    boolean hasRemaining();

    long getLong();

    int arrayOffset();

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface putLong(long arg0);

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface put(byte[] arg0);

    java.nio.ByteBuffer[] nioByteBuffers();

    long getLong(int arg0);

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface put(int arg0, byte arg1);

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface put(byte arg0);

    short getShortAfterPosition(int arg0);

    byte get();

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface reset();

    int hashCode();

    java.nio.ByteBuffer asSubByteBuffer(int arg0);

    int limit();

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface put(byte[] arg0, int arg1, int arg2);

    boolean equals(java.lang.Object arg0);

    int capacity();

    byte getByteAfterPosition(int arg0);

    int getInt();

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface retain();

    int remaining();

    boolean hasArray();

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface limit(int arg0);

    int getInt(int arg0);

    void get(int arg0, byte[] arg1, int arg2, int arg3);

    long getLongAfterPosition(int arg0);

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface skip(int arg0);

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface moveBack(int arg0);

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface mark();

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface rewind();

    int position();

    int read(java.nio.channels.ReadableByteChannel arg0) throws java.io.IOException;

    byte[] toBytes(int arg0, int arg1);

    void get(byte[] arg0);

    org.apache.hadoop.hbase.nio.SingleByteBuffJVMInterface duplicate();

    short getShort();
}
