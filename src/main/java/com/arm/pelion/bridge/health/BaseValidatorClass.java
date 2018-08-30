/**
 * @file BaseValidatorClass.java
 * @brief Base validator class
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
package com.arm.pelion.bridge.health;

import com.arm.pelion.bridge.core.BaseClass;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.health.interfaces.HealthCheckServiceInterface;

/**
 * Base Validator Class
 * @author Doug Anson
 */
public abstract class BaseValidatorClass extends BaseClass implements Runnable {
    private static final int DEF_CHECK_INTERVAL_MS = 15000;     // 15 seconds
    protected HealthCheckServiceInterface m_provider = null;
    protected String m_key = null;
    protected Object m_value = false;
    protected String m_description = null;
    protected boolean m_running = false;
    protected int m_validator_interval_ms = 0;
    private boolean m_override_check_interval = true;  // true: nail to default, false: from config file
    
    // main constructor
    public BaseValidatorClass(HealthCheckServiceInterface provider,String key) {
        super(provider.getOrchestrator().errorLogger(),provider.getOrchestrator().preferences());
        this.m_provider = provider;
        this.m_key = this.preferences().valueOf(key + "_validator_key");
        this.m_description = this.preferences().valueOf(key + "_validator_description");
        this.m_validator_interval_ms = this.preferences().intValueOf(key + "_validator_interval_ms");
        if (this.m_validator_interval_ms <= 0 || this.m_override_check_interval == true) {
            this.m_validator_interval_ms = DEF_CHECK_INTERVAL_MS;
        }
        this.m_value = null;
        this.m_running = false; 
    }
    
    // update the statistic and notify
    protected void updateStatisticAndNotify() {
        // update the health statistic
        this.m_provider.updateHealthStatistic(new HealthStatistic(this.m_key,this.m_description,this.m_value));
    }
    
    // run 
    @Override
    public void run() {
        this.m_running = true;
        this.validationLoop();
    }
    
    // halt 
    public void halt() {
        this.m_running = false;
    }
    
    // main validation loop
    private void validationLoop() {
        while (this.m_running == true) {
            try {
                // validate the statistic
                this.validate();
                
                // sleep for a bit...
                Utils.waitForABit(this.errorLogger(),this.m_validator_interval_ms);
            }
            catch (Exception ex) {
                this.errorLogger().warning("BaseValidator: Exception caught: " + ex.getMessage(),ex);
            }
        }
    }
    
    // abstract method - validate()
    protected abstract void validate();
}
