package org.apache.hadoop.hbase.client;

public interface OperationJVMInterface {

    java.util.Map toMap();

    java.lang.String toJSON(int arg0) throws java.io.IOException;

    java.lang.String toString();

    java.util.Map getFingerprint();

    java.lang.String toString(int arg0);

    java.lang.String toJSON() throws java.io.IOException;

    java.util.Map toMap(int arg0);
}
