#!/bin/bash

set -x

# prepare deployment
# substitute ${version} in target/deployment/*.yaml and *.sh files
sbt 'project root' 'clean' 'compile' 'release'

# make storedq-compute images
sbt 'project cluster' 'clean' 'compile' 'test' 'docker:publish'

# make storedq-api images
sbt 'project frontend' 'clean' 'compile' 'test' 'docker:publish'

chmod +x target/deployment/up.sh
./target/deployment/up.sh

# docker rmi $(docker images "dangling=true" -q) 2>&1 || true
# docker rmi $(docker images | grep 127.0.0.1:5000/inu/storedq-compute | tail -n +3 | awk '{print $3'}) 2>&1 || true
# docker rmi $(docker images | grep 127.0.0.1:5000/inu/storedq-api | tail -n +3 | awk '{print $3'}) 2>&1 || true

set +x

