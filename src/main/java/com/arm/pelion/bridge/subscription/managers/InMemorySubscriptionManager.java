/**
 * @file InMemorySubscriptionManager.java
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
import com.arm.pelion.bridge.data.SerializableArrayListOfHashMaps;
import com.arm.pelion.bridge.data.SerializableHashMap;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;

/**
 * In-memory subscription manager
 *
 * @author Doug Anson
 */
public class InMemorySubscriptionManager extends BaseClass implements SubscriptionManager {
    // defaulted endpoint type
    protected static String DEFAULT_ENDPOINT_TYPE="default";
    
    private Orchestrator m_orchestrator = null;
    private String m_default_ep_type = null;
    private SubscriptionProcessor m_subscription_processor = null;

    private SerializableArrayListOfHashMaps m_subscriptions = null;
    
    // default behavior is to ignore ObjectID(1)/ObjectID(3)/ObjectID(5)/ObjectID(10255) subscriptions - those are auto-handled.
    private boolean m_enable_1_3_5_10255_objectid_subscriptions = false;
    
    // constructor
    public InMemorySubscriptionManager(Orchestrator orchestrator) {
        super(orchestrator.errorLogger(), orchestrator.preferences());
        this.m_orchestrator = orchestrator;
        this.m_subscriptions = new SerializableArrayListOfHashMaps(orchestrator,"SUBSCRIPTION_DETAILS");
        this.m_default_ep_type = this.preferences().valueOf("mds_def_ep_type");
        if (this.m_default_ep_type == null || this.m_default_ep_type.length() == 0) {
            this.m_default_ep_type = DEFAULT_ENDPOINT_TYPE;
        }
        this.m_subscription_processor = null;
        
        // override for ObjectID(1)/ObjectID(3)/ObjectID(5)/ObjectID(10255) subscriptions
        this.m_enable_1_3_5_10255_objectid_subscriptions = this.preferences().booleanValueOf("enable_1_3_5_10255_subscriptions");
        if (this.m_enable_1_3_5_10255_objectid_subscriptions == true) {
            // Override ENABLED
            this.errorLogger().warning("SubscriptionManager(InMemory): ObjectID(1)/ObjectID(3)/ObjectID(5)/ObjectID(10255) subscriptions are ENABLED.");
        }
        else {
            // disabled
            this.errorLogger().info("SubscriptionManager(InMemory): ObjectID(1)/ObjectID(3)/ObjectID(5)/ObjectID(10255) subscriptions are DISABLED.");
        }
        
        // DEBUG
        this.errorLogger().info("SubscriptionManager(InMemory): initialized.");
    }
    
    // add a subscription processor to the subscription manager
    @Override
    public void addSubscriptionProcessor(SubscriptionProcessor subscription_processor) {
        this.m_subscription_processor = subscription_processor;
    }
    
    // ObjectID(3)/ObjectID(5)/ObjectID(10255) resource observation enablement
    @Override
    public boolean isNotASpecialityResource(String uri) {
        if (this.m_enable_1_3_5_10255_objectid_subscriptions == false) {
            if (this.isObjectID(uri,1) == true || this.isObjectID(uri,3) == true || this.isObjectID(uri,5) == true || this.isObjectID(uri,10255) == true) {
                return false;
            }
        }
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

    // add subscription
    @Override
    public void addSubscription(String endpoint, String ep_type, String uri, boolean is_observable) {
        ep_type = this.checkAndDefaultEndpointType(ep_type);
        if (!this.containsSubscription(endpoint, ep_type, uri)) {
            // adjust for ObjectID(1)/ObjectID(3)/ObjectID(5)/ObjectID(10255) avoidance...
            if (this.m_enable_1_3_5_10255_objectid_subscriptions == true) {
                this.errorLogger().info("SubscriptionManager(InMemory): Adding Subscription: " + endpoint + ":" + ep_type + ":" + uri);
                this.m_subscriptions.add(this.makeSubscription(endpoint, ep_type, uri));
                if (this.m_subscription_processor != null) {
                    this.m_subscription_processor.subscribe(endpoint,ep_type,uri,is_observable);
                }
            }
            else {
                // not enabling ObjectID(1)/ObjectID(3)/ObjectID(5)/ObjectID(10255) monitoring... so make sure the subscription does not refer to those...
                if (this.isObjectID(uri,1) == false && this.isObjectID(uri,3) == false && this.isObjectID(uri,5) == false && this.isObjectID(uri,10255) == false) {
                    // is NOT ObjectID(3)/ObjectID(5)/ObjectID(10255)... so add it...
                    this.errorLogger().info("SubscriptionManager(InMemory): Adding Subscription: " + endpoint + ":" + ep_type + ":" + uri);
                    this.m_subscriptions.add(this.makeSubscription(endpoint, ep_type, uri));
                    if (this.m_subscription_processor != null) {
                        this.m_subscription_processor.subscribe(endpoint,ep_type,uri,is_observable);
                    }
                }
                else {
                    // ignore it as its an ObjectID(3)/ObjectID(5)/ObjectID(10255) and we are not enabling them...
                    this.errorLogger().info("SubscriptionManager(InMemory): NOT adding ObjectID(3)/ObjectID(5)/ObjectID(10255) subscription: " + endpoint + ":" + ep_type + ":" + uri);
                }
            }
        }
    }

    // contains a given subscription?
    @Override
    public boolean containsSubscription(String endpoint, String ep_type, String uri) {
        boolean has_subscription = false;
        HashMap<String, Serializable> subscription = this.makeSubscription(endpoint, ep_type, uri);
        if (this.containsSubscription(subscription) >= 0) {
            has_subscription = true;
        }

        return has_subscription;
    }
    
    // remove all subscriptions for a given endpoint (called when a endpoint is deregistered)
    @Override
    public void removeEndpointSubscriptions(String endpoint) {
        for(int i=0;i<this.m_subscriptions.size() && this.m_subscription_processor != null;++i) {
            HashMap<String,Serializable> subscription = (HashMap<String,Serializable>)this.m_subscriptions.get(i);
            String t_endpoint = (String)subscription.get("endpoint");
            String t_ept = (String)subscription.get("ep_type");
            String t_uri = (String)subscription.get("uri");
            if (t_endpoint != null && endpoint != null && t_endpoint.equalsIgnoreCase(endpoint)) {
                this.errorLogger().info("SubscriptionManager(InMemory): Removing Subscription: " + t_endpoint + ":" + t_uri);
                this.m_subscription_processor.unsubscribe(t_endpoint,t_ept,t_uri);
            }
        }
        for(int i=0;i<this.m_subscriptions.size();++i) {
            HashMap<String,Serializable> subscription = this.m_subscriptions.get(i);
            String t_endpoint = (String)subscription.get("endpoint");
            if (t_endpoint != null && endpoint != null && t_endpoint.equalsIgnoreCase(endpoint)) {
                this.m_subscriptions.remove(i);
            }
        }
    }

    // remove a subscription
    @Override
    public void removeSubscription(String endpoint, String ep_type, String uri) {
        HashMap<String,Serializable> subscription = this.makeSubscription(endpoint, ep_type, uri);
        int index = this.containsSubscription(subscription);
        if (index >= 0) {
            this.errorLogger().info("SubscriptionManager(InMemory): Removing Subscription: " + endpoint + ":" + uri);
            this.m_subscriptions.remove(index);
            if (this.m_subscription_processor != null) {
                this.m_subscription_processor.unsubscribe(endpoint,ep_type,uri);
            }
        }
    }

    // contains a given subscription?
    private int containsSubscription(HashMap<String,Serializable> subscription) {
        int index = -1;

        for (int i = 0; i < this.m_subscriptions.size() && index < 0; ++i) {
            if (this.sameSubscription(subscription,this.m_subscriptions.get(i))) {
                index = i;
            }
        }

        return index;
    }

    // compare subscriptions
    private boolean sameSubscription(HashMap<String,Serializable> s1, HashMap<String,Serializable> s2) {
        boolean same_subscription = false;

        // compare contents...
        if (s1.get("endpoint") != null && s2.get("endpoint") != null && ((String)s1.get("endpoint")).equalsIgnoreCase((String)s2.get("endpoint"))) {
            if (s1.get("ep_type") != null && s2.get("ep_type") != null && ((String)s1.get("ep_type")).equalsIgnoreCase((String)s2.get("ep_type"))) {
                if (s1.get("uri") != null && s2.get("uri") != null && ((String)s1.get("uri")).equalsIgnoreCase((String)s2.get("uri"))) {
                    // they are the same
                    same_subscription = true;
                }
            }
        }

        return same_subscription;
    }

    // make subscription entry 
    private HashMap<String,Serializable> makeSubscription(String endpoint, String ep_type, String uri) {
        String d = this.m_orchestrator.getTablenameDelimiter();
        SerializableHashMap subscription = new SerializableHashMap(this.m_orchestrator,"SUB" + d + endpoint + d + ep_type + d + uri);
        subscription.put("endpoint", endpoint);
        subscription.put("ep_type", ep_type);
        subscription.put("uri", uri);
        return (HashMap<String,Serializable>)subscription.map();
    }
    
    // defaulted endpoint type
    private String checkAndDefaultEndpointType(String ep_type) {
        if (ep_type == null || ep_type.length() <= 0) {
            // return the defaulted EP type
            if (this.m_default_ep_type == null) {
                return DEFAULT_ENDPOINT_TYPE;
            }
            else {
                return this.m_default_ep_type;
            }
        }
        return ep_type;
    }

    // get the endpoint type for a given endpoint
    @Override
    public String endpointTypeFromEndpointName(String endpoint) {
        String ep_type = null;

        for (int i = 0; i < this.m_subscriptions.size() && ep_type == null; ++i) {
            HashMap<String, Serializable> subscription = this.m_subscriptions.get(i);
            if (endpoint != null && endpoint.equalsIgnoreCase((String)subscription.get("endpoint")) == true) {
                ep_type = (String)subscription.get("ep_type");
            }
        }

        // DEBUG
        this.errorLogger().info("SubscriptionManager(InMemory): endpoint: " + endpoint + " type: " + ep_type);

        // return the endpoint type
        return ep_type;
    }
    
    // check for a specific objectID in a subscription
    private boolean isObjectID(String uri,int id) {
        boolean is_objectid = false;
        
        if (uri != null && uri.length() > 0) {
            String str_id = "" + id;
            String[] tmp = uri.split("/");
            // DEBUG
            this.errorLogger().info("SubscriptionManager(InMemory): isObjectID() URI: " + uri + " ID: " + str_id + " ARR: " + Arrays.toString(tmp));
            if (tmp != null && tmp.length > 1) {
                String tmp_id = tmp[1];
                if (tmp_id != null && tmp_id.equalsIgnoreCase(str_id)) {
                    is_objectid = true;
                }
            }
        }
        
        return is_objectid;
    }
}
