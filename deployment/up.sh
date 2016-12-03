#!/bin/bash

set -x

if kubectl get petset seed >/dev/nul 2>&1; then
  kubectl delete petset seed
  kubectl delete po -l app=storedq-cluster
fi

kubectl apply -f target/deployment/manifests/seed-petset.yaml

sleep 10

kubectl delete po seed-0

kubectl apply -f target/deployment/manifests/frontend-deploy.yaml --record

set +x