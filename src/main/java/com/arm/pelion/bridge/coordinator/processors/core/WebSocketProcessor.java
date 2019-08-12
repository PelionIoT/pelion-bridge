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
import com.arm.pelion.bridge.core.Utils;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Future;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.client.WebSocketClient;

/**
 * WebSocket processor for Pelion
 *
 * @author Doug Anson
 */
public class WebSocketProcessor extends Thread {
    private static final int WEBSOCKET_TIMEOUT_MS = 6000000;            // set a very long timeout... 
       private PelionProcessor m_pelion_processor = null;
    private boolean m_running = false;
    private String m_uri = null;
    private String m_auth = null;
    private WebSocketClient m_ws = null;
    private Session m_session = null;

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
    
    // connect our websocket
    private boolean connect() {
        try {
            if (this.m_ws == null) {
                // create the websocket...
                this.m_ws = new WebSocketClient();
                
                // start if created...
                try {
                    this.errorLogger().warning("WebSocketProcessor: Starting websocket client...");
                    if (this.m_ws != null) {
                        this.m_ws.start();
                    }

                    // connect the websocket...
                    this.errorLogger().warning("WebSocketProcessor: Connecting websocket: " + this.m_uri + "...");
                    this.m_ws.connect(this,new URI(this.m_uri));
                    return true;
                }
                catch (Exception ex2) {
                    this.errorLogger().warning("WebSocketProcessor: Exception in connect: " + ex2.getMessage());
                }
            }
        }
        catch (Exception ex) {
            // exception during websocket init
            this.errorLogger().warning("WebSocketProcessor: Unable to connect. Exception: " + ex.getMessage());
        }
        return false;
    }
    
    // disconnect our websocket
    public void disconnect() {
        if (this.m_session != null) {
            try {
                this.m_session.disconnect();
                this.m_session.close();
            }
            catch (Exception ex) {
                // silent
            }
        }
    }

    // initialize the websocket
    public void startWebSocketListener() {
        // DEBUG
        this.errorLogger().info("WebSocketProcessor: Removing previous Websocket (if any)...");
        
        // clear any existing websocket
        this.disconnect();
        
        // DEBUG
        this.errorLogger().info("WebSocketProcessor: Connecting our Websocket...");
        this.connect();
    }

    /**
     * run method for the receive thread
     */
    @Override
    public void run() {
        if (!this.m_running) {
            // DEBUG
            this.errorLogger().warning("WebSocketProcessor: Running the notification processor...");
            
            this.m_running = true;
            this.processNotifications();
        }
        else {
            // already running the websocket processing looper
            this.errorLogger().warning("WebSocketProcessor: Already running the notification processor (OK)...");
        }
    }

    /**
     * process notifications from the websocket
     */
    private void processNotifications() {
        while(this.m_running == true) {
            try {
                // get the next notification...
                Utils.waitForABit(this.errorLogger(), 1000);
            }
            catch (Exception ex) {
                // note but keep going...
                this.errorLogger().warning("WebSocketProcessor: Exception caught: " + ex.getMessage() + ". Continuing...");
            }
        }
    }
    
    @OnWebSocketConnect
    public void onConnect(Session session) throws IOException {
        // DEBUG
        this.errorLogger().warning("WebSocketProcessor: onConnect(): CONNECTED");
        this.m_session = session;
        if (session.isOpen() == true) {
            // DEBUG
            this.errorLogger().warning("WebSocketProcessor: Websocket connected... Starting listener...");
            
            // set the timeout...
            this.m_ws.setConnectTimeout(WEBSOCKET_TIMEOUT_MS);
            
            // start our thread...
            this.start();
        }
        else {
            // unable to connect
            this.errorLogger().warning("WebSocketProcessor: Unable to connect Websocket. Not starting listener.");
        }
    }

    @OnWebSocketMessage
    public void onMessage(String message) {
        // DEBUG
        this.errorLogger().warning("WebSocketProcessor: onMessage(): MESSAGE: " + message);
        
        // process the message...
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        // DEBUG
        this.errorLogger().warning("WebSocketProcessor: onClose(): Disconnected: " + statusCode + " Reason: " + reason);
        
        // disconnect
        if (this.m_ws != null) {
            this.m_ws.destroy();
            this.m_ws = null;
        }
        
        // end our processing loop
        this.m_session = null;
        this.m_ws = null;
        this.m_running = false;
    }
}