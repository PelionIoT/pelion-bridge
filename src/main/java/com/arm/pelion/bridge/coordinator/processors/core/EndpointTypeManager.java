/**
 * @file EndpointTypeManager.java
 * @brief Endpoint Type Manager for pelion-bridge
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
package com.arm.pelion.bridge.coordinator.processors.core;

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.core.BaseClass;
import com.arm.pelion.bridge.data.SerializableHashMap;

/**
 * Endpoint Type Management for pelion-bridge
 * @author Doug Anson
 */
public class EndpointTypeManager extends BaseClass {
    private Orchestrator m_orchestrator = null;
    
    // endpoint type hashmap
    private SerializableHashMap m_endpoint_type_list = null;
    
    // constructor
    public EndpointTypeManager(Orchestrator orchestrator) {
        super(orchestrator.errorLogger(), orchestrator.preferences());
        this.m_orchestrator = orchestrator;
        
        // create endpoint name/endpoint type map
        this.m_endpoint_type_list = new SerializableHashMap(orchestrator,"BSM_ENDPOINT_TYPE_LIST");
    }
    

    // get the endpoint type from the endpoint name (if cached...)
    public String endpointTypeFromEndpointName(String ep) {
        return (String)this.m_endpoint_type_list.get(ep);
    }    

    // set the endpoint type from a given endpoint name
    public void setEndpointTypeFromEndpointName(String endpoint, String type) {
        this.m_endpoint_type_list.put(endpoint,type);
    }
}
