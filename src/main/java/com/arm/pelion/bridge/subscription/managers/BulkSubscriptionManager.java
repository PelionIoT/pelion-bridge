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
import com.arm.pelion.bridge.data.SerializableHashMap;

/**
 * Bulk Subscription manager (null-manager)
 * @author Doug Anson
 */
public class BulkSubscriptionManager extends BaseClass implements SubscriptionManager {
    private SubscriptionProcessor m_subscription_processor = null;
    private Orchestrator m_orchestrator = null;
    
    // endpoint type hashmap
    private SerializableHashMap m_endpoint_type_list = null;
    
    // constructor
    public BulkSubscriptionManager(Orchestrator orchestrator) {
        super(orchestrator.errorLogger(), orchestrator.preferences());
        this.m_orchestrator = orchestrator;
        this.m_subscription_processor = null;
        
        // create endpoint name/endpoint type map
        this.m_endpoint_type_list = new SerializableHashMap(orchestrator,"BSM_ENDPOINT_TYPE_LIST");
        
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
    public void addSubscription(String ep, String ept, String uri, boolean is_observable) {
        if (this.m_subscription_processor != null) {
            this.m_subscription_processor.subscribe(ep,ept,uri,is_observable);
        }
        this.m_endpoint_type_list.put(ep,ept);
    }

    // contains a subscription
    @Override
    public boolean containsSubscription(String ep, String ept, String uri) {
       // always true
       return true;
    }

    // remove endpoint subscriptions (called when an endpoint is deregistered)
    @Override
    public void removeEndpointSubscriptions(String ep) {
        // just clean up the endpoint type list
        this.m_endpoint_type_list.remove(ep);
    }

    // remove a subscription
    @Override
    public void removeSubscription(String ep, String ept, String uri) {
        if (this.m_subscription_processor != null) {
            this.m_subscription_processor.unsubscribe(ep,ept,uri);
        }
        
        // clean up the endpoint type list
        this.m_endpoint_type_list.remove(ep);
    }

    // get the endpoint type from the endpoint name (if cached...)
    @Override
    public String endpointTypeFromEndpointName(String ep) {
        return (String)this.m_endpoint_type_list.get(ep);
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
