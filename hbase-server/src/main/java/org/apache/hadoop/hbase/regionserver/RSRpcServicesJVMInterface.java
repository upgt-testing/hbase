package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.ipc.HBaseRPCErrorHandlerJVMInterface;
import org.apache.hadoop.hbase.ipc.PriorityFunctionJVMInterface;
import org.apache.hadoop.hbase.conf.ConfigurationObserverJVMInterface;

public interface RSRpcServicesJVMInterface extends HBaseRPCErrorHandlerJVMInterface, PriorityFunctionJVMInterface, ConfigurationObserverJVMInterface {

    org.apache.hadoop.conf.Configuration getConfiguration();

    java.net.InetSocketAddress getSocketAddress();

    void onConfigurationChange(org.apache.hadoop.conf.Configuration arg0);

    java.lang.String getScanDetailsWithId(long arg0);

    org.apache.hadoop.hbase.ipc.RpcSchedulerJVMInterface getRpcScheduler();

    boolean checkOOME(java.lang.Throwable arg0);

    int getScannersCount();

    java.lang.Object getPriority();
}
