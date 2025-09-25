package org.apache.hadoop.hbase.security;

public interface UserJVMInterface {

    int hashCode();

    org.apache.hadoop.security.token.Token getToken(java.lang.String arg0, java.lang.String arg1) throws java.io.IOException;

    java.lang.String[] getGroupNames();

    boolean equals(java.lang.Object arg0);

    org.apache.hadoop.security.UserGroupInformation getUGI();

    java.lang.String toString();

    void addToken(org.apache.hadoop.security.token.Token<? extends org.apache.hadoop.security.token.TokenIdentifier> arg0);

    java.lang.String getName();

    boolean isLoginFromKeytab();

    java.lang.String getShortName();

    java.util.Collection getTokens();
}
