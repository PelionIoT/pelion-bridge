/**
 * @file  BasePeerProcessorManager.java
 * @brief Base Class Peer Processor Manager
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
package com.arm.pelion.bridge.coordinator.processors.factories;

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.arm.GenericMQTTProcessor;
import com.arm.pelion.bridge.coordinator.processors.core.PeerProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.AsyncResponseProcessor;
import com.arm.pelion.bridge.transport.HttpTransport;
import com.arm.pelion.bridge.transport.Transport;
import java.util.ArrayList;
import java.util.Map;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;

/**
 * Base Peer PeerProcessor Manager: Manages a collection of MQTT-based processors
 *
 * @author Doug Anson
 */
public class BasePeerProcessorFactory extends PeerProcessor implements Transport.ReceiveListener, PeerProcessorInterface {

    protected ArrayList<GenericMQTTProcessor> m_mqtt_processor_list = null;
    protected HttpTransport m_http = null;
    protected int m_default_processor = 0;
    
    // constructor
    public BasePeerProcessorFactory(Orchestrator manager, HttpTransport http) {
        super(manager, null);
        this.m_http = http;
        this.m_mqtt_processor_list = new ArrayList<>();
    }

    // query the default processor for the authentication hash
    @Override
    public String createAuthenticationHash() {
        return this.createAuthenticationHash(this.m_default_processor);
    }

    // create an authentication hash for a specific MQTT broker
    public String createAuthenticationHash(int index) {
        if (this.m_mqtt_processor_list.size() > 0 && index < this.m_mqtt_processor_list.size()) {
            return this.m_mqtt_processor_list.get(index).createAuthenticationHash();
        }
        return null;
    }

    // init listeners for each MQTT broker connection
    @Override
    public void initListener() {
        for (int i = 0; i < this.m_mqtt_processor_list.size(); ++i) {
            this.m_mqtt_processor_list.get(i).initListener();
        }
    }

    // stop listeners for each MQTT broker connection
    @Override
    public void stopListener() {
        for (int i = 0; i < this.m_mqtt_processor_list.size(); ++i) {
            this.m_mqtt_processor_list.get(i).stopListener();
        }
    }

    // add a GenericMQTTProcessor
    public void addProcessor(GenericMQTTProcessor mqtt_processor) {
        this.addProcessor(mqtt_processor, false);
    }

    // add a GenericMQTTProcessor
    public void addProcessor(GenericMQTTProcessor mqtt_processor, boolean is_default) {
        if (is_default == true) {
            this.m_default_processor = this.m_mqtt_processor_list.size();
        }
        this.m_mqtt_processor_list.add(mqtt_processor);
    }

    // get the number of processors
    public int numProcessors() {
        return this.m_mqtt_processor_list.size();
    }

    // get the default processor
    public GenericMQTTProcessor mqttProcessor() {
        if (this.m_mqtt_processor_list.size() > 0) {
            return this.m_mqtt_processor_list.get(this.m_default_processor);
        }
        return null;
    }

    // message processor for inbound messages
    @Override
    public void onMessageReceive(String topic, String message) {
        for (int i = 0; i < this.m_mqtt_processor_list.size(); ++i) {
            this.m_mqtt_processor_list.get(i).onMessageReceive(topic, message);
        }
    }

    @Override
    public void processNewRegistration(Map message) {
        for (int i = 0; i < this.m_mqtt_processor_list.size(); ++i) {
            this.m_mqtt_processor_list.get(i).processNewRegistration(message);
        }
    }
    
    @Override
    public void completeNewDeviceRegistration(Map endpoint) {
        for (int i = 0; i < this.m_mqtt_processor_list.size(); ++i) {
            this.m_mqtt_processor_list.get(i).completeNewDeviceRegistration(endpoint);
        }
    }

    @Override
    public void processReRegistration(Map message) {
        for (int i = 0; i < this.m_mqtt_processor_list.size(); ++i) {
            this.m_mqtt_processor_list.get(i).processReRegistration(message);
        }
    }

    @Override
    public String[] processDeregistrations(Map message) {
        ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < this.m_mqtt_processor_list.size(); ++i) {
            String[] tmp = this.m_mqtt_processor_list.get(i).processDeregistrations(message);
            for (int j = 0; j < tmp.length; ++j) {
                list.add(tmp[j]);
            }
        }
        String[] returns = new String[list.size()];
        return list.toArray(returns);
    }

    @Override
    public String[] processRegistrationsExpired(Map message) {
        return this.processDeregistrations(message);
    }

    @Override
    public void processAsyncResponses(Map message) {
        for (int i = 0; i < this.m_mqtt_processor_list.size(); ++i) {
            this.m_mqtt_processor_list.get(i).processAsyncResponses(message);
        }
    }

    @Override
    public void processNotification(Map message) {
        for (int i = 0; i < this.m_mqtt_processor_list.size(); ++i) {
            this.m_mqtt_processor_list.get(i).processNotification(message);
        }
    }

    @Override
    public void recordAsyncResponse(String response, String uri, Map ep, AsyncResponseProcessor processor) {
        for (int i = 0; i < this.m_mqtt_processor_list.size(); ++i) {
            this.m_mqtt_processor_list.get(i).recordAsyncResponse(response, uri, ep, processor);
        }
    }
}
