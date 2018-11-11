/**
 * @file AWSIoTMQTTProcessor.java
 * @brief AWS IoT MQTT Peer Processor
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
package com.arm.pelion.bridge.coordinator.processors.aws.mqtt;

import com.arm.pelion.bridge.coordinator.processors.aws.AWSIoTDeviceManager;
import com.arm.pelion.bridge.coordinator.processors.arm.GenericConnectablePeerProcessor;
import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.arm.PelionProcessor;
import com.arm.pelion.bridge.core.ApiResponse;
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.ConnectionCreator;
import com.arm.pelion.bridge.coordinator.processors.interfaces.DeviceManagerToPeerProcessorInterface;
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
 * AWS IoT peer processor based on MQTT
 *
 * @author Doug Anson
 */
public class AWSIoTProcessor extends GenericConnectablePeerProcessor implements ReconnectionInterface, DeviceManagerToPeerProcessorInterface, ConnectionCreator, Transport.ReceiveListener, PeerProcessorInterface, AsyncResponseProcessor {
    // maximum number of AWSIOT device shadows per worker
    private static final int MAX_AWSIOT_DEVICE_SHADOWS = 25000;     // limitation: # ephemeral ports
    
    private String m_aws_iot_observe_notification_topic = null;
    private String m_aws_iot_coap_cmd_topic_get = null;
    private String m_aws_iot_coap_cmd_topic_put = null;
    private String m_aws_iot_coap_cmd_topic_post = null;
    private String m_aws_iot_coap_cmd_topic_delete = null;

    // AWSIoT Device Manager
    private AWSIoTDeviceManager m_device_manager = null;
    
    // we are configured
    private boolean m_configured = false;
        
    // constructor (singleton)
    public AWSIoTProcessor(Orchestrator manager, MQTTTransport mqtt, HttpTransport http) {
        this(manager, mqtt, null, http);
    }

    // constructor (with suffix for preferences)
    public AWSIoTProcessor(Orchestrator manager, MQTTTransport mqtt, String suffix, HttpTransport http) {
        super(manager, mqtt, suffix, http);

        // AWSIoT Processor Announce
        this.errorLogger().warning("Amazon AWSIoT Processor ENABLED. (MQTT)");
        
        // get the max shadows override
        this.m_max_shadows = manager.preferences().intValueOf("aws_iot_max_shadows",this.m_suffix);
        if (this.m_max_shadows <= 0) {
            this.m_max_shadows = MAX_AWSIOT_DEVICE_SHADOWS;
        }
        this.errorLogger().warning("AWSIoT(MQTT): AWSIoT Max Shadows (OVERRIDE) Limit: " + this.getMaxNumberOfShadows() + " devices");

        // Observation notification topic
        this.m_aws_iot_observe_notification_topic = this.orchestrator().preferences().valueOf("aws_iot_observe_notification_topic",this.m_suffix);

        // if unified format enabled, observation == notify
        if (this.unifiedFormatEnabled()) {
            this.m_aws_iot_observe_notification_topic = this.m_aws_iot_observe_notification_topic.replace("observation", this.m_observation_key);
        }

        // initialize the topic root (AWS customized)
        this.initTopicRoot("aws_iot_topic_root");

        // Send CoAP commands back through mDS into the endpoint via these Topics... 
        this.m_aws_iot_coap_cmd_topic_get = this.orchestrator().preferences().valueOf("aws_iot_coap_cmd_topic", this.m_suffix).replace("__TOPIC_ROOT__", this.getTopicRoot()).replace("__COMMAND_TYPE__", "get");
        this.m_aws_iot_coap_cmd_topic_put = this.orchestrator().preferences().valueOf("aws_iot_coap_cmd_topic", this.m_suffix).replace("__TOPIC_ROOT__", this.getTopicRoot()).replace("__COMMAND_TYPE__", "put");
        this.m_aws_iot_coap_cmd_topic_post = this.orchestrator().preferences().valueOf("aws_iot_coap_cmd_topic", this.m_suffix).replace("__TOPIC_ROOT__", this.getTopicRoot()).replace("__COMMAND_TYPE__", "post");
        this.m_aws_iot_coap_cmd_topic_delete = this.orchestrator().preferences().valueOf("aws_iot_coap_cmd_topic", this.m_suffix).replace("__TOPIC_ROOT__", this.getTopicRoot()).replace("__COMMAND_TYPE__", "delete");

        // AWSIoT Device Manager - will initialize and upsert our AWSIoT bindings/metadata
        this.m_device_manager = new AWSIoTDeviceManager(this.m_suffix, http, this);
    
        // initialize our MQTT transport list
        this.initMQTTTransportList();
        
        // configured?
        String aws_iot_region = this.orchestrator().preferences().valueOf("aws_iot_region",this.m_suffix);
        String aws_iot_access_key_id = this.orchestrator().preferences().valueOf("aws_iot_access_key_id",this.m_suffix);
        String aws_iot_secret_access_key = this.orchestrator().preferences().valueOf("aws_iot_secret_access_key",this.m_suffix);
        if (aws_iot_region != null && aws_iot_region.length() > 0 &&  aws_iot_region.contains("Goes_Here") == false  &&
            aws_iot_access_key_id != null && aws_iot_access_key_id.length() > 0 &&  aws_iot_access_key_id.contains("Goes_Here") == false  && 
            aws_iot_secret_access_key != null && aws_iot_secret_access_key.length() > 0 &&  aws_iot_secret_access_key.contains("Goes_Here") == false) {
            // we are configured
            this.m_configured = true;
        }
    }
    
    // OVERRIDE: process a new registration in AWSIoT
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

                    // invoke a GET to get the resource information for this endpoint... we will upsert the Metadata when it arrives
                    this.retrieveEndpointAttributes(endpoint,this);
                }
            }
            else {
                // exceeded the maximum number of device shadows
                this.errorLogger().warning("AWSIoT(MQTT): Exceeded maximum number of device shadows. Limit: " + this.getMaxNumberOfShadows());
            }
        }
        else {
            // nothing to shadow
            this.errorLogger().info("AWSIoT(MQTT): Nothing to shadow (OK).");
        }
    }
    
    // OVERRIDE: subscirption to topics
    @Override
    public void subscribeToTopics(String ep_name, Topic topics[]) {
        super.subscribeToTopics(ep_name, topics);
    }

    // OVERRIDE: process a re-registration in AWSIoT
    @Override
    public void processReRegistration(Map data) {
        List notifications = (List) data.get("reg-updates");
        for (int i = 0; notifications != null && i < notifications.size(); ++i) {
            Map entry = (Map) notifications.get(i);
            // DEBUG
            // this.errorLogger().info("AWSIoT : CoAP re-registration: " + entry);
            if (this.hasSubscriptions((String) entry.get("ep")) == false) {
                // no subscriptions - so process as a new registration
                this.errorLogger().info("AWSIoT(MQTT): CoAP re-registration: no subscriptions.. processing as new registration...");
                this.processRegistration(data, "reg-updates");
            }
            else {
                // already subscribed (OK)
                this.errorLogger().info("AWSIoT(MQTT): CoAP re-registration: already subscribed (OK)");
            }
        }
    }
    
    // OVERRIDE: process a deregistration (deletion TEST)
    @Override
    public String[] processDeregistrations(Map parsed) {        
        // process the base class...
        String deletions[] = this.processDeregistrationsBase(parsed);
            
        // TEST: We can actually DELETE the device on deregistration to test device-delete before the device-delete message goes live
        if (this.orchestrator().deviceRemovedOnDeRegistration() == true) {
            // processing deregistration as device deletion
            this.errorLogger().info("AWSIoT(MQTT): processing de-registration as device deletion (OK).");
            
             // delete the device shadows...
            for (int i = 0; deletions != null && i < deletions.length; ++i) {
                if (deletions[i] != null && deletions[i].length() > 0) {
                    // Unsubscribe... 
                    this.unsubscribe(deletions[i]);
                    
                    // Disconnect MQTT *and* Delete the device shadow...
                    this.deleteDevice(deletions[i]);
                    
                    // remove type
                    this.removeEndpointTypeFromEndpointName(deletions[i]);
                }
            }
        }
        else {
            // not processing deregistration as a device deletion
            this.errorLogger().info("AWSIoT(MQTT): Not processing de-registration as device deletion (OK).");
            
            // just disconnect from MQTT
            for (int i = 0; deletions != null && i < deletions.length; ++i) {
                if (deletions[i] != null && deletions[i].length() > 0) {
                    // Unsubscribe...
                    this.unsubscribe(deletions[i]);

                    // Disconnect MQTT *only*
                    this.disconnectDeviceFromMQTT(deletions[i]);

                    // remove type
                    this.removeEndpointTypeFromEndpointName(deletions[i]);
                }
            }
        }
        
        // return a default 
        return super.processDeregistrations(parsed);
    }
    
    // OVERRIDE: process a registrations-expired 
    @Override
    public String[] processRegistrationsExpired(Map parsed) {
       // process a de-registration event
       return this.processDeregistrations(parsed);
    }
    
    // OVERRIDE: handle device deletions Google Cloud
    @Override
    public String[] processDeviceDeletions(Map parsed) {
        // complete processing in base class...
        String[] deletions = this.processDeviceDeletionsBase(parsed);
        
        // delete the device shadows...
        for (int i = 0; deletions != null && i < deletions.length; ++i) {
            if (deletions[i] != null && deletions[i].length() > 0) {
                // Unsubscribe... 
                this.unsubscribe(deletions[i]);

                // Disconnect from MQTT *and* delete the device shadow...
                this.deleteDevice(deletions[i]);

                // remove type
                this.removeEndpointTypeFromEndpointName(deletions[i]);
            }
        }
        
        // return our deletions
        return deletions;
    }
    
    // OVERRIDE: process a notification/observation in AWSIoT
    @Override
    public void processNotification(Map data) {
        if (this.m_configured == true) {
            // DEBUG
            //this.errorLogger().info("processIncomingDeviceServerMessage(AWSIoT)...");

            // get the list of parsed notifications
            List notifications = (List) data.get("notifications");
            for (int i = 0; notifications != null && i < notifications.size(); ++i) {
                // we have to process the payload... this may be dependent on being a string core type... 
                Map notification = (Map) notifications.get(i);

                // decode the Payload...
                String b64_coap_payload = (String) notification.get("payload");
                String decoded_coap_payload = Utils.decodeCoAPPayload(b64_coap_payload);

                // DEBUG
                //this.errorLogger().info("AWSIoT(MQTT): Decoded Payload: " + decoded_coap_payload);
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

                // get the path
                String path = Utils.valueFromValidKey(notification, "path", "uri");

                // we will send the raw CoAP JSON... AWSIoT can parse that... 
                String coap_raw_json = this.jsonGenerator().generateJson(notification);

                // strip off []...
                String coap_json_stripped = this.stripArrayChars(coap_raw_json);

                // get our endpoint name
                String ep_name = Utils.valueFromValidKey(notification, "id", "ep");

                // get our endpoint type
                String ep_type = this.getEndpointTypeFromEndpointName(ep_name);

                // encapsulate into a coap/device packet...
                String aws_iot_gw_coap_json = coap_json_stripped;

                // DEBUG
                this.errorLogger().info("AWSIoT(MQTT): CoAP notification (STR): " + aws_iot_gw_coap_json);

                // send to AWSIoT...
                if (this.mqtt(ep_name) != null) {
                    String topic = this.customizeTopic(this.m_aws_iot_observe_notification_topic, ep_name, ep_type) + path;
                    boolean status = this.mqtt(ep_name).sendMessage(topic, aws_iot_gw_coap_json, QoS.AT_MOST_ONCE);
                    if (status == true) {
                        // not connected
                        this.errorLogger().info("AWSIoT(MQTT): CoAP notification sent. SUCCESS");
                    }
                    else {
                        // send failed
                        this.errorLogger().warning("AWSIoT(MQTT): CoAP notification not sent. SEND FAILED");
                    }
                }
                else {
                    // not connected
                    this.errorLogger().info("AWSIoT(MQTT): CoAP notification not sent. NOT CONNECTED");
                }
            }
        }
        else {
            // unconfigured
            this.errorLogger().info("AWSIoT(MQTT): Not configured. Please configure AWSIoT credentials and restart the bridge (OK).");
        }
    }

    // AWS IoT: create the endpoint AWSIoT topic data
    @Override
    protected HashMap<String, Object> createEndpointTopicData(String ep_name, String ep_type) {
        HashMap<String, Object> topic_data = null;
        if (this.m_aws_iot_coap_cmd_topic_get != null) {
            Topic[] list = new Topic[NUM_COAP_VERBS];
            String[] topic_string_list = new String[NUM_COAP_VERBS];
            topic_string_list[0] = this.customizeTopic(this.m_aws_iot_coap_cmd_topic_get, ep_name, ep_type);
            topic_string_list[1] = this.customizeTopic(this.m_aws_iot_coap_cmd_topic_put, ep_name, ep_type);
            topic_string_list[2] = this.customizeTopic(this.m_aws_iot_coap_cmd_topic_post, ep_name, ep_type);
            topic_string_list[3] = this.customizeTopic(this.m_aws_iot_coap_cmd_topic_delete, ep_name, ep_type);
            for (int i = 0; i < NUM_COAP_VERBS; ++i) {
                list[i] = new Topic(topic_string_list[i], QoS.AT_LEAST_ONCE);
            }
            topic_data = new HashMap<>();
            topic_data.put("topic_list", list);
            topic_data.put("topic_string_list", topic_string_list);
            topic_data.put("ep_type", ep_type);
        }
        return topic_data;
    }

    // final customization of a MQTT Topic...
    private String customizeTopic(String topic, String ep_name, String ep_type) {
        String cust_topic = topic.replace("__EPNAME__", ep_name).replace("__TOPIC_ROOT__", this.getTopicRoot());
        if (ep_type == null) {
            ep_type = this.getEndpointTypeFromEndpointName(ep_name);
        }
        if (ep_type != null) {
            cust_topic = cust_topic.replace("__DEVICE_TYPE__", ep_type);
            this.errorLogger().info("AWSIoT Customized Topic: " + cust_topic);
        }
        else {
            // replace with "default"
            cust_topic = cust_topic.replace("__DEVICE_TYPE__", PelionProcessor.DEFAULT_ENDPOINT_TYPE);
            
            // WARN
            this.errorLogger().warning("AWSIoT Customized Topic (EPT UNK): " + cust_topic);
        }
        return cust_topic;
    }
    
    // send the API Response back through the topic
    private void sendApiResponse(String ep_name,String topic,ApiResponse response) {        
        // publish
        this.mqtt(ep_name).sendMessage(topic, response.createResponseJSON());
    }

    // CoAP command handler - processes CoAP commands coming over MQTT channel
    @Override
    public void onMessageReceive(String topic, String message) {
        if (this.m_configured) {
            // DEBUG
            this.errorLogger().info("AWSIoT(MQTT): Topic: " + topic + " message: " + message);

            // parse the topic to get the endpoint
            // format: mbed/__DEVICE_TYPE__/__EPNAME__/coap/__COMMAND_TYPE__/#
            String ep_name = this.getEndpointNameFromTopic(topic);

            // parse the topic to get the endpoint type
            String ep_type = this.getEndpointTypeFromEndpointName(ep_name);

            // process any API requests...
            if (this.isApiRequest(message)) {
                // process the message
                String reply_topic = this.customizeTopic(this.m_aws_iot_observe_notification_topic, ep_name, ep_type).replace(this.m_observation_key,this.m_api_response_key);
                this.sendApiResponse(ep_name,reply_topic,this.processApiRequestOperation(message));

                // return as we are done with the API request... no AsyncResponses necessary for raw API requests...
                return;
            }

            // pull the CoAP Path URI from the message itself... its JSON... 
            // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
            String uri = this.getCoAPURI(message);
            if (uri == null || uri.length() == 0) {
                // optionally pull the CoAP URI Path from the MQTT topic (SECONDARY)
                uri = this.getCoAPURIFromTopic(topic);
            }

            // pull the CoAP Payload from the message itself... its JSON... 
            // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
            String value = this.getCoAPValue(message);

            // Get the payload
            // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
            String payload = this.getCoAPPayload(message);

            // pull the CoAP verb from the message itself... its JSON... (PRIMARY)
            // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
            String coap_verb = this.getCoAPVerb(message);
            if (coap_verb == null || coap_verb.length() == 0) {
                // optionally pull the CoAP verb from the MQTT Topic (SECONDARY)
                coap_verb = this.getCoAPVerbFromTopic(topic);
            }

            // if the ep_name is wildcarded... get the endpoint name from the JSON payload
            // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
            if (ep_name == null || ep_name.length() <= 0 || ep_name.equalsIgnoreCase("+")) {
                ep_name = this.getCoAPEndpointName(message);
            }

            // if there are mDC/mDS REST options... lets add them
            // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get", "options":"noResp=true" }
            String options = this.getRESTOptions(message);

            // dispatch the coap resource operation request
            String response = this.orchestrator().processEndpointResourceOperation(coap_verb, ep_name, uri, value, options);

            // examine the response
            if (response != null && response.length() > 0) {
                // SYNC: We only process AsyncResponses from GET verbs... we dont sent HTTP status back through AWSIoT.
                this.errorLogger().info("AWSIoT(MQTT): Response: " + response);

                // AsyncResponse detection and recording...
                if (this.isAsyncResponse(response) == true) {
                    // CoAP GET and PUT provides AsyncResponses...
                    if (coap_verb.equalsIgnoreCase("get") == true || coap_verb.equalsIgnoreCase("put") == true) {
                        // its an AsyncResponse.. so record it...
                        this.recordAsyncResponse(response, coap_verb, this.mqtt(ep_name), this, topic, this.getReplyTopic(ep_name, this.getEndpointTypeFromEndpointName(ep_name), uri), message, ep_name, uri);
                    }
                    else {
                        // we ignore AsyncResponses to PUT,POST,DELETE
                        this.errorLogger().info("AWSIoT(MQTT): Ignoring AsyncResponse for " + coap_verb + " (OK).");
                    }
                }
                else if (coap_verb.equalsIgnoreCase("get")) {
                    // not an AsyncResponse... so just emit it immediately... only for GET...
                    this.errorLogger().info("AWSIoT(MQTT): Response: " + response + " from GET... creating observation...");

                    // we have to format as an observation...
                    String observation = this.createObservation(coap_verb, ep_name, uri, payload, value);

                    // DEBUG
                    this.errorLogger().info("AWSIoT(MQTT): Sending Observation(GET): " + observation);

                    // send the observation (GET reply)...
                    if (this.mqtt(ep_name) != null) {
                        String reply_topic = this.customizeTopic(this.m_aws_iot_observe_notification_topic, ep_name, ep_type);
                        reply_topic = reply_topic.replace(this.m_observation_key, this.m_cmd_response_key);
                        boolean status = this.mqtt(ep_name).sendMessage(reply_topic, observation, QoS.AT_MOST_ONCE);
                        if (status == true) {
                            // success
                            this.errorLogger().info("AWSIoT(MQTT): CoAP observation(get) sent. SUCCESS");
                        }
                        else {
                            // send failed
                            this.errorLogger().warning("AWSIoT(MQTT): CoAP observation(get) not sent. SEND FAILED");
                        }
                    }
                    else {
                        // not connected
                        this.errorLogger().warning("AWSIoT(MQTT): CoAP observation(get) not sent. NOT CONNECTED");
                    }
                }
            }
        }
        else {
            // unconfigured
            this.errorLogger().info("AWSIoT(MQTT): Not configured. Please configure AWSIoT credentials and restart the bridge (OK).");
        }
    }

    // process new device registration
    @Override
    protected synchronized Boolean registerNewDevice(Map message) {
        if (this.m_configured) {
            if (this.m_device_manager != null) {
                // get the device ID and device Type
                String device_type = Utils.valueFromValidKey(message, "endpoint_type", "ept");
                String device_id = Utils.valueFromValidKey(message, "id", "ep");

                // DEBUG
                this.errorLogger().info("AWSIoT(MQTT): Registering new device: " + device_id + " type: " + device_type);


                // create the device in AWSIoT
                Boolean success = this.m_device_manager.registerNewDevice(message);

                // if successful, validate (i.e. add...) an MQTT Connection
                if (success == true) {
                    this.validateMQTTConnection(this, device_id, device_type, null);
                }

                // return status
                return success;
            }
        }
        else {
            // unconfigured
            this.errorLogger().info("AWSIoT(MQTT): Not configured. Please configure AWSIoT credentials and restart the bridge (OK).");
        }
        return false;
    }
    
     // disconnect the device from MQTT
    private void disconnectDeviceFromMQTT(String device) {
        // DEBUG
        this.errorLogger().warning("AWSIoT(MQTT): Disconnecting MQTT for device: " + device + "...");

        // stop the listener thread for this device
        this.stopListenerThread(device);

        // disconnect MQTT for this device
        this.disconnect(device);
        
         // DEBUG
        this.errorLogger().warning("AWSIoT(MQTT): Disconnected MQTT for device: " + device + " SUCCESSFULLY.");
    }

    // process device de-registration
    @Override
    protected synchronized Boolean deleteDevice(String device_id) {
        boolean deleted = true;
        if (this.m_configured) {
            if (this.m_device_manager != null && device_id != null && device_id.length() > 0) {
                // remove the MQTT transport instance
                this.disconnectDeviceFromMQTT(device_id);

                // DEBUG
                this.errorLogger().info("AWSIoT(MQTT): deleting device shadow: " + device_id);

                // remove the device from AWSIoT
                deleted = this.m_device_manager.deleteDevice(device_id);
            }

            // DEBUG
            if (deleted == false && device_id != null && device_id.length() > 0) {
                // unable to delete the device shadow from IoTHub
                this.errorLogger().warning("AWSIoT(MQTT): WARNING: Unable to delete device " + device_id + " from IoTHub!");
            }
            else {
                // successfully deleted the device shadow from Google CloudIoT
                this.errorLogger().warning("AWSIoT(MQTT): Device " + device_id + " deleted from IoTHub SUCCESSFULLY.");
            }
        }
        else {
            // unconfigured
            this.errorLogger().info("AWSIoT(MQTT): Not configured. Please configure AWSIoT credentials and restart the bridge (OK).");
        }
        
        // return the deletion status
        return deleted;
    }
    
    // start our listener thread
    private void startListenerThread(String ep_name,MQTTTransport mqtt) {
        // ensure we only have 1 thread/endpoint
        this.m_mqtt_thread_list.remove(ep_name);

        // create and start the listener
        TransportReceiveThread listener = new TransportReceiveThread(mqtt);
        listener.setOnReceiveListener(this);
        this.m_mqtt_thread_list.put(ep_name, listener);
        listener.start();
    }

    // add a MQTT transport for a given endpoint - this is how MS AWSIoT MQTT integration works... 
    @Override
    public boolean createAndStartMQTTForEndpoint(String ep_name, String ep_type, Topic topics[]) {
        boolean connected = false;
        try {
            // we may already have a connection established for this endpoint... if so, we just ignore...
            if (this.mqtt(ep_name) == null) {
                // no connection exists already... so... go get our endpoint details
                HashMap<String, Serializable> ep = this.m_device_manager.getEndpointDetails(ep_name);
                if (ep != null) {
                    // create a new MQTT Transport instance for our endpoint
                    MQTTTransport mqtt = new MQTTTransport(this.errorLogger(), this.preferences(), this);
                    if (mqtt != null) {
                        // record the additional endpoint details
                        mqtt.setEndpointDetails(ep_name, ep_type);
                        
                        // AWSIoT has X.509 Certs/Keys that we must pre-plumb
                        mqtt.prePlumbTLSCertsAndKeys((String)ep.get("PrivateKey"),(String)ep.get("PublicKey"),(String)ep.get("certificatePem"),(String)ep.get("thingName"));

                        // set the AWSIoT endpoint address
                        this.m_mqtt_host = (String)ep.get("endpointAddress");

                        // ClientID is the endpoint name
                        String client_id = ep_name;

                        // add it to the list indexed by the endpoint name... not the clientID...
                        this.addMQTTTransport(ep_name, mqtt);

                        // DEBUG
                        this.errorLogger().warning("AWSIoT(MQTT): connecting MQTT for endpoint: " + ep_name + " type: " + ep_type + "...");

                        // connect and start listening... 
                        if (this.connect(ep_name, client_id) == true) {
                            // DEBUG
                            this.errorLogger().warning("AWSIoT(MQTT): connection SUCCESS");
                            this.errorLogger().info("Creating and registering listener Thread for endpoint: " + ep_name + " type: " + ep_type);

                            // start the listener thread
                            this.startListenerThread(ep_name, mqtt);
                            
                            // if we have topics in our param list, lets go ahead and subscribe
                            if (topics != null) {
                                // DEBUG
                                this.errorLogger().info("AWSIoT(MQTT): re-subscribing to topics...");
                                
                                // re-subscribe
                                this.mqtt(ep_name).subscribe(topics);
                            }
                            
                            // we are connected
                            connected = true;
                        }
                        else {
                            // unable to connect!
                            this.errorLogger().critical("AWSIoT(MQTT): Unable to connect MQTT for endpoint: " + ep_name + " type: " + ep_type);
                            this.remove(ep_name);

                            // ensure we only have 1 thread/endpoint
                            this.stopListenerThread(ep_name);
                        }
                    }
                    else {
                        // unable to allocate MQTT connection for our endpoint
                        this.errorLogger().critical("AWSIoT(MQTT): ERROR. Unable to allocate MQTT connection for: " + ep_name);
                    }
                }
                else {
                    // unable to find endpoint details
                    this.errorLogger().warning("AWSIoT(MQTT): unable to find endpoint details for: " + ep_name + "... ignoring...");
                }
            }
            else {
                // already connected... just ignore
                this.errorLogger().info("AWSIoT(MQTT): already have connection for " + ep_name + " (OK)");
                connected = true;
            }
        }
        catch (Exception ex) {
            // exception caught... capture and note the stack trace
            this.errorLogger().critical("AWSIoT(MQTT): createAndStartMQTTForEndpoint(): exception: " + ex.getMessage() + " endpoint: " + ep_name, ex);
        }
        
        // return the connected status
        return connected;
    }
    
    // AsyncResponse response processor
    @Override
    public synchronized boolean processAsyncResponse(Map endpoint) {
        // with the attributes added, we finally create the device in AWS IOT
        this.completeNewDeviceRegistration(endpoint);

        // return our processing status
        return true;
    }
    
    // get our defaulted reply topic
    @Override
    public String getReplyTopic(String ep_name, String ep_type, String def) {
        return this.customizeTopic(this.m_aws_iot_observe_notification_topic, ep_name, ep_type).replace(this.m_observation_key, this.m_cmd_response_key);
    }
    
    // get the endpoint name from the MQTT topic
    @Override
    public String getEndpointNameFromTopic(String topic) {
        // format: mbed/__COMMAND_TYPE__/__DEVICE_TYPE__/__EPNAME__/<uri path>
        return this.getTopicElement(topic, 3);                                   // POSITION SENSITIVE
    }

    // get the CoAP verb from the MQTT topic
    @Override
    public String getCoAPVerbFromTopic(String topic) {
        // format: mbed/__COMMAND_TYPE__/__DEVICE_TYPE__/__EPNAME__/<uri path>
        return this.getTopicElement(topic, 1);                                   // POSITION SENSITIVE
    }

    // get the CoAP URI from the MQTT topic
    private String getCoAPURIFromTopic(String topic) {
        // format: mbed/__COMMAND_TYPE__/__DEVICE_TYPE__/__EPNAME__/<uri path>
        return this.getURIPathFromTopic(topic, 4);                               // POSITION SENSITIVE
    }
    
    // restart our device connection 
    @Override
    public boolean startReconnection(String ep_name,String ep_type,Topic topics[]) {
        if (this.m_configured) {
            if (this.m_device_manager != null) {
                // stop the current listener thread
                this.stopListenerThread(ep_name);

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
        }
        else {
            // unconfigured
            this.errorLogger().info("AWSIoT(MQTT): Not configured. Please configure AWSIoT credentials and restart the bridge (OK).");
        }
        return false;
    }

    // complete processing of adding the new device
    @Override
    public synchronized void completeNewDeviceRegistration(Map device) {
        try {
            // create the device in AWSIoT
            this.errorLogger().info("AWSIoT(MQTT): calling registerNewDevice(): " + device);
            boolean ok = this.registerNewDevice(device);
            if (ok) {
                // success
                this.errorLogger().info("AWSIoT(MQTT): registerNewDevice() completed SUCCESSFULLY");
            }
            else {
                // failure
                this.errorLogger().info("AWSIoT(MQTT): registerNewDevice() FAILED");
            };
        }
        catch (Exception ex) {
            this.errorLogger().warning("AWSIoT(MQTT): caught exception in registerNewDevice(): " + device);
        }

        try {
            // subscribe for AWSIoT as well..
            String ep_type = Utils.valueFromValidKey(device, "endpoint_type", "ept");
            String ep_name = Utils.valueFromValidKey(device, "id", "ep");
            this.errorLogger().info("AWSIoT(MQTT): calling subscribe(): " + device);
            this.subscribe(ep_name,ep_type,this.createEndpointTopicData(ep_name, ep_type),this);
            this.errorLogger().info("AWSIoT(MQTT): subscribe() completed");
        }
        catch (Exception ex) {
            this.errorLogger().warning("AWSIoT(MQTT): caught exception in subscribe(): " + device);
        }
    }
    
    // Connection to AWSIoT MQTT vs. generic MQTT...
    private boolean connect(String ep_name, String client_id) {
        // if not connected attempt
        if (this.isConnected(ep_name) == false) {
            if (this.mqtt(ep_name).connect(this.m_mqtt_host, this.m_mqtt_port, client_id, this.m_use_clean_session)) {
                this.orchestrator().errorLogger().info("AWSIoT(MQTT): Setting CoAP command listener...");
                this.mqtt(ep_name).setOnReceiveListener(this);
                this.orchestrator().errorLogger().info("AWSIoT(MQTT): connection completed successfully");
            }
        }
        else {
            // already connected
            this.orchestrator().errorLogger().info("AWSIoT(MQTT): Already connected (OK)...");
        }

        // return our connection status
        this.orchestrator().errorLogger().info("AWSIoT(MQTT): Connection status: " + this.isConnected(ep_name));
        return this.isConnected(ep_name);
    }
    
    // we have to override the creation of the authentication hash.. it has to be dependent on a given endpoint name
    @Override
    public String createAuthenticationHash() {
        return Utils.createHash(this.prefValue("aws_iot_secret_access_key", this.m_suffix) + this.prefValue("aws_iot_access_key_id", this.m_suffix));
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
 }
