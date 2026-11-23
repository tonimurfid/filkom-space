#!/usr/bin/env bash
# Run from any working directory; uses a dedicated Minikube context.
set -euo pipefail
cd "$(dirname "$0")/.."
profile=${MINIKUBE_PROFILE:-filkom-space}
for command in docker minikube kubectl istioctl python3; do
  command -v "$command" >/dev/null || { echo "Missing command: $command" >&2; exit 1; }
done
for name in ROOM_DB_USERNAME ROOM_DB_PASSWORD RESERVATION_DB_USERNAME RESERVATION_DB_PASSWORD DEMO_ADMIN_PASSWORD DEMO_STUDENT_PASSWORD; do
  [[ -n ${!name:-} ]] || { echo "Export $name first (see docs/DEPLOYMENT_MINIKUBE_ISTIO.md)." >&2; exit 1; }
done
minikube start -p "$profile" --driver=docker \
  --cpus="${MINIKUBE_CPUS:-2}" --memory="${MINIKUBE_MEMORY:-6000}" \
  --kubernetes-version="${KUBERNETES_VERSION:-v1.35.0}"
k() { kubectl --context "$profile" "$@"; }
# Build with the existing multi-stage Dockerfiles. No registry upload is needed.
for service in room-service reservation-service; do
  docker build -f "$service/Dockerfile" -t "filkom-space/$service:minikube" .
  minikube -p "$profile" image load "filkom-space/$service:minikube"
done
istioctl --context "$profile" install -y -f deploy/istio/install.yaml
k apply -f deploy/k8s/namespace.yaml
# Supply credentials at runtime; do not commit a generated Secret manifest.
secret_file=$(mktemp)
chmod 600 "$secret_file"
trap 'rm -f "$secret_file"' EXIT
python3 - "$secret_file" <<'PY'
import os
import sys
mapping = {
    'room-db-username': 'ROOM_DB_USERNAME', 'room-db-password': 'ROOM_DB_PASSWORD',
    'reservation-db-username': 'RESERVATION_DB_USERNAME',
    'reservation-db-password': 'RESERVATION_DB_PASSWORD',
    'admin-password': 'DEMO_ADMIN_PASSWORD', 'student-password': 'DEMO_STUDENT_PASSWORD',
}
with open(sys.argv[1], 'w') as out:
    for key, variable in mapping.items():
        value = os.environ[variable]
        if '\n' in value or '\r' in value:
            raise ValueError(variable + ' must be a single-line value')
        out.write(key + '=' + value + '\n')
PY
k -n filkom-space create secret generic filkom-secrets --from-env-file="$secret_file" \
  --dry-run=client -o yaml | k apply --server-side -f -
rm -f "$secret_file"
k apply -f deploy/k8s/config.yaml
k -n filkom-space patch configmap filkom-config --type merge \
  -p "$(python3 -c 'import json,os; print(json.dumps({"data":{"DEMO_STUDENT_ID":os.environ.get("DEMO_STUDENT_ID","235150200111001")}}))')"
k apply -f deploy/k8s/databases.yaml
for database in room-db reservation-db; do
  k -n filkom-space rollout status "statefulset/$database" --timeout=300s
done
k apply -f deploy/k8s/applications.yaml
k apply -f deploy/istio/traffic.yaml
# Refresh Pods if an existing profile has reused the local image tag or new env.
k -n filkom-space rollout restart deployment/room-service deployment/reservation-service
for service in room-service reservation-service; do
  k -n filkom-space rollout status "deployment/$service" --timeout=420s
done
istioctl --context "$profile" analyze -n filkom-space
k -n filkom-space get pods,services,pvc
printf '\nReady. Run: bash scripts/verify_minikube.sh\n'
