/**
 * @file TreasureDataPeerProcessorFactory.java
 * @brief TreasureData Peer Processor Factory
 * @author Doug Anson
 * @version 1.0
 * @see
 *
 * Copyright 2020. ARM Ltd. All rights reserved.
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
import com.arm.pelion.bridge.coordinator.processors.arm.treasuredata.TreasureDataProcessor;
import com.arm.pelion.bridge.transport.Transport;
import java.util.ArrayList;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;

/**
 * TreasureData Processor Manager: Factory for initiating a the TreasureData peer processor 
 *
 * @author Doug Anson
 */
public class TreasureDataPeerProcessorFactory extends BasePeerProcessorFactory implements Transport.ReceiveListener, PeerProcessorInterface {

    // Factory method for initializing the TreasureData collection orchestrator
    public static TreasureDataPeerProcessorFactory createPeerProcessor(Orchestrator manager) {
        // create me
        TreasureDataPeerProcessorFactory me = new TreasureDataPeerProcessorFactory(manager);

        // initialize me
        boolean TreasureData_enabled = manager.preferences().booleanValueOf("enable_td_addon");
        if (TreasureData_enabled == true) {
            // create the TreasureData peer processor...
            manager.errorLogger().info("Registering TreasureData processor...");
            TreasureDataProcessor p = new TreasureDataProcessor(manager,null);
            me.addProcessor(p);
        }

        // return me
        return me;
    }

    // constructor
    public TreasureDataPeerProcessorFactory(Orchestrator manager) {
        super(manager, null);
        this.m_peer_processor_list = new ArrayList<>();
    }
}