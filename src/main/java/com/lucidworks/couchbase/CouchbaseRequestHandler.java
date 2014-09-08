package com.lucidworks.couchbase;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorChain;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.couchbase.capi.CAPIBehavior;
import com.couchbase.capi.CAPIServer;
import com.couchbase.capi.CouchbaseBehavior;


public class CouchbaseRequestHandler extends RequestHandlerBase implements SolrCoreAware {

  private static final Logger LOG = LoggerFactory.getLogger(CouchbaseRequestHandler.class);
  
  public static final String HANDLER_PARAMS = "params";
  public static final String BUCKET_MARK = "bucket";
  public static final String FIELD_MAPPING_FIELD = "fieldmappings";
  public static final String SPLITPATH_FIELD = "splitpath";
  public static final String USERNAME_FIELD = "username";
  public static final String PASSWORD_FIELD = "password";
  public static final String PORT_FIELD = "port";
  public static final String NAME_FIELD = "name";
  public static final String COMMIT_AFTER_BATCH_FIELD = "name";
  /** Select the update processor chain to use.  A RequestHandler may or may not respect this parameter */
  public static final String UPDATE_CHAIN = "update.chain";
  
  CouchbaseBehavior couchbaseBehaviour;
  CAPIBehavior capiBehaviour;
  CAPIServer server;
  private int port;
  private String username;
  private String password;
  private TypeSelector typeSelector;
  private Settings settings;
  private SolrCore core;
  private UpdateRequestProcessor processor;

  private Map<String, String> documentTypeParentFields;
  private Map<String, String> documentTypeRoutingFields;
  private Map<String,Bucket> buckets = new HashMap<String, Bucket>();

  @Override
  public void inform(SolrCore core) {
    this.core = core;
    SolrQueryRequest req = new SolrQueryRequestBase(core, new SolrParams() {
      
      @Override
      public String[] getParams(String param) {
        // TODO Auto-generated method stub
        return null;
      }
      
      @Override
      public Iterator<String> getParameterNamesIterator() {
        // TODO Auto-generated method stub
        return null;
      }
      
      @Override
      public String get(String param) {
        // TODO Auto-generated method stub
        return null;
      }
    }) {};
    SolrQueryResponse rsp = new SolrQueryResponse();
    UpdateRequestProcessorChain processorChain =
        core.getUpdateProcessingChain("");
    processor = processorChain.createProcessor(req, rsp);
  }
  
  @Override
  public void init(NamedList args) {
    super.init(args);
    Map<String,String> params = SolrParams.toMap((NamedList<String>)args.get(HANDLER_PARAMS));
    username = params.get(USERNAME_FIELD);
    password = params.get(PASSWORD_FIELD);
    port = Integer.parseInt(params.get(PORT_FIELD));
    boolean commitAfterBatch = Boolean.parseBoolean(params.get(COMMIT_AFTER_BATCH_FIELD));
    
    List<NamedList<Object>> bucketslist = args.getAll(BUCKET_MARK);
    for(NamedList<Object> bucket : bucketslist) {
      String name = (String)bucket.get(NAME_FIELD);
      String splitpath = (String)bucket.get(SPLITPATH_FIELD);
      NamedList<Object> mappingslist = (NamedList<Object>) bucket.get(FIELD_MAPPING_FIELD);
      Map<String,String> fieldmappings = SolrParams.toMap(mappingslist);
      Bucket b = new Bucket(name, splitpath, fieldmappings);
      buckets.put(name, b);
    }
    
    settings = new Settings();
    this.documentTypeParentFields = settings.getByPrefix("couchbase.documentTypeParentFields.");
    for (String key: documentTypeParentFields.keySet()) {
        String parentField = documentTypeParentFields.get(key);
        LOG.info("Using field {} as parent for type {}", parentField, key);
    }

    this.documentTypeRoutingFields = settings.getByPrefix("couchbase.documentTypeRoutingFields.");
    for (String key: documentTypeRoutingFields.keySet()) {
        String routingField = documentTypeRoutingFields.get(key);
        LOG.info("Using field {} as routing for type {}", routingField, key);
    }
    typeSelector = new DefaultTypeSelector();
    typeSelector.configure(settings);
    couchbaseBehaviour = new SolrCouchbaseBehaviour();
    capiBehaviour = new SolrCAPIBehaviour(this, typeSelector, documentTypeParentFields, documentTypeRoutingFields, commitAfterBatch);
    
  }
  
  @Override
  public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp)
      throws Exception {
    SolrParams params = req.getParams();
    String q = params.get(CommonParams.Q);
    
    q = q.toLowerCase();
    q = q.trim();
    
    switch(q) {
    case "start"  :
      startCouchbasePlugin();
      break;
    case "stop" :
      stopCouchbasePlugin();
      break;
    }
  }

  @Override
  public String getDescription() {
    return "Couchbase plugin";
  }
  
  @Override
  public String getSource() { return null; }

  public void startCouchbasePlugin() {
    server = new CAPIServer(capiBehaviour, couchbaseBehaviour, port, username, password);
    //TODO fix this
    try{
      server.start();
    } catch (Exception e) {
      
    }
    port = server.getPort();
//    LOG.info(String.format("CAPIServer started on port %d", port));
  }
  
  public void stopCouchbasePlugin() {
    try {
      server.stop();
    } catch (Exception e) {
      LOG.error("Error while stopping CAPI server.", e);
    }
  }
  
  public UpdateRequestProcessor getProcessor() {
    return this.processor;
  }
  
  public SolrCore getCore() {
    return this.core;
  }
  
  public Bucket getBucket(String name) {
    return buckets.get(name);
  }
}
