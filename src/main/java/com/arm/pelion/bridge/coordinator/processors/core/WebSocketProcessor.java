/**
 * @file WebSocketProcessor.java
 * @brief Pelion web socket processor
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

import com.arm.pelion.bridge.coordinator.processors.arm.PelionProcessor;
import com.arm.pelion.bridge.core.ErrorLogger;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Future;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.WebSocketClient;

/**
 * WebSocket processor for Pelion
 *
 * @author Doug Anson
 */
public class WebSocketProcessor extends Thread {
    private static final int API_KEY_UNCONFIGURED_WAIT_MS = 600000;     // pause for 5 minutes if an unconfigured API key is detected
    private static final int API_KEY_CONFIGURED_WAIT_MS = 10000;        // pause for 10 seconds if a configured API key is detected
    private PelionProcessor m_pelion_processor = null;
    private boolean m_running = false;
    private String m_uri = null;
    private String m_auth = null;
    private WebSocketClient m_ws = null;
    private Future<Session> m_session = null;

    // default constructor
    public WebSocketProcessor(PelionProcessor pelion_processor) {
        this.m_pelion_processor = pelion_processor;
        this.m_running = false;
        this.m_ws = null;
        this.m_uri = this.initWebSocketURI();
        this.m_auth = this.initWebSocketAuth();
        this.configurePelionForWebSockets();
    }
    
    // direct Pelion to use WebSockets
    private boolean configurePelionForWebSockets() {
        if (this.isConfiguredAPIKey()) {
            return this.m_pelion_processor.enableWebSockets();
        }
        return false;
    }
    
    // initialize the websocket URI
    private String initWebSocketURI() {
        if (this.isConfiguredAPIKey()) {
            String mds_host = this.m_pelion_processor.preferences().valueOf("api_endpoint_address");
            String connect_version = this.m_pelion_processor.preferences().valueOf("mds_rest_version");
            return "wss://" + mds_host + "/v" + connect_version + "/notification/websocket-connect";
        }
        return null;
    }
    
    // initialize the websocket auth payload
    private String initWebSocketAuth() {
        if (this.isConfiguredAPIKey()) {
            // get the API key if configured...
            String api_key = this.m_pelion_processor.apiToken();
            
            // construct our payload...
            return "pelion_" + api_key;
        }
        return null;
    }
    
    // is our API key configured?
    private boolean isConfiguredAPIKey() {
        return this.m_pelion_processor.isConfiguredAPIKey();
    }

    // get our error logger
    private ErrorLogger errorLogger() {
        return this.m_pelion_processor.errorLogger();
    }

    // initialize the websocket
    public void startWebSocketListener() {
        // DEBUG
        this.errorLogger().info("WebSocketProcessor: Removing previous webhook (if any)...");
        
        // delete any older webhooks
        this.m_pelion_processor.removeWebhook();
        
        // DEBUG
        this.errorLogger().info("WebSocketProcessor: Listening on WebSocket...");

        // start our thread...
        this.start();
    }

    /**
     * run method for the receive thread
     */
    @Override
    public void run() {
        if (!this.m_running) {
            this.m_running = true;
            this.webSocketProcessor();
        }
    }
    
    private boolean initWebSocket() {
        try {
            if (this.m_ws == null) {
                this.m_ws = new WebSocketClient();
                this.m_session = this.m_ws.connect(null,new URI(this.m_uri));
            }
        }
        catch (IOException | URISyntaxException ex) {
            // exception during websocket init
        }
        return false;
    }

    /**
     * web socket thread loop
     */
    private void webSocketProcessor() {
        try {
            // initialize the websocket
            this.initWebSocket();
            
            //
        }
        catch (Exception ex) {
            // note but keep going...
            this.errorLogger().warning("WebSocketProcessor: Exception caught: " + ex.getMessage() + ". Continuing...");
        }
    }
}