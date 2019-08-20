/**
 * @file DeviceAttributeRetrievalDispatcher.java
 * @brief Device Attribute Retrieval Dispatcher the Pelion bridge
 * @author Doug Anson
 * @version 1.0
 * @see
 *
 * Copyright 2019. ARM Ltd. All rights reserved.
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
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.pelion.bridge.core.BaseClass;
import com.arm.pelion.bridge.core.Utils;
import com.google.api.client.util.Base64;
import java.util.Map;

/**
 * Device Attribute Retrieval Dispatcher the Pelion bridge
 * @author Doug Anson
 */
public class DeviceAttributeRetrievalDispatcher extends BaseClass implements AsyncResponseProcessor, Runnable {
    private static final int WAIT_TIME_MS = 5000;       // wait 5 seconds
    
    private PelionProcessor m_processor = null;
    private DeviceAttributeRetrievalDispatchManager m_manager = null;
    private String m_device_id = null;
    private String m_device_type = null;
    private String m_uri = null;
    private boolean m_running = false;
    private Map m_endpoint = null;
    private Map m_response = null;
    
    // default constructor
    public DeviceAttributeRetrievalDispatcher(DeviceAttributeRetrievalDispatchManager manager,PelionProcessor processor,Map endpoint,String device_id,String device_type,String uri) {
        super(processor.errorLogger(), processor.preferences());
        this.m_processor = processor;
        this.m_manager = manager;
        this.m_endpoint = endpoint;
        this.m_device_id = device_id;
        this.m_device_type = device_type;
        this.m_uri = uri;
    }
    
    @Override
    public boolean processAsyncResponse(Map response) {
        boolean ok = true;
        Object value = "none";
        
        try {
            // DEBUG
            this.errorLogger().info("PelionDeviceAttributeDispatcher(" + this.m_uri + "): processing AsyncResponse: " + response);

            // extract the value of the resource given by the URI...
            String b64_payload = (String)response.get("payload");
            
            // convert to clear string form
            String s = new String(Base64.decodeBase64(b64_payload));
            if (s != null && s.length() > 0) {
                value = s;
            }

            // DEBUG
            this.errorLogger().info("PelionDeviceAttributeDispatcher(" + this.m_uri + "): Value: " + value + " Payload: " + b64_payload);
        }
        catch (Exception ex) {
             // exception caught
             this.errorLogger().warning("PelionDeviceAttributeDispatcher(" + this.m_uri + "): Exception: " + ex.getMessage());
        }
       
        // notify the manager with the updated value...
        this.m_manager.updateAttributeForDevice(this,value);
        
        // we are done running
        this.m_running = false;
            
        // return our status
        return ok;
    }

    @Override
    public void run() {
        // Create the Device Attributes URL
        String url = this.m_processor.createCoAPURL(this.m_device_id, this.m_uri);

        // Dispatch and get the response (an AsyncId)
        String json_response = this.m_processor.httpsGet(url,"text/plain", this.m_processor.apiToken());
        int http_code = this.m_processor.getLastResponseCode();

        // add the URI to the ID for the response
        Map response = this.m_processor.orchestrator().getJSONParser().parseJson(json_response);
        response.put("uri",this.m_uri);
        json_response = this.m_processor.orchestrator().getJSONGenerator().generateJson(response);

        // DEBUG
        this.errorLogger().info("PelionDeviceAttributeDispatcher(" + this.m_uri + "): URL: " + url + " CODE: " + http_code + " RESPONSE: " + response);

        // record the response to get processed later
        if (json_response != null) {
            this.m_processor.orchestrator().recordAsyncResponse(json_response, url, this.m_endpoint, this);
        }
        
        // Sleep until done
        while(this.m_running == true) {
            Utils.waitForABit(this.errorLogger(), WAIT_TIME_MS);
        }
        
        // DEBUG
        this.errorLogger().info("PelionDeviceAttributeDispatcher(" + this.m_uri + "): task exited (OK)");
    }
}
