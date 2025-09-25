package org.apache.hadoop.hbase.nio;

public interface ByteBuffJVMInterface extends HBaseReferenceCountedJVMInterface {

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface position(int arg0);

    int read(java.nio.channels.FileChannel arg0, long arg1) throws java.io.IOException;

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface putInt(int arg0);

    byte get(int arg0);

    byte[] array();

    short getShort(int arg0);

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface slice();

    boolean hasRemaining();

    java.nio.ByteBuffer[] nioByteBuffers();

    byte get();

    org.apache.hadoop.hbase.nio.RefCntJVMInterface getRefCnt();

    short getShortAfterPosition(int arg0);

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface reset();

    int limit();

    java.nio.ByteBuffer asSubByteBuffer(int arg0);

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface put(byte[] arg0, int arg1, int arg2);

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface touch();

    byte getByteAfterPosition(int arg0);

    int getInt();

    boolean hasArray();

    int refCnt();

    int remaining();

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface limit(int arg0);

    int getInt(int arg0);

    void get(int arg0, byte[] arg1, int arg2, int arg3);

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface skip(int arg0);

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface mark();

    boolean release();

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface touch(java.lang.Object arg0);

    byte[] toBytes(int arg0, int arg1);

    void get(byte[] arg0, int arg1, int arg2);

    int write(java.nio.channels.FileChannel arg0, long arg1) throws java.io.IOException;

    int getIntAfterPosition(int arg0);

    void get(java.nio.ByteBuffer arg0, int arg1, int arg2);

    byte[] toBytes();

    long getLong();

    int arrayOffset();

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface putLong(long arg0);

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface put(byte[] arg0);

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface put(int arg0, byte arg1);

    long getLong(int arg0);

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface put(byte arg0);

    int capacity();

    java.lang.String toString();

    long getLongAfterPosition(int arg0);

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface moveBack(int arg0);

    int position();

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface rewind();

    int read(java.nio.channels.ReadableByteChannel arg0) throws java.io.IOException;

    short getShort();

    void get(byte[] arg0);

    org.apache.hadoop.hbase.nio.ByteBuffJVMInterface duplicate();
}
