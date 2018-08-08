/**
 * @file    WebhookValidator.java
 * @brief mDS webhook validation and subscription checker
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
package com.arm.connector.bridge.coordinator.processors.arm;

import com.arm.connector.bridge.core.ErrorLogger;
import com.arm.connector.bridge.data.SerializableArrayList;

/**
 * This class periodically polls mDS/mDC and validates the webhook and subscription settings
 *
 * @author Doug Anson
 */
public class WebhookValidator extends Thread {

    private mbedDeviceServerProcessor m_mds = null;
    private int m_poll_interval_ms = 0;
    private String m_webhook_url = null;
    private boolean m_running = false;
    private int m_max_retry_count = 0;
    
    private SerializableArrayList m_subscriptions = null;

    // default constructor
    public WebhookValidator(mbedDeviceServerProcessor mds, int poll_interval_ms) {
        this.m_mds = mds;
        this.m_poll_interval_ms = poll_interval_ms;
        this.m_webhook_url = null;
        this.m_subscriptions = new SerializableArrayList(mds.orchestrator(),"SUBSCRIPTION_NAMES");
        this.m_running = false;
        this.m_max_retry_count = mds.preferences().intValueOf("mds_webhook_retry_max_tries");
    }

    // get our error logger
    private ErrorLogger errorLogger() {
        return this.m_mds.errorLogger();
    }

    // initialize the poller
    public void startPolling() {
        // DEBUG
        this.errorLogger().info("Beginning Webhook/Subscription Poll: " + this.m_poll_interval_ms + "ms...");
        // start our thread...
        this.start();
    }

    // validate
    public void validate() {
        // DEBUG
        this.errorLogger().info("Validating mDS/mDC webhook status...");

        // validate the webhook
        if (this.validateWebhook() == true) {
            // DEBUG
            this.errorLogger().info("Webhook OK. Validating Subscriptions...");

            // validate the resource subscriptions
            if (this.validateResourceSubscriptions() == true) {
                // DEBUG
                this.errorLogger().info("Webhook OK. Subscriptions OK...");
            }
            else {
                // DEBUG
                this.errorLogger().info("Webhook OK. Re-Initializing Subscriptions...");

                // reset the subscriptions
                if (this.reInitializeSubscriptions() == true) {
                    // DEBUG
                    this.errorLogger().info("Webhook OK. Subscriptions OK (reset)...");
                }
                else {
                    // DEBUG
                    this.errorLogger().info("Webhook OK. Subscription re-initialize FAILED.");
                }
            }
        }
        else {
            // DEBUG
            this.errorLogger().info("Re-Initializing webhook...");

            // reset the webhook
            if (this.reInitializeWebhook() == true) {
                // DEBUG
                this.errorLogger().info("Webhook OK (reset). Re-Initializing Subscriptions...");

                // reset the subscriptions
                if (this.reInitializeSubscriptions() == true) {
                    // DEBUG
                    this.errorLogger().info("Webhook OK (reset). Subscriptions OK (reset)...");
                }
                else {
                    // DEBUG
                    this.errorLogger().info("Webhook OK  (reset). Subscription re-initialize FAILED.");
                }
            }
            else {
                // DEBUG
                this.errorLogger().info("Webhook re-initialize FAILED.");
            }
        }
    }

    // unset the webhook
    public void resetWebhook() {
        this.m_webhook_url = null;
    }

    // set the webhook
    public void setWebhook(String webhook_url) {
        if (webhook_url != null && webhook_url.length() > 0) {
            this.m_webhook_url = webhook_url;
        }
    }

    // add a subscription 
    public void addSubscription(String url) {
        if (!this.isSubscribed(url)) {
            this.m_subscriptions.add(url);
        }
    }

    // remove all subscriptions for a given endpoint
    public synchronized void removeSubscriptionsforEndpoint(String endpoint) {
        if (endpoint != null && endpoint.length() > 0) {
            String key = "/" + endpoint + "/";
            for (int i = 0; i < this.m_subscriptions.size(); ++i) {
                String s = (String)this.m_subscriptions.get(i);
                if (s.contains(key) == true) {
                    // delete this resource
                    this.m_subscriptions.remove(i);
                }
            }
        }
    }

    // remove a subscription
    public synchronized void removeSubscription(String url) {
        int index = getSubscriptionIndex(url);
        if (index >= 0) {
            this.m_subscriptions.remove(index);
        }
        else {
            this.errorLogger().warning("removeSubscription: NOT FOUND: " + url);
        }
    }

    // already subscribed?
    private boolean isSubscribed(String url) {
        return (this.getSubscriptionIndex(url) >= 0);
    }

    // already subscribed?
    private int getSubscriptionIndex(String url) {
        int index = -1;

        //  is the subscription already in the list?
        for (int i = 0; i < this.m_subscriptions.size() && index < 0; ++i) {
            String s = (String)this.m_subscriptions.get(i);
            if (s.equalsIgnoreCase(url)) {
                index = i;
            }
        }

        return index;
    }

    /**
     * run method for the receive thread
     */
    @Override
    public void run() {
        if (!this.m_running) {
            this.m_running = true;
            this.validatorLoop();
        }
    }

    /**
     * main thread loop
     */
    @SuppressWarnings("empty-statement")
    private void validatorLoop() {
        while (this.m_running == true) {
            // validate the webhook and subscriptions
            this.validate();

            // sleep for a bit...
            try {
                Thread.sleep(this.m_poll_interval_ms);
            }
            catch (InterruptedException ex) {
                // silent
                ;
            }
        }
    }

    // re-ininitialize the webhook
    private boolean reInitializeWebhook() {
        boolean reinitialized = false;

        // reset the webhook
        this.resetWebhook();

        // direct mbedDeviceServerProcessor to re-establish the webhook
        this.m_mds.resetWebhook();
        this.m_mds.setWebhook();

        // check the HTTP result code
        int status = this.m_mds.getLastResponseCode();
        status = status - 200;
        if (status >= 0 && (status < 100 || status < 300)) {
            // 20x response - OK
            reinitialized = true;

            // DEBUG
            this.errorLogger().info("reInitializeWebhook: re-init webhook: " + this.m_webhook_url + " RESULT: " + (status + 200));

        }
        else {
            // DEBUG
            this.errorLogger().info("reInitializeWebhook: re-init webhook: " + this.m_webhook_url + " RESULT: " + (status + 200));
            reinitialized = false;
        }

        // return our status
        return reinitialized;
    }

    // re-initialize the subscriptions
    private synchronized boolean reInitializeSubscriptions() {
        int count = 0;
        boolean reinitialized = true;
        try {
            for (int i = 0; i < this.m_subscriptions.size(); ++i) {
                // remove any previous subscription
                this.m_mds.unsubscribeFromEndpointResource((String)this.m_subscriptions.get(i));

                // re-subscribe
                String url = (String)this.m_subscriptions.get(i);
                this.m_mds.subscribeToEndpointResource((String)this.m_subscriptions.get(i));

                // check the HTTP result code
                int status = this.m_mds.getLastResponseCode();

                // check for queue-mode endpoint unavailable...
                if (status == 429) {
                    // endpoint is in queue mode and is unavailable... retry
                    ++count;
                    if (count < this.m_max_retry_count) {
                        // retrying...
                        this.errorLogger().info("reInitializeSubscriptions: retrying... (" + this.m_subscriptions.get(i) + ") endpoint reports unavailable...");

                        // backup and retry...
                        --i;
                        reinitialized = true;
                    }
                    else {
                        // giving up on this endpoint
                        this.errorLogger().info("reInitializeSubscriptions: giving up: " + this.m_subscriptions.get(i) + " endpoint reports unavailable...");

                        // continue retrying other endpoints though...
                        reinitialized = true;
                    }
                }
                else if (status == 404) {
                    // endpoint is gone... just remove this resource
                    this.errorLogger().info("reInitializeSubscriptions: endpoint gone... removing: " + this.m_subscriptions.get(i));
                    this.m_subscriptions.remove(i);
                }
                else {
                    // general case...
                    status = status - 200;
                    if (status >= 0 && status < 100) {
                        // 20x response - OK
                        reinitialized = true;

                        // DEBUG
                        this.errorLogger().info("reInitializeSubscriptions: re-init subscription: " + url + " RESULT: " + (status + 200));

                    }
                    else {
                        // DEBUG
                        this.errorLogger().info("reInitializeSubscriptions: re-init subscription: " + url + " RESULT: " + (status + 200));
                        reinitialized = false;
                    }
                }
            }
        }
        catch (Exception ex) {
            // giving up on this endpoint
            this.errorLogger().info("reInitializeSubscriptions: caught exception: " + ex.getMessage() + " (OK)... will retry later.");
        }

        // return true
        return reinitialized;
    }

    // validate the webhook
    private boolean validateWebhook() {
        boolean validated = false;

        // get the current webhook
        String url = this.m_mds.getWebhook();
        if (url != null && url.length() > 0) {
            if (url.equalsIgnoreCase(this.m_webhook_url) == true) {
                // DEBUG
                this.errorLogger().info("validateWebhook: webhook present and validated (" + url + ")");

                // VALIDATED
                validated = true;
            }
            else {
                // DEBUG
                this.errorLogger().info("validateWebhook: webhook present and but NOT VALIDATED. Expected: " + this.m_webhook_url + " Received: " + url);
            }
        }
        else {
            // DEBUG
            this.errorLogger().info("validateWebhook: webhook present and but NOT VALIDATED. Expected: " + this.m_webhook_url + " Received: <empty>");
        }

        // return our validation status
        return validated;
    }

    // validate the resource subscriptions
    private boolean validateResourceSubscriptions() {
        boolean validated = true;

        // loop through - either all the subscriptions are valid or we reset...
        for (int i = 0; i < this.m_subscriptions.size() && validated; ++i) {
            // check the ith subscription
            validated = this.m_mds.getEndpointResourceSubscriptionStatus((String)this.m_subscriptions.get(i));
        }

        // return the status;
        return validated;
    }
}
