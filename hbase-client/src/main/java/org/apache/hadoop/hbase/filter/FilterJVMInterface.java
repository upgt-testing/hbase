package org.apache.hadoop.hbase.filter;

public interface FilterJVMInterface {

    boolean isFamilyEssential(byte[] arg0) throws java.io.IOException;

    boolean isReversed();

    boolean filterAllRemaining() throws java.io.IOException;

    void setReversed(boolean arg0);

    boolean filterRowKey(byte[] arg0, int arg1, int arg2) throws java.io.IOException;

    byte[] toByteArray() throws java.io.IOException;

    boolean filterRow() throws java.io.IOException;

    void reset() throws java.io.IOException;

    boolean hasFilterRow();
}
