/**
 * @file SAMPLEProcessor.java
 * @brief SAMPLE Peer Processor
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
package com.arm.pelion.bridge.coordinator.processors.SAMPLE;

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.arm.GenericMQTTProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.GenericSender;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;
import com.arm.pelion.bridge.coordinator.processors.interfaces.ReconnectionInterface;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.transport.HttpTransport;
import com.arm.pelion.bridge.transport.MQTTTransport;
import com.arm.pelion.bridge.transport.Transport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.fusesource.mqtt.client.Topic;

/**
 * SAMPLE Processor: This can be a MQTT-based processor or a HTTP-based processor... its derived from GenericMQTTProcessor.
 *
 * @author Doug Anson
 */
public class SAMPLEProcessor extends GenericMQTTProcessor implements PeerProcessorInterface, GenericSender,Transport.ReceiveListener, ReconnectionInterface, AsyncResponseProcessor {
    private boolean m_configured = false;
    private HttpTransport m_http = null;
    private SAMPLEDeviceManager m_device_manager = null;
    
    // SAMPLE Auth Token
    private String m_auth_token = null;
    
    // Defaulted device type prefix
    private String m_device_type_prefix = null;
    
    // Defaulted content type
    private String m_content_type = null;
    
    // constructor
    public SAMPLEProcessor(Orchestrator manager, MQTTTransport mqtt, HttpTransport http) {
        this(manager, mqtt, http, null);
    }

    // constructor
    public SAMPLEProcessor(Orchestrator manager, MQTTTransport mqtt, HttpTransport http, String suffix) {
        super(manager, mqtt, suffix, http);
        this.m_http = http;
        
        // We can pass in a single MQTTTransport connection (See SAMPLEPeerProcessorFactory) or manage one MQTTTransport per device shadow as needed by SAMPLE
        
        // init the added HTTP headers required
        http.addHeader("Cache-Control","no-cache");
        
        // create the device manager
        this.m_device_manager = new SAMPLEDeviceManager(this,http,suffix);
        
        // device type prefix default
        this.m_device_type_prefix = "mbed";
        
        // HTTP content type for SAMPLE
        this.m_content_type = "application/json";
        
        // get the SAMPLE auth token 
        this.m_auth_token = this.prefValue("SAMPLE_auth_token");
        if (this.m_auth_token != null && this.m_auth_token.contains("Goes_Here") == true) {
            // unconfigured
            this.m_configured = false;
        }
        else {
            // configured
            this.m_configured = true;
        }

        // announce the processor
        if (this.m_configured) {
            // SAMPLE 3rd Party peer PeerProcessor Announce
            this.errorLogger().warning("SAMPLE Processor ENABLED.");
        }
        else {
            // SAMPLE 3rd Party peer PeerProcessor Announce (UNCONFIGURED)
            this.errorLogger().warning("SAMPLE Processor ENABLED (UNCONFIGURED).");
        }
    }
    
    // get the resource URIs as a list
    private List<String> getResourceURIs(Map endpoint) {
        ArrayList<String> uri_list = new ArrayList<>();
        
        // loop through the endpoints resource list and get the URIs
        List resources = (List)endpoint.get("resources"); 
        for(int i=0;resources != null && i<resources.size();++i) {
            Map entry = (Map)resources.get(i);
            if (entry != null) {
                String uri = (String)entry.get("uri");
                if (Utils.isCompleteURI(uri) == true && Utils.isHandledURI(uri) == false) {
                    uri_list.add(uri);
                }
            }
        }
        
        // DEBUG
        this.errorLogger().info("SAMPLE: Resource URIs: " + uri_list);
        
        // return the array of resource URIs
        return uri_list;
    }
    
    // initialize any SAMPLE listeners
    @Override
    public void initListener() {
        // not used
    }

    // stop our SAMPLE 3rd Party peer listeners
    @Override
    public void stopListener() {
        // not used
    }
    
    // Create the authentication hash
    @Override
    public String createAuthenticationHash() {
        // just create a hash of something unique to the peer side... 
        String peer_secret = this.m_auth_token;
        return Utils.createHash(peer_secret);
    }
    
    // GenericSender Implementation: send a message
    @Override
    public void sendMessage(String to, String message) {
        if (this.m_configured) {
            // DEBUG
            this.errorLogger().warning("SAMPLE(sendMessage): TO: " + to + " MESSAGE: " + message);
            
        }
        else {
            // not configured
            this.errorLogger().warning("SAMPLE(sendMessage): SAMPLE Auth Token is UNCONFIGURED. Please configure and restart the bridge (OK).");
        }
    }
    
    // process a device deletion
    @Override
    public String[] processDeviceDeletions(Map parsed) {
        String[] devices = this.parseDeviceDeletionsBody(parsed);
        for(int i=0;devices != null && i<devices.length;++i) {
            String device_type_id = this.createDeviceTypeID(devices[i],this.m_device_type_prefix);
            this.m_device_manager.deleteDevice(devices[i]);
            this.m_device_manager.deleteDeviceType(device_type_id);
        }
        return super.processDeviceDeletions(parsed);
    }
    
    // process a deregistration
    @Override
    public String[] processDeregistrations(Map parsed) {
        String[] devices = this.parseDeRegistrationBody(parsed);
        for(int i=0;devices != null && i<devices.length;++i) {
            String device_type_id = this.createDeviceTypeID(devices[i],this.m_device_type_prefix);
            this.m_device_manager.deleteDevice(devices[i]);
            this.m_device_manager.deleteDeviceType(device_type_id);
        }
        return super.processDeregistrations(parsed);
    }

    // complete new registration
    @Override
    public void completeNewDeviceRegistration(Map device) {
        if (this.m_configured) {
            if (this.m_device_manager != null) {
                // create the device twin
                boolean ok = this.m_device_manager.createDevice(device,this.m_device_type_prefix);
                if (ok) {
                    // add our device type
                    this.setEndpointTypeFromEndpointName((String)device.get("ep"),(String)device.get("ept"));
                    this.errorLogger().warning("SAMPLE(completeNewDeviceRegistration): Device Shadow: " + device.get("ep") + " creation SUCCESS");
                }
                else {
                    this.errorLogger().warning("SAMPLE(completeNewDeviceRegistration): Device Shadow: " + device.get("ep") + " creation FAILURE");
                }
            }
            else {
                this.errorLogger().warning("SAMPLE(completeNewDeviceRegistration): DeviceManager is NULL. Shadow Device creation FAILURE: " + device);
            }
        }
        else {
            // not configured
            this.errorLogger().warning("SAMPLE(completeNewDeviceRegistration): SAMPLE Auth Token is UNCONFIGURED. Please configure and restart the bridge (OK).");
        }
    }
    
    @Override
    public boolean startReconnection(String ep_name, String ep_type, Topic[] topics) {
        // XXX 
        // manage MQTT reconnection requests in case we get disconnected from SAMPLE
        return true;
    }
    
    @Override
    public boolean processAsyncResponse(Map response) {
        // XXX 
        // process Async Responses 
        this.errorLogger().warning("SAMPLE(processAsyncResponse): AsyncResponse: " + response);
        return true;
    }
    
    // XXX create device URL
    public String buildDeviceURL() {
        return "https://foo.bar";
    }
    
    // XXX create device URL
    public String buildDeviceURL(String ep) {
        return "https://foo.bar";
    }
    
    // XXX create device type URL
    public String buildDeviceTypeURL() {
        return "https://foo.bar";
    }
    
    // XXX create device type URL
    public String buildDeviceTypeURL(String ept) {
        return "https://foo.bar";
    }
    
    // get the auth token
    private String getAuthToken() {
        return this.m_auth_token;
    }
    
    // get the the value from one of two keys
    private String valueFromValidKey(Map data, String key1, String key2) {
        if (data != null) {
            if (data.get(key1) != null) {
                return (String)data.get(key1);
            }
            if (data.get(key2) != null) {
                return (String)data.get(key2);
            }
        }
        return null;
    }
    
    // create the device type ID
    private String createDeviceTypeID(String ep,String prefix) {
        return this.getEndpointTypeFromEndpointName(ep);
    }
    
    // dispatch a http GET
    public String httpGet(String url) {
        return this.m_http.httpsGetApiTokenAuth(url, this.m_auth_token, null, m_content_type);
    }
    
    // dispatch a http PUT
    public String httpPut(String url,String data) {
        return this.m_http.httpsPutApiTokenAuth(url, this.m_auth_token, data, m_content_type);
    }
    
    // dispatch a http POST
    public String httpPost(String url,String data) {
        return this.m_http.httpsPostApiTokenAuth(url, this.m_auth_token, data, m_content_type);
    }
    
    // dispatch a http DELETE
    public String httpDelete(String url) {
        return this.m_http.httpsDeleteApiTokenAuth(url, this.m_auth_token, null, m_content_type);
    }
}
