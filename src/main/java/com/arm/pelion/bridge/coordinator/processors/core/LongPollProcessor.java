/**
 * @file LongPollProcessor.java
 * @brief Pelion long polling processor
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
package com.arm.pelion.bridge.coordinator.processors.core;

import com.arm.pelion.bridge.coordinator.processors.arm.PelionProcessor;
import com.arm.pelion.bridge.core.ErrorLogger;

/**
 * Long-Polling processor for Pelion
 *
 * @author Doug Anson
 */
public class LongPollProcessor extends Thread {

    private PelionProcessor m_pelion_processor = null;
    private boolean m_running = false;

    // default constructor
    public LongPollProcessor(PelionProcessor mds) {
        this.m_pelion_processor = mds;
        this.m_running = false;
    }

    // get our error logger
    private ErrorLogger errorLogger() {
        return this.m_pelion_processor.errorLogger();
    }

    // initialize the poller
    public void startPolling() {
        // DEBUG
        this.errorLogger().info("Removing previous webhook (if any)...");
        
        // delete any older webhooks
        this.m_pelion_processor.removeWebhook();
        
        // DEBUG
        this.errorLogger().info("Beginning long polling...");

        // start our thread...
        this.start();
    }

    // poll 
    private void poll() {
        String response = null;

        // persistent GET over https()
        this.m_pelion_processor.errorLogger().info("LongPollProcessor: using HTTPS persistent get...");
        response = this.m_pelion_processor.persistentHTTPSGet(this.m_pelion_processor.longPollURL());
         
        // note the response code
        int last_code = this.m_pelion_processor.getLastResponseCode();
        if (last_code == 400) {
            // API key already has a callback webhook setup
            this.errorLogger().warning("LongPollProcessor: using API Key that has already setup a callback webhook... please use another key!");
        }
        else if (last_code == 401) {
            // API Key might be wrong?
            this.errorLogger().warning("LongPollProcessor: API Key does not appear to be valid (401 - Unauthorized). Please check the key.");
        }
        else if (last_code == 410) {
            // Pull channel is borked - reset API Key
            this.errorLogger().critical("LongPollProcessor: polling error code 410 seen. Pull channel is not functioning properly. Please create and use another API Key");
        }
        else {
            // OK
            this.errorLogger().info("LongPollProcessor: (OK).");
            
            // make sure we have a message to process...
            if (response != null && response.length() > 0) {
                // DEBUG
                this.errorLogger().info("LongPollProcessor: processing recevied message: " + response);
                
                // send whatever we get back as if we have received it via the webhook...
                this.m_pelion_processor.processDeviceServerMessage(response,null);
            }
            else {
                // DEBUG
                this.errorLogger().info("LongPollProcessor: received message: <empty>");
                
                // nothing to process
                this.errorLogger().info("LongPollProcessor: Nothing to process (OK)");
            }
        }
    }

    /**
     * run method for the receive thread
     */
    @Override
    public void run() {
        if (!this.m_running) {
            this.m_running = true;
            this.pollingLooper();
        }
    }

    /**
     * main thread loop
     */
    private void pollingLooper() {
        while (this.m_running == true) {
            // validate the webhook and subscriptions
            this.poll();
        }
    }
}
