/**
 * @file LoggerWebSocket.java
 * @brief pelion-bridge Logging WebSocket
 * @author Brian Daniels
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
package com.arm.pelion.bridge.loggerservlet;

import com.arm.pelion.bridge.core.ErrorLogger;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;


/**
 * Logger Web Socket implementation
 * 
 * @author Brian Daniels
 */
@WebSocket
public class LoggerWebSocket {
    public ArrayList<Session> m_sessions = new ArrayList<>();
    private ErrorLogger m_logger = null;
    
    public void setErrorLogger(ErrorLogger logger) {
        this.m_logger = logger;
    }
    
    @OnWebSocketConnect
    public void onConnect(Session session) throws IOException {
        this.m_sessions.add(session);
        LoggerTracker.getInstance().join(this);
        
        // DEBUG
        //this.m_logger.info("LoggerWebSocket: Connected to: " + session.getRemoteAddress());
    }

    @OnWebSocketClose
    public void onClose(Session session, int status, String reason) {
        LoggerTracker.getInstance().leave(this);
        this.m_sessions.remove(session);
        
        // DEBUG
        //this.m_logger.info("LoggerWebSocket: Disconnected from: " + session.getRemoteAddress());
    }

    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        this.m_logger.info("LoggerWebSocket: Exception caught: " + error.getMessage() + ". Session: " + session);
    }
}