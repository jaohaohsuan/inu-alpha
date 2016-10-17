#!/bin/bash

set -x

PETSET_NAME=seed

if kubectl get petset ${PETSET_NAME} >/dev/nul 2>&1; then
  kubectl delete petset ${PETSET_NAME}
  kubectl delete po -l app=storedq-cluster
fi

kubectl apply -f target/deployment/manifests/seed-petset.yaml

sleep 20

kubectl apply -f target/deployment/manifests/frontend-deploy.yaml --record

set +x