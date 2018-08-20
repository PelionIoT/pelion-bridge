/**
 * @file BaseClass.java
 * @brief base class for most classes defined in pelion-bridge
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
package com.arm.pelion.bridge.core;

import com.arm.pelion.bridge.preferences.PreferenceManager;

/**
 * Base Class for fundamental base class for pelion-bridge
 *
 * @author Doug Anson
 */
public class BaseClass {

    private ErrorLogger m_error_logger = null;
    private PreferenceManager m_preference_manager = null;

    /**
     * default constructor
     * @param error_logger
     * @param preference_manager
     */
    public BaseClass(ErrorLogger error_logger, PreferenceManager preference_manager) {
        this.m_error_logger = error_logger;
        this.m_preference_manager = preference_manager;
    }

    /**
     * Get our error handler
     * @return
     */
    public com.arm.pelion.bridge.core.ErrorLogger errorLogger() {
        return this.m_error_logger;
    }

    /**
     * Get the preferences manager
     * @return
     */
    public com.arm.pelion.bridge.preferences.PreferenceManager preferences() {
        return this.m_preference_manager;
    }

    /**
     * get a preference value
     * @param key
     * @return
     */
    protected String prefValue(String key) {
        return this.prefValue(key, null);
    }

    /**
     * get a preference value
     * @param key
     * @param suffix
     * @return
     */
    protected String prefValue(String key, String suffix) {
        if (this.m_preference_manager != null) {
            return this.m_preference_manager.valueOf(key, suffix);
        }
        return null;
    }

    /**
     * get a preference value with a default if none exists
     * @param key
     * @param def_value
     * @return
     */
    protected String prefValueWithDefault(String key, String def_value) {
        return this.prefValueWithDefault(key, null, def_value);
    }

    /**
     * get a preference value with a default if none exists
     * @param key
     * @param suffix
     * @param def_value
     * @return
     */
    protected String prefValueWithDefault(String key, String suffix, String def_value) {
        String value = this.prefValue(key, suffix);
        if (value != null && value.length() > 0) {
            return value;
        }
        return def_value;
    }

    /**
     * get an integer preference value
     * @param key
     * @return
     */
    protected int prefIntValue(String key) {
        return this.prefIntValue(key, null);
    }

    /**
     * get an integer preference value
     * @param key
     * @param suffix
     * @return
     */
    protected int prefIntValue(String key, String suffix) {
        if (this.m_preference_manager != null) {
            return this.m_preference_manager.intValueOf(key, suffix);
        }
        return -1;
    }

    /**
     * get a float preference value
     * @param key
     * @return
     */
    protected float prefFloatValue(String key) {
        return this.prefFloatValue(key, null);
    }

    /**
     * get a float preference value
     * @param key
     * @param suffix
     * @return
     */
    protected float prefFloatValue(String key, String suffix) {
        if (this.m_preference_manager != null) {
            return this.m_preference_manager.floatValueOf(key, suffix);
        }
        return (float) -1.0;
    }

    /**
     * get a boolean preference value
     * @param key
     * @return
     */
    protected boolean prefBoolValue(String key) {
        return this.prefBoolValue(key, null);
    }

    /**
     * get a boolean preference value
     * @param key
     * @param suffix
     * @return
     */
    protected boolean prefBoolValue(String key, String suffix) {
        if (this.m_preference_manager != null) {
            return this.m_preference_manager.booleanValueOf(key, suffix);
        }
        return false;
    }
}
