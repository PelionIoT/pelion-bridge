/**
 * @file DeviceManagerToPeerProcessorInterface.java
 * @brief PeerProcessor interface from within an associated DeviceManager implementation
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
package com.arm.pelion.bridge.coordinator.processors.interfaces;

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.core.ErrorLogger;
import com.arm.pelion.bridge.preferences.PreferenceManager;

/**
 * PeerProcessor interface from within an associated DeviceManager implementation
 * @author Doug Anson
 */
public interface DeviceManagerToPeerProcessorInterface {
    // get the error logger
    public ErrorLogger errorLogger();
    
    // get the Preferences Manager
    public PreferenceManager preferences();
    
    // get the orchestrator
    public Orchestrator orchestrator();
    
    // set the endpoint type for the endpoint name
    public void setEndpointTypeFromEndpointName(String ep_name, String ep_type);
    
    // HTTPS accessor methods
    public String httpsGet(String url);
    public String httpsPut(String url, String payload);
    public String httpsPost(String url, String payload);
    public String httpsDelete(String url, String etag, String payload);
}
