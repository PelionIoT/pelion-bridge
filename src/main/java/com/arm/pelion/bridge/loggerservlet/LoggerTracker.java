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

import com.arm.pelion.bridge.core.ErrorLogger;
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
    private static LoggerTracker m_instance = null;
    private Thread me_thread = null;
    private boolean is_running = false;
    private Queue<String> m_log_queue = null;
    private List<LoggerWebSocket> m_members = null;
    private ErrorLogger m_logger = null;
    
    public static LoggerTracker getInstance() {
        if (LoggerTracker.m_instance == null) {
            LoggerTracker.m_instance = new LoggerTracker();
        }
        return LoggerTracker.m_instance;
    }
    
    // constuctor
    public LoggerTracker() {
        try {
            this.m_log_queue = new LinkedList<String>();
            this.m_members = new ArrayList<>();
            this.is_running = true;
            this.me_thread = new Thread(this);
            this.me_thread.start();
        }
        catch (Exception ex) {
            // silent
        }
    }
    
    public void setErrorLogger(ErrorLogger logger) {
        this.m_logger = logger;
    }
    
    public ErrorLogger errorLogger() {
        return this.m_logger;
    }
    
    public synchronized void join(LoggerWebSocket socket) {
        socket.setErrorLogger(this.m_logger);
        this.m_members.add(socket);
    }

    public synchronized void leave(LoggerWebSocket socket) {
        if (socket != null) {
            this.m_members.remove(socket);
            socket.close();
        }
    }

    public synchronized void write(String message) {
        this.putMessage(message);
    }
    
    private void putMessage(String message) {
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
            while(this.getMessageCount() > 0) {
                try {
                    String message = this.getNextMessage();
                    for(LoggerWebSocket member: m_members) {
                        for (int i=0;member != null && i<member.m_sessions.size();++i) {
                            if (member.m_sessions.get(i).isOpen() == true) {
                                // dump the message and remove
                                member.m_sessions.get(i).getRemote().sendStringByFuture(message);
                            }
                        }
                    }
                }
                catch (Exception ex) {
                    // silent...
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