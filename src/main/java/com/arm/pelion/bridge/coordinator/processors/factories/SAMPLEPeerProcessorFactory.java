/**
 * @file SAMPLEPeerProcessorFactory.java
 * @brief SAMPLE Peer Processor Factory
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
import com.arm.pelion.bridge.transport.HttpTransport;
import com.arm.pelion.bridge.transport.Transport;
import java.util.ArrayList;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;
import com.arm.pelion.bridge.coordinator.processors.SAMPLE.SAMPLEProcessor;
import com.arm.pelion.bridge.transport.MQTTTransport;

/**
 * SAMPLE Processor Manager: Factory for initiating a the SAMPLE peer processor 
 *
 * @author Doug Anson
 */
public class SAMPLEPeerProcessorFactory extends BasePeerProcessorFactory implements Transport.ReceiveListener, PeerProcessorInterface {

    // Factory method for initializing the SAMPLE collection orchestrator
    public static SAMPLEPeerProcessorFactory createPeerProcessor(Orchestrator manager, HttpTransport http) {
        // create me
        SAMPLEPeerProcessorFactory me = new SAMPLEPeerProcessorFactory(manager, http);

        // initialize me
        boolean SAMPLE_enabled = manager.preferences().booleanValueOf("SAMPLE_enable_addon");
        if (SAMPLE_enabled == true) {
            // XXX if SAMPLE cloud can make use of a single MQTT connection for all shadows... create and pass it here.
            MQTTTransport mqtt = new MQTTTransport(manager.errorLogger(),manager.preferences(),null);
            manager.errorLogger().info("Registering SAMPLE processor...");
            SAMPLEProcessor p = new SAMPLEProcessor(manager,mqtt,http);
            mqtt.setReconnectionProvider(p);
            me.addProcessor(p);
        }

        // return me
        return me;
    }

    // constructor
    public SAMPLEPeerProcessorFactory(Orchestrator manager, HttpTransport http) {
        super(manager, null);
        this.m_http = http;
        this.m_mqtt_processor_list = new ArrayList<>();
    }
}