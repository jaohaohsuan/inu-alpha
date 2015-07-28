#!/bin/bash

set -e

exec "$@" &

./opt/logstash/bin/logstash -v -f /opt/logstash/logstash-config/logstash.conf