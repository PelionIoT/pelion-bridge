/**
 * @file AWSIoTPeerProcessorFactory.java
 * @brief AWS IoT Peer Processor Factory
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

import com.arm.pelion.bridge.coordinator.processors.arm.GenericConnectablePeerProcessor;
import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.transport.HttpTransport;
import com.arm.pelion.bridge.transport.Transport;
import java.util.ArrayList;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;

/**
 * AWS IOT Peer Processor Manager: Factory for initiating a peer processor for AWS IOT
 *
 * @author Doug Anson
 */
public class AWSIoTPeerProcessorFactory extends BasePeerProcessorFactory implements Transport.ReceiveListener, PeerProcessorInterface {

    // Factory method for initializing the AWS IotHub MQTT collection orchestrator
    public static AWSIoTPeerProcessorFactory createPeerProcessor(Orchestrator manager, HttpTransport http) {
         // default is to use MQTT
        boolean use_mqtt = true;
        
        // determine whether we use MQTT or HTTP
        String aws_iot_transport = manager.preferences().valueOf("aws_iot_transport");
        if (aws_iot_transport != null && aws_iot_transport.equalsIgnoreCase("http")) {
            use_mqtt = false;
        }
        
        // create me
        AWSIoTPeerProcessorFactory me = new AWSIoTPeerProcessorFactory(manager, http);

        // initialize me
        boolean aws_iot_gw_enabled = manager.preferences().booleanValueOf("enable_aws_iot_gw_addon");
        if (aws_iot_gw_enabled == true) {
            if (use_mqtt == true) {
                manager.errorLogger().info("Registering AWS IoT MQTT processor...");
                GenericConnectablePeerProcessor p = new com.arm.pelion.bridge.coordinator.processors.aws.mqtt.AWSIoTProcessor(manager, null, http);
                me.addProcessor(p);
            }
            else {
                manager.errorLogger().info("Registering AWS IoT HTTP processor...");
                GenericConnectablePeerProcessor p = new com.arm.pelion.bridge.coordinator.processors.aws.http.AWSIoTProcessor(manager, null, http);
                me.addProcessor(p);
            }
        }

        // return me
        return me;
    }

    // constructor
    public AWSIoTPeerProcessorFactory(Orchestrator manager, HttpTransport http) {
        super(manager, null);
        this.m_http = http;
        this.m_peer_processor_list = new ArrayList<>();
    }
}
