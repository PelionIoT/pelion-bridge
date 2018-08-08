#!/usr/bin/python

import paho.mqtt.client as paho

import base64
import json
from Crypto.Cipher import AES
from Crypto import Random
import base64
import os

# the block size for the cipher object; must be 16, 24, or 32 for AES
BLOCK_SIZE = 32

# the character used for padding--with a block cipher such as AES, the value
# you encrypt must be a multiple of BLOCK_SIZE in length.  This character is
# used to ensure that your value is always a multiple of BLOCK_SIZE
PADDING = '{'

# one-liner to sufficiently pad the text to be encrypted
pad = lambda s: s + (BLOCK_SIZE - len(s) % BLOCK_SIZE) * PADDING

# one-liners to encrypt/encode and decrypt/decode a string
# encrypt with AES, encode with base64
EncodeAES = lambda c, s: base64.b64encode(c.encrypt(pad(s)))
DecodeAES = lambda c, e: c.decrypt(base64.b64decode(e)).rstrip(PADDING)

# generate a random secret key
secret_bytes = bytearray(16)
for i in range(0,16):
	secret_bytes[i] = i
secret = buffer(secret_bytes,0,16)

iv_buffer = bytearray(16)
iv = buffer(iv_buffer,0,16)


# create a cipher object using the random secret
cipher = AES.new(secret)

mqttc = paho.Client()

def decrypt(data, secret, iv):
	aes = AES.new(secret, AES.MODE_CBC, iv)
	return aes.decrypt(data)

# Callbacks
def on_connect(mosq, obj, rc):
    print("connect rc: "+str(rc))

def on_message(mosq, obj, msg):
    print( "Received on topic: " + msg.topic + " Message: "+str(msg.payload) + "\n");
    if "notification" in msg.topic:
    	parsed = json.loads(msg.payload) 
    	print( "Payload: " + parsed[0]['payload'] + "\n");
	ciphertext = base64.b64decode(parsed[0]['payload']);
    	print( "Decrypted: " + decrypt(parsed[0]['payload'],secret,iv) + "\n");
 
def on_subscribe(mosq, obj, mid, granted_qos):
    print("Subscribed OK")

# Set callbacks
mqttc.on_message = on_message
mqttc.on_connect = on_connect
mqttc.on_subscribe = on_subscribe

# Connect and subscribe
ip = "10.1.0.26"
mqttc.connect(ip, 1883, 60)
mqttc.subscribe("/domain/#", 0)

# Wait forever, receiving messages
rc = 0
while rc == 0:
    rc = mqttc.loop()

print("rc: "+str(rc))
