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
import com.arm.pelion.bridge.core.Utils;

/**
 * Long-Polling processor for Pelion
 *
 * @author Doug Anson
 */
public class LongPollProcessor extends Thread {
    private static final int LONG_POLL_SHORT_WAIT_MAX = 3000;           // max ms after normal long poll operation
    private static final int LONG_POLL_SHORT_WAIT_MIN = 1000;           // min ms after normal long poll operation
    private static final int API_KEY_UNCONFIGURED_WAIT_MS = 600000;     // pause for 5 minutes if an unconfigured API key is detected
    private static final int API_KEY_CONFIGURED_WAIT_MS = 10000;        // pause for 10 seconds if a configured API key is detected
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
        this.errorLogger().info("LongPollProcessor: Removing previous webhook (if any)...");
        
        // delete any older webhooks
        this.m_pelion_processor.removeWebhook();
        
        // DEBUG
        this.errorLogger().info("LongPollProcessor: Beginning long polling...");

        // start our thread...
        this.start();
    }

    // poll Pelion for new notifications
    private void poll() {
        String response = null;

        // persistent GET over https()
        this.m_pelion_processor.errorLogger().info("LongPollProcessor: Invoking HTTPS(GET) to poll Pelion API for new notifications...");
        response = this.m_pelion_processor.persistentHTTPSGet(this.m_pelion_processor.longPollURL());
        
        // note the response code
        int last_code = this.m_pelion_processor.getLastResponseCode();
        
        // DEBUG
        this.m_pelion_processor.errorLogger().info("LongPollProcessor: URL: " + this.m_pelion_processor.longPollURL() + " CODE: " + last_code);
        
        // act
        if (last_code == 400) {
            // API key already has a callback webhook setup
            this.errorLogger().warning("LongPollProcessor: API Key was previously setup in webhook mode... Please create and use another API Key and restart the bridge...");
            Utils.waitForABit(this.errorLogger(),API_KEY_UNCONFIGURED_WAIT_MS);
        }
        else if (last_code == 401) {
            if (this.m_pelion_processor.isConfiguredAPIKey() == false) {
                // API Key unconfigured
                this.errorLogger().warning("LongPollProcessor: API Key is not Configured. Please configure the API Key. Paused...please restart the bridge...");
            
                // we are unconfigured... so wait longer
                Utils.waitForABit(this.errorLogger(),API_KEY_UNCONFIGURED_WAIT_MS);
            }
            else {
                // Incorrect API Key... pause but wait less... 
                this.errorLogger().warning("LongPollProcessor: API Key does not appear to be valid (code 401). Please re-check/edit/save the key and restart the bridge...");
                Utils.waitForABit(this.errorLogger(),API_KEY_CONFIGURED_WAIT_MS);
            }
        }
        else if (last_code == 410) {
            // Pull channel is borked - reset API Key
            this.errorLogger().critical("LongPollProcessor: poll error code 410. Pelion pull channel not functioning properly. Please create and use another API Key and restart the bridge...");
            Utils.waitForABit(this.errorLogger(),API_KEY_UNCONFIGURED_WAIT_MS);
        }
        else {
            if (Utils.httpResponseCodeOK(last_code)) {
                // make sure we have a message to process...
                if (response != null && response.length() > 0) {
                    // DEBUG
                    this.errorLogger().info("LongPollProcessor: processing recevied message: " + response + " http_code=" + last_code);

                    // send whatever we get back as if we have received it via the webhook...
                    this.m_pelion_processor.processDeviceServerMessage(response,null);

                    // wait briefly... just to slow things down a little bit...
                    Utils.waitForABit(this.errorLogger(),Utils.createRandomNumberWithinRange(LONG_POLL_SHORT_WAIT_MIN, LONG_POLL_SHORT_WAIT_MAX));
                }
                else {
                    // DEBUG
                    this.errorLogger().info("LongPollProcessor: received message: <empty>. http_code=" + last_code);

                    // nothing to process
                    this.errorLogger().info("LongPollProcessor: Nothing to process (OK). http_code=" + last_code);

                    // wait briefly... just to slow things down a little bit...
                    Utils.waitForABit(this.errorLogger(),Utils.createRandomNumberWithinRange(LONG_POLL_SHORT_WAIT_MIN, LONG_POLL_SHORT_WAIT_MAX));
                }
            }
            else {
                // error code but possibly OK... note the non-handled code
                this.errorLogger().warning("LongPollProcessor: Received CODE: " + last_code);
                
                // wait briefly... just to slow things down a little bit more...
                Utils.waitForABit(this.errorLogger(),Utils.createRandomNumberWithinRange(2*LONG_POLL_SHORT_WAIT_MIN,4*LONG_POLL_SHORT_WAIT_MAX));
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
            try {
                // validate the webhook and subscriptions
                this.poll();
            }
            catch (Exception ex) {
                // note but keep going...
                this.errorLogger().warning("LongPoll: Exception caught: " + ex.getMessage() + ". Continuing...");
            }
        }
    }
}