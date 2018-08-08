/**
 * @file    MQTTMessage.java
 * @brief MQTT Message base class
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
package com.arm.connector.bridge.transport;

import org.fusesource.mqtt.client.Message;

/**
 * MQTT Message base class
 *
 * @author Doug Anson
 */
public class MQTTMessage {

    Message m_mqtt_message;
    String m_message;
    String m_topic;

    public MQTTMessage(Message mqtt_message) {
        this.m_mqtt_message = mqtt_message;
        byte[] payload = this.m_mqtt_message.getPayload();
        if (payload != null && payload.length > 0) {
            this.m_message = new String(payload);
        }
        this.m_topic = this.m_mqtt_message.getTopic();
    }

    public String getMessage() {
        return this.m_message;
    }

    public String getTopic() {
        return this.m_topic;
    }

    public void ack() {
        this.m_mqtt_message.ack();
    }
}
