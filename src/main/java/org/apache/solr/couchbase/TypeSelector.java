package org.apache.solr.couchbase;


public interface TypeSelector {
    void configure(Settings settings);
    String getType(String index, String docId);
}
