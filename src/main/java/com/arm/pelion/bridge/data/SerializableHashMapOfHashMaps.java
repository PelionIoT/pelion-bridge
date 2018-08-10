/**
 * @file SerializableHashMapOfHashMaps.java
 * @brief Serializable HashMap of HashMaps decider
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
import com.arm.pelion.bridge.data.interfaces.Distributable;
import com.arm.pelion.bridge.preferences.PreferenceManager;
import java.io.Serializable;
import java.util.HashMap;

/**
 * SerializableHashMapOfHashMaps
 * @author Doug Anson
 */
public class SerializableHashMapOfHashMaps implements Distributable {
    private Orchestrator m_orchestrator = null;
    private InMemoryTemplatedHashMap<InMemoryTemplatedHashMap<Serializable>> m_im_hashmap = null;
    private DatabaseTemplatedHashMap<DatabaseTemplatedHashMap<Serializable>> m_db_hashmap = null;
    private String m_tablename = null;
    private DatabaseConnector m_db = null;
    private SerializableHashMap m_outer_map = null;
    
    // constructor
    public SerializableHashMapOfHashMaps(Orchestrator orchestrator,String tablename) {
        this.m_orchestrator = orchestrator;
        this.m_tablename = tablename;
        this.m_db = orchestrator.getDatabaseConnector();
        if (this.m_db != null) {
            this.m_db_hashmap = new DatabaseTemplatedHashMap(this,this.m_db,this.m_tablename);
            
            // create the outer map
            this.m_outer_map = new SerializableHashMap(this.m_orchestrator,this.m_tablename);
        }
        else {
            this.m_im_hashmap = new InMemoryTemplatedHashMap(this);
        }
    }
    
    // get() method
    public HashMap<String,Serializable> get(String key) {
        if (this.m_im_hashmap != null) {
            return this.m_im_hashmap.get(key);
        }
        if (this.m_db_hashmap != null) {
            return this.m_db_hashmap.get(key);
        }
        return null;
    }
    
    // put() method
    public void put(String key,HashMap<String,Serializable> value) {
        if (this.m_im_hashmap != null) {
            try {
                // upcast into InMemory templated hashmap
                InMemoryTemplatedHashMap<Serializable> v = (InMemoryTemplatedHashMap<Serializable>)value;
                this.m_im_hashmap.put(key,v);
            }
            catch (Exception ex) {
                if (value != null) {
                    this.m_orchestrator.errorLogger().warning("SerializableHashMapOfHashMaps<InMemory>: upcasting failed for value type: " + value.getClass().getName() + " exception: " + ex.getMessage());
                }
                else {
                    this.m_orchestrator.errorLogger().warning("SerializableHashMapOfHashMaps<InMemory>: upcasting failed: NULL parameter. exception: " + ex.getMessage());
                }
            }
        }
        if (this.m_db_hashmap != null) {
            try {
                // upcast the Inner HashMap to our specific decorated HashMap
                DatabaseTemplatedHashMap<Serializable> v = (DatabaseTemplatedHashMap<Serializable>)value;

                // create the Inner tablename for the Inner HashMap
                String inner_tablename = this.m_tablename + this.m_orchestrator.getTablenameDelimiter() + this.createInnerID(v);
                v.setTablename(inner_tablename);

                // Add the HashMap to our HashMap
                this.m_db_hashmap.put(key,v);

                // update/insert the inner tablename into our table
                this.upsert(key,inner_tablename);
            }
            catch (Exception ex) {
                if (value != null) {
                    this.m_orchestrator.errorLogger().warning("SerializableHashMapOfHashMaps<DB>: upcasting failed for value type: " + value.getClass().getName() + " exception: " + ex.getMessage());
                }
                else {
                    this.m_orchestrator.errorLogger().warning("SerializableHashMapOfHashMaps<DB>: upcasting failed: NULL parameter. exception: " + ex.getMessage());
                }
            }
        }
    }
    
    // remove() method
    public void remove(String key) {
        if (this.m_im_hashmap != null) {
            this.m_im_hashmap.remove(key);
        }
        if (this.m_db_hashmap != null) {
            // remove from the HashMap
            this.m_db_hashmap.remove(key);
            
            // remove the Inner Table
            DatabaseTemplatedHashMap<Serializable> v = (DatabaseTemplatedHashMap<Serializable>)this.m_db_hashmap.get(key);
            if (v != null) {
                // inner deletion of the entire table 
                v.delete();
            }
            
            // remove the row (inner table name) from our table
            this.delete(key);
        }
    }
    
    // create the inner object ID
    private String createInnerID(Object o) {
        if (o != null) {
            // return the HashCode of the object
            return "" +  o.hashCode();
        }
        return null;
    }
    
    // upsert our outer table
    @Override
    public void upsert(String key,Serializable value) {
        this.m_outer_map.put(key, value);
    }
    
    // delete our outer table row
    @Override
    public void delete(String key) {
        this.m_outer_map.delete(key);
    }
    
    // ErrorLogger
    private ErrorLogger errorLogger() {
        return this.m_orchestrator.errorLogger();
    }
    
    // Preferences
    private PreferenceManager preferences() {
        return this.m_orchestrator.preferences();
    }
    
    // not used
    @Override
    public void upsert(Serializable value) {
        // not used
    }

    // not used
    @Override
    public void delete(int index) {
        //not used
    }
}