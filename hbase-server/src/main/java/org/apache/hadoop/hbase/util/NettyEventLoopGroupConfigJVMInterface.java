package org.apache.hadoop.hbase.util;

public interface NettyEventLoopGroupConfigJVMInterface {

    java.lang.Class serverChannelClass();

    java.lang.Class clientChannelClass();

    java.lang.Object group();
}
