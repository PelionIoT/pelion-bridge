/**
 * @file DatabaseConnector.java
 * @brief Distributed Database Connector
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
package com.arm.pelion.bridge.data;

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.core.ErrorLogger;
import com.arm.pelion.bridge.preferences.PreferenceManager;
import java.io.Serializable;
import com.arm.pelion.bridge.data.interfaces.Database;

/**
 * DatabaseConnector
 * @author Doug Anson
 */
public class DatabaseConnector implements Database {
    private Orchestrator m_orchestrator = null;
    private boolean m_connected = false;
    private String m_tablename = null;
    
    // default connector
    public DatabaseConnector(Orchestrator orchestrator,String ip_address,int port,String username,String pw) {
        super();
        this.m_orchestrator = orchestrator;
        this.m_connected = false;
        
        // Announce
        this.errorLogger().info("Distributed Database Connector: IP: " + ip_address + " Port: " + port + " User: " + username + " PW: " + pw);
    }
    
    // close down the DB connection
    @Override
    public void close() {
        if (this.m_connected == true) {
            // XXX close down the database connection
            
        }
        
        // we are no longer connected
        this.m_connected = false;
    }
   
    // initialize the table and prep for sync
    @Override
    public void initialize(String tablename) {
        if (this.connect() == true) {
            // initialize our DB connection if needed... 
            
            // create our table if it does not exist
            
        }
    }

    // load from the table into the storage
    @Override
    public void load() {
        if (this.connect() == true) {
            // load the table into the HashMap
        }
    }

    // upsert (key/value pair)
    @Override
    public void upsert(String key,Serializable value) {
        if (this.connect() == true) {
            // check if the row is in the table
            
            // if the row is in the table, update its value
            
            // if the row is NOT in the table, add it
        }
    }
    
    // upsert value only (simply insert value into the table)
    @Override
    public void upsert(Serializable value) {
        if (this.connect() == true) {
            // simply add the value to the table...
        }
    }
    
    // delete row (by key/value pair)
    @Override
    public void delete(String key) {
        if (this.connect() == true) {
            // delete the row in the table specified by key
        }
    }

    // delete row (by row index)
    @Override
    public void delete(int index) {
        if (this.connect() == true) {
            // delete the row in the table specified by index
        }
    }
    
    // delete (table)
    public void deleteTable(String tablename) {
        if (this.connect() == true) {
            // delete the table 
        }
    }
    
    // connect if needed
    private boolean connect() {
        // Connect if not connected
        if (this.m_connected == false) {
            // connect and set our connected state
            
            // XXX
        }
        
        // return the connection status
        return this.m_connected;
    }
    
    // ErrorLogger
    private ErrorLogger errorLogger() {
        return this.m_orchestrator.errorLogger();
    }
    
    // Preferences
    private PreferenceManager preferences() {
        return this.m_orchestrator.preferences();
    }
}
