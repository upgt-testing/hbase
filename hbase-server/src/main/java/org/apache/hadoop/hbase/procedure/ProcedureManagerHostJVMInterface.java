package org.apache.hadoop.hbase.procedure;

public interface ProcedureManagerHostJVMInterface<E> {

    void loadProcedures(org.apache.hadoop.conf.Configuration arg0);

    E loadInstance(java.lang.Class<?> arg0) throws java.io.IOException;

    void register(E arg0);

    java.util.Set getProcedureManagers();
}
