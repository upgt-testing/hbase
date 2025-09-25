package org.apache.hadoop.hbase.http;

public interface InfoServerJVMInterface {

    void setAttribute(java.lang.String arg0, java.lang.Object arg1);

    void addUnprivilegedServlet(java.lang.String arg0, java.lang.String arg1, java.lang.Class<? extends javax.servlet.http.HttpServlet> arg2);

    void addServlet(java.lang.String arg0, java.lang.String arg1, java.lang.Class<? extends javax.servlet.http.HttpServlet> arg2);

    int getPort();

    void addPrivilegedServlet(java.lang.String arg0, java.lang.String arg1, java.lang.Class<? extends javax.servlet.http.HttpServlet> arg2);

    void stop() throws java.lang.Exception;

    void start() throws java.io.IOException;
}
