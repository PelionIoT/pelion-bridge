/**
 * @file TypeDecoder.java
 * @brief Class that aids in the parsing of an input String into its fundamental type (Float, Integer, String)
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
 * TypeDecoder class
 *
 * @author Doug Anson
 */
public class TypeDecoder extends BaseClass {

    private Double m_d = 0.0;
    private Float m_f = (float)0.0;
    private Boolean m_b = false;
    private Long m_l = (long)0;
    private Integer m_i = (int)0;
    private String m_s = null;

    // Default Constructor
    public TypeDecoder(ErrorLogger logger, PreferenceManager preferences) {
        super(logger, preferences);
    }

    // Is a Double Type?
    private boolean isDouble(Object data) {
        try {
            if (data != null && data instanceof String) {
                if (((String) data).contains(".") == true) {
                    this.m_d = Double.parseDouble(((String) data));
                    return true;
                }
            }
        }
        catch (NumberFormatException ex) {
            this.errorLogger().info("isDouble: Exception in Double parse: " + ex.getMessage());
        }
        return false;
    }
    
    // Is a Double Type?
    private boolean isFloat(Object data) {
        try {
            if (data != null && data instanceof String) {
                if (((String) data).contains(".") == true) {
                    this.m_f = Float.parseFloat(((String) data));
                    return true;
                }
            }
        }
        catch (NumberFormatException ex) {
            this.errorLogger().info("isDouble: Exception in Double parse: " + ex.getMessage());
        }
        return false;
    }

    // Is an Long Type?
    private boolean isLong(Object data) {
        try {
            if (data != null && data instanceof String) {
                if (((String) data).length() > 0) {
                    this.m_l = Long.parseLong(((String) data));
                    return true;
                }
            }
        }
        catch (NumberFormatException ex) {
            this.errorLogger().info("isInteger: Exception in Integer parse: " + ex.getMessage());
        }
        return false;
    }
    
    // Is an Integer Type?
    private boolean isInteger(Object data) {
        try {
            if (data != null && data instanceof String) {
                if (((String) data).length() > 0) {
                    this.m_i = Integer.parseInt(((String) data));
                    return true;
                }
            }
        }
        catch (NumberFormatException ex) {
            this.errorLogger().info("isInteger: Exception in Integer parse: " + ex.getMessage());
        }
        return false;
    }

    // Is an String Type?
    private boolean isString(Object data) {
        try {
            if (data != null && data instanceof String) {
                this.m_s = (String) data;
                return true;
            }
        }
        catch (Exception ex) {
            this.errorLogger().info("isString: Exception in String parse: " + ex.getMessage());
        }
        return false;
    }

    // Get the fundamenal value
    public Object getFundamentalValue(Object data) {
        if (data != null) {
            // direct comparisons first...
            if (data instanceof Double) {
                // DEBUG
                this.errorLogger().info("getFundamentalValue: Type is Double");

                this.m_d = (Double)data;
                return this.m_d;
            }
            else if (data instanceof Float) {
                // DEBUG
                this.errorLogger().info("getFundamentalValue: Type is Float");

                this.m_f = (Float)data;
                return this.m_f;
            }
            /*
            else if (data instanceof Long) {
                // DEBUG
                this.errorLogger().info("getFundamentalValue: Type is Long");

                this.m_l = (Long)data;
                return this.m_l;
            }
            */
            else if (data instanceof Integer) {
                // DEBUG
                this.errorLogger().info("getFundamentalValue: Type is Integer");

                this.m_i = (Integer)data;
                return this.m_i;
            }
            else if (data instanceof Boolean) {
                // DEBUG
                this.errorLogger().info("getFundamentalValue: Type is Boolean");

                this.m_b = (Boolean)data;
                return this.m_b;
            }
            else if (data instanceof String) {
                // return its value processed as a String
                return this.getFundamentalValueFromString((String)data);
            }
        }
        return data;
    }
    
    // get the fundamental object instance from the String representation
    public Object getFundamentalValueFromString(String data) {
        if (this.isDouble(data)) {
            // DEBUG
            this.errorLogger().info("getFundamentalValue: Type is Double");

            // return a Double
            return this.m_d;
        }
        else if (this.isFloat(data)) {
            // DEBUG
            this.errorLogger().info("getFundamentalValue: Type is Float");

            // return an Float
            return this.m_f;
        }
        /*
        else if (this.isLong(data)) {
            // DEBUG
            this.errorLogger().info("getFundamentalValue: Type is Long");

            // return an Integer
            return this.m_i;
        }
        */
        else if (this.isInteger(data)) {
            // DEBUG
            this.errorLogger().info("getFundamentalValue: Type is Integer");

            // return an Integer
            return this.m_i;
        }
        else if (this.isString(data)) {
            // DEBUG
            this.errorLogger().info("getFundamentalValue: Type is String");

            // return an Integer
            return this.m_s;
        }
        else {
            // DEBUG
            this.errorLogger().info("getFundamentalValue: Type is Object");

            // return itself... not a Double, Integer, or String 
            return data;
        }
    }
}