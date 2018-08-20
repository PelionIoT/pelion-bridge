/**
 * @file Transport.java
 * @brief transport base class
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
package com.arm.pelion.bridge.transport;

import com.arm.pelion.bridge.coordinator.processors.interfaces.ReconnectionInterface;
import com.arm.pelion.bridge.core.BaseClass;
import com.arm.pelion.bridge.core.ErrorLogger;
import com.arm.pelion.bridge.preferences.PreferenceManager;

/**
 * Generic transport base class
 *
 * @author Doug Anson
 */
public abstract class Transport extends BaseClass {

    protected boolean m_connected = false;
    private final ErrorLogger m_error_logger = null;
    private final PreferenceManager m_preference_manager = null;
    protected Object m_endpoint = null;
    protected Transport.ReceiveListener m_listener = null;
    protected ReconnectionInterface m_reconnector = null;

    // ReceiveListener class for Transport callback event processing
    public interface ReceiveListener {

        /**
         * on message receive, this will be callback to the registered listener
         *
         * @param topic
         * @param message
         */
        public void onMessageReceive(String topic, String message);
    }

    /**
     * Constructor
     *
     * @param error_logger
     * @param preference_manager
     * @param reconnector
     */
    public Transport(ErrorLogger error_logger, PreferenceManager preference_manager, ReconnectionInterface reconnector) {
        super(error_logger, preference_manager);
        this.m_connected = false;
        this.setReconnectionProvider(reconnector);
    }
    
    /**
     * set the reconnection provider
     * @param reconnector
     */
    public void setReconnectionProvider(ReconnectionInterface reconnector) {
        this.m_reconnector = reconnector;
    }

    /**
     * main thread loop
     * @return  processing status
     */
    public abstract boolean receiveAndProcess();

    /**
     * connect transport
     *
     * @param host
     * @param port
     * @return
     */
    public abstract boolean connect(String host, int port);

    /**
     * send a message
     *
     * @param topic
     * @param message
     */
    public abstract void sendMessage(String topic, String message);

    /**
     * disconnect
     */
    public void disconnect() {
        this.m_connected = false;
    }

    /**
     * set the receive listener
     *
     * @param listener
     */
    public void setOnReceiveListener(Transport.ReceiveListener listener) {
        this.m_listener = listener;
    }

    /**
     * connection status
     *
     * @return
     */
    public boolean isConnected() {
        return this.m_connected;
    }
}
