#!/bin/bash

set -e

exec "$@" &

./elk/logstash/bin/logstash -f /elk/logstash/logstash-config/logstash-cht.conf &

./elk/logstash/bin/logstash -f /elk/logstash/logstash-config/logstash-ytx.conf

