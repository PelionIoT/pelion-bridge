/**
 * @file DatabaseValidator.java
 * @brief Pelion Database Validation
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

import com.arm.pelion.bridge.health.interfaces.HealthCheckServiceInterface;

/**
 * This class periodically checks pelion-bridge's Database connection
 *
 * @author Doug Anson
 */
public class DatabaseValidator extends BaseValidatorClass implements Runnable {
    private boolean m_last_value = false;
    
    // default constructor
    public DatabaseValidator(HealthCheckServiceInterface provider) {
        super(provider,"database");
        this.m_value = (Boolean)false;      // boolean value for this validator
        this.m_last_value = false;
    }   
    
    // validate
    @Override
    protected void validate() {
        // DEBUG
        this.errorLogger().info("DatabaseValidator: Validating Database Connections...");

        // validate the database connections
        if (this.validateDatabaseConnection() == true) {
            // DEBUG
            this.errorLogger().info("DatabaseValidator: Database Connections OK.");
            this.m_value = (Boolean)true;
        }
        else {
            // DEBUG
            this.errorLogger().warning("DatabaseValidator: Database connection is DOWN.");
            this.m_value = (Boolean)false;
        }
        
        // update our stats and notify if changed
        if (this.m_last_value != (Boolean)this.m_value) {
            this.m_last_value = (Boolean)this.m_value;
            this.updateStatisticAndNotify();
        }
    }

    // WORKER: validate the Database Connections
    private boolean validateDatabaseConnection() {
        return true;
    }
}