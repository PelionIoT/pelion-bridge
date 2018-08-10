/**
 * @file    PreferenceManager.java
 * @brief preferences manager
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
package com.arm.pelion.bridge.preferences;

import com.arm.pelion.bridge.core.BaseClass;
import com.arm.pelion.bridge.core.ErrorLogger;
import com.arm.pelion.bridge.data.DatabaseConnector;
import com.arm.pelion.bridge.data.SerializableHashMap;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

/**
 * preferences manager
 *
 * @author Doug Anson
 */
public class PreferenceManager extends BaseClass {
    // DEBUG tag
    private static final String DEFAULT_LOG_TAG = "PreferenceManager";
    
    // Config Tags
    private static final String SERVICE_PROPERTIES_FILE_TAG = "config_file";      // passed as -Dconfig_file="../conf/service.properties"
    private static final String IS_MASTER_NODE_TAG = "is_master_node";            // passed as -Dis_master_node=true
    
    // Defaults
    private static final boolean DEFAULT_IS_MASTER_NODE = true;
    private static final int DEFAULT_INT_VALUE = -1;
    private static final float DEFAULT_FLOAT_VALUE = (float) -1.0;
    private static final String DEFAULT_PROPERTIES_FILE = "WEB-INF/classes/service.properties";
    
    private String m_properties_file = null;
    private boolean m_is_master_node = DEFAULT_IS_MASTER_NODE;          // default is a master node
    private Properties m_config_properties = null;                      // DB config properties
    private String m_log_tag = DEFAULT_LOG_TAG;
    
    // default that the node is a master node... hence no caching/sync of Properties       
    private SerializableHashMap m_cache = null;
    
    // constructor
    public PreferenceManager(ErrorLogger error_logger,String log_tag) {
        super(error_logger, null);
        this.m_properties_file = DEFAULT_PROPERTIES_FILE;
        this.m_is_master_node = DEFAULT_IS_MASTER_NODE;
        this.m_log_tag = log_tag;
        this.readPreferencesFile();
    }
    
    // initialize caching (defaulted node master)
    public void initializeCache(DatabaseConnector db) {
        this.initializeCache(db,true);
    }
    
    // initialize caching (full)
    public void initializeCache(DatabaseConnector db,boolean is_master_node) {
        this.m_cache = new SerializableHashMap(db,this.errorLogger(),this,"PROPERTIES");
        this.m_is_master_node = is_master_node;
        if (this.m_is_master_node == true) {
            // Properties --> Cache (db save)
            this.cachePreferences();
        }
    }

    public boolean booleanValueOf(String key) {
        return this.booleanValueOf(key, null);
    }

    public boolean booleanValueOf(String key, String suffix) {
        boolean result = false;
        String value = this.valueOf(key, suffix);
        if (value != null && value.length() > 0 && value.equalsIgnoreCase("true")) {
            result = true;
        }
        return result;
    }

    public int intValueOf(String key) {
        return this.intValueOf(key, null);
    }

    public int intValueOf(String key, String suffix) {
        int result = DEFAULT_INT_VALUE;
        String value = this.valueOf(key, suffix);
        try {
            if (value != null && value.length() > 0) {
                result = Integer.parseInt(value);
            }
        }
        catch (NumberFormatException ex) {
            result = DEFAULT_INT_VALUE;
        }
        return result;
    }

    public float floatValueOf(String key) {
        return this.floatValueOf(key, null);
    }

    public float floatValueOf(String key, String suffix) {
        float result = DEFAULT_FLOAT_VALUE;
        String value = this.valueOf(key, suffix);
        try {
            if (value != null && value.length() > 0) {
                result = Float.parseFloat(value);
            }
        }
        catch (NumberFormatException ex) {
            result = DEFAULT_FLOAT_VALUE;
        }
        return result;
    }

    public String valueOf(String key) {
        return this.valueOf(key, null);
    }

    public String valueOf(String key, String suffix) {
        String value = null;
        if (this.m_cache != null && this.m_is_master_node == false) {
            // query the cache... if it has the value, go with it...
            value = (String)this.m_cache.get(key);
            
            // if we dont have a value, then we query the Properties object as a backing store...
            if (value == null || value.length() == 0) {
                // get the property from the properties object
                value = this.m_config_properties.getProperty(this.createKey(key, suffix));
            }
        }
        else {
            // just get the property from the properties object
            value = this.m_config_properties.getProperty(this.createKey(key, suffix));
        }
        
        // DEBUG
        //this.errorLogger().info("Preference: [" + this.createKey(key,suffix) + "] = [" + value + "]");
        
        // return the value
        return value;
    }

    private String createKey(String key, String suffix) {
        // default
        String full_key = key;

        // look for the suffix... if its there, use it.
        if (suffix != null && suffix.length() > 0) {
            full_key = key + "_" + suffix;
        }

        // special case handling: if "_0", then treat as ""
        if (suffix != null && suffix.length() > 0 && suffix.equalsIgnoreCase("0")) {
            // reset to the key itself only... 
            full_key = key;
        }

        // return the full key
        return full_key;
    }

    private String getAbsolutePath(String file) {
        String fq_file = file;

        try {
            String dir = System.getProperty("user.dir");
            String separator = System.getProperty("file.separator");
            fq_file = dir + separator + file;
            //this.errorLogger().info("getAbsolutePath: dir: " + dir + " file: " + file + " FQ file: " + fq_file);
        }
        catch (Exception ex) {
            this.errorLogger().warning(this.m_log_tag + ": unable to calculate absolute path for: " + file);
            fq_file = file;
        }
        return fq_file;
    }
    
    // convert a String boolean value to a actual boolean value
    private boolean stringToBoolean(String bool_str) {
        boolean result = false;
        
        if (bool_str != null && bool_str.equalsIgnoreCase("true")) {
            result = true;
        }
        
        // return the result
        return result;
    }

    // read the preferences file
    private boolean readPreferencesFile() {
        boolean success = false;

        try {
            this.m_properties_file = System.getProperty(PreferenceManager.SERVICE_PROPERTIES_FILE_TAG, PreferenceManager.DEFAULT_PROPERTIES_FILE);
            this.m_is_master_node = this.stringToBoolean(System.getProperty(PreferenceManager.IS_MASTER_NODE_TAG,"true"));
                    
            // master node configuraton 
            if (this.m_is_master_node == true) {
                // we are a master instance
                this.errorLogger().warning(this.m_log_tag + ": Node Configuration state is MASTER (default)");
            }
            else {
                // we are a worker instance
                this.errorLogger().warning(this.m_log_tag + ": Node Configuration state is WORKER");
            }
            
            // read the preference file in...
            success = this.readPreferencesFile(this.getAbsolutePath(this.m_properties_file), false);
            if (!success) {
                this.errorLogger().warning(this.m_log_tag + ": WARNING - Unable to read specified config file: " + this.getAbsolutePath(this.m_properties_file) + " trying default: " + PreferenceManager.DEFAULT_PROPERTIES_FILE);
                
                // read the default preferences file...
                return this.readPreferencesFile(PreferenceManager.DEFAULT_PROPERTIES_FILE);
            }
        }
        catch (Exception ex) {
            success = this.readPreferencesFile(PreferenceManager.DEFAULT_PROPERTIES_FILE);
        }
        return success;
    }
    
    // cache our preferences to InMemory
    private void cachePreferences() {
        if (this.m_cache != null) {
            Enumeration e = this.m_config_properties.propertyNames();
            while (e.hasMoreElements()) {
              String key = (String) e.nextElement();
              String value = (String)this.m_config_properties.getProperty(key);
              this.m_cache.put(key, value);
            }
        }
    }

    // read the DB configuration properties file
    private boolean readPreferencesFile(String file) {
        return this.readPreferencesFile(file, true);
    }

    // read the DB configuration properties file
    private boolean readPreferencesFile(String file, boolean war_internal) {
        boolean success = false;
        FileInputStream fis = null;
        if (this.m_config_properties == null) {
            try {
                this.m_config_properties = new Properties();
                if (war_internal) {
                    this.m_config_properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream(file));
                }
                else {
                    fis = new FileInputStream(file);
                    this.m_config_properties.load(fis);
                    if (fis != null) {
                        fis.close();
                    }
                }
                this.errorLogger().info(this.m_log_tag + ": Read configuration file: " + file + " successfully");
                success = true;
            }
            catch (IOException ex) {
                this.errorLogger().critical(this.m_log_tag + ": Unable to read properties file: " + file);
                this.m_config_properties = null;
            }
        }
        return success;
    }

    // reload the configuration
    public void reload() {
        this.errorLogger().warning(this.m_log_tag +": Reloading service properties/configuration...");
        this.m_config_properties = null;
        this.readPreferencesFile();
        this.cachePreferences();
    }
}
