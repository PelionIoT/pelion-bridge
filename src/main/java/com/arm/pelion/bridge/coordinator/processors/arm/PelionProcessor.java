/**
 * @file PelionProcessor.java
 * @brief Pelion Processor for the Pelion Device Shadow Bridge (HTTP based)
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
package com.arm.pelion.bridge.coordinator.processors.arm;

import com.arm.pelion.bridge.coordinator.processors.core.LongPollProcessor;
import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.core.HttpProcessor;
import com.arm.pelion.bridge.coordinator.processors.core.ShadowDeviceThreadDispatcher;
import com.arm.pelion.bridge.coordinator.processors.core.WebhookValidator;
import com.arm.pelion.bridge.core.ApiResponse;
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.transport.HttpTransport;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.mbed.lwm2m.LWM2MResource;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PelionProcessorInterface;
import java.util.ArrayList;

/**
 * Pelion Peer Processor - this HTTP based processor integrates with the REST API of Pelion for the device shadow bridge functionality
 *
 * @author Doug Anson
 */
public class PelionProcessor extends HttpProcessor implements Runnable, PelionProcessorInterface, AsyncResponseProcessor {
    // How many device entries to retrieve in a single /v3/devices query (discovery)
    private static final int PELION_MAX_DEVICES_PER_QUERY = 100;
    
    // Pelion API port
    private static final int PELION_API_PORT = 443;                            // std TLS port used by pelion
    
    // maximum number of device shadows to create at at time
    private static final int DEFAULT_MAX_SHADOW_CREATE_THREADS = 100;          // 100 creates at a time...
    
    // defaulted number of webhook retries
    private static final int PELION_WEBHOOK_RETRIES = 25;                      // 25 retries
    
    // webhook retry wait time in ms..
    private static final int PELION_WEBHOOK_RETRY_WAIT_MS = 10000;             // 10 seconds
    
    // amount of time to wait on boot before device discovery
    private static final int DEVICE_DISCOVERY_DELAY_MS = 15000;                // 15 seconds
    
    // default endpoint type
    public static String DEFAULT_ENDPOINT_TYPE = "default";                    // default endpoint type
    
    private String m_pelion_api_hostname = null;
    private int m_pelion_api_port = 0;
    private String m_pelion_cloud_uri = null;
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
    private WebhookValidator m_webhook_validator = null;
    
    // maximum number of grouped device shadow create threads
    private int m_mds_max_shadow_create_threads = DEFAULT_MAX_SHADOW_CREATE_THREADS;
    
    // Webhook establishment retries
    private int m_webook_num_retries = PELION_WEBHOOK_RETRIES;
    
    // defaulted endpoint type
    private String m_def_ep_type = DEFAULT_ENDPOINT_TYPE;
    
    // Webhook establishment retry wait time in ms
    private int m_webhook_retry_wait_ms = PELION_WEBHOOK_RETRY_WAIT_MS;
    
    // Option - delete a device if it deregisters (default FALSE)
    private boolean m_delete_device_on_deregistration = false;
    
    // Pelion Connect API version
    private String m_connect_api_version = "2";
    
    // Pelion Device API version
    private String m_device_api_version = "3";
    
    // Pelion duplicate message detection
    private String m_last_message = null;
    
    // Pelion API Key is configured or not?
    private boolean m_api_key_is_configured = false;
    
    // Maximum # of devices to query per GET
    private int m_max_devices_per_query = PELION_MAX_DEVICES_PER_QUERY;

    // constructor
    public PelionProcessor(Orchestrator orchestrator, HttpTransport http) {
        super(orchestrator, http);
        
        // Pelion Connection Parameters: Host, Port
        this.m_pelion_api_hostname = orchestrator.preferences().valueOf("mds_address");
        if (this.m_pelion_api_hostname == null || this.m_pelion_api_hostname.length() == 0) {
            this.m_pelion_api_hostname = orchestrator.preferences().valueOf("api_endpoint_address");
        }
        this.m_pelion_api_port = orchestrator.preferences().intValueOf("mds_port");
                
        // Last message buffer init
        this.m_last_message = null;
        
        // configure the maximum number of device shadow creates per group
        this.m_mds_max_shadow_create_threads = orchestrator.preferences().intValueOf("mds_max_shadow_create_threads");
        if (this.m_mds_max_shadow_create_threads <= 0) {
            this.m_mds_max_shadow_create_threads = DEFAULT_MAX_SHADOW_CREATE_THREADS;
        }
        this.errorLogger().warning("PelionProcessor: Maximum group shadow create threads per dispatch: " + this.m_mds_max_shadow_create_threads);
        
        // configure webhook setup retries
        this.m_webook_num_retries = orchestrator.preferences().intValueOf("mds_webhook_num_retries");
        if (this.m_webook_num_retries <= 0) {
            this.m_webook_num_retries = PELION_WEBHOOK_RETRIES;
        }
        
        // determine if the API key is configured or not
        this.setAPIKeyConfigured(this.apiToken());
        
        // get the requested pagination value
        this.m_max_devices_per_query = orchestrator.preferences().intValueOf("pelion_pagination_limit");
        if (this.m_max_devices_per_query <= 0) {
            this.m_max_devices_per_query = PELION_MAX_DEVICES_PER_QUERY;
        }
        this.errorLogger().warning("PelionProcessor: Pagination Limit set to: " + this.m_max_devices_per_query + " device IDs per page retrieved");
        
        // LongPolling Support
        this.m_enable_long_poll = this.prefBoolValue("mds_enable_long_poll");
        this.m_long_poll_uri = this.prefValue("mds_long_poll_uri");
        
        // display number of webhook setup retries allowed
        this.errorLogger().warning("PelionProcessor: Number of webhook setup retries configured to: " + this.m_webook_num_retries);

        // get the device attributes path
        this.m_device_attributes_path = orchestrator.preferences().valueOf("mds_device_attributes_path");

        // get the device attributes content type
        this.m_device_attributes_content_type = orchestrator.preferences().valueOf("mds_device_attributes_content_type");

        // initialize the default type of URI for contacting Pelion
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
            orchestrator.errorLogger().warning("PelionProcessor: Device removal on de-registration ENABLED");
        }
        else {
            orchestrator.errorLogger().warning("PelionProcessor: Device removal on de-registration DISABLED");
        }
        
        // finalize long polling setup if enabled
        if (this.longPollEnabled()) {
            // using long polling... so construct the long poll URL and start long polling (if API KEY is set...)
            this.errorLogger().warning("PelionProcessor: Long Polling ENABLED. Webhook usage DISABLED. Starting long polling...");

            // override use of long polling vs webhooks for notifications
            this.m_long_poll_url = this.constructLongPollURL();

            // start the Long polling thread... (will check if API KEY is set or not...)
            this.startLongPolling();
        }
        else {
            // using webhooks. Start webhook validator if API key is set...
            if (this.isConfiguredAPIKey() == true) {
                // start the webhook validator thread....
                this.errorLogger().warning("PelionProcessor: Webhook usage ENABLED. Long Polling DISABLED. Starting webhook validator...");
                this.m_webhook_validator = new WebhookValidator(this);
            }
            else {
                // No API Key set
                this.errorLogger().warning("PelionProcessor: Webhook validator not started. API KEY not set (OK)");
            }
        }
    }
    
    // long polling enabled
    public boolean webHookEnabled() {
        return !(this.m_enable_long_poll);
    }
    
    // set whether our API Key is configured or not...
    private void setAPIKeyConfigured(String api_key) {
        this.m_api_key_is_configured = false;
        if (api_key != null && api_key.contains("Goes_Here") == false) {
            // its not in its default configuration... so we assume configured!
            this.m_api_key_is_configured = true;
        }
        
        // DEBUG
        if (this.isConfiguredAPIKey()) {
            // configured
            this.errorLogger().warning("PelionProcessor: API Key is CONFIGURED");
        }
        else {
            // not configured... note as the bridge will be paused if long polling...
            this.errorLogger().warning("PelionProcessor: API Key is UNCONFIGURED");
        }
    }
    
    // is our API Key configured?
    public boolean isConfiguredAPIKey() {
        return this.m_api_key_is_configured;
    }
    
    // device removal on deregistration?
    @Override
    public boolean deviceRemovedOnDeRegistration() {
        return this.m_delete_device_on_deregistration;
    }
    
    // process device-deletions of endpoints
    @Override
    public void processDeviceDeletions(String[] endpoints) {
        // empty in pelion processor
    }
    
    // process de-registeration of endpoints
    @Override
    public void processDeregistrations(String[] endpoints) {
        // empty in pelion processor
    }
    
    // process registerations-expired of endpoints
    @Override
    public void processRegistrationsExpired(String[] endpoints) {
        // empty in pelion processor
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
                
                // process and route the Pelion message
                this.processDeviceServerMessage(json, request);
            }
            else {
                // DUPLICATE!  So ignore it
                this.errorLogger().info("PelionProcessor: Duplicate message discovered... Ignoring(OK)...");
            }
        }
        
        // ALWAYS send the response back as an ACK to Pelion
        this.sendResponseToPelion("application/json;charset=utf-8", request, response, "", "{}");
    }
    
    // remove the Pelion webhook
    @Override
    public synchronized void removeWebhook() {
        // create the dispatch URL
        String dispatch_url = this.createWebhookDispatchURL();

        // delete the callback URL (SSL)
        this.httpsDelete(dispatch_url);
    }

    // reset the Pelion Notification Callback URL
    @Override
    public boolean resetWebhook() {        
        // delete the webhook
        this.removeWebhook();
        
        // set the webhook
        return setWebhook(false);
    }
    
    // set our Pelion Notification Callback URL
    @Override
    public boolean setWebhook() {
        // external interface will invoke device discovery...
        return this.setWebhook(true);
    }

    // set our Pelion Notification Callback URL (with device discovery option)
    private synchronized boolean setWebhook(boolean do_discovery) {
        boolean ok = false;
        boolean do_restart = true;
        
        if (this.longPollEnabled() == false) {
            // lets make sure that we have a configured API Key...
            if (this.isConfiguredAPIKey() == true) {
                // API Key has been set... so lets try to setup the webhook now...
                for(int i=0;i<this.m_webook_num_retries && ok == false;++i) {
                    this.errorLogger().warning("PelionProcessor: Setting up webhook to Pelion...");
                    
                    // create the dispatch URL
                    String target_url = this.createWebhookURL();
                    
                    // set the webhook
                    ok = this.setWebhook(target_url);

                    // if OK, lets set bulk subscriptions...
                    if (ok) {
                        // bulk subscriptions enabled
                        this.errorLogger().warning("PelionProcessor: Webhook to Pelion set. Enabling bulk subscriptions.");
                        ok = this.setupBulkSubscriptions();
                        if (ok) {
                            if (do_discovery == true) {
                                // scan for devices now
                                this.errorLogger().warning("PelionProcessor: Initial scan for mbed devices...");
                                this.startDeviceDiscovery();
                            }
                            else {
                                // skip the device discovery
                                this.errorLogger().warning("PelionProcessor: Skipping initial scan for mbed devices (OK).");
                            }
                        }
                        else {
                            // ERROR
                            this.errorLogger().warning("PelionProcessor: Webhook not setup. Not scanning for devices yet...");
                        }
                    }

                    // wait a bit if we have failed
                    else {
                        // log and wait
                        this.errorLogger().warning("PelionProcessor: Pausing.. then will retry to set the webhook to Pelion...");
                        Utils.waitForABit(this.errorLogger(), this.m_webhook_retry_wait_ms);
                    }
                }
            }
            else {
                // Webhook has not been set. 
                this.errorLogger().warning("PelionProcessor: Pelion API Key has not been set. Unable to setup webhook. Please set the API Key and restart the bridge...");
                do_restart = false;
            }   

            // Final status
            if (ok == true) {
                // SUCCESS
                this.errorLogger().warning("PelionProcessor: Webhook to Pelion setup SUCCESSFULLY");
            }
            else if (do_restart == true) {
                // FAILURE
                this.errorLogger().critical("PelionProcessor: Unable to set the webhook to Pelion. Restarting bridge...");

                // RESET
                this.orchestrator().reset();
            }
            else {
                // FAILURE
                this.errorLogger().critical("PelionProcessor: Pelion API Key has not been set. Unable to set the webhook..");
            }
        }
        else {
            // not used by long polling
            ok = true;
        }
        
        // return our status
        return ok;
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
            this.errorLogger().info("PelionProcessor: Invoking API Request. ContentType: " + content_type + " URL: " + url);

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
                this.errorLogger().warning("PelionProcessor: ERROR: HTTP verb[" + verb + "] ContentType: [" + content_type + "] is UNKNOWN. Unable to execute request...");
                return this.createJSONMessage("api_execute_status","invalid coap verb");
            }
        }
        else {
            // invalid parameters
            this.errorLogger().warning("PelionProcessor: ERROR: invalid parameters in API request. Unable to execute request...");
            return this.createJSONMessage("api_execute_status","iinvalid api parameters");
        }
        
        // return a sanitized response
        String sanitized = this.sanitizeApiResponse(response);
        
        // DEBUG
        this.errorLogger().info("PelionProcessor:Sanitized API Response: " + sanitized);
        
        // return the sanitized response
        return sanitized;
    }
    
    // sanitize the API response
    private String sanitizeApiResponse(String response) {
        if (response == null || response.length() <= 0) {
            // DEBUG
            this.errorLogger().info("PelionProcessor: Response was EMPTY (OK).");
            
            // empty response
            return this.createJSONMessage("api_execute_status","empty response");
        }
        else {            
            // response should be parsable JSON
            Map parsed = this.tryJSONParse(response);
            if (parsed != null && parsed.isEmpty() == false) {
                // DEBUG
                this.errorLogger().info("PelionProcessor: Parsable RESPONSE: " + response);
                
                // parsable! just return the (patched) JSON string
                return response;
            }
            else {
                // DEBUG
                this.errorLogger().warning("PelionProcessor: Response parsing FAILED");
                
                // unparsable JSON... error
                return this.createJSONMessage("api_execute_status","unparsable json");
            }
        }
    }

    // determine if our callback URL has already been set
    private boolean webhookSet(String target_url) {
        return this.webhookSet(target_url, false);
    }

    // determine if our callback URL has already been set
    private boolean webhookSet(String target_url, boolean skip_check) {
        // get the current webhook
        String current_url = this.getWebhook();
        
        // Display the current webhook
        if (current_url != null && current_url.length() > 0) {
            // webhook previously set
            this.errorLogger().info("PelionProcessor: current_url: " + current_url + " target_url: " + target_url);
        }
        else {
            // no webhook set
            this.errorLogger().info("PelionProcessor: no webhook set yet");
        }
        
        // determine if we have a properly setup webhook already...
        boolean is_set = (target_url != null && current_url != null && target_url.equalsIgnoreCase(current_url));
        
        // now, if setup and we want to validate the auth header... lets delete and re-create the webhook
        if (is_set == true) {
            if (skip_check == false) {
                // webhook is set... but we want to to reset it to ensure we have the proper auth header...
                this.errorLogger().info("PelionProcessor: Webhook(auth reset) is set: " + current_url);
                
                // lets ensure that we always have the expected Auth Header setup. So, while the same, lets delete it...
                this.errorLogger().info("PelionProcessor: Webhook auth reset: Deleting existing webhook...");
                this.removeWebhook();

                // ... and reinstall it with the proper auth header... 
                this.errorLogger().info("PelionProcessor: Webhook auth reset: Re-establishing webhook with current auth header...");
                is_set = this.setWebhook(target_url, skip_check); // skip_check, go ahead and assume we need to set it...
                if (is_set == true) {
                    // SUCCESS
                    this.errorLogger().info("PelionProcessor: Webhook auth reset: Re-checking that webhook is properly set...");
                    current_url = this.getWebhook();
                    is_set = (target_url != null && current_url != null && target_url.equalsIgnoreCase(current_url));
                }
                else {
                    // webhook no longer set properly
                    this.errorLogger().info("PelionProcessor: Webhook auth reset: Webhook no longer setup correctly... resetting...");
                }
            }
            else {
                // webhook is set. Skipping auth header reset...
                this.errorLogger().info("PelionProcessor: No auth reset: Webhook is set: " + current_url);
            }
        }
        else {
            // Not set or incorrectly set... so reset it... 
            this.errorLogger().warning("PelionProcessor: Webhook has not been set yet.");
            is_set = false;
        }
        
        return is_set;
    }
    
    // get the currently configured callback URL (public, used by webhook validator)
    public String getWebhook() {
        String url = null;
        boolean success = false;
        String json = null;
        int http_code = 0;

        // create the dispatch URL
        String dispatch_url = this.createWebhookDispatchURL();
        
        // loop through a few times and try
        for(int i=0;i<this.m_webook_num_retries && success == false;++i) {
            try {
                // Issue GET and look at the response
                json = this.httpsGet(dispatch_url);
                http_code = this.getLastResponseCode();
                if (json != null && json.length() > 0) {
                    // Callback API used: parse the JSON
                    Map parsed = (Map) this.parseJson(json);
                    url = (String) parsed.get("url");

                    // DEBUG
                    this.orchestrator().errorLogger().info("PelionProcessor: received url: " + url + " from pelion callback dispatch: " + dispatch_url + " CODE: " + http_code);
                    
                    // success!!
                    success = true;
                }
                else if (http_code == 404) {
                    // no webhook record found
                    this.orchestrator().errorLogger().warning("PelionProcessor: no webhook record found (404)");
                    
                    // lets wait for a bit... 
                    success = false;
                } 
                else if (http_code == 200) {
                    // no response received back from Pelion - but success code given
                    this.orchestrator().errorLogger().warning("PelionProcessor no response received from webhook query: " + dispatch_url + " CODE: " + http_code + "... retrying...");
                
                    // wait for a bit...
                    success = false;
                }
                else {
                    // no response received back from Pelion - with an unexpected error code
                    this.orchestrator().errorLogger().warning("PelionProcessor: ERROR: no response received from webhook query: " + dispatch_url + " CODE: " + http_code + " (may need to re-create API Key if using long polling previously...)");
                    
                    // wait for a bit
                    success = false;
                }
            }
            catch (Exception ex) {
                // exception caught
                this.orchestrator().errorLogger().warning("PelionProcessor: Exception during webhook query: " + dispatch_url + " message: " + ex.getMessage() + " CODE: " + http_code + " JSON: " + json);
                
                // wait for a bit
                success = false;
            }
            
            // if unsuccessful... wait a bit
            if (success == false) {
                Utils.waitForABit(this.errorLogger(), this.m_webhook_retry_wait_ms);
            }
        }

        // return the URL only...
        return url;
    }

    // set our Pelion Notification Callback URL
    private boolean setWebhook(String target_url) {
        return this.setWebhook(target_url, true); // default is to check if the URL is already set... 
    }

    // set our Pelion Notification Callback URL
    private boolean setWebhook(String target_url, boolean check_url_set) {
        boolean webhook_set_ok = false; // assume default is that the URL is NOT set... 

        // we must check to see if we want to check that the URL is already set...
        if (check_url_set == true) {
            // go see if the URL is already set.. 
            String url = this.getWebhook();
            webhook_set_ok = (url != null && target_url != null && url.length() > 0 && url.equalsIgnoreCase(target_url));
        }

        // proceed to set the URL if its not already set.. 
        if (!webhook_set_ok) {
            // first lets reset Pelion so that we can setup our webhook
            this.removeWebhook();
            
            // now, lets create our webhook URL and Auth JSON
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
            this.errorLogger().info("PelionProcessor: json: " + json + " dispatch: " + dispatch_url);

            // set the callback URL (SSL)
            this.httpsPut(dispatch_url, json);

            // check that it succeeded
            String url = this.getWebhook();
            boolean is_set = (url != null && target_url != null && url.length() > 0 && url.equalsIgnoreCase(target_url));
            if (is_set == false) {
                // DEBUG
                this.errorLogger().warning("PelionProcessor: ERROR: unable to set webhook to: " + target_url);
                
                // not set...
                webhook_set_ok = false;
            }
            else {
                // DEBUG
                this.errorLogger().info("PelionProcessor: Webhook set to: " + target_url + " (SUCCESS)");
                
                // SUCCESS
                webhook_set_ok = true;
            }
        }
        else {
            // DEBUG
            this.errorLogger().info("PelionProcessor: Webhook already set to: " + target_url + " (OK)");
            
            // SUCCESS
            webhook_set_ok = true;
        }
        
        // return our status
        return webhook_set_ok;
    }
    
    // create any authentication header JSON that may be necessary
    private Map createWebhookHeaderAuthJSON() {
        // Create a hashmap and fill it
        HashMap<String,String> map = new HashMap<>();
        map.put("Authentication",this.orchestrator().createAuthenticationHash());
        return map;
    }

    // create our webhook URL that we will get called back on...
    private String createWebhookURL() {
        String url = null;

        String local_ip = Utils.getExternalIPAddress();
        String override_ip = this.preferences().valueOf("mds_gw_address");
        if (override_ip != null && override_ip.length() > 0 && override_ip.contains(".") == true && override_ip.equalsIgnoreCase("off") == false) {
            // override our local IP address...
            local_ip = override_ip;
            this.errorLogger().info("PelionProcessor: Overriding webhook IP address to: " + local_ip);
        }
        
        int local_port = this.prefIntValue("mds_gw_port");
        String notify_uri = this.prefValue("mds_gw_context_path") + this.prefValue("mds_gw_events_path");

        // build and return the webhook callback URL
        return this.m_pelion_cloud_uri + local_ip + ":" + local_port + notify_uri;
    }

    // Pelion: create the dispatch URL for changing the notification webhook URL
    private String createWebhookDispatchURL() {
        return this.createBaseURL() + "/notification/callback";
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

    // process and route the Pelion message to the appropriate peer method
    public void processDeviceServerMessage(String json, HttpServletRequest request) {
        // DEBUG
        this.orchestrator().errorLogger().info("PelionProcessor: Received message from Pelion: " + json);

        // tell the orchestrator to call its peer processors with this Pelion message
        try {
            if (json != null && json.length() > 0 && json.equalsIgnoreCase("{}") == false) {
                Map parsed = (Map) this.parseJson(json);
                if (parsed != null) {
                    // DEBUG
                    this.errorLogger().info("PelionProcessor: Parsed: " + parsed);

                    // notifications processing
                    if (parsed.containsKey("notifications")) {
                        if (this.validateNotification(request)) {
                            // DEBUG
                            this.errorLogger().info("PelionProcessor: Notification VALIDATED");

                            // validated notification... process it...
                            this.orchestrator().processNotification(parsed);
                        }
                        else {
                            // validation FAILED. Note but do not process...
                            this.errorLogger().warning("PelionProcessor: Notification validation FAILED. Not processed (OK)");
                        }
                    }  
                    
                    // registrations processing
                    if (parsed.containsKey("registrations")) {
                        this.orchestrator().processNewRegistration(parsed);
                    }
                    
                    // registration updates processing
                    if (parsed.containsKey("reg-updates")) {
                        this.orchestrator().processReRegistration(parsed);
                    }
                    
                    // de-registrations processing
                    if (parsed.containsKey("de-registrations")) {
                        this.orchestrator().processDeregistrations(parsed);
                    }
                    
                    // registrations expired processing
                    if (parsed.containsKey("registrations-expired")) {
                        this.orchestrator().processRegistrationsExpired(parsed);
                    }
                    
                    // async-response processing
                    if (parsed.containsKey("async-responses")) {
                        this.orchestrator().processAsyncResponses(parsed);
                    }
                }
                else {
                    // parseJson() failed...
                    this.errorLogger().warning("PelionProcessor: Unable to parse JSON: " + json);
                }
            }
            else {
                // empty JSON... so not parsed
                this.errorLogger().info("PelionProcessor: Empty JSON not parsed (OK).");
            }
        }
        catch (Exception ex) {
            // exception during JSON parsing
            this.errorLogger().info("PelionProcessor: Exception during JSON parse of message: " + json + "... ignoring.", ex);
        }
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
            // dispatch the Pelion REST based on CoAP verb received
            if (verb.equalsIgnoreCase(("get"))) {
                this.errorLogger().info("PelionProcessor: Invoking GET: " + url);
                json = this.httpsGet(url);
                if (json == null) json = "";
            }
            if (verb.equalsIgnoreCase(("put"))) {
                this.errorLogger().info("PelionProcessor: Invoking PUT: " + url + " DATA: " + value);
                json = this.httpsPut(url, value);
                if (json == null) json = "";
            }
            if (verb.equalsIgnoreCase(("post"))) {
                this.errorLogger().info("PelionProcessor: Invoking POST: " + url + " DATA: " + value);
                 json = this.httpsPost(url, value, "plain/text", this.apiToken());  // nail content_type to "plain/text"
                 if (json == null) json = "";
            }
            if (verb.equalsIgnoreCase(("delete"))) {
                this.errorLogger().info("PelionProcessor: Invoking DELETE: " + url);
                 json = this.httpsDelete(url, "plain/text", this.apiToken());      // nail content_type to "plain/text"
                 if (json == null) json = "";
            }
            if (verb.equalsIgnoreCase(("del"))) {
                this.errorLogger().info("PelionProcessor: Invoking DELETE: " + url);
                json = this.httpsDelete(url, "plain/text", this.apiToken());      // nail content_type to "plain/text"
                if (json == null) json = "";
            }
        }
        else {
            this.errorLogger().info("PelionProcessor: ERROR: CoAP Verb is NULL. Not processing: ep: " + ep_name + " uri: " + uri + " value: " + value);
            json = null;
        }

        return json;
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
    
    // initialize the endpoint's default attributes 
    public void initDeviceWithDefaultAttributes(Map endpoint) {
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
            this.errorLogger().info("PelionProcessor: Exception caught: " + ex.getMessage(), ex);
        }
        
        // DEBUG
        if (has_device_attributes == true) {
            this.errorLogger().info("PelionProcessor: Device HAS attributes: " + endpoint);
        }
        else {
            this.errorLogger().info("PelionProcessor: Device DOES NOT have attributes: " + endpoint);
        }

        // return our status
        return has_device_attributes;
    }

    // retrieve the actual device attributes
    private void retrieveDeviceAttributes(Map endpoint, AsyncResponseProcessor processor) {
        // get the device ID and device Type
        String device_id = Utils.valueFromValidKey(endpoint, "id", "ep");
                    
        // Create the Device Attributes URL
        String url = this.createCoAPURL(device_id, this.m_device_attributes_path);

        // DEBUG
        //this.errorLogger().info("ATTRIBUTES: Calling GET to receive: " + url);
        
        // Dispatch and get the response (an AsyncId)
        String json_response = this.httpsGet(url, this.m_device_attributes_content_type, this.apiToken());

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
            this.retrieveDeviceAttributes(endpoint,processor);
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
                this.errorLogger().warning("PelionProcessor: No peer AsyncResponse processor. Device may not get addeded within peer.");
            }
        }
    }

    // parse the device attributes
    private Map parseDeviceAttributes(Map response, Map endpoint) {
        LWM2MResource res = null;
        
        try {
            // Convert the TLV to a LWM2M Resource List...
            List<LWM2MResource> list = Utils.tlvDecodeToLWM2MObjectList(this.errorLogger(),(String) response.get("payload"));
                        
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
            this.errorLogger().info("PelionProcessor: Error parsing TLV device attributes... using defaults...OK: " + ex.getMessage(),ex);
        }

        // return the updated endpoint
        return endpoint;
    }
    
    // pull the initial device metadata from Pelion.. add it to the device endpoint map
    @Override
    public void pullDeviceMetadata(Map endpoint, AsyncResponseProcessor processor) {
        // Get the DeviceID and DeviceType
        String device_type = Utils.valueFromValidKey(endpoint, "endpoint_type", "ept");
        String device_id = Utils.valueFromValidKey(endpoint, "id", "ep");

        // record the device type for the device ID
        this.orchestrator().getEndpointTypeManager().setEndpointTypeFromEndpointName(device_id, device_type);
            

        // initialize the endpoint with defaulted device attributes
        this.initDeviceWithDefaultAttributes(endpoint);

        // save off the peer processor for later
        endpoint.put("peer_processor", processor);

        // invoke GETs to retrieve the actual attributes (we are the processor for the callbacks...)
        this.getActualDeviceAttributes(endpoint, this);
    }

    //
    // The following methods are stubbed out for now - they provide defaulted device metadata info.
    // The actual CoAP Resource URI's are specified in the bridge configuration file and must be the same for all devices.
    // 
    // pull the device manufacturer information
    private void pullDeviceManufacturer(Map endpoint) {
        //this.m_device_manufacturer_res
        endpoint.put("meta_mfg", "arm");
    }

    // pull the device Serial Number information
    private void pullDeviceSerialNumber(Map endpoint) {
        //this.m_device_serial_number_res
        endpoint.put("meta_serial", "0123456789");
    }

    // pull the device model information
    private void pullDeviceModel(Map endpoint) {
        //this.m_device_model_res
        endpoint.put("meta_model", "pelion");
    }

    // pull the device manufacturer information
    private void pullDeviceClass(Map endpoint) {
        //this.m_device_class_res
        endpoint.put("meta_class", "arm");
    }

    // pull the device manufacturer information
    private void pullDeviceDescription(Map endpoint) {
        //this.m_device_description_res
        endpoint.put("meta_description", "pelion device");
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
        endpoint.put("meta_total_mem", "n/a");
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
                    this.errorLogger().info("PelionProcessor: ORIG endpoint: " + orig_endpoint);
                    this.errorLogger().info("PelionProcessor: RESPONSE: " + response);
                    Map endpoint = this.parseDeviceAttributes(response,orig_endpoint);
                    
                    // DEBUG
                    this.errorLogger().info("PelionProcessor: endpoint: " + endpoint);
                    
                    // call the AsyncResponseProcessor within the peer to finalize the device
                    peer_processor.processAsyncResponse(endpoint);
                }
                else {
                    // error - no peer AsyncResponseProcessor...
                    this.errorLogger().warning("PelionProcessor: No peer AsyncResponse processor. Device may not get addeded within peer: " + record);
                }
            }
            else {
                // error - no peer AsyncResponseProcessor...
                this.errorLogger().warning("PelionProcessor: No peer AsyncResponse processor. Device may not get addeded within peer: " + orig_endpoint);
            }

            // return processed status (defaulted)
            return true;
        }

        // return non-processed
        return false;
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
    
    // setup initial Device Shadows from Pelion
    private void setupExistingDeviceShadows() {
        // DEBUG
        this.errorLogger().warning("PelionProcessor: Starting the device discovery thread dispatcher...");
        
        // Create the ShadowDeviceThread Dispatcher
        Thread sdtp = new Thread(new ShadowDeviceThreadDispatcher(this,this.m_mds_max_shadow_create_threads));
        sdtp.start();
    }
    
    // worker to setup a specific device's shadow
    public void dispatchDeviceSetup(Map device) {
        // endpoint to create the shadow with...
        HashMap<String,Object> endpoint = new HashMap<>();
        
        // sanitize the endpoint type
        device.put("endpoint_type",this.sanitizeEndpointType((String)device.get("endpoint_type")));

        // get the device ID and device Type
        String device_type = Utils.valueFromValidKey(device, "endpoint_type", "ept");
        String device_id = Utils.valueFromValidKey(device, "id", "ep");
                    
        // duplicate relevant portions for compatibility...
        endpoint.put("ep", device_id);
        endpoint.put("ept",device_type);
        
        // copy over the rest of the device record including the metadata
        HashMap<String,Object> device_map = (HashMap<String,Object>)device;
        for (Map.Entry<String, Object> entry : device_map.entrySet()) {
            endpoint.put(entry.getKey(),entry.getValue());
        }

        // DEBUG
        this.errorLogger().warning("PelionProcessor: Discovered Pelion device with ID: " + device_id + " Type: " + device_type);
            
        // now, query Pelion again for each device and get its resources
        List resources = this.discoverDeviceResources(device_id);
        
        // if we have resources, add them to the record
        if (resources != null && resources.size() > 0) {
            endpoint.put("resources",resources); 
        }
        
        // Save ept for ep in the peers...
        this.orchestrator().getEndpointTypeManager().setEndpointTypeFromEndpointName(device_id, device_type);

        // process as new device registration...
        this.orchestrator().completeNewDeviceRegistration(endpoint);
    }
    
    // create the registered devices retrieval URL
    private String createGetRegisteredDevicesURL() {
        // create the url to capture all of the registered devices
        String url = this.createBaseURL("/v" + this.m_device_api_version) + "/devices" + "?filter=state%3Dregistered" ;

        // DEBUG
        this.errorLogger().info("PelionProcessor: Get Registered Devices URL: " + url);
        
        // return the device discovery URL
        return url;
    }
    
    // create the Device Resource Discovery URL 
    private String createDeviceResourceDiscoveryURL(String device) {
        // build out the URL for Pelion Device Resource discovery...
        String url = this.createBaseURL("/v" + this.m_connect_api_version) + "/endpoints/" +  device;

        // DEBUG
        this.errorLogger().info("PelionProcessor: Discovery URL: " + url);
        
        // return the device resource discovery URL
        return url;
    }

    // perform device discovery
    public List discoverRegisteredDevices() {
        return this.performDiscovery(this.createGetRegisteredDevicesURL(),"data");
    }

    // discover the device resources
    private List discoverDeviceResources(String device) {
        return this.performDiscovery(this.createDeviceResourceDiscoveryURL(device));
    }
    
    // perform a discovery (JSON)
    private List performDiscovery(String url) {
        return this.performDiscovery(url,null);
    }
    
    // determine if we have more pages or not to read
    private boolean hasMorePages(Map page) {
        if (page != null && page.containsKey("has_more")) {
            return (Boolean)page.get("has_more");
        }
        return false;
    }
    
    // get the last device ID
    private String getLastDeviceID(List list) {
        if (list != null && list.size() > 0) {
            Map device = (Map)list.get(list.size() - 1);
            return Utils.valueFromValidKey(device, "id", "ep");
        }
        return null;
    }
    
    // combine pages into a single List result
    private List combinePages(ArrayList<List> pages) {
        ArrayList<Map> combined_list = new ArrayList<>();
        
        // loop through and combine if needed
        if (pages != null) {
            // handle singleton case
            if (pages.size() == 1) {
                // just bail and return the first list
                return pages.get(0);
            }
            else {
                // we have to loop and combine...
                for(int i=0;i<pages.size();++i) {
                    // Grab the ith page 
                    List page = (List)pages.get(i);
                    
                    // we are combining device discovery data... so we need to combine the "data" values...
                    for(int j=0;page != null && j<page.size();++j) {
                        combined_list.add((Map)page.get(j));
                    }                    
                }
            }
        }
        
        // if we have nothing to report... nullify for consistency
        if (combined_list.isEmpty()) {
            combined_list = null;
        }
        
        // return the combined list
        return (List)combined_list;
    }
    
    // create the "after" filter
    private String createAfterFilter(String device_id) {
        if (device_id == null || device_id.length() == 0) {
            return "";
        }
        return "&after=" + device_id;
    }
    
    // perform a pagenated discovery (of devices...)
    private Map performPagenatedDiscovery(String base_url,String key) {
        boolean more_pages = true;
        HashMap<String,Object> response = new HashMap<>();
        ArrayList<List> pages = new ArrayList<>();
        String last_device_id = null;
        
        // limit filter - also set the ordering... 
        String filter = "&limit=" + this.m_max_devices_per_query + "&order=ASC";
        
        // loop and pull all of the pages... 
        while(more_pages == true) {
            String url = base_url + filter + this.createAfterFilter(last_device_id);
            this.errorLogger().info("PelionProcessor: URL: " + url);
            String json = this.httpsGet(url);
            if (json != null && json.length() > 0) {
                try {
                    if (key != null) {
                        Map base = this.jsonParser().parseJson(json);
                        if (base != null) {
                            // Add the page
                            List page = (List)base.get(key);
                            pages.add(page);
                            
                            // Query the page and see if we need to repeat...
                            more_pages = this.hasMorePages(base);
                            if (more_pages) {
                                last_device_id = this.getLastDeviceID((List)base.get(key));
                            }
                            
                            // DEBUG
                            this.errorLogger().info("PelionProcessor: Added: " + page.size() + " Total: " + pages.size());
                        }
                    }
                }
                catch (Exception ex) {
                    this.errorLogger().warning("PelionProcessor: Exception in JSON parse: " + ex.getMessage() + " URL: " + url);
                    more_pages = false;
                }
            }
            else {
                this.errorLogger().info("PelionProcessor: No DEVICE response given for URL: " + url);
                more_pages = false;
            }
        }
        
        // combine the data pages into one list
        List combined_list = this.combinePages(pages);
        
        // if not null, create the new response with the combined data list
        if (combined_list != null) {
            // has data 
            response.put(key,combined_list);
        }
        else {
            // empty list
            response.put(key,new ArrayList());
        }
        
        return response;
    }
   
    // perform a discovery (JSON)
    private List performDiscovery(String url,String key) {
        this.errorLogger().info("PelionProcessor: URL: " + url + " Key: " + key);
        if (key != null) {
            // Device Discovery - handle possible pagenation
            Map response = this.performPagenatedDiscovery(url,key);
            List list = (List)response.get(key);
            
            // DEBUG
            this.errorLogger().info("PelionProcessor: Number of devices found (paginated discovery): " + list.size());
            
            // return our list...
            return list;
        }
        else {
            // Resource Discovery - no pagenation support needed
            String json = this.httpsGet(url);
            if (json != null && json.length() > 0) {
                List list = this.jsonParser().parseJsonToArray(json);
                this.errorLogger().info("PelionProcessor: Response: " + list);
                return list;
            }
            else {
                this.errorLogger().info("PelionProcessor: No RESOURCE info response given for URL: " + url);
            }
        }
        return null;
    }
    
    // create the base URL for Pelion operations
    private String createBaseURL() {
        return this.createBaseURL("/v" + this.m_connect_api_version);
    }
    
    // create the base URL for Pelion operations
    private String createBaseURL(String version) {
        return this.m_pelion_cloud_uri + this.m_pelion_api_hostname + ":" + this.m_pelion_api_port + version;
    }

    // create the CoAP operation URL
    private String createCoAPURL(String ep_name, String uri) {
        String url = this.createBaseURL() + "/endpoints/" + ep_name + uri;
        return url;
    }
    
    // sanitize the endpoint type
    private String sanitizeEndpointType(String ept) {
        if (ept == null || ept.length() == 0) {
            return this.m_def_ep_type;
        }
        return ept;
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
        this.errorLogger().info("PelionProcessor: constructLongPollURL: Long Poll URL: " + url);
        return url;
    }

    // start the long polling thread
    private void startLongPolling() {
        if (this.isConfiguredAPIKey() == true) {
            // setup bulk subscriptions
            this.errorLogger().warning("PelionProcessor: Enabling bulk subscriptions...");
            boolean ok = this.setupBulkSubscriptions();
            if (ok == true) {
                // success
                this.errorLogger().info("PelionProcessor: bulk subscriptions enabled SUCCESS");
            }
            else {
                // failure
                this.errorLogger().info("PelionProcessor: bulk subscriptions enabled FAILED");
            }

            // now begin to long poll Pelion
            if (this.m_long_poll_processor == null) {
                this.m_long_poll_processor = new LongPollProcessor(this);
                this.m_long_poll_processor.startPolling();
            }
        }
        else {
            // API key is not configured... so no long polling
            this.errorLogger().warning("PelionProcessor: Stopping long polling. API Key not configured (OK)");
        }
    }
    
    // establish bulk subscriptions in Pelion
    private boolean setupBulkSubscriptions() {
        boolean ok = false;
        
        // DEBUG
        this.errorLogger().info("PelionProcessor: Setting up bulk subscriptions...");

        // JSON for the bulk subscription (must be an array)
        String json = "[" + this.createJSONMessage("endpoint-name","*") + "]";

        // Create the URI for the bulk subscription PUT
        String url = this.createBaseURL() + "/subscriptions";

        // DEBUG
        this.errorLogger().info("PelionProcessor: Bulk subscriptions URL: " + url + " DATA: " + json);

        // send PUT to establish the bulk subscriptions
        String result = this.httpsPut(url, json, "application/json", this.apiToken());
        int error_code = this.getLastResponseCode();

        // DEBUG
        if (result != null && result.length() > 0) {
            this.errorLogger().info("PelionProcessor: Bulk subscriptions setup RESULT: " + result);
        }

        // check the setup error code 
        if (error_code == 204) {    // SUCCESS response code: 204
            // success!
            this.errorLogger().info("PelionProcessor: Bulk subscriptions setup SUCCESS: Code: " + error_code);
            ok = true;
        }
        else {
            // failure
            this.errorLogger().warning("PelionProcessor: Bulk subscriptions setup FAILED: Code: " + error_code);
        }
        
        // return our status
        return ok;
    }
    
    // setup the mbed device server default URI
    private void setupPelionCloudURI() {
        this.m_pelion_cloud_uri = "https://";
        this.m_pelion_api_port = PELION_API_PORT;
    }
    
    // validate the notification
    private Boolean validateNotification(HttpServletRequest request) {
        if (request != null) {
            boolean validated = false;
            if (request.getHeader("Authentication") != null) {
                String calc_hash = this.orchestrator().createAuthenticationHash();
                String header_hash = request.getHeader("Authentication");
                validated = Utils.validateHash(header_hash, calc_hash);

                // DEBUG
                if (!validated) {
                    this.errorLogger().warning("PelionProcessor: Notification Validation FAILED: calc: " + calc_hash + " header: " + header_hash);
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
    
    // discovery thread 
    @Override
    public void run() {
        // wait a bit
        Utils.waitForABit(this.errorLogger(), this.m_device_discovery_delay_ms);
        
        // now discover our devices and setup shadows...
        this.setupExistingDeviceShadows();
    }
    
    // validate the webhook (Health Stats)
    public boolean webhookOK() {
        try {
            String url = createWebhookURL();
            String webhook = this.getWebhook();
            if (webhook != null && webhook.length() > 0 && url.equalsIgnoreCase(webhook) == true) {
                // webhook is OK
                return true;
            }
        }
        catch (Exception ex) {
            // silent
        }
        return false;
    }
    
    // validate long poll (Health Stats)
    public boolean longPollOK() {
        try {
            // we cannot directly test long poll... just that we can make HTTP(get) calls
            String my_ip = Utils.getExternalIPAddress();
            if (my_ip != null && my_ip.length() > 0 && my_ip.contains(".") == true) {
                return true;
            }
        }
        catch (Exception ex) {
            // silent
        }
        return false;
    }
    
    // get the active thread count
    public int getActiveThreadCount() {
        return this.orchestrator().getActiveThreadCount();
    }
}