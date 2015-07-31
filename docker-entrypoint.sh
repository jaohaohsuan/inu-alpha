#!/bin/bash

set -e

exec "$@" &

./elk/logstash/bin/logstash -v -f /elk/logstash/logstash-config/logstash.conf