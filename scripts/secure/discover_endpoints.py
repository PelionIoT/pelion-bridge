#!/usr/bin/python

import paho.mqtt.client as paho
mqttc = paho.Client()
topic = "/request/domain/endpoints"
message = "{ \"type\": \"mbed-eth-device\", \"stale\":\"false\" }"
#message = "{ \"type\": \"\", \"status\":\"\" }"
#message = "{}"
ip = "10.1.0.26"
mqttc.connect(ip, 1883, 60)
mqttc.publish(topic,message)
mqttc.disconnect();
