/**
 * @file DeviceManager.java
 * @brief Device manager base class
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

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.core.BaseClass;
import com.arm.pelion.bridge.core.ErrorLogger;
import com.arm.pelion.bridge.data.SerializableHashMapOfHashMaps;
import com.arm.pelion.bridge.preferences.PreferenceManager;
import com.arm.pelion.bridge.transport.HttpTransport;

/**
 * DeviceManager - base class for device managers
 * @author Doug Anson
 */
public class DeviceManager extends BaseClass {
    private static final int DEFAULT_NUM_RETRIES = 10;                              // 10 httpsGet() attempts before giving up... typically 10 is fine...
    private static final int DEFAULT_RETRY_WAIT_MS = 1000;                          // typically wait about 1 second, then retry the https "get" call...
    protected HttpTransport m_http = null;
    protected Orchestrator m_orchestrator = null;
    protected String m_suffix = null;
    
    protected SerializableHashMapOfHashMaps m_endpoint_details = null;
    protected int m_num_retries = DEFAULT_NUM_RETRIES;
    protected int m_get_retry_wait_ms = DEFAULT_RETRY_WAIT_MS;  
    
    // new constructor
    public DeviceManager(Orchestrator orchestrator, HttpTransport http, String suffix) {
        this(orchestrator.errorLogger(),orchestrator.preferences(),suffix,http,orchestrator);
    }
    
    // default constructor
    public DeviceManager(ErrorLogger error_logger, PreferenceManager preference_manager,String suffix, HttpTransport http, Orchestrator orchestrator) {
        super(error_logger, preference_manager);
        
        // HTTP and suffix support
        this.m_http = http;
        this.m_suffix = suffix;
        this.m_orchestrator = orchestrator;
        
        // initialize the endpoint keys map
        this.m_endpoint_details = new SerializableHashMapOfHashMaps(orchestrator,"ENDPOINT_DETAILS");
        
        // Number of https "get" retries
        this.m_num_retries = this.preferences().intValueOf("http_get_num_retries",this.m_suffix);
        if (this.m_num_retries <= 0) {
            this.m_num_retries = DEFAULT_NUM_RETRIES;
        }
        
        // Wait time in ms between https "get" retries
        this.m_get_retry_wait_ms = this.preferences().intValueOf("http_get_retry_wait_ms",this.m_suffix);
        if (this.m_get_retry_wait_ms <= 0) {
            this.m_get_retry_wait_ms = DEFAULT_RETRY_WAIT_MS;
        }
    }
}