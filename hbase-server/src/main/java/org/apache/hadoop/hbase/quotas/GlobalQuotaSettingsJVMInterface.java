package org.apache.hadoop.hbase.quotas;

public interface GlobalQuotaSettingsJVMInterface extends QuotaSettingsJVMInterface {

    java.lang.Object getQuotaType();

    java.util.List getQuotaSettings();
}
