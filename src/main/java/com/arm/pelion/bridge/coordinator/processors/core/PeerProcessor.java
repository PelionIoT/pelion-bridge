/**
 * @file PeerProcessor.java
 * @brief peer processor base class
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
package com.arm.pelion.bridge.coordinator.processors.core;

import com.arm.pelion.bridge.core.Processor;
import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.GenericSender;
import com.arm.pelion.bridge.coordinator.processors.interfaces.TopicParseInterface;
import com.arm.pelion.bridge.core.TypeDecoder;
import com.arm.pelion.bridge.core.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.binary.Base64;

/**
 * Peer Processor base class
 *
 * @author Doug Anson
 */
public class PeerProcessor extends Processor implements GenericSender, TopicParseInterface {
    // enable/disable DRAFT MQTT standard compliance
    public static final boolean ENABLE_DRAFT_MQTT_INTEGRATION_FORMAT = true;   // enable: true, disable(default): false

    private AsyncResponseManager m_async_response_manager = null;
    private String m_mds_topic_root = null;
    private TypeDecoder m_type_decoder = null;
    private String m_mds_request_tag = null;
    
    // auto subscribe to observable resources true by default
    protected boolean m_auto_subscribe_to_obs_resources = true;
    
    // unified format now true by default
    //private boolean m_unified_format_enabled = true;
    
    // enable this if you want to have re-subscription even if the subscription already exists (i.e. wipe/reset)
    protected boolean m_re_subscribe = true;
    
    // Visited List
    private ArrayList<String> m_device_seen_list = null;
    
    // keys used to differentiate between data from CoAP observations and responses from CoAP commands 
    protected String m_observation_key = "notify";                  
    protected String m_cmd_response_key = "cmd-response";           
    protected String m_api_response_key = "api-response";           
    
    // LWM2M over MQTT draft integratino format enablement
    private boolean m_enable_draft_mqtt_formats = ENABLE_DRAFT_MQTT_INTEGRATION_FORMAT;
    
    // default constructor
    public PeerProcessor(Orchestrator orchestrator, String suffix) {
        super(orchestrator, suffix);
        
        // initialize the seen list
        this.m_device_seen_list = new ArrayList<>();
                
        // allocate our AsyncResponse orchestrator
        this.m_async_response_manager = new AsyncResponseManager(orchestrator);
                
        // initial topic root
        this.m_mds_topic_root = "";
                
        // initialize the auto subscription to OBS resources
        this.initAutoSubscribe(null);
        
        // initialize the mDS request tag
        this.initRequestTag(null);
        
        // allocate our TypeDecoder
        this.m_type_decoder = new TypeDecoder(orchestrator.errorLogger(), orchestrator.preferences());
        
        // MQTT Draft format enable/disable - this needs to be down in PeerProcessor as its shared between PelionProcessor and the peers...
        this.m_enable_draft_mqtt_formats = orchestrator.preferences().booleanValueOf("mqtt_draft_format_enabled",this.m_suffix);
        if (this.m_enable_draft_mqtt_formats == false) {
            this.m_enable_draft_mqtt_formats = ENABLE_DRAFT_MQTT_INTEGRATION_FORMAT;
        }
        
        // debug 
        if (this.m_enable_draft_mqtt_formats == true) {
            this.errorLogger().warning("PeerProcessor: Draft MQTT formatting ENABLED");
        }
        else {
            this.errorLogger().warning("PeerProcessor: Draft MQTT formatting DISABLED");
        }
    }
    
    // are draft MQTT formats enabled?
    public boolean draftMQTTFormatsEnabled() {
        return this.m_enable_draft_mqtt_formats;
    }
    
    // process a received new registration
    protected void processRegistration(Map data, String key) {
        List endpoints = (List) data.get(key);
        for (int i = 0; endpoints != null && i < endpoints.size(); ++i) {
            Map endpoint = (Map) endpoints.get(i);
            
            // get the device ID and device Type
            String device_type = Utils.valueFromValidKey(endpoint, "endpoint_type", "ept");
            String device_id = Utils.valueFromValidKey(endpoint, "id", "ep");

            // set the endpoint type for this endpoint name (only if never set prior)
            if (this.getEndpointTypeFromEndpointName(device_id) == null) {
                this.setEndpointTypeFromEndpointName(device_id, device_type);
            }
            
            // pull our metadata and complete the new registration
            this.orchestrator().pullDeviceMetadata(endpoint,null);
         }
    }
    
    // process a reregistration
    public void processReRegistration(Map data) {
        List notifications = (List) data.get("reg-updates");
        for (int i = 0; notifications != null && i < notifications.size(); ++i) {
            Map endpoint = (Map) notifications.get(i);
            
            // get the device ID and device Type
            String device_type = Utils.valueFromValidKey(endpoint, "endpoint_type", "ept");
            String device_id = Utils.valueFromValidKey(endpoint, "id", "ep");
            
            // DEBUG
            //this.errorLogger().warning("ENDPOINT RECORD: " + endpoint);
            
            // set the endpoint type for this endpoint name (only if never set prior)
            if (this.getEndpointTypeFromEndpointName(device_id) == null) {
                this.setEndpointTypeFromEndpointName(device_id, device_type);
            }
        }
    }
    
    // process a device deletion
    public String[] processDeviceDeletions(Map parsed) {
        String[] device_deletions = this.processDeviceDeletionsBase(parsed);
        for (int i = 0; i < device_deletions.length; ++i) {
            this.orchestrator().getEndpointTypeManager().removeEndpointTypeFromEndpointName(device_deletions[i]);
        }
        return device_deletions;
    }
    
    // protected device deletion
    protected String[] processDeviceDeletionsBase(Map parsed) {
        String[] device_deletions = this.parseDeviceDeletionsBody(parsed);
        this.orchestrator().processDeviceDeletions(device_deletions);
        return device_deletions;
    }
    
    // process a deregistration
    public String[] processDeregistrations(Map parsed) {
        String[] deregistrations = this.processDeregistrationsBase(parsed);
        for (int i = 0; i < deregistrations.length; ++i) {
            this.orchestrator().getEndpointTypeManager().removeEndpointTypeFromEndpointName(deregistrations[i]);
        }
        return deregistrations;
    }
    
    // protected device deregistration
    protected String[] processDeregistrationsBase(Map parsed) {
        String[] deregistrations = this.parseDeRegistrationBody(parsed);
        this.orchestrator().processDeregistrations(deregistrations);
        return deregistrations;
    }
    
    // process a registrations-expired
    public String[] processRegistrationsExpired(Map parsed) {
        String[] regs_expired = this.processRegistrationsExpiredBase(parsed);
        for (int i = 0; i < regs_expired.length; ++i) {
            this.orchestrator().getEndpointTypeManager().removeEndpointTypeFromEndpointName(regs_expired[i]);
        }
        return regs_expired;
    }
    
    // protected registrations-expired
    protected String[] processRegistrationsExpiredBase(Map parsed) {
        String[] regs_expired = this.parseRegistrationsExpiredBody(parsed);
        this.orchestrator().processRegistrationsExpired(regs_expired);
        return regs_expired;
    }
    
    // process an observation
    public void processNotification(Map data) {
        // DEBUG
        //this.errorLogger().info("processIncomingDeviceServerMessage(Peer)...");

        // get the list of parsed notifications
        List notifications = (List) data.get("notifications");
        for (int i = 0; notifications != null && i < notifications.size(); ++i) {
            try {
                Map notification = (Map) notifications.get(i);

                // decode the Payload...
                String b64_coap_payload = (String) notification.get("payload");
                String decoded_coap_payload = Utils.decodeCoAPPayload(b64_coap_payload);

                // DEBUG
                //this.errorLogger().info("processIncomingDeviceServerMessage(Peer): Decoded Payload: " + decoded_coap_payload);
                // Try a JSON parse... if it succeeds, assume the payload is a composite JSON value...
                Map json_parsed = this.tryJSONParse(decoded_coap_payload);
                if (json_parsed != null && json_parsed.isEmpty() == false) {
                    // add in a JSON object payload value directly...
                    notification.put("value", Utils.retypeMap(json_parsed, this.fundamentalTypeDecoder()));             // its JSON (flat...)                                                   // its JSON 
                }
                else {
                    // add in a decoded payload value as a fundamental type...
                    notification.put("value", this.fundamentalTypeDecoder().getFundamentalValueFromString(decoded_coap_payload)); // its a Float, Integer, or String
                }

                // we will send the raw CoAP JSON... WatsonIoT can parse that... 
                String coap_raw_json = this.jsonGenerator().generateJson(notification);

                // strip off []...
                String coap_json_stripped = this.stripArrayChars(coap_raw_json);

                // get the device ID and device Type
                String ep_type = Utils.valueFromValidKey(notification, "endpoint_type", "ept");
                String ep_name = Utils.valueFromValidKey(notification, "id", "ep");
                if (ep_type == null) {
                    ep_type = this.getEndpointTypeFromEndpointName(ep_name);
                }
     
                // get the resource URI
                String uri = (String) notification.get("path");

                // send it as JSON over the observation sub topic
                String topic = this.createObservationTopic(ep_type, ep_name, uri);
                
                // encapsulate into a coap/device packet...
                String coap_json = coap_json_stripped;

                // DEBUG
                this.errorLogger().info("PeerProcessor: Active subscription for ep_name: " + ep_name + " ep_type: " + ep_type + " uri: " + uri);
                this.errorLogger().info("PeerProcessor: Publishing notification: payload: " + coap_json + " topic: " + topic);

                // WARN if we have a null EPT... may have notification data processing loss...
                if (ep_type == null || ep_type.length() == 0) {
                    // optional warning
                    this.errorLogger().info("PeerProcessor(processNotification): WARNING: EPT is NULL. TOPIC: " + topic + " MESSAGE: " + coap_json);
                }

                // publish to Peer...
                this.sendMessage(topic, coap_json);
            }
            catch (Exception ex) {
                // caught exception in processing notifications...
                this.errorLogger().warning("PeerProcessor: Exception while processing notifications: " + ex.getMessage(),ex);
            }
        }
    }
    
    // message in draft MQTT format come here and are processed...
    private void onMessageReceiveDraftFormat(String topic, String message) {
        // DEBUG
        this.errorLogger().info("PeerProcessor: onMessageReceiveDraftFormat: DRAFT FORMAT: topic: " + topic + " message: " + message);
        
        // parse the json 
        Map parsed = this.tryJSONParse(message);
        if (parsed != null) {
            // split the topic so we can get the endpoint name. POSITION SENSITIVE: <tenant_id>/lwmwm/rd/<endpoint_name>/uplink
            String[] items = topic.split("/");
            
            // endpoint name
            String ep_name = items[items.length - 2];
            
            // uri (ensure we have a forward slash...)
            String uri = (String)parsed.get("paths");
            if (uri != null && uri.charAt(0) != '/') {
                uri = "/" + uri;
            }
            
            // get the endpoint type from the endpoint name
            String ep_type = this.getEndpointTypeFromEndpointName(ep_name);
            
            // CoAP verb
            String verb = this.operationToCoapVerb((Integer)parsed.get("operation"));
            
            // Options (not used)
            String options = "";
            
            // get the value (TO DO: what format is this payload supposed to be in?)
            String value = "";
            
            // decode the Payload...
            String b64_coap_payload = (String) parsed.get("payload");
            String decoded_coap_payload = Utils.decodeCoAPPayload(b64_coap_payload);

            // DEBUG
            //this.errorLogger().info("processIncomingDeviceServerMessage(Peer): Decoded Payload: " + decoded_coap_payload);
            // Try a JSON parse... if it succeeds, assume the payload is a composite JSON value...
            Map json_parsed = this.tryJSONParse(decoded_coap_payload);
            if (json_parsed != null && json_parsed.isEmpty() == false) {
                // add in a JSON object payload value directly... 
                value += Utils.retypeMap(json_parsed, this.fundamentalTypeDecoder());
            }
            else {
                // add in a decoded payload value as a fundamental type...
                value += this.fundamentalTypeDecoder().getFundamentalValue(decoded_coap_payload);
            }
            
            // perform the operation
            String json = this.orchestrator().processEndpointResourceOperation(verb, ep_name, uri, value, options);
            
            // send a response back if we have one...
            if (json != null) {
                // Strip the request tag
                String response_topic = this.createResourceResponseTopic(ep_type, ep_name, uri);

                // SYNC: here we have to handle AsyncResponses. if mDS returns an AsyncResponse... handle it
                if (this.isAsyncResponse(json) == true) {
                    if (verb.equalsIgnoreCase("get") == true || verb.equalsIgnoreCase("put") == true) {
                        // DEBUG
                        this.errorLogger().info("PeerProcessor: onMessageReceiveDraftFormat: saving async response (" + verb + ") on topic: " + response_topic + " value: " + json);
                        
                        // its an AsyncResponse to a GET or PUT.. so record it... 
                        this.recordAsyncResponse(json, verb, response_topic, message, ep_name, uri);
                    }
                    else {
                        // we dont process AsyncResponses to POST and DELETE
                        this.errorLogger().info("PeerProcessor: onMessageReceiveDraftFormat: AsyncResponse (" + verb + ") ignored (OK).");
                    }
                }
                else {
                    // DEBUG
                    this.errorLogger().info("PeerProcessor: onMessageReceiveDraftFormat: sending immediate reply (" + verb + ") on topic: " + response_topic + " value: " + json);

                    // not an AsyncResponse... so just emit it immediately... (GET only)
                    this.sendMessage(response_topic, json);
                }
            }
            else {
                // Error - no response (due to error condition)
                this.errorLogger().warning("PeerProcessor: onMessageReceiveDraftFormat: no response to request VERB(" + verb + ")... null response (ERROR)");
            }
        }
    }
    
    // messages from MQTT come here and are processed...
    public void onMessageReceive(String topic, String message) {
        // DEBUG
        this.errorLogger().info("PeerProcessor: onMessageReceive: Topic: " + topic + " message: " + message);
        
        // Get/Put/Post Endpoint Resource Value...
        if (this.isEndpointResourceRequest(topic)) {
            String json = null;

            // parse the topic to get the endpoint and CoAP verb
            // format: iot-2/type/mbed/id/mbed-eth-observe/cmd/put/fmt/json
            String ep_name = this.getCoAPEndpointName(message);

            // pull the CoAP URI and Payload from the message itself... its JSON... 
            // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
            String uri = this.getCoAPURI(message);

            // pull the CoAP verb from the message itself... its JSON... (PRIMARY)
            // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
            String verb = this.getCoAPVerb(message);

            // get the CoAP value to send
            String value = this.getCoAPValue(message);

            // if there are mDC/mDS REST options... lets add them
            // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get", "options":"noResp=true" }
            String options = this.getRESTOptions(message);

            // get the endpoint type from the endpoint name
            String ep_type = this.getEndpointTypeFromEndpointName(ep_name);

            // perform the operation
            json = this.orchestrator().processEndpointResourceOperation(verb, ep_name, uri, value, options);
            
            // send a response back if we have one...
            if (json != null) {
                // Strip the request tag
                String response_topic = this.createResourceResponseTopic(ep_type, ep_name, uri);

                // SYNC: here we have to handle AsyncResponses. if mDS returns an AsyncResponse... handle it
                if (this.isAsyncResponse(json) == true) {
                    if (verb.equalsIgnoreCase("get") == true || verb.equalsIgnoreCase("put") == true) {
                        // DEBUG
                        this.errorLogger().info("PeerProcessor: onMessageReceive: saving async response (" + verb + ") on topic: " + response_topic + " value: " + json);

                        // its an AsyncResponse to a GET or PUT.. so record it... 
                        this.recordAsyncResponse(json, verb, response_topic, message, ep_name, uri);
                    }
                    else {
                        // we dont process AsyncResponses to POST and DELETE
                        this.errorLogger().info("PeerProcessor: onMessageReceive: AsyncResponse (" + verb + ") ignored (OK).");
                    }
                }
                else {
                    // DEBUG
                    this.errorLogger().info("PeerProcessor: onMessageReceive: sending immediate reply (" + verb + ") on topic: " + response_topic + " value: " + json);

                    // not an AsyncResponse... so just emit it immediately... (GET only)
                    this.sendMessage(response_topic, json);
                }
            }
            else if (verb != null) {
                // Error - no response (due to error condition)
                this.errorLogger().warning("PeerProcessor: onMessageReceive: no response to request VERB(" + verb + ")... null response (ERROR)");
            }
            else {
                // draft formatting is enabled... it may be a draft format request... 
                if (this.draftMQTTFormatsEnabled()) {
                    // try processing this message as a draft message
                    this.onMessageReceiveDraftFormat(topic,message);
                }
                else {
                    // Error - format error
                    this.errorLogger().warning("PeerProcessor: onMessageReceive: request format error");    
                }
            }
        }
        else {
            // not a recognized notification
            this.errorLogger().warning("PeerProcessor: onMessageReceive: not a recognized notification/request: MESSAGE: " + message + " TOPIC: " + topic + "... ignoring (OK)");
        }
    }
    
    // record an async response to process later (Peer Peer)
    protected void recordAsyncResponse(String response, String coap_verb, String response_topic, String message, String ep_name, String uri) {
        this.asyncResponseManager().recordAsyncResponse(response, coap_verb, this, this, response_topic, null, message, ep_name, uri);
    }
    
    // record an async response to process later (override for MQTT-based peers)
    protected void recordAsyncResponse(String response, String coap_verb, GenericSender sender, PeerProcessor proc, String response_topic, String reply_topic, String message, String ep_name, String uri) {
        this.asyncResponseManager().recordAsyncResponse(response, coap_verb, sender, proc, response_topic, reply_topic, message, ep_name, uri);
    }

    
    // get the endpoint type from the endpoint name
    public String getEndpointTypeFromEndpointName(String ep_name) {
       return this.orchestrator().getEndpointTypeManager().getEndpointTypeFromEndpointName(ep_name);
    }
    
    // get the current endpoint count
    public int getCurrentEndpointCount() {
        return this.getEndpointTypeManager().size();
    }

    // set the endpoint type from the endpoint name
    public void setEndpointTypeFromEndpointName(String ep_name, String ep_type) {
        this.orchestrator().getEndpointTypeManager().setEndpointTypeFromEndpointName(ep_name,ep_type);
    }
    
    // remove the endpoint type from the endpoint name
    public void removeEndpointTypeFromEndpointName(String ep_name) {
        this.orchestrator().getEndpointTypeManager().removeEndpointTypeFromEndpointName(ep_name);
    }
    
    // initialize the mDS request tag
    protected void initRequestTag(String res_name) {
        this.m_mds_request_tag = "request";
        
        // mDS Request TAG
        if (res_name != null && res_name.length() > 0) {
            this.m_mds_request_tag = this.orchestrator().preferences().valueOf(res_name, this.m_suffix);
            if (this.m_mds_request_tag != null) {
                this.m_mds_request_tag = "/" + this.m_mds_request_tag;
            }
        }
    }
    
    // get the request tag
    protected String getRequestTag() {
        return this.m_mds_request_tag;
    }
    
    // initialize auto OBS subscriptions
    protected void initAutoSubscribe(String res_name) {
        // default
        this.m_auto_subscribe_to_obs_resources = false;
        
        if (res_name != null && res_name.length() > 0) {
            boolean res_value = this.orchestrator().preferences().booleanValueOf(res_name,this.m_suffix);
            if (res_value != this.m_auto_subscribe_to_obs_resources) {
                this.m_auto_subscribe_to_obs_resources = res_value;
            }
        }
    }
    
    // initialize the topic root...
    protected void initTopicRoot(String pref) {
        String topic_root = this.preferences().valueOf(pref,this.m_suffix);
        if (topic_root != null && topic_root.length() > 0) {
            this.m_mds_topic_root = topic_root;
        }
    }

    // get our topic root
    protected String getTopicRoot() {
        if (this.m_mds_topic_root == null) {
            return "";
        }
        return this.m_mds_topic_root;
    }
    
    // get the get the endpoint type  manager
    public EndpointTypeManager getEndpointTypeManager() {
        return this.orchestrator().getEndpointTypeManager();
    }

    // get the AsyncResponseManager
    protected AsyncResponseManager asyncResponseManager() {
        return this.m_async_response_manager;
    }
    
     // get TypeDecoder if needed
    protected TypeDecoder fundamentalTypeDecoder() {
        return this.m_type_decoder;
    }

    // unified format enabled (always true)
    protected boolean unifiedFormatEnabled() {
        return true;
    }
    
    // not an observation or a new_registration...
    private boolean isNotObservationOrNewRegistration(String topic) {
        if (topic != null) {
            return (topic.contains(this.m_observation_key) == false && topic.contains("new_registration") == false);
        }
        return false;
    }
    
    // test to check if a topic is requesting endpoint resource itself
    protected boolean isEndpointResourceRequest(String topic) {
        boolean is_endpoint_resource_request = false;
        
        // get the resource URI
        String resource_uri = this.getResourceURIFromTopic(topic);

        // see what we have
        if (resource_uri != null && resource_uri.length() > 0) {
            if (this.isNotObservationOrNewRegistration(topic) == true) {
                is_endpoint_resource_request = true;
            }
        }

        // DEBUG
        this.errorLogger().info("PeerProcessor: topic: " + topic + " is: " + is_endpoint_resource_request);
        return is_endpoint_resource_request;
    }
    
    // determine if the received MQTT message is REST api request
    protected boolean isApiRequest(String message) {
        // simply check for "request/subscriptions"
        if(message != null) {
            Map parsed = this.tryJSONParse(message);
            if (parsed != null) {
                String uri = (String)parsed.get("api_uri");             // req
                String verb = (String)parsed.get("api_verb");           // req
                String api_key = (String)parsed.get("api_key");         // req
                if (uri != null && uri.length() > 0 && verb != null && verb.length() > 0 && api_key != null && api_key.length() > 0) {
                    return true;
                }
            }
        }
        return false;
    }
    
    // sanitize the HTTP Verb
    protected String sanitizeHTTPVerb(String verb) {
        // non-zero length and null
        if (verb != null && verb.length() > 1) {
            if (verb.equalsIgnoreCase("get") || verb.equalsIgnoreCase("put") || verb.equalsIgnoreCase("post") || verb.equalsIgnoreCase("delete")) {
                return verb;
            }
        }
        return null;
    }
    
    // sanitize the REST Options
    protected String sanitizeRESTOptions(String options) {
        // non-zero length and null
        if (options != null && options.length() > 2) {
            if (options.charAt(0) != '?') {
                return "?" + options;
            }
            return options;
        }
        return "";
    }
    
    // sanitize the REST content type
    protected String sanitizeContentType(String content_type) {
         // non-zero length and null
        if (content_type != null && content_type.length() > 0) {
            return content_type;
        }
        return null;
    }
    
    // sanitize the REST caller ID
    protected String sanitizeCallerID(String caller_id) {
         // non-zero length and null
        if (caller_id != null && caller_id.length() > 0) {
            return caller_id;
        }
        return null;
    }
    
    // sanitize the REST API Key
    protected String sanitizeAPIKey(String api_key) {
        // non-zero length and null
        if (api_key != null && api_key.length() > 0 && api_key.contains("ak_")) {
            return api_key;
        }
        return null;
    }
    
    // sanitize the basic validation of the structure of the URI
    protected String sanitizeURIStructure(String uri) {
        // non-zero length and nulls...
        if (uri != null && uri.length() > 1) {
            // make sure it contains slashes
            if (uri.contains("/")) {
                // if we dont have a leading slash... put one there. 
                if (uri.charAt(0) != '/') {
                    uri = "/" + uri;
                }
                
                // return the uri
                return uri;
            }
        }
        return null;
    }
    
    // response is an AsyncResponse?
    protected boolean isAsyncResponse(String response) {
        return (response.contains("\"async-response-id\":") == true);
    }
    
    // returns  /mbed/<qualifier>
    protected String createBaseTopic(String qualifier) {
        return this.getTopicRoot() + "/" + qualifier;
    }
    
    // get the endpoint name from the topic (request topic sent) 
    // format: <topic_root>/request/endpoints/<ep_type>/<endpoint name>/<URI> POSITION SENSITIVE
    @Override
    public String getEndpointNameFromTopic(String topic) {
        String modified_topic = this.removeRequestTagFromTopic(topic); // strips <topic_root>/request/endpoints/ 
        String[] items = modified_topic.split("/");
        if (items.length >= 2 && items[1].trim().length() > 0) { // POSITION SENSITIVE
            return items[1].trim();                              // POSITION SENSITIVE
        }
        return null;
    }
    
    // get the endpoint type from the topic (request topic sent) 
    // format: <topic_root>/request/endpoints/<ep_type>/<endpoint name>/<URI> POSITION SENSITIVE
    @Override
    public String getEndpointTypeFromTopic(String topic) {
        String modified_topic = this.removeRequestTagFromTopic(topic); // strips <topic_root>/request/endpoints/ 
        String[] items = modified_topic.split("/");
        if (items.length >= 1 && items[0].trim().length() > 0) { // POSITION SENSITIVE
            return items[0].trim();                              // POSITION SENSITIVE
        }
        return null;
    }

    // get the resource URI from the topic (request topic sent) 
    // format: <topic_root>/request/endpoints/<ep_type>/<endpoint name>/<URI>
    @Override
    public String getResourceURIFromTopic(String topic) {
        // get the endpoint type 
        String ep_type = this.getEndpointTypeFromTopic(topic);

        // get the endpoint name
        String ep_name = this.getEndpointNameFromTopic(topic);

        // get the URI...
        return this.getResourceURIFromTopic(topic, ep_type, ep_name);
    }
    
    // format: <topic_root>/request/endpoints/<ep_type>/<endpoint name>/<URI> POSITION SENSITIVE
    @Override
    public String getCoAPVerbFromTopic(String topic) {
        // not present in topic by default 
        this.errorLogger().warning("PeerProcessor: WARNING topic: " + topic + " requesting CoAP verb (not present)");
        return null;
    }
    
    // get the resource URI from the topic (request topic sent) 
    // format: <topic_root>/request/endpoints/<ep_type>/<endpoint name>/<URI> POSITION SENSITIVE
    protected String getResourceURIFromTopic(String topic, String ep_type, String ep_name) {
        String modified_topic = this.removeRequestTagFromTopic(topic);  // strips <topic_root>/request/endpoints/ POSITION SENSITIVE
        return modified_topic.replace(ep_type + "/" + ep_name, "");      // strips <ep_type>/<endpoint name> POSITION SENSITIVE
    }
    
    // strip off the request TAG
    // mbed/request/<ep_type>/<endpoint>/<URI> --> <ep_type>/<endpoint>/<URI> POSITION SENSITIVE
    protected String removeRequestTagFromTopic(String topic) {
        if (topic != null) {
            String stripped = topic.replace(this.getTopicRoot() + this.getRequestTag() + "/", "");
            this.errorLogger().info("PeerProcessor: topic: " + topic + " stripped: " + stripped);
            return stripped;
        }
        return null;
    }

    // returns /mbed/<domain>/new_registration/<ep_type>/<endpoint>
    protected String createNewRegistrationTopic(String ep_type, String ep_name) {
        return this.createBaseTopic("new_registration") + "/" + ep_type + "/" + ep_name;
    }

    // returns /mbed/<domain>/request/<ep_type>
    protected String createEndpointResourceRequest() {
        return this.createEndpointResourceRequest(null);
    }

    // returns /mbed/<domain>/request/<ep_type>
    protected String createEndpointResourceRequest(String ep_type) {
        String suffix = "";
        if (ep_type != null && ep_type.length() > 0) {
            suffix = "/" + ep_type;
        }
        return this.createBaseTopic("request") + suffix;
    }
    
    // returns mbed/<domain>/notify/<ep_type>/<endpoint>/<uri>
    protected String createObservationTopic(String ep_type, String ep_name, String uri) {
        return this.createBaseTopic(this.m_observation_key) + "/" + ep_type + "/" + ep_name + uri;
    }

    // returns mbed/<domain>/cmd-response/<ep_type>/<endpoint>/<uri>
    protected String createResourceResponseTopic(String ep_type, String ep_name, String uri) {
        return this.createBaseTopic(this.m_cmd_response_key) + "/" + ep_type + "/" + ep_name + uri;
    }
    
    // get the observability of a given resource
    protected boolean isObservableResource(Map resource) {
        try {
            return (Boolean)resource.get("obs");
        }
        catch (Exception ex) {
            // silent
        }
        return false;
    }

    // parse the device-deletions body
    protected String[] parseDeviceDeletionsBody(Map body) {
        // explicit device-deletions
        List list = (List) body.get("device-deletions");
        if (list != null && list.size() > 0) {
            return list.toString().replace("[", "").replace("]", "").replace(",", " ").split(" ");
        }
        return new String[0];
    }
    
    // parse the de-registration body
    protected String[] parseDeRegistrationBody(Map body) {
        // explicit de-registrations
        List list = (List) body.get("de-registrations");
        if (list != null && list.size() > 0) {
            return list.toString().replace("[", "").replace("]", "").replace(",", " ").split(" ");
        }
        return new String[0];
    }
    
    // parse the registrations-expired body
    protected String[] parseRegistrationsExpiredBody(Map body) {
        // registrations-expired (implicit de-registration)
        List list = (List) body.get("registrations-expired");
        if (list != null && list.size() > 0) {
            return list.toString().replace("[", "").replace("]", "").replace(",", " ").split(" ");
        }
        return new String[0];
    }
    
    // pull the EndpointName from the message
    protected String getCoAPEndpointName(String message) {
        // expected format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        //this.errorLogger().info("getCoAPValue: payload: " + message);
        Map parsed = this.tryJSONParse(message);
        String val = null;
        if (parsed != null) {
            val = (String) parsed.get("ep");
            if (val == null || val.length() == 0) {
                val = (String) parsed.get("deviceId");
            }
        }
        return val;
    }

    // pull the CoAP verb from the message
    protected String getCoAPVerb(String message) {
        // expected format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        //this.errorLogger().info("getCoAPValue: payload: " + message);
        Map parsed = this.tryJSONParse(message);
        String val = null;
        if (parsed != null) {
            val = (String) parsed.get("coap_verb");
            if (val == null || val.length() == 0) {
                val = (String) parsed.get("method");
            }
        }

        // map to lower case
        if (val != null) {
            val = val.toLowerCase();
        }
        return val;
    }
    
    // get the resource URI from the message
    protected String getCoAPURI(String message) {
        // expected format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        //this.errorLogger().info("getCoAPURI: payload: " + message);
        Map parsed = this.tryJSONParse(message);
        String val = null;
        if (parsed != null) {
            val = (String) parsed.get("path");
            if (val == null || val.length() == 0) {
                val = (String) parsed.get("resourceId");
            }
        }

        // adapt for those variants that have path as "311/0/5850" vs. "/311/0/5850"... 
        if (val != null && val.charAt(0) != '/') {
            // prepend a "/"
            val = "/" + val;
        }
        return val;
    }
    
    // get the resource payload from the message 
    protected String getCoAPPayload(String message) {
        // expected format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        //this.errorLogger().info("getCoAPValue: payload: " + message);
        Map parsed = this.tryJSONParse(message);
        String val = null;
        if (parsed != null) {
            val = (String) parsed.get("new_value");
            if (val == null || val.length() == 0) {
                val = (String) parsed.get("payload");
                if (val != null) {
                    return val;
                }
            }
        }
        return null;
    }
    
    // get the resource value from the message
    protected String getCoAPValue(String message) {
        // expected format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        //this.errorLogger().info("getCoAPValue: payload: " + message);
        Map parsed = this.tryJSONParse(message);
        String val = null;
        if (parsed != null) {
            val = (String) parsed.get("new_value");
            if (val == null || val.length() == 0) {
                val = (String) parsed.get("payload");
                if (val != null) {
                    // see if the value is Base64 encoded
                    String last = val.substring(val.length() - 1);
                    if (val.contains("==") || last.contains("=")) {
                        // value appears to be Base64 encoded... so decode... 
                        try {
                            // DEBUG
                            this.errorLogger().info("PeerProcessor: Value: " + val + " flagged as Base64 encoded... decoding...");

                            // Decode
                            val = new String(Base64.decodeBase64(val));

                            // DEBUG
                            this.errorLogger().info("PeerProcessor: Base64 Decoded Value: " + val);
                        }
                        catch (Exception ex) {
                            // just use the value itself...
                            this.errorLogger().info("PeerProcessor: Exception in base64 decode", ex);
                        }
                    }
                }
            }
        }
        return val;
    }
    
    // pull any mDC/mDS REST options from the message (optional)
    protected String getRESTOptions(String message) {
        // expected format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get", "options":"noResp=true" }
        //this.errorLogger().info("getCoAPValue: payload: " + message);
        Map parsed = this.tryJSONParse(message);
        if (parsed != null) {
            return (String) parsed.get("options");
        }
        return null;
    }
    
    // retrieve a specific element from the topic structure
    protected String getTopicElement(String topic, int index) {
        String element = "";
        String[] parsed = topic.split("/");
        if (parsed != null && parsed.length > index) {
            element = parsed[index];
        }
        
        // map to lower case.. 
        if (element != null) {
            element = element.toLowerCase();
        }
        
        return element;
    }
    
     // create the URI path from the topic
    protected String getURIPathFromTopic(String topic, int start_index) {
        try {
            // split by forward slash
            String tmp_slash[] = topic.split("/");

            // we now re-assemble starting from a specific index
            StringBuilder buf = new StringBuilder();
            for (int i = start_index; tmp_slash.length > 5 && i < tmp_slash.length; ++i) {
                buf.append("/");
                buf.append(tmp_slash[i]);
            }

            return buf.toString();
        }
        catch (Exception ex) {
            // Exception during parse
            this.errorLogger().info("PeerProcessor: WARNING: getURIPathFromTopic: Exception: " + ex.getMessage());
        }
        return null;
    }
    
    // create the observation
    protected String createObservation(String verb, String ep_name, String uri, String payload, String value) {
        Map notification = new HashMap<>();
        
        // needs to look like this:  {"path":"/303/0/5700","payload":"MjkuNzU\u003d","max-age":"60","ep":"350e67be-9270-406b-8802-dd5e5f20","value":"29.75"}    
        if (value != null) {
            notification.put("value", this.fundamentalTypeDecoder().getFundamentalValueFromString(value));
        }
        if (payload != null) {
            notification.put("payload",payload);
        }

        // Unified Format?
        if (this.unifiedFormatEnabled() == true) {
            if (uri != null && uri.charAt(0) == '/') {
                notification.put("resourceId", uri.substring(1));
            }
            else {
                notification.put("resourceId", uri);
            }
            notification.put("deviceId", ep_name);
            notification.put("method", verb);
        }
        else {
            if (uri != null && uri.charAt(0) == '/') {
                notification.put("path", uri.substring(1));
            }
            else {
                notification.put("path", uri);
            }
            notification.put("ep", ep_name);
            notification.put("coap_verb", verb);
        }

        // we will send the raw CoAP JSON... AWSIoT can parse that... 
        String coap_raw_json = this.jsonGenerator().generateJson(notification);

        // strip off []...
        String coap_json_stripped = this.stripArrayChars(coap_raw_json);

        // encapsulate into a coap/device packet...
        String coap_json = coap_json_stripped;

        // DEBUG
        this.errorLogger().info("PeerProcessor: CoAP notification(" + verb + " REPLY): " + coap_json);

        // return the generic MQTT observation JSON...
        return coap_json;
    }
    
    // reformat response topic per draft MQTT format rules
    public String createDraftFormatReplyTopic(Map record) {
        // DEBUG
        this.errorLogger().info("PeerProcessor: RECORD: " + record);
        
        // create the draft topic 
        String ep = (String)record.get("ep_name");
        String topic = this.orchestrator().getTenantID() + "/lwm2m/rd/" + ep + "/downlink";
        
        // DEBUG
        this.errorLogger().info("PeerProcessor(createDraftFormatReplyTopic): " + topic);
        
        // return the topic
        return topic;
    }
    
    // MQTT DRAFT: convert coap verb to operation
    private Integer coapVerbToOperation(String verb) {
        Integer operation = (Integer)0;
        
        if (verb != null && verb.length() > 0) {
            if (verb.equalsIgnoreCase("get")) {
                // Read
                operation = (Integer)9; 
            }
            
            if (verb.equalsIgnoreCase("put")) {
                // Write
                operation = (Integer)12; 
            }
            
            if (verb.equalsIgnoreCase("post")) {
                // Execute
                operation = (Integer)15; 
            }
        }
        
        return operation;
    }
    
    // MQTT DRAFT: convert operation to coap verb
    private String operationToCoapVerb(Integer operation) {
        if (operation == 9) {
            return "get";
        }
        if (operation == 12) {
            return "put";
        }
        if (operation == 15) {
            return "post";
        }
        return null;
    }
    
    // reformat response payload per draft MQTT format rules
    public byte[] createDraftFormatReplyPayload(Map record,String reply) {
        Integer token = 0;
        
        // DEBUG
        this.errorLogger().info("PeerProcessor: REPLY: " + reply);
        
        // parse the JSON
        Map json = this.tryJSONParse(reply);
        if (json != null) {
            // pull out the relevant elements for the formatted payload
            String verb = (String)json.get("coap_verb");
            String path = (String)json.get("path");
            
            // process the payload
            String b64_coap_payload = (String)json.get("payload");
            Object payload = Utils.decodeCoAPPayloadToObject(b64_coap_payload);
            
            // now create the formatted response
            HashMap<String,Object> draft_reply = new HashMap<>();
            draft_reply.put("paths",path);
            draft_reply.put("payload",payload);
            
            // if the draft format has a token, add it to the response...
            String message = (String)record.get("message");
            Map parsed_message = this.tryJSONParse(message);
            if (parsed_message != null && parsed_message.containsKey("token") == true) {
                token = (Integer)parsed_message.get("token");
            }
            draft_reply.put("token",token);
            
            // map the coap verb to operation type
            draft_reply.put("operation", this.coapVerbToOperation(verb));
            
            // stringify the json
            String draft_reply_str = this.jsonGenerator().generateJson(draft_reply);
        
            // Convert the JSON to CBOR and return the raw bytes
            return this.jsonToCbor(draft_reply_str);
        }
        return null;
    }
    
    // convert to JSON from CBOR
    protected String cborToJson(byte[] cbor_bytes) {
        try {
            CBORFactory factory = new CBORFactory();
            ObjectMapper mapper = new ObjectMapper(factory);
            JsonNode json = mapper.readValue(cbor_bytes, JsonNode.class);
            return json.asText();
        } 
        catch (IOException e) {
            this.errorLogger().warning("PeerProcessor: Exception converting CBOR to JSON", e);
        }
        return null;
    }
    
    // convert to CBOR from JSON
    protected byte[] jsonToCbor(String json) {
	try {
            CBORFactory factory = new CBORFactory();
            ObjectMapper mapper = new ObjectMapper(factory);
            return mapper.writeValueAsBytes(json);
	} 
        catch (JsonProcessingException e) {
            this.errorLogger().warning("PeerProcessor: Exception converting JSON to CBOR", e);
	}
        return null;
    }
    
    // default formatter for AsyncResponse replies
    public String formatAsyncResponseAsReply(Map async_response, String verb) {
        // DEBUG
        this.errorLogger().info("PeerProcessor: formatAsyncResponseAsReply(" + verb + ") AsyncResponse: " + async_response);

        // Handle AsyncReplies that are CoAP GETs
        if (verb != null && verb.equalsIgnoreCase("GET") == true) {
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

                        // build out the observation
                        String message = this.createObservation(verb, ep_name, uri, payload, value);

                        // DEBUG
                        this.errorLogger().info("PeerProcessor: formatAsyncResponseAsReply: Created(" + verb + ") observation: " + message + " reply topic: " + async_response.get("reply_topic"));

                        // return the message
                        return message;
                    }
                }
            }
            catch (Exception ex) {
                // Error in creating the observation message from the AsyncResponse GET reply... 
                this.errorLogger().warning("PeerProcessor: formatAsyncResponseAsReply(GET): Exception in formatAsyncResponseAsReply(): ", ex);
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

                        // build out the observation
                        String message = this.createObservation(verb, ep_name, uri, payload, value);

                        // DEBUG
                        this.errorLogger().info("PeerProcessor: formatAsyncResponseAsReply: Created(" + verb + ") Observation: " + message);

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

                    // build out the observation
                    String message = this.createObservation(verb, ep_name, uri, payload, value);

                    // DEBUG
                    this.errorLogger().info("PeerProcessor: formatAsyncResponseAsReply Created(" + verb + ") Observation: " + message);

                    // return message
                    return message;
                }
            }
            catch (Exception ex) {
                // Error in creating the observation message from the AsyncResponse PUT reply... 
                this.errorLogger().warning("PeerProcessor: formatAsyncResponseAsReply(PUT): Exception in formatAsyncResponseAsReply(): ", ex);
            }
        }

        // return null message
        return null;
    }
    
    // split AsyncID
    private String[] splitAsyncID(String id) {
        String[] parts = null;

        if (id != null && id.length() > 0) {
            // copy the string
            String tmp = id;

            // loop through and remove key delimiters
            tmp = tmp.replace('#', ' ');
            tmp = tmp.replace('@', ' ');
            tmp = tmp.replace('/', ' ');

            // split
            parts = tmp.split(" ");
        }

        // return the parsed parts
        return parts;
    }
    
    // extract the URI from the async-id
    // format: 1408956550#cc69e7c5-c24f-43cf-8365-8d23bb01c707@decd06cc-2a32-4e5e-80d0-7a7c65b90e6e/303/0/5700
    protected String getURIFromAsyncID(String id) {
        String uri = null;

        // split
        String[] parts = this.splitAsyncID(id);
        if (parts != null && parts.length > 1) {
            // re-assemble the URI
            uri = "/";
            for (int i = 3;i < parts.length; ++i) {
                uri += parts[i];
                if (i < (parts.length - 1)) {
                    uri += "/";
                }
            }

            // DEBUG
            this.errorLogger().info("PeerProcessor: getURIFromAsyncID: URI: " + uri);
        }
        else {
            // mbed Cloud async-response ID format has changed - so we need to pull this from the async-response record
            uri = this.asyncResponseManager().getURIFromAsyncID(id);
            
            // DEBUG
            this.errorLogger().info("PeerProcessor: getURIFromAsyncID: (async-response) URI: " + uri);
        }

        // return the URI
        return uri;
    }

    // extract the endpoint name from the async-id
    // format: 1408956550#cc69e7c5-c24f-43cf-8365-8d23bb01c707@decd06cc-2a32-4e5e-80d0-7a7c65b90e6e/303/0/5700
    protected String getEndpointNameFromAsyncID(String id) {
        String name = null;
                
        // split
        String[] parts = this.splitAsyncID(id);
        if (parts != null && parts.length > 1) {
            // record the name
            name = parts[1];
            
            // DEBUG
            this.errorLogger().info("PeerProcessor: getEndpointNameFromAsyncID: endpoint: " + name);
        }
        else {
            // mbed Cloud async-response ID format has changed - so we need to pull this from the async-response record
            name = this.asyncResponseManager().getEndpointNameFromAsyncID(id);
            
            // DEBUG
            this.errorLogger().info("PeerProcessor: getEndpointNameFromAsyncID: (async-response) endpoint: " + name);
        }

        // return the endpoint name
        return name;
    }
    
    // OVERRIDE: process a received new registration for AWSIoT
    public void processNewRegistration(Map data) {
        this.processRegistration(data,"registrations");
    }
    
    // record an async response to process later (default)
    public void recordAsyncResponse(String response, String uri, Map ep, AsyncResponseProcessor processor) {
        if (this.asyncResponseManager() != null) {
            this.asyncResponseManager().recordAsyncResponse(response, uri, ep, processor);
        }
    }
    
    // process & route async response messages (defaulted implementation)
    public void processAsyncResponses(Map data) {
        List responses = (List) data.get("async-responses");
        for (int i = 0; responses != null && i < responses.size(); ++i) {
            this.asyncResponseManager().processAsyncResponse((Map) responses.get(i));
        }
    }
    
    //
    // These methods are stubbed out by default... but need to be implemented in derived classes.
    // They are the "responders" to mDS events for devices and initialize/start and stop "listeners"
    // that are appropriate for the peer/3rd Party...(for example MQTT...)
    //
    
    // intialize a listener for the peer
    public void initListener() {
        // not used
    }

    // stop the listener for a peer
    public void stopListener() {
        // not used
    }
    
    // GenericSender Implementation: send a message (String)
    @Override
    public boolean sendMessage(String to, String message) {
        // not used
        return false;
    }
    
    // GenericSender Implementation: send a message (byte array)
    @Override
    public boolean sendMessage(String to, byte[] bytes) {
        // not used
        return false;
    }
    
    // process new device registration
    protected Boolean registerNewDevice(Map message) {
        // nothing to do in base class
        return false;
    }

    // process device re-registration
    protected Boolean reregisterDevice(Map message) {
        // nothing to do in base class
        return false;
    }
    
    // process device deletion
    protected Boolean deleteDevice(String device) {
        // nothing to do in base class
        return false;
    }

    // process device registration expired
    protected Boolean expireDeviceRegistration(String device) {
        // nothing to do in base class
        return false;
    }
    
    // read the requested data from Command Requestor
    protected String read(HttpServletRequest request) {
        try {
            BufferedReader reader = request.getReader();
            String line = reader.readLine();
            StringBuilder buf = new StringBuilder();
            while (line != null) {
                buf.append(line);
                line = reader.readLine();
            }
            return buf.toString();
        }
        catch (IOException ex) {
            // silent
        }
        return null;
    }

    // send the REST response back to the Command Requestor
    protected void sendResponseToCommandRequestor(String content_type, HttpServletRequest request, HttpServletResponse response, String header, String body) {
        try {
            response.setContentType(content_type);
            response.setHeader("Pragma", "no-cache");
            try (PrintWriter out = response.getWriter()) {
                if (header != null && header.length() > 0) {
                    out.println(header);
                }
                if (body != null && body.length() > 0) {
                    out.println(body);
                }
            }
        }
        catch (IOException ex) {
            this.errorLogger().critical("PeerProcessor: Unable to send response back to command requestor...", ex);
        }
    }
    
    // add device to seen list
    protected synchronized void addDeviceToSeenList(String id) {
        if (id != null && id.length() > 0) {
            this.m_device_seen_list.add(id);
        }
    }
    
    // is the device previously seen?
    protected synchronized boolean deviceAlreadySeen(String id) {
        if (id != null && id.length() > 0) {
            for(int i=0;i<this.m_device_seen_list.size();++i) {
                if (this.m_device_seen_list.get(i).equalsIgnoreCase(id)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    // remove the device from the seen list
    protected synchronized void removeDeviceFromSeenList(String id) {
        if (id != null && id.length() > 0) {
            for(int i=0;i<this.m_device_seen_list.size();++i) {
                if (this.m_device_seen_list.get(i).equalsIgnoreCase(id)) {
                    this.m_device_seen_list.remove(i);
                    return;
                }
            }
        }
    }
}