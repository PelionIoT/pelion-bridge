/**
 * @file Manager.java
 * @brief Primary Servlet Manager for pelion-bridge
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
package com.arm.pelion.bridge.servlet;

import com.arm.pelion.bridge.core.BridgeMain;
import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.core.ErrorLogger;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.preferences.PreferenceManager;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Primary Servlet Manager for pelion-bridge
 *
 * @author Doug Anson
 */
public final class Manager {
    // MOVE THESE INTO THE BUILD ENV
    public static final String LOG_TAG = "Pelion-Bridge";               // Log Tag
    public static final String BRIDGE_VERSION_STR = "1.0.0";            // our version (need to tie to build...)
    
    private Orchestrator m_orchestrator = null;
    private HttpServlet m_servlet = null;
    private static volatile Manager m_manager = null;
    private ErrorLogger m_error_logger = null;
    private PreferenceManager m_preference_manager = null;
    
    private String m_event_notification_path = null;
    private BridgeMain m_main = null;

    // instance factory
    public static Manager getInstance(HttpServlet servlet,ErrorLogger error_logger,PreferenceManager preferences) {
        if (Manager.m_manager == null) {
            Manager.m_manager = new Manager(error_logger,preferences);
        }
        Manager.m_manager.setServlet(servlet);
        return Manager.m_manager;
    }

    // default constructor
    public Manager(ErrorLogger error_logger,PreferenceManager preferences) {
        // save the error handler
        this.m_error_logger = error_logger;
        this.m_preference_manager = preferences;
        this.m_main = null;
        
        // announce our self
        this.errorLogger().info(LOG_TAG + ": Date: " + Utils.dateToString(Utils.now()) + ". Bridge version: v" + BRIDGE_VERSION_STR);
        
        // create the orchestrator
        this.m_orchestrator = new Orchestrator(this.m_error_logger, this.m_preference_manager);
        
        // bind to this manager
        this.m_orchestrator.setManager(this);

        // configure the error logger logging level
        this.m_error_logger.configureLoggingLevel(this.m_preference_manager);

        // Event notification path...
        this.m_event_notification_path = this.m_preference_manager.valueOf("mds_gw_events_path");
    }
    
    // set the server
    public void setBridgeMain(BridgeMain main) {
        this.m_main = main;
    }
    
    // start statistics gathering
    public void startStatisticsMonitoring() {
        this.m_orchestrator.startStatisticsMonitoring();
    }
    
    // RESET
    public void reset() {
        if (this.m_main != null) {
            // have the server reset
            this.m_main.restart();
        }
        else {
            // ERROR
            this.errorLogger().critical("Manager: UNABLE to RESTART (null BridgeMain)");
        }
    }

    public void processNotification(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        this.m_orchestrator.processIncomingDeviceServerMessage(request, response);
    }
    
    public void initListeners() {
        this.m_orchestrator.initPeerListener();
    }
    
    public void stopListeners() {
        this.m_orchestrator.stopPeerListener();
    }

    public void initWebhooks() {
        this.m_orchestrator.initializeDeviceServerWebhook();
    }

    public void resetNotifications() {
        this.m_orchestrator.resetDeviceServerWebhook();
    }
    
    public int getActiveThreadCount() {
        return this.m_main.getActiveThreadCount();
    }

    private void setServlet(HttpServlet servlet) {
        this.m_servlet = servlet;
    }

    private ErrorLogger errorLogger() {
        return this.m_error_logger;
    }
}
