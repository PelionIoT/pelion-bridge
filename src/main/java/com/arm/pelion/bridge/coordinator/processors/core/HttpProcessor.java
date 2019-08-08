/**
 * @file  HttpProcessor.java
 * @brief Core Http Processor methods for the Pelion
 * @author Doug Anson
 * @version 1.0
 * @see
 *
 * Copyright 2015-2018. ARM Ltd. All rights reserved.
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
import com.arm.pelion.bridge.core.Processor;
import com.arm.pelion.bridge.transport.HttpTransport;
import java.io.BufferedReader;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Http-based Processor for Pelion (core methods)
 * @author Doug Anson
 */
public class HttpProcessor extends Processor {
    private HttpTransport m_http = null;
    private String m_content_type = null;
    private String m_api_token = null;
     
    // primary constructor
    public HttpProcessor(Orchestrator orchestrator, HttpTransport http) {
        super(orchestrator, null);
        
        // Http Transport
        this.m_http = http;
        
        // Default content type
        this.m_content_type = orchestrator.preferences().valueOf("mds_content_type");
        
        // Pelion API Token
        this.m_api_token = this.orchestrator().preferences().valueOf("mds_api_token");
        if (this.m_api_token == null || this.m_api_token.length() == 0) {
            // new key to use..
            this.m_api_token = this.orchestrator().preferences().valueOf("api_key");
        }
    }
    
    // read the requested data from mbed Cloud
    protected String read(HttpServletRequest request) {
        try {
            BufferedReader reader = request.getReader();
            String line = reader.readLine();
            StringBuilder buf = new StringBuilder();
            while (line != null) {
                buf.append(line);
                line = reader.readLine();
            }
            //reader.close();
            
            // DEBUG
            this.errorLogger().info("PelionProcessor: Read: " + buf.toString());
            return buf.toString();
        }
        catch (Exception ex) {
            // ERROR
            this.errorLogger().info("PelionProcessor: Exception in READ: " + ex.getMessage(),ex);
        }
        return "{}";
    }

    // send the REST response back to Pelion
    protected void sendResponseToPelion(String content_type, HttpServletRequest request, HttpServletResponse response, String header, String body) {
        response.setContentType(content_type);
        response.setHeader("Pragma", "no-cache");
        try {    
            try (PrintWriter out = response.getWriter()) {
                if (header != null && header.length() > 0) {
                    out.println(header);
                }
                if (body != null && body.length() > 0) {
                    out.println(body);
                }
                out.flush();
                out.close();
                response.setStatus(200);
            }
        }
        catch (Exception ex ) {
            this.errorLogger().info("Pelion Processor: Exceutpion during send response back to Pelion...(OK)");
        }
        response.setStatus(200);
    }
    
    // get the API Token
    protected String apiToken() {
        return this.m_api_token;
    }
    
    // get the last response code
    public int getLastResponseCode() {
        return this.m_http.getLastResponseCode();
    }

    // invoke HTTP GET request (SSL)
    protected String httpsGet(String url) {
        return this.httpsGet(url,this.m_content_type,this.m_api_token);
    }
    
    // invoke peristent HTTPS Get
    public String persistentHTTPSGet(String url) {
        return this.persistentHTTPSGet(url, this.m_content_type);
    }

    // invoke peristent HTTPS Get
    protected String persistentHTTPSGet(String url, String content_type) {
        String response = this.m_http.httpsPersistentGetApiTokenAuth(url, this.m_api_token, null, content_type);
        this.errorLogger().info("persistentHTTPSGet: response: " + this.m_http.getLastResponseCode());
        return response;
    }

    // invoke HTTP GET request (SSL)
    protected String httpsGet(String url, String content_type,String api_key) {
        String response = this.m_http.httpsGetApiTokenAuth(url, api_key, null, content_type);
        this.errorLogger().info("httpsGet: response: " + this.m_http.getLastResponseCode());
        return response;
    }

    // invoke HTTP PUT request (SSL)
    protected String httpsPut(String url) {
        return this.httpsPut(url, null);
    }

    // invoke HTTP PUT request (SSL)
    protected String httpsPut(String url, String data) {
        return this.httpsPut(url, data, this.m_content_type, this.m_api_token);
    }

    // invoke HTTP PUT request (SSL)
    protected String httpsPut(String url, String data, String content_type, String api_key) {
        String response = this.m_http.httpsPutApiTokenAuth(url, api_key, data, content_type);
        this.errorLogger().info("httpsPut: response: " + this.m_http.getLastResponseCode());
        return response;
    }

    // invoke HTTP POST request (SSL)
    protected String httpsPost(String url, String data) {
        return this.httpsPost(url, data, this.m_content_type, this.m_api_token);
    }
    
    // invoke HTTP POST request (SSL)
    protected String httpsPost(String url, String data, String content_type, String api_key) {
        String response = this.m_http.httpsPostApiTokenAuth(url, api_key, data, content_type);
        this.errorLogger().info("httpsPost: response: " + this.m_http.getLastResponseCode());
        return response;
    }

    // invoke HTTP DELETE request
    protected String httpsDelete(String url) {
        return this.httpsDelete(url, this.m_content_type, this.m_api_token);
    }

    // invoke HTTP DELETE request
    protected String httpsDelete(String url, String content_type, String api_key) {
        String response = this.m_http.httpsDeleteApiTokenAuth(url, api_key, null, content_type);
        this.errorLogger().info("httpDelete: response: " + this.m_http.getLastResponseCode());
        return response;
    }
}