/**
 * @file GoogleCloudPeerProcessorFactory.java
 * @brief Google Cloud Peer Processor Factory
 * @author Doug Anson
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
package com.arm.pelion.bridge.coordinator.processors.factories;

import com.arm.pelion.bridge.coordinator.processors.arm.GenericConnectablePeerProcessor;
import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.google.mqtt.GoogleCloudMQTTProcessor;
import com.arm.pelion.bridge.transport.HttpTransport;
import com.arm.pelion.bridge.transport.Transport;
import java.util.ArrayList;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;

/**
 * Google Cloud Peer Processor Manager: Factory for initiating a peer processor for Google CloudIoT
 *
 * @author Doug Anson
 */
public class GoogleCloudPeerProcessorFactory extends BasePeerProcessorFactory implements Transport.ReceiveListener, PeerProcessorInterface {

    // Factory method for initializing the AWS IotHub MQTT collection orchestrator
    public static GoogleCloudPeerProcessorFactory createPeerProcessor(Orchestrator manager, HttpTransport http) {
        // create me
        GoogleCloudPeerProcessorFactory me = new GoogleCloudPeerProcessorFactory(manager, http);

        // initialize me
        boolean google_cloud_gw_enabled = manager.preferences().booleanValueOf("enable_google_cloud_addon");
        if (google_cloud_gw_enabled == true) {
            manager.errorLogger().info("Registering Google Cloud MQTT processor...");
            GenericConnectablePeerProcessor p = new GoogleCloudMQTTProcessor(manager, null, http);
            me.addProcessor(p);
        }

        // return me
        return me;
    }

    // constructor
    public GoogleCloudPeerProcessorFactory(Orchestrator manager, HttpTransport http) {
        super(manager, null);
        this.m_http = http;
        this.m_peer_processor_list = new ArrayList<>();
    }
}