/**
 * @file    DatabaseTemplatedHashMap.java
 * @brief Database HashMap implementation with distributable decoration
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

import java.util.HashMap;
import com.arm.pelion.bridge.data.interfaces.Distributable;
import java.io.Serializable;

/**
 * DatabaseTemplatedHashMap
 * @author Doug Anson
 * @param <T> - template parameter for the HashMap value type
 */
public class DatabaseTemplatedHashMap<T> extends HashMap<String,T> implements Distributable {
    private DatabaseConnector m_db = null;
    private String m_tablename = null;
    private Object m_container = null;
    
    // default constructor
    public DatabaseTemplatedHashMap(Object container,DatabaseConnector db,String tablename) {
        super();
        this.m_container = container;
        this.m_db = db;
        this.setTablename(tablename);
    }
    
    // get the container class
    public Object container() {
        return this.m_container;
    }
    
    // set the table name
    public void setTablename(String tablename) {
        this.m_tablename = tablename;
        this.initialize();
    }
    
    // get the tablename
    public String getTablename() {
        return this.m_tablename;
    }
    
    // Override the HashMap::put() 
    @Override
    public T put(String key,T value) {
        super.put(key,(T)value);
        this.upsert(key,(Serializable)value);
        return value;
    }
    
    // Override the HashMap::remove() 
    public void remove(String key) {
        super.remove(key);
        this.delete(key);
    }

    // upsert the DB with the latest HashMap entry...
    @Override
    public void upsert(String key, Serializable value) {
        // See if this key already exists
        
        // Key Exists: upsert the value
        
        // Key does not Exist: add to the table
        
        // Commit
    }

    // delete from the DB the deleted HashMap entry
    @Override
    public void delete(String key) {
        // Delete the row cooresponding to the requestd Key
        
        // Commit
    }
    
    // delete the table associated with the HashMap
    public void delete() {        
        // Delete the TABLE and all its contents
        
        // Commit
    }
    
    // initialize the DB and connect to it...
    private void initialize() {
        this.m_db.close();
        this.m_db.initialize(this.m_tablename);
        this.m_db.load();
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

