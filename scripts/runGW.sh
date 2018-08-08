#!/bin/sh

rm -f ./logs/mds-mqtt-gateway.log 2> /dev/null
mkdir -p ./logs
java -Dconfig_file="conf/gateway.properties" -jar mds-mqtt-gateway-1.0-SNAPSHOT.war 2>&1 1>./logs/mds-mqtt-gateway.log
