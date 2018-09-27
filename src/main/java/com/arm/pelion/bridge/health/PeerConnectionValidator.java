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
import com.arm.pelion.bridge.coordinator.processors.factories.BasePeerProcessorFactory;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;
import com.arm.pelion.bridge.health.interfaces.HealthCheckServiceInterface;
import java.util.List;

/**
 * This class periodically checks all Peer Processor connections
 *
 * @author Doug Anson
 */
public class PeerConnectionValidator extends BaseValidatorClass implements Runnable {
    // default constructor
    public PeerConnectionValidator(HealthCheckServiceInterface provider,String qualifier) {
        super(provider,"peer",qualifier);
        this.m_value = (Boolean)false;      // boolean value for this validator
    }   
    
    // validate
    @Override
    protected void validate() {
        // make sure we are actually using Peer in the peer...otherwise dont report it...
        if (this.mqttInUse() == true) {
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
        boolean in_use = true;
        List<PeerProcessorInterface> list = this.m_provider.getPeerProcessorList();
        for(int i=0;list != null && i<list.size() && in_use;++i) {
            BasePeerProcessorFactory f = (BasePeerProcessorFactory)list.get(i);
            GenericConnectablePeerProcessor p = f.genericPeerProcessor();
            if (p != null) {
                // cumulative
                in_use = (in_use & p.mqttInUse());
            }
        }
        return in_use;
    }
    
    // WORKER: is HTTP enabled in the generic Peer? (non-std, optional)
    private boolean httpInUse() {
        boolean in_use = true;
        List<PeerProcessorInterface> list = this.m_provider.getPeerProcessorList();
        for(int i=0;list != null && i<list.size() && in_use;++i) {
            BasePeerProcessorFactory f = (BasePeerProcessorFactory)list.get(i);
            GenericConnectablePeerProcessor p = f.genericPeerProcessor();
            if (p != null) {
                // cumulative
                in_use = (in_use & p.httpInUse());
            }
        }
        return in_use;
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
        boolean ok = true;
        List<PeerProcessorInterface> list = this.m_provider.getPeerProcessorList();
        for(int i=0;list != null && i<list.size() && ok;++i) {
            BasePeerProcessorFactory f = (BasePeerProcessorFactory)list.get(i);
            GenericConnectablePeerProcessor p = f.genericPeerProcessor();
            if (p != null) {
                // cumulative
                ok = (ok & p.mqttConnectionsOK());
            }
        }
        return ok;
    }
    
    // WORKER: validate the HTTP Connections (non-std)
    private boolean validateHTTPConnections() {
        boolean ok = true;
        List<PeerProcessorInterface> list = this.m_provider.getPeerProcessorList();
        for(int i=0;list != null && i<list.size() && ok;++i) {
            BasePeerProcessorFactory f = (BasePeerProcessorFactory)list.get(i);
            GenericConnectablePeerProcessor p = f.genericPeerProcessor();
            if (p != null) {
                // cumulative
                ok = (ok & p.httpStatusOK());
            }
        }
        return ok;
    }
}