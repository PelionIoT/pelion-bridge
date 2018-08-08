/**
 * @file    SerializableHashMap.java
 * @brief Serializable HashMap decider
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
package com.arm.connector.bridge.data;

import com.arm.connector.bridge.coordinator.Orchestrator;
import com.arm.connector.bridge.core.ErrorLogger;
import com.arm.connector.bridge.data.interfaces.Distributable;
import com.arm.connector.bridge.preferences.PreferenceManager;
import java.io.Serializable;
import java.util.HashMap;

/**
 * SerializableHashMap
 * @author Doug Anson
 */
public class SerializableHashMap implements Distributable {
    private InMemoryTemplatedHashMap<Serializable> m_im_hashmap = null;
    private DatabaseTemplatedHashMap<Serializable> m_db_hashmap = null;
    private ErrorLogger m_error_logger = null;
    private PreferenceManager m_preferences = null;
    private String m_tablename = null;
    
    // constructor (Orchestrator)
    public SerializableHashMap(Orchestrator orchestrator,String tablename) {
        this(orchestrator.getDatabaseConnector(),orchestrator.errorLogger(),orchestrator.preferences(),tablename);
    }
    
    // constructor (pre-Orchestrator)
    public SerializableHashMap(DatabaseConnector db,ErrorLogger error_logger,PreferenceManager preferences,String tablename) {
        this.m_tablename = tablename;
        this.m_error_logger = error_logger;
        this.m_preferences = preferences;
        if (db != null) {
            this.m_db_hashmap = new DatabaseTemplatedHashMap(this,db,this.m_tablename);
        }
        else {
            this.m_im_hashmap = new InMemoryTemplatedHashMap(this);
        }
    }
    
    // inner map method
    public HashMap<String,Serializable> map() {
        if (this.m_im_hashmap != null) {
            return this.m_im_hashmap;
        }
        if (this.m_db_hashmap != null) {
            return this.m_db_hashmap;
        }
        return null;
    }
    
    // get() method
    public Serializable get(String key) {
        if (this.m_im_hashmap != null) {
            return this.m_im_hashmap.get(key);
        }
        if (this.m_db_hashmap != null) {
            return this.m_db_hashmap.get(key);
        }
        return null;
    }
    
    // put() method
    public void put(String key,Serializable value) {
        if (this.m_im_hashmap != null) {
            this.m_im_hashmap.put(key,value);
        }
        if (this.m_db_hashmap != null) {
            this.m_db_hashmap.put(key,value);
        }
    }
    
    // remove() method
    public void remove(String key) {
        if (this.m_im_hashmap != null) {
            this.m_im_hashmap.remove(key);
        }
        if (this.m_db_hashmap != null) {
            this.m_db_hashmap.remove(key);
        }
    }

    // upsert
    @Override
    public void upsert(String key,Serializable value) {
        if (this.m_im_hashmap != null) {
            this.m_im_hashmap.upsert(key,value);
        }
        if (this.m_db_hashmap != null) {
            this.m_db_hashmap.upsert(key,value);
        }
    }
    
    // delete
    @Override
    public void delete(String key) {
        if (this.m_im_hashmap != null) {
            this.m_im_hashmap.delete(key);
        }
        if (this.m_db_hashmap != null) {
            this.m_db_hashmap.delete(key);
        }
    }
    
    // ErrorLogger
    private ErrorLogger errorLogger() {
        return this.m_error_logger;
    }
    
    // Preferences
    private PreferenceManager preferences() {
        return this.m_preferences;
    }

    // not used
    @Override
    public void upsert(Serializable value) {
        // not used
    }

    // not used
    @Override
    public void delete(int index) {
        // not used
    }
}
