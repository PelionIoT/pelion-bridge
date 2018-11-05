/**
 * @file HTTPDeviceListener.java
 * @brief Device Listener for HTTP Peer Processors
 * @author Doug Anson
 * @version 1.0
 * @see
 *
 * Copyright 2018. ARM Ltd. All rights reserved.
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
package com.arm.pelion.bridge.coordinator.processors.core;

import com.arm.pelion.bridge.coordinator.processors.interfaces.HTTPDeviceListenerInterface;
import com.arm.pelion.bridge.core.BaseClass;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.transport.HttpTransport;

/**
 * HTTP-based device listener for HTTP-based peer processors
 * @author Doug Anson
 */
public class HTTPDeviceListener extends BaseClass implements Runnable {
    private static final int DEFAULT_LISTENER_WAIT_TIME_MS = 1000;              // 1 second
    private HTTPDeviceListenerInterface m_processor = null;
    private HttpTransport m_http = null;
    private boolean m_running = true;
    private String m_ep_name = null;
    private Thread m_thread = null;
    private int m_listener_wait_time_ms = DEFAULT_LISTENER_WAIT_TIME_MS;
    
    // constructor
    public HTTPDeviceListener(HTTPDeviceListenerInterface processor,HttpTransport http,String ep_name) {
        super(processor.errorLogger(),processor.preferences());
        this.m_ep_name = ep_name;
        this.m_processor = processor;
        this.m_http = http;
        this.m_thread = new Thread(this);
        try {
            this.m_thread.start();
        }
        catch(Exception ex) {
            this.errorLogger().warning("HTTPDeviceListener: Unable to start device listner for: " + this.m_ep_name);
        }
    }
    
    // get the HTTP instance for this listener
    public HttpTransport http() {
        return this.m_http;
    }
    
    // halt this process
    public void halt() {
        this.m_running = false;
    }

    @Override
    public void run() {
        this.errorLogger().warning("HTTPDeviceListener: Device Listener for " + this.m_ep_name + " has started.");
        Utils.waitForABit(this.errorLogger(), 4*this.m_listener_wait_time_ms);
        while(this.m_running) {
            try {
                Utils.waitForABit(this.errorLogger(), this.m_listener_wait_time_ms);
                this.m_processor.pollAndProcessDeviceMessages(this.m_http,this.m_ep_name);
            }
            catch(Exception ex) {
                this.errorLogger().warning("HTTPDeviceListener: Exception in Device Listener: " + ex.getMessage());
            }
        }
        this.errorLogger().warning("HTTPDeviceListener: Device Listener for " + this.m_ep_name + " has halted (OK)");
    }
}
