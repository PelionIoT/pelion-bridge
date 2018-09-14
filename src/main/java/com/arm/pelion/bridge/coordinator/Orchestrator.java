/**
 * @file Orchestrator.java
 * @brief Orchestrator for the Pelion bridge
 * @author Doug Anson
 * @version 1.0
 * @see
 *
 * Copyright 2018. ARM Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.arm.pelion.bridge.coordinator;

// Processors
import com.arm.pelion.bridge.coordinator.processors.arm.PelionProcessor;
import com.arm.pelion.bridge.coordinator.processors.factories.WatsonIoTPeerProcessorFactory;
import com.arm.pelion.bridge.coordinator.processors.factories.MSIoTHubPeerProcessorFactory;
import com.arm.pelion.bridge.coordinator.processors.factories.AWSIoTPeerProcessorFactory;
import com.arm.pelion.bridge.coordinator.processors.factories.GenericMQTTPeerProcessorFactory;
import com.arm.pelion.bridge.core.ApiResponse;
import com.arm.pelion.bridge.coordinator.processors.factories.GoogleCloudPeerProcessorFactory;

// Core
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.pelion.bridge.core.ErrorLogger;
import com.arm.pelion.bridge.json.JSONGenerator;
import com.arm.pelion.bridge.json.JSONParser;
import com.arm.pelion.bridge.json.JSONGeneratorFactory;
import com.arm.pelion.bridge.preferences.PreferenceManager;
import com.arm.pelion.bridge.transport.HttpTransport;
import java.util.ArrayList;
import java.util.Map;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.arm.pelion.bridge.data.DatabaseConnector;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;
import com.arm.pelion.bridge.servlet.Manager;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PelionProcessorInterface;
import com.arm.pelion.bridge.coordinator.processors.core.EndpointTypeManager;
import com.arm.pelion.bridge.coordinator.processors.factories.SAMPLEPeerProcessorFactory;
import com.arm.pelion.bridge.health.HealthCheckServiceProvider;
import com.arm.pelion.bridge.health.interfaces.HealthCheckServiceInterface;
import com.arm.pelion.bridge.health.interfaces.HealthStatisticListenerInterface;

/**
 * This the primary orchestrator for the pelion bridge
 *
 * @author Doug Anson
 */
public class Orchestrator implements PelionProcessorInterface, PeerProcessorInterface, HealthStatisticListenerInterface {
    // Default Health Check Service Provider Sleep time in MS
    private static final int DEF_HEALTH_CHECK_SERVICE_PROVIDER_SLEEP_TIME_MS = (60000 * 10);    // 10 minutes
    
    // Health Stats Key 
    public static final String HEALTH_STATS_KEY = "[HEALTH_STATS]";        
    
    // database table delimiter
    private static String DEF_TABLENAME_DELIMITER = "_";

    // our servlet
    private final HttpServlet m_servlet = null;

    // Logging and preferences...
    private ErrorLogger m_error_logger = null;
    private PreferenceManager m_preference_manager = null;

    // Pelion processor (1 only...)
    private PelionProcessorInterface m_pelion_processor = null;

    // Peer processor list (n-way...default is 1 though...)
    private ArrayList<PeerProcessorInterface> m_peer_processor_list = null;
    
    // Health Check Services Provider/Manager
    private boolean m_enable_health_checks = true;                 // true: enabled, false: disabled
    private HealthCheckServiceProvider m_health_check_service_provider = null;
    private Thread m_health_check_service_provider_thread = null;
    
    // Health Check Services Provider Sleep time (in ms)
    private int m_health_check_service_provider_sleep_time_ms = DEF_HEALTH_CHECK_SERVICE_PROVIDER_SLEEP_TIME_MS;
    
    // Health Check Shadow Count
    private int m_shadow_count = 0;

    // our HTTP transport interface
    private HttpTransport m_http = null;

    // JSON support
    private JSONGeneratorFactory m_json_factory = null;
    private JSONGenerator m_json_generator = null;
    private JSONParser m_json_parser = null;
    
    // listeners initialized status...
    private boolean m_listeners_initialized = false;
    
    // persist preferences and configuration
    private DatabaseConnector m_db = null;
    private String m_tablename_delimiter = null;
    private boolean m_is_master_node = true;        // default is to be a master node
    
    // our primary manager
    private Manager m_manager = null;

    // primary constructor
    public Orchestrator(ErrorLogger error_logger, PreferenceManager preference_manager) {
        // save the error handler
        this.m_error_logger = error_logger;
        this.m_preference_manager = preference_manager;
        
        // we are parent
        this.m_error_logger.setParent(this);
        
        // get our master node designation
        this.m_is_master_node = this.m_preference_manager.booleanValueOf("is_master_node");

        // initialize the database connector
        boolean enable_distributed_db_cache = this.preferences().booleanValueOf("enable_distributed_db_cache");
        if (enable_distributed_db_cache == true) {
            this.m_tablename_delimiter = this.preferences().valueOf("distributed_db_tablename_delimiter");
            if (this.m_tablename_delimiter == null || this.m_tablename_delimiter.length() == 0) {
                this.m_tablename_delimiter = DEF_TABLENAME_DELIMITER;
            }
            String db_ip_address = this.preferences().valueOf("distributed_db_ip_address");
            int db_port = this.preferences().intValueOf("distributed_db_port");
            String db_username = this.preferences().valueOf("distributed_db_username");
            String db_pw = this.preferences().valueOf("distributed_db_password");
            this.m_db = new DatabaseConnector(this,db_ip_address,db_port,db_username,db_pw);
        }
        
        // finalize the preferences manager
        this.m_preference_manager.initializeCache(this.m_db,this.m_is_master_node);
        
        // JSON Factory
        this.m_json_factory = JSONGeneratorFactory.getInstance();

        // create the JSON Generator
        this.m_json_generator = this.m_json_factory.newJsonGenerator();

        // create the JSON Parser
        this.m_json_parser = this.m_json_factory.newJsonParser();

        // build out the HTTP transport
        this.m_http = new HttpTransport(this.m_error_logger, this.m_preference_manager);

        // We always create the Pelion processor (1 only)
        this.m_pelion_processor = new PelionProcessor(this, this.m_http);
      
        // initialize our peer processors... (n-way... but default is just 1...)
        this.initPeerProcessorList();
        
        // Get the health check service provider sleep time
        if (this.m_enable_health_checks == true) {
            this.m_health_check_service_provider_sleep_time_ms = preferences().intValueOf("heath_check_sleep_time_ms");
            if (this.m_health_check_service_provider_sleep_time_ms <= 0) {
                this.m_health_check_service_provider_sleep_time_ms = DEF_HEALTH_CHECK_SERVICE_PROVIDER_SLEEP_TIME_MS;
            }

            // DEBUG
            this.errorLogger().warning("Orchestrator: Stats Check Sleep Interval (ms): " + this.m_health_check_service_provider_sleep_time_ms);

            // create our health check service provider and its runtime thread...
            this.m_health_check_service_provider = new HealthCheckServiceProvider(this,this.m_health_check_service_provider_sleep_time_ms); 
            this.m_health_check_service_provider.initialize();
            this.m_health_check_service_provider.addListener(this);
        }
        else {
            // not enabled
            this.errorLogger().warning("Orchestrator: Stats Checking DISABLED");
        }
       
        // start device discovery in Pelion...
        this.initDeviceDiscovery();
    }
    
    // start the health check provider thread to monitor and provide statistics
    public void startStatisticsMonitoring() {
        if (this.m_enable_health_checks == true) {
            try {
                // DEBUG
                this.errorLogger().warning("Orchestrator: Statistics and health monitoring starting...");
                this.m_health_check_service_provider_thread = new Thread(this.m_health_check_service_provider);
                if (this.m_health_check_service_provider_thread != null) {
                    this.m_health_check_service_provider_thread.start();
                }
            }
            catch (Exception ex) {
                this.errorLogger().critical("Orchestrator: Exception caught while starting health check provider: " + ex.getMessage());
            }
        }
    }
    
    // get the health check provider
    public HealthCheckServiceInterface getHealthCheckServiceProvider() {
        return (HealthCheckServiceInterface)this.m_health_check_service_provider;
    }
    
    // set our manager
    public void setManager(Manager manager) {
        this.m_manager = manager;
    }
    
    // shutdown/reset our instance
    public void reset() {
        if (this.m_manager != null) {
            this.m_manager.reset();
        }
    }
    
    // get the tablename delimiter
    public String getTablenameDelimiter() {
        return this.m_tablename_delimiter;
    }
    
    // get the database connector
    public DatabaseConnector getDatabaseConnector() {
        return this.m_db;
    }

    // initialize our peer processor
    private void initPeerProcessorList() {
        // initialize the list
        this.m_peer_processor_list = new ArrayList<>();

        // add peer processors
        if (this.ibmPeerEnabled()) {
            // IBM WatsonIoT/MQTT
            this.errorLogger().info("Orchestrator: Adding IBM WatsonIoT MQTT Processor");
            this.m_peer_processor_list.add(WatsonIoTPeerProcessorFactory.createPeerProcessor(this, this.m_http));
        }
        if (this.msPeerEnabled()) {
            // MS IoTHub/MQTT
            this.errorLogger().info("Orchestrator: Adding MS IoTHub MQTT Processor");
            this.m_peer_processor_list.add(MSIoTHubPeerProcessorFactory.createPeerProcessor(this, this.m_http));
        }
        if (this.awsPeerEnabled()) {
            // Amazon AWSIoT/MQTT
            this.errorLogger().info("Orchestrator: Adding AWSIoT MQTT Processor");
            this.m_peer_processor_list.add(AWSIoTPeerProcessorFactory.createPeerProcessor(this, this.m_http));
        }
        if (this.googleCloudPeerEnabled()) {
            // Google CloudIoT/MQTT
            this.errorLogger().info("Orchestrator: Adding Google CloudIoT MQTT Processor");
            this.m_peer_processor_list.add(GoogleCloudPeerProcessorFactory.createPeerProcessor(this, this.m_http));
        }
        if (this.genericMQTTPeerEnabled()) {
            // Generic MQTT
            this.errorLogger().info("Orchestrator: Adding Generic MQTT Processor");
            this.m_peer_processor_list.add(GenericMQTTPeerProcessorFactory.createPeerProcessor(this, this.m_http));
        }
        
        // TEMPLATE FOR SAMPLE Peer
        if (this.SAMPLEPeerEnabled()) {
            // SAMPLE
            this.errorLogger().info("Orchestrator: Adding SAMPLE Processor");
            this.m_peer_processor_list.add(SAMPLEPeerProcessorFactory.createPeerProcessor(this, this.m_http));
        }
    }
    
    // use SAMPLE peer processor?
    private Boolean SAMPLEPeerEnabled() {
        return (this.preferences().booleanValueOf("SAMPLE_enable_addon"));
    }

    // use IBM peer processor?
    private Boolean ibmPeerEnabled() {
        return (this.preferences().booleanValueOf("enable_iotf_addon"));
    }

    // use MS peer processor?
    private Boolean msPeerEnabled() {
        return (this.preferences().booleanValueOf("enable_iot_event_hub_addon"));
    }

    // use AWS peer processor?
    private Boolean awsPeerEnabled() {
        return (this.preferences().booleanValueOf("enable_aws_iot_gw_addon"));
    }
    
    // use Google Cloud peer processor?
    private Boolean googleCloudPeerEnabled() {
        return (this.preferences().booleanValueOf("enable_google_cloud_addon"));
    }

    // use generic MQTT peer processor?
    private Boolean genericMQTTPeerEnabled() {
        return this.preferences().booleanValueOf("enable_generic_mqtt_processor");
    }

    // are the listeners active?
    public boolean peerListenerActive() {
        return this.m_listeners_initialized;
    }

    // initialize peer listener
    public void initPeerListener() {
        if (!this.m_listeners_initialized) {
            // MQTT Listener
            for (int i = 0; i < this.m_peer_processor_list.size(); ++i) {
                this.m_peer_processor_list.get(i).initListener();
            }
            this.m_listeners_initialized = true;
        }
    }

    // stop the peer listener
    public void stopPeerListener() {
        if (this.m_listeners_initialized) {
            // MQTT Listener
            for (int i = 0; i < this.m_peer_processor_list.size(); ++i) {
                this.m_peer_processor_list.get(i).stopListener();
            }
            this.m_listeners_initialized = false;
        }
    }

    // initialize the Pelion webhook
    public void initializeDeviceServerWebhook() {
        if (this.m_pelion_processor != null) {
            this.m_pelion_processor.setWebhook();
        }
    }

    // process the Pelion inbound message
    public void processIncomingDeviceServerMessage(HttpServletRequest request, HttpServletResponse response) {
        // process the received REST message
        //this.errorLogger().info("events (REST-" + request.getMethod() + "): " + request.getRequestURI());
        this.pelion_processor().processNotificationMessage(request, response);
    }

    // get the HttpServlet
    public HttpServlet getServlet() {
        return this.m_servlet;
    }

    // get the ErrorLogger
    public ErrorLogger errorLogger() {
        return this.m_error_logger;
    }

    // get he Preferences manager
    public final PreferenceManager preferences() {
        return this.m_preference_manager;
    }

    // get the peer processor list
    public ArrayList<PeerProcessorInterface> peer_processor_list() {
        return this.m_peer_processor_list;
    }

    // get the pelion processor
    public PelionProcessorInterface pelion_processor() {
        return this.m_pelion_processor;
    }

    // get the JSON parser instance
    public JSONParser getJSONParser() {
        return this.m_json_parser;
    }

    // get the JSON generation instance
    public JSONGenerator getJSONGenerator() {
        return this.m_json_generator;
    }

    // get our ith peer processor
    private PeerProcessorInterface peerProcessor(int index) {
        if (index >= 0 && this.m_peer_processor_list != null && index < this.m_peer_processor_list.size()) {
            return this.m_peer_processor_list.get(index);
        }
        return null;
    }

    // Message: API Request
    @Override
    public ApiResponse processApiRequestOperation(String uri,String data,String options,String verb,int request_id,String api_key,String caller_id, String content_type) {
        if (this.m_pelion_processor != null) {
            return this.pelion_processor().processApiRequestOperation(uri, data, options, verb, request_id, api_key, caller_id, content_type);
        }
        return null;
    }
    
    // Message: notifications
    @Override
    public void processNotificationMessage(HttpServletRequest request, HttpServletResponse response) {
        if (this.m_pelion_processor != null) {
            this.pelion_processor().processNotificationMessage(request, response);
        }
    }
    
    // Message: device-deletions (mbed Cloud)
    @Override
    public void processDeviceDeletions(String[] endpoints) {
        if (this.m_pelion_processor != null) {
            this.pelion_processor().processDeviceDeletions(endpoints);
        }
    }

    // Message: de-registrations
    @Override
    public void processDeregistrations(String[] endpoints) {
        if (this.m_pelion_processor != null) {
            this.pelion_processor().processDeregistrations(endpoints);
        }
    }
    
    // Message: registrations-expired
    @Override
    public void processRegistrationsExpired(String[] endpoints) {
        if (this.m_pelion_processor != null) {
            this.pelion_processor().processRegistrationsExpired(endpoints);
        }
    }

    @Override
    public String processEndpointResourceOperation(String verb, String ep_name, String uri, String value, String options) {
        if (this.m_pelion_processor != null) {
            return this.pelion_processor().processEndpointResourceOperation(verb, ep_name, uri, value, options);
        }
        return null;
    }

    @Override
    public boolean setWebhook() {
        if (this.m_pelion_processor != null) {
            return this.pelion_processor().setWebhook();
        }
        return false;
    }

    @Override
    public boolean resetWebhook() {
        if (this.m_pelion_processor != null) {
            return this.pelion_processor().resetWebhook();
        }
        return false;
    }
    
    @Override
    public void removeWebhook() {
        if (this.m_pelion_processor != null) {
            this.pelion_processor().removeWebhook();
        }
    }

    @Override
    public void pullDeviceMetadata(Map endpoint, AsyncResponseProcessor processor) {
        if (this.m_pelion_processor != null) {
            this.pelion_processor().pullDeviceMetadata(endpoint, processor);
        }
    }

    // PeerProcessorInterface Orchestration
    @Override
    public String createAuthenticationHash() {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; this.m_peer_processor_list != null && i < this.m_peer_processor_list.size(); ++i) {
            buf.append(this.peerProcessor(i).createAuthenticationHash());
        }
        return buf.toString();
    }

    @Override
    public void recordAsyncResponse(String response, String uri, Map ep, AsyncResponseProcessor processor) {
        for (int i = 0; this.m_peer_processor_list != null && i < this.m_peer_processor_list.size(); ++i) {
            this.peerProcessor(i).recordAsyncResponse(response, uri, ep, processor);
        }
    }

    // Message: registration
    @Override
    public void processNewRegistration(Map message) {
        for (int i = 0; this.m_peer_processor_list != null && i < this.m_peer_processor_list.size(); ++i) {
            this.peerProcessor(i).processNewRegistration(message);
        }
    }

    // Message: reg-updates
    @Override
    public void processReRegistration(Map message) {
        for (int i = 0; this.m_peer_processor_list != null && i < this.m_peer_processor_list.size(); ++i) {
            this.peerProcessor(i).processReRegistration(message);
        }
    }
    
    // Message: device-deletions (mbed Cloud)
    @Override
    public String[] processDeviceDeletions(Map message) {
        ArrayList<String> deletions = new ArrayList<>();
        
        for (int i = 0; this.m_peer_processor_list != null && i < this.m_peer_processor_list.size(); ++i) {
            String[] ith_deletions = this.peerProcessor(i).processDeviceDeletions(message);
            for (int j = 0; ith_deletions != null && j < ith_deletions.length; ++j) {
                boolean add = deletions.add(ith_deletions[j]);
            }
        }
        
        String[] deletion_str_array = new String[deletions.size()];
        return deletions.toArray(deletion_str_array);
    }

    // Message: de-registrations
    @Override
    public String[] processDeregistrations(Map message) {
        ArrayList<String> deregistrations = new ArrayList<>();
            
        // loop through the list and process the de-registrations
        for (int i = 0; this.m_peer_processor_list != null && i < this.m_peer_processor_list.size(); ++i) {
            String[] ith_deregistrations = this.peerProcessor(i).processDeregistrations(message);
            for (int j = 0; ith_deregistrations != null && j < ith_deregistrations.length; ++j) {
                boolean add = deregistrations.add(ith_deregistrations[j]);
            }
        }
        
        String[] dereg_str_array = new String[deregistrations.size()];
        return deregistrations.toArray(dereg_str_array);
    }

    // Message: registrations-expired
    @Override
    public String[] processRegistrationsExpired(Map message) {
        ArrayList<String> registrations_expired = new ArrayList<>();
        
        // only if devices are removed on de-regsistration 
        for (int i = 0; this.m_peer_processor_list != null && i < this.m_peer_processor_list.size(); ++i) {
            String[] ith_reg_expired = this.peerProcessor(i).processRegistrationsExpired(message);
            for (int j = 0; ith_reg_expired != null && j < ith_reg_expired.length; ++j) {
                boolean add = registrations_expired.add(ith_reg_expired[j]);
            }
        }
        
        String[] regexpired_str_array = new String[registrations_expired.size()];
        return registrations_expired.toArray(regexpired_str_array);
    }
    
    // complete new device registration
    @Override
    public void completeNewDeviceRegistration(Map message) {
        for (int i = 0; this.m_peer_processor_list != null && i < this.m_peer_processor_list.size(); ++i) {
            this.peerProcessor(i).completeNewDeviceRegistration(message);
        }
    }

    @Override
    public void processAsyncResponses(Map message) {
        for (int i = 0; this.m_peer_processor_list != null && i < this.m_peer_processor_list.size(); ++i) {
            this.peerProcessor(i).processAsyncResponses(message);
        }
    }

    @Override
    public void processNotification(Map message) {
        for (int i = 0; this.m_peer_processor_list != null && i < this.m_peer_processor_list.size(); ++i) {
            this.peerProcessor(i).processNotification(message);
        }
    }
    
    @Override
    public void initListener() {
        for (int i = 0; this.m_peer_processor_list != null && i < this.m_peer_processor_list.size(); ++i) {
            this.peerProcessor(i).initListener();
        }
    }

    @Override
    public void stopListener() {
        for (int i = 0; this.m_peer_processor_list != null && i < this.m_peer_processor_list.size(); ++i) {
            this.peerProcessor(i).stopListener();
        }
    }

    @Override
    public boolean deviceRemovedOnDeRegistration() {
        if (this.pelion_processor() != null) {
            return this.pelion_processor().deviceRemovedOnDeRegistration();
        }
        
        // default is false
        return false;
    }
    
    // init any device discovery
    @Override
    public void initDeviceDiscovery() {
        this.pelion_processor().initDeviceDiscovery();
    }

    // orchestrator processing the publication of statistics 
    @Override
    public void publish(String json) {
       // dump to error logger with a KEY that the properties-editor will detect
       this.errorLogger().critical(HEALTH_STATS_KEY + json);
       
       // note in log so that log moves...
       this.errorLogger().warning("Health Stats: Updated (OK).");
    }
    
    // get the active thread count
    public int getActiveThreadCount() {
        if (this.m_manager != null) {
            return this.m_manager.getActiveThreadCount();
        }
        else {
            // log... but not to worry... will get set later...
            this.errorLogger().info("Orchestrator: Manager is NULL for getting active thread count (OK).");
        }
        return 1;
    }
    
    // increment the shadow count
    public void incrementShadowCount() {
        this.m_shadow_count += 1;
    }
    
    // get the shadow count
    public int getShadowCount() {
        return this.m_shadow_count;
    }

    // unused
    @Override
    public EndpointTypeManager getEndpointTypeManager() {
        if (this.m_peer_processor_list.size() > 0) {
            // return just the first one... they are all the same... 
            return this.m_peer_processor_list.get(0).getEndpointTypeManager();
        }
        return null;
    }
}