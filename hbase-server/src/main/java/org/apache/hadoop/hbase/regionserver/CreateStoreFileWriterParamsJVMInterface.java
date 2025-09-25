package org.apache.hadoop.hbase.regionserver;

public interface CreateStoreFileWriterParamsJVMInterface {

    org.apache.hadoop.hbase.regionserver.CreateStoreFileWriterParamsJVMInterface writerCreationTracker(java.util.function.Consumer<org.apache.hadoop.fs.Path> arg0);

    java.util.function.Consumer writerCreationTracker();

    boolean includesTag();

    org.apache.hadoop.hbase.regionserver.CreateStoreFileWriterParamsJVMInterface includesTag(boolean arg0);

    org.apache.hadoop.hbase.regionserver.CreateStoreFileWriterParamsJVMInterface fileStoragePolicy(java.lang.String arg0);

    org.apache.hadoop.hbase.regionserver.CreateStoreFileWriterParamsJVMInterface totalCompactedFilesSize(long arg0);

    java.lang.Object compression();

    java.lang.String fileStoragePolicy();

    org.apache.hadoop.hbase.regionserver.CreateStoreFileWriterParamsJVMInterface maxKeyCount(long arg0);

    boolean includeMVCCReadpoint();

    org.apache.hadoop.hbase.regionserver.CreateStoreFileWriterParamsJVMInterface shouldDropBehind(boolean arg0);

    boolean isCompaction();

    boolean shouldDropBehind();

    org.apache.hadoop.hbase.regionserver.CreateStoreFileWriterParamsJVMInterface isCompaction(boolean arg0);

    org.apache.hadoop.hbase.regionserver.CreateStoreFileWriterParamsJVMInterface includeMVCCReadpoint(boolean arg0);

    long maxKeyCount();

    long totalCompactedFilesSize();
}
