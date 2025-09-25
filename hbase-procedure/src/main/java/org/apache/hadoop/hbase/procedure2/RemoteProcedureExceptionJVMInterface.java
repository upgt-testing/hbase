package org.apache.hadoop.hbase.procedure2;

public interface RemoteProcedureExceptionJVMInterface extends ProcedureExceptionJVMInterface {

    java.io.IOException unwrapRemoteIOException();

    java.lang.Object convert();

    java.lang.String toString();

    java.lang.String getSource();

    java.lang.Exception unwrapRemoteException();
}
