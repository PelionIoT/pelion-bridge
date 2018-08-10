/**
 * @file    ErrorLogger.java
 * @brief error logging facility
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

/**
 * Error Handler
 *
 * @author Doug Anson
 */
public class ErrorLogger extends BaseClass {

    // Default message
    /**
     *
     */
    public static final String DEFAULT_MESSAGE = "<No Message-OK>";

    // Logging classifications
    /**
     *
     */
    public static final int INFO = 0x0001;     // informational

    /**
     *
     */
    public static final int WARNING = 0x0002;     // warning

    /**
     *
     */
    public static final int CRITICAL = 0x0004;     // critical error

    // masks
    /**
     *
     */
    public static final int SHOW_ALL = 0x00FF;     // show all

    /**
     *
     */
    public static final int SHOW_INFO = 0x0001;     // show INFO only

    /**
     *
     */
    public static final int SHOW_WARNING = 0x0002;     // show WARNING only

    /**
     *
     */
    public static final int SHOW_CRITICAL = 0x0004;     // show CRITICAL only

    // maxmium number of tracked log entries
    /**
     *
     */
    public static final int MAX_LOG_ENTRIES = 500;      // reset the list after retaining this many entries

    private String m_message = null;                    // our message
    private Exception m_exception = null;               // our exception
    private int m_level = 0;                            // error classification level
    private int m_mask = SHOW_ALL;                      // default error classification mask
    private volatile ArrayList<String> m_log = null;    // error log

    /**
     * constructor
     */
    public ErrorLogger() {
        super(null, null);
        this.m_message = ErrorLogger.DEFAULT_MESSAGE;
        this.m_exception = null;
        this.m_level = ErrorLogger.INFO;
        this.m_mask = ErrorLogger.SHOW_ALL;
        this.m_log = new ArrayList<>();
    }

    /*
    * Configure the logging level
     */
    public void configureLoggingLevel(PreferenceManager preferences) {
        String config = preferences.valueOf("mds_bridge_error_level", null);
        if (config != null && config.length() > 0) {
            int mask = 0;
            if (config.contains("info")) {
                mask |= ErrorLogger.SHOW_INFO;
            }
            if (config.contains("warning")) {
                mask |= ErrorLogger.SHOW_WARNING;
            }
            if (config.contains("critical")) {
                mask |= ErrorLogger.SHOW_CRITICAL;
            }
            if (config.contains("all")) {
                mask = ErrorLogger.SHOW_ALL;
            }
            this.setLoggingMask(mask);
        }
    }

    // set the logging mask
    private void setLoggingMask(int mask) {
        this.m_mask = mask;
    }

    // buffer the log entry
    private void buffer(String entry) {
        if (entry != null && entry.length() > 0) {
            if (this.m_log.size() >= MAX_LOG_ENTRIES) {
                this.m_log.clear();
            }
            this.m_log.add(entry);
        }
    }
    
    // Ping(): tracer for call path debugging
    public void ping() {
        this.ping("PING");
    }
    public void ping(String message) {
        Exception ex = new Exception ("PingException");
        this.warning(message + " Exception: " + ex.getMessage(),ex);
    }

    /**
     * log entry - messages only
     *
     * @param message
     */
    public void info(String message) {
        this.log(ErrorLogger.INFO, message, null);
    }

    /**
     *
     * @param message
     */
    public void warning(String message) {
        this.log(ErrorLogger.WARNING, message, null);
    }

    /**
     *
     * @param message
     */
    public void critical(String message) {
        this.log(ErrorLogger.CRITICAL, message, null);
    }

    /**
     * log entry - messages and exceptions
     *
     * @param message
     * @param ex
     */
    public void info(String message, Exception ex) {
        this.log(ErrorLogger.INFO, message, ex);
    }

    /**
     *
     * @param message
     * @param ex
     */
    public void warning(String message, Exception ex) {
        this.log(ErrorLogger.WARNING, message, ex);
    }

    /**
     *
     * @param message
     * @param ex
     */
    public void critical(String message, Exception ex) {
        this.log(ErrorLogger.CRITICAL, message, ex);
    }

    /**
     * log entry - exceptions only
     *
     * @param ex
     */
    public void info(Exception ex) {
        this.log(ErrorLogger.INFO, ErrorLogger.DEFAULT_MESSAGE, ex);
    }

    /**
     *
     * @param ex
     */
    public void warning(Exception ex) {
        this.log(ErrorLogger.WARNING, ErrorLogger.DEFAULT_MESSAGE, ex);
    }

    /**
     *
     * @param ex
     */
    public void critical(Exception ex) {
        this.log(ErrorLogger.CRITICAL, ErrorLogger.DEFAULT_MESSAGE, ex);
    }

    // log a message (base)
    private void log(int level, String message, Exception exception) {
        this.m_message = message;
        this.m_exception = exception;
        this.m_level = level;
        this.log();
    }

    // post the log
    private void log() {
        // check what level we want to display
        if ((this.m_mask & this.m_level) != 0) {
            if (this.m_exception != null) {
                if (this.m_message != null) {
                    // log the message
                    System.out.println(this.prettyLevel() + this.m_message + " Exception: " + this.m_exception + ".\n\nStackTrace: " + this.stackTraceToString(this.m_exception));
                    this.buffer(this.m_message + " Exception: " + this.m_exception + ".\n\nStackTrace: " + this.stackTraceToString(this.m_exception));
                }
                else {
                    // log the exception
                    System.out.println(this.prettyLevel() + this.m_exception);
                    this.buffer("" + this.m_exception);
                    System.out.println(this.prettyLevel() + this.stackTraceToString(this.m_exception));
                }
            }
            // log what we have
            else if (this.m_message != null) {
                // log the message
                System.out.println(this.prettyLevel() + this.m_message);
                this.buffer(this.m_message);
            }
            else {
                // no message
                this.m_message = "UNKNOWN ERROR";

                // log the message
                System.out.println(this.prettyLevel() + this.m_message);
                this.buffer(this.m_message);
            }
        }
    }

    // pretty display of logging level
    private String prettyLevel() {
        if (this.m_level == ErrorLogger.INFO) {
            return "INFO: ";
        }
        if (this.m_level == ErrorLogger.WARNING) {
            return "WARN: ";
        }
        if (this.m_level == ErrorLogger.CRITICAL) {
            return "CRIT: ";
        }
        return "UNK: ";
    }

    // convert a stack trace to a string
    private String stackTraceToString(Exception ex) {
        if (ex != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            return sw.toString();
        }
        else {
            return "stackTraceToString: exception instance is NULL";
        }
    }
}
