#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
profile=${MINIKUBE_PROFILE:-filkom-space}
export EVIDENCE_DIR=${EVIDENCE_DIR:-evidence}
mkdir -p "$EVIDENCE_DIR"
k() { kubectl --context "$profile" "$@"; }
for name in DEMO_ADMIN_PASSWORD DEMO_STUDENT_PASSWORD; do
  [[ -n ${!name:-} ]] || { echo "Export $name first." >&2; exit 1; }
done
k -n filkom-space get pods,services,pvc -o wide | tee "$EVIDENCE_DIR/cluster.txt"
k -n filkom-space get pods -o json > "$EVIDENCE_DIR/pods.json"
python3 - "$EVIDENCE_DIR/pods.json" <<'PY'
import json,sys
pods=json.load(open(sys.argv[1]))['items']
for app in ['room-service','reservation-service']:
    active=[p for p in pods if p['metadata'].get('labels',{}).get('app')==app and not p['metadata'].get('deletionTimestamp')]
    assert active, app
    for pod in active:
        status=pod['status']
        # Kubernetes native sidecars use initContainerStatuses (restartPolicy Always).
        containers=status.get('containerStatuses', []) + status.get('initContainerStatuses', [])
        statuses={s['name']:s.get('ready', False) for s in containers}
        assert statuses.get(app) and statuses.get('istio-proxy'), statuses
    print('PASS: '+app+' application and istio-proxy ready (2/2)')
PY
istioctl --context "$profile" analyze -n filkom-space 2>&1 | tee "$EVIDENCE_DIR/istio-analyze.txt"
istioctl --context "$profile" proxy-status | tee "$EVIDENCE_DIR/proxy-status.txt"
k -n filkom-space get gateway,virtualservice,destinationrule,peerauthentication -o yaml > "$EVIDENCE_DIR/mesh-config.yaml"
k -n istio-system port-forward --address=127.0.0.1 svc/istio-ingressgateway 18080:80 > "$EVIDENCE_DIR/port-forward.log" 2>&1 &
forward_pid=$!
trap 'kill "$forward_pid" 2>/dev/null || true' EXIT
python3 - <<'PY'
import socket,time
for attempt in range(60):
    try:
        with socket.create_connection(('127.0.0.1',18080),timeout=1):break
    except OSError:time.sleep(1)
else:raise RuntimeError('Ingress port-forward did not start')
PY
# Always test the port-forward just created, independent of a caller's URL override.
GATEWAY_BASE_URL=http://127.0.0.1:18080 python3 scripts/smoke_gateway.py | tee "$EVIDENCE_DIR/gateway-smoke.txt"
# Public GET must fail at transport level from a Pod with no Envoy sidecar.
# A 401/403 HTTP response would NOT prove mTLS rejection, and fails this check.
probe="plaintext-probe-$(date +%s)"
k -n filkom-space run "$probe" --image=python:3.12-alpine \
  --restart=Never \
  --annotations='sidecar.istio.io/inject=false' \
  --command -- python -u -c '
import http.client, urllib.error, urllib.request
for host,port in [("room-service",8081),("reservation-service",8082)]:
    path="/api/v2/rooms" if port==8081 else "/api/v2/availability"
    try:
        urllib.request.urlopen(f"http://{host}:{port}{path}", timeout=10)
    except urllib.error.HTTPError as error:
        raise AssertionError(f"Plaintext reached HTTP application: {error.code}")
    except (ConnectionResetError, http.client.RemoteDisconnected) as error:
        print(f"PASS: STRICT mTLS rejected plaintext to {host}: {type(error).__name__}")
    except urllib.error.URLError as error:
        if not isinstance(error.reason, ConnectionResetError):
            raise AssertionError(f"Inconclusive transport failure: {error.reason}")
        print(f"PASS: STRICT mTLS rejected plaintext to {host}: connection reset")
    else:
        raise AssertionError(f"Plaintext unexpectedly reached {host}")
'
if ! k -n filkom-space wait --for=jsonpath='{.status.phase}'=Succeeded "pod/$probe" --timeout=180s; then
  k -n filkom-space logs "$probe" || true
  k -n filkom-space get pod "$probe" -o yaml
  exit 1
fi
k -n filkom-space logs "$probe" | tee "$EVIDENCE_DIR/mtls-negative.txt"
k -n filkom-space get pod "$probe" -o json > "$EVIDENCE_DIR/mtls-probe.json"
k -n filkom-space delete pod "$probe" --wait=false > /dev/null
if [[ ${RUN_NEWMAN:-false} == true ]]; then
  npx --yes --package=newman@6.2.1 --package=newman-reporter-htmlextra@1.23.1 newman run tests/postman/filkom-space-v2.postman_collection.json \
    --env-var roomBase=http://127.0.0.1:18080 \
    --env-var reservationBase=http://127.0.0.1:18080 \
    --env-var "adminPassword=$DEMO_ADMIN_PASSWORD" \
    --env-var "studentPassword=$DEMO_STUDENT_PASSWORD" \
    --reporters cli,htmlextra --reporter-htmlextra-export "$EVIDENCE_DIR/postman-report.html" \
    --reporter-htmlextra-omitHeaders --reporter-htmlextra-title "FILKOM Space API v2 through Istio" \
    --timeout-request 20000 --color off | tee "$EVIDENCE_DIR/newman.txt"
fi
printf '\nPASS: Minikube + Istio verification complete. Evidence: %s\n' "$EVIDENCE_DIR"
