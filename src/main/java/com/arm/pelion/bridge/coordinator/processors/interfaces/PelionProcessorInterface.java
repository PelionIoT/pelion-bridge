/**
 * @file PelionProcessorInterface.java
 * @brief Pelion processor interface
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

import com.arm.pelion.bridge.core.ApiResponse;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This interface defines the exposed methods of the Pelion processor
 *
 * @author Doug Anson
 */
public interface PelionProcessorInterface {
    // process an API request 
    public ApiResponse processApiRequestOperation(String uri,String data,String options,String verb,int request_id,String api_key,String caller_id,String content_type);
    
    // process observations/notifications incoming messages from pelion
    public void processNotificationMessage(HttpServletRequest request, HttpServletResponse response);

    // perform endpoint get/put/post/delete operations on endpoint resources
    public String processEndpointResourceOperation(String verb, String ep_name, String uri, String value, String options);
    
    // process endpoint registrations-expired from device server
    public void processRegistrationsExpired(String[] endpoints);

    // Device Metadata extraction
    public void pullDeviceMetadata(Map endpoint, AsyncResponseProcessor processor);

    // device removal on deregistration?
    public boolean deviceRemovedOnDeRegistration();
    
    // process endpoint deletions from device server
    public void processDeviceDeletions(String[] endpoints);
    
    // process endpoint de-registrations from device server
    public void processDeregistrations(String[] endpoints);
    
    // initial device discovery on startup
    public void initDeviceDiscovery();
    
    // Webhook management
    public boolean setWebhook();
    public boolean resetWebhook();
    public void removeWebhook();
}
