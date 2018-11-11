/**
 * @file GoogleCloudProcessor.java
 * @brief Google Cloud Peer Processor (HTTP)
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
package com.arm.pelion.bridge.coordinator.processors.google.http;

import com.arm.pelion.bridge.coordinator.processors.google.GoogleCloudDeviceManager;
import com.arm.pelion.bridge.transport.RetryHttpInitializerWrapper;
import com.arm.pelion.bridge.coordinator.processors.arm.GenericConnectablePeerProcessor;
import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.core.HTTPDeviceListener;
import com.arm.pelion.bridge.coordinator.processors.core.JwTRefresherThread;
import com.arm.pelion.bridge.core.ApiResponse;
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.DeviceManagerToPeerProcessorInterface;
import com.arm.pelion.bridge.coordinator.processors.interfaces.HTTPDeviceListenerInterface;
import com.arm.pelion.bridge.coordinator.processors.interfaces.JwTRefresherResponderInterface;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.transport.HttpTransport;
import com.arm.pelion.bridge.transport.Transport;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.pubsub.PubsubScopes;
import com.google.api.services.cloudiot.v1.CloudIot;
import com.google.api.services.pubsub.Pubsub;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import java.io.UnsupportedEncodingException;

/**
 * Google CloudIoT peer processor based on HTTP
 *
 * @author Doug Anson
 */
public class GoogleCloudProcessor extends GenericConnectablePeerProcessor implements JwTRefresherResponderInterface, HTTPDeviceListenerInterface, DeviceManagerToPeerProcessorInterface, Transport.ReceiveListener, PeerProcessorInterface, AsyncResponseProcessor {    
    // Google Auth Token Qualifer
    public static final String GOOGLE_AUTH_QUALIFIER = "Bearer";
                    
    // keystore root directory
    private String m_keystore_rootdir = null;

    // GoogleCloud Device Manager
    private GoogleCloudDeviceManager m_device_manager = null;
    
    // Client ID Template
    private String m_google_cloud_client_id_template = null;
    
    // GoogleCloud Project ID
    private String m_google_cloud_project_id = null;
    
    // GoogleCloud Region
    private String m_google_cloud_region = null;
        
    // max number of connect retries (JwT expiration/reset)
    private int m_max_retries = 0;
    
    // number of ms to wait prior to reconnect from JwT refresh
    private int m_jwt_refresh_wait_ms = 15000;          // 15 seconds
    
    // Google Cloud Credential
    private GoogleCredential m_credential = null;
    
    // Google Cloud AUTH Cred
    private String m_google_cloud_auth_json = null;
    
    // Google CloudIoT instance
    private CloudIot m_cloud_iot = null;
    
    // Google Pubsub instance
    private Pubsub m_pub_sub = null;
    
    // Google CloudIoT Application Name
    private String m_google_cloud_application_name = null;
    
    // Google CloudIoT Registry Name
    private String m_google_cloud_registry_name = null;
    
    // Login status
    private boolean m_google_cloud_logged_in = false;
    
    // default JWT expiration length (in seconds)
    private int m_jwt_refresh_interval = (5 * 60 * 60);    // JwT refresh interval: 5 hours
    private long m_jwt_expiration_secs = (23 * 60 * 60);   // JwT token max expiration : 23 hours
    
    // JwT refresher Thread
    private HashMap<String,JwTRefresherThread> m_jwt_refesher_thread_list = null;
    
    // HTTP listeners for our device shadows
    private HashMap<String,HTTPDeviceListener> m_device_listeners = null;
    
    // HTTP Auth Token list for device shadows
    private HashMap<String,HashMap<String,Object>> m_endpoint_auth_data = null;
    
    // Version tracker for HTTP polling
    private HashMap<String,Integer> m_device_config_versions = null;
    
    // URL templates for Google Cloud IoT/HTTP
    private String m_google_cloud_observe_notification_message_url_template = null;
    private String m_google_cloud_device_config_request_url_template = null;
    private String m_google_cloud_device_set_state_command_url_template = null;
    
    // we are configured
    private boolean m_configured = false;

    // constructor (singleton)
    public GoogleCloudProcessor(Orchestrator manager, HttpTransport http) {
        this(manager, null, http);
    }

    // constructor (with suffix for preferences)
    public GoogleCloudProcessor(Orchestrator manager, String suffix, HttpTransport http) {
        super(manager, null, suffix, http);

        // GoogleCloud Processor Announce
        this.errorLogger().warning("Google CloudIoT Processor ENABLED (HTTP)");
                        
        // keystore root directory
        this.m_keystore_rootdir = this.orchestrator().preferences().valueOf("mqtt_keystore_basedir",this.m_suffix);
        
        // max number of retries...
        this.m_max_retries = this.preferences().intValueOf("mqtt_connect_retries", this.m_suffix);

        // get the client ID template
        this.m_google_cloud_client_id_template = this.orchestrator().preferences().valueOf("google_cloud_client_id_template",this.m_suffix);
        
        // get our Google AUTH Json
        this.m_google_cloud_auth_json = this.orchestrator().preferences().valueOf("google_cloud_auth_json",this.m_suffix);
        
        // make sure we are configured
        if (this.m_google_cloud_auth_json != null && this.m_google_cloud_auth_json.contains("Goes_Here") == false) {
            // get the Project ID
            this.m_google_cloud_project_id = this.getProjectID(this.m_google_cloud_auth_json);

            // Google CloudIot Application Name
            this.m_google_cloud_application_name = this.getApplicationName(this.m_google_cloud_auth_json);

            // get the Region
            this.m_google_cloud_region = this.orchestrator().preferences().valueOf("google_cloud_region",this.m_suffix);

            // Google CloudIot Registry Name
            this.m_google_cloud_registry_name = this.orchestrator().preferences().valueOf("google_cloud_registry_name",this.m_suffix);
           
            // HTTP URL templates
            this.m_google_cloud_observe_notification_message_url_template = this.orchestrator().preferences().valueOf("google_cloud_observe_notification_message_url",this.m_suffix);
            this.m_google_cloud_device_config_request_url_template = this.orchestrator().preferences().valueOf("google_cloud_device_config_request_url",this.m_suffix);
            this.m_google_cloud_device_set_state_command_url_template = this.orchestrator().preferences().valueOf("google_cloud_device_set_state_command_url",this.m_suffix);

            // create the HTTP-based device listeners
            this.m_device_listeners = new HashMap<>();
            
            // Create the HTTP Auth Data List
            this.m_endpoint_auth_data = new HashMap<>();
            
            // Create the device versions struct
            this.m_device_config_versions = new HashMap<>();

            // HTTP Auth Qualifier
            this.m_http_auth_qualifier = GOOGLE_AUTH_QUALIFIER;

            // DEBUG
            this.errorLogger().info("ProjectID: " + this.m_google_cloud_project_id + 
                                    " Application Name: " + this.m_google_cloud_application_name + 
                                    " Region: " + this.m_google_cloud_region);

            // initialize the topic root
            this.initTopicRoot("google_cloud_topic_root");

            // create the CloudIoT instance
            this.m_cloud_iot = this.createCloudIoTInstance();

            // create the Pubsub instance
            this.m_pub_sub = this.createPubSubInstance();

            // GoogleCloud Device Manager - will initialize and upsert our GoogleCloud bindings/metadata
            this.m_device_manager = new GoogleCloudDeviceManager(this.m_suffix, http, this, this.m_google_cloud_project_id, this.m_google_cloud_region, this.m_cloud_iot,this.m_pub_sub,this.m_observation_key,this.m_cmd_response_key);

            // initialize our MQTT transport list
            this.initMQTTTransportList();

            // initialize the JwT refresher thread list
            this.m_jwt_refesher_thread_list = new HashMap<>();
            
            // we are configured
            this.m_configured = true;
        }
        else {
            // unconfigured
            this.errorLogger().warning("GoogleCloudIOT: AUTH JSON is UNCONFIGURED. Pausing bridge...");
        }
    }
    
    // create the device observation notification url
    private String buildDeviceObservationNotificationURL(String ep_name) {
        return this.m_google_cloud_observe_notification_message_url_template
                .replace("__EPNAME__", ep_name)
                .replace("__PROJECT_ID__",this.m_google_cloud_project_id)
                .replace("__CLOUD_REGION__",this.m_google_cloud_region)
                .replace("__REGISTRY_NAME__",this.m_google_cloud_registry_name);
    }
    
    // create the device config change request receive URL
    private String buildDeviceConfigChangeRequestURL(String ep_name) {
        return this.m_google_cloud_device_config_request_url_template
                .replace("__EPNAME__", ep_name)
                .replace("__PROJECT_ID__",this.m_google_cloud_project_id)
                .replace("__CLOUD_REGION__",this.m_google_cloud_region)
                .replace("__REGISTRY_NAME__",this.m_google_cloud_registry_name);
    }
    
    // create the device set state command URL
    private String buildDeviceSetStateCommandURL(String ep_name) {
        return this.m_google_cloud_device_set_state_command_url_template
                .replace("__EPNAME__", ep_name)
                .replace("__PROJECT_ID__",this.m_google_cloud_project_id)
                .replace("__CLOUD_REGION__",this.m_google_cloud_region)
                .replace("__REGISTRY_NAME__",this.m_google_cloud_registry_name);
    }
    
    // get the JwT refresh interval in seconds
    @Override
    public long getJwTRefreshIntervalInSeconds() {
        return this.m_jwt_refresh_interval;
    }
    
    // Get our Google Project ID from the Auth JSON
    private String getProjectID(String auth_json) {
        Map parsed = this.jsonParser().parseJson(auth_json);
        if (parsed != null) {
            return (String)parsed.get("project_id");
        }
        return null;
    }
    
    // Get our Google Application Name from the Auth JSON
    private String getApplicationName(String auth_json) {
        String project_id = this.getProjectID(auth_json);
        if (project_id != null) {
            project_id = project_id.replace("-", " ");
            String parts[] = project_id.split(" ");
            return parts[0];
        }
        return null;
    }
    
    // OVERRIDE: process a new registration in GoogleCloud
    @Override
    protected synchronized void processRegistration(Map data, String key) {
        List endpoints = (List) data.get(key);
        if (endpoints != null && endpoints.size() > 0) {
            if ((this.getCurrentEndpointCount() + endpoints.size()) < this.getMaxNumberOfShadows()) {
                for (int i = 0; endpoints != null && i < endpoints.size(); ++i) {
                    Map endpoint = (Map) endpoints.get(i);

                    // get the device ID and device Type
                    String device_type = Utils.valueFromValidKey(endpoint, "endpoint_type", "ept");
                    String device_id = Utils.valueFromValidKey(endpoint, "id", "ep");

                    // invoke a GET to get the resource information for this endpoint... we will upsert the Metadata when it arrives
                    this.retrieveEndpointAttributes(endpoint,this);
                }
            }
            else {
                // exceeded the maximum number of device shadows
                this.errorLogger().warning("GoogleCloudIOT: Exceeded maximum number of device shadows. Limit: " + this.getMaxNumberOfShadows());
            }
        }
        else {
            // nothing to shadow
            this.errorLogger().info("GoogleCloudIOT: Nothing to shadow (OK).");
        }
    }

    // OVERRIDE: process a re-registration in GoogleCloud
    @Override
    public void processReRegistration(Map data) {
        List notifications = (List) data.get("reg-updates");
        for (int i = 0; notifications != null && i < notifications.size(); ++i) {
            Map entry = (Map) notifications.get(i);
            // DEBUG
            // this.errorLogger().info("GoogleCloud : CoAP re-registration: " + entry);
            if (this.hasSubscriptions((String) entry.get("ep")) == false) {
                // no subscriptions - so process as a new registration
                this.errorLogger().info("GoogleCloudIOT: CoAP re-registration: no subscriptions.. processing as new registration...");
                this.processRegistration(data, "reg-updates");
            }
            else {
                // already subscribed (OK)
                this.errorLogger().info("GoogleCloudIOT: CoAP re-registration: already subscribed (OK)");
            }
        }
    }
    
    // OVERRIDE: process a deregistration (deletion TEST)
    @Override
    public String[] processDeregistrations(Map parsed) {      
        // process the base class...
        String deletions[] = this.processDeregistrationsBase(parsed);
            
        // TEST: We can actually DELETE the device on deregistration to test device-delete before the device-delete message goes live
        if (this.orchestrator().deviceRemovedOnDeRegistration() == true) {
            // processing deregistration as device deletion
            this.errorLogger().info("GoogleCloudIOT: processing de-registration as device deletion (OK).");
            
             // delete the device shadows...
            for (int i = 0; deletions != null && i < deletions.length; ++i) {
                if (deletions[i] != null && deletions[i].length() > 0) {
                    // Unsubscribe... 
                    this.unsubscribe(deletions[i]);
                    
                    // Disconnect MQTT *and* Delete the device shadow...
                    this.deleteDevice(deletions[i]);
                    
                    // remove type
                    this.removeEndpointTypeFromEndpointName(deletions[i]);
                }
            }
        }
        else {
            // not processing deregistration as a device deletion
            this.errorLogger().info("GoogleCloudIOT: Not processing de-registration as device deletion (OK).");
            
            // just disconnect from MQTT
            for (int i = 0; deletions != null && i < deletions.length; ++i) {
                if (deletions[i] != null && deletions[i].length() > 0) {
                    // Unsubscribe...
                    this.unsubscribe(deletions[i]);

                    // Disconnect MQTT *only*
                    this.closeDeviceShadow(deletions[i]);

                    // remove type
                    this.removeEndpointTypeFromEndpointName(deletions[i]);
                }
            }
        }
        
        // return a default 
        return super.processDeregistrations(parsed);
    }
    
    // OVERRIDE: process a registrations-expired 
    @Override
    public String[] processRegistrationsExpired(Map parsed) {
       // process a de-registration event
       return this.processDeregistrations(parsed);
    }
    
    // OVERRIDE: handle device deletions Google Cloud
    @Override
    public String[] processDeviceDeletions(Map parsed) {
        // complete processing in base class...
        String[] deletions = this.processDeviceDeletionsBase(parsed);
        
        // delete the device shadows...
        for (int i = 0; deletions != null && i < deletions.length; ++i) {
            if (deletions[i] != null && deletions[i].length() > 0) {
                // Unsubscribe... 
                this.unsubscribe(deletions[i]);

                // Disconnect from MQTT *and* delete the device shadow...
                this.deleteDevice(deletions[i]);

                // remove type
                this.removeEndpointTypeFromEndpointName(deletions[i]);
            }
        }
        
        // return our deletions
        return deletions;
    }
    
    // GenericSender Implementation: send a message
    @Override
    public void sendMessage(String topic, String message) {
        if (this.m_configured) {
            // DEBUG
            this.errorLogger().info("GoogleCloudIoT(HTTP): sendMessage: TOPIC: " + topic + " MESSAGE: " + message);
            try {
                // Get the endpoint name
                String ep_name = this.getEndpointNameFromNotificationTopic(topic);

                // create the posting URL 
                String url = this.buildDeviceObservationNotificationURL(this.mbedDeviceIDToGoogleDeviceID(ep_name));
                
                // create a JSON struct the Google CloudIoT expects for notifications
                String google_message = this.buildGoogleEventMessagePayload(message);

                // DEBUG
                this.errorLogger().info("GoogleCloudIoT(HTTP): sendMessage; URL: " + url + " MESSAGE: " + message + " TOPIC: " + topic,new Exception());

                // post the message to Google Cloud IoT
                this.httpsPost(ep_name, url, google_message);
                int http_code = this.getLastResponseCode(ep_name);

                // DEBUG
                if (Utils.httpResponseCodeOK(http_code)) {
                    // SUCCESS
                    this.errorLogger().info("GoogleCloudIoT(HTTP): message: " + message + " sent to device: " + ep_name + " SUCCESSFULLY. Code: " + http_code);
                }
                else if (http_code == 403 || http_code == 404) {
                    // FAILURE - forbidden/not found
                    this.errorLogger().warning("GoogleCloudIoT(HTTP): message: " + message + " send to device: " + ep_name + " FAILED (forbidden/not found). Code: " + http_code);
                    
                    // attempt to re-create the device - we will re-create it on the next re-registration...
                    this.errorLogger().warning("GoogleCloudIoT(HTTP): removing stale device sahdow: " + ep_name);
                    this.closeDeviceShadow(ep_name);
                }
                else {
                    // FAILURE - unknown
                    this.errorLogger().warning("GoogleCloudIoT(HTTP): message: " + message + " send to device: " + ep_name + " FAILED. Code: " + http_code);
                }
            }
            catch (Exception ex) {
                this.errorLogger().warning("GoogleCloudIoT(HTTP): Exception in sendMessage: " + ex.getMessage(),ex);
            }
        }
        else {
            // not configured
            this.errorLogger().info("GoogleCloudIoT(HTTP): Google CloudIoT Auth Token is UNCONFIGURED. Please configure and restart the bridge (OK).");
        }
    }
    
    // send the API Response back through the topic
    private void sendApiResponse(String topic, ApiResponse response) {
        // DEBUG
        this.errorLogger().info("GoogleCloudIoT(sendApiResponse): TOPIC: " + topic + " RESPONSE: " + response.createResponseJSON());
        
        // publish via sendMessage()
        this.sendMessage(topic, response.createResponseJSON());
    }
    
    // GoogleCloud Specific: CoAP command handler - processes CoAP commands coming over MQTT channel
    @Override
    public void onMessageReceive(String ep_name, String message) {
        // DEBUG
        this.errorLogger().info("GoogleCloudIOT: CoAP Command message to process: EP: " + ep_name + " message: " + message);
        
        // parse the topic to get the endpoint type
        String ep_type = this.getEndpointTypeFromEndpointName(ep_name);
        
        // process any API requests...
        if (this.isApiRequest(message)) {
            // use a fudged topic
            String fudged_topic = this.createFudgedTopic(ep_name);
            
            // process the message
            this.sendApiResponse(fudged_topic,this.processApiRequestOperation(message));
            
            // return as we are done with the API request... no AsyncResponses necessary for raw API requests...
            return;
        }
        
        // DEBUG
        this.errorLogger().info("GoogleCloudIOT: NOT an API request... continuing...");

        // pull the CoAP Path URI from the message itself... its JSON... 
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        String uri = this.getCoAPURI(message);

        // pull the CoAP Payload from the message itself... its JSON... 
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        String value = this.getCoAPValue(message);
        
        // Get the payload
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        String payload = this.getCoAPPayload(message);

        // pull the CoAP verb from the message itself... its JSON... (PRIMARY)
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        String coap_verb = this.getCoAPVerb(message);

        // if the ep_name is wildcarded... get the endpoint name from the JSON payload
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get" }
        if (ep_name == null || ep_name.length() <= 0 || ep_name.equalsIgnoreCase("+")) {
            ep_name = this.getCoAPEndpointName(message);
        }

        // if there are mDC/mDS REST options... lets add them
        // format: { "path":"/303/0/5850", "new_value":"0", "ep":"mbed-eth-observe", "coap_verb": "get", "options":"noResp=true" }
        String options = this.getRESTOptions(message);
        
        // DEBUG
        this.errorLogger().info("GoogleCloudIOT(onMessageReceive): processing op: VERB: " + coap_verb + " EP: " + ep_name + " URI: " + uri + " VALUE: " + value + " OPTIONS: " + options, new Exception());
        
        // dispatch the CoAP resource operation request to mbed Cloud
        String response = this.orchestrator().processEndpointResourceOperation(coap_verb, ep_name, uri, value, options);

        // examine the response
        if (response != null && response.length() > 0) {
            // SYNC: We only process AsyncResponses from GET verbs... we dont sent HTTP status back through GoogleCloud.
            this.errorLogger().info("GoogleCloudIOT(CoAP Command): Response: " + response);

            // AsyncResponse detection and recording...
            if (this.isAsyncResponse(response) == true) {
                // CoAP GET and PUT provides AsyncResponses...
                if (coap_verb.equalsIgnoreCase("get") == true || coap_verb.equalsIgnoreCase("put") == true) {
                    // DEBUG
                    this.errorLogger().info("GoogleCloudIoT(HTTP): Recording ASYNC RESPONSE: " + response);
                    
                    // its an AsyncResponse.. so record it...
                    String fudged_topic = this.createFudgedTopic(ep_name);
                    this.recordAsyncResponse(response, coap_verb, fudged_topic, message, ep_name, uri);
                }
                else {
                    // we ignore AsyncResponses to PUT,POST,DELETE
                    this.errorLogger().info("GoogleCloudIoT(HTTP): Ignoring AsyncResponse for " + coap_verb + " (OK).");
                }
            }
            else if (coap_verb.equalsIgnoreCase("get")) {                
                // not an AsyncResponse... so just emit it immediately... only for GET...
                this.errorLogger().info("GoogleCloudIoT(HTTP): Response: " + response + " from GET... creating observation...");

                // we have to format as an observation...
                String observation = this.createObservation(coap_verb, ep_name, uri, payload, value);

                // DEBUG
                this.errorLogger().info("GoogleCloudIoT(HTTP): Sending Observation (GET): " + observation);

                // We send this is a state change to Google Cloud IoT
                String url = this.buildDeviceSetStateCommandURL(this.mbedDeviceIDToGoogleDeviceID(ep_name));
                
                // Encode the data into a payload
                String google_message = this.buildGoogleEventMessagePayload(observation);
                
                // POST to Google CloudIoT
                this.httpsPost(ep_name, url, payload);
                int http_code = this.getLastResponseCode(ep_name);
                
                // DEBUG
                if (http_code < 300) {
                    // SUCCESS
                    this.errorLogger().info("GoogleCloudIoT(HTTP): observation sent SUCCESSFULLY. CODE: " + http_code);
                }
                else {
                    // FAILURE
                    this.errorLogger().warning("GoogleCloudIoT(HTTP): observation send FAILED. CODE: " + http_code + " URL: " + url);
                }
            }
        }
    }
    
    // close down the device shadow
    private void closeDeviceShadow(String device) {
        // DEBUG
        this.errorLogger().warning("GoogleCloudIOT: Closing down device: " + device + "...");

        // stop and remove the device listener
        this.removeDeviceListener(device);
        
        // stop the refresher thread
        this.stopJwTRefresherThread(device);

        // stop the listener thread for this device
        this.stopListenerThread(device);
        
        // remove the auth data
        this.m_endpoint_auth_data.remove(device);
        
        // remove the config version tracking
        this.m_device_config_versions.remove(device);
        
         // DEBUG
        this.errorLogger().warning("GoogleCloudIOT: device: " + device + " closed down SUCCESSFULLY.");
    }

    /**
     * process device deletion
     * @param device
     * @return
     */
    @Override
    protected synchronized Boolean deleteDevice(String device) {
        if (this.m_device_manager != null && device != null && device.length() > 0) {
            // close down shadow
            this.closeDeviceShadow(device);
            
             // DEBUG
            this.errorLogger().info("GoogleCloudIOT: deleting device: " + device + " from Google CloudIoT...");
            
            // remove the device from GoogleCloud
            if (this.m_device_manager.deleteDevice(device) == false) {
                // unable to delete the device shadow from Google CloudIoT
                this.errorLogger().warning("GoogleCloudIOT: WARNING: Unable to delete device " + device + " from Google CloudIoT!");
            }    
            else {
                // successfully deleted the device shadow from Google CloudIoT
                this.errorLogger().warning("GoogleCloudIOT: Device " + device + " deleted from Google CloudIoT SUCCESSFULLY.");
            }
        }
        
        // aggressive deletion
        return true;
    }
    
    // create our specific Google Cloud JWT for a device
    public String createGoogleCloudJWT(String ep_name) throws IOException {
        try {
            // use the appropriate keyfile
            Date now = new Date();
            long expiration_seconds = (now.getTime()/1000) + this.m_jwt_expiration_secs; // 23 hours from now... expire.
            Date expire_date = new Date(expiration_seconds*1000); // must be in ms
            JwtBuilder jwtBuilder =
                Jwts.builder()
                    .setIssuedAt(now)
                    .setExpiration(expire_date)
                    .setAudience(this.m_google_cloud_project_id);

            byte[] privKey = Utils.readRSAKeyforDevice(this.errorLogger(),this.m_keystore_rootdir, ep_name, true); // priv key read
            if (privKey != null && privKey.length > 1) {
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privKey);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return jwtBuilder.signWith(SignatureAlgorithm.RS256, kf.generatePrivate(spec)).compact();
            }
            else {
                // invalid key read
                this.errorLogger().warning("GoogleCloudIOT: WARNING: input key is null or has length 1");
            }
        }
        catch (InvalidKeySpecException | NoSuchAlgorithmException ex) {
            // error creating JWT
            this.errorLogger().critical("GoogleCloudIOT: Exception in creating JWT: " + ex.getMessage());
        }
        return null;
    }
    
    // Refresh the JwT for a given endpoint
    @Override
    public void refreshJwTForEndpoint(String ep_name) {
        try {
            // DEBUG
            this.errorLogger().info("GoogleCloudIOT: Refreshing JwT for endpoint: " + ep_name + "...");
            
            // get or init our auth data for this endpoint
            HashMap<String, Object> endpoint_auth_data = this.checkAndInitEndpointAuthData(ep_name);

            // create a new JwT and store it as the HTTP Auth Token
            endpoint_auth_data.put("auth_token",this.createGoogleCloudJWT(ep_name));
            
            // set the new auth data for this endpoint
            this.m_endpoint_auth_data.put(ep_name,endpoint_auth_data);
        }
        catch(IOException ex) {
            // error creating JWT
            this.errorLogger().critical("GoogleCloudIOT: Exception in refreshing JWT: " + ex.getMessage());
        }
    }
    
    // Start the JwT refresher thread
    private void startJwTRefresherThread(String ep_name) {
        // make sure we only have 1 refresher thread...
        this.stopJwTRefresherThread(ep_name);
        
        // start a JwT refresher thread...
        JwTRefresherThread jwt_refresher = new JwTRefresherThread(this,ep_name);
        this.m_jwt_refesher_thread_list.put(ep_name,jwt_refresher);
        jwt_refresher.start();
    }
    
    // End the JwT refresher thread
    public void stopJwTRefresherThread(String ep_name) {
        JwTRefresherThread doomed = this.m_jwt_refesher_thread_list.get(ep_name);
        if (doomed != null) {
            // DEBUG
            this.errorLogger().warning("GoogleCloudIOT: Stopping JwT Refresher for: " + ep_name);
            
            // remove from ThreadList
            this.m_jwt_refesher_thread_list.remove(ep_name);
            
            try {
                // stop the event loop in the thread
                doomed.haltThread();
            }
            catch (Exception ex) {
                // silent
            }
        }
    }
    
    // AsyncResponse response processor
    @Override
    public synchronized boolean processAsyncResponse(Map endpoint) {
        // with the attributes added, we finally create the device in Google CloudIoT
        this.completeNewDeviceRegistration(endpoint);    

        // return our processing status
        return true;
    }
    
    // have we already seen this version config message?
    private boolean alreadySeenConfig(String ep_name, int version) {
        if (ep_name != null && version > 0) {
            Integer ep_version = this.m_device_config_versions.get(ep_name);
            if (ep_version != null && version == ep_version) {
                return true;
            }
        }
        return false;
    }
    
    // get the next message (these are device command messages from Google Cloud IoT --> Pelion)
    private String getNextMessage(String ep_name) { 
        String message = null;
        boolean continue_polling = true;
        int version = 0;
        Integer int_version = version;
                
        // create the URL
        String url = this.buildDeviceConfigChangeRequestURL(this.mbedDeviceIDToGoogleDeviceID(ep_name));
        
        while(continue_polling == true) {
            // dispatch the GET and collect the result - with message ACK immediately to Google Cloud IoT
            String google_message = this.httpsGet(ep_name,url);
            int http_code = this.getLastResponseCode(ep_name);
            
            // Parse the Google message
            Map parsed = this.tryJSONParse(google_message);
            try {
                String str_version = (String)parsed.get("version");
                if (str_version != null && str_version.length() > 0) {
                    int_version = Integer.parseInt(str_version);
                    version = int_version;
                }
            }
            catch (NumberFormatException ex) {
                // formatting exception.. use default of 0
            }
            
            // have we already seen this version?
            if (this.alreadySeenConfig(ep_name, version) == false) {
                // DEBUG
                this.errorLogger().info("GoogleCloudIoT(HTTP): getNextMessage: URL: " + url + " CODE: " + http_code + " MESSAGE: " + google_message);

                // if we have a message... process it.
                if (google_message != null && google_message.length() > 0) {
                    // parse the Google message 
                    message = this.parseGoogleMessage(google_message);

                    // DEBUG
                    this.errorLogger().info("GoogleCloudIoT(HTTP): getNextMessage: Config Change Request: " + message + " CODE: " + http_code);
                }
                
                // we are done polling
                continue_polling = false;
                
                // save this version
                this.m_device_config_versions.put(ep_name,int_version);
            }
            else {
                // we've already seen this version... so continue polling
            }
        }
        
        // return the message
        return message;
    }
    
    // parse an inbound google message
    private String parseGoogleMessage(String google_message) {
        try {
            // Format: {"binaryData":"<Base64EncodedData>"}
            Map parsed = this.orchestrator().getJSONParser().parseJson(google_message);
            String b64_message = (String)parsed.get("binaryData");
            return new String(Base64.decodeBase64(b64_message),"UTF-8");
        }
        catch(UnsupportedEncodingException ex) {
            // error in parsing google message
            this.errorLogger().warning("GoogleCloudIoT(HTTP): Exception in parseGoogleMessage: " + ex.getMessage());
        }
        return null;
    }
    
    // poll for and process device command messages
    @Override
    public void pollAndProcessDeviceMessages(HttpTransport http,String ep_name) {
        // Get the next message
        String message = this.getNextMessage(ep_name);
        if (message != null && message.length() > 0) {
            // DEBUG
            this.errorLogger().info("GoogleCloudIoT(pollAndProcessDeviceMessages): EP: " + ep_name + " Config Change Request: " + message);
            
            // parse and process the configuration change request message
            this.onMessageReceive(ep_name, message);
        }
        else {
            // No message to process... OK
            this.errorLogger().info("GoogleCloudIoT(HTTP): EP: " + ep_name + " No config change request to process. (OK)");
        }
    }

    // we have to override the creation of the authentication hash.. it has to be dependent on a given endpoint name
    @Override
    public String createAuthenticationHash() {
        return Utils.createHash(this.prefValue("google_cloud_auth_json", this.m_suffix));
    }
    
    // mbed endpoint ID to google device ID
    private String mbedDeviceIDToGoogleDeviceID(String ep_name) {
        return this.m_device_manager.mbedDeviceIDToGoogleDeviceID(ep_name);
    }
    
    // google device ID to mbed endpoint ID
    private String googleDeviceIDToMbedDeviceID(String device_id) {
        return this.m_device_manager.googleDeviceIDToMbedDeviceID(device_id);
    }
    
    // complete processing of adding the new device
    @Override
    public void completeNewDeviceRegistration(Map device) {
        if (this.m_configured) {
            if (this.m_device_manager != null) {
                // get the device ID and device Type
                String device_type = Utils.valueFromValidKey(device, "endpoint_type", "ept");
                String device_id = Utils.valueFromValidKey(device, "id", "ep");
                
                // check if we already have auth creds for this device twin... if we do, it already exists...
                if (this.m_endpoint_auth_data.get(device_id) == null) {
                    // create the device shadow/twin
                    boolean ok = this.m_device_manager.registerNewDevice(device);
                    if (ok) {
                        // add our device type
                        this.setEndpointTypeFromEndpointName(device_id, device_type);
                        this.errorLogger().warning("GoogleCloudIoT(completeNewDeviceRegistration): Device Shadow: " + device_id + " creation SUCCESS");

                        // create our auth token for this new device
                        this.checkAndInitEndpointAuthData(device_id);

                        // Create and start our device listener thread for this device
                        this.createDeviceListener(device_id);
                    }
                    else {
                        this.errorLogger().warning("GoogleCloudIoT(completeNewDeviceRegistration): Device Shadow: " + device_id + " creation FAILURE");
                    }
                }
                else {
                    // device already exists (OK)
                    this.errorLogger().info("GoogleCloudIoT(completeNewDeviceRegistration): Device Shadow: " + device_id + " already exists (OK)");
                }
            }
            else {
                this.errorLogger().warning("GoogleCloudIoT(completeNewDeviceRegistration): DeviceManager is NULL. Shadow Device creation FAILURE: " + device);
            }
        }
        else {
            // not configured
            this.errorLogger().warning("GoogleCloudIoT(completeNewDeviceRegistration): Google CloudIoT Auth Token is UNCONFIGURED. Please configure and restart the bridge (OK).");
        }
    }
    
    // OVERRIDE: initListener() needs to accomodate a MQTT connection for each endpoint
    @Override
    @SuppressWarnings("empty-statement")
    public void initListener() {
        // unused
    }

    // OVERRIDE: stopListener() needs to accomodate a MQTT connection for each endpoint
    @Override
    @SuppressWarnings("empty-statement")
    public void stopListener() {
        // unused
    }
    
    // create our CloudIoT instance
    private CloudIot createCloudIoTInstance() {
        CloudIot inst = null;
        
        // Log into Google Cloud
        this.m_google_cloud_logged_in = this.googleCloudLogin(this.m_google_cloud_project_id, this.m_google_cloud_auth_json);
        if (this.m_google_cloud_logged_in == true) {
            try {
                // JSON factory
                JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

                // setup the Http wrapper
                HttpRequestInitializer init = new RetryHttpInitializerWrapper(this.m_credential);
            
                // create the CloudIot instance
                inst = new CloudIot.Builder(GoogleNetHttpTransport.newTrustedTransport(),jsonFactory, init)
                        .setApplicationName(this.m_google_cloud_application_name)
                        .build();
            } 
            catch (GeneralSecurityException | IOException ex) {
                this.errorLogger().critical("GoogleCloudIOT: Unable to create CloudIot instance: " + ex.getMessage());
                inst = null;
            }
        }
        
        // return our instance
        return inst;
    }
    
    // create our Pubsub instance
    private Pubsub createPubSubInstance() {
        Pubsub inst = null;
        
        // only if logged in...
        if (this.m_google_cloud_logged_in == true) {
            try {
                // JSON factory
                JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
                
                // setup the Http wrapper
                HttpRequestInitializer init = new RetryHttpInitializerWrapper(this.m_credential);
                
                // create the Pubsub instance
                inst = new Pubsub.Builder(GoogleNetHttpTransport.newTrustedTransport(),jsonFactory, init)
                                 .setApplicationName(this.m_google_cloud_application_name)
                                 .build();
            }
            catch (GeneralSecurityException | IOException ex) {
                this.errorLogger().critical("GoogleCloudIOT: Unable to create Pubsub instance: " + ex.getMessage());
                inst = null;
            }
        }
        
        // return our instance
        return inst;
    }
    
    // log into the Google Cloud as a Service Account
    private boolean googleCloudLogin(String project_id,String auth_json) {
        boolean success = false;
        String edited_auth_json = null;
        
        try {
            // announce login
            this.errorLogger().info("GoogleCloudIOT: logging into project_id: " + project_id + "...");
            
            // remove \\00A0 as it can be copied during config setting of the auth json by the configurator...
            // hex(A0) = dec(160)... just replace with an ordinary space... that will make Google's JSON parser happy...
            edited_auth_json = com.arm.pelion.bridge.core.Utils.replaceAllCharOccurances(auth_json,(char)160,' ');
            
            // DEBUG
            //this.errorLogger().info("googleCloudLogin():AUTH:" + edited_auth_json);
            
            // Build service account credential.
            this.m_credential = GoogleCredential.fromStream(new ByteArrayInputStream(edited_auth_json.getBytes()));
            
            // add scopes
            if (this.m_credential.createScopedRequired()) {
                this.m_credential = this.m_credential.createScoped(PubsubScopes.all());
            }
            
            // success!
            success = true;
            
            // DEBUG
            this.errorLogger().warning("GoogleCloudIOT LOGIN SUCCESSFUL. project_id: " + project_id);
        }
        catch (com.google.api.client.googleapis.json.GoogleJsonResponseException ex) {
            // caught exception during login
            this.errorLogger().warning("GoogleCloudIOT: Unable to log into Google Cloud: " + ex.getMessage());
        }
        catch (IOException ex) {
            // caught exception during login
            this.errorLogger().warning("GoogleCloudIOT: Unable to log into Google Cloud: " + ex.getMessage());
            success = false;
        }
        
        // return our status
        return success;
    }
    
     // get and init our endpoint auth data
    private HashMap<String,Object> checkAndInitEndpointAuthData(String ep_name) {
        // check if we have already created our data for this endpoint
        HashMap<String,Object> endpoint_auth_data = this.m_endpoint_auth_data.get(ep_name);
        if (endpoint_auth_data == null) {
            // does not exist yet... so initialize one and set it...
            endpoint_auth_data = new HashMap<>();
            
            // each endpoint has its own instance of HttpTransport...
            endpoint_auth_data.put("http_transport", new HttpTransport(this.errorLogger(),this.preferences()));
            
            try {
                // create our initial JwT Token
                endpoint_auth_data.put("auth_token", this.createGoogleCloudJWT(ep_name));
                
                // put the new entry into our list
                this.m_endpoint_auth_data.put(ep_name,endpoint_auth_data);
            }
            catch (IOException ex) {
                // error creating JwT
                this.errorLogger().warning("GoogleCloudIoT(HTTP): Exception caught in checkAndInitEndpointAuthData: " + ex.getMessage());
                endpoint_auth_data = null;
            }
        }
        return endpoint_auth_data;
    }
    
    // create our HTTP-based device listener (checkAndInitEndpointAuthData() must have been called apriori!)
    private void createDeviceListener(String ep_name) {
        if (ep_name != null && ep_name.length() > 0) {
            if (this.m_device_listeners.get(ep_name) == null) {
                // auth data should already be created prior to this method being called 
                HashMap<String,Object> endpoint_auth_data = this.m_endpoint_auth_data.get(ep_name);
                if (endpoint_auth_data != null) {
                    HttpTransport http = (HttpTransport)endpoint_auth_data.get("http_transport");
                    this.m_device_listeners.put(ep_name, new HTTPDeviceListener(this,http,ep_name));
                }
                else {
                    // no auth data for the endpoint... 
                    this.errorLogger().warning("GoogleCloudIoT(HTTP): Unable to setup device listner (no AUTH data) for endpoint: " + ep_name);
                }
            }
        }
    }
    
    // remove our HTTP-based device listener
    private void removeDeviceListener(String ep_name) {
        if (ep_name != null && ep_name.length() > 0) {
        HTTPDeviceListener doomed = this.m_device_listeners.get(ep_name);
            if (doomed != null) {
                doomed.halt();
                this.m_device_listeners.remove(ep_name);
            }
        }
    }
    
    // GET specific data to a given URL 
    protected String httpsGet(String ep_name,String url) {
        HashMap<String,Object> endpoint_auth_data = this.m_endpoint_auth_data.get(ep_name);
        if (endpoint_auth_data != null) {
            HttpTransport http = (HttpTransport)endpoint_auth_data.get("http_transport");
            String auth_token = (String)endpoint_auth_data.get("auth_token");
            http.addHeader("cache-control", "no-cache");
            return http.httpsGetApiTokenAuth(url, auth_token, null, "application/json");
        }
        else {
            // no auth data for the endpoint... 
            this.errorLogger().warning("GoogleCloudIoT(HTTP): httpsGet() FAILED (no AUTH data) for endpoint: " + ep_name);
        }
        return null;
    }

    // PUT specific data to a given URL (with data)
    protected String httpsPut(String ep_name, String url, String payload) {
        HashMap<String,Object> endpoint_auth_data = this.m_endpoint_auth_data.get(ep_name);
        if (endpoint_auth_data != null) {
            HttpTransport http = (HttpTransport)endpoint_auth_data.get("http_transport");
            String auth_token = (String)endpoint_auth_data.get("auth_token");
            http.addHeader("cache-control", "no-cache");
            return http.httpsPutApiTokenAuth(url, auth_token, payload, "application/json");
        }
        else {
            // no auth data for the endpoint... 
            this.errorLogger().warning("GoogleCloudIoT(HTTP): httpsPut() FAILED (no AUTH data) for endpoint: " + ep_name);
        }
        return null;
    }
    
    // POST specific data to a given URL (with data)
    protected String httpsPost(String ep_name, String url, String payload) {
        HashMap<String,Object> endpoint_auth_data = this.m_endpoint_auth_data.get(ep_name);
        if (endpoint_auth_data != null) {
            HttpTransport http = (HttpTransport)endpoint_auth_data.get("http_transport");
            String auth_token = (String)endpoint_auth_data.get("auth_token");
            http.addHeader("cache-control", "no-cache");
            return http.httpsPostApiTokenAuth(url, auth_token, payload, "application/json");
        }
        else {
            // no auth data for the endpoint... 
            this.errorLogger().warning("GoogleCloudIoT(HTTP): httpsPost() FAILED (no AUTH data) for endpoint: " + ep_name);
        }
        return null;
    }
    
    // DELETE specific data to a given URL (with data)
    @Override
    public String httpsDelete(String ep_name,String url) {
        HashMap<String,Object> endpoint_auth_data = this.m_endpoint_auth_data.get(ep_name);
        if (endpoint_auth_data != null) {
            HttpTransport http = (HttpTransport)endpoint_auth_data.get("http_transport");
            String auth_token = (String)endpoint_auth_data.get("auth_token");
            http.addHeader("cache-control", "no-cache");
            return http.httpsDelete(url, null, null, null, null);
        }
        else {
            // no auth data for the endpoint... 
            this.errorLogger().warning("GoogleCloudIoT(HTTP): httpsDelete() FAILED (no AUTH data) for endpoint: " + ep_name);
        }
        return null;
    }
    
    // build out our Google CloudIoT payload
    private String buildGoogleEventMessagePayload(String message) {
        return "{\"binary_data\":\"" + Base64.encodeBase64URLSafeString(message.getBytes()) + "\"}";
    }
    
    // get the last response code for a given endpoint
    private int getLastResponseCode(String ep_name) {
        HashMap<String,Object> endpoint_auth_data = this.m_endpoint_auth_data.get(ep_name);
        if (endpoint_auth_data != null) {
            HttpTransport http = (HttpTransport)endpoint_auth_data.get("http_transport");
            return http.getLastResponseCode();
        }
        return 200;
    }
}