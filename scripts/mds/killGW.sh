#!/bin/sh

PID=`ps -ef | grep java | grep -v grep | grep mds-mqtt | awk '{ print $2 }'`

if [ "${PID}X" != "X" ]; then
     echo "Killing mDS-MQTT Gateway..."
     kill ${PID}
else
     echo "mDS-MQTT Gateway not running (OK)"
fi
exit 0
