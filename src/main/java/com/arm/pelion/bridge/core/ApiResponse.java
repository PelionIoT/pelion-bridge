/**
 * @file ApiResponse.java
 * @brief mbed Device Server API Response
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
package com.arm.pelion.bridge.core;

import com.arm.pelion.bridge.coordinator.Orchestrator;
import java.util.HashMap;
import java.util.Map;

/**
 * API Response class
 * 
 * @author Doug Anson
 */
public class ApiResponse extends Processor {
    private static final String DEFAULT_CONTENT_TYPE = "application/json";  // default content type
    private int m_request_id;
    private String m_request_uri;
    private String m_request_data;
    private String m_request_options;
    private String m_request_verb;
    private String m_request_caller_id;
    private String m_content_type;
    private int m_response_http_code;
    private String m_response_data;
    
    // default constructor
    public ApiResponse(Orchestrator orchestrator,String suffix,String uri,String data,String options,String verb,String caller_id,String content_type,int request_id) {
        super(orchestrator,suffix);
        this.m_request_id = request_id;
        this.m_request_uri = uri;
        this.m_request_data = data;
        this.m_request_options = options;
        this.m_request_verb = verb;
        this.m_request_caller_id = caller_id;
        this.m_content_type = content_type;
        this.m_response_http_code = 900;
        this.m_response_data = "";
    }
    
    // set the response data
    public void setReplyData(String response_data) {
        this.m_response_data = response_data;
    }
    
    // set the HTTP response code
    public void setHttpCode(int http_code) {
        this.m_response_http_code = http_code;
    }
    
    // get the HTTP response code
    public int getHttpCode() {
        return this.m_response_http_code;
    }
    
    // get the response data
    public String getReplyData() {
        return this.m_response_data;
    }
    
    // get the content type
    public String getContentType() {
        return this.m_content_type;
    }
    
    // get the request URI
    public String getRequestURI() {
        return this.m_request_uri;
    }
    
    // get the request data
    public String getRequestData() {
        return this.m_request_data;
    }
    
    // get the request options
    public String getRequestOptions() {
        return this.m_request_options;
    }
    
    // get the request http verb
    public String getRequestVerb() {
        return this.m_request_verb;
    }
    
    // get the request caller ID
    public String getRequestCallerID() {
        return this.m_request_caller_id;
    }
    
    // create the response JSON
    public String createResponseJSON() {
        HashMap<String,Object> map = new HashMap<>();
        if (this.m_request_uri != null && this.m_request_uri.length() > 0) {
            map.put("api_uri",this.m_request_uri);
        }
        else {
            map.put("api_uri","none");
        }
        if (this.m_request_options != null && this.m_request_options.length() > 2) {
            map.put("api_options",this.m_request_options);
        }
        else {
            map.put("api_options","none");
        }
        if (this.m_request_data != null && this.m_request_data.length() > 0) {
            map.put("api_request_data",this.parseJson(this.m_request_data));
        }
        else {
            map.put("api_request_data","none");
        }
        if (this.m_request_verb != null && this.m_request_verb.length() > 0) {
            map.put("api_verb",this.m_request_verb);
        }
        else {
            map.put("api_verb","none");
        }
        if (this.m_request_caller_id != null && this.m_request_caller_id.length() > 0) {
            map.put("api_caller_id",this.m_request_caller_id);
        }
        else {
            map.put("api_caller_id","none");
        }
        if (this.m_content_type != null && this.m_content_type.length() > 0) {
            map.put("api_content_type",this.m_content_type);
        }
        else {
            map.put("api_content_type",DEFAULT_CONTENT_TYPE);     // defaulted
        }
        if (this.m_response_data != null && this.m_response_data.length() > 0) {
            map.put("api_response",this.parseResponseData(this.m_response_data));
        }
        else {
            map.put("api_response","none");
        }
        map.put("api_http_code",this.m_response_http_code);
        map.put("api_request_id",this.m_request_id);
        return this.jsonGenerator().generateJson(map);
    }
    
    // create the parsed JSON response as a Map
    private Map parseResponseData(String data) {
        // try direct parse of input data first
        Map parsed = this.tryJSONParse(data);
        if (parsed == null || parsed.isEmpty() == true) {
            // create a JSON with the input data and try that...
            HashMap<String,String> d = new HashMap<>();
            if (data == null || data.length() <= 0) {
                data = "none";
            }
            d.put("payload", data);
            parsed = d;
        }
        return parsed;
    }
}
