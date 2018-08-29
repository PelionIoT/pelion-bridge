/**
 * @file WatsonIoTPeerProcessorFactory.java
 * @brief IBM Watson IoT Peer Processor Factory
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
import com.arm.pelion.bridge.coordinator.processors.ibm.WatsonIoTMQTTProcessor;
import com.arm.pelion.bridge.transport.HttpTransport;
import com.arm.pelion.bridge.transport.MQTTTransport;
import com.arm.pelion.bridge.transport.Transport;
import java.util.ArrayList;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;

/**
 * IBM Watson IoT Peer Processor Manager: Factory for initiating a peer processor for IBM Watson IoT
 *
 * @author Doug Anson
 */
public class WatsonIoTPeerProcessorFactory extends BasePeerProcessorFactory implements Transport.ReceiveListener, PeerProcessorInterface {

    // Factory method for initializing the IBM MQTT collection orchestrator
    public static WatsonIoTPeerProcessorFactory createPeerProcessor(Orchestrator manager, HttpTransport http) {
        // create me
        WatsonIoTPeerProcessorFactory me = new WatsonIoTPeerProcessorFactory(manager, http);

        // initialize me
        boolean iotf_enabled = manager.preferences().booleanValueOf("enable_iotf_addon");
        String mgr_config = manager.preferences().valueOf("mqtt_mgr_config");
        if (mgr_config != null && mgr_config.length() > 0) {
            // muliple MQTT brokers requested... follow configuration and assign suffixes
            String[] config = mgr_config.split(";");
            for (int i = 0; i < config.length; ++i) {
                if (iotf_enabled == true && config[i].equalsIgnoreCase("iotf") == true) {
                    manager.errorLogger().info("Registering Watson IoT MQTT processor...");
                    MQTTTransport mqtt = new MQTTTransport(manager.errorLogger(), manager.preferences(),null);
                    WatsonIoTMQTTProcessor p = new WatsonIoTMQTTProcessor(manager, mqtt, "" + i, http);
                    mqtt.setReconnectionProvider(p);
                    me.addProcessor(p);
                }
                if (iotf_enabled == true && config[i].equalsIgnoreCase("iotf-d") == true) {
                    manager.errorLogger().info("Registering Watson IoT MQTT processor (default)...");
                    MQTTTransport mqtt = new MQTTTransport(manager.errorLogger(), manager.preferences(), null);
                    WatsonIoTMQTTProcessor p = new WatsonIoTMQTTProcessor(manager, mqtt, "" + i, http);
                    mqtt.setReconnectionProvider(p);
                    me.addProcessor(p, true);
                }
            }
        }
        else // single MQTT broker configuration requested
        {
            if (iotf_enabled == true) {
                manager.errorLogger().info("Registering Watson IoT MQTT processor (singleton)...");
                MQTTTransport mqtt = new MQTTTransport(manager.errorLogger(), manager.preferences(), null);
                WatsonIoTMQTTProcessor p = new WatsonIoTMQTTProcessor(manager, mqtt, http);
                mqtt.setReconnectionProvider(p);
                me.addProcessor(p);
            }
        }

        // return me
        return me;
    }

    // constructor
    public WatsonIoTPeerProcessorFactory(Orchestrator manager, HttpTransport http) {
        super(manager, null);
        this.m_http = http;
        this.m_mqtt_processor_list = new ArrayList<>();
    }
}
