set -x

my_ip=$(hostname -i)

function format {
  local fqdn=$1

  local result=$(nslookup $fqdn 2> /dev/null | \
    grep 'Address' | \
    head -5 | awk '{print $3}' | \
    sort | xargs | \
    sed -e 's/ /,/g')
  if [ -n "$result" ]; then
    export $2=${result}
  fi
}

export SEED_NODES=$(kubectl get endpoints ${PEER_DISCOVERY_SERVICE} --no-headers | awk '{ print $2}')

set +x