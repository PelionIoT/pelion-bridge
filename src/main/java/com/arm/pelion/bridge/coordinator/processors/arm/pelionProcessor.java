/**
 * @file  pelionProcessor.java
 * @brief Peer Processor for the Pelion
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

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.core.ApiResponse;
import com.arm.pelion.bridge.core.Processor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.transport.HttpTransport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.mbed.lwm2m.LWM2MResource;
import com.arm.pelion.bridge.coordinator.processors.interfaces.pelionProcessorInterface;

/**
 * mbed Cloud Peer processor 
 *
 * @author Doug Anson
 */
public class pelionProcessor extends Processor implements Runnable, pelionProcessorInterface, AsyncResponseProcessor {
    // defaulted number of webhook retries
    private static final int PELION_WEBHOOK_RETRIES = 10;                      // 10 retries
    
    // webhook retry wait time in ms..
    private static final int PELION_WEBHOOK_RETRY_WAIT_MS = 2500;              // 2.5 seconds
    
    // amount of time to wait on boot before device discovery
    private static final int DEVICE_DISCOVERY_DELAY_MS = 15000;                // 15 seconds
    
    // default endpoint type
    public static String DEFAULT_ENDPOINT_TYPE = "default";                    // default endpoint type
    
    private HttpTransport m_http = null;
    private String m_pelion_api_hostname = null;
    private int m_pelion_api_port = 0;
    private String m_content_type = null;
    private String m_api_token = null;
    private String m_pelion_cloud_uri = null;
    //private String m_pelion_version = null;
    private long m_device_discovery_delay_ms = DEVICE_DISCOVERY_DELAY_MS;

    // device metadata resource URI from configuration
    private String m_device_manufacturer_res = null;
    private String m_device_serial_number_res = null;
    private String m_device_model_res = null;
    private String m_device_class_res = null;
    private String m_device_description_res = null;
    private String m_device_firmware_info_res = null;
    private String m_device_hardware_info_res = null;
    private String m_device_descriptive_location_res = null;
    
    private String m_device_attributes_path = null;
    private String m_device_attributes_content_type = null;
    
    // Long Poll vs Webhook usage
    private boolean m_using_callback_webhooks = false;
    private boolean m_enable_long_poll = false;
    private String m_long_poll_uri = null;
    private String m_long_poll_url = null;
    private LongPollProcessor m_long_poll_processor = null;
    
    // Webhook establishment retries
    private int m_webook_num_retries = PELION_WEBHOOK_RETRIES;
    
    // defaulted endpoint type
    private String m_def_ep_type = DEFAULT_ENDPOINT_TYPE;
    
    // Webhook establishment retry wait time in ms
    private int m_webhook_retry_wait_ms = PELION_WEBHOOK_RETRY_WAIT_MS;
    
    // Option - delete a device if it deregisters (default FALSE)
    private boolean m_delete_device_on_deregistration = false;
    
    // Pelion Connect API version
    private String m_rest_version = "2";
    
    // Pelion duplicate message detection
    private String m_last_message = null;

    // constructor
    @SuppressWarnings("empty-statement")
    public pelionProcessor(Orchestrator orchestrator, HttpTransport http) {
        super(orchestrator, null);
        this.m_http = http;
        this.m_pelion_api_hostname = orchestrator.preferences().valueOf("mds_address");
        if (this.m_pelion_api_hostname == null || this.m_pelion_api_hostname.length() == 0) {
            this.m_pelion_api_hostname = orchestrator.preferences().valueOf("api_endpoint_address");
        }
        this.m_pelion_api_port = orchestrator.preferences().intValueOf("mds_port");
        this.m_content_type = orchestrator.preferences().valueOf("mds_content_type");
        this.m_last_message = null;
        this.m_webook_num_retries = orchestrator.preferences().intValueOf("mds_webhook_num_retries");
        if (this.m_webook_num_retries <= 0) {
            this.m_webook_num_retries = PELION_WEBHOOK_RETRIES;
        }
        
        // LongPolling Support
        this.m_enable_long_poll = this.prefBoolValue("mds_enable_long_poll");
        this.m_long_poll_uri = this.prefValue("mds_long_poll_uri");
       
        this.m_api_token = this.orchestrator().preferences().valueOf("mds_api_token");
        if (this.m_api_token == null || this.m_api_token.length() == 0) {
            // new key to use..
            this.m_api_token = this.orchestrator().preferences().valueOf("api_key");
        }
        
        // display number of webhook setup retries allowed
        this.errorLogger().warning("pelionProcessor: Number of webhook retries set at: " + this.m_webook_num_retries);

        // get the device attributes path
        this.m_device_attributes_path = orchestrator.preferences().valueOf("mds_device_attributes_path");

        // get the device attributes content type
        this.m_device_attributes_content_type = orchestrator.preferences().valueOf("mds_device_attributes_content_type");

        // initialize the default type of URI for contacting mbed Cloud
        this.setupPelionCloudURI();
       
        // configure the callback type based on the version of mDS (only if not using long polling)
        if (this.longPollEnabled() == false) {
            this.m_using_callback_webhooks = true;
        }
        
        // default device type in case we need it
        this.m_def_ep_type = orchestrator.preferences().valueOf("mds_def_ep_type");
        if (this.m_def_ep_type == null || this.m_def_ep_type.length() <= 0) {
            this.m_def_ep_type = DEFAULT_ENDPOINT_TYPE;
        }

        // init the device metadata resource URI's
        this.initDeviceMetadataResourceURIs();
                
        // configuration for allowing de-registration messages to remove device shadows...or not.
        this.m_delete_device_on_deregistration = this.prefBoolValue("mds_remove_on_deregistration");
        if (this.m_delete_device_on_deregistration == true) {
            orchestrator.errorLogger().warning("pelionProcessor: device removal on deregistration ENABLED");
        }
        else {
            orchestrator.errorLogger().warning("pelionProcessor: device removal on deregistration DISABLED");
        }
        
        // OVEERRIDE - long polling vs. Webhook
        this.longPollOverrideSetup();
    }
    
    // override use of long polling vs. webhooks for notifications
    private void longPollOverrideSetup() {
        if (this.longPollEnabled()) {
            // DEBUG
            this.errorLogger().warning("pelionProcessor: Long Poll Override ENABLED. Using Long Polling (webhook DISABLED)");

            // override use of long polling vs webhooks for notifications
            this.m_long_poll_url = this.constructLongPollURL();

            // start the Long polling thread...
            this.startLongPolling();
        }
    }
    
    // Long polling enabled or disabled?
    private boolean longPollEnabled() {
        return (this.m_enable_long_poll == true && this.m_long_poll_uri != null && this.m_long_poll_uri.length() > 0);
    }
    
    // get the long polling URL
    public String longPollURL() {
        return this.m_long_poll_url;
    }
    
    // build out the long poll URL
    private String constructLongPollURL() {
        String url = this.createBaseURL() + "/" + this.m_long_poll_uri;
        this.errorLogger().info("constructLongPollURL: Long Poll URL: " + url);
        return url;
    }

    // start the long polling thread
    private void startLongPolling() {
        // now long poll
        if (this.m_long_poll_processor == null) {
            this.m_long_poll_processor = new LongPollProcessor(this);
            this.m_long_poll_processor.startPolling();
        }
    }
    
    // sanitize the endpoint type
    private String sanitizeEndpointType(String ept) {
        if (ept == null || ept.length() == 0) {
            return this.m_def_ep_type;
        }
        return ept;
    }
    
    // process an API request operation
    @Override
    public ApiResponse processApiRequestOperation(String uri,String data,String options,String verb,int request_id,String api_key,String caller_id,String content_type) {
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
    
    // device removal on deregistration?
    @Override
    public boolean deviceRemovedOnDeRegistration() {
        return this.m_delete_device_on_deregistration;
    }

    // initialize the device metadata resource URIs
    private void initDeviceMetadataResourceURIs() {
        this.m_device_manufacturer_res = this.prefValue("mds_device_manufacturer_res");
        this.m_device_serial_number_res = this.prefValue("mds_device_serial_number_res");
        this.m_device_model_res = this.prefValue("mds_device_model_res");
        this.m_device_class_res = this.prefValue("mds_device_class_res");
        this.m_device_description_res = this.prefValue("mds_device_description_res");
        this.m_device_firmware_info_res = this.prefValue("mds_device_firmware_info_res");
        this.m_device_hardware_info_res = this.prefValue("mds_device_hardware_info_res");
        this.m_device_descriptive_location_res = this.prefValue("mds_device_descriptive_location_res");
    }

    // setup the mbed device server default URI
    private void setupPelionCloudURI() {
        this.m_pelion_cloud_uri = "https://";
        this.m_pelion_api_port = 443;
    }

    // our the mbed Cloud notifications coming in over the webhook validatable?
    private Boolean validatableNotifications() {
        return this.m_using_callback_webhooks;
    }
    
    // validate the notification
    private Boolean validateNotification(HttpServletRequest request) {
        if (request != null) {
            boolean validated = false;
            if (this.validatableNotifications() == true && request.getHeader("Authentication") != null) {
                String calc_hash = this.orchestrator().createAuthenticationHash();
                String header_hash = request.getHeader("Authentication");
                validated = Utils.validateHash(header_hash, calc_hash);

                // DEBUG
                if (!validated) {
                    this.errorLogger().warning("validateNotification: failed: calc: " + calc_hash + " header: " + header_hash);
                }

                // return validation status
                return validated;
            }
            else {
                // using push-url. No authentication possible.
                return true;
            }
        }
        else {
            // no request - so assume we are validated
            return true;
        }
    }

    // create any authentication header JSON that may be necessary
    @SuppressWarnings("empty-statement")
    private Map createWebhookHeaderAuthJSON() {
        // Create a hashmap and fill it
        HashMap<String,String> map = new HashMap<>();
        map.put("Authentication",this.orchestrator().createAuthenticationHash());
        return map;
    }

    // create our webhook URL that we will get called back on...
    private String createWebhookURL() {
        String url = null;

        String local_ip = Utils.getExternalIPAddress(this.prefBoolValue("mds_use_gw_address"), this.prefValue("mds_gw_address"));
        int local_port = this.prefIntValue("mds_gw_port") + 1;
        String notify_uri = this.prefValue("mds_gw_context_path") + this.prefValue("mds_gw_events_path");

        // build and return the webhook callback URL
        return this.m_pelion_cloud_uri + local_ip + ":" + local_port + notify_uri;
    }

    // mbed Cloud: create the dispatch URL for changing the notification webhook URL
    private String createWebhookDispatchURL() {
        return this.createBaseURL() + "/notification/callback";
    }

    // get the currently configured callback URL (public, used by webhook validator)
    public String getWebhook() {
        String url = null;
        String headers = null;

        // create the dispatch URL
        String dispatch_url = this.createWebhookDispatchURL();

        // Issue GET and look at the response
        String json = this.httpsGet(dispatch_url);
        try {
            if (json != null && json.length() > 0) {
                // Callback API used: parse the JSON
                Map parsed = (Map) this.parseJson(json);
                url = (String) parsed.get("url");

                // headers are optional...
                try {
                    headers = (String) parsed.get("headers");
                }
                catch (Exception json_ex) {
                    headers = "";
                }

                // DEBUG
                this.orchestrator().errorLogger().info("getNotificationCallbackURL(callback): url: " + url + " headers: " + headers + " dispatch: " + dispatch_url);
            }
            else {
                // no response received back from mbed Cloud
                this.orchestrator().errorLogger().warning("getNotificationCallbackURL: no response recieved from dispatch: " + dispatch_url);
            }
        }
        catch (Exception ex) {
            this.orchestrator().errorLogger().warning("getNotificationCallbackURL: exception: " + ex.getMessage() + ". json=" + json);
        }

        return url;
    }

    // determine if our callback URL has already been set
    private boolean webhookSet(String target_url) {
        return this.webhookSet(target_url, false);
    }

    // determine if our callback URL has already been set
    private boolean webhookSet(String target_url, boolean skip_check) {
        String current_url = this.getWebhook();
        this.errorLogger().info("pelionProcessor: current_url: " + current_url + " target_url: " + target_url);
        boolean is_set = (target_url != null && current_url != null && target_url.equalsIgnoreCase(current_url));
        if (is_set == true && skip_check == false) {
            // for Connector, lets ensure that we always have the expected Auth Header setup. So, while the same, lets delete and re-install...
            this.errorLogger().info("pelionProcessor: deleting existing webhook URL...");
            this.removeWebhook();
            this.errorLogger().info("pelionProcessor: re-establishing webhook URL...");
            is_set = this.setWebhook(target_url, skip_check); // skip_check, go ahead and assume we need to set it...
            if (is_set) {
                // SUCCESS
                this.errorLogger().info("pelionProcessor: re-checking that webhook URL is properly set...");
                current_url = this.getWebhook();
                is_set = (target_url != null && current_url != null && target_url.equalsIgnoreCase(current_url));
            }
            else {
                // ERROR
                this.errorLogger().info("pelionProcessor: re-checking that webhook URL is properly set...");
            }
        }
        return is_set;
    }

    // remove the mbed Cloud Connector Notification Callback webhook
    public void removeWebhook() {
        // create the dispatch URL
        String dispatch_url = this.createWebhookDispatchURL();

        // delete the callback URL (SSL)
        this.httpsDelete(dispatch_url);
    }

    // reset the mbed Cloud Notification Callback URL
    @Override
    public boolean resetWebhook() {        
        // delete the webhook
        this.removeWebhook();
        
        // set the webhook
        return setWebhook();
    }

    // set our mbed Cloud Notification Callback URL
    @Override
    public boolean setWebhook() {
        boolean ok = false;
        if (this.longPollEnabled() == false) {
            for(int i=0;i<this.m_webook_num_retries && ok == false;++i) {
                this.errorLogger().warning("pelionProcessor: Setting up webhook to mbed Cloud...");
                String target_url = this.createWebhookURL();
                ok = this.setWebhook(target_url);

                // EXPERIMENTAL - test for bulk subscriptions setting
                if (ok) {
                    // bulk subscriptions enabled
                    this.errorLogger().warning("pelionProcessor: Webhook to mbed Cloud set. Enabling bulk subscriptions.");
                    ok = this.setupBulkSubscriptions();
                    if (ok) {
                        // scan for devices now
                        this.errorLogger().warning("pelionProcessor: Initial scan for mbed devices...");
                        this.startDeviceDiscovery();
                    }
                    else {
                        // ERROR
                        this.errorLogger().warning("pelionProcessor: Webhook not setup. Not scanning for devices yet...");
                    }
                }

                // wait a bit if we have failed
                else {
                    // log and wait
                    this.errorLogger().warning("pelionProcessor: Waiting a bit... then retry establishing webhook to mbed Cloud...");
                    Utils.waitForABit(this.errorLogger(), this.m_webhook_retry_wait_ms);
                }
            }

            // Final status
            if (ok) {
                // SUCCESS
                this.errorLogger().warning("pelionProcessor: webhook setup SUCCESS");
            }
            else {
                // FAILURE
                this.errorLogger().critical("pelionProcessor: UNABLE TO SET WEBHOOK. Resetting bridge...");

                // RESET
                this.orchestrator().reset();
            }
        }
        else {
            // not used by long polling
            ok = true;
        }
        
        // return our status
        return ok;
    }
    
    // establish bulk subscription 
    private boolean setupBulkSubscriptions() {
        boolean ok = false;
        
        // DEBUG
        this.errorLogger().warning("pelionProcessor: setting up bulk subscriptions...");
        
        // JSON for the bulk subscription (must be an array)
        String json = "[" + this.createJSONMessage("endpoint-name","*") + "]";
        
        // Create the URI for the bulk subscription PUT
        String url = this.createBaseURL() + "/subscriptions";
        
        // DEBUG
        this.errorLogger().info("pelionProcessor: bulk subscriptions URL: " + url + " DATA: " + json);
        
        // send PUT to establish the bulk subscriptions
        String result = this.httpsPut(url, json, "application/json", this.m_api_token);
        int error_code = this.getLastResponseCode();
        
        // DEBUG
        if (result != null && result.length() > 0) {
            this.errorLogger().info("pelionProcessor: bulk subscriptions setup RESULT: " + result);
        }
        
        // check the setup error code 
        if (error_code == 204) {    // SUCCESS response code: 204
            // success!
            this.errorLogger().warning("pelionProcessor: bulk subscriptions setup SUCCESS: Code: " + error_code);
            ok = true;
        }
        else {
            // failure
            this.errorLogger().warning("pelionProcessor: bulk subscriptions setup FAILED: Code: " + error_code);
        }
        
        // return our status
        return ok;
    }

    // set our mbed Cloud Notification Callback URL
    private boolean setWebhook(String target_url) {
        return this.setWebhook(target_url, true); // default is to check if the URL is already set... 
    }

    // set our mbed Cloud Notification Callback URL
    private boolean setWebhook(String target_url, boolean check_url_set) {
        boolean webhook_set_ok = false; // assume default is that the URL is NOT set... 

        // we must check to see if we want to check that the URL is already set...
        if (check_url_set == true) {
            // go see if the URL is already set.. 
            webhook_set_ok = this.webhookSet(target_url);
        }

        // proceed to set the URL if its not already set.. 
        if (!webhook_set_ok) {
            String dispatch_url = this.createWebhookDispatchURL();
            Map auth_header_json = this.createWebhookHeaderAuthJSON();
            String json = null;

            // build out the callback JSON
            if (auth_header_json == null) {
                json = this.createJSONMessage("url", target_url);
            }
            else {
                HashMap<String,Object> map = new HashMap<>();
                map.put("url",target_url);
                map.put("headers",auth_header_json);
                json = this.createJSONMessage(map);
            }

            // DEBUG
            this.errorLogger().info("pelionProcessor: json: " + json + " dispatch: " + dispatch_url);

            // set the callback URL (SSL)
            this.httpsPut(dispatch_url, json);

            // check that it succeeded
            if (!this.webhookSet(target_url, !check_url_set)) {
                // DEBUG
                this.errorLogger().warning("pelionProcessor: ERROR: unable to set callback URL to: " + target_url);
                
                // not set...
                webhook_set_ok = false;
            }
            else {
                // DEBUG
                this.errorLogger().info("pelionProcessor: notification URL set to: " + target_url + " (SUCCESS)");
                
                // SUCCESS
                webhook_set_ok = true;
            }
        }
        else {
            // DEBUG
            this.errorLogger().info("pelionProcessor: notification URL already set to: " + target_url + " (OK)");
            
            // SUCCESS
            webhook_set_ok = true;
        }
        
        // return our status
        return webhook_set_ok;
    }

    // process device-deletions of endpoints (mbed Cloud only)
    @Override
    public void processDeviceDeletions(String[] endpoints) {
        // XXX TO DO
    }
    
    // process de-registeration of endpoints
    @Override
    public void processDeregistrations(String[] endpoints) {
        for (int i = 0; i < endpoints.length; ++i) {
            // create the endpoint subscription removal URL...
            String url = this.createBaseURL() + "/endpoints/" + endpoints[i];
            this.errorLogger().info("processDeregistrations: sending endpoint subscription removal request: " + url);
            this.httpsDelete(url);
        }
    }
    
    // process registerations-expired of endpoints
    @Override
    public void processRegistrationsExpired(String[] endpoints) {
        // nothing to process for device server
    }
    
    // process the notification
    @Override
    public synchronized void processNotificationMessage(HttpServletRequest request, HttpServletResponse response) {
        // read the request...
        String json = this.read(request);
        if (json != null && json.length() > 0 && json.equalsIgnoreCase("{}") == false) {
            // Check for message duplication... 
            if (this.isDuplicateMessage(json) == false) {
                // record the "last" message
                this.m_last_message = json;
                
                // process and route the mbed Cloud message
                this.processDeviceServerMessage(json, request);
            }
            else {
                // DUPLICATE!  So ignore it
                this.errorLogger().info("processNotificationMessage(mbed Cloud): duplicate message discovered... ignoring...(OK).");
            }
        }
        
        // ALWAYS send the response back as an ACK to mbed Cloud
        this.sendResponseToDeviceServer("application/json;charset=utf-8", request, response, "", "{}");
    }
    
    // check for duplicated messages
    private boolean isDuplicateMessage(String message) {
        if (this.m_last_message != null && message != null && message.length() > 0 && this.m_last_message.equalsIgnoreCase(message) == true) {
            // possible duplicate to previous message
            
            // check for duplicate de-registrations
            if (message.contains("\"de-registrations\":") == true) {
                // two identical de-registrations cannot happen
                return true;
            }
          
            // check for duplicate registrations-expired
            if (message.contains("\"registrations-expired\":") == true) {
                // two identical de-registrations cannot happen
                return true;
            }
            
            // check for duplicate registrations
            if (message.contains("\"registrations\":") == true) {
                // two identical de-registrations cannot happen
                return true;
            }
            
            // check for duplicate reg-updates
            if (message.contains("\"reg-updates\":") == true) {
                // two identical de-registrations cannot happen
                return true;
            }
            
            // we allow for duplicate "notifications" as they dont involve shadow lifecycle changes...
        }
        
        // default is false
        return false;
    }

    // process and route the mbed Cloud message to the appropriate peer method (long poll method)
    public void processDeviceServerMessage(String json) {
        this.processDeviceServerMessage(json, null);
    }

    // process and route the mbed Cloud message to the appropriate peer method
    private void processDeviceServerMessage(String json, HttpServletRequest request) {
        // DEBUG
        this.orchestrator().errorLogger().info("processDeviceServerMessage(mbed Cloud): Received message from mbed Cloud: " + json);

        // tell the orchestrator to call its peer processors with this mbed Cloud message
        try {
            if (json != null && json.length() > 0 && json.equalsIgnoreCase("{}") == false) {
                Map parsed = (Map) this.parseJson(json);
                if (parsed != null) {
                    if (parsed.containsKey("notifications")) {
                        if (this.validateNotification(request)) {
                            // DEBUG
                            this.errorLogger().info("processDeviceServerMessage(mbed Cloud): notification VALIDATED");

                            // validated notification... process it...
                            this.orchestrator().processNotification(parsed);
                        }
                        else {
                            // validation FAILED. Note but do not process...
                            this.errorLogger().warning("processDeviceServerMessage(mbed Cloud): notification validation FAILED. Not processed (OK)");
                        }
                    }

                    // DEBUG
                    this.errorLogger().info("processDeviceServerMessage(mbed Cloud) Parsed: " + parsed);
                    
                    // act on the request...
                    if (parsed.containsKey("registrations")) {
                        this.orchestrator().processNewRegistration(parsed);
                    }
                    if (parsed.containsKey("reg-updates")) {
                        this.orchestrator().processReRegistration(parsed);
                    }
                    if (parsed.containsKey("de-registrations")) {
                        this.orchestrator().processDeregistrations(parsed);
                    }
                    if (parsed.containsKey("registrations-expired")) {
                        this.orchestrator().processRegistrationsExpired(parsed);
                    }
                    if (parsed.containsKey("async-responses")) {
                        this.orchestrator().processAsyncResponses(parsed);
                    }
                }
                else {
                    // parseJson() failed...
                    this.errorLogger().warning("processDeviceServerMessage(mbed Cloud): unable to parse JSON: " + json);
                }
            }
            else {
                // empty JSON... so not parsed
                this.errorLogger().info("processDeviceServerMessage(mbed Cloud): empty JSON not parsed (OK).");
            }
        }
        catch (Exception ex) {
            // exception during JSON parsing
            this.errorLogger().info("processDeviceServerMessage(mbed Cloud): Exception during notification body JSON parsing: " + json + "... ignoring.", ex);
        }
    }

    // get to endpoint resource subscription 
    public boolean getEndpointResourceSubscriptionStatus(String url) {
        boolean subscribed = false;
        String json = null;
        this.errorLogger().info("getEndpointResourceSubscriptionStatus: getting subscription status: " + url);
        this.httpsGet(url);

        // check the status...
        int status = this.getLastResponseCode();
        status = status - 200;
        if (status >= 0 && status < 100) {
            // 20x response - OK
            subscribed = true;
        }

        // return the result
        return subscribed;
    }

    // process endpoint resource operation request
    @Override
    public String processEndpointResourceOperation(String verb, String ep_name, String uri, String value, String options) {
        String json = null;
        String url = this.createCoAPURL(ep_name, uri);

        // add our options if they are specified
        if (options != null && options.length() > 0 && options.contains("=") == true) {
            // There is no way to validate that these options dont break the request... there may also be security issues here. 
            url += "?" + options;
        }

        if (verb != null && verb.length() > 0) {
            // dispatch the mbed Cloud REST based on CoAP verb received
            if (verb.equalsIgnoreCase(("get"))) {
                this.errorLogger().info("processEndpointResourceOperation: Invoking GET: " + url);
                json = this.httpsGet(url);
                if (json == null) json = "";
            }
            if (verb.equalsIgnoreCase(("put"))) {
                this.errorLogger().info("processEndpointResourceOperation: Invoking PUT: " + url + " DATA: " + value);
                json = this.httpsPut(url, value);
                if (json == null) json = "";
            }
            if (verb.equalsIgnoreCase(("post"))) {
                this.errorLogger().info("processEndpointResourceOperation: Invoking POST: " + url + " DATA: " + value);
                 json = this.httpsPost(url, value, "plain/text", this.m_api_token);  // nail content_type to "plain/text"
                 if (json == null) json = "";
            }
            if (verb.equalsIgnoreCase(("delete"))) {
                this.errorLogger().info("processEndpointResourceOperation: Invoking DELETE: " + url);
                 json = this.httpsDelete(url, "plain/text", this.m_api_token);      // nail content_type to "plain/text"
                 if (json == null) json = "";
            }
            if (verb.equalsIgnoreCase(("del"))) {
                this.errorLogger().info("processEndpointResourceOperation: Invoking DELETE: " + url);
                json = this.httpsDelete(url, "plain/text", this.m_api_token);      // nail content_type to "plain/text"
                if (json == null) json = "";
            }
        }
        else {
            this.errorLogger().info("processEndpointResourceOperation: ERROR: CoAP Verb is NULL. Not processing: ep: " + ep_name + " uri: " + uri + " value: " + value);
            json = null;
        }

        return json;
    }
    
    // initialize the endpoint's default attributes 
    private void initDeviceWithDefaultAttributes(Map endpoint) {
        this.pullDeviceManufacturer(endpoint);
        this.pullDeviceSerialNumber(endpoint);
        this.pullDeviceModel(endpoint);
        this.pullDeviceClass(endpoint);
        this.pullDeviceDescription(endpoint);
        this.pullDeviceHardwareInfo(endpoint);
        this.pullDeviceLocationDescriptionInfo(endpoint);
        this.pullDeviceCurrentTimeInfo(endpoint);
        this.pullDeviceTotalMemoryInfo(endpoint);
    }

    // determine if a given endpoint actually has device attributes or not... if not, the defaults will be used
    private boolean hasDeviceAttributes(Map endpoint) {
        boolean has_device_attributes = false;

        try {
            // get the list of resources from the endpoint
            List resources = (List) endpoint.get("resources");

            // look for a special resource - /3/0
            if (resources != null && resources.size() > 0) {
                for (int i = 0; i < resources.size() && !has_device_attributes; ++i) {
                    Map resource = (Map) resources.get(i);
                    if (resource != null) {
                        // get the path value
                        String path = (String) resource.get("path");

                        // look for /3/0
                        if (path != null && path.equalsIgnoreCase(this.m_device_attributes_path) == true) {
                            // we have device attributes in this endpoint... go get 'em. 
                            has_device_attributes = true;
                        }
                    }
                }
            }
        }
        catch (Exception ex) {
            // caught exception
            this.errorLogger().info("hasDeviceAttributes: Exception caught: " + ex.getMessage(), ex);
        }
        
        // DEBUG
        if (has_device_attributes == true) {
            this.errorLogger().info("hasDeviceAttributes: HAS DEVICE ATTRIBUTES: " + endpoint);
        }
        else {
            this.errorLogger().info("hasDeviceAttributes: DOES NOT HAVE DEVICE ATTRIBUTES: " + endpoint);
        }

        // return our status
        return has_device_attributes;
    }

    // dispatch GETs to retrieve the actual device attributes
    private void dispatchDeviceAttributeGETs(Map endpoint, AsyncResponseProcessor processor) {
        // Create the Device Attributes URL
        String url = this.createCoAPURL((String) endpoint.get("ep"), this.m_device_attributes_path);

        // DEBUG
        //this.errorLogger().info("ATTRIBUTES: Calling GET to receive: " + url);
        
        // Dispatch and get the response (an AsyncId)
        String json_response = this.httpsGet(url, this.m_device_attributes_content_type, this.m_api_token);

        // record the response to get processed later
        if (json_response != null) {
            this.orchestrator().recordAsyncResponse(json_response, url, endpoint, processor);
        }
    }

    // check and dispatch the appropriate GETs to retrieve the actual device attributes
    private void getActualDeviceAttributes(Map endpoint, AsyncResponseProcessor processor) {
        // dispatch GETs to retrieve the attributes from the endpoint... 
        if (this.hasDeviceAttributes(endpoint)) {
            // dispatch GETs to to retrieve and parse those attributes
            this.dispatchDeviceAttributeGETs(endpoint,processor);
        }
        else {
            // device does not have device attributes... so just use the defaults... 
            AsyncResponseProcessor peer_processor = (AsyncResponseProcessor) endpoint.get("peer_processor");
            if (peer_processor != null) {
                // call the AsyncResponseProcessor within the peer...
                peer_processor.processAsyncResponse(endpoint);
            }
            else {
                // error - no peer AsyncResponseProcessor...
                this.errorLogger().warning("getActualDeviceAttributes: no peer AsyncResponse processor. Device may not get addeded within peer.");
            }
        }
    }

    // parse the device attributes
    private Map parseDeviceAttributes(Map response, Map endpoint) {
        LWM2MResource res = null;
        
        try {
            // Convert the TLV to a LWM2M Resource List...
            List<LWM2MResource> list = Utils.tlvDecodeToLWM2MObjectList(this.errorLogger(),(String) response.get("payload"));
            
            // DEBUG
            //for(int i=0;list != null && i<list.size();++i) {
            //    res = list.get(i);
            //    this.errorLogger().info("parseDeviceAttributes: URI: " + 
            //                            this.m_device_attributes_path + "/" + res.getId().intValue() + " Value: " + res.getStringValue() + "]");
            //}
            
            // /3/0/0
            endpoint.put("meta_mfg", Utils.getLWM2MResourceValueByResourceID(this.errorLogger(),list,0)); 
            
            // /3/0/1
            endpoint.put("meta_model", Utils.getLWM2MResourceValueByResourceID(this.errorLogger(),list,1));
            
            // /3/0/2
            endpoint.put("meta_serial", Utils.getLWM2MResourceValueByResourceID(this.errorLogger(),list,2));
            
            // /3/0/13
            endpoint.put("meta_time", Utils.getLWM2MResourceValueByResourceID(this.errorLogger(),list,13)); 
            
            // /3/0/17
            endpoint.put("meta_type", Utils.getLWM2MResourceValueByResourceID(this.errorLogger(),list,17)); 
            
            // /3/0/18
            endpoint.put("meta_hardware", Utils.getLWM2MResourceValueByResourceID(this.errorLogger(),list,18)); 
            
            // /3/0/21
            endpoint.put("meta_total_mem", Utils.getLWM2MResourceValueByResourceID(this.errorLogger(),list,21)); 
        }
        catch (Exception ex) {
            // exception during TLV parse... 
            this.errorLogger().info("parseDeviceAttributes: Error parsing TLV device attributes... using defaults...OK: " + ex.getMessage(),ex);
        }

        // return the updated endpoint
        return endpoint;
    }

    // callback for device attribute processing... 
    @Override
    public boolean processAsyncResponse(Map response) {
        // DEBUG
        //this.errorLogger().info("processAsyncResponse(MDS): RESPONSE: " + response);

        // Get the originating record
        HashMap<String, Object> record = (HashMap<String, Object>) response.get("orig_record");
        if (record != null) {
            Map orig_endpoint = (Map) record.get("orig_endpoint");
            if (orig_endpoint != null) {
                // Get the peer processor
                AsyncResponseProcessor peer_processor = (AsyncResponseProcessor) orig_endpoint.get("peer_processor");
                if (peer_processor != null) {
                    // parse the device attributes
                    this.errorLogger().info("mbed Cloud: processAsyncResponse: ORIG endpoint: " + orig_endpoint);
                    this.errorLogger().info("mbed Cloud: processAsyncResponse: RESPONSE: " + response);
                    Map endpoint = this.parseDeviceAttributes(response,orig_endpoint);
                    
                    // DEBUG
                    this.errorLogger().info("mbed Cloud: processAsyncResponse: endpoint: " + endpoint);

                    // call the AsyncResponseProcessor within the peer to finalize the device
                    peer_processor.processAsyncResponse(endpoint);
                }
                else {
                    // error - no peer AsyncResponseProcessor...
                    this.errorLogger().warning("processAsyncResponse(MDS): no peer AsyncResponse processor. Device may not get addeded within peer: " + record);
                }
            }
            else {
                // error - no peer AsyncResponseProcessor...
                this.errorLogger().warning("processAsyncResponse(MDS): no peer AsyncResponse processor. Device may not get addeded within peer: " + orig_endpoint);
            }

            // return processed status (defaulted)
            return true;
        }

        // return non-processed
        return false;
    }

    // pull the initial device metadata from mbed Cloud.. add it to the device endpoint map
    @Override
    public void pullDeviceMetadata(Map endpoint, AsyncResponseProcessor processor) {
        // initialize the endpoint with defaulted device attributes
        this.initDeviceWithDefaultAttributes(endpoint);

        // save off the peer processor for later
        endpoint.put("peer_processor", processor);

        // invoke GETs to retrieve the actual attributes (we are the processor for the callbacks...)
        this.getActualDeviceAttributes(endpoint, this);
    }

    // read the requested data from mbed Cloud
    private String read(HttpServletRequest request) {
        try {
            BufferedReader reader = request.getReader();
            String line = reader.readLine();
            StringBuilder buf = new StringBuilder();
            while (line != null) {
                buf.append(line);
                line = reader.readLine();
            }
            return buf.toString();
        }
        catch (IOException ex) {
            // silent
        }
        return null;
    }

    // send the REST response back to mbed Cloud
    private void sendResponseToDeviceServer(String content_type, HttpServletRequest request, HttpServletResponse response, String header, String body) {
        try {
            response.setContentType(content_type);
            response.setHeader("Pragma", "no-cache");
            try (PrintWriter out = response.getWriter()) {
                if (header != null && header.length() > 0) {
                    out.println(header);
                }
                if (body != null && body.length() > 0) {
                    out.println(body);
                }
            }
        }
        catch (IOException ex) {
            this.errorLogger().critical("Unable to send response back to mbed Cloud...", ex);
        }
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
        return this.m_pelion_cloud_uri + this.m_pelion_api_hostname + ":" + this.m_pelion_api_port + version;
    }

    // create the CoAP operation URL
    private String createCoAPURL(String ep_name, String uri) {
        String url = this.createBaseURL() + "/endpoints/" + ep_name + uri;
        return url;
    }

    // build out the query string
    private String buildQueryString(String qs, String key, String value) {
        String updated_qs = qs;

        if (updated_qs != null && key != null && value != null) {
            if (updated_qs.length() == 0) {
                updated_qs = key + "=" + value;
            }
            else if (updated_qs.contains(key) == false) {
                updated_qs = updated_qs + "&" + key + "=" + value;
            }
            else {
                // attempted overwrite of previously set value
                this.errorLogger().warning("attempted overwrite of option: " + key + "=" + value + " in qs: " + updated_qs);
            }
        }

        return updated_qs;
    }

    //
    // The following methods are stubbed out for now - they provide defaulted device metadata info.
    // The actual CoAP Resource URI's are specified in the bridge configuration file and must be the same for all devices.
    // 
    // pull the device manufacturer information
    private void pullDeviceManufacturer(Map endpoint) {
        //this.m_device_manufacturer_res
        endpoint.put("meta_mfg", "ARM");
    }

    // pull the device Serial Number information
    private void pullDeviceSerialNumber(Map endpoint) {
        //this.m_device_serial_number_res
        endpoint.put("meta_serial", "0123456789");
    }

    // pull the device model information
    private void pullDeviceModel(Map endpoint) {
        //this.m_device_model_res
        endpoint.put("meta_model", "mbed");
    }

    // pull the device manufacturer information
    private void pullDeviceClass(Map endpoint) {
        //this.m_device_class_res
        endpoint.put("meta_class", "cortex-m");
    }

    // pull the device manufacturer information
    private void pullDeviceDescription(Map endpoint) {
        //this.m_device_description_res
        endpoint.put("meta_description", "mbed device");
    }

    // pull the device hardware information
    private void pullDeviceHardwareInfo(Map endpoint) {
        //this.m_device_hardware_info_res
        endpoint.put("meta_hardware", "1.0");
    }

    // pull the description location information for the device
    private void pullDeviceLocationDescriptionInfo(Map endpoint) {
        //this.m_device_descriptive_location_res
        endpoint.put("meta_location", "n/a");
    }
    
    // pull the current time from the device
    private void pullDeviceCurrentTimeInfo(Map endpoint) {
        //epoc
        endpoint.put("meta_time",Utils.getUTCTime());  // UTC time
    }
    
    // pull the total device memory information for the device
    private void pullDeviceTotalMemoryInfo(Map endpoint) {
        //this.m_device_descriptive_location_res
        endpoint.put("meta_total_mem", "128K");  // typical min: 128k
    }
    
    // init any device discovery
    @Override
    public void initDeviceDiscovery() {
        if (this.longPollEnabled() == true) {
            this.startDeviceDiscovery();
        }
    }
    
    // start device discovery for device shadow setup
    private void startDeviceDiscovery() {
        this.run();
    }
    
    // setup initial Device Shadows (mbed Cloud only...)
    private void setupExistingDeviceShadows() {
        // query mbed Cloud for the current list of Registered devices
        List devices = this.discoverRegisteredDevices();

        // loop through each device, get resource descriptions...
        HashMap<String,Object> endpoint = new HashMap<>();
        for(int i=0;devices != null && i<devices.size();++i) {
            Map device = (Map)devices.get(i);
            
            // DEBUG
            //this.errorLogger().info("setupExistingDeviceShadows: DEVICE: " + device);

            // sanitize the endpoint type
            device.put("endpoint_type",this.sanitizeEndpointType((String)device.get("endpoint_type")));

            // copy over the relevant portions
            endpoint.put("ep", (String)device.get("id"));
            endpoint.put("ept",(String)device.get("endpoint_type"));

            // DEBUG
            this.errorLogger().warning("pelionProcessor(BOOT): discovered mbed Cloud device ID: " + (String)device.get("id") + " Type: " + (String)device.get("endpoint_type"));

            // now, query mbed Cloud again for each device and get its resources
            List resources = this.discoverDeviceResources((String)device.get("id"));

            // For now, we simply add to each resource JSON, a "path" that mimics the "uri" element... we need to use "uri" once done with Connector
            for(int j=0;resources != null && j<resources.size();++j) {
                Map resource = (Map)resources.get(j);
                resource.put("path", (String)resource.get("uri"));

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
        }
    }
    
    // get the observability of a given resource
    protected boolean isObservableResource(Map resource) {
        String obs_str = (String) resource.get("obs");
        return (obs_str != null && obs_str.equalsIgnoreCase("true"));
    }
    
    // create the registered devices retrieval URL
    private String createRegisteredDeviceRetrievalURL() {
        // create the url to capture all of the registered devices
        String url = this.createBaseURL("/v3") + "/devices" + "?filter=state%3Dregistered" ;

        // DEBUG
        this.errorLogger().info("createRegisteredDeviceRetrievalURL: " + url);
        
        // return the device discovery URL
        return url;
    }
    
    // create the Device Resource Discovery URL 
    private String createDeviceResourceDiscoveryURL(String device) {
        // build out the URL for mbed Cloud Device Resource discovery...
        String url = this.createBaseURL("/v2") + "/endpoints/" +  device;

        // DEBUG
        this.errorLogger().info("createDeviceResourceDiscoveryURL: " + url);
        
        // return the device resource discovery URL
        return url;
    }

    // perform device discovery
    private List discoverRegisteredDevices() {
        return this.performDiscovery(this.createRegisteredDeviceRetrievalURL(),"data");
    }

    // discover the device resources
    private List discoverDeviceResources(String device) {
        return this.performDiscovery(this.createDeviceResourceDiscoveryURL(device),"root");
    }
    
    // perform a discovery (JSON)
    private List performDiscovery(String url,String key) {
        if (key != null) {
            String json = this.performDiscoveryToString(url);
            if (json != null && json.length() > 0) {
                try {
                    Map base = this.jsonParser().parseJson(json);
                    if (base != null) {
                        this.errorLogger().info("performDiscovery: Response: " + base);
                        return (List)base.get(key);
                    }
                }
                catch (Exception ex) {
                    this.errorLogger().info("performDiscovery(mbed Cloud): Exception in JSON parse: " + ex.getMessage() + " URL: " + url);
                }
            }
            else {
                this.errorLogger().info("performDiscovery(mbed Cloud): No response given for URL: " + url);
            }
        }
        else {
            this.errorLogger().warning("performDiscovery(mbed Cloud): ERROR: No kev provided for Discovery. URL: " + url);
        }
        return null;
    }
    
    // perform a discovery
    private String performDiscoveryToString(String url) {
        return this.httpsGet(url);
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
    
    // discovery thread 
    @Override
    public void run() {
        // wait a bit
        Utils.waitForABit(this.errorLogger(), this.m_device_discovery_delay_ms);
        
        // now discover our devices and setup shadows...
        this.setupExistingDeviceShadows();
    }
}
