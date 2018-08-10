/**
 * @file BulkSubscriptionManager.java
 * @brief In-memory subscription manager
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
package com.arm.pelion.bridge.subscription.managers;

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.interfaces.SubscriptionManager;
import com.arm.pelion.bridge.coordinator.processors.interfaces.SubscriptionProcessor;
import com.arm.pelion.bridge.core.BaseClass;

/**
 * Bulk Subscription manager (null-manager)
 * @author Doug Anson
 */
public class BulkSubscriptionManager extends BaseClass implements SubscriptionManager {
    private SubscriptionProcessor m_subscription_processor = null;
    private Orchestrator m_orchestrator = null;
    
    // constructor
    public BulkSubscriptionManager(Orchestrator orchestrator) {
        super(orchestrator.errorLogger(), orchestrator.preferences());
        this.m_orchestrator = orchestrator;
        this.m_subscription_processor = null;
        
        // DEBUG
        this.errorLogger().info("SubscriptionManager: Bulk subscription manager initialized.");
    }
    
    // add an additional subscription processor
    @Override
    public void addSubscriptionProcessor(SubscriptionProcessor subscription_processor) {
        this.m_subscription_processor = subscription_processor;
    }

    // add a subscription
    @Override
    public void addSubscription(String domain, String endpoint, String ep_type, String uri, boolean is_observable) {
        if (this.m_subscription_processor != null) {
            this.m_subscription_processor.subscribe(domain,endpoint,ep_type,uri,is_observable);
        }
    }

    // contains a subscription
    @Override
    public boolean containsSubscription(String domain, String endpoint, String ep_type, String uri) {
       // always true
       return true;
    }

    // remove endpoint subscriptions (called when an endpoint is deregistered)
    @Override
    public void removeEndpointSubscriptions(String endpoint) {
        // not used (called when a endpoint is deregistered)
    }

    // remove a subscription
    @Override
    public void removeSubscription(String domain, String endpoint, String ep_type, String uri) {
        if (this.m_subscription_processor != null) {
            this.m_subscription_processor.unsubscribe(domain,endpoint,ep_type,uri);
        }
    }

    // get the endpoint type from the endpoint name (if cached...)
    @Override
    public String endpointTypeFromEndpointName(String endpoint) {
        // not cached - so defer... 
        return null;
    }
    
    // ObjectID(3)/ObjectID(5)/ObjectID(10255) resource observation enablement
    @Override
    public boolean isNotASpecialityResource(String uri) {
        // always true
        return true;
    }
    
    // resource URI is fully qualified
    @Override
    public boolean isFullyQualifiedResource(String uri) {
        if (uri != null && uri.length() > 0) {
            String[] tmp = uri.split("/");
            if (tmp != null && tmp.length == 4) {
                return true;
            }
        }
        return false;
    }
}
