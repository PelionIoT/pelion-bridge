/**
 * @file IoTHubMQTTProcessor.java
 * @brief IoTHub MQTT Peer Processor
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
package com.arm.pelion.bridge.coordinator.processors.ms;

import com.arm.pelion.bridge.coordinator.processors.arm.GenericConnectablePeerProcessor;
import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.core.ApiResponse;
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.ConnectionCreator;
import com.arm.pelion.bridge.coordinator.processors.interfaces.ReconnectionInterface;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.transport.HttpTransport;
import com.arm.pelion.bridge.transport.MQTTTransport;
import com.arm.pelion.bridge.transport.Transport;
import com.arm.pelion.bridge.transport.TransportReceiveThread;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;

/**
 * MS IoTHub peer processor based on MQTT
 *
 * @author Doug Anson
 */
public class IoTHubMQTTProcessor extends GenericConnectablePeerProcessor implements Runnable, ReconnectionInterface, ConnectionCreator, Transport.ReceiveListener, PeerProcessorInterface, AsyncResponseProcessor {    
    // maximum number of IoTHub device shadows per worker
    private static final int MAX_IOTHUB_DEVICE_SHADOWS = 25000;                             // limitation: # ephemeral ports
    
    private static final String IOTHUB_DEVICE_PREFIX_SEPARATOR = "-";                       // device prefix separator (if used...)... cannot be an "_"
    private static final long SAS_TOKEN_VALID_TIME_MS = 365 * 24 * 60 * 60 * 1000;          // SAS Token created for 1 year expiration
    private static final long SAS_TOKEN_RECREATE_INTERVAL_MS = 360 * 24 * 60 * 60 * 1000;   // number of days to wait before re-creating the SAS Token
    
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
    private String m_iot_hub_version_tag = null;
    private Thread m_iot_hub_sas_token_refresh_thread = null;
    private boolean m_sas_token_run_refresh_thread = true;
    private long m_iot_hub_sas_token_validity_time_ms = SAS_TOKEN_VALID_TIME_MS;
    private long m_iot_hub_sas_token_recreate_interval_ms= SAS_TOKEN_RECREATE_INTERVAL_MS;

    // constructor (singleton)
    public IoTHubMQTTProcessor(Orchestrator manager, MQTTTransport mqtt, HttpTransport http) {
        this(manager, mqtt, null, http);
    }

    // constructor (with suffix for preferences)
    public IoTHubMQTTProcessor(Orchestrator manager, MQTTTransport mqtt, String suffix, HttpTransport http) {
        super(manager, mqtt, suffix, http);

        // IoTHub Processor Announce
        this.errorLogger().info("Azure IoTHub Processor ENABLED.");
        
        // get the max shadows override
        this.m_max_shadows = manager.preferences().intValueOf("iot_event_hub_max_shadows",this.m_suffix);
        if (this.m_max_shadows <= 0) {
            this.m_max_shadows = MAX_IOTHUB_DEVICE_SHADOWS;
        }
        this.errorLogger().warning("IoTHub: Azure IoTHub Max Shadows (OVERRIDE) Limit: " + this.getMaxNumberOfShadows() + " devices");

        // get the IoTHub version tag
        this.m_iot_hub_version_tag = this.orchestrator().preferences().valueOf("iot_event_hub_version_tag",this.m_suffix);
                
        // Get the IoTHub Connect String
        this.m_iot_hub_connect_string = this.orchestrator().preferences().valueOf("iot_event_hub_connect_string",this.m_suffix);
        
       // initialize the SAS Token and its refresher...
       this.initSASToken(false);
        
        // initialize our MQTT transport list
        this.initMQTTTransportList();
        
        // continue only if configured
        if (this.m_iot_hub_sas_token != null && this.m_iot_hub_sas_token.contains("Goes_Here") == false) {
            // get our defaults
            this.m_mqtt_host = this.orchestrator().preferences().valueOf("iot_event_hub_mqtt_ip_address", this.m_suffix).replace("__IOT_EVENT_HUB__", this.m_iot_hub_name);

            // Observation notification topic
            this.m_iot_hub_observe_notification_topic = this.orchestrator().preferences().valueOf("iot_event_hub_observe_notification_topic", this.m_suffix) + this.m_observation_key;

            // Send CoAP commands back through mDS into the endpoint via these Topics... 
            this.m_iot_hub_coap_cmd_topic_base = this.orchestrator().preferences().valueOf("iot_event_hub_coap_cmd_topic", this.m_suffix).replace("__COMMAND_TYPE__", "#");

            // IoTHub Device Manager - will initialize and upsert our IoTHub bindings/metadata
            this.m_device_manager = new IoTHubDeviceManager(this.m_suffix, http, this, this.m_iot_hub_name, this.m_iot_hub_sas_token);

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
            this.errorLogger().warning("IoTHub: IoTHub Connection String is UNCONFIGURED. Pausing...");
        }
    }
    
    // default # of devices we can shadow
    @Override
    protected int getMaxNumberOfShadows() {
        return MAX_IOTHUB_DEVICE_SHADOWS;
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
                        this.errorLogger().warning("IoTHub: Exception caught while starting SAS Token refresher: " + ex.getMessage());
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
            this.errorLogger().warning("IoTHub: Refreshing SAS Token...");
            
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
            this.errorLogger().info("IoTHub: Parsed Connection String: " + map);
        }
        else {
            // we dont have a connection string... compatibility with older configs
            this.errorLogger().warning("IoTHub: No Connection String supplied. SASToken/HubName required in configuration (OK)");
        }
        
        // return the map
        return map;
    }

    // OVERRIDE: process a received new registration for IoTHub
    @Override
    protected synchronized void processRegistration(Map data, String key) {
        List endpoints = (List) data.get(key);
        if (endpoints != null && endpoints.size() > 0) {
            if ((this.getCurrentEndpointCount() + endpoints.size()) < this.getMaxNumberOfShadows()) {
                for (int i = 0; endpoints != null && i < endpoints.size(); ++i) {
                    Map endpoint = (Map) endpoints.get(i);

                    // get the device ID and device Type
                    String device_type = Utils.valueFromValidKey(endpoint, "endpoint_type", "ept");
                    String device_id = Utils.valueFromValidKey(endpoint, "id", "ep");

                    // ensure we have the endpoint type
                    this.setEndpointTypeFromEndpointName(device_id, device_type);

                    // invoke a GET to get the resource information for this endpoint... we will upsert the Metadata when it arrives
                    this.retrieveEndpointAttributes(endpoint,this);
                }
            }
            else {
                // exceeded the maximum number of device shadows
                this.errorLogger().warning("IoTHub: Exceeded maximum number of device shadows. Limit: " + this.getMaxNumberOfShadows());
            }
        }
        else {
            // nothing to shadow
            this.errorLogger().info("IoTHub: Nothing to shadow (OK).");
        }
    }

    // OVERRIDE: process a re-registration in IoTHub
    @Override
    public void processReRegistration(Map data) {
        List notifications = (List) data.get("reg-updates");
        for (int i = 0; notifications != null && i < notifications.size(); ++i) {
            Map entry = (Map) notifications.get(i);

            // get the device ID
            String device_id = Utils.valueFromValidKey(entry, "id", "ep");
            
            // IOTHUB Prefix
            String iothub_ep_name = this.addDeviceIDPrefix(device_id);

            // DEBUG
            // this.errorLogger().info("IoTHub : CoAP re-registration: " + entry);
            if (this.hasSubscriptions(iothub_ep_name) == false) {
                // no subscriptions - so process as a new registration
                this.errorLogger().info("IoTHub: CoAP re-registration: no subscriptions.. processing as new registration...");
                this.processRegistration(data, "reg-updates");
            }
            else {
                // already subscribed (OK)
                this.errorLogger().info("IoTHub: CoAP re-registration: already subscribed (OK)");
            }
        }
    }

    // OVERRIDE: process a deregistration (deletion TEST)
    @Override
    public String[] processDeregistrations(Map parsed) {        
        // TEST: We can actually DELETE the device on deregistration to test device-delete before the device-delete message goes live
        if (this.orchestrator().deviceRemovedOnDeRegistration() == true) {
            // processing deregistration as device deletion
            this.errorLogger().info("IoTHub: processing de-registration as device deletion (OK).");
            this.processDeviceDeletions(parsed, true);
        }
        else {
            // not processing deregistration as a deletion
            this.errorLogger().info("IoTHub: Not processing de-registration as device deletion (OK).");
        }
        
        // always by default...
        return super.processDeregistrations(parsed);
    }
    
    // OVERRIDE: handle device deletions IoTHub
    @Override
    public String[] processDeviceDeletions(Map parsed) {
        return this.processDeviceDeletions(parsed,false);
    }
    
    // OVERRIDE: process a registrations-expired 
    @Override
    public String[] processRegistrationsExpired(Map parsed) {
       // process a de-registration event
       return this.processDeregistrations(parsed);
    }
    
    // handle device deletions IoTHub
    private String[] processDeviceDeletions(Map parsed,boolean use_deregistration) {
        String[] deletions = null;
        
        // complete processing in base class...
        if (use_deregistration == true) {
            deletions = this.processDeregistrationsBase(parsed);
        }
        else {
            deletions = this.processDeviceDeletionsBase(parsed);
        }
        
        // delete the device shadows...
        for (int i = 0; deletions != null && i < deletions.length; ++i) {
            if (deletions[i] != null && deletions[i].length() > 0) {
                // DEBUG
                this.errorLogger().info("IoTHub: processing device deletion for device: " + deletions[i]);
                
                // IoTHub add-on... 
                this.unsubscribe(deletions[i]);

                // Remove from IoTHub
                this.deleteDevice(deletions[i]);
                
                // remove type
                this.removeEndpointTypeFromEndpointName(deletions[i]);
            }
        }
        
        // return our deletions
        return deletions;
    }
    
    // OVERRIDE: process a mDS notification for IoTHub
    @Override
    public void processNotification(Map data) {
        // DEBUG
        //this.errorLogger().info("processIncomingDeviceServerMessage(IoTHub)...");

        // get the list of parsed notifications
        List notifications = (List) data.get("notifications");
        for (int i = 0; notifications != null && i < notifications.size(); ++i) {
            // we have to process the payload... this may be dependent on being a string core type... 
            Map notification = (Map) notifications.get(i);

            // decode the Payload...
            String b64_coap_payload = (String) notification.get("payload");
            String decoded_coap_payload = Utils.decodeCoAPPayload(b64_coap_payload);

            // DEBUG
            //this.errorLogger().info("IoTHub: Decoded Payload: " + decoded_coap_payload);
            // Try a JSON parse... if it succeeds, assume the payload is a composite JSON value...
            Map json_parsed = this.tryJSONParse(decoded_coap_payload);
            if (json_parsed != null && json_parsed.isEmpty() == false) {
                // add in a JSON object payload value directly... 
                notification.put("value", Utils.retypeMap(json_parsed, this.fundamentalTypeDecoder()));             // its JSON (flat...)                                                   // its JSON 
            }
            else {
                // add in a decoded payload value as a fundamental type...
                notification.put("value", this.fundamentalTypeDecoder().getFundamentalValue(decoded_coap_payload)); // its a Float, Integer, or String
            }

            // get our Pelion endpoint name
            String ep_name = Utils.valueFromValidKey(notification, "id", "ep");

            // IOTHUB Prefix
            String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

            // we will send the raw CoAP JSON... IoTHub can parse that... 
            String coap_raw_json = this.jsonGenerator().generateJson(notification);

            // strip off []...
            String coap_json_stripped = this.stripArrayChars(coap_raw_json);

            // encapsulate into a coap/device packet...
            String iot_event_hub_coap_json = coap_json_stripped;

            // DEBUG
            this.errorLogger().info("IoTHub: CoAP notification (STR): " + iot_event_hub_coap_json);

            // send to IoTHub...
            if (this.mqtt(iothub_ep_name) != null) {
                boolean status = this.mqtt(iothub_ep_name).sendMessage(this.customizeTopic(this.m_iot_hub_observe_notification_topic, iothub_ep_name, ep_name, null), iot_event_hub_coap_json, QoS.AT_MOST_ONCE);
                if (status == true) {
                    // not connected
                    this.errorLogger().info("IoTHub: CoAP notification sent. SUCCESS");
                }
                else {
                    // send failed
                    this.errorLogger().warning("IoTHub: CoAP notification not sent. SEND FAILED");
                }
            }
            else {
                // not connected
                this.errorLogger().info("IoTHub: CoAP notification not sent. NOT CONNECTED");
            }
        }
    }

    // IoTHub Specific: subscribe to the IoTHub MQTT topics
    @Override
    public void subscribeToTopics(String ep_name, Topic topics[]) {
        // IOTHUB DeviceID Prefix
        this.errorLogger().info("IoTHub: subscribing to topics...");
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);
        this.mqtt(iothub_ep_name).subscribe(topics);
        
        // for IoTHub, we dont have to subscribe to any special topics for API requests... 
    }

    // IoTHub Specific: does this endpoint already have registered subscriptions?
    @Override
    protected boolean hasSubscriptions(String ep_name) {
        try {
            // IOTHUB DeviceID Prefix
            String iothub_ep_name = this.addDeviceIDPrefix(ep_name);
            if (this.m_endpoints.get(iothub_ep_name) != null) {
                HashMap<String, Object> topic_data = (HashMap<String, Object>) this.m_endpoints.get(iothub_ep_name);
                if (topic_data != null && topic_data.size() > 0) {
                    return true;
                }
            }
        }
        catch (Exception ex) {
            //silent
        }
        return false;
    }

    // IoTHub Specific: register topics for CoAP commands
    private void subscribe(String ep_name,String ep_type) {
        // IOTHUB Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);
        HashMap<String, Object> topic_data = this.createEndpointTopicData(iothub_ep_name, ep_type);
        this.subscribe(ep_name,ep_type,topic_data,this);
        
    }
    
    // IoTHub Specific: register topics for CoAP commands
    @Override
    protected void subscribe(String ep_name, String ep_type, HashMap<String, Object> topic_data,ConnectionCreator cc) {
        // IOTHUB Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

        if (iothub_ep_name != null && this.validateMQTTConnection(cc, ep_name, ep_type, null)) {
            // DEBUG
            this.orchestrator().errorLogger().info("IoTHub: Subscribing to CoAP command topics for endpoint: " + ep_name);
            try {
                if (topic_data != null) {
                    // get,put,post,delete enablement
                    this.m_endpoints.remove(iothub_ep_name);
                    this.m_endpoints.put(iothub_ep_name, topic_data);
                    this.setEndpointTypeFromEndpointName(ep_name, ep_type);
                    this.subscribeToTopics(iothub_ep_name, (Topic[]) topic_data.get("topic_list"));
                }
                else {
                    this.orchestrator().errorLogger().warning("IoTHub: GET/PUT/POST/DELETE topic data NULL. GET/PUT/POST/DELETE disabled");
                }
            }
            catch (Exception ex) {
                this.orchestrator().errorLogger().info("IoTHub: Exception in subscribe for " + iothub_ep_name + " : " + ex.getMessage());
            }
        }
        else {
            this.orchestrator().errorLogger().info("IoTHub: NULL Endpoint name in subscribe()... ignoring...");
        }
    }

    // IoTHub Specific: un-register topics for CoAP commands
    @Override
    protected boolean unsubscribe(String ep_name) {
        boolean unsubscribed = false;

        // IOTHUB Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

        if (iothub_ep_name != null && this.mqtt(iothub_ep_name) != null) {
            // DEBUG
            this.orchestrator().errorLogger().info("IoTHub: Un-Subscribing to CoAP command topics for endpoint: " + iothub_ep_name);
            try {
                HashMap<String, Object> topic_data = (HashMap<String, Object>) this.m_endpoints.get(iothub_ep_name);
                if (topic_data != null) {
                    // unsubscribe...
                    this.mqtt(iothub_ep_name).unsubscribe((String[]) topic_data.get("topic_string_list"));
                }
                else {
                    // not in subscription list (OK)
                    this.orchestrator().errorLogger().info("IoTHub: Endpoint: " + iothub_ep_name + " not in subscription list (OK).");
                    unsubscribed = true;
                }
            }
            catch (Exception ex) {
                this.orchestrator().errorLogger().info("IoTHub: Exception in unsubscribe for " + ep_name + " : " + ex.getMessage());
            }
        }
        else if (this.mqtt(iothub_ep_name) != null) {
            this.orchestrator().errorLogger().info("IoTHub: NULL Endpoint name... ignoring unsubscribe()...");
            unsubscribed = true;
        }
        else {
            this.orchestrator().errorLogger().info("IoTHub: No MQTT connection for " + iothub_ep_name + "... ignoring unsubscribe()...");
            unsubscribed = true;
        }

        // clean up
        if (iothub_ep_name != null) {
            this.m_endpoints.remove(iothub_ep_name);
        }

        // return the unsubscribe status
        return unsubscribed;
    }

    // IoTHub Specific: get a topic element (parsed as a URL with a key as a String)
    private String getTopicElement(String topic, String key) {
        String value = null;

        try {
            // split by forward slash
            String tmp_slash[] = topic.split("/");

            // take the last element and split it again, by &
            if (tmp_slash != null && tmp_slash.length > 0) {
                String tmp_properties[] = tmp_slash[tmp_slash.length - 1].split("&");
                for (int i = 0; tmp_properties != null && i < tmp_properties.length && value == null; ++i) {
                    String prop[] = tmp_properties[i].split("=");
                    if (prop != null && prop.length == 2 && prop[0].equalsIgnoreCase(key) == true) {
                        value = prop[1];
                    }
                }
            }
        }
        catch (Exception ex) {
            // Exception during parse
            this.errorLogger().info("IoTHub; WARNING: getTopicElement: Exception: " + ex.getMessage());
        }

        // DEBUG
        if (value != null) {
            // value found
            this.errorLogger().info("IoTHub: getTopicElement: key: " + key + "  value: " + value);
        }
        else {
            // value not found
            this.errorLogger().info("IoTHub: getTopicElement: key: " + key + "  value: NULL");
        }

        // return the value
        return value;
    }
    
    // send the API Response back through the topic
    private void sendApiResponse(String iothub_ep_name,String topic,ApiResponse response) {        
        // publish
        this.mqtt(iothub_ep_name).sendMessage(topic, response.createResponseJSON());
    }

    // CoAP command handler - processes CoAP commands coming over MQTT channel
    @Override
    public void onMessageReceive(String topic, String message) {
        // DEBUG
        this.errorLogger().info("IoTHub: Topic: " + topic + " message: " + message);

        // parse the topic to get the endpoint
        // format: devices/__IOTHUB_EPNAME__/messages/devicebound/#
        // IOTHUB DevicIDPrefix
        String iothub_ep_name = this.getEndpointNameFromTopic(topic);
        String ep_name = removeDeviceIDPrefix(iothub_ep_name);
        
        // process any API requests...
        if (this.isApiRequest(message)) {
            // process the message
            String reply_topic = this.customizeTopic(this.m_iot_hub_observe_notification_topic, iothub_ep_name, ep_name, null);
            reply_topic = reply_topic.replace(this.m_observation_key,this.m_api_response_key);
            this.sendApiResponse(iothub_ep_name,reply_topic,this.processApiRequestOperation(message));
            
            // return as we are done with the API request... no AsyncResponses necessary for raw API requests...
            return;
        }

        // pull the CoAP Path URI from the message itself... its JSON... 
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        String uri = this.getCoAPURI(message);
        if (uri == null || uri.length() == 0) {
            // optionally pull the CoAP URI Path from the MQTT topic (SECONDARY)
            uri = this.getResourceURIFromTopic(topic);
        }

        // pull the CoAP Payload from the message itself... its JSON... 
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        String value = this.getCoAPValue(message);

        // pull the CoAP verb from the message itself... its JSON... (PRIMARY)
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        String coap_verb = this.getCoAPVerb(message);
        if (coap_verb == null || coap_verb.length() == 0) {
            // optionally pull the CoAP verb from the MQTT Topic (SECONDARY)
            coap_verb = this.getCoAPVerbFromTopic(topic);
        }

        // if the ep_name is wildcarded... get the endpoint name from the JSON payload
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        if (iothub_ep_name == null || iothub_ep_name.length() <= 0 || iothub_ep_name.equalsIgnoreCase("+")) {
            iothub_ep_name = this.addDeviceIDPrefix(this.getCoAPEndpointName(message));
        }

        // if there are mDC/mDS REST options... lets add them
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get", "options":"noResp=true" }
        String options = this.getRESTOptions(message);

        // dispatch the coap resource operation request
        String response = this.orchestrator().processEndpointResourceOperation(coap_verb, this.removeDeviceIDPrefix(iothub_ep_name), uri, value, options);

        // examine the response
        if (response != null && response.length() > 0) {
            // SYNC: We only process AsyncResponses from GET verbs... we dont sent HTTP status back through IoTHub.
            this.errorLogger().info("IoTHub: Response: " + response);

            // AsyncResponse detection and recording...
            if (this.isAsyncResponse(response) == true) {
                // CoAP GET and PUT provides AsyncResponses...
                if (coap_verb.equalsIgnoreCase("get") == true || coap_verb.equalsIgnoreCase("put") == true) {
                    // its an AsyncResponse.. so record it...
                    this.recordAsyncResponse(response, coap_verb, this.mqtt(iothub_ep_name), this, topic, this.getReplyTopic(iothub_ep_name, this.getEndpointTypeFromEndpointName(iothub_ep_name), uri), message, iothub_ep_name, uri);
                }
                else {
                    // we ignore AsyncResponses to PUT,POST,DELETE
                    this.errorLogger().info("IoTHub: Ignoring AsyncResponse for " + coap_verb + " (OK).");
                }
            }
            else if (coap_verb.equalsIgnoreCase("get")) {
                // not an AsyncResponse... so just emit it immediately... only for GET...
                this.errorLogger().info("IoTHub: Response: " + response + " from GET... creating observation...");

                // we have to format as an observation...
                String observation = this.createObservation(coap_verb, iothub_ep_name, uri, response);

                // DEBUG
                this.errorLogger().info("IoTHub: Sending Observation(GET): " + observation);

                // send the observation (GET reply)...
                if (this.mqtt(iothub_ep_name) != null) {
                    String reply_topic = this.customizeTopic(this.m_iot_hub_observe_notification_topic, iothub_ep_name, ep_name, null);
                    reply_topic = reply_topic.replace(this.m_observation_key, this.m_cmd_response_key);
                    boolean status = this.mqtt(iothub_ep_name).sendMessage(reply_topic, observation, QoS.AT_MOST_ONCE);
                    if (status == true) {
                        // success
                        this.errorLogger().info("IoTHub: CoAP observation(get) sent. SUCCESS");
                    }
                    else {
                        // send failed
                        this.errorLogger().warning("IoTHub: CoAP observation(get) not sent. SEND FAILED");
                    }
                }
                else {
                    // not connected
                    this.errorLogger().warning("IIoTHub: CoAP observation(get) not sent. NOT CONNECTED");
                }
            }
        }
    }

    // IoTHub Specific: default formatter for AsyncResponse replies (IoTHub specific - ADD DEVICE PREFIX to the message...) 
    @Override
    public String formatAsyncResponseAsReply(Map async_response, String verb) {
        // DEBUG
        this.errorLogger().info("IoTHub(" + verb + ") AsyncResponse: " + async_response);

        if (verb != null && verb.equalsIgnoreCase("GET") == true) {
            try {
                // DEBUG
                this.errorLogger().info("IoTHub: CoAP AsyncResponse for GET: " + async_response);

                // get the payload from the ith entry
                String payload = (String) async_response.get("payload");
                if (payload != null) {
                    // trim 
                    payload = payload.trim();

                    // parse if present
                    if (payload.length() > 0) {
                        // Base64 decode
                        String value = Utils.decodeCoAPPayload(payload);

                        // build out the response
                        String uri = this.getURIFromAsyncID((String) async_response.get("id"));
                        String ep_name = this.getEndpointNameFromAsyncID((String) async_response.get("id"));

                        // IOTHUB DeviceID Prefix
                        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

                        // build out the observation
                        String message = this.createObservation(verb, iothub_ep_name, uri, value);

                        // DEBUG
                        this.errorLogger().info("IoTHub: Created(" + verb + ") GET observation: " + message);

                        // return the message
                        return message;
                    }
                }
            }
            catch (Exception ex) {
                // Error in creating the observation message from the AsyncResponse GET reply... 
                this.errorLogger().warning("IoTHub: Exception in formatAsyncResponseAsReply(): ", ex);
            }
        }

        // Handle AsyncReplies that are CoAP PUTs
        if (verb != null && verb.equalsIgnoreCase("PUT") == true) {
            try {
                // check to see if we have a payload or not... 
                String payload = (String) async_response.get("payload");
                if (payload != null) {
                    // trim 
                    payload = payload.trim();

                    // parse if present
                    if (payload.length() > 0) {
                        // Base64 decode
                        String value = Utils.decodeCoAPPayload(payload);

                        // build out the response
                        String uri = this.getURIFromAsyncID((String) async_response.get("id"));
                        String ep_name = this.getEndpointNameFromAsyncID((String) async_response.get("id"));

                        // IOTHUB DeviceID Prefix
                        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

                        // build out the observation
                        String message = this.createObservation(verb, iothub_ep_name, uri, value);

                        // DEBUG
                        this.errorLogger().info("IoTHub: Created(" + verb + ") PUT Observation: " + message);

                        // return the message
                        return message;
                    }
                }
                else {
                    // no payload... so we simply return the async-id
                    String value = (String) async_response.get("async-id");

                    // build out the response
                    String uri = this.getURIFromAsyncID((String) async_response.get("id"));
                    String ep_name = this.getEndpointNameFromAsyncID((String) async_response.get("id"));

                    // IOTHUB DeviceID Prefix
                    String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

                    // build out the observation
                    String message = this.createObservation(verb, iothub_ep_name, uri, value);

                    // DEBUG
                    this.errorLogger().info("IoTHub: Created(" + verb + ") PUT Observation: " + message);

                    // return message
                    return message;
                }
            }
            catch (Exception ex) {
                // Error in creating the observation message from the AsyncResponse PUT reply... 
                this.errorLogger().warning("IoTHub: Exception in formatAsyncResponseAsReply(): ", ex);
            }
        }

        // return null message
        return null;
    }

    // IoTHub Specific: process new device registration
    @Override
    protected synchronized Boolean registerNewDevice(Map message) {
        if (this.m_device_manager != null) {
            // create the device in IoTHub
            Boolean success = this.m_device_manager.registerNewDevice(message);
            if (success == true) {
                // get the device ID and device Type
                String device_type = Utils.valueFromValidKey(message, "endpoint_type", "ept");
                String device_id = Utils.valueFromValidKey(message, "id", "ep");

                this.setEndpointTypeFromEndpointName(device_id, device_type);

                // IOTHUB DeviceID Prefix
                String iothub_ep_name = this.addDeviceIDPrefix(device_id);

                // if successful, validate (i.e. add...) an MQTT Connection
                if (success == true) {
                    success = this.validateMQTTConnection(this, device_id, device_type, null);
                }
            }

            // return status
            return success;
        }
        return false;
    }

    // IoTHub Specific: process device deletion
    @Override
    protected synchronized Boolean deleteDevice(String ep_name) {
        if (this.m_device_manager != null && ep_name != null && ep_name.length() > 0) {
            // IOTHUB DeviceID Prefix
            String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

            // DEBUG
            this.errorLogger().warning("IoTHub: deleting device: " + ep_name);

            // remove the MQTT transport instance
            this.disconnect(ep_name);
            this.remove(iothub_ep_name);
            
            // remove the device's MQTT thread listener 
            if (this.m_mqtt_thread_list.get(iothub_ep_name) != null) {
                try {
                    this.m_mqtt_thread_list.get(iothub_ep_name).disconnect();
                }
                catch (Exception ex) {
                    // note but continue...
                    this.errorLogger().warning("IoTHub: exception during device deletion", ex);
                }
                this.m_mqtt_thread_list.remove(iothub_ep_name);
            }

            // remove the device from IoTHub
            if (this.m_device_manager.deleteDevice(ep_name) == false) {
                this.errorLogger().warning("IoTHub: unable to de-register device from IoTHub...");
            }
            
            // remove type from the type list
            this.removeEndpointTypeFromEndpointName(ep_name);
        }
        
        // aggressive deletion
        return true;
    }

    // IoTHub Specific: AsyncResponse response processor
    @Override
    public synchronized boolean processAsyncResponse(Map endpoint) {
        // with the attributes added, we finally create the device in MS IoTHub
        this.completeNewDeviceRegistration(endpoint);
            
        // return our processing status
        return true;
    }
    
    // IOTHUB Specific: restart our device connection 
    @Override
    public boolean startReconnection(String ep_name,String ep_type,Topic topics[]) {
        if (this.m_device_manager != null) {
            // IOTHUB DeviceID Prefix
            String iothub_ep_name = this.addDeviceIDPrefix(ep_name);
            
            // stop the current listener thread
            this.stopListenerThread(iothub_ep_name);
            
            // clean up old MQTT connection (will remove as well...)
            this.disconnect(ep_name);
            
            // Create a new device record
            HashMap<String,Serializable> ep = new HashMap<>();
            ep.put("ep",ep_name);
            ep.put("ept", ep_type);
            
            // DEBUG
            this.errorLogger().info("startReconnection: EP: " + ep);            
            
            // deregister the old device (it may be gone already...)
            this.m_device_manager.deleteDevice(ep_name);
            
            // sleep for abit
            Utils.waitForABit(this.errorLogger(), this.m_reconnect_sleep_time_ms);
            
            // now create a new device
            this.completeNewDeviceRegistration(ep);
            
            // sleep for abit
            Utils.waitForABit(this.errorLogger(), this.m_reconnect_sleep_time_ms);
            
            // create a new MQTT connection (will re-subscribe and start listener threads...)
            return this.createAndStartMQTTForEndpoint(ep_name, ep_type, topics);
        }
        return false;
    }

    // IoTHub Specific: complete processing of adding the new device
    @Override
    public void completeNewDeviceRegistration(Map endpoint) {
        // create the new device type
        String device_type = Utils.valueFromValidKey(endpoint, "endpoint_type", "ept");
        String device_id = Utils.valueFromValidKey(endpoint, "id", "ep");
        
        try {
            // create the device in IoTHub
            this.errorLogger().info("completeNewDeviceRegistration: calling registerNewDevice(): " + endpoint);
            this.registerNewDevice(endpoint);
            this.errorLogger().info("IoTHub: registerNewDevice() completed");
        }
        catch (Exception ex) {
            this.errorLogger().warning("IoTHub: caught exception in registerNewDevice(): " + endpoint, ex);
        }

        try {
            // subscribe for IoTHub as well..
            this.errorLogger().info("IoTHub: calling subscribe(): " + endpoint);
            this.subscribe(device_id, device_type);
            this.errorLogger().info("IoTHub: subscribe() completed");
        }
        catch (Exception ex) {
            this.errorLogger().warning("IoTHub: caught exception in subscribe(): " + endpoint, ex);
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
    
    // IoTHub Specific: create the endpoint IoTHub topic data
    @Override
    protected HashMap<String, Object> createEndpointTopicData(String ep_name, String ep_type) {
        HashMap<String, Object> topic_data = null;
        if (this.m_iot_hub_coap_cmd_topic_base != null) {
            Topic[] list = new Topic[m_num_coap_topics];
            String[] topic_string_list = new String[m_num_coap_topics];

            // IOTHUB DeviceID Prefix
            String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

            topic_string_list[0] = this.customizeTopic(this.m_iot_hub_coap_cmd_topic_base, iothub_ep_name, ep_name, ep_type);
            for (int i = 0; i < m_num_coap_topics; ++i) {
                list[i] = new Topic(topic_string_list[i], QoS.AT_LEAST_ONCE);
            }
            topic_data = new HashMap<>();
            topic_data.put("topic_list", list);
            topic_data.put("topic_string_list", topic_string_list);
        }
        return topic_data;
    }
    
    // IoTHub Specific: get our defaulted reply topic
    @Override
    public String getReplyTopic(String ep_name, String ep_type, String def) {
        // IOTHUB DeviceID Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);
        return this.customizeTopic(this.m_iot_hub_observe_notification_topic, iothub_ep_name, ep_name, ep_type).replace(this.m_observation_key, this.m_cmd_response_key);
    }

    // final customization of a MQTT Topic...
    private String customizeTopic(String topic, String iothub_ep_name, String ep_name, String ep_type) {
        String cust_topic = topic.replace("__EPNAME__", iothub_ep_name);
        if (ep_type == null) {
            ep_type = this.getEndpointTypeFromEndpointName(ep_name);
        }
        if (ep_type != null) {
            cust_topic = cust_topic.replace("__DEVICE_TYPE__", ep_type);
            this.errorLogger().info("IoTHub Customized Topic: " + cust_topic);
        }
        else {
            // replace with "default"
            cust_topic = cust_topic.replace("__DEVICE_TYPE__", "default");
            
            // WARN
            this.errorLogger().warning("IoTHub Customized Topic (EPT UNK): " + cust_topic);
        }
        return cust_topic;
    }
    
    // OVERRIDE stop the listener thread
    @Override
    protected void stopListenerThread(String iothub_ep_name) {
        // ensure we only have 1 thread/endpoint
        if (this.m_mqtt_thread_list.get(iothub_ep_name) != null) {
            TransportReceiveThread listener = (TransportReceiveThread) this.m_mqtt_thread_list.get(iothub_ep_name);
            this.m_mqtt_thread_list.remove(iothub_ep_name);
            listener.halt();
        }
    }
    
    // start our listener thread
    private void startListenerThread(String ep_name,MQTTTransport mqtt) {
        // IOTHUB DeviceID Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);
        
        // ensure we only have 1 thread/endpoint
        this.m_mqtt_thread_list.remove(iothub_ep_name);
        
        // create and start the listener
        TransportReceiveThread listener = new TransportReceiveThread(mqtt);
        listener.setOnReceiveListener(this);
        this.m_mqtt_thread_list.put(iothub_ep_name, listener);
        listener.start();
    }
    
    // IoTHub Specific: add a MQTT transport for a given endpoint - this is how MS IoTHub MQTT integration works... 
    @Override
    public boolean createAndStartMQTTForEndpoint(String ep_name, String ep_type, Topic topics[]) {
        boolean connected = false; 
        
        // make sure we configured
        if (this.m_iot_hub_sas_token != null && this.m_iot_hub_sas_token.contains("Goes_Here") == false) {
            // IOTHUB DeviceID Prefix
            String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

            if (this.mqtt(iothub_ep_name) == null) {
                // create a new MQTT Transport instance
                MQTTTransport mqtt = new MQTTTransport(this.errorLogger(), this.preferences(), this);
                if (mqtt != null) {
                    // set the additional endpoint details
                    mqtt.setEndpointDetails(ep_name, ep_type);

                    // MQTT username is based upon the device ID (endpoint_name)
                    String username = this.orchestrator().preferences().valueOf("iot_event_hub_mqtt_username", this.m_suffix).replace("__IOT_EVENT_HUB__", this.m_iot_hub_name).replace("__EPNAME__", iothub_ep_name);

                    // add a version tag per: https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-mqtt-support
                    username = username + "/" + this.m_iot_hub_version_tag;

                    // set the creds for the IoTHub MQTT Transport instance
                    mqtt.setClientID(iothub_ep_name);
                    mqtt.setUsername(username);
                    mqtt.setPassword(this.m_device_manager.createMQTTPassword(iothub_ep_name));

                    // IoTHub only works with SSL... 
                    mqtt.useSSLConnection(true);

                    // but DONT initialize the SSL context with self signed certs/keys
                    mqtt.noSelfSignedCertsOrKeys(true);

                    // add it to the list indexed by the endpoint name... not the clientID...
                    this.addMQTTTransport(iothub_ep_name, mqtt);

                    // DEBUG
                    this.errorLogger().warning("IoTHub: connecting MQTT for endpoint: " + ep_name + " type: " + ep_type + "...");

                    // connect and start listening... 
                    if (this.connect(ep_name) == true) {
                        // DEBUG
                        this.errorLogger().warning("IoTHub: connection SUCCESS");
                        this.errorLogger().info("IoTHub: Creating and registering listener Thread for endpoint: " + iothub_ep_name + " type: " + ep_type);

                        // start the listener thread
                        this.startListenerThread(ep_name, mqtt);

                        // if we have topics in our param list, lets go ahead and subscribe
                        if (topics != null) {
                            // DEBUG
                            this.errorLogger().info("IoTHub: re-subscribing to topics...");

                            // re-subscribe
                            this.mqtt(iothub_ep_name).subscribe(topics);
                        }

                        // we are connected
                        connected = true;
                    }
                    else {
                        // ensure we only have 1 thread/endpoint
                        this.stopListenerThread(iothub_ep_name);
                        
                        // unable to connect!
                        this.errorLogger().critical("IoTHub: Unable to connect MQTT for endpoint: " + ep_name + " type: " + ep_type);
                        this.disconnect(ep_name);
                    }
                }
                else {
                    // unable to allocate MQTT transport
                    this.errorLogger().critical("IoTHub: CRITICAL: Unable to allocate MQTT transport... ERROR");
                    connected = false;
                }
            }
            else {
                // already connected... just ignore
                this.errorLogger().info("IoTHub: already have connection for " + iothub_ep_name + " (OK)");
                connected = true;
            }
        }
        else {
            // we are unconfigured
            this.errorLogger().warning("IoTHub: Unable to connect to IoTHub - UNCONFIGURED (OK)");
        }
        
        // return the connection status
        return connected;
    }

    // IoTHub Specific: validate the MQTT Connection
    @Override
    protected synchronized boolean validateMQTTConnection(ConnectionCreator cc,String ep_name, String ep_type, Topic topics[]) {
        // IOTHUB DeviceID Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

        // see if we already have a connection for this endpoint...
        if (this.mqtt(iothub_ep_name) == null) {
            // create a MQTT connection for this endpoint... 
            cc.createAndStartMQTTForEndpoint(ep_name, ep_type, null);
        }

        // return our connection status
        return this.isConnected(ep_name);
    }
    
    // Connection to IoTHub MQTT vs. generic MQTT...
    private boolean connect(String ep_name) {
        // IOTHUB Prefix 
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

        // if not connected attempt
        if (!this.isConnected(ep_name)) {
            if (this.mqtt(iothub_ep_name).connect(this.m_mqtt_host, this.m_mqtt_port, iothub_ep_name, true) == true) {
                this.orchestrator().errorLogger().info("IoTHub: Setting CoAP command listener...");
                this.mqtt(iothub_ep_name).setOnReceiveListener(this);
                this.orchestrator().errorLogger().info("IoTHub: connection completed successfully");
            }
        }
        else {
            // already connected
            this.orchestrator().errorLogger().info("IoTHub: Already connected (OK)...");
        }

        // return our connection status
        this.orchestrator().errorLogger().info("IoTHub: Device: " + ep_name + " MQTT connection status=" + this.isConnected(ep_name));
        return this.isConnected(ep_name);
    }
    
    // IoTHub Specific: disconnect
    @Override
    protected void disconnect(String ep_name) {
        // IOTHUB DeviceID Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);
        
        // if connected, disconnect MQTT handle...
        if (this.isConnected(ep_name) == true) {
            // DEBUG
            this.errorLogger().warning("IoTHub: Disconnecting MQTT for device: " + ep_name + "...");
            this.mqtt(iothub_ep_name).disconnect(true);
        }
        
        // remove from the MQTT connection list...
        this.remove(iothub_ep_name);
    }

    // IoTHub Specific: are we connected
    @Override
    protected boolean isConnected(String ep_name) {
        // IOTHUB DeviceID Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);
        if (this.mqtt(iothub_ep_name) != null) {
            return this.mqtt(iothub_ep_name).isConnected();
        }
        return false;
    }
    
    // IoTHub Specific: get the endpoint name from the MQTT topic
    @Override
    public String getEndpointNameFromTopic(String topic) {
        // format: devices/__EPNAME__/messages/devicebound/#
        return this.getTopicElement(topic, 1);
    }

    // IoTHub Specific: get the CoAP verb from the MQTT topic
    @Override
    public String getCoAPVerbFromTopic(String topic) {
        // format: devices/__EPNAME__/messages/devicebound/coap_verb=put....
        return this.getTopicElement(topic, "coap_verb");
    }

    // IoTHub Specific: get the CoAP URI from the MQTT topic
    @Override
    public String getResourceURIFromTopic(String topic) {
        // format: devices/__EPNAME__/messages/devicebound/coap_uri=....
        return this.getTopicElement(topic, "coap_uri");
    }
    
    // IoTHub Specific: we have to override the creation of the authentication hash.. it has to be dependent on a given endpoint name
    @Override
    public String createAuthenticationHash() {
        return Utils.createHash(this.m_iot_hub_sas_token);
    }

    // OVERRIDE: initListener() needs to accomodate a MQTT connection for each endpoint
    @Override
    @SuppressWarnings("empty-statement")
    public void initListener() {
        // unused
    }

    // OVERRIDE: stopListener() needs to accomodate a MQTT connection for each endpoint
    @Override
    @SuppressWarnings("empty-statement")
    public void stopListener() {
        // unused
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
}
