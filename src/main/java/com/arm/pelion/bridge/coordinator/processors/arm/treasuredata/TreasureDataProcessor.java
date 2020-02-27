/**
 * @file TreasureDataProcessor.java
 * @brief TreeasureData Peer Processor
 * @author Doug Anson
 * @version 1.0
 * @see
 *
 * Copyright 2020. ARM Ltd. All rights reserved.
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
package com.arm.pelion.bridge.coordinator.processors.arm.treasuredata;

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.arm.GenericConnectablePeerProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.GenericSender;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;
import com.arm.pelion.bridge.coordinator.processors.interfaces.ReconnectionInterface;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.transport.Transport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.fusesource.mqtt.client.Topic;
import com.treasuredata.client.*;
import com.treasure_data.logger.*;
import java.util.Properties;

/**
 * TreasureData Processor: API-based TreasureData Processor
 *
 * @author Doug Anson
 */
public class TreasureDataProcessor extends GenericConnectablePeerProcessor implements PeerProcessorInterface, GenericSender,Transport.ReceiveListener, ReconnectionInterface, AsyncResponseProcessor { 
    private boolean m_configured = false;
    private TreasureDataDeviceManager m_device_manager = null;
    
    // TreasureData Auth Token
    private String m_auth_token = null;
    
    // Defaulted device type prefix
    private String m_device_type_prefix = null;
    
    // Defaulted content type
    private String m_content_type = null;
    
    // TreasureData API
    private TDClient m_td_api = null;
    private TreasureDataLogger m_td_logger = null;
    private String m_td_endpoint = null;
    private Properties m_td_properties = null;
    private Properties m_td_logger_properties = null;
    private String m_database_name = null;
    private String m_database_table_name = null;
    
    // Endpoint Name/Type map
    private HashMap<String,String> m_endpoint_type_map = null;
    
    // constructor
    public TreasureDataProcessor(Orchestrator manager, String suffix) {
        super(manager, null, suffix, null);
        
        // initialize the Name/Type map
        this.m_endpoint_type_map = new HashMap<>();
        
        // Get the TD Endpoint configuration
        this.m_td_endpoint = manager.preferences().valueOf("td_endpoint",this.m_suffix);
        if (this.m_td_endpoint != null && this.m_td_endpoint.contains("TD_Endpoint_Goes_Here") == true) {
           // unconfigured
            this.m_configured = false;
            
            // TreasureData 3rd Party peer PeerProcessor Announce (UNCONFIGURED)
            this.errorLogger().warning("TreasureData Processor ENABLED (UNCONFIGURED).");
        }
        else {
            // get the TreasureData auth token 
            this.m_auth_token = manager.preferences().valueOf("td_api_key",this.m_suffix);
            if (this.m_auth_token != null && this.m_auth_token.contains("TD_Master_API_Key_Goes_Here") == true) {
                // unconfigured
                this.m_configured = false;

                // TreasureData 3rd Party peer PeerProcessor Announce (UNCONFIGURED)
                this.errorLogger().warning("TreasureData Processor ENABLED (UNCONFIGURED).");
            }
            else {
                // configured
                this.m_configured = true;
                
                // DEBUG
                this.errorLogger().info("TreasureData: API Key: " + this.m_auth_token);
                
                // get the TD Database name and Database table name
                this.m_database_name = "pelion_" + this.orchestrator().getTenantName().replace("-","_");
                this.m_database_table_name = manager.preferences().valueOf("td_database_table_name",this.m_suffix);
                
                // DEBUG
                this.errorLogger().warning("TreasureData: Database Name: " + this.m_database_name + " Observation Table Name: " + this.m_database_table_name);
                
                // create our TD properties, then our TD API instance...
                try {
                    // Create the configuration properties map
                    this.m_td_properties = this.createTDProperties();

                    // create the TreasureData API
                    this.m_td_api = TDClient.newBuilder(false).setProperties(this.m_td_properties).setApiKey(this.m_auth_token).build();                             
                }
                catch (Exception ex) {
                    this.errorLogger().warning("TreasureData Processor: Exception caught during API Create. Message: " + ex.getMessage());
                    this.m_configured = false;
                }
                
                // TD Server Status
                this.errorLogger().warning("TreasureData: API Server Status: " + this.m_td_api.serverStatus());
                
                // DB create
                if (this.m_configured == true) {
                    // create our tenant DB if one does not exist
                    try {
                        // create the db if it does not already exist
                        this.m_td_api.createDatabaseIfNotExists(this.m_database_name);
                    }
                    catch(TDClientException ex) {
                        // error
                        this.errorLogger().warning("TreasureData Processor: Exception caught during Database create: Name:" + this.m_database_name + " Message: " + ex.getMessage());
                        this.m_configured = false;
                    }
                }
                
                // DB Table create
                if (this.m_configured == true) {
                    // create our injest table within our tenant DB if one does not exist
                    try {
                        // create the db table if it does not already exist
                        this.m_td_api.createTableIfNotExists(this.m_database_name, this.m_database_table_name);
                    }
                    catch(TDClientException ex) {
                        // error
                        this.errorLogger().warning("TreasureData Processor: Exception caught during Database Table create: DB:" + this.m_database_name + " Table: " + this.m_database_table_name + " Message: " + ex.getMessage());
                    }
                }  
                
                // Get the TD Logger now
                if (this.m_configured == true) {
                    // create our TD Logger
                    try {
                        // create the logger
                        this.m_td_logger = TreasureDataLogger.getLogger(this.m_database_name,this.createLoggerProperties());
                        
                        // create the device manager
                        this.m_device_manager = new TreasureDataDeviceManager(this,this.m_td_api,this.m_td_logger,suffix);
                    }
                    catch(Exception ex) {
                        // error
                        this.errorLogger().warning("TreasureData Processor: Exception caught during Logger create: " + ex.getMessage()); 
                    }
                }
                
                this.m_configured = true;

                // TreasureData PeerProcessor Announce - its ready...
                this.errorLogger().warning("TreasureData Processor ENABLED. URL: " + this.m_td_endpoint);
            }
        }
    }
    
    // create the TreasureData logger properties
    private Properties createLoggerProperties() {
        // create the properties list
        if (this.m_td_logger_properties == null) {
            this.m_td_logger_properties = new Properties();
        }
        
        // defaulted values for the logger
        this.m_td_logger_properties.put("td.logger.api.key",this.m_auth_token);
        this.m_td_logger_properties.put("td.logger.api.server.scheme","http://");
        this.m_td_logger_properties.put("td.logger.api.server.port","80");
        this.m_td_logger_properties.put("td.logger.agentmode","false");
        this.m_td_logger_properties.put("td.logger.api.server.host",this.m_td_endpoint);
        
        // return the properties
        return this.m_td_logger_properties;
    }
    
    // create the TreasureData properties
    private Properties createTDProperties() {
        // create the properties list
        if (this.m_td_properties == null) {
            this.m_td_properties = new Properties();
        }
            
        // TD Client Retry Limit
        this.m_td_properties = this.addTDProperty("td_client_retry_limit");
        
        // XXX Add others as needed...
        
        // return the properties
        return this.m_td_properties;
    }
    
    // add TD Property
    private Properties addTDProperty(String name) {
        // get the property value
        String value = this.prefValue(name);
        
        // if its specified... map to TD namespace and add
        if (value != null && value.length() > 0) {
            // map to TD namespace
            String td_prop_name = name.replace("_", ".");
            
            // DEBUG
            this.errorLogger().warning("TreasureData: API Configuration Property Set: " + td_prop_name + "=" + value);
            
            // add to the properties list
            this.m_td_properties.setProperty(td_prop_name, value);
        }
        return this.m_td_properties;
    }
    
    // get the resource URIs as a list
    private List<String> getResourceURIs(Map endpoint) {
        ArrayList<String> uri_list = new ArrayList<>();
        
        // loop through the endpoints resource list and get the URIs
        List resources = (List)endpoint.get("resources"); 
        for(int i=0;resources != null && i<resources.size();++i) {
            Map entry = (Map)resources.get(i);
            if (entry != null) {
                String uri = Utils.valueFromValidKey(entry, "uri", "path");
                if (Utils.isCompleteURI(uri) == true && Utils.isHandledURI(uri) == false) {
                    uri_list.add(uri);
                }
            }
        }
        
        // DEBUG
        this.errorLogger().info("TreasureData: Resource URIs: " + uri_list);
        
        // return the array of resource URIs
        return uri_list;
    }
    
    // create the native object value from the received observation message
    private Object nativeValueFromMessage(String message) {
        Object value = null;
        
        // get the Base64 Encoded value from the message
        Map parsed = this.tryJSONParse(message);
        if (parsed != null && parsed.containsKey("value") == true) {
            return this.fundamentalTypeDecoder().getFundamentalValue(parsed.get("value"));
        }
        
        // return the native value
        return value;
    }
    
    // create TD observation update message
    private Map createTDObservationMessage(String message) {
        // pull the value from the payload
        Object value = this.nativeValueFromMessage(message);
        
        // Parse the message
        Map parsed = this.tryJSONParse(message);
        
        // create the TD update message
        HashMap<String,Object> td_message = new HashMap<>();
        td_message.put("ept",this.endpointTypeFromEndpointName((String)parsed.get("ep")));
        td_message.put("ep",(String)parsed.get("ep"));
        td_message.put("uri",(String)parsed.get("path"));
        td_message.put("value",value);
        
        // create the JSON TD message
        return td_message;
    }
    
    // GenericSender Implementation: send a message
    @Override
    public boolean sendMessage(String topic, String message) {       
        if (this.m_configured) {
            // create the TD Message
            Map td_message = this.createTDObservationMessage(message);
            
            // DEBUG
            this.errorLogger().info("TreasureData(sendMessage): TD Log Message: " + td_message);
            
            try {
                // Log it...
                this.m_td_logger.log(this.m_database_table_name, td_message);

                // return our status
                return true;
            }
            catch (Exception ex) {
                // error
                this.errorLogger().warning("TreasureData: Exception in sendMessage(): DATA: " + td_message + " ERROR: " + ex.getMessage());
            }
        }
        else {
            // not configured
            this.errorLogger().info("TreasureData(sendMessage): TreasureData Auth Token is UNCONFIGURED. Please configure and restart the bridge (OK).");
        }
        return false;
    }
    
    // GenericSender Implementation: send a message
    @Override
    public boolean sendMessage(String topic, byte[] bytes) {        
        return this.sendMessage(topic,new String(bytes));
    }
    
    // process a device deletion
    @Override
    public String[] processDeviceDeletions(Map parsed) {
        String[] devices = this.processDeviceDeletionsBase(parsed);
        for(int i=0;devices != null && i<devices.length;++i) {
            String device_type_id = this.createDeviceTypeID(devices[i],this.m_device_type_prefix);
            this.removeEndpointTypeFromEndpointName(devices[i]);
        }
        return devices;
    }
    
    // OVERRIDE: process a deregistration
    @Override
    public String[] processDeregistrations(Map parsed) {
        String[] devices = this.processDeregistrationsBase(parsed);
        
        // TEST: We can actually DELETE the device on deregistration to test device-delete before the device-delete message goes live
        if (this.orchestrator().deviceRemovedOnDeRegistration() == true) {
            for(int i=0;devices != null && i<devices.length;++i) {
                String device_type_id = this.createDeviceTypeID(devices[i],this.m_device_type_prefix);
                this.removeEndpointTypeFromEndpointName(devices[i]);
            }
        }
        return devices;
    }
    
    // OVERRIDE: process a registrations-expired 
    @Override
    public String[] processRegistrationsExpired(Map parsed) {
       // process a de-registration event
       return this.processDeregistrations(parsed);
    }

    // OVERRIDE: process a received new registration
    @Override
    public void processNewRegistration(Map data) {
        List registrations = (List)data.get("registrations");
        if (registrations != null && registrations.size() > 0) {
            if ((this.getCurrentEndpointCount() + registrations.size()) < this.getMaxNumberOfShadows()) {
                for(int i=0;registrations != null && i<registrations.size();++i) {
                    Map device = (Map)registrations.get(i);
                    this.completeNewDeviceRegistration(device);
                }
                super.processNewRegistration(data);
            }
            else {
                // exceeded the maximum number of device shadows
                this.errorLogger().warning("TreasureData: Exceeded maximum number of device shadows. Limit: " + this.getMaxNumberOfShadows());
            }
        }
        else {
            // nothing to shadow
            this.errorLogger().info("TreasureData: Nothing to shadow (OK).");
        }
    }
    
    // OVERRIDE:  process a reregistration
    @Override
    public void processReRegistration(Map data) {
        List registrations = (List)data.get("reg-updates");
        if (registrations != null && registrations.size() > 0) {
            if ((this.getCurrentEndpointCount() + registrations.size()) < this.getMaxNumberOfShadows()) {
                for(int i=0;registrations != null && i<registrations.size();++i) {
                    Map device = (Map)registrations.get(i);
                    this.completeNewDeviceRegistration(device);
                }
                super.processReRegistration(data);
            }
            else {
                // exceeded the maximum number of device shadows
                this.errorLogger().warning("TreasureData: Exceeded maximum number of device shadows. Limit: " + this.getMaxNumberOfShadows());
            }
        }
        else {
            // nothing to shadow
            this.errorLogger().info("TreasureData: Nothing to shadow (OK).");
        }
    }
    
    // OVERRIDE: complete new registration
    @Override
    public void completeNewDeviceRegistration(Map device) {
        if (this.m_configured) {
            if (this.m_device_manager != null) {
                // get the device ID and device Type
                String device_type = Utils.valueFromValidKey(device, "endpoint_type", "ept");
                String device_id = Utils.valueFromValidKey(device, "id", "ep");
                    
                // create the device twin
                boolean ok = this.m_device_manager.createDevice(device,this.m_device_type_prefix);
                if (ok) {
                    // add our device type
                    this.setEndpointTypeFromEndpointName(device_id, device_type);
                    this.errorLogger().warning("TreasureData(completeNewDeviceRegistration): Device Shadow: " + device_id + " creation SUCCESS");
                }
                else {
                    this.errorLogger().warning("TreasureData(completeNewDeviceRegistration): Device Shadow: " + device_id + " creation FAILURE");
                }
            }
            else {
                this.errorLogger().warning("TreasureData(completeNewDeviceRegistration): DeviceManager is NULL. Shadow Device creation FAILURE: " + device);
            }
        }
        else {
            // not configured
            this.errorLogger().warning("TreasureData(completeNewDeviceRegistration): TreasureData Auth Token is UNCONFIGURED. Please configure and restart the bridge (OK).");
        }
    }
    
    // get the endpoint type from the endpoint name
    public String endpointTypeFromEndpointName(String ep) {
        return this.m_endpoint_type_map.get(ep);
    }
    
    // clear the endpoint type from the endpoint name
    public void removeEndpointFromMap(String ep) {
        this.m_endpoint_type_map.remove(ep);
    }
    
    // add the endpoint type to the endpoint name
    public void setEndpointTypeForEndpointName(String ep,String ept) {
        this.m_endpoint_type_map.put(ep,ept);
    }
    
    // get the auth token
    private String getAuthToken() {
        return this.m_auth_token;
    }
    
    // create the device type ID
    private String createDeviceTypeID(String ep,String prefix) {
        return this.getEndpointTypeFromEndpointName(ep);
    }
    
    @Override
    public boolean startReconnection(String ep_name, String ep_type, Topic[] topics) {
        // not used
        return true;
    }
    
    @Override
    public boolean processAsyncResponse(Map response) {
        // not used - process Async Responses 
        this.errorLogger().warning("TreasureData(processAsyncResponse): AsyncResponse: " + response);
        return true;
    }
    
    // initialize any TreasureData listeners
    @Override
    public void initListener() {
        // not used
    }

    // stop our TreasureData 3rd Party peer listeners
    @Override
    public void stopListener() {
        // not used
    }
    
    // Create the authentication hash
    @Override
    public String createAuthenticationHash() {
        // just create a hash of something unique to the peer side... 
        return Utils.createHash(this.m_auth_token);
    }
}