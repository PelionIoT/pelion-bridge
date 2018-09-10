/**
 * @file SAMPLEDeviceManager.java
 * @brief SAMPLE Device Manager
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
package com.arm.pelion.bridge.coordinator.processors.sample;

import com.arm.pelion.bridge.coordinator.processors.core.DeviceManager;
import com.arm.pelion.bridge.transport.HttpTransport;
import java.util.Map;

/**
 * SAMPLE Device Manager
 * @author Doug Anson
 */
public class SAMPLEDeviceManager extends DeviceManager {
    private SAMPLEProcessor m_processor = null;
    
    // constructor
    public SAMPLEDeviceManager(SAMPLEProcessor processor, HttpTransport http) {
        this(processor,http,null);
    }

    // constructor
    public SAMPLEDeviceManager(SAMPLEProcessor processor,HttpTransport http,String suffix) {
        super(processor.orchestrator(),http,suffix);
        this.m_processor = processor;
    }
    
    // create our device type id
    public String createDeviceTypeID(Map device,String prefix) {
        return (String)device.get("ept");
    }
    
    // does the SAMPLE device type exist?
    private boolean deviceTypeExists(String device_type_id) {
        this.errorLogger().warning("SAMPLE(deviceExists): device type ID: " + device_type_id);
        return false;
    }
    
    // does the SAMPLE device twin exist?
    private boolean deviceExists(String device_id) {
        this.errorLogger().warning("SAMPLE(deviceExists): device: " + device_id);
        return false;
    }
    
    // create the SAMPLE devic type
    private boolean createDeviceType(Map device,String prefix) {        
        // create the device type ID
        String device_type_id = this.createDeviceTypeID(device,prefix);
        
        // see if our type already exists
        if (this.deviceTypeExists(device_type_id)) {
            // already exists... so OK...
            return true;
        }
        else {
            // DEBUG
            this.errorLogger().warning("SAMPLE(createDeviceType): Creating SAMPLE device type for Pelion Device: " + device);
            
            // create the device type
            String create_device_type_json = "{}";                  // XXXX
            String url = this.m_processor.buildDeviceTypeURL();
                        
            // issue POST to create the device type
            String result = this.httpPost(url, create_device_type_json);
            int result_code = this.getLastResponseCode();
            
            // DEBUG
            this.errorLogger().warning("SAMPLE(createDeviceType): JSON: " + create_device_type_json + " URL: " + url + " STATUS: " + result_code);
            
            // check the result code
            if (result_code < 300) {
                // success!!
                this.errorLogger().warning("SAMPLE(createDeviceType): SUCCESSFUL: create device type. HTTP Code: " + result_code);
                return true;
            }
            else {
                // failure
                this.errorLogger().warning("SAMPLE(createDeviceType): FAILED to create device type. HTTP Code: " + result_code);
            } 
        }
        return false;
    }
    
    // create the SAMPLE device twin
    public boolean createDevice(Map device,String prefix) {
        String device_id = (String)device.get("ep");
        if (this.deviceExists(device_id) == false) {
            if (this.createDeviceType(device,prefix) == true) {
                // DEBUG
                this.errorLogger().warning("SAMPLE(createDevice): Creating SAMPLE device for Pelion Device: " + device);

                // create the device
                String create_device_json = "{}";                   // XXXX
                String url = this.m_processor.buildDeviceURL();

                // issue POST to create the device
                String result = this.httpPost(url, create_device_json);
                int result_code = this.getLastResponseCode();

                // DEBUG
                this.errorLogger().warning("SAMPLE(createDevice): JSON: " + create_device_json + " URL: " + url + " STATUS: " + result_code);

                // check the result code
                if (result_code < 300) {
                    // success!!
                    this.errorLogger().warning("SAMPLE(createDevice): SUCCESSFUL: create device. HTTP Code: " + result_code);                    
                    return true;
                }
                else {
                    // failure
                    this.errorLogger().warning("SAMPLE(createDevice): FAILED to create device. HTTP Code: " + result_code);
                } 
            }
            else {
                // unable to create the device type
                this.errorLogger().warning("SAMPLE(createDevice): Unable to create device type, thus unable to create device");
            }
        }
        else {
            this.errorLogger().warning("SAMPLE(createDevice): Device already exists (OK). Device: " + device);
            return true;
        }
        return false;
    }
    
    // delete the SAMPLE device twin
    public boolean deleteDevice(String device_id) {
        // DEBUG
        this.errorLogger().warning("SAMPLE(deleteDevice): device: " + device_id);
        
        // delete the device
        String url = this.m_processor.buildDeviceURL(device_id);
        String result = this.httpDelete(url);
        int result_code = this.getLastResponseCode();
        
        // check the result code
        if (result_code < 300) {
            // success!!
            this.errorLogger().warning("SAMPLE(deleteDevice): SUCCESSFUL: deleted device. HTTP Code: " + result_code);
            return true;
        }
        else {
            // failure
            this.errorLogger().warning("SAMPLE(deleteDevice): FAILED to delete device. HTTP Code: " + result_code);
        } 
        return false;
    }
    
    // delete the SAMPLE device type
    public boolean deleteDeviceType(String device_type_id) {
        // DEBUG
        this.errorLogger().warning("SAMPLE(deleteDeviceType): device type: " + device_type_id);
        
        // delete the device type
        String url = this.m_processor.buildDeviceTypeURL(device_type_id);
        String result = this.httpDelete(url);
        int result_code = this.getLastResponseCode();
        
        // check the result code
        if (result_code < 300) {
            // success!!
            this.errorLogger().warning("SAMPLE(deleteDeviceType): SUCCESSFUL: deleted device type. HTTP Code: " + result_code);
            return true;
        }
        else {
            // failure
            this.errorLogger().warning("SAMPLE(deleteDeviceType): FAILED to delete device type. HTTP Code: " + result_code);
        } 
        return false;
    }
    
    // get an SAMPLE device resource value
    public Object getDeviceResourceValue(String device_id,String resource_name) {
        this.errorLogger().warning("SAMPLE(getDeviceResourceValue): device: " + device_id + " resource: " + resource_name);
        return null;
    }
    
    // update an SAMPLE device resource value
    public boolean updateDeviceResourceValue(String device_id,String resource_name,Object resource_value) {
        this.errorLogger().warning("SAMPLE(getDeviceResourceValue): device: " + device_id + " resource: " + resource_name + " value: " + resource_value);
        return true;
    }
    
    // dispatch a http GET
    private String httpGet(String url) {
        return this.m_processor.httpGet(url);
    }
    
    // dispatch a http PUT
    private String httpPut(String url,String data) {
        return this.m_processor.httpPut(url,data);
    }
    
    // dispatch a http POST
    private String httpPost(String url,String data) {
        return this.m_processor.httpPost(url,data);
    }
    
    // dispatch a http DELETE
    private String httpDelete(String url) {
        return this.m_processor.httpDelete(url);
    }
    
    // get the last response code
    private int getLastResponseCode() {
        return this.m_http.getLastResponseCode();
    }
}