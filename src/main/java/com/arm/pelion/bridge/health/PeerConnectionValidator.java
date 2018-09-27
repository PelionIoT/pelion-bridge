/**
 * @file PeerConnectionValidator.java
 * @brief Pelion Peer Connection validation
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
package com.arm.pelion.bridge.health;

import com.arm.pelion.bridge.coordinator.processors.arm.GenericConnectablePeerProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;
import com.arm.pelion.bridge.health.interfaces.HealthCheckServiceInterface;

/**
 * This class periodically checks all Peer Processor connections
 *
 * @author Doug Anson
 */
public class PeerConnectionValidator extends BaseValidatorClass implements Runnable {
    private PeerProcessorInterface m_peer = null;
    
    // default constructor
    public PeerConnectionValidator(HealthCheckServiceInterface provider,PeerProcessorInterface peer) {
        super(provider,"peer",((GenericConnectablePeerProcessor)peer).hsQualifier());
        this.m_peer = peer;
        this.m_value = (Boolean)false;      // boolean value for this validator
    }   
    
    // validate
    @Override
    protected void validate() {
        // make sure we are actually using Peer in the peer...otherwise dont report it...
        if (this.mqttInUse() == true || this.httpInUse()) {
            // DEBUG
            this.errorLogger().info("PeerConnctionValidator: Validating Peer Connections...");

            // validate the mqtt connections
            if (this.validatePeerConnections() == true) {
                // DEBUG
                this.errorLogger().info("PeerConnctionValidator: Peer Connections OK.");
                this.m_value = (Boolean)true;
            }
            else {
                // DEBUG
                this.errorLogger().warning("PeerConnctionValidator: One or more Peer Connections is DOWN.");
                this.m_value = (Boolean)false;
            }

            // update our stats and notify if changed
            this.updateStatisticAndNotify();
        }
    }
    
    // WORKER: is MQTT enabled in the generic Peer? (default)
    private boolean mqttInUse() {
        GenericConnectablePeerProcessor p = (GenericConnectablePeerProcessor)this.m_peer;
        if (p != null) {
            return p.mqttInUse();
        }
        return false;
    }
    
    // WORKER: is HTTP enabled in the generic Peer? (non-std, optional)
    private boolean httpInUse() {
        GenericConnectablePeerProcessor p = (GenericConnectablePeerProcessor)this.m_peer;
        if (p != null) {
            return p.httpInUse();
        }
        return false;
    }

    // WORKER: validate the Peer Connections (typically MQTT...)
    private boolean validatePeerConnections() {
        if (this.mqttInUse() == true) {
            // validate MQTT connections
            return this.validateMQTTConnections();
        }
        if (this.httpInUse() == true) {
            // validate HTTP status
            return this.validateHTTPConnections();
        }
        
        // unsure what type of connection we are using... so report FALSE
        return false;
    }
    
    // WORKER: validate the MQTT Connections (default)
    private boolean validateMQTTConnections() {
        GenericConnectablePeerProcessor p = (GenericConnectablePeerProcessor)this.m_peer;
        if (p != null) {
            return p.mqttConnectionsOK();
        }
        return false;
    }
    
    // WORKER: validate the HTTP Connections (non-std)
    private boolean validateHTTPConnections() {
        GenericConnectablePeerProcessor p = (GenericConnectablePeerProcessor)this.m_peer;
        if (p != null) {
            return p.httpStatusOK();
        }
        return false;
    }
}