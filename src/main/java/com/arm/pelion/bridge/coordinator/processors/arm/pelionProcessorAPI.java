/**
 * @file  pelionProcessor.java
 * @brief Peer Processor for the Pelion (API variant)
 * @author Doug Anson
 * @version 1.0
 * @see
 *
 * Copyright 2015-2018. ARM Ltd. All rights reserved.
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
package com.arm.pelion.bridge.coordinator.processors.arm;

import com.arm.mbed.cloud.sdk.DeviceDirectory;
import com.arm.mbed.cloud.sdk.common.CallLogLevel;
import com.arm.mbed.cloud.sdk.common.ConnectionOptions;
import com.arm.mbed.cloud.sdk.common.MbedCloudException;
import com.arm.mbed.cloud.sdk.common.listing.ListResponse;
import com.arm.mbed.cloud.sdk.devicedirectory.model.Device;
import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.core.ApiResponse;
import com.arm.pelion.bridge.core.Processor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.arm.pelion.bridge.transport.HttpTransport;
import java.util.HashMap;
import java.util.List;
import com.arm.pelion.bridge.coordinator.processors.interfaces.pelionProcessorInterface;

/**
 * mbed Cloud Peer processor 
 *
 * @author Doug Anson
 */
public class pelionProcessorAPI extends Processor implements pelionProcessorInterface, AsyncResponseProcessor {
    private String m_api_token = null;
    private ConnectionOptions m_connect_options = null;
    private DeviceDirectory m_device_api = null;
    private String m_log_level = "pelion-bridge";
    
        // default endpoint type
    public static String DEFAULT_ENDPOINT_TYPE = "default";                    // default endpoint type
    
    private HttpTransport m_http = null;
    private String m_mds_host = null;
    private int m_mds_port = 0;
    private String m_mds_username = null;
    private String m_mds_password = null;
    private String m_content_type = null;
    private String m_default_mds_uri = null;
    private String m_mds_version = null;
    private String m_rest_version = "2";
    // defaulted endpoint type
    private String m_def_ep_type = DEFAULT_ENDPOINT_TYPE;
    
    // default constructor
    public pelionProcessorAPI(Orchestrator orchestrator, HttpTransport http) {
        super(orchestrator, null);
        this.m_http = http;
        
        this.m_mds_host = orchestrator.preferences().valueOf("mds_address");
        if (this.m_mds_host == null || this.m_mds_host.length() == 0) {
            this.m_mds_host = orchestrator.preferences().valueOf("api_endpoint_address");
        }
        this.m_mds_port = orchestrator.preferences().intValueOf("mds_port");
        this.m_mds_version = this.prefValue("mds_version");
        
        // API Key
        this.m_api_token = this.orchestrator().preferences().valueOf("mds_api_token");
        if (this.m_api_token == null || this.m_api_token.length() == 0) {
            // new key to use..
            this.m_api_token = this.orchestrator().preferences().valueOf("api_key");
        }
        // default device type in case we need it
        this.m_def_ep_type = orchestrator.preferences().valueOf("mds_def_ep_type");
        if (this.m_def_ep_type == null || this.m_def_ep_type.length() <= 0) {
            this.m_def_ep_type = DEFAULT_ENDPOINT_TYPE;
        }
        
        // Connection Options
        this.m_connect_options = new ConnectionOptions(this.m_api_token);
        
        // Logging level for the connection
        this.m_connect_options.setClientLogLevel(CallLogLevel.getLevel(this.m_log_level));
        
        // Create the device API instance
        this.m_device_api = new DeviceDirectory(this.m_connect_options);
        
        // initial device discovery
        this.initDeviceDiscovery();
    }

    @Override
    public ApiResponse processApiRequestOperation(String uri, String data, String options, String verb, int request_id, String api_key, String caller_id, String content_type) {
        ApiResponse response = new ApiResponse(this.orchestrator(),this.m_suffix,uri,data,options,verb,caller_id,content_type,request_id);
        
        // execute the API Request
        response.setReplyData(this.executeApiRequest(uri,data,options,verb,api_key,content_type));
        
        // set the http result code
        response.setHttpCode(this.getLastResponseCode());
        
        // return the response
        return response;
    }
    
    // execute an API request and return the response
    private String executeApiRequest(String uri,String data,String options,String verb,String api_key,String content_type) {
        String response = "";
        
        // execute if we have valid parameters
        if (uri != null && verb != null) {
            // create our API Request URL (blank version)
            String url = this.createBaseURL("") + uri + options;

            // DEBUG
            this.errorLogger().info("executeApiRequest(mbed Cloud): invoking API Request ContentType: " + content_type + " URL: " + url);

            // GET
            if (verb.equalsIgnoreCase("get")) {
                response = this.httpsGet(url, content_type, api_key);
            }   
            // PUT
            else if (verb.equalsIgnoreCase("put")) {
                response = this.httpsPut(url, data, content_type, api_key);
            }   
            // POST
            else if (verb.equalsIgnoreCase("post")) {
                response = this.httpsPost(url,data, content_type, api_key);
            }   
            // DELETE
            else if (verb.equalsIgnoreCase("delete")) {
                response = this.httpsDelete(url, data, api_key);
            } 
            else {
                // verb is unknown - should never get called as verb is already sanitized...
                this.errorLogger().warning("executeApiRequest(mbed Cloud): ERROR: HTTP verb[" + verb + "] ContentType: [" + content_type + "] is UNKNOWN. Unable to execute request...");
                return this.createJSONMessage("api_execute_status","invalid coap verb");
            }
        }
        else {
            // invalid parameters
            this.errorLogger().warning("executeApiRequest(mbed Cloud): ERROR: invalid parameters in API request. Unable to execute request...");
            return this.createJSONMessage("api_execute_status","iinvalid api parameters");
        }
        
        // return a sanitized response
        String sanitized = this.sanitizeResponse(response);
        
        // DEBUG
        this.errorLogger().info("executeApiRequest(mbed Cloud):Sanitized API Response: " + sanitized);
        
        // return the sanitized response
        return sanitized;
    }
    
    // sanitize the API response
    private String sanitizeResponse(String response) {
        if (response == null || response.length() <= 0) {
            // DEBUG
            this.errorLogger().info("APIResponse: Response was EMPTY (OK).");
            
            // empty response
            return this.createJSONMessage("api_execute_status","empty response");
        }
        else {            
            // response should be parsable JSON
            Map parsed = this.tryJSONParse(response);
            if (parsed != null && parsed.isEmpty() == false) {
                // DEBUG
                this.errorLogger().info("APIResponse: Parsable RESPONSE: " + response);
                
                // parsable! just return the (patched) JSON string
                return response;
            }
            else {
                // DEBUG
                this.errorLogger().warning("APIResponse: Response parsing FAILED");
                
                // unparsable JSON... error
                return this.createJSONMessage("api_execute_status","unparsable json");
            }
        }
    }

    @Override
    public void processNotificationMessage(HttpServletRequest request, HttpServletResponse response) {
        // XXX
    }

    @Override
    public String processEndpointResourceOperation(String verb, String ep_name, String uri, String value, String options) {
        // XXX
        return null;
    }

    @Override
    public void processDeviceDeletions(String[] endpoints) {
        // not enabled by default
    }

    @Override
    public void processDeregistrations(String[] endpoints) {
        // not enabled by default
    }

    @Override
    public void processRegistrationsExpired(String[] endpoints) {
        // not enabled by default
    }

    @Override
    public boolean setWebhook() {
        // not used
        return false;    
    }

    @Override
    public boolean resetWebhook() {
        // not used
        return false;
    }

    @Override
    public void pullDeviceMetadata(Map endpoint, AsyncResponseProcessor processor) {
        // XXX
    }

    @Override
    public boolean deviceRemovedOnDeRegistration() {
        // disable by default
        return false;    
    }

    @Override
    public void initDeviceDiscovery() {
        try {
            // get the list of devices
            ListResponse<Device> devices = this.m_device_api.listDevices(null);
            if (devices != null && devices.getTotalCount() > 0) {
                // setup the device shadow
                this.setupExistingDeviceShadows(devices.getData());
            }
            else if (devices != null) {
                // no initial devices
                this.errorLogger().info("mbedCloudProcessor: no devices registered in Pelion (OK).");
            }
            else {
                // no device instance
                this.errorLogger().warning("mbedCloudProcessor: null device list... continuing...");
            }
        } 
        catch (MbedCloudException ex) {
            this.errorLogger().critical("mbedCloudProcessor: Exception in device discovery: " + ex.getMessage(),ex);
        }
    }
    
    // setup initial Device Shadows (mbed Cloud only...)
    private void setupExistingDeviceShadows(List<Device> devices) {
        // loop through each device, get resource descriptions...
        HashMap<String,Object> endpoint = new HashMap<>();
        for(int i=0;devices != null && i<devices.size();++i) {
            Device device = (Device)devices.get(i);
            
            // DEBUG
            this.errorLogger().info("setupExistingDeviceShadows: DEVICE: " + device);
            
            /*
            // sanitize the endpoint type
            device.put("endpoint_type",this.sanitizeEndpointType((String)device.get("endpoint_type")));

            // copy over the relevant portions
            endpoint.put("ep", (String)device.get("id"));
            endpoint.put("ept",(String)device.get("endpoint_type"));

            // DEBUG
            this.errorLogger().warning("mbedCloudProcessor(BOOT): discovered mbed Cloud device ID: " + (String)device.get("id") + " Type: " + (String)device.get("endpoint_type"));

            // now, query mbed Cloud again for each device and get its resources
            List resources = this.discoverDeviceResources((String)device.get("id"));

            // For now, we simply add to each resource JSON, a "path" that mimics the "uri" element... we need to use "uri" once done with Connector
            for(int j=0;resources != null && j<resources.size();++j) {
                Map resource = (Map)resources.get(j);
                resource.put("path", (String)resource.get("uri"));

                // auto-subscribe to observable resources... if enabled.
                this.orchestrator().subscribeToEndpointResource((String) endpoint.get("ep"), (String) resource.get("path"), false);

                // SYNC: here we dont have to worry about Sync options - we simply dispatch the subscription to mbed Cloud and setup for it...
                this.orchestrator().removeSubscription((String) endpoint.get("ep"), (String) endpoint.get("ept"), (String) resource.get("path"));
                this.orchestrator().addSubscription((String) endpoint.get("ep"), (String) endpoint.get("ept"), (String) resource.get("path"), this.isObservableResource(resource));
                
                // SYNC: save ept for ep in the peers...
                this.orchestrator().setEndpointTypeFromEndpointName((String) endpoint.get("ep"), (String) endpoint.get("ept"));
            }

            // put the resource list into our payload...
            endpoint.put("resources",resources);

            // process as new device registration...
            this.orchestrator().completeNewDeviceRegistration(endpoint);
            */
        }
    }
    
    // get the observability of a given resource
    private boolean isObservableResource(Map resource) {
        String obs_str = (String) resource.get("obs");
        return (obs_str != null && obs_str.equalsIgnoreCase("true"));
    }
    
    // sanitize the endpoint type
    private String sanitizeEndpointType(String ept) {
        if (ept == null || ept.length() == 0) {
            return DEFAULT_ENDPOINT_TYPE;
        }
        return ept;
    }
    
    // add REST version information
    private String connectorVersion() {
        return "/v" + this.m_rest_version;
    }

    // create the base URL for mbed Cloud operations
    private String createBaseURL() {
        return this.createBaseURL(this.connectorVersion());
    }
    
    // create the base URL for mbed Cloud operations
    private String createBaseURL(String version) {
        return this.m_default_mds_uri + this.m_mds_host + ":" + this.m_mds_port + version;
    }
    
    // get the last response code
    public int getLastResponseCode() {
        return this.m_http.getLastResponseCode();
    }

    // invoke HTTP GET request (SSL)
    private String httpsGet(String url) {
        return this.httpsGet(url,this.m_content_type,this.m_api_token);
    }
    
    // invoke peristent HTTPS Get
    public String persistentHTTPSGet(String url) {
        return this.persistentHTTPSGet(url, this.m_content_type);
    }

    // invoke peristent HTTPS Get
    private String persistentHTTPSGet(String url, String content_type) {
        String response = this.m_http.httpsPersistentGetApiTokenAuth(url, this.m_api_token, null, content_type);
        this.errorLogger().info("persistentHTTPSGet: response: " + this.m_http.getLastResponseCode());
        return response;
    }

    // invoke HTTP GET request (SSL)
    private String httpsGet(String url, String content_type,String api_key) {
        String response = this.m_http.httpsGetApiTokenAuth(url, api_key, null, content_type);
        this.errorLogger().info("httpsGet: response: " + this.m_http.getLastResponseCode());
        return response;
    }

    // invoke HTTP PUT request (SSL)
    private String httpsPut(String url) {
        return this.httpsPut(url, null);
    }

    // invoke HTTP PUT request (SSL)
    private String httpsPut(String url, String data) {
        return this.httpsPut(url, data, this.m_content_type, this.m_api_token);
    }

    // invoke HTTP PUT request (SSL)
    private String httpsPut(String url, String data, String content_type, String api_key) {
        String response = this.m_http.httpsPutApiTokenAuth(url, api_key, data, content_type);
        this.errorLogger().info("httpsPut: response: " + this.m_http.getLastResponseCode());
        return response;
    }

    // invoke HTTP POST request (SSL)
    private String httpsPost(String url, String data) {
        return this.httpsPost(url, data, this.m_content_type, this.m_api_token);
    }
    
    // invoke HTTP POST request (SSL)
    private String httpsPost(String url, String data, String content_type, String api_key) {
        String response = this.m_http.httpsPostApiTokenAuth(url, api_key, data, content_type);
        this.errorLogger().info("httpsPost: response: " + this.m_http.getLastResponseCode());
        return response;
    }

    // invoke HTTP DELETE request
    private String httpsDelete(String url) {
        return this.httpsDelete(url, this.m_content_type, this.m_api_token);
    }

    // invoke HTTP DELETE request
    private String httpsDelete(String url, String content_type, String api_key) {
        String response = this.m_http.httpsDeleteApiTokenAuth(url, api_key, null, content_type);
        this.errorLogger().info("httpDelete: response: " + this.m_http.getLastResponseCode());
        return response;
    }

    @Override
    public boolean processAsyncResponse(Map response) {
        // XXX
        return false;
    }
}
