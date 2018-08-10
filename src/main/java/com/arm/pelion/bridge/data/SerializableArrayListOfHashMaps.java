/**
 * @file    SerializableArrayListOfHashMaps.java
 * @brief Serializable ArrayList of HashMaps decider
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
 * SerializableArrayListOfHashMaps
 * @author Doug Anson
 */
public class SerializableArrayListOfHashMaps implements Distributable {
    private Orchestrator m_orchestrator = null;
    private InMemoryTemplatedArrayList<InMemoryTemplatedHashMap<Serializable>> m_im_arraylist = null;
    private DatabaseTemplatedArrayList<DatabaseTemplatedHashMap<Serializable>> m_db_arraylist = null;
    private String m_tablename = null;
    private DatabaseConnector m_db = null;
    private SerializableArrayList m_outer_list = null;
    
    // constructor
    public SerializableArrayListOfHashMaps(Orchestrator orchestrator,String tablename) {
        this.m_orchestrator = orchestrator;
        this.m_tablename = tablename;
        this.m_db = orchestrator.getDatabaseConnector();
        if (this.m_db != null) {
            this.m_db_arraylist = new DatabaseTemplatedArrayList(this,this.m_db,this.m_tablename);
            
            // create the outer map
            this.m_outer_list = new SerializableArrayList(this.m_orchestrator,this.m_tablename);
        }
        else {
            this.m_im_arraylist = new InMemoryTemplatedArrayList(this);
        }
    }
    
    // size() method
    public int size() {
        if (this.m_im_arraylist != null) {
            return this.m_im_arraylist.size();
        }
        if (this.m_db_arraylist != null) {
            return this.m_db_arraylist.size();
        }
        return 0;
    }
    
    // get() method
    public HashMap<String,Serializable> get(int index) {
        if (this.m_im_arraylist != null) {
            return this.m_im_arraylist.get(index);
        }
        if (this.m_db_arraylist != null) {
            return this.m_db_arraylist.get(index);
        }
        return null;
    }
    
    // paddut() method
    public void add(HashMap<String,Serializable> value) {
        if (this.m_im_arraylist != null) {
            try {
                // upcast into InMemory hashmap
                InMemoryTemplatedHashMap<Serializable> v = (InMemoryTemplatedHashMap<Serializable>)value;
                this.m_im_arraylist.add(v);
            }
            catch (Exception ex) {
                if (value != null) {
                    this.m_orchestrator.errorLogger().warning("SerializableArrayListOfHashMaps<InMemory>: upcasting failed for value type: " + value.getClass().getName() + " exception: " + ex.getMessage(), ex);
                }
                else {
                    this.m_orchestrator.errorLogger().warning("SerializableArrayListOfHashMaps<InMemory>: upcasting failed: NULL parameter. exception: " + ex.getMessage(), ex);
                }
            }
        }
        if (this.m_db_arraylist != null) {
            try {
                // upcast the Inner HashMap to our specific decorated HashMap
                DatabaseTemplatedHashMap<Serializable> v = (DatabaseTemplatedHashMap<Serializable>)value;

                // create the Inner tablename for the Inner HashMap
                String inner_tablename = this.m_tablename + this.m_orchestrator.getTablenameDelimiter() + this.createInnerID(v);
                v.setTablename(inner_tablename);

                // Add the HashMap to our HashMap
                this.m_db_arraylist.add(v);

                // update/insert the inner tablename into our table
                this.upsert(inner_tablename);
            }
            catch (Exception ex) {
                if (value != null) {
                    this.m_orchestrator.errorLogger().warning("SerializableArrayListOfHashMaps<DB>: upcasting failed for value type: " + value.getClass().getName() + " exception: " + ex.getMessage(), ex);
                }
                else {
                    this.m_orchestrator.errorLogger().warning("SerializableArrayListOfHashMaps<DB>: upcasting failed: NULL parameter. exception: " + ex.getMessage(), ex);
                }
            }
        }
    }
    
    // remove() method
    public void remove(int index) {
        if (this.m_im_arraylist != null) {
            this.m_im_arraylist.remove(index);
        }
        if (this.m_db_arraylist != null) {
            // remove from the HashMap
            this.m_db_arraylist.remove(index);
            
            // remove the Inner Table
            DatabaseTemplatedHashMap<Serializable> v = (DatabaseTemplatedHashMap<Serializable>)this.m_db_arraylist.get(index);
            if (v != null) {
                // inner deletion of the entire table 
                v.delete();
            }
            
            // remove the row (inner table name) from our table
            this.delete(index);
        }
    }
    
    // outer table insert
    @Override
    public void upsert(Serializable value) {
        this.m_outer_list.upsert(value);
    }

    // outer table delete
    @Override
    public void delete(int index) {
        this.m_outer_list.delete(index);
    }
    
    // create the inner object ID
    private String createInnerID(Object o) {
        if (o != null) {
            // return the HashCode of the object
            return "" +  o.hashCode();
        }
        return null;
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
    public void upsert(String key,Serializable value) {
        // not used
    }
    
    // not used
    @Override
    public void delete(String key) {
        // not used
    }
}