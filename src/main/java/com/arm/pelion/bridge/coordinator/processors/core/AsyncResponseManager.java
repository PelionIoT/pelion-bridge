/**
 * @file  AsyncResponseManager.java
 * @brief async response manager for Pelion async response handling in pelion-bridge
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

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.GenericSender;
import com.arm.pelion.bridge.core.ErrorLogger;
import com.arm.pelion.bridge.json.JSONParser;
import java.util.HashMap;
import java.util.Map;

/**
 * async response manager handling async responses from Pelion
 *
 * @author Doug Anson
 */
public class AsyncResponseManager {

    private final HashMap<String, HashMap<String, Object>> m_responses;
    private final Orchestrator m_manager;

    public AsyncResponseManager(Orchestrator manager) {
        this.m_manager = manager;
        this.m_responses = new HashMap<>();
    }

    // get the error logger
    private Orchestrator manager() {
        return this.m_manager;
    }

    // get the error logger
    private ErrorLogger errorLogger() {
        return this.manager().errorLogger();
    }

    // get the async response ID
    private String id(Map response) {
        return (String) response.get("id");
    }

    // do we have a recording for a given AsyncResponse?
    private boolean haveRecordForAsyncResponse(String id) {
        return (this.m_responses.containsKey(id) == true);
    }
    
    // get the async-response for a given ID
    private Map<String,Object> getRecordForAsyncResponse(String id) {
        Map<String,Object> record = null;
        if (this.haveRecordForAsyncResponse(id)) {
            record = this.m_responses.get(id);
        }
        return record;
    }
    
    // extract the URI from the async-id
    public String getURIFromAsyncID(String id) {
        String uri = null;
        Map<String,Object> record = this.getRecordForAsyncResponse(id);
        if (record != null) {
            uri = (String)record.get("uri");
        }
        
        // DEBUG
        this.errorLogger().info("recordAsyncResponse: getURIFromAsyncID: ID: " + id + " URI: " + uri);
        
        // return the URI
        return uri;
    }
    
    // get the CoAP Endpoint Name from the async record
    public String getEndpointNameFromAsyncID(String id) {
        String name = null;
        Map<String,Object> record = this.getRecordForAsyncResponse(id);
        if (record != null) {
            name = (String)record.get("ep_name");
        }
        
        // DEBUG
        this.errorLogger().info("recordAsyncResponse: getEndpointNameFromAsyncID: ID: " + id + " endpoint: " + name);
        
        // return the endpoint name
        return name;
    }

    // record an AsyncResponse
    public void recordAsyncResponse(String response, String uri, Map ep, AsyncResponseProcessor processor) {
        this.recordAsyncResponse(response, (String) ep.get("verb"), null, null, null, null, null, (String) ep.get("ep"), uri, processor, ep);
    }

    // record an AsyncResponse
    public void recordAsyncResponse(String response, String coap_verb, GenericSender sender, PeerProcessor proc, String response_topic, String reply_topic, String message, String ep_name, String uri) {
        this.recordAsyncResponse(response, coap_verb, sender, proc, response_topic, reply_topic, message, ep_name, uri, null, null);
    }

    // record an AsyncResponse
    public void recordAsyncResponse(String response, String coap_verb, GenericSender sender, PeerProcessor proc, String response_topic, String reply_topic, String message, String ep_name, String uri, AsyncResponseProcessor processor, Map orig_endpoint) {
        // we have to catch exceptions from the JSON parser... 
        try {
            if (response != null) {
                // create a new AsyncResponse record
                HashMap<String, Object> record = new HashMap<>();

                // parse the response
                JSONParser parser = this.manager().getJSONParser();
                Map parsed = parser.parseJson(response);
                if (parsed != null && (String) parsed.get("async-response-id") != null) {
                    // add it to the record too
                    record.put("response_map", parsed);
                    
                    // fill in the record with other good convenient things too... 
                    if (coap_verb != null) {
                        record.put("verb", coap_verb);
                    }
                    if (response != null) {
                        record.put("response", response);
                    }
                    if (sender != null) {
                        record.put("sender", sender);
                    }
                    if (proc != null) {
                        record.put("proc", proc);
                    }
                    if (response_topic != null) {
                        record.put("response_topic", response_topic);
                    }
                    if (reply_topic != null) {
                        record.put("reply_topic", reply_topic);
                    }
                    if (message != null) {
                        record.put("message", message);
                    }
                    if (ep_name != null) {
                        record.put("ep_name", ep_name);
                    }
                    if (uri != null) {
                        record.put("uri", uri);
                    }
                    if (processor != null) {
                        record.put("processor", processor);
                    }
                    if (orig_endpoint != null) {
                        record.put("orig_endpoint", orig_endpoint);
                    }

                    // add the record to our list
                    this.m_responses.put((String) parsed.get("async-response-id"), record);

                    // DEBUG
                    this.errorLogger().info("recordAsyncResponse: Adding Record: ID:" + (String) parsed.get("async-response-id") + " RECORD: " + record);
                }
                else {
                    // WARNING: no async-response-id found in this message... so ignore it.
                    this.errorLogger().info("recordAsyncResponse: No async-response-id found in JSON: " + response + "... Ignoring message...");
                }
            }
            else {
                // WARNING: NO Response provided... so ignore
                this.errorLogger().info("recordAsyncResponse: response is NULL. ignoring...");
            }
        }
        catch (Exception ex) {
            // CRITICAL: unable to parse the JSON... 
            this.errorLogger().warning("recordAsyncResponse: Exception in parsing JSON: " + response + "... Unable to parse async message", ex);
        }
    }

    // process AsyncResponse
    @SuppressWarnings("empty-statement")
    public void processAsyncResponse(Map response) {
        // get our AsyncResponse ID
        String id = this.id(response);

        // do we have a record for this AsyncResponse?
        if (this.haveRecordForAsyncResponse(id) == true) {
            // Get the record
            HashMap<String, Object> record = this.m_responses.get(id);

            // call GenericSender if registered...
            GenericSender sender = (GenericSender) record.get("sender");
            if (sender != null) {
                // GenericSender responder registered for this.. to pull the other values.. 
                String response_topic = (String) record.get("response_topic");
                PeerProcessor proc = (PeerProcessor) record.get("proc");
                String verb = (String) record.get("verb");

                // construct the reply message value
                String reply = proc.formatAsyncResponseAsReply(response, verb);
                if (reply != null) {
                    // Get the reply GenericSender "to"...default is the response topic
                    String target_topic = response_topic;

                    // If the reply topic is different that the response topic... it takes preference...
                    if (record.get("reply_topic") != null) {
                        target_topic = (String) record.get("reply_topic");
                    }

                    // DEBUG
                    this.errorLogger().info("processAsyncResponse: sending reply(" + verb + ") to AsyncResponse: ID: " + id + " Topic: " + target_topic + " Message: " + reply);

                    // send the reply...
                    sender.sendMessage(target_topic, reply);
                }
                else {
                    // DEBUG
                    this.errorLogger().info("processAsyncResponse: not sending reply(" + verb + ") to AsyncResponse: ID: " + id + " (OK).");
                }
            }
            
            // call AsyncResponseProcessor if registered....
            AsyncResponseProcessor processor = (AsyncResponseProcessor) record.get("processor");
            if (processor != null) {
                // create the augmented record
                response.put("orig_record", record);

                // DEBUG
                this.errorLogger().info("processAsyncResponse: Calling registered AsyncResponseProcessor for ID: " + id);

                // invoke the processor
                processor.processAsyncResponse(response);
            }

            // DEBUG
            this.errorLogger().info("processAsyncResponse: Removing record for AsyncResponse: ID: " + id);

            // finally delete the record
            this.m_responses.remove(id);
        }
        else {
            // no record of this resposne... so just ignore it.. 
            this.errorLogger().info("processAsyncResponse: No AsyncResponse record for ID: " + id + " Ignoring: " + response.toString());
        }
    }
}
