/**
 * @file LoggerTracker.java
 * @brief pelion-bridge Logger over WebSockets
 * @author Brian Daniels
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
package com.arm.pelion.bridge.loggerservlet;

import com.arm.pelion.bridge.core.Utils;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Logger Tracker implementation
 * 
 * @author Brian Daniels
 */
public class LoggerTracker implements Runnable {
    private static final int RECHECK_INTERVAL_MS = 800;            // sleep interval for draining log queue
    private static final LoggerTracker me = new LoggerTracker();
    private static Thread me_thread = new Thread(me);
    private boolean is_running = true;
    private Queue<String> m_log_queue = new LinkedList<String>();
    private List<LoggerWebSocket> m_members = new ArrayList<>();
    
    public static LoggerTracker getInstance() {
        if (me_thread.isAlive() == false) {
            me_thread.start();
        }
        return me;
    }
    
    public void join(LoggerWebSocket socket) {
        m_members.add(socket);
    }

    public void leave(LoggerWebSocket socket) {
        m_members.remove(socket);
    }

    public void write(String message) {
        this.putMessage(message);
    }
    
    public boolean connected() {
        boolean is_connected = true;
        if (this.m_members != null) {
            for(LoggerWebSocket member: m_members) {
                is_connected = is_connected & member.session.isOpen();
            }
        }
        else {
            is_connected = false;
        }
        return is_connected;
    }
    
    private synchronized void putMessage(String message) {
        this.m_log_queue.add(message);
    }
    
    private synchronized String getNextMessage() {
        return this.m_log_queue.poll();
    }
    
    private synchronized int getMessageCount() {
        return this.m_log_queue.size();
    }
    
    private void writeLogCache() {
        while(this.is_running == true) {
            for(LoggerWebSocket member: m_members) {
                while(this.getMessageCount() > 0) {
                    if (member != null && member.session != null && member.session.isOpen() == true) {
                        // dump the message and remove
                        member.session.getRemote().sendStringByFuture(this.getNextMessage());
                    }
                }
            }
            
            // if we have drained our queue... sleep
            do {
                // Sleep a bit and recheck
                Utils.waitForABit(null, RECHECK_INTERVAL_MS);
            }
            while (this.getMessageCount() == 0);
        }
    }

    @Override
    public void run() {
        this.writeLogCache();
    }
}