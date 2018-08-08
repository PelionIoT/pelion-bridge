/**
 * @file    ConsoleManager.java
 * @brief console manager for the connector bridge (unused)
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
package com.arm.connector.bridge.console;

import com.arm.connector.bridge.coordinator.Orchestrator;
import com.arm.connector.bridge.core.BaseClass;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author danson
 */
public class ConsoleManager extends BaseClass {

    private Orchestrator m_orchestrator = null;

    public ConsoleManager(Orchestrator orchestrator) {
        super(orchestrator.errorLogger(), orchestrator.preferences());
        this.m_orchestrator = orchestrator;
    }

    // process the console request for the connector bridge
    @SuppressWarnings("empty-statement")
    public void processConsole(HttpServletRequest request, HttpServletResponse response) {
        // not implemented 
        ;
    }
}
