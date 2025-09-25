package org.apache.hadoop.hbase.client;

public interface QueryJVMInterface extends OperationWithAttributesJVMInterface {

    byte[] getACL();

    org.apache.hadoop.hbase.client.QueryJVMInterface setReplicaId(int arg0);

    int getReplicaId();

    org.apache.hadoop.hbase.security.visibility.AuthorizationsJVMInterface getAuthorizations() throws org.apache.hadoop.hbase.exceptions.DeserializationException;

    java.lang.Object getConsistency();

    org.apache.hadoop.hbase.filter.FilterJVMInterface getFilter();

    java.lang.Boolean getLoadColumnFamiliesOnDemandValue();

    org.apache.hadoop.hbase.client.QueryJVMInterface setLoadColumnFamiliesOnDemand(boolean arg0);

    java.lang.Object getIsolationLevel();

    org.apache.hadoop.hbase.client.QueryJVMInterface setColumnFamilyTimeRange(byte[] arg0, long arg1, long arg2);

    boolean doLoadColumnFamiliesOnDemand();

    java.util.Map getColumnFamilyTimeRange();
}
