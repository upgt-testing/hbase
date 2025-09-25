package org.apache.hadoop.hbase.quotas;

public interface QuotaSettingsJVMInterface {

    java.lang.Object getQuotaType();

    org.apache.hadoop.hbase.TableNameJVMInterface getTableName();

    java.lang.String getUserName();

    java.lang.String getNamespace();

    java.lang.String getRegionServer();
}
