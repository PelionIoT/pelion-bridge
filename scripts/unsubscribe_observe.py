#!/usr/bin/python

import paho.mqtt.client as paho
mqttc = paho.Client()
topic = "/request/domain/subscriptions/mbed-eth-observe/303/0/5700"
message = "{\"unsubscribe\":\"true\"}"
ip = "10.1.0.26"
mqttc.connect(ip, 1883, 60)
mqttc.publish(topic,message)
mqttc.disconnect();
