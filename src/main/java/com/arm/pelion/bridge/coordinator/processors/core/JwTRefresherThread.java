/**
 * @file JwTRefresherThread.java
 * @brief JwT Refresher Thread implementation
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

import com.arm.pelion.bridge.coordinator.processors.interfaces.JwTRefresherResponderInterface;
import com.arm.pelion.bridge.core.ErrorLogger;
import com.arm.pelion.bridge.core.Utils;

/**
 * JwT Refresher Thread implementation
 * 
 * @author Doug Anson
 */
public class JwTRefresherThread extends Thread {
    private JwTRefresherResponderInterface m_responder = null;
    private boolean m_running = false;
    private long m_wait_between_refresh_ms = 0;
    private String m_ep_name = null;
    private long m_wait_for_lock = 0;
    
    // Constructor
    public JwTRefresherThread(JwTRefresherResponderInterface processor,String ep_name) {
        this.m_responder = processor;
        this.m_ep_name = ep_name;
        this.m_wait_between_refresh_ms = this.m_responder.getJwTRefreshIntervalInSeconds() * 1000;
        this.m_wait_for_lock = this.m_responder.waitForLockTime();        
    }
    
    /**
     * get our running state
     *
     * @return
     */
    public boolean isRunning() {
        return this.m_running;
    }
    
    // stop running
    public void haltThread() {
        this.m_running = false;
    }
    
    /**
     * run method for the receive thread
     */
    @Override
    public void run() {
        if (!this.m_running) {
            this.m_running = true;
            this.listenerThreadLoop();
        }
        
        // Exiting
        this.errorLogger().warning("JwTRefresher: JwT Refresh Thread STOPPED for: " + this.m_ep_name);
    }
    
    /**
     * main thread loop
     */
    private void listenerThreadLoop() {
        while (this.m_running) {
            try {
                // sleep until we need to refresh our JwT
                Thread.sleep(this.m_wait_between_refresh_ms);
                
                // wait until the processor is idle
                while(this.m_responder.operationStart() == false) {
                    // continue sleeping until we have a lock on the processor
                    Utils.waitForABit(this.errorLogger(), this.m_wait_for_lock);
                }
                
                // DEBUG
                this.errorLogger().info("JwTRefresher: Refreshing JwT for endpoint: " + this.m_ep_name);

                // now refresh our token
                this.m_responder.refreshJwTForEndpoint(this.m_ep_name);
                
                // UNLOCK
                this.m_responder.operationStop();
                
            }
            catch (InterruptedException ex) {
                // silent
            }
        }
    }
    
    // Error Logger
    private ErrorLogger errorLogger() {
        if (this.m_responder != null) {
            return this.m_responder.errorLogger();
        }
        return null;
    }
}
