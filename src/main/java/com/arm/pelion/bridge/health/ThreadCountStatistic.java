/**
 * @file ThreadCountStatistic.java
 * @brief Pelion JVM Thread Count Statistic
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
package com.arm.pelion.bridge.health;

import com.arm.pelion.bridge.coordinator.processors.arm.PelionProcessor;
import com.arm.pelion.bridge.health.interfaces.HealthCheckServiceInterface;

/**
 * This class periodically checks how many threads we have active in our JVM
 *
 * @author Doug Anson
 */
public class ThreadCountStatistic extends BaseValidatorClass implements Runnable {
    private int m_last_value = -1;
    
    // default constructor
    public ThreadCountStatistic(HealthCheckServiceInterface provider) {
        super(provider,"thread_count");
        this.m_value = (Integer)0;      // Integer value for this validator
        this.m_last_value = -1;
    }   
    
    // validate
    @Override
    protected void validate() {
        // DEBUG
        this.errorLogger().info("ThreadCountStatistic: Checking active thread count...");
        this.m_value = (Integer)this.getActiveThreadCount();
        
        if (this.m_last_value != (Integer)this.m_value) {
            this.m_last_value = (Integer)this.m_value;
            this.errorLogger().info("ThreadCountStatistic: Updated active thread count: " + (Integer)this.m_value);
            this.updateStatisticAndNotify();
        }
    }

    // WORKER: query how many active threads we currently have
    private int getActiveThreadCount() {
        PelionProcessor p = (PelionProcessor)this.m_provider.getPelionProcessor();
        return p.getActiveThreadCount();
    }
}