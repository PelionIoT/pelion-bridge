/*
 * @file AWSIoTMQTTProcessor.java
 * @brief AWS IoT HTTP Peer Processor
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

package com.arm.pelion.bridge.coordinator.processors.aws.http;

import com.arm.pelion.bridge.coordinator.processors.aws.AWSIoTDeviceManager;
import com.arm.pelion.bridge.coordinator.processors.arm.GenericConnectablePeerProcessor;
import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.arm.PelionProcessor;
import com.arm.pelion.bridge.coordinator.processors.core.HTTPDeviceListener;
import com.arm.pelion.bridge.core.ApiResponse;
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.DeviceManagerToPeerProcessorInterface;
import com.arm.pelion.bridge.coordinator.processors.interfaces.HTTPDeviceListenerInterface;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.transport.HttpTransport;
import com.arm.pelion.bridge.transport.MQTTTransport;
import com.arm.pelion.bridge.transport.Transport;
import java.util.List;
import java.util.Map;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;
import java.util.HashMap;

/**
 * AWS IoT peer processor based on HTTP
 *
 * @author Doug Anson
 */
public class AWSIoTProcessor extends GenericConnectablePeerProcessor implements HTTPDeviceListenerInterface, DeviceManagerToPeerProcessorInterface, Transport.ReceiveListener, PeerProcessorInterface, AsyncResponseProcessor {
    // AWSIoT Device Manager
    private AWSIoTDeviceManager m_device_manager = null;
    
    private String m_aws_iot_observe_notification_topic = null;
    private String m_aws_iot_coap_cmd_topic_get = null;
    private String m_aws_iot_coap_cmd_topic_put = null;
    private String m_aws_iot_coap_cmd_topic_post = null;
    private String m_aws_iot_coap_cmd_topic_delete = null;
    
    // HTTP listeners for our device shadows
    private HashMap<String,HTTPDeviceListener> m_device_listeners = null;
    
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
        this.errorLogger().warning("Amazon AWSIoT Processor ENABLED. (HTTP)");
        
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
        
        // Device Listeners
        this.m_device_listeners = new HashMap<>();
        
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
                this.errorLogger().warning("AWSIoT(HTTP): Exceeded maximum number of device shadows. Limit: " + this.getMaxNumberOfShadows());
            }
        }
        else {
            // nothing to shadow
            this.errorLogger().info("AWSIoT(HTTP): Nothing to shadow (OK).");
        }
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
                this.errorLogger().info("AWSIoT(HTTP): CoAP re-registration: no subscriptions.. processing as new registration...");
                this.processRegistration(data, "reg-updates");
            }
            else {
                // already subscribed (OK)
                this.errorLogger().info("AWSIoT(HTTP): CoAP re-registration: already subscribed (OK)");
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
            this.errorLogger().info("AWSIoT(HTTP): processing de-registration as device deletion (OK).");
            
             // delete the device shadows...
            for (int i = 0; deletions != null && i < deletions.length; ++i) {
                if (deletions[i] != null && deletions[i].length() > 0) {
                    // Delete the device shadow...
                    this.deleteDevice(deletions[i]);
                }
            }
        }
        else {
            // not processing deregistration as a device deletion
            this.errorLogger().info("AWSIoT(HTTP): Not processing de-registration as device deletion (OK).");
            
            // clean up
            for (int i = 0; deletions != null && i < deletions.length; ++i) {
                if (deletions[i] != null && deletions[i].length() > 0) {
                    // closedown only...
                    this.closeDeviceShadow(deletions[i]);
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
    
    // OVERRIDE: handle device deletions AWSIoT Cloud
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
            //this.errorLogger().info("AWSIoT(HTTP): Decoded Payload: " + decoded_coap_payload);
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
            String message = this.stripArrayChars(coap_raw_json);

            // get our endpoint name
            String ep_name = Utils.valueFromValidKey(notification, "id", "ep");

            // DEBUG
            this.errorLogger().info("AWSIoT(HTTP): CoAP notification (STR): " + message);

            // create a fudged topic
            String fudged_topic = this.createFudgedTopic(ep_name);
            
            // publish
            this.sendMessage(fudged_topic,message);
        }
    }
    
    // send the API Response back through the topic
    private void sendApiResponse(String topic, ApiResponse response) {
        // DEBUG
        this.errorLogger().info("AWSIoT(MQTT):(sendApiResponse): TOPIC: " + topic + " RESPONSE: " + response.createResponseJSON());
        
        // publish via sendMessage()
        this.sendMessage(topic, response.createResponseJSON());
    }

    // GenericSender Implementation: send a message
    @Override
    public boolean sendMessage(String topic, String message) {
        boolean ok = false;
        if (this.m_configured) {
            // DEBUG
            this.errorLogger().info("AWSIoT(MQTT):(HTTP): sendMessage: TOPIC: " + topic + " MESSAGE: " + message);
            try {
                // Get the endpoint name
                String ep_name = this.getEndpointNameFromNotificationTopic(topic);
                ok = this.publish(ep_name,message);
                
                // DEBUG
                if (ok) {
                    // SUCCESS
                    this.errorLogger().info("AWSIoT(MQTT):(HTTP): message: " + message + " sent to device: " + ep_name + " SUCCESSFULLY.");
                }
                else {
                    // FAILURE
                    this.errorLogger().warning("AWSIoT(MQTT):(HTTP): message: " + message + " send to device: " + ep_name + " FAILED.");
                }
            }
            catch (Exception ex) {
                this.errorLogger().warning("AWSIoT(MQTT):(HTTP): Exception in sendMessage: " + ex.getMessage(),ex);
            }
        }
        else {
            // not configured
            this.errorLogger().info("AWSIoT(MQTT):(HTTP): AWS IoT Auth Token is UNCONFIGURED. Please configure and restart the bridge (OK).");
        }
        return ok;
    }
    
    // CoAP command handler - processes CoAP commands coming over MQTT channel
    @Override
    public void onMessageReceive(String ep_name, String message) {
        // DEBUG
        this.errorLogger().info("AWSIoT(MQTT): CoAP Command message to process: EP: " + ep_name + " message: " + message);
        
        // parse the topic to get the endpoint type
        String ep_type = this.getEndpointTypeFromEndpointName(ep_name);
        
        // process any API requests...
        if (this.isApiRequest(message)) {
            // use a fudged topic
            String fudged_topic = this.createFudgedTopic(ep_name);
            
            // process the message
            this.sendApiResponse(fudged_topic,this.processApiRequestOperation(message));
            
            // return as we are done with the API request... no AsyncResponses necessary for raw API requests...
            return;
        }
        
        // DEBUG
        this.errorLogger().info("AWSIoT(MQTT): NOT an API request... continuing...");

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

        // if the ep_name is wildcarded... get the endpoint name from the JSON payload
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        if (ep_name == null || ep_name.length() <= 0 || ep_name.equalsIgnoreCase("+")) {
            ep_name = this.getCoAPEndpointName(message);
        }

        // if there are mDC/mDS REST options... lets add them
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get", "options":"noResp=true" }
        String options = this.getRESTOptions(message);
        
        // DEBUG
        this.errorLogger().info("AWSIoT(onMessageReceive): processing op: VERB: " + coap_verb + " EP: " + ep_name + " URI: " + uri + " VALUE: " + value + " OPTIONS: " + options, new Exception());
        
        // dispatch the CoAP resource operation request to mbed Cloud
        String response = this.orchestrator().processEndpointResourceOperation(coap_verb, ep_name, uri, value, options);

        // examine the response
        if (response != null && response.length() > 0) {
            // SYNC: We only process AsyncResponses from GET verbs... we dont sent HTTP status back through AWSIoT.
            this.errorLogger().info("AWSIoT(CoAP Command): Response: " + response);

            // AsyncResponse detection and recording...
            if (this.isAsyncResponse(response) == true) {
                // CoAP GET and PUT provides AsyncResponses...
                if (coap_verb.equalsIgnoreCase("get") == true || coap_verb.equalsIgnoreCase("put") == true) {
                    // DEBUG
                    this.errorLogger().info("AWSIoT(MQTT): Recording ASYNC RESPONSE: " + response);
                    
                    // its an AsyncResponse.. so record it...
                    String fudged_topic = this.createFudgedTopic(ep_name);
                    this.recordAsyncResponse(response, coap_verb, fudged_topic, message, ep_name, uri);
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
                this.errorLogger().info("AWSIoT(MQTT): Sending Observation (GET): " + observation);

                // publish to AWS IoT
                boolean ok = this.publish(ep_name, observation);
                
                // DEBUG
                if (ok) {
                    // SUCCESS
                    this.errorLogger().info("AWSIoT(MQTT): observation sent SUCCESSFULLY.");
                }
                else {
                    // FAILURE
                    this.errorLogger().warning("AWSIoT(MQTT): observation send FAILED.");
                }
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
    
    // close down the device shadow
    private void closeDeviceShadow(String device) {
        // DEBUG
        this.errorLogger().warning("AWSIoT(HTTP): Closing down device: " + device + "...");

        // stop and remove the device listener
        this.removeDeviceListener(device);

        // stop the listener thread for this device
        this.stopListenerThread(device);
                
         // DEBUG
        this.errorLogger().warning("AWSIoT(MQTT): device: " + device + " closed down SUCCESSFULLY.");
    }

    // process device de-registration
    @Override
    protected synchronized Boolean deleteDevice(String device) {
        if (this.m_device_manager != null && device != null && device.length() > 0) {
            // close down shadow
            this.closeDeviceShadow(device);
            
             // DEBUG
            this.errorLogger().info("AWSIoT(HTTP): deleting device: " + device + " from AWSIoT...");
            
            // remove the device from AWSIOT
            if (this.m_device_manager.deleteDevice(device) == false) {
                // unable to delete the device shadow from AWSIOT
                this.errorLogger().warning("AWSIoT(HTTP): WARNING: Unable to delete device " + device + " from AWSIoT!");
            }    
            else {
                // successfully deleted the device shadow from AWSIOT
                this.errorLogger().warning("AWSIoT(HTTP): Device " + device + " deleted from AWS IoT SUCCESSFULLY.");
            }
        }
        
        // aggressive deletion
        return true;
    }
    
    // AsyncResponse response processor
    @Override
    public synchronized boolean processAsyncResponse(Map endpoint) {
        // with the attributes added, we finally create the device in AWS IOT
        this.completeNewDeviceRegistration(endpoint);

        // return our processing status
        return true;
    }
   
    // complete processing of adding the new device
    @Override
    public synchronized void completeNewDeviceRegistration(Map device) {
        try {
            // create the device in AWSIoT
            this.errorLogger().info("AWSIoT(HTTP): calling registerNewDevice(): " + device);
            boolean ok = this.registerNewDevice(device);
            if (ok) {
                // success
                this.errorLogger().info("AWSIoT(HTTP): registerNewDevice() completed SUCCESSFULLY");
            }
            else {
                // failure
                this.errorLogger().info("AWSIoT(HTTP): registerNewDevice() FAILED");
            }
        }
        catch (Exception ex) {
            this.errorLogger().warning("AWSIoT(HTTP): caught exception in registerNewDevice(): " + device);
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
                this.errorLogger().info("AWSIoT(HTTP): Registering new device: " + device_id + " type: " + device_type);
                
                // create the device in AWSIoT
                Boolean success = this.m_device_manager.registerNewDevice(message);

                // if successful, validate (i.e. add...) an MQTT Connection
                if (success == true) {
                    // add our device type
                    this.setEndpointTypeFromEndpointName(device_id, device_type);

                    // Create and start our device listener thread for this device
                    this.createDeviceListener(device_id);
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
    
    // get the next message (these are device command messages from AWS IoT --> Pelion)
    private String getNextMessage(HttpTransport http, String ep_name) { 
        // pull the next request
        String message = this.query(ep_name);
        
        // DEBUG
        if (message != null && message.length() > 0) {
            this.errorLogger().info("AWSIoT: NEW CMD: " + message);
        }
        
        // return our message
        return message;
    }
    
    @Override
    public void pollAndProcessDeviceMessages(HttpTransport http, String ep_name) {
        // Get the next message
        String message = this.getNextMessage(http, ep_name);
        if (message != null && message.length() > 0) {
            // DEBUG
            this.errorLogger().info("AWSIoT(pollAndProcessDeviceMessages): EP: " + ep_name + " Command: " + message);
            
            // parse and process the configuration change request message
            this.onMessageReceive(ep_name, message);
        }
        else {
            // No message to process... OK
            this.errorLogger().info("AWSIoT(HTTP): EP: " + ep_name + " No command request to process. (OK)");
        }
    }
    
    // create our HTTP-based device listener
    private void createDeviceListener(String ep_name) {
        if (ep_name != null && ep_name.length() > 0) {
            if (this.m_device_listeners.get(ep_name) == null) {
                // create a listner (since AWSIoT uses CLI, we dont have to supply a HttpTranport...)
                this.m_device_listeners.put(ep_name, new HTTPDeviceListener(this,null,ep_name));
            }
        }
    }
    
    // finalize a notification topic
    private String createNotificationTopicForEndpoint(String ep_name) {
        return this.customizeTopic(this.m_aws_iot_observe_notification_topic, ep_name, this.getEndpointTypeFromEndpointName(ep_name));
    }
    
    // final customization of a publiciation Topic...
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
    
    // publish via AWS CLI
    private boolean publish(String ep_name,String payload) {
        String topic = this.createNotificationTopicForEndpoint(ep_name);
        String args = "iot-data publish --topic " + topic + " --payload " + payload;
        String result = Utils.awsCLI(this.errorLogger(), args);
        
        // DEBUG
        this.errorLogger().info("AWSIoT(publish): ARGS: " + args + " RESULT: " + result);
        
        // look at the result - we should have an error code to check...
        if (result != null && result.length() > 0 && result.contains("status") == true) {
            Map parsed = this.tryJSONParse(result);
            if (parsed != null) {
                int status = (Integer)parsed.get("status");
                if (status == 0) {
                    // success!
                    return true;
                }
            }
        }
        
        // return failure
        return false;
    }
    
    // query via AWS CLI
    private String query(String ep_name) {
        String args = "iot-data get-thing-shadow --thing-name " + ep_name;
        String result = Utils.awsCLI(this.errorLogger(), args);
        
        // DEBUG
        this.errorLogger().warning("AWSIoT(query): ARGS: " + args + " RESULT: " + result);
        
        // look at the result - we should have an error code to check...
        if (result != null && result.length() > 0) {
            return result;
        }
        
        // return nothing
        return null;
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
