#!/bin/bash

set -x

kubectl apply -f target/deployment/manifests/svc.yaml

kubectl apply -f target/deployment/manifests/deploy.yaml --record

set +x