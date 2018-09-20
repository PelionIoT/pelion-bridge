/**
 * @file GenericMQTTPeerProcessorFactory.java
 * @brief Generic MQTT Processor Factory
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
import com.arm.pelion.bridge.coordinator.processors.arm.GenericConnectablePeerProcessor;
import com.arm.pelion.bridge.transport.HttpTransport;
import com.arm.pelion.bridge.transport.Transport;
import java.util.ArrayList;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;
import com.arm.pelion.bridge.transport.MQTTTransport;

/**
 * Generic MQTT Peer Processor Manager: Factory for initiating a generic MQTT peer processor
 *
 * @author Doug Anson
 */
public class GenericMQTTPeerProcessorFactory extends BasePeerProcessorFactory implements Transport.ReceiveListener, PeerProcessorInterface {

    // Factory method for initializing the generic MQTT collection orchestrator
    public static GenericMQTTPeerProcessorFactory createPeerProcessor(Orchestrator manager, HttpTransport http) {
        // create me
        GenericMQTTPeerProcessorFactory me = new GenericMQTTPeerProcessorFactory(manager, http);

        // initialize me
        boolean generic_mqtt_processor_enabled = manager.preferences().booleanValueOf("enable_generic_mqtt_processor");
        if (generic_mqtt_processor_enabled == true) {
            manager.errorLogger().info("Registering Generic MQTT processor...");
            MQTTTransport mqtt = new MQTTTransport(manager.errorLogger(), manager.preferences(), null);
            GenericConnectablePeerProcessor p = new GenericConnectablePeerProcessor(manager, mqtt, http);
            me.addProcessor(p);
        }

        // return me
        return me;
    }

    // constructor
    public GenericMQTTPeerProcessorFactory(Orchestrator manager, HttpTransport http) {
        super(manager, null);
        this.m_http = http;
        this.m_peer_processor_list = new ArrayList<>();
    }
}
