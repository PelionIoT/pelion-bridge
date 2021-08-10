/**
 * @file IoTHubProcessor.java
 * @brief IoTHub Peer Processor (HTTP-based)
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
package com.arm.pelion.bridge.coordinator.processors.ms.http;

import com.arm.pelion.bridge.coordinator.processors.core.HTTPDeviceListener;
import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.arm.GenericConnectablePeerProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.DeviceManagerToPeerProcessorInterface;
import com.arm.pelion.bridge.coordinator.processors.interfaces.GenericSender;
import com.arm.pelion.bridge.coordinator.processors.interfaces.HTTPDeviceListenerInterface;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;
import com.arm.pelion.bridge.coordinator.processors.ms.IoTHubDeviceManager;
import com.arm.pelion.bridge.core.ApiResponse;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.transport.HttpTransport;
import com.arm.pelion.bridge.transport.Transport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MS IoTHub peer processor based on HTTP
 * 
 * @author Doug Anson
 */
public class IoTHubProcessor extends GenericConnectablePeerProcessor implements Runnable, DeviceManagerToPeerProcessorInterface, HTTPDeviceListenerInterface, PeerProcessorInterface, GenericSender,Transport.ReceiveListener, AsyncResponseProcessor {    
    private static final String IOTHUB_DEVICE_PREFIX_SEPARATOR = "-";                       // device prefix separator (if used...)... cannot be an "_"
    private static final long SAS_TOKEN_VALID_TIME_MS = 365 * 24 * 60 * 60 * 1000;          // SAS Token created for 1 year expiration
    private static final long SAS_TOKEN_RECREATE_INTERVAL_MS = 360 * 24 * 60 * 60 * 1000;   // number of days to wait before re-creating the SAS Token
    private static final String IOTHUB_AUTH_QUALIFIER = "SharedAccessSignature";            // IoTHub SAS Token qualifier
    
    private int m_num_coap_topics = 1;                                  // # of MQTT Topics for CoAP verbs in IoTHub implementation
    private String m_iot_hub_observe_notification_topic = null;
    private String m_iot_hub_coap_cmd_topic_base = null;
    private String m_iot_hub_name = null;
    private String m_iot_hub_sas_token = null;
    private boolean m_iot_hub_sas_token_initialized = false;
    private String m_iot_hub_connect_string = null;
    private String m_iot_hub_password_template = null;
    private IoTHubDeviceManager m_device_manager = null;
    private boolean m_iot_event_hub_enable_device_id_prefix = false;
    private String m_iot_event_hub_device_id_prefix = null;
    private String m_iot_hub_api_version = null;
    private Thread m_iot_hub_sas_token_refresh_thread = null;
    private boolean m_sas_token_run_refresh_thread = true;
    private long m_iot_hub_sas_token_validity_time_ms = SAS_TOKEN_VALID_TIME_MS;
    private long m_iot_hub_sas_token_recreate_interval_ms= SAS_TOKEN_RECREATE_INTERVAL_MS;
    private boolean m_configured = false;
    
    // HTTP listeners for our device shadows
    private HashMap<String,HTTPDeviceListener> m_device_listeners = null;
    
    // URL templates for IoTHub/HTTP
    private String m_iot_event_hub_observe_notification_message_url_template = null;
    private String m_iot_event_hub_device_cmd_message_url_template = null;
    private String m_iot_event_hub_device_cmd_ack_url_template = null;
    
    // constructor
    public IoTHubProcessor(Orchestrator manager, HttpTransport http) {
        this(manager, http, null);
    }

    // constructor
    public IoTHubProcessor(Orchestrator manager, HttpTransport http, String suffix) {
        super(manager, null, suffix, http);
        
        // Http processor 
        this.m_http = http;
        
        // IoTHub Processor Announce
        this.errorLogger().warning("Azure IoTHub Processor ENABLED. (HTTP)");
        
        // get the IoTHub version tag
        this.m_iot_hub_api_version = this.orchestrator().preferences().valueOf("iot_event_hub_api_version",this.m_suffix);
                
        // Get the IoTHub Connect String
        this.m_iot_hub_connect_string = this.orchestrator().preferences().valueOf("iot_event_hub_connect_string",this.m_suffix);
        
        // HTTP URL templates
        this.m_iot_event_hub_observe_notification_message_url_template = this.orchestrator().preferences().valueOf("iot_event_hub_observe_notification_message_url",this.m_suffix);
        this.m_iot_event_hub_device_cmd_message_url_template = this.orchestrator().preferences().valueOf("iot_event_hub_device_cmd_message_url",this.m_suffix);
        this.m_iot_event_hub_device_cmd_ack_url_template = this.orchestrator().preferences().valueOf("iot_event_hub_device_cmd_ack_url",this.m_suffix);
                
        // create the HTTP-based device listeners
        this.m_device_listeners = new HashMap<>();
        
        // HTTP Auth Qualifier
        this.m_http_auth_qualifier = IOTHUB_AUTH_QUALIFIER;
        
        // initialize the SAS Token and its refresher...
        this.initSASToken(false);
        
        // continue only if configured
        if (this.m_iot_hub_connect_string != null && this.m_iot_hub_connect_string.contains("Goes_Here") == false) {
            // we are configured!
            this.m_configured = true;
            
            // IoTHub SAS Token (take out the qualifier if present...)
            this.m_http_auth_token = this.m_iot_hub_sas_token.replace(this.m_http_auth_qualifier + " ", "").trim();
        
            // get our defaults
            this.m_mqtt_host = this.orchestrator().preferences().valueOf("iot_event_hub_mqtt_ip_address", this.m_suffix).replace("__IOT_EVENT_HUB__", this.m_iot_hub_name);

            // Observation notification topic
            this.m_iot_hub_observe_notification_topic = this.orchestrator().preferences().valueOf("iot_event_hub_observe_notification_topic", this.m_suffix) + this.m_observation_key;

            // Send CoAP commands back through mDS into the endpoint via these Topics... 
            this.m_iot_hub_coap_cmd_topic_base = this.orchestrator().preferences().valueOf("iot_event_hub_coap_cmd_topic", this.m_suffix).replace("__COMMAND_TYPE__", "#");

            // IoTHub Device Manager - will initialize and upsert our IoTHub bindings/metadata
            this.m_device_manager = new IoTHubDeviceManager(this.m_suffix, http, this, this.m_iot_hub_name, this.m_iot_hub_sas_token, false);

            // set the MQTT password template
            this.m_iot_hub_password_template = this.orchestrator().preferences().valueOf("iot_event_hub_mqtt_password", this.m_suffix).replace("__IOT_EVENT_HUB__", this.m_iot_hub_name);

            // Enable prefixing of mbed Cloud names for IoTHub
            this.m_iot_event_hub_enable_device_id_prefix = this.prefBoolValue("iot_event_hub_enable_device_id_prefix", this.m_suffix);
            this.m_iot_event_hub_device_id_prefix = null;

            // If prefixing is enabled, get the prefix
            if (this.m_iot_event_hub_enable_device_id_prefix == true) {
                this.m_iot_event_hub_device_id_prefix = this.preferences().valueOf("iot_event_hub_device_id_prefix", this.m_suffix);
                if (this.m_iot_event_hub_device_id_prefix != null) {
                    this.m_iot_event_hub_device_id_prefix += IOTHUB_DEVICE_PREFIX_SEPARATOR;
                }
            }
        }
        else {
            // unconfigured
            this.errorLogger().warning("IoTHub(HTTP): IoTHub Connection String is UNCONFIGURED. Pausing...");
        }
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
        this.errorLogger().info("IoTHub(HTTP): Resource URIs: " + uri_list);
        
        // return the array of resource URIs
        return uri_list;
    }
    
    // initialize the SAS Token
    private void initSASToken(boolean enable_refresher) {
         // initialize the SAS Token and set the IoTHub name
        Map test_parse = this.parseConnectionString(this.m_iot_hub_connect_string);
        if (test_parse != null && test_parse.isEmpty() == false) {
            // we can generate the SAS Token from the connection string...
            this.m_iot_hub_sas_token = this.createSASToken(this.m_iot_hub_connect_string,this.m_iot_hub_sas_token_validity_time_ms);
            this.m_iot_hub_name = this.getIoTHubNameFromConnectionString(this.m_iot_hub_connect_string);
            this.m_iot_hub_sas_token_initialized = true;
            
            // start a refresh thread
            if (enable_refresher) {
                if (this.m_iot_hub_sas_token_refresh_thread == null) {
                    try {
                        this.m_iot_hub_sas_token_refresh_thread = new Thread(this);
                        this.m_iot_hub_sas_token_refresh_thread.start();
                    }
                    catch (Exception ex) {
                        this.errorLogger().warning("IoTHub(HTTP): Exception caught while starting SAS Token refresher: " + ex.getMessage());
                        this.m_iot_hub_sas_token_refresh_thread = null;
                    }
                }
            }
        }
        else {
            // we must pull the SAS token and hub name from the configuration properties
            this.m_iot_hub_sas_token = this.orchestrator().preferences().valueOf("iot_event_hub_sas_token", this.m_suffix);
            this.m_iot_hub_name = this.orchestrator().preferences().valueOf("iot_event_hub_name", this.m_suffix);
        }
    }
    
    // XXX refresh our SAS Token
    private void refreshSASToken() {
        if (this.m_iot_hub_sas_token_initialized == true) {
            // DEBUG
            this.errorLogger().warning("IoTHub(HTTP): Refreshing SAS Token...");
            
            // XXX disconnect
            
            // create the new SAS token
            this.m_iot_hub_sas_token = this.createSASToken(this.m_iot_hub_connect_string,this.m_iot_hub_sas_token_validity_time_ms);
            
            // XXX reconnect
        }
    }
    
    // create our SAS Token
    private String createSASToken(String connection_string,long validity_time_ms) {
        String iot_hub_host = this.getHostNameFromConnectionString(connection_string);
        String key_value= this.getSharedAccessKeyFromConnectionString(connection_string);
        String key_name = this.getSharedAccessKeyNameFromConnectionString(connection_string);
        if (iot_hub_host != null && key_value != null && key_name != null) {
            return Utils.CreateIoTHubSASToken(this.errorLogger(), iot_hub_host, key_name, key_value, validity_time_ms);
        }
        return null;
    }
    
    // get our IoTHub Name from the Connection String
    private String getIoTHubNameFromConnectionString(String connection_string) {
        // remove the fully qualified domain name and just return the iothub name
        return this.getHostNameFromConnectionString(connection_string).replace(".azure-devices.net",""); 
    }
    
    // get the HostName from the ConnectionString
    private String getHostNameFromConnectionString(String connection_string) {
        return this.getConnectionStringElement("HostName",connection_string);
    }
    
    // get the SharedAccessKey from the ConnectionString
    private String getSharedAccessKeyFromConnectionString(String connection_string) {
        return this.getConnectionStringElement("SharedAccessKey",connection_string);
    }
    
    // get the SharedAccessKeyName from the ConnectionString
    private String getSharedAccessKeyNameFromConnectionString(String connection_string) {
        return this.getConnectionStringElement("SharedAccessKeyName",connection_string);
    }
    
    // get a specific element from the Connection String
    private String getConnectionStringElement(String key,String connection_string) {
        HashMap<String,String> connection_string_map = this.parseConnectionString(connection_string);
        if (connection_string_map != null && connection_string_map.isEmpty() == false) {
            return connection_string_map.get(key);
        }
        return null;
    }
    
    // parse the connection string into a HashMap<String,String>
    private HashMap<String,String> parseConnectionString(String connection_string) {
        HashMap<String,String> map = null;
        // Connection String format: HostName=<hubname>.azure-devices.net;SharedAccessKeyName=<keyName>;SharedAccessKey=<key>
        if (connection_string != null && connection_string.contains("HostName=") == true && connection_string.contains("SharedAccessKeyName=") == true && connection_string.contains("SharedAccessKey=") == true) {
            // divide into elements via semicolon
            String[] elements = connection_string.split(";");
            if (elements != null) {
                map = new HashMap<>();
                for(int i=0;i<elements.length;++i) {
                    // divide ith element from key=value
                    String[] kvp = elements[i].split("=");
                    if (kvp != null && kvp.length >= 2) {
                        // place the key and value in the map
                        map.put(kvp[0],kvp[1]);
                    }
                }
            }
        }
        
        // DEBUG
        if (map != null) {
            // we have a good connection string
            this.errorLogger().info("IoTHub(HTTP): Parsed Connection String: " + map);
        }
        else {
            // we dont have a connection string... compatibility with older configs
            this.errorLogger().warning("IoTHub(HTTP): No Connection String supplied. SASToken/HubName required in configuration (OK)");
        }
        
        // return the map
        return map;
    }
   
    // create the device observation notification url
    private String buildDeviceObservationNotificationURL(String ep_name) {
        return this.m_iot_event_hub_observe_notification_message_url_template.replace("__EPNAME__", ep_name).replace("__IOT_EVENT_HUB__",this.m_iot_hub_name).replace("__API_VERSION__",this.m_iot_hub_api_version);
    }
    
    // create the device coap command URL
    private String buildDeviceCommandURL(String ep_name) {
        return this.m_iot_event_hub_device_cmd_message_url_template.replace("__EPNAME__", ep_name).replace("__IOT_EVENT_HUB__",this.m_iot_hub_name).replace("__API_VERSION__",this.m_iot_hub_api_version);
    }
    
    // create the device coap command ack URL
    private String buildDeviceCommandACKURL(String ep_name,String etag) {
        return this.m_iot_event_hub_device_cmd_ack_url_template.replace("__EPNAME__", ep_name).replace("__IOT_EVENT_HUB__",this.m_iot_hub_name).replace("__API_VERSION__",this.m_iot_hub_api_version).replace("__ETAG__", etag);
    }
    
    // GenericSender Implementation: send a message
    @Override
    public boolean sendMessage(String topic, String message) {
        boolean ok = false;
        if (this.m_configured) {
            // DEBUG
            this.errorLogger().info("IoTHub(sendMessage): TOPIC: " + topic + " MESSAGE: " + message);
            try {
                // Get the endpoint name
                String ep_name = this.getEndpointNameFromNotificationTopic(topic);

                // IOTHUB Prefix
                String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

                // create the posting URL 
                String url = this.buildDeviceObservationNotificationURL(iothub_ep_name);

                // DEBUG
                this.errorLogger().info("IoTHub(sendMessage): URL: " + url + " MESSAGE: " + message);

                // post the message to IoTHub
                this.httpsPost(url, message);
                int http_code = this.m_http.getLastResponseCode();

                // DEBUG
                if (Utils.httpResponseCodeOK(http_code)) {
                    // SUCCESS
                    this.errorLogger().info("IoTHub(sendMessage): message: " + message + " sent to device: " + ep_name + " SUCCESSFULLY. Code: " + http_code);
                    ok = true;
                }
                else if (http_code != 404) {
                    // FAILURE
                    this.errorLogger().warning("IoTHub(sendMessage): message: " + message + " send to device: " + ep_name + " FAILED. Code: " + http_code);
                }
            }
            catch (Exception ex) {
                this.errorLogger().warning("IoTHub(HTTP): Exception in sendMessage: " + ex.getMessage(),ex);
            }
        }
        else {
            // not configured
            this.errorLogger().info("IoTHub(sendMessage): IoTHub Auth Token is UNCONFIGURED. Please configure and restart the bridge (OK).");
        }
        return ok;
    }
    
    // process a device deletion
    @Override
    public String[] processDeviceDeletions(Map parsed) {
        String[] devices = this.processDeviceDeletionsBase(parsed);
        for(int i=0;devices != null && i<devices.length;++i) {
            this.removeDeviceListener(devices[i]);
            this.m_device_manager.deleteDevice(devices[i]);
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
                this.removeDeviceListener(devices[i]);
                this.m_device_manager.deleteDevice(devices[i]);
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
                this.errorLogger().warning("IoTHub(HTTP): Exceeded maximum number of device shadows. Limit: " + this.getMaxNumberOfShadows());
            }
        }
        else {
            // nothing to shadow
            this.errorLogger().info("IoTHub(HTTP): Nothing to shadow (OK).");
        }
    }
    
    // OVERRIDE:  process a reregistration
    @Override
    public void processReRegistration(Map data) {
        List notifications = (List) data.get("reg-updates");
        for (int i = 0; notifications != null && i < notifications.size(); ++i) {
            Map entry = (Map) notifications.get(i);

            // get the device ID
            String device_id = Utils.valueFromValidKey(entry, "id", "ep");
            
            // IOTHUB Prefix
            String iothub_ep_name = this.addDeviceIDPrefix(device_id);

            // process as a new registration
            this.processRegistration(data, "reg-updates");
        }
    }
    
    // OVERRIDE: complete new registration
    @Override
    public void completeNewDeviceRegistration(Map device) {
        if (this.m_configured) {
            if (this.m_device_manager != null) {
                // get the device ID and device Type
                String device_type = this.sanitizeEndpointType(Utils.valueFromValidKey(device, "endpoint_type", "ept"));
                String device_id = Utils.valueFromValidKey(device, "id", "ep");
                    
                // create the device twin
                boolean ok = this.m_device_manager.registerNewDevice(device);
                if (ok) {
                    // add our device type
                    this.setEndpointTypeFromEndpointName(device_id, device_type);
                    this.errorLogger().warning("IoTHub(completeNewDeviceRegistration): Device Shadow: " + device_id + " creation SUCCESS");
                    
                    // Create and start our device listener thread for this device
                    this.createDeviceListener(device_id);
                }
                else {
                    this.errorLogger().warning("IoTHub(completeNewDeviceRegistration): Device Shadow: " + device_id + " creation FAILURE");
                }
            }
            else {
                this.errorLogger().warning("IoTHub(completeNewDeviceRegistration): DeviceManager is NULL. Shadow Device creation FAILURE: " + device);
            }
        }
        else {
            // not configured
            this.errorLogger().warning("IoTHub(completeNewDeviceRegistration): IoTHub Auth Token is UNCONFIGURED. Please configure and restart the bridge (OK).");
        }
    }
    
    // IoTHub Specific: process device deletion
    @Override
    protected synchronized Boolean deleteDevice(String ep_name) {
        if (this.m_device_manager != null && ep_name != null && ep_name.length() > 0) { 
            // Stop the device listener
            this.removeDeviceListener(ep_name);
            
            // DEBUG
            this.errorLogger().info("IoTHub(HTTP): deleting device shadow: " + ep_name);

            // remove the device from IoTHub
            if (this.m_device_manager.deleteDevice(ep_name) == false) {
                // unable to delete the device shadow from IoTHub
                this.errorLogger().warning("IoTHub(HTTP): WARNING: Unable to delete device " + ep_name + " from IoTHub!");
            }
            else {
                // successfully deleted the device shadow from Google CloudIoT
                this.errorLogger().warning("IoTHub(HTTP): Device " + ep_name + " deleted from IoTHub SUCCESSFULLY.");
            }
            
            // remove type from the type list
            this.removeEndpointTypeFromEndpointName(ep_name);
        }
        
        // aggressive deletion
        return true;
    }
    
    @Override
    public boolean processAsyncResponse(Map endpoint) {
        // with the attributes added, we finally create the device in MS IoTHub
        this.completeNewDeviceRegistration(endpoint);
            
        // return our processing status
        return true;
    }

    // get the next message (these are device command messages from IoTHub --> Pelion)
    private String getNextMessage(HttpTransport http,String ep_name) {
        // IOTHUB Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);
        
        // create the URL
        String url = this.buildDeviceCommandURL(iothub_ep_name);
            
        // dispatch the GET and collect the result - with message ACK immediately to IoTHub
        String message = this.httpsGet(http,url);
        int http_code = http.getLastResponseCode();
        String etag = http.getLastETagValue();
        if (message != null && message.length() > 0) {
            // DEBUG
            this.errorLogger().info("IoTHub(HTTP): getNextMessage: Acking Message: " + message + " CODE: " + http_code + " ETAG: " + etag);

            // immediately ACK a message if we get one...
            this.ackLastMessage(http,ep_name,etag);
        }
        
        // return the message
        return message;
    }
    
    // ack a command message
    private void ackLastMessage(HttpTransport http,String ep_name,String etag) {
        try {
            // IOTHUB Prefix
            String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

            // create the URL
            String url = this.buildDeviceCommandACKURL(iothub_ep_name, etag);

            // dispatch the ACK to dequeue the message within IoTHub
            this.httpsDelete(http, url, etag);
            int http_code = http.getLastResponseCode();

            // DEBUG
            this.errorLogger().info("IoTHub(HTTP): URL: " + url + " EP: " + ep_name + " ETAG: " + etag + " CODE: " + http_code);
        }
        catch (Exception ex) {
            this.errorLogger().warning("IoTHub(HTTP): Exception in ackLastCommandMessage: " + ex.getMessage(),ex);
        }
    }
    
    // poll for and process device command messages
    @Override
    public void pollAndProcessDeviceMessages(HttpTransport http,String ep_name) {
        // Get the next message
        String message = this.getNextMessage(http,ep_name);
        if (message != null && message.length() > 0) {
            // DEBUG
            this.errorLogger().info("IoTHub(HTTP): DEVICE: " + ep_name + " MESSAGE: " + message);

            // parse and process the message
            this.onMessageReceive(ep_name, message);
        }
        else {
            // No message to process... OK
            this.errorLogger().info("IoTHub(HTTP): DEVICE: " + ep_name + " No message to process. (OK)");
        }
    }
    
    // send the API Response back through the topic
    private void sendApiResponse(String topic, ApiResponse response) {
        // publish via sendMessage()
        this.sendMessage(topic, response.createResponseJSON());
    }
    
    // process a message to send to Pelion...
    @Override
    public void onMessageReceive(String ep_name,String message) {
        // DEBUG
        this.errorLogger().info("IoTHub (HTTP):  message: " + message);

        // IoTHub Device Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);
        
        // process any API requests...
        if (this.isApiRequest(message)) {
            // use a fudged topic
            String topic = this.createFudgedTopic(ep_name);
            
            // process the message
            this.sendApiResponse(topic,this.processApiRequestOperation(message));
            
            // return as we are done with the API request... no AsyncResponses necessary for raw API requests...
            return;
        }

        // pull the CoAP Path URI from the message itself... its JSON... 
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        String uri = this.getCoAPURI(message);

        // pull the CoAP Payload from the message itself... its JSON... 
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        String value = this.getCoAPValue(message);
        
        // Get the payload
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        String payload = this.getCoAPPayload(message);

        // pull the CoAP verb from the message itself... its JSON... (PRIMARY)
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        String coap_verb = this.getCoAPVerb(message);

        // if there are mDC/mDS REST options... lets add them
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get", "options":"noResp=true" }
        String options = this.getRESTOptions(message);

        // dispatch the coap resource operation request
        String response = this.orchestrator().processEndpointResourceOperation(coap_verb, ep_name, uri, value, options);

        // examine the response
        if (response != null && response.length() > 0) {
            // SYNC: We only process AsyncResponses from GET verbs... we dont sent HTTP status back through IoTHub.
            this.errorLogger().info("IoTHub(HTTP): Response: " + response);

            // AsyncResponse detection and recording...
            if (this.isAsyncResponse(response) == true) {
                // CoAP GET and PUT provides AsyncResponses...
                if (coap_verb.equalsIgnoreCase("get") == true || coap_verb.equalsIgnoreCase("put") == true) {
                    // DEBUG
                    this.errorLogger().info("IoTHub(HTTP): Recording ASYNC RESPONSE: " + response);
                    
                    // its an AsyncResponse.. so record it...
                    String topic = this.createFudgedTopic(ep_name);
                    this.recordAsyncResponse(response, coap_verb, topic, message, ep_name, uri);
                }
                else {
                    // we ignore AsyncResponses to PUT,POST,DELETE
                    this.errorLogger().info("IoTHub(HTTP): Ignoring AsyncResponse for " + coap_verb + " (OK).");
                }
            }
            else if (coap_verb.equalsIgnoreCase("get")) {
                // not an AsyncResponse... so just emit it immediately... only for GET...
                this.errorLogger().info("IoTHub(HTTP): Response: " + response + " from GET... creating observation...");

                // we have to format as an observation...
                String observation = this.createObservation(coap_verb, iothub_ep_name, uri, payload, value);

                // DEBUG
                this.errorLogger().info("IoTHub(HTTP): Sending Observation (GET): " + observation);

                // send the observation (GET reply)...
                String topic = this.createFudgedTopic(ep_name);
                this.sendMessage(topic, observation);
            }
        }
    }
    
    // create our HTTP-based device listener 
    private void createDeviceListener(String ep_name) {
        if (ep_name != null && ep_name.length() > 0) {
            if (this.m_device_listeners.get(ep_name) == null) {
                this.m_device_listeners.put(ep_name, new HTTPDeviceListener(this,new HttpTransport(this.errorLogger(),this.preferences()),ep_name));
            }
        }
    }
    
    // remove our HTTP-based device listener
    private void removeDeviceListener(String ep_name) {
        if (ep_name != null && ep_name.length() > 0) {
        HTTPDeviceListener doomed = this.m_device_listeners.get(ep_name);
            if (doomed != null) {
                doomed.halt();
                this.m_device_listeners.remove(ep_name);
            }
        }
    }
    
    // MS IoTHub Specific: DeviceID Prefix enabler
    private String addDeviceIDPrefix(String ep_name) {
        String iothub_ep_name = ep_name;
        if (this.m_iot_event_hub_device_id_prefix != null && ep_name != null) {
            if (ep_name.contains(this.m_iot_event_hub_device_id_prefix) == false) {
                iothub_ep_name = this.m_iot_event_hub_device_id_prefix + ep_name;
            }
        }

        // DEBUG
        //this.errorLogger().info("addDeviceIDPrefix: ep_name: " + ep_name + " --> iothub_ep_name: " + iothub_ep_name);
        return iothub_ep_name;
    }

    // MS IoTHub Specific: DeviceID Prefix remover
    private String removeDeviceIDPrefix(String iothub_ep_name) {
        String ep_name = iothub_ep_name;
        if (this.m_iot_event_hub_device_id_prefix != null && iothub_ep_name != null) {
            return iothub_ep_name.replace(this.m_iot_event_hub_device_id_prefix, "");
        }

        // trim..
        if (ep_name != null) {
            ep_name = ep_name.trim();
        }

        // DEBUG
        //this.errorLogger().info("removeDeviceIDPrefix: iothub_ep_name: " + iothub_ep_name + " --> ep_name: " + ep_name);
        return ep_name;
    }
    
    // create the device type ID
    private String createDeviceTypeID(String ep,String prefix) {
        return this.getEndpointTypeFromEndpointName(ep);
    }
    
    // Run the refresh thread to refresh the SAS Token periodically
    @Override
    public void run() {
        while(this.m_sas_token_run_refresh_thread == true) {
            Utils.waitForABit(this.errorLogger(), this.m_iot_hub_sas_token_recreate_interval_ms);
            this.refreshSASToken();
        }
        
        // WARN - refresh thread has halted
        this.errorLogger().warning("IoTHuB: WARNING: SAS Token refresh thread has halted. SAS Token may expire within the year...");
    }

    // Create the authentication hash for the webhook auth header
    @Override
    public String createAuthenticationHash() {
        // use the IoTHub connection string as the hash seed for the webhook auth header...
        return Utils.createHash(this.m_iot_hub_connect_string);
    }
    
    // initialize any IoTHub listeners
    @Override
    public void initListener() {
        // not used in HTTP based integration
    }

    // stop any IoTHub listeners
    @Override
    public void stopListener() {
        // not used in HTTP based integration
    }
}
