#!/bin/bash

my_ip=$(hostname --ip-address)

function format {
  local fqdn=$1

  local result=$(host $fqdn | \
    grep -v "not found" | grep -v "connection timed out" | \
    grep -v $my_ip | \
    sort | \
    head -5 | \
    awk '{print $4}' | \
    xargs | \
    sed -e 's/ /,/g')
  if [ ! -z "$result" ]; then
    export $2=$result
  fi
}

format $PEER_DISCOVERY_SERVICE SEED_NODES
format $AKKA_PERSISTENCE_SERVICE CASSANDRA_NODES