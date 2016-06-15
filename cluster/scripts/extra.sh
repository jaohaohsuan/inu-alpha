set -x

my_ip=$(hostname -i)

function format {
  local fqdn=$1

  local result=$(nslookup $fqdn 2> /dev/null | \
    grep -v ${my_ip} | \
    grep 'Address' | \
    head -5 | awk '{print $3}' | \
    sort | xargs | \
    sed -e 's/ /,/g')
  if [ -n "$result" ]; then
    export $2=${result}
  fi
}

format $PEER_DISCOVERY_SERVICE SEED_NODES
format $AKKA_PERSISTENCE_SERVICE CASSANDRA_NODES

set +x