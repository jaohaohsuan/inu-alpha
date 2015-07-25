#!/bin/bash

set -e

exec "$@" &

./logstash/bin/logstash --verbose -f /logstash/stt-xml.conf


