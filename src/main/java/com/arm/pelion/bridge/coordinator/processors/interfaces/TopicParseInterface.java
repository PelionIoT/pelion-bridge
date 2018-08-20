/*
 * @file  TopicParseInterface.java
 * @brief interface for topic parsing
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
 * TopicParseInterface Interface defines parsing of topics for endpoint, endpoint type, and URI
 * 
 * @author Doug Anson
 */
public interface TopicParseInterface {
    // get the endpoint name from the topic
    public String getEndpointNameFromTopic(String topic);
    
    // get the endpoint type from the topic
    public String getEndpointTypeFromTopic(String topic);
    
    // get the endpoint resource URI from the topic
    public String getResourceURIFromTopic(String topic);
    
    // get the CoAP verb from the topic
    public String getCoAPVerbFromTopic(String topic);
}
