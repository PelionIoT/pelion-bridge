/**
 * @file    WebhookValidator.java
 * @brief pelion webhook validation checker
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
package com.arm.pelion.bridge.coordinator.processors.core;

import com.arm.pelion.bridge.coordinator.processors.arm.PelionProcessor;
import com.arm.pelion.bridge.core.BaseClass;
import com.arm.pelion.bridge.core.Utils;

/**
 * Pelion Webhook Validation checker/restorer
 * @author Doug Anson
 */
public class WebhookValidator extends BaseClass implements Runnable {
    private static final int DEFAULT_WEBHOOK_VALIDATE_INTERVAL_MS = 60000;      // 1 minute
    private static final int DEFAULT_MAX_RETRIES = 10;                          // 10 tries to reset the webhook..
    
    private boolean m_run_validator = false;
    private PelionProcessor m_pelion_processor = null;
    private Thread m_thread = null;
    private int m_webhook_validate_interval_ms = DEFAULT_WEBHOOK_VALIDATE_INTERVAL_MS;
    private int m_max_retries = DEFAULT_MAX_RETRIES;
    
    // constructor
    public WebhookValidator(PelionProcessor pelion_processor) {
        super(pelion_processor.errorLogger(),pelion_processor.preferences());
        this.m_pelion_processor = pelion_processor;
     
        // only configure if we are using webhooks...
        if (this.m_pelion_processor != null && this.m_pelion_processor.webHookEnabled() == true) {
            // set the validation interval
            this.m_webhook_validate_interval_ms = this.prefIntValue("webhook_validation_interval");
            if (this.m_webhook_validate_interval_ms <= 0) {
                this.m_webhook_validate_interval_ms = DEFAULT_WEBHOOK_VALIDATE_INTERVAL_MS;
            }
            
            // DEBUG
            this.errorLogger().warning("WebhookValidator: Validate Interval set to: " + m_webhook_validate_interval_ms + " ms");

            // create our thread...
            this.m_run_validator = true;
            this.m_thread = new Thread(this);
            try {
                // start the thread...
                this.m_thread.start();
            }
            catch (Exception ex) {
                // exception in thread creation
                this.errorLogger().warning("WebhookValdiator: ERROR: Unable to start webhook valdiator: " + ex.getMessage());
            }
        }
        else if (this.m_pelion_processor != null) {
            // not using webhooks...
            this.errorLogger().warning("WebhookValdiator: DISABLED (using long polling) - OK");
        }
    }
    
    // run our webhook validation and restoration
    @Override
    public void run() {
        if (this.m_pelion_processor != null && this.m_pelion_processor.webHookEnabled() == true) {
            while(this.m_run_validator == true) {
                Utils.waitForABit(this.errorLogger(), this.m_webhook_validate_interval_ms);
                this.validateWebhook();
            }
            this.errorLogger().warning("WebhookValidator: WARNING: Webhook validator loop has exited. No longer validating webhook.");
        }
    }
    
    // validate the webhook - reset it if necessary...
    private void validateWebhook() {
        if (this.m_pelion_processor != null) {
            boolean ok = this.m_pelion_processor.webhookOK();
            if (ok) {
                // webhook is OK
                this.errorLogger().info("WebhookValidator: Webhook appears OK");
            }
            else {
                // webhook is not OK.. we will reset it...
                this.errorLogger().warning("WebhookValidator: Webhook is DOWN. Resetting...");
                int backoff_interval_ms = 10000;            // 10 seconds
                for(int i=0;!ok && i<this.m_max_retries;++i) {
                    // Exp backoff - wait for a bit
                    Utils.waitForABit(this.errorLogger(), (i+1)*backoff_interval_ms);
                    
                    // attempt reset
                    ok = this.m_pelion_processor.resetWebhook();
                    
                    // DEBUG
                    if (ok) {
                        // SUCCESS
                        this.errorLogger().warning("WebhookValidator: Webhook reset succeeded. Webhook appears OK");
                    }
                    else {
                        // FAILURE
                        this.errorLogger().info("WebhookValidator: Webhook reset FAILED. Retrying...");
                    }
                }
                
                // final note
                if (!ok) {
                    // final failure
                    this.errorLogger().warning("WebhookValidator: Webhook resetting has FAILED. Unable to re-establish webhook");
                }
            }
        }
    }
}
