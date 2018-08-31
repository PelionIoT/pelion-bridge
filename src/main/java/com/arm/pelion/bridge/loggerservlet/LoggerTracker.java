/*
 * Copyright 2018 bridan01.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arm.pelion.bridge.loggerservlet;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author bridan01
 */
public class LoggerTracker {
    private static final LoggerTracker INSTANCE = new LoggerTracker();

    public static LoggerTracker getInstance()
    {
        return INSTANCE;
    }
    
    private List<LoggerWebSocket> members = new ArrayList<>();

    public void join(LoggerWebSocket socket) 
    {
        members.add(socket);
    }

    public void leave(LoggerWebSocket socket) 
    {
        members.remove(socket);
    }

    public void write(String message) 
    {
        for(LoggerWebSocket member: members)
        {
            member.session.getRemote().sendStringByFuture(message);
        }
    }
}
