/**
 * @file MemoryStatistic.java
 * @brief Pelion bridge JVM Memory statistic
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

import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.health.interfaces.HealthCheckServiceInterface;
import java.util.Map;

/**
 * This class periodically checks how many device shadows its established
 *
 * @author Doug Anson
 */
public class MemoryStatistic extends BaseValidatorClass implements Runnable {    
    private String m_mem_key = null;
    private String m_uom = null;
    
    // default constructor
    public MemoryStatistic(HealthCheckServiceInterface provider,String mem_key,String uom) {
        super(provider,"mem_" + mem_key);
        this.m_mem_key = mem_key;
        this.m_uom = uom;
        this.m_value = (String)"";      // String value for this validator
    }   
    
    // validate
    @Override
    protected void validate() {
        String val = (String)this.gatherMemoryStatistic();
        if (val != null) {
            // update
            this.m_value = (String)val;
            this.updateStatisticAndNotify();

            // DEBUG
            this.errorLogger().info("MemoryStatistic: (" + this.m_mem_key + "): " + (String)this.m_value);
        }
        else {
            // no update
            this.errorLogger().info("MemoryStatistic: (" + this.m_mem_key + "): <no update>");
        }
    }

    // WORKER: how much memory have we consumed in the VM
    private String gatherMemoryStatistic() {
        Map json = Utils.gatherMemoryStatistics(this.errorLogger());
        if (json != null && json.get(this.m_mem_key) != null) {
            return "" + json.get(this.m_mem_key) + " " + this.m_uom;
        }
        return null;
    }
}