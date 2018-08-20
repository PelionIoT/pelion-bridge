/**
 * @file SerializableArrayList.java
 * @brief Serializable ArrayList decider
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
import java.util.ArrayList;

/**
 * SerializableArrayList
 * @author Doug Anson
 */
public class SerializableArrayList implements Distributable {
    private Orchestrator m_orchestrator = null;
    private InMemoryTemplatedArrayList<Serializable> m_im_arraylist = null;
    private DatabaseTemplatedArrayList<Serializable> m_db_arraylist = null;
    private String m_tablename = null;
    
    // constructor
    public SerializableArrayList(Orchestrator orchestrator,String tablename) {
        this.m_orchestrator = orchestrator;
        this.m_tablename = tablename;
        DatabaseConnector db = orchestrator.getDatabaseConnector();
        if (db != null) {
            this.m_db_arraylist = new DatabaseTemplatedArrayList(this,db,this.m_tablename);
        }
        else {
            this.m_im_arraylist = new InMemoryTemplatedArrayList(this);
        }
    }
    
    // inner list method
    public ArrayList<Serializable> list() {
        if (this.m_im_arraylist != null) {
            return this.m_im_arraylist;
        }
        if (this.m_db_arraylist != null) {
            return this.m_db_arraylist;
        }
        return null;
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
    public Serializable get(int index) {
        if (this.m_im_arraylist != null) {
            return this.m_im_arraylist.get(index);
        }
        if (this.m_db_arraylist != null) {
            return this.m_db_arraylist.get(index);
        }
        return null;
    }
    
    // add() method
    public void add(Serializable value) {
        if (this.m_im_arraylist != null) {
            this.m_im_arraylist.add(value);
        }
        if (this.m_db_arraylist != null) {
            this.m_db_arraylist.add(value);
        }
    }
    
    // remove() method
    public void remove(int index) {
        if (this.m_im_arraylist != null) {
            this.m_im_arraylist.remove(index);
        }
        if (this.m_db_arraylist != null) {
            this.m_db_arraylist.remove(index);
        }
    }

    // upsert
    @Override
    public void upsert(Serializable value) {
        if (this.m_im_arraylist != null) {
            this.m_im_arraylist.upsert(value);
        }
        if (this.m_db_arraylist != null) {
            this.m_db_arraylist.upsert(value);
        }
    }
    
    // delete
    @Override
    public void delete(int index) {
        if (this.m_im_arraylist != null) {
            this.m_im_arraylist.delete(index);
        }
        if (this.m_db_arraylist != null) {
            this.m_db_arraylist.delete(index);
        }
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
    public void upsert(String key, Serializable value) {
        // not used
    }

    // not used
    @Override
    public void delete(String key) {
        // not used
    }
}
