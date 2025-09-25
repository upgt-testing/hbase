package org.apache.hadoop.hbase.zookeeper;

public interface ZKNodeTrackerJVMInterface extends ZKListenerJVMInterface {

    byte[] getData(boolean arg0);

    byte[] blockUntilAvailable() throws java.lang.InterruptedException;

    boolean checkIfBaseNodeAvailable();

    java.lang.String getNode();

    java.lang.String toString();

    byte[] blockUntilAvailable(long arg0, boolean arg1) throws java.lang.InterruptedException;

    void nodeDataChanged(java.lang.String arg0);

    void nodeCreated(java.lang.String arg0);

    void stop();

    void nodeDeleted(java.lang.String arg0);

    void start();
}
