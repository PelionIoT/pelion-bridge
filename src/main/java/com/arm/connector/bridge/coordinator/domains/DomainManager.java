/**
 * @file    DomainManager.java
 * @brief mDS domain checker manager
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
package com.arm.connector.bridge.coordinator.domains;

import com.arm.connector.bridge.coordinator.Orchestrator;
import com.arm.connector.bridge.core.ErrorLogger;
import com.arm.connector.bridge.preferences.PreferenceManager;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Manager managing the addition of new mDS domains being added to mDS
 *
 * @author Doug Anson
 */
public class DomainManager {

    private String m_domain = null;
    private Orchestrator m_orchestrator = null;
    private ErrorLogger m_error_logger = null;
    private PreferenceManager m_preference_manager = null;

    // constructor
    public DomainManager(ErrorLogger error_logger, PreferenceManager preference_manager, String domain) {
        this.m_domain = domain;
        this.m_error_logger = error_logger;
        this.m_preference_manager = preference_manager;
        this.m_orchestrator = new Orchestrator(error_logger, preference_manager, domain);
    }

    public void processConsole(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        this.m_orchestrator.processConsoleEvent(request, response);
    }

    public void processNotification(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        this.m_orchestrator.processIncomingDeviceServerMessage(request, response);
    }

    // get the orchestrator
    public Orchestrator getOrchestrator() {
        return this.m_orchestrator;
    }

    // get the domain name...
    public String domain() {
        return this.m_domain;
    }
}
