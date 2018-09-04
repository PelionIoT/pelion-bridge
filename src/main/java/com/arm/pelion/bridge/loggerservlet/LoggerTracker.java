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

import java.util.ArrayList;
import java.util.List;

/**
 * Logger Tracker implementation
 * 
 * @author Brian Daniels
 */
public class LoggerTracker {
    private static final LoggerTracker INSTANCE = new LoggerTracker();

    public static LoggerTracker getInstance() {
        return INSTANCE;
    }
    
    private List<LoggerWebSocket> members = new ArrayList<>();

    public void join(LoggerWebSocket socket) {
        members.add(socket);
    }

    public void leave(LoggerWebSocket socket) {
        members.remove(socket);
    }

    public void write(String message) {
        for(LoggerWebSocket member: members) {
            member.session.getRemote().sendStringByFuture(message);
        }
    }
    
    public boolean connected() {
        boolean is_connected = true;
        for(LoggerWebSocket member: members) {
            is_connected = is_connected & member.session.isOpen();
        }
        return is_connected;
    }
}
