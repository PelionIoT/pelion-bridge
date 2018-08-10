/*
 * @file  SubscriptionManager.java
 * @brief interface for generic subscription management
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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arm.pelion.bridge.coordinator.processors.interfaces;

/**
 * GenericSender Interface defines subscription management
 * @author Doug Anson
 */
public interface SubscriptionManager {
    // add a subscription processor to the subscription manager
    public void addSubscriptionProcessor(SubscriptionProcessor subscription_processor);
            
    // add a subscription
    public void addSubscription(String endpoint, String ep_type, String uri, boolean is_observable);
    
    // contains a given subscription
    public boolean containsSubscription(String endpoint, String ep_type, String uri);
    
    // remove subscriptions for a given endpoint (called when a endpoint is deregistered)
    public void removeEndpointSubscriptions(String endpoint);
    
    // remove a specific subscription
    public void removeSubscription(String endpoint, String ep_type, String uri);
    
    // get an endpoint type from the endpoint name (cached)
    public String endpointTypeFromEndpointName(String endpoint);
    
    // ObjectID(3)/ObjectID(5)/ObjectID(10255) subscription avoidance...
    public boolean isNotASpecialityResource(String uri);
    
    // resource URI is fully qualified
    public boolean isFullyQualifiedResource(String uri);
}