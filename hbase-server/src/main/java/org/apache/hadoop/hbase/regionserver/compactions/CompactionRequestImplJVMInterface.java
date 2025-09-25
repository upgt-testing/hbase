package org.apache.hadoop.hbase.regionserver.compactions;

public interface CompactionRequestImplJVMInterface extends CompactionRequestJVMInterface {

    void setPriority(int arg0);

    int hashCode();

    void setIsMajor(boolean arg0, boolean arg1);

    boolean equals(java.lang.Object arg0);

    boolean isOffPeak();

    boolean isAfterSplit();

    java.lang.String toString();

    boolean isMajor();

    long getSize();

    void setAfterSplit(boolean arg0);

    long getSelectionTime();

    void setOffPeak(boolean arg0);

    void setWriterCreationTracker(java.util.function.Consumer<org.apache.hadoop.fs.Path> arg0);

    java.lang.Object getTracker();

    java.util.function.Consumer getWriterCreationTracker();

    void setDescription(java.lang.String arg0, java.lang.String arg1);

    boolean isAllFiles();

    int getPriority();

    java.util.Collection getFiles();
}
