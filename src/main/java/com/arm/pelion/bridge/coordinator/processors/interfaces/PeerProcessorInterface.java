/**
 * @file    PeerInterface.java
 * @brief Generic Peer Interface for the pelion-bridge
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
package com.arm.pelion.bridge.coordinator.processors.interfaces;
import java.util.Map;

/**
 * This interface defines the exposed methods of a peer processor (such as a GenericMQTTProcessor) 
 * that are consumed by the Pelion processor
 *
 * @author Doug Anson
 */
public interface PeerProcessorInterface {

    // create peer-centric authentication hash for Pelion webhook authentication
    public String createAuthenticationHash();

    // process a new endpoint registration message from Pelion
    public void processNewRegistration(Map message);

    // process an endpoint re-registration message from Pelion
    public void processReRegistration(Map message);

    // process an endpoint device deletions message from Pelion
    public String[] processDeviceDeletions(Map message);
    
    // process an endpoint de-registration message from Pelion
    public String[] processDeregistrations(Map message);

    // process an endpoint registrations-expired message from Pelion
    public String[] processRegistrationsExpired(Map message);

    // process an endpoint async response result from Pelion
    public void processAsyncResponses(Map message);

    // process an endpoint resource notification message from Pelion
    public void processNotification(Map message);
    
    // acquire the subscriptions manager from the peer
    public SubscriptionManager subscriptionsManager();
    
    // process a new device registration (directly, Pelion)
    public void completeNewDeviceRegistration(Map endpoint);

    // init/start peer listeners
    public void initListener();

    // stop peer listeners
    public void stopListener();
    
    // record async responses
    public void recordAsyncResponse(String response, String uri, Map ep, AsyncResponseProcessor processor);
    
    // save the endpoint type for a given endpoint name
    public void setEndpointTypeFromEndpointName(String ep,String ept);
}
