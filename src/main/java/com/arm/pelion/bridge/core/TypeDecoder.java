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
    private Integer m_i = 0;
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
                    Double d = Double.parseDouble(((String) data));
                    this.m_d = d;
                    return true;
                }
            }
        }
        catch (NumberFormatException ex) {
            this.errorLogger().info("isDouble: Exception in Double parse: " + ex.getMessage());
        }
        return false;
    }

    // Is an Integer Type?
    private boolean isInteger(Object data) {
        try {
            if (data != null && data instanceof String) {
                if (((String) data).length() > 0) {
                    Integer i = Integer.parseInt(((String) data));
                    this.m_i = i;
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
            if (data instanceof Integer) {
                // DEBUG
                this.errorLogger().info("getFundamentalValue: Type is Integer");

                this.m_i = (Integer)data;
                return this.m_i;
            }
            else if (data instanceof Float) {
                // DEBUG
                this.errorLogger().info("getFundamentalValue: Type is Float(Double)");

                this.m_d = (Double)data;
                return this.m_d;
            }
            else if (data instanceof Double) {
                // DEBUG
                this.errorLogger().info("getFundamentalValue: Type is Double");

                this.m_d = (Double)data;
                return this.m_d;
            }
            else {
                // secondary checks next
                if (this.isDouble((String) data)) {
                    // DEBUG
                    this.errorLogger().info("getFundamentalValue: Type is Double");

                    // return a Double
                    return this.m_d;
                }
                else if (this.isInteger((String) data)) {
                    // DEBUG
                    this.errorLogger().info("getFundamentalValue: Type is Integer");

                    // return an Integer
                    return this.m_i;
                }
                else if (this.isString((String) data)) {
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
        return data;
    }
}