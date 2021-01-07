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
import java.util.ArrayList;

/**
 * Endpoint Type Management for pelion-bridge
 * @author Doug Anson
 */
public class EndpointTypeManager extends BaseClass {
    private Orchestrator m_orchestrator = null;
    
    // endpoint type hashmap
    private SerializableHashMap m_endpoint_type_list = null;
    
    // banned list
    private ArrayList<String> m_banned_devices = null;
    
    // constructor
    public EndpointTypeManager(Orchestrator orchestrator) {
        super(orchestrator.errorLogger(), orchestrator.preferences());
        this.m_orchestrator = orchestrator;
        
        // allocate the banned devices list
        this.m_banned_devices = new ArrayList<>();
        
        // create endpoint name/endpoint type map
        this.m_endpoint_type_list = new SerializableHashMap(orchestrator,"ENDPOINT_TYPE_LIST");
    }

    // get the endpoint type from the endpoint name (if cached...)
    public String getEndpointTypeFromEndpointName(String ep) {
        String ept = (String)this.m_endpoint_type_list.get(ep);
        
        // DEBUG
        this.errorLogger().info("EndpointTypeManager: EP: " + ep + " EPT: " + ept);
        
        // return the Endpoint Type
        return ept;
    }    

    // set the endpoint type from a given endpoint name
    public synchronized void setEndpointTypeFromEndpointName(String endpoint, String type) {
        if (type != null && type.length() > 0) {
            if (this.isBannedDevice(endpoint) == false) {
                // DEBUG
                this.errorLogger().warning("EndpointTypeManager: Setting EPT: " + type + " for EP: " + endpoint);
                
                // set the endpoint type 
                this.m_endpoint_type_list.put(endpoint,type);

                // DEBUG
                this.errorLogger().info("EndpointTypeManager: Count(Set): " + this.size());
            }
            else {
                // device is banned... so dont add it
                this.errorLogger().info("EndpointTypeManager: EP: " + endpoint + " is banned. Ignoring (OK)");
            }
        }
    }
    
    // remove the endpoint type from the endpoint name
    public synchronized void removeEndpointTypeFromEndpointName(String endpoint) {
        if (endpoint != null && endpoint.length() > 0) {
            // DEBUG
            this.errorLogger().info("EndpointTypeManager: Removing Type for EP: " + endpoint);

            this.m_endpoint_type_list.remove(endpoint);

            // DEBUG
            this.errorLogger().info("EndpointTypeManager: Count(Remove): " + this.size());
        }
    }
    
    // get the count of the map
    public synchronized int size() {
        // DEBUG
        this.errorLogger().info("EndpointTypeManager: Map(" + this.m_endpoint_type_list.map().size() + "): " + this.m_endpoint_type_list.map());
        return this.m_endpoint_type_list.map().size();
    }
    
    // is the given device banned?
    private synchronized boolean isBannedDevice(String endpoint) {
        for(int i=0;endpoint != null && i<this.m_banned_devices.size();++i) {
            if (endpoint.equalsIgnoreCase(this.m_banned_devices.get(i))) {
                return true;
            }
        }
        return false;
    }
    
    // permanently ban device (optional)
    public synchronized void banDevice(String endpoint) {
        if (this.isBannedDevice(endpoint) == false) {
            this.m_banned_devices.add(endpoint);
        }
        this.m_endpoint_type_list.remove(endpoint);
    }
}