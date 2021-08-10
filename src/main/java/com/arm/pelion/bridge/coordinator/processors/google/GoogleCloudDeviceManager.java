/**
 * @file    GoogleDeviceManager.java
 * @brief   Google Device Manager for the Google Peer Processor
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
package com.arm.pelion.bridge.coordinator.processors.google;

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.arm.PelionProcessor;
import com.arm.pelion.bridge.coordinator.processors.core.DeviceManager;
import com.arm.pelion.bridge.coordinator.processors.interfaces.DeviceManagerToPeerProcessorInterface;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.data.SerializableHashMap;
import com.arm.pelion.bridge.transport.HttpTransport;
import com.google.api.services.cloudiot.v1.CloudIot;
import com.google.api.services.cloudiot.v1.model.Device;
import com.google.api.services.cloudiot.v1.model.DeviceCredential;
import com.google.api.services.cloudiot.v1.model.DeviceRegistry;
import com.google.api.services.cloudiot.v1.model.EventNotificationConfig;
import com.google.api.services.cloudiot.v1.model.HttpConfig;
import com.google.api.services.cloudiot.v1.model.MqttConfig;
import com.google.api.services.cloudiot.v1.model.PublicKeyCredential;
import com.google.api.services.cloudiot.v1.model.StateNotificationConfig;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.model.Topic;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class defines the required REST functions to manage Google CloudIoT devices
 *
 * @author Doug Anson
 */
public class GoogleCloudDeviceManager extends DeviceManager implements Runnable {
    private static String CLOUD_KEY_PREFIX = "lwm2m";
    private static String CLOUD_KEY_SEPARATOR = "_";
    private String m_project_id = null;
    private String m_region = null;
    private String m_topic_root = null;
    private String m_registry_name = null;
    private CloudIot m_cloud_iot = null;
    private Pubsub m_pub_sub = null;
    private String m_registry_path_template = null;
    private String m_registry_path = null;
    private String m_project_path_template = null;
    private String m_project_path = null;
    private String m_device_path_template = null;
    private String m_obs_key = null;
    private String m_cmd_key = null; 
    private int m_google_cloud_key_length = 0;
    private String m_google_cloud_key_create_cmd_template = null;
    private String m_google_cloud_key_convert_cmd_template = null;
    private String m_keystore_rootdir = null;
    private int m_num_days = 0;
    private DeviceManagerToPeerProcessorInterface m_processor = null;
    
    // constructor
    public GoogleCloudDeviceManager(HttpTransport http,DeviceManagerToPeerProcessorInterface processor,String project_id,String region,CloudIot cloud_iot,Pubsub pub_sub,String obs_key,String cmd_key) {
        this(null,http,processor,project_id,region,cloud_iot,pub_sub,obs_key,cmd_key);
    }

    // defaulted constructor
    public GoogleCloudDeviceManager(String suffix, HttpTransport http,DeviceManagerToPeerProcessorInterface processor,String project_id,String region,CloudIot cloud_iot,Pubsub pub_sub,String obs_key,String cmd_key) {
        super(processor.errorLogger(),processor.preferences(),suffix,http,processor.orchestrator());
        this.m_processor = processor;
        
        // create the registry ID
        this.m_project_id = project_id;
        this.m_region = region;
        this.m_cloud_iot = cloud_iot;
        this.m_pub_sub = pub_sub;
        this.m_obs_key = obs_key;
        this.m_cmd_key = cmd_key;
        this.m_google_cloud_key_length = this.orchestrator().preferences().intValueOf("google_cloud_key_length",this.m_suffix);
        this.m_topic_root = this.orchestrator().preferences().valueOf("google_cloud_topic_root",this.m_suffix);
        this.m_registry_name = this.orchestrator().preferences().valueOf("google_cloud_registry_name",this.m_suffix);
        this.m_project_path_template = this.orchestrator().preferences().valueOf("google_cloud_project_path_template",this.m_suffix);
        this.m_registry_path_template = this.orchestrator().preferences().valueOf("google_cloud_registry_path_template",this.m_suffix);
        this.m_device_path_template = this.orchestrator().preferences().valueOf("google_cloud_client_id_template",this.m_suffix);
        this.m_project_path = this.buildProjectPath(this.m_project_id,this.m_region);
        this.m_registry_path = this.buildRegistryPath(this.m_project_id,this.m_region);
        this.m_google_cloud_key_create_cmd_template = this.orchestrator().preferences().valueOf("google_cloud_key_create_cmd_template",this.m_suffix);
        this.m_google_cloud_key_convert_cmd_template = this.orchestrator().preferences().valueOf("google_cloud_key_convert_cmd_template",this.m_suffix);
        this.m_keystore_rootdir = this.orchestrator().preferences().valueOf("mqtt_keystore_basedir",this.m_suffix);
        this.m_num_days = this.orchestrator().preferences().intValueOf("google_cloud_cert_days_length",this.m_suffix);
        
        // ensure that we have a device registry        
        if (this.initDeviceRegistry(this.m_registry_name,this.m_obs_key,this.m_cmd_key)) {
            // DEBUG
            this.errorLogger().info("Google: device registry exists: " + this.m_registry_name + "... (OK).");
        }
        else {
            // unable to create the device registry
            this.errorLogger().critical("Google: CRITICAL Unable to create device registry: " + this.m_registry_name);
        }
    }
    
    // initialize a new device registry
    private boolean initDeviceRegistry(String registry_id,String notifications,String commands) {
        if (this.deviceRegistryExists(registry_id) == false) {
            // no device registry... so create it. 
            return this.createDeviceRegistry(registry_id,notifications,commands);
        }
        else {
            // DEBUG
            this.errorLogger().info("initDeviceRegistry: Device Registry exists: " + registry_id + "...(OK).");
        }
        return true;
    }
    
    // build the device path
    private String buildDevicePath(String ep_name) {
        // build the path
        return this.m_device_path_template.replace("__PROJECT_ID__", this.m_project_id).replace("__CLOUD_REGION__", this.m_region).replace("__REGISTRY_NAME__", this.m_registry_name).replace("__EPNAME__",ep_name);
    }
    
    // build the project path 
    private String buildProjectPath(String project_id,String region) {
        // build the path
        return this.m_project_path_template.replace("__PROJECT_ID__", project_id).replace("__CLOUD_REGION__", region);
    }
    
    // build the registry path 
    private String buildRegistryPath(String project_id,String region) {
        // build the path
        return this.m_registry_path_template.replace("__PROJECT_ID__", project_id).replace("__CLOUD_REGION__", region).replace("__REGISTRY_NAME__", this.m_registry_name);
    }
    
    // create the full PubSub Path
    private String buildFullPubSubPath(String topic_path) {
        // build the path
        return "projects/" + this.m_project_id + "/topics/" + topic_path;
    }
    
    // does our device regsitry exist?
    private boolean deviceRegistryExists(String registry_id) {
        boolean exists = false;
        
        try {
            // see if our device registry exists
            DeviceRegistry registry = this.m_cloud_iot.projects().locations().registries().get(this.m_registry_path).execute();
            if (registry != null) {
                // registry exists
                exists = true;
            }
        } 
        catch (IOException ex) {
            // registry does not exist
        }
        
        // return our status
        return exists;
    }
    
    // install a device registry
    private boolean createDeviceRegistry(String registry_id,String notifications,String commands) {
        boolean created = false;
        
        try {
            DeviceRegistry registry = new DeviceRegistry();
            
            // setup the event notification topic
            String eventNotificationTopic = this.buildFullPubSubPath(notifications);
            this.createTopic(eventNotificationTopic);
            EventNotificationConfig eventNotificationConfig = new EventNotificationConfig();
            eventNotificationConfig.setPubsubTopicName(eventNotificationTopic);
            List<EventNotificationConfig> eventNotificationConfigs = new ArrayList<>();
            eventNotificationConfigs.add(eventNotificationConfig);
            registry.setEventNotificationConfigs(eventNotificationConfigs);
            
            // setup the state change notification topic
            String stateChangeTopic = this.buildFullPubSubPath(commands);
            this.createTopic(stateChangeTopic);
            StateNotificationConfig stateChangeConfig = new StateNotificationConfig();
            stateChangeConfig.setPubsubTopicName(stateChangeTopic);
            registry.setStateNotificationConfig(stateChangeConfig);
            
            // now build out the registry configuration
            MqttConfig mqttConfig = new MqttConfig();
            registry.setMqttConfig(mqttConfig);
            HttpConfig httpConfig = new HttpConfig();
            registry.setHttpConfig(httpConfig);
            registry.setId(this.m_registry_name);
            DeviceRegistry inst = this.m_cloud_iot.projects().locations().registries().create(this.m_project_path,registry).execute();
            if (inst != null) {
                // created!
                created = true;
            }
        }
        catch (IOException ex) {
            // Unable to create registry
            this.errorLogger().critical("GoogleCloudIOT: CRITICAL: Unable to create device registry: " + ex.getMessage());
            this.errorLogger().critical("GoogleCloudIOT: projectPath: " + this.m_project_path + " registryPath: " + this.m_registry_path);
        }
        
        // return our status
        return created;
    }

    // process new device registration
    public boolean registerNewDevice(Map message) {
        boolean status = false;

        // get the device details
        String ep_type = Utils.valueFromValidKey(message, "endpoint_type", "ept");
        String ep_name = Utils.valueFromValidKey(message, "id", "ep");

        // see if we already have a device...
        HashMap<String, Serializable> ep = this.getDeviceDetails(ep_name);
        if (ep != null) {
            // next lets ensure that Google also has a record of this device...
            if (this.googleDeviceExists(ep_name) == true) {
                // DEBUG
                this.errorLogger().info("GoogleCloudIOT: registerNewDevice: device details: " + ep);

                // we are good
                status = true;
            }
            else {
                // we have a copy of the device.. but Google does not... 
                status = this.createAndRegisterNewDevice(message,false);
            }
        }
        else {
            // DEBUG
            this.errorLogger().info("GoogleCloudIOT: registerNewDevice: no device found for: " + ep_name + "... (OK).");
            //this.errorLogger().info("Google: Device Details: " + message);

            // device is not registered... so create/register it
            status = this.createAndRegisterNewDevice(message,true);
        }
        
        // add the device type
        if (status == true) {
            this.m_processor.setEndpointTypeFromEndpointName(ep_name, ep_type);
        }

        // return our status
        return status;
    }
    
    // mbed endpoint ID to google device ID
    public String mbedDeviceIDToGoogleDeviceID(String ep_name) {
        if (ep_name != null) {
            return this.m_topic_root + "_" + ep_name;
        }
        return null;
    }
    
    // google device ID to mbed endpoint ID
    public String googleDeviceIDToMbedDeviceID(String device_id) {
        if (device_id != null) {
            String remove_me = this.m_topic_root + "_";
            return device_id.replace(remove_me,"");
        }
        return null;
    }
    
    // does the device already exist in google?
    private boolean googleDeviceExists(String ep_name) {
        boolean exists = false;
        
        try {
            // build our device path (Google Device ID)
            String device_path = this.buildDevicePath(this.mbedDeviceIDToGoogleDeviceID(ep_name));
            
            // create the device now... 
            Device inst = this.m_cloud_iot.projects().locations().registries().devices().get(device_path).execute();
            if (inst != null) {
                // device exists!
                exists = true;
            }
        }
        catch(IOException ex) {
            // unable to get the device
            this.errorLogger().info("GoogleCloudIOT: Unable to query for the device (exception): " + ex.getMessage());
        }
        
        // return the existance status
        return exists;
    }
    
    // map LWM2M URI to Google Cloud IoT compatible key
    private String lwm2mURIToGoogleKey(String uri) {
        if (uri != null) {
            return CLOUD_KEY_PREFIX + uri.replace("/",CLOUD_KEY_SEPARATOR);
        }
        return uri;
    }
    
    // map Google Cloud IoT compatible key to LWM2M URI
    private String googleKeyToLwm2mURI(String key) {
        if (key != null) {
            return key.replace(CLOUD_KEY_PREFIX,"").replace(CLOUD_KEY_SEPARATOR, "/");
        }
        return key;
    }
    
    // get the number of LWM2M resources in our device
    private int getNumResources(Map message) {
        List resources = (List)message.get("resources");
        if (resources != null) {
            return resources.size();
        }
        return 0;
    }
    
    // get the ith LWMW2M resource
    private Map getResource(int i,Map message) {
        List resources = (List)message.get("resources");
        if (resources != null && i >= 0 && i < resources.size()) {
            return (Map)resources.get(i);
        }
        return (Map)null;
    }
    
    // create the device metadata
    private HashMap<String,String> createDeviceMetadata(Map message) {
        HashMap<String,String> metadata = new HashMap<>();
        PelionProcessor p = (PelionProcessor)this.orchestrator().pelion_processor();
        p.initDeviceWithDefaultAttributes(message);
        metadata.put("device_type",this.sanitizeEndpointType(Utils.valueFromValidKey(message, "ept", "endpoint_type")));
        metadata.put("device_id",Utils.valueFromValidKey(message, "id", "ep"));
        metadata.put("serial_number",(String)message.get("meta_serial"));
        metadata.put("device_description",(String)message.get("meta_description"));
        metadata.put("hardware_version",(String)message.get("meta_hardware"));
        metadata.put("firmware_version",(String)message.get("meta_firmware"));
        metadata.put("platform_type",(String)message.get("meta_model"));
        metadata.put("platform_cpu",(String)message.get("meta_class"));
        
        // create OBS records for each LWM2M resource...
        int num_resources = this.getNumResources(message);
        for(int i=0;i<num_resources;++i) {
            Map resource = this.getResource(i,message);
            if (resource != null) {
                // get the LWM2M Resource URI
                String uri = Utils.valueFromValidKey(resource, "uri", "path");
                if (uri != null && Utils.isCompleteURI(uri) == true && Utils.isHandledURI(uri) == false) {
                    // put observable resources into the metadata
                    Object obs = resource.get("obs");
                    if (obs instanceof Boolean) {
                        // newer format: Boolean
                        Boolean obs_b = (Boolean)obs;
                        String obs_s = "false";
                        if (obs_b == true) {
                            obs_s = "true";
                        }
                        String key = this.lwm2mURIToGoogleKey(uri);
                        metadata.put(key,obs_s);
                    }
                    else if (obs instanceof String) {
                        // older format: String
                        String obs_s = (String)obs;
                        if (obs_s != null && obs_s.length() > 0) {
                            String key = this.lwm2mURIToGoogleKey(uri);
                            metadata.put(key,obs_s);
                        }
                    }
                    else {
                        // if its present but neither String nor Boolean...
                        if (obs != null) {
                            String key = this.lwm2mURIToGoogleKey(uri);
                            metadata.put(key,"true");
                        }
                    }
                }
            }
        }
        
        // DEBUG
        this.errorLogger().info("GoogleCloudIOT: METADATA: " + metadata);
        this.errorLogger().info("GoogleCloudIOT: MESSAGE: " + message);
        
        return metadata;
    }
    
    // create the device credentials
    private ArrayList<DeviceCredential> createDeviceCredentials(Map message) throws UnsupportedEncodingException {
        DeviceCredential cred = new DeviceCredential();
        PublicKeyCredential pkc = new PublicKeyCredential();
        byte[] pubKey = Utils.readRSAKeyforDevice(this.errorLogger(),this.m_keystore_rootdir,(String)message.get("ep"),false); //public key
        pkc.setKey(new String(pubKey,"UTF-8"));
        pkc.setFormat("RSA_X509_PEM");
        cred.setPublicKey(pkc);
        ArrayList<DeviceCredential> credlist = new ArrayList<>();
        credlist.add(cred);
        return credlist;
    }
    
    // check whether we have valid RSA creds in a keystore for a given device's shadow
    private boolean haveDeviceShadowCredentials(String ep_name) {
        boolean have_creds = false;
        
        // try to read the private key from the keystore file
        byte[] pkey  = Utils.readRSAKeyforDevice(this.errorLogger(), this.m_keystore_rootdir, ep_name, true);
        if (pkey != null && pkey.length > 10) {
            // we have creds
            have_creds = true;
        }
        
        // return our status
        return have_creds;
    }

    // create and register a new device
    private boolean createAndRegisterNewDevice(Map message,boolean cache_device) {
        boolean created = false;
        
        // create the new device type
        String ep_type = Utils.valueFromValidKey(message, "endpoint_type", "ept");
        String ep_name = Utils.valueFromValidKey(message, "id", "ep");
        
        // create the Google Device
        try {
            if (this.googleDeviceExists(ep_name) == false) {
                // device does not exist in Google CloudIoT... so we have to build it...
                Device device = new Device();

                // map to endpoint name - we preface with the endpoint_type
                device.setId(this.mbedDeviceIDToGoogleDeviceID(ep_name));
                
                // create the key file for this device
                String keystore = Utils.createRSAKeysforDevice(this.errorLogger(),this.m_keystore_rootdir,this.m_num_days,this.m_google_cloud_key_create_cmd_template,this.m_google_cloud_key_convert_cmd_template,this.m_google_cloud_key_length,ep_name);
                if (keystore != null) {                    
                    // set the device metadata
                    device.setMetadata(this.createDeviceMetadata(message));

                    // create our credential for this device...
                    device.setCredentials(this.createDeviceCredentials(message));

                    // create the device now... 
                    Device inst = this.m_cloud_iot.projects().locations().registries().devices().create(this.m_registry_path,device).execute();
                    if (inst != null) {
                        // cache the device
                        if (cache_device == true) {
                            // DEBUG
                            this.errorLogger().info("GoogleCloudIOT: registerNewDevice: device created!  Now saving off device details...");

                            // save off device details... (empty result)
                            this.saveAddDeviceDetails(ep_name, ep_type, keystore);
                            created = true;
                        }
                        else {
                            // already cached (OK)
                            this.errorLogger().info("GoogleCloudIOT: registerNewDevice: device already cached...(OK).");
                            created = true;
                        }
                    }
                }
                else {
                    // unable to create keystore... so cannot create device
                    this.errorLogger().warning("GoogleCloudIOT: registerNewDevice: key creation FAILED. Unable to create device.");
                }
            }
            else if (this.haveDeviceShadowCredentials(ep_name) == false) {
                // device exists in Google CloudIoT... but we no longer have creds for it...
                this.errorLogger().warning("GoogleCloudIoT: device exists in cloud but we no longer have shadow creds. Re-creating shadow...");
                
                // First, we delete the old device in CloudIoT
                this.deleteDevice(ep_name); 
                
                // then we recurse into this method and try again
                created = this.createAndRegisterNewDevice(message, cache_device);
            }
            else if (cache_device == true) {
                // device already exists in google... so just add it here...
                this.errorLogger().info("GoogleCloudIOT: registerNewDevice: device already exists with creds... (OK). Caching device details...");

                // save off device details... (empty result)
                this.saveAddDeviceDetails(ep_name, ep_type, null);
                created = true;
            }
            else {
                // device already exists in google... so just add it here...
                this.errorLogger().info("GoogleCloudIOT: registerNewDevice: device already exists... (OK). Already cached(OK).");
                created = true;
            }
        }
        catch(IOException | NullPointerException ex) {
            // Unable to create device
            this.errorLogger().warning("GoogleCloudIOT: registerNewDevice: ERROR: Unable to create device: " + ep_name + " Error: " + ex.getMessage());
        }

        // return our status
        return created;
    }
    
    // remove the device shadow's keystore
    private void removeKeystoreForDeviceShadow(String ep_name) {
        if (ep_name != null && ep_name.length() > 0) {
            // Get the endpoint device details
            Map device = this.m_endpoint_details.get(ep_name);
            if (device != null && device.isEmpty() == false) {
                // get the keystore filename
                String keystore = (String)device.get("keystore");
                if (keystore != null) {
                    // DEBUG
                    this.errorLogger().info("GoogleCloudIoT: Deleting keystore: " + keystore + "...");
                    
                    // remove the keystore
                    Utils.deleteKeystore(this.errorLogger(), keystore, ep_name);
                }
            }
        }
    }

    // process device deletion
    public boolean deleteDevice(String ep_name) {
        boolean deleted = false;
        if (ep_name != null && ep_name.length() > 0) {
            try {
                // remove the device from Google
                String device_path = this.buildDevicePath(this.mbedDeviceIDToGoogleDeviceID(ep_name));
                this.m_cloud_iot.projects().locations().registries().devices().delete(device_path).execute();

                // DEBUG
                this.errorLogger().warning("GoogleCloudIOT: deleteDevice: device: " + ep_name + " deletion SUCCESS");

                // success
                deleted = true;
            }
            catch (IOException ex) {
                // unable to delete the device
                this.errorLogger().warning("GoogleCloudIOT: deleteDevice: WARNING:  Unable to delete device: " + ep_name + " from Google CloudIoT. Exception: " + ex.getMessage());
            }
        }
        else {
            this.errorLogger().warning("GoogleCloudIOT: deleteDevice: WARNING: device name is NULL. Nothing deleted");
        }
        
        // remove the keystore for this device
        this.removeKeystoreForDeviceShadow(ep_name);
        
        // remove the endpoint details
        this.m_endpoint_details.remove(ep_name);
        
        // error
        return deleted;
    }

    // get a given device's details...
    public HashMap<String, Serializable> getDeviceDetails(String device_id) {
        return this.m_endpoint_details.get(device_id);
    }

    // Parse the AddDevice result and capture key elements 
    private void saveAddDeviceDetails(String ep_name, String ep_type,String keystore) {
        SerializableHashMap entry = null;
        
        // create our cache entry
        String d = this.orchestrator().getTablenameDelimiter();
        entry = new SerializableHashMap(this.orchestrator(),"GOOGLE_DEVICE" + d + ep_name + d + ep_type);
        entry.put("ep_name", ep_name);
        entry.put("ep_type", ep_type);
        entry.put("device_path",this.buildDevicePath(this.mbedDeviceIDToGoogleDeviceID(ep_name)));
        entry.put("region",this.m_region);
        entry.put("project",this.m_project_id);
        entry.put("registry_path",this.m_registry_path);
        entry.put("registry_name",this.m_registry_name);
        entry.put("keystore",keystore);
        
        // save off
        this.m_endpoint_details.put(ep_name,entry.map());
    }

    // save device details
    public void saveDeviceDetails(String ep_name, HashMap<String, Serializable> entry) {
        // don't overwrite an existing entry..
        if (this.getDeviceDetails(ep_name) == null) {
            // save off the endpoint details
            this.m_endpoint_details.put(ep_name, entry);
        }
    }
    
    // get the orchestrator
    private Orchestrator orchestrator() {
        return this.m_orchestrator;
    }
    
    // Create a topic
    private Topic createTopic(String topic) {
        if (this.m_pub_sub != null) {
            try {
                try {
                    // see if we already have the topic
                    return this.m_pub_sub.projects().topics().get(topic).execute();
                }
                catch (com.google.api.client.googleapis.json.GoogleJsonResponseException ex) {
                    // Create the Topic
                    this.errorLogger().info("GoogleCloudIOT: Creating PubSub Topic: " + topic);
                    return this.m_pub_sub.projects().topics().create(topic,new Topic()).execute();
                }
            }
            catch (com.google.api.client.googleapis.json.GoogleJsonResponseException ex) {
                // DEBUG
                this.errorLogger().info("GoogleCloudIOT: Exception during PubSub topic creation: " + topic);
            } 
            catch (IOException ex) {
                // no pubsub instance
                this.errorLogger().info("GoogleCloudIOT: I/O exception in PubSub topic creation: " + topic);
            }
            catch (Exception ex) {
                // no pubsub instance
                this.errorLogger().info("GoogleCloudIOT: General exception in PubSub topic creation: " + topic);
            }
        }
        else {
            // no pubsub instance
            this.errorLogger().warning("GoogleCloudIOT: no PubSub instance... unable to create topic");
        }
        return null;
    }
    
    // Remove a Topic
    private void removeTopic(String topic) {
        if (this.m_pub_sub != null) {
            try {
                // remove the topic
                this.errorLogger().info("GoogleCloudIOT: removing topic: " + topic + "...");
                this.m_pub_sub.projects().topics().delete(topic).execute();
            }
            catch (com.google.api.client.googleapis.json.GoogleJsonResponseException ex) {
                // DEBUG
                this.errorLogger().info("GoogleCloudIOT: Exception during topic removal: " + topic);
            }   
            catch (IOException ex) {
                // DEBUG
                this.errorLogger().info("GoogleCloudIOT: I/O exception during topic removal: " + topic);
            }
            catch (Exception ex) {
                // DEBUG
                this.errorLogger().info("GoogleCloudIOT: General exception during topic removal: " + topic);
            }
        }
    }

    // not used. 
    @Override
    public void run() {
        // unused
    }
}