#!/bin/bash -ex

kubectl apply -f target/deployment/manifests/storedq-api.yaml --record