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
import com.arm.pelion.bridge.coordinator.processors.arm.GenericMQTTProcessor;
import com.arm.pelion.bridge.transport.HttpTransport;
import com.arm.pelion.bridge.transport.MQTTTransport;
import com.arm.pelion.bridge.transport.Transport;
import java.util.ArrayList;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;

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
        String mgr_config = manager.preferences().valueOf("mqtt_mgr_config");
        if (mgr_config != null && mgr_config.length() > 0) {
            // muliple MQTT brokers requested... follow configuration and assign suffixes
            String[] config = mgr_config.split(";");
            for (int i = 0; i < config.length; ++i) {
                if (config[i].equalsIgnoreCase("std") == true) {
                    manager.errorLogger().info("Registering Generic MQTT processor...");
                    MQTTTransport mqtt = new MQTTTransport(manager.errorLogger(), manager.preferences(), null);
                    GenericMQTTProcessor p = new GenericMQTTProcessor(manager, mqtt, "" + i, http);
                    me.addProcessor(p);
                }
                if (config[i].equalsIgnoreCase("std-d") == true) {
                    manager.errorLogger().info("Registering Generic MQTT processor (default)...");
                    MQTTTransport mqtt = new MQTTTransport(manager.errorLogger(), manager.preferences(), null);
                    GenericMQTTProcessor p = new GenericMQTTProcessor(manager, mqtt, "" + i, http);
                    me.addProcessor(p, true);
                }
            }
        }
        else {
            manager.errorLogger().info("Registering Generic MQTT processor (singleton)...");
            MQTTTransport mqtt = new MQTTTransport(manager.errorLogger(), manager.preferences(), null);
            GenericMQTTProcessor p = new GenericMQTTProcessor(manager, mqtt, http);
            me.addProcessor(p);
        }

        // return me
        return me;
    }

    // constructor
    public GenericMQTTPeerProcessorFactory(Orchestrator manager, HttpTransport http) {
        super(manager, null);
        this.m_http = http;
        this.m_mqtt_processor_list = new ArrayList<>();
    }
}
