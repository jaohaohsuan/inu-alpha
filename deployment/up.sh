#!/bin/bash

set -x

#kubectl apply -f target/deployment/manifests/svc.yaml

kubectl apply -f target/deployment/manifests/seed-petset.yaml --record

sleep 20

kubectl apply -f target/deployment/manifests/frontend-deploy.yaml --record

set +x