/**
 * @file    orchestrator.java
 * @brief orchestrator for the connector bridge
 * @author Doug Anson
 * @version 1.0
 * @see
 *
 * Copyright 2015. ARM Ltd. All rights reserved.
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
package com.arm.connector.bridge.coordinator;

import com.arm.connector.bridge.console.ConsoleManager;

// Interfaces
import com.arm.connector.bridge.coordinator.processors.interfaces.PeerInterface;

// Processors
import com.arm.connector.bridge.coordinator.processors.arm.mbedDeviceServerProcessor;
import com.arm.connector.bridge.coordinator.processors.arm.GenericMQTTProcessor;
import com.arm.connector.bridge.coordinator.processors.sample.Sample3rdPartyProcessor;
import com.arm.connector.bridge.coordinator.processors.ibm.WatsonIoTPeerProcessorFactory;
import com.arm.connector.bridge.coordinator.processors.ms.MSIoTHubPeerProcessorFactory;
import com.arm.connector.bridge.coordinator.processors.aws.AWSIoTPeerProcessorFactory;
import com.arm.connector.bridge.coordinator.processors.core.ApiResponse;
import com.arm.connector.bridge.coordinator.processors.google.GoogleCloudPeerProcessorFactory;

// Core
import com.arm.connector.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.connector.bridge.coordinator.processors.interfaces.SubscriptionManager;
import com.arm.connector.bridge.core.ErrorLogger;
import com.arm.connector.bridge.json.JSONGenerator;
import com.arm.connector.bridge.json.JSONParser;
import com.arm.connector.bridge.json.JSONGeneratorFactory;
import com.arm.connector.bridge.preferences.PreferenceManager;
import com.arm.connector.bridge.transport.HttpTransport;
import java.util.ArrayList;
import java.util.Map;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.arm.connector.bridge.coordinator.processors.interfaces.mbedDeviceServerInterface;
import com.arm.connector.bridge.data.DatabaseConnector;

/**
 * This the primary orchestrator for the connector bridge
 *
 * @author Doug Anson
 */
public class Orchestrator implements mbedDeviceServerInterface, PeerInterface {
    private static String DEF_TABLENAME_DELIMITER = "_";

    private final HttpServlet m_servlet = null;

    private ErrorLogger m_error_logger = null;
    private PreferenceManager m_preference_manager = null;

    // mDS processor
    private mbedDeviceServerInterface m_mbed_device_server_processor = null;

    // Peer processor list
    private ArrayList<PeerInterface> m_peer_processor_list = null;

    private ConsoleManager m_console_manager = null;

    private HttpTransport m_http = null;

    private JSONGeneratorFactory m_json_factory = null;
    private JSONGenerator m_json_generator = null;
    private JSONParser m_json_parser = null;
    private boolean m_listeners_initialized = false;

    private String m_mds_domain = null;
    
    private DatabaseConnector m_db = null;
    private String m_tablename_delimiter = null;
    private boolean m_is_master_node = true;        // default is to be a master node

    public Orchestrator(ErrorLogger error_logger, PreferenceManager preference_manager, String domain) {
        // save the error handler
        this.m_error_logger = error_logger;
        this.m_preference_manager = preference_manager;

        // MDS domain is required 
        if (domain != null && domain.equalsIgnoreCase(this.preferences().valueOf("mds_def_domain")) == false) {
            this.m_mds_domain = domain;
        }
        
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

        // REQUIRED: We always create the mDS REST processor
        this.m_mbed_device_server_processor = new mbedDeviceServerProcessor(this, this.m_http);
      
        // initialize our peer processor list
        this.initPeerProcessorList();

        // create the console manager
        this.m_console_manager = new ConsoleManager(this);
        
        // start any needed device discovery
        this.initDeviceDiscovery();
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
            // IBM/MQTT: create the MQTT processor manager
            this.errorLogger().info("Orchestrator: adding IBM Watson IoT MQTT Processor");
            this.m_peer_processor_list.add(WatsonIoTPeerProcessorFactory.createPeerProcessor(this, this.m_http));
        }
        if (this.msPeerEnabled()) {
            // MS IoTHub/MQTT: create the MQTT processor manager
            this.errorLogger().info("Orchestrator: adding MS IoTHub MQTT Processor");
            this.m_peer_processor_list.add(MSIoTHubPeerProcessorFactory.createPeerProcessor(this, this.m_http));
        }
        if (this.awsPeerEnabled()) {
            // AWS IoT/MQTT: create the MQTT processor manager
            this.errorLogger().info("Orchestrator: adding AWS IoT MQTT Processor");
            this.m_peer_processor_list.add(AWSIoTPeerProcessorFactory.createPeerProcessor(this, this.m_http));
        }
        if (this.googleCloudPeerEnabled()) {
            // Google Cloud: create the Google Cloud peer processor...
            this.errorLogger().info("Orchestrator: adding Google Cloud Processor");
            this.m_peer_processor_list.add(GoogleCloudPeerProcessorFactory.createPeerProcessor(this, this.m_http));
        }
        if (this.genericMQTTPeerEnabled()) {
            // Create the sample peer processor...
            this.errorLogger().info("Orchestrator: adding Generic MQTT Processor");
            this.m_peer_processor_list.add(GenericMQTTProcessor.createPeerProcessor(this, this.m_http));
        }
        if (this.samplePeerEnabled()) {
            // Create the sample peer processor...
            this.errorLogger().info("Orchestrator: adding 3rd Party Sample REST Processor");
            this.m_peer_processor_list.add(Sample3rdPartyProcessor.createPeerProcessor(this, this.m_http));
        }
    }

    // use IBM peer processor?
    private Boolean ibmPeerEnabled() {
        return (this.preferences().booleanValueOf("enable_iotf_addon") || this.preferences().booleanValueOf("enable_starterkit_addon"));
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

    // use sample 3rd Party peer processor?
    private Boolean samplePeerEnabled() {
        return this.preferences().booleanValueOf("enable_3rd_party_rest_processor");
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

    // initialize the mbed Device Server webhook
    public void initializeDeviceServerWebhook() {
        if (this.m_mbed_device_server_processor != null) {
            // set the webhook
            this.m_mbed_device_server_processor.setWebhook();

            // begin validation polling
            this.beginValidationPolling();
        }
    }

    // reset mbed Device Server webhook
    public void resetDeviceServerWebhook() {
        // REST (mDS)
        if (this.m_mbed_device_server_processor != null) {
            this.m_mbed_device_server_processor.resetWebhook();
        }
    }

    // process the mbed Device Server inbound message
    public void processIncomingDeviceServerMessage(HttpServletRequest request, HttpServletResponse response) {
        // process the received REST message
        //this.errorLogger().info("events (REST-" + request.getMethod() + "): " + request.getRequestURI());
        this.device_server_processor().processNotificationMessage(request, response);
    }

    // process the Console request/event
    public void processConsoleEvent(HttpServletRequest request, HttpServletResponse response) {
        // process the received REST message
        //this.errorLogger().info("console (REST-" + request.getMethod() + "): " + request.getServletPath());
        this.console_manager().processConsole(request, response);
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

    // get the Peer processor
    public ArrayList<PeerInterface> peer_processor_list() {
        return this.m_peer_processor_list;
    }

    // get the mDS processor
    public mbedDeviceServerInterface device_server_processor() {
        return this.m_mbed_device_server_processor;
    }

    // get the console manager
    public ConsoleManager console_manager() {
        return this.m_console_manager;
    }

    // get our mDS domain
    public String getDomain() {
        return this.m_mds_domain;
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
    private PeerInterface peerProcessor(int index) {
        if (index >= 0 && this.m_peer_processor_list != null && index < this.m_peer_processor_list.size()) {
            return this.m_peer_processor_list.get(index);
        }
        return null;
    }

    // Message: API Request
    @Override
    public ApiResponse processApiRequestOperation(String uri,String data,String options,String verb,int request_id,String api_key,String caller_id, String content_type) {
        return this.device_server_processor().processApiRequestOperation(uri, data, options, verb, request_id, api_key, caller_id, content_type);
    }
    
    // Message: notifications
    @Override
    public void processNotificationMessage(HttpServletRequest request, HttpServletResponse response) {
        this.device_server_processor().processNotificationMessage(request, response);
    }
    
    // Message: device-deletions (mbed Cloud)
    @Override
    public void processDeviceDeletions(String[] endpoints) {
        this.device_server_processor().processDeviceDeletions(endpoints);
    }

    // Message: de-registrations
    @Override
    public void processDeregistrations(String[] endpoints) {
        this.device_server_processor().processDeregistrations(endpoints);
    }
    
    // Message: registrations-expired
    @Override
    public void processRegistrationsExpired(String[] endpoints) {
        this.device_server_processor().processRegistrationsExpired(endpoints);
    }

    @Override
    public String subscribeToEndpointResource(String uri, Map options, Boolean init_webhook) {
        return this.device_server_processor().subscribeToEndpointResource(uri, options, init_webhook);
    }

    @Override
    public String subscribeToEndpointResource(String ep_name, String uri, Boolean init_webhook) {
        return this.device_server_processor().subscribeToEndpointResource(ep_name, uri, init_webhook);
    }

    @Override
    public String unsubscribeFromEndpointResource(String uri, Map options) {
        return this.device_server_processor().unsubscribeFromEndpointResource(uri, options);
    }

    @Override
    public String processEndpointResourceOperation(String verb, String ep_name, String uri, String value, String options) {
        return this.device_server_processor().processEndpointResourceOperation(verb, ep_name, uri, value, options);
    }

    @Override
    public void setWebhook() {
        this.device_server_processor().setWebhook();
    }

    @Override
    public void resetWebhook() {
        this.device_server_processor().resetWebhook();
    }

    @Override
    public void pullDeviceMetadata(Map endpoint, AsyncResponseProcessor processor) {
        this.device_server_processor().pullDeviceMetadata(endpoint, processor);
    }

    // PeerInterface Orchestration
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
    
    public void removeSubscription(String domain, String endpoint, String ep_type, String uri) {
        for (int i = 0; this.m_peer_processor_list != null && i < this.m_peer_processor_list.size(); ++i) {
            this.peerProcessor(i).subscriptionsManager().removeSubscription(domain,endpoint,ep_type,uri);
        }
    }
    
    public void addSubscription(String domain, String endpoint, String ep_type, String uri, boolean is_observable) {
        for (int i = 0; this.m_peer_processor_list != null && i < this.m_peer_processor_list.size(); ++i) {
            this.peerProcessor(i).subscriptionsManager().addSubscription(domain,endpoint,ep_type,uri,is_observable);
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
    public void beginValidationPolling() {
        this.device_server_processor().beginValidationPolling();
    }

    @Override
    public String createSubscriptionURI(String ep_name, String resource_uri) {
        return this.device_server_processor().createSubscriptionURI(ep_name, resource_uri);
    }

    @Override
    public boolean deviceRemovedOnDeRegistration() {
        if (this.device_server_processor() != null) {
            return this.device_server_processor().deviceRemovedOnDeRegistration();
        }
        
        // default is false
        return false;
    }
    
    @Override
    public boolean usingMbedCloud() {
        if (this.device_server_processor() != null) {
            return this.device_server_processor().usingMbedCloud();
        }
        
        // default is false
        return false;    
    }

    @Override
    public SubscriptionManager subscriptionsManager() {
        // unused
        return null;
    }

    @Override
    public void initDeviceDiscovery() {
        this.device_server_processor().initDeviceDiscovery();
    }
}
