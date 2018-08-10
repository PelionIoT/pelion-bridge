/*
 * @file  SubscriptionProcessor.java
 * @brief interface for for additional handling of subscribe/unsubscribe events (OPTIONAL)
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
 * Subscription Processor for handling events during subscription processing
 * @author Doug Anson
 */
public interface SubscriptionProcessor {
    // subscription
    public void subscribe(String domain, String ep, String ept, String path, boolean is_observable);
    
    // unsubscribe
    public void unsubscribe(String domain, String ep, String ept, String path);
}
