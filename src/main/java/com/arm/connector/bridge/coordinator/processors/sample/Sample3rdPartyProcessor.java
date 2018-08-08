/**
 * @file    Sample3rdPartyProcessor.java
 * @brief Stubbed out Sample 3rd Party Peer Processor
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
package com.arm.connector.bridge.coordinator.processors.sample;

import com.arm.connector.bridge.coordinator.Orchestrator;
import com.arm.connector.bridge.coordinator.processors.core.PeerProcessor;
import com.arm.connector.bridge.coordinator.processors.interfaces.GenericSender;
import com.arm.connector.bridge.coordinator.processors.interfaces.PeerInterface;
import com.arm.connector.bridge.core.Utils;
import com.arm.connector.bridge.transport.HttpTransport;
import java.util.Map;

/**
 * Sample 3rd Party peer processor (derived from PeerProcessor.. may want to switch to GenericMQTTProcessor)
 *
 * @author Doug Anson
 */
public class Sample3rdPartyProcessor extends PeerProcessor implements PeerInterface, GenericSender {

    private HttpTransport m_http = null;

    // (OPTIONAL) Factory method for initializing the Sample 3rd Party peer
    public static Sample3rdPartyProcessor createPeerProcessor(Orchestrator manager, HttpTransport http) {
        // create me
        Sample3rdPartyProcessor me = new Sample3rdPartyProcessor(manager, http);

        // return me
        return me;
    }

    // constructor
    public Sample3rdPartyProcessor(Orchestrator orchestrator, HttpTransport http) {
        this(orchestrator, http, null);
    }

    // constructor
    public Sample3rdPartyProcessor(Orchestrator orchestrator, HttpTransport http, String suffix) {
        super(orchestrator,suffix);
        this.m_http = http;
        this.m_mds_domain = orchestrator.getDomain();

        // Sample 3rd Party peer PeerProcessor Announce
        this.errorLogger().info("Sample 3rd Party peer Processor ENABLED.");
    }
    
    //
    // OVERRIDES for Sample3rdPartyProcessor...
    //
    // These methods are stubbed out by default... but need to be implemented in derived classes.
    // They are the "responders" to mDS events for devices and initialize/start and stop "listeners"
    // that are appropriate for the peer/3rd Party...(for example MQTT...)
    //
    // If the peer does not support tree-like organizations of input data in a pub/sub fashion, additional
    // overrides may need to be implemented. Additionally, if special per-device message sending is required,
    // additional overrides will need to be implemented. See AWSIoT, IoTHub, and WatsonIoT peers as examples. 
    //
    
    // initialize any Sample 3rd Party peer listeners
    @Override
    public void initListener() {
        // XXX to do
        this.errorLogger().info("initListener(Sample)");
    }

    // stop our Sample 3rd Party peer listeners
    @Override
    public void stopListener() {
        // XXX to do
        this.errorLogger().info("stopListener(Sample)");
    }
    
    // Create the authentication hash
    @Override
    public String createAuthenticationHash() {
        // just create a hash of something unique to the peer side... 
        String peer_secret = "";
        return Utils.createHash(peer_secret);
    }
    
    // GenericSender Implementation: send a message
    @Override
    public void sendMessage(String to, String message) {
        // send a message over Google Cloud...
        this.errorLogger().info("sendMessage(Sample): Sending Message to: " + to + " message: " + message);
        
        // send this message to the peer
    }

    // complete new registration
    @Override
    public void completeNewDeviceRegistration(Map endpoint) {
        // complete shadow device registration here...
    }
}
