/*
 * Copyright 2018 douans01.
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

import com.arm.pelion.bridge.transport.MQTTTransport;
import org.fusesource.mqtt.client.Topic;

/**
 * Interface to undergo a network re-connection cycle
 * @author Doug Anson
 */
public interface ReconnectionInterface {
    // restart our connection for the given device via MQTT
    public boolean startReconnection(String ep_name,String ep_type,Topic topics[]);
}
