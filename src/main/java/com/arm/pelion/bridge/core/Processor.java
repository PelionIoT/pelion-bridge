/*
 * @file Processor.java
 * @brief processor base class
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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arm.pelion.bridge.core;

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.json.JSONGenerator;
import com.arm.pelion.bridge.json.JSONParser;
import java.util.HashMap;
import java.util.Map;

/**
 * Processor base class
 * @author Doug Anson
 */
public class Processor extends BaseClass {
    public static int NUM_COAP_VERBS = 4;                                   // GET, PUT, POST, DELETE
    private static final String DEFAULT_EMPTY_STRING = " ";
    private String m_empty_string = Processor.DEFAULT_EMPTY_STRING;
    protected String m_suffix = null;
    private Orchestrator m_orchestrator = null;
    private JSONGenerator m_json_generator = null;
    private JSONParser m_json_parser = null;
        
    // bulk subscriptions
    protected boolean m_enable_bulk_subscriptions = false;
    
    // Sync lock
    private boolean m_operation_pending = false;
    
    // constructor
    public Processor(Orchestrator orchestrator, String suffix) {
        super(orchestrator.errorLogger(), orchestrator.preferences());
        this.m_suffix = suffix;
        this.m_orchestrator = orchestrator;
        this.m_json_parser = orchestrator.getJSONParser();
        this.m_json_generator = orchestrator.getJSONGenerator();
        
        // Handle the remapping of empty strings so that our JSON parsers wont complain...
        this.m_empty_string = orchestrator.preferences().valueOf("mds_bridge_empty_string", suffix);
        if (this.m_empty_string == null || this.m_empty_string.length() == 0) {
            this.m_empty_string = Processor.DEFAULT_EMPTY_STRING;
        }
        
        // EXPERIMENTAL: bulk subscriptions
        this.m_enable_bulk_subscriptions = this.prefBoolValue("mds_enable_bulk_subscriptions");
        if (this.m_enable_bulk_subscriptions == true) {
            this.errorLogger().info("Bulk subscriptions ENABLED (EXPERIMENTAL)");
        }
        
        // suffix setup
        this.m_suffix = suffix;
        
        // unlock
        this.operationStop();
    }
    
    // create a short JSON message
    protected String createJSONMessage(String key,String value) {
        HashMap<String,String> map = new HashMap<>();
        map.put(key,value);
        return this.createJSONMessage(map);
    }
    
    // create a short JSON message
    protected String createJSONMessage(Map map) {
        return this.jsonGenerator().generateJson(map);
    }
    
    // Lock 
    public synchronized boolean operationStart() {
        if (this.m_operation_pending == false) {
            this.m_operation_pending = true;
            return true;
        }
        return false;
    }
    
    // Pending?
    public synchronized boolean operationPending() {
        return this.m_operation_pending;
    }
    
    // Unlock
    public synchronized void operationStop() {
        this.m_operation_pending = false;
    }
    
    // jsonParser is broken with empty strings... so we have to fill them in with spaces.. 
    private String replaceEmptyStrings(String data) {
        if (data != null) {
            return data.replaceAll("\"\"", "\"" + this.m_empty_string + "\"").replace("[]", "null");
        }
        return data;
    }

    // parse the JSON...
    protected Object parseJson(String json) {
        Object parsed = null;
        String modified_json = "";
        try {
            modified_json = this.replaceEmptyStrings(json);
            if (json != null && json.contains("{") && json.contains("}")) {
                parsed = this.jsonParser().parseJson(modified_json);
            }
        }
        catch (Exception ex) {
            this.orchestrator().errorLogger().warning("JSON parsing exception for: " + json + " MODIFED: " + modified_json +  " Message: " + ex.getMessage(), ex);
            parsed = null;
        }
        return parsed;
    }

    // strip array values... not needed
    protected String stripArrayChars(String json) {
        return json.replace("[", "").replace("]", "");
    }
    
    // attempt a json parse... 
    protected Map tryJSONParse(String payload) {
        HashMap<String, Object> result = new HashMap<>();
        try {
            result = (HashMap<String, Object>) this.orchestrator().getJSONParser().parseJson(this.replaceEmptyStrings(payload));
            return result;
        }
        catch (Exception ex) {
            // parse error
            this.errorLogger().info("tryJSONParse: caught exception. JSON: " + payload + " Exception: "+ ex.getMessage());
        }
        return result;
    }
    
    // protected getters/setters...
    protected JSONParser jsonParser() {
        return this.m_json_parser;
    }

    // get the JSON generator
    protected JSONGenerator jsonGenerator() {
        return this.m_json_generator;
    }

    // get the orchestrator
    public Orchestrator orchestrator() {
        return this.m_orchestrator;
    }
}
