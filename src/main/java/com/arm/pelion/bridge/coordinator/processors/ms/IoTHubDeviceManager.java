/**
 * @file IoTHubDeviceManager.java
 * @brief MS IoTHub Device Manager for the MS IoTHub Peer Processor
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
package com.arm.pelion.bridge.coordinator.processors.ms;

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.core.DeviceManager;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.data.SerializableHashMap;
import com.arm.pelion.bridge.transport.HttpTransport;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * This class defines the required methods to manage MS IoTHub devices
 *
 * @author Doug Anson
 */
public class IoTHubDeviceManager extends DeviceManager {
    private String m_device_id_url_template = null;
    private String m_api_version = null;
    private String m_iot_event_hub_name = null;
    private String m_iot_event_hub_add_device_json = null;
    private String m_iot_event_hub_sas_token = null;
    private String m_iot_event_hub_auth_qualifier = "SharedAccessSignature";
    private IoTHubMQTTProcessor m_processor = null;

    // IoTHub Device ID prefixing...
    private boolean m_iot_event_hub_enable_device_id_prefix = false;
    private String m_iot_event_hub_device_id_prefix = null;

    // constructor
    public IoTHubDeviceManager(HttpTransport http, IoTHubMQTTProcessor processor, String hub_name, String sas_token) {
        this(null, http, processor, hub_name, sas_token);
    }

    // constructor
    public IoTHubDeviceManager(String suffix, HttpTransport http, IoTHubMQTTProcessor processor, String hub_name,String sas_token) {
        super(processor.errorLogger(), processor.preferences(),suffix,http,processor.orchestrator());
        this.m_processor = processor;

        // IoTHub Name
        this.m_iot_event_hub_name = hub_name;

        // IoTHub REST API Version
        this.m_api_version = this.preferences().valueOf("iot_event_hub_api_version", this.m_suffix);

        // IoTHub DeviceID REST URL Template
        this.m_device_id_url_template = this.preferences().valueOf("iot_event_hub_device_id_url", this.m_suffix).replace("__IOT_EVENT_HUB__", this.m_iot_event_hub_name).replace("__API_VERSION__", this.m_api_version);

        // Add device JSON template
        this.m_iot_event_hub_add_device_json = this.preferences().valueOf("iot_event_hub_add_device_json", this.m_suffix);

        // IoTHub SAS Token (take out the qualifier if present...)
        this.m_iot_event_hub_sas_token = sas_token.replace("SharedAccessSignature ", "").trim();

        // Enable prefixing of mbed Cloud names for IoTHub
        this.m_iot_event_hub_enable_device_id_prefix = this.prefBoolValue("iot_event_hub_enable_device_id_prefix", this.m_suffix);
        this.m_iot_event_hub_device_id_prefix = null;

        // If prefixing is enabled, setup the details...
        if (this.m_iot_event_hub_enable_device_id_prefix == true) {
            this.m_iot_event_hub_device_id_prefix = this.preferences().valueOf("iot_event_hub_device_id_prefix", this.m_suffix);
            if (this.m_iot_event_hub_device_id_prefix != null) {
                this.m_iot_event_hub_device_id_prefix += "-";
            }
        }
    }

    // IoTHub DeviceID Prefix enabler
    private String addDeviceIDPrefix(String ep_name) {
        String iothub_ep_name = ep_name;
        if (this.m_iot_event_hub_device_id_prefix != null && ep_name != null) {
            if (ep_name.contains(this.m_iot_event_hub_device_id_prefix) == false) {
                iothub_ep_name = this.m_iot_event_hub_device_id_prefix + ep_name;
            }
        }

        // DEBUG
        //this.errorLogger().info("addDeviceIDPrefix: ep_name: " + ep_name + " --> iothub_ep_name: " + iothub_ep_name);
        return iothub_ep_name;
    }
    
    // httpsGet the orchestrator
    private Orchestrator orchestrator() {
        return this.m_orchestrator;
    }

    // process new device registration
    public boolean registerNewDevice(Map message) {
        boolean status = false;

        // httpsGet the device details
        String ep_type = Utils.valueFromValidKey(message, "endpoint_type", "ept");
        String ep_name = Utils.valueFromValidKey(message, "id", "ep");

        // see if we already have a device...
        HashMap<String, Serializable> ep = this.getDeviceDetails(ep_name);
        if (ep != null) {
            // save off this device 
            this.saveDeviceDetails(ep_name, ep);

            // we are good
            status = true;
        }
        else {
            // device is not registered... so create/register it
            status = this.createAndRegisterNewDevice(message);
        }
        
        // add the device type
        if (status == true) {
            this.m_processor.setEndpointTypeFromEndpointName(ep_name, ep_type);
        }

        // return our status
        return status;
    }

    // create and register a new device
    private boolean createAndRegisterNewDevice(Map message) {
        Boolean status = false;

        // create the new device type
        String device_type = Utils.valueFromValidKey(message, "endpoint_type", "ept");
        String ep_name = Utils.valueFromValidKey(message, "id", "ep");

        // IOTHUB DeviceID Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

        // create the URL
        String url = this.m_device_id_url_template.replace("__EPNAME__", iothub_ep_name);

        // build out the PUT payload
        String payload = this.m_iot_event_hub_add_device_json.replace("__EPNAME__", iothub_ep_name);

        // DEBUG
        this.errorLogger().info("IoTHub: registerNewDevice: URL: " + url + " DATA: " + payload);

        // dispatch and look for the result
        String result = this.httpsPut(url, payload);

        // check the result
        int http_code = this.m_http.getLastResponseCode();
        if (Utils.httpResponseCodeOK(http_code)) {
            // DEBUG
            this.errorLogger().info("IoTHub: registerNewDevice: SUCCESS. RESULT: " + result);
            status = true;

            // DEBUG
            this.errorLogger().info("IoTHub: registerNewDevice: saving off device details...");

            // save off device details...
            this.saveAddDeviceDetails(iothub_ep_name, device_type, result);
        }
        else if (http_code == 409) {
            // DEBUG
            this.errorLogger().info("IoTHub: registerNewDevice: SUCCESS (already registered)");
            status = true;

            // save off device details...
            this.saveAddDeviceDetails(iothub_ep_name, device_type, result);
        }
        else {
            // DEBUG
            this.errorLogger().warning("IoTHub: registerNewDevice: FAILURE: " + this.m_http.getLastResponseCode() + " RESULT: " + result);
        }
        
        // return our status
        return status;
    }

    // process device deletion
    public Boolean deleteDevice(String ep_name) {
        // IOTHUB DeviceID Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

        // create the URL
        String url = this.m_device_id_url_template.replace("__EPNAME__", iothub_ep_name);

        // Get the ETag
        String etag = this.getETagForDevice(iothub_ep_name);

        // DEBUG
        this.errorLogger().info("IoTHub: deleteDevice: URL: " + url);

        // dispatch and look for the result
        String result = this.httpsDelete(url, etag);

        // check the result
        int http_code = this.m_http.getLastResponseCode();
        if (Utils.httpResponseCodeOK(http_code)) {
            // DEBUG
            this.errorLogger().info("IoTHub: deleteDevice: device: " + ep_name + " deletion SUCCESS");
        }
        else if (http_code == 404) {
            // DEBUG
            this.errorLogger().info("IoTHub: ddeleteDevice: device: " + ep_name + " deletion SUCCES(not found)");
        }
        else {
            // DEBUG
            this.errorLogger().warning("IoTHub: deleteDevice: FAILURE: " + this.m_http.getLastResponseCode() + " RESULT: " + result);
        }

        // remove the endpoint details
        this.m_endpoint_details.remove(iothub_ep_name);

        // return our status
        return true;
    }

    // httpsGet a given device's details...
    private HashMap<String, Serializable> getDeviceDetails(String ep_name) {
        HashMap<String, Serializable> ep = null;
        boolean status = false;
        boolean success = false;

        // IOTHUB DeviceID Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

        // create the URL
        String url = this.m_device_id_url_template.replace("__EPNAME__", iothub_ep_name);

        // DEBUG
        this.errorLogger().info("IoTHub: getDeviceDetails: URL: " + url);

        // loop through and try a few times...
        for(int i=0;i<this.m_num_retries && success == false;++i) {
            // dispatch and look for the result
            String result = this.httpsGet(url);

            // check the result
            int http_code = this.m_http.getLastResponseCode();
            if (Utils.httpResponseCodeOK(http_code)) {
                // DEBUG
                this.errorLogger().info("IoTHub: getDeviceDetails: SUCCESS. RESULT: " + result);
                status = true;
                success = true;
            }
            else if (http_code == 404) {
                // DEBUG (not found... OK)
                this.errorLogger().info("IoTHub: getDeviceDetails: SUCCESS. (Not FOUND - OK)");
                success = true;
            }
            else {
                // DEBUG
                this.errorLogger().info("IoTHub: getDeviceDetails: FAILURE: " + this.m_http.getLastResponseCode() + ". Retrying...");
            }

            // parse our result...
            if (status == true) {
                ep = this.parseDeviceDetails(ep_name, result);
            }
            
            // if unsuccessful... wait a bit and retry
            if (success == false) {
                Utils.waitForABit(this.errorLogger(),this.m_get_retry_wait_ms);
            }
        }

        // return our endpoint details
        return ep;
    }

    // GET specific data to a given URL 
    private String httpsGet(String url) {
        this.m_http.setAuthorizationQualifier(this.m_iot_event_hub_auth_qualifier);
        this.errorLogger().info("httpsGet: SASToken: " + this.m_iot_event_hub_sas_token);
        String result = this.m_http.httpsGetApiTokenAuth(url, this.m_iot_event_hub_sas_token, null, "application/json");
        return result;
    }

    // PUT specific data to a given URL (with data)
    private String httpsPut(String url, String payload) {
        this.m_http.setAuthorizationQualifier(this.m_iot_event_hub_auth_qualifier);
        this.errorLogger().info("httpsPut: SASToken: " + this.m_iot_event_hub_sas_token);
        String result = this.m_http.httpsPutApiTokenAuth(url, this.m_iot_event_hub_sas_token, payload, "application/json");
        return result;
    }

    // DELETE specific data to a given URL (with data)
    private String httpsDelete(String url, String etag) {
        return this.httpsDelete(url, etag, null);
    }

    private String httpsDelete(String url, String etag, String payload) {
        this.m_http.setAuthorizationQualifier(this.m_iot_event_hub_auth_qualifier);
        this.m_http.setETagValue(etag);             // ETag header required...
        this.m_http.setIfMatchValue("*");           // If-Match header required... 
        String result = this.m_http.httpsDeleteApiTokenAuth(url, this.m_iot_event_hub_sas_token, payload, "application/json");
        return result;
    }

    // Get the ETag value for the device
    private String getETagForDevice(String ep_name) {
        HashMap<String, Serializable> ep = this.getEndpointDetails(ep_name);
        if (ep != null) {
            return (String)ep.get("etag");
        }
        return null;
    }

    // httpsGet the endpoint key
    public String getEndpointKey(String ep_name) {
        return this.getEndpointKey(ep_name, "primary_key");
    }

    private String getEndpointKey(String ep_name, String id) {
        HashMap<String, Serializable> ep = this.getEndpointDetails(ep_name);
        if (ep != null) {
            return (String)ep.get(id);
        }
        return null;
    }

    // httpsGet the endpoint details
    public HashMap<String,Serializable> getEndpointDetails(String ep_name) {
        // IOTHUB DeviceID Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

        return this.m_endpoint_details.get(iothub_ep_name);
    }

    // parse our device details
    private HashMap<String, Serializable> parseDeviceDetails(String ep_name, String json) {
        return this.parseDeviceDetails(ep_name, "", json);
    }

    private HashMap<String, Serializable> parseDeviceDetails(String device, String device_type, String json) {
        SerializableHashMap ep = null;

        // IOTHUB DeviceID Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(device);

        // check the input json
        if (json != null) {
            try {
                if (json.contains("ErrorCode:DeviceNotFound;") == false) {
                    // Parse the JSON...
                    Map parsed = this.orchestrator().getJSONParser().parseJson(json);
                    if (parsed != null) {
                        // Device Details
                        String d = this.orchestrator().getTablenameDelimiter();
                        ep = new SerializableHashMap(this.orchestrator(),"IOTHUB_DEVICE" + d + device + d + device_type);

                        // Device Keys
                        Map authentication = (Map) parsed.get("authentication");
                        Map symmetric_key = (Map) authentication.get("symmetricKey");
                        ep.put("primary_key", (String) symmetric_key.get("primaryKey"));
                        ep.put("secondary_key", (String) symmetric_key.get("secondaryKey"));

                        // ETag for device
                        ep.put("etag", (String) parsed.get("etag"));

                        // Device Name
                        ep.put("deviceID", (String) parsed.get("deviceId"));
                        ep.put("ep_name", iothub_ep_name);
                        ep.put("ep_type", device_type);

                        // record the entire record for later...
                        ep.put("json_record", json);

                        // DEBUG
                        //this.errorLogger().info("parseDeviceDetails for " + device + ": " + ep);
                    }
                    else {
                        // unable to parse device details
                        this.errorLogger().warning("IoTHub: parseDeviceDetails: ERROR Unable to parse device details!");
                    }
                }
                else {
                    // device is not found
                    this.errorLogger().info("IoTHub: parseDeviceDetails: device " + iothub_ep_name + " is not a registered device (OK)");
                    ep = null;
                }
            }
            catch (Exception ex) {
                // exception in parsing... so nullify...
                this.errorLogger().warning("IoTHub: parseDeviceDetails: exception while parsing device " + iothub_ep_name + " JSON: " + json, ex);
                if (ep != null) {
                    this.errorLogger().warning("IoTHub: parseDeviceDetails: last known ep contents: " + ep);
                }
                else {
                    this.errorLogger().warning("IoTHub: parseDeviceDetails: last known ep contents: EMPTY");
                }
                ep = null;
            }
        }
        else {
            this.errorLogger().info("IoTHub: parseDeviceDetails: input JSON is EMPTY");
            ep = null;
        }

        // return our endpoint details
        if (ep != null) {
            return ep.map();
        }
        
        // returning empty map
        this.errorLogger().info("IoTHub: parseDeviceDetails: returning empty map!"); 
        return null;
    }

    // Parse the AddDevice result and capture key elements 
    private void saveAddDeviceDetails(String ep_name, String device_type, String json) {
        // IOTHUB DeviceID Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

        // parse our device details into structure
        HashMap<String, Serializable> ep = this.parseDeviceDetails(iothub_ep_name, device_type, json);
        if (ep != null) {
            // save off the details
            this.saveDeviceDetails(iothub_ep_name, ep);
        }
        else {
            // unable to parse details
            this.errorLogger().warning("IoTHub: saveAddDeviceDetails: ERROR: unable to parse device " + iothub_ep_name + " details JSON: " + json);
        }
    }

    // save device details
    public void saveDeviceDetails(String ep_name, HashMap<String, Serializable> entry) {
        // IOTHUB DeviceID Prefix
        String iothub_ep_name = this.addDeviceIDPrefix(ep_name);

        // don't overwrite an existing entry..
        if (this.m_endpoint_details.get(iothub_ep_name) == null) {
            // DEBUG
            this.errorLogger().info("IoTHub: saveDeviceDetails: saving " + iothub_ep_name + ": " + entry);

            // save off the endpoint details
            this.m_endpoint_details.put(iothub_ep_name, entry);
        }
    }

    // create a MQTT Password for a given device
    public String createMQTTPassword(String device) {
        // use the IoTHub SAS Token + the original signature qualifier
        return this.m_iot_event_hub_auth_qualifier + " " + this.m_iot_event_hub_sas_token;
    }
}
