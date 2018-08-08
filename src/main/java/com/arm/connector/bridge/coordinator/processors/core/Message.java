/**
 * @file    Message.java
 * @brief message base class
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
package com.arm.connector.bridge.coordinator.processors.core;

/**
 * message base class
 *
 * @author Doug Anson
 */
public class Message {

    private String m_uri = null;
    private String m_content = null;
    private boolean m_wait = false;

    public Message(String uri, String content, boolean wait) {
        this.m_uri = uri;
        this.m_content = content;
        this.m_wait = wait;
    }

    public String uri() {
        return this.m_uri;
    }

    public String content() {
        return this.m_content;
    }

    public boolean waitForResponse() {
        return this.m_wait;
    }
}
