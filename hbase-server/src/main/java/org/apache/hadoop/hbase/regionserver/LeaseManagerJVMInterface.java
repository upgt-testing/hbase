package org.apache.hadoop.hbase.regionserver;

public interface LeaseManagerJVMInterface {

    void run();

    void closeAfterLeasesExpire();

    void cancelLease(java.lang.String arg0) throws org.apache.hadoop.hbase.regionserver.LeaseException;

    void renewLease(java.lang.String arg0) throws org.apache.hadoop.hbase.regionserver.LeaseException;

    void close();
}
