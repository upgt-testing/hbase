package org.apache.hadoop.hbase.coprocessor;

public interface CoprocessorHostJVMInterface<C, E> {

    void load(java.lang.Class<? extends C> arg0, int arg1, org.apache.hadoop.conf.Configuration arg2) throws java.io.IOException;

    E createEnvironment(C arg0, int arg1, int arg2, org.apache.hadoop.conf.Configuration arg3);

    E findCoprocessorEnvironment(java.lang.String arg0);

    void shutdown(E arg0);

    C checkAndGetInstance(java.lang.Class<?> arg0) throws java.lang.InstantiationException, java.lang.IllegalAccessException;

    E load(org.apache.hadoop.fs.Path arg0, java.lang.String arg1, int arg2, org.apache.hadoop.conf.Configuration arg3, java.lang.String[] arg4) throws java.io.IOException;

    E load(org.apache.hadoop.fs.Path arg0, java.lang.String arg1, int arg2, org.apache.hadoop.conf.Configuration arg3) throws java.io.IOException;

    E checkAndLoadInstance(java.lang.Class<?> arg0, int arg1, org.apache.hadoop.conf.Configuration arg2) throws java.io.IOException;

    java.util.Set getCoprocessors();
}
