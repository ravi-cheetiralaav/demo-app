#!/usr/bin/env bash
# Post-deployment smoke test for demo-app running in Kubernetes.
# Usage: scripts/smoke-test.sh [namespace] [expected-version]
set -euo pipefail

NAMESPACE="${1:-demo-app}"
EXPECTED_VERSION="${2:-}"
LOCAL_PORT="${LOCAL_PORT:-18080}"
FAILURES=0

pass() { echo "  [PASS] $1"; }
fail() { echo "  [FAIL] $1"; FAILURES=$((FAILURES + 1)); }

echo "==> Waiting for rollout of deployment/demo-app in namespace '${NAMESPACE}'"
kubectl -n "${NAMESPACE}" rollout status deployment/demo-app --timeout=180s

echo "==> Port-forwarding service/demo-app to localhost:${LOCAL_PORT}"
kubectl -n "${NAMESPACE}" port-forward service/demo-app "${LOCAL_PORT}:80" >/dev/null 2>&1 &
PF_PID=$!
trap 'kill ${PF_PID} 2>/dev/null || true' EXIT

for _ in $(seq 1 30); do
  curl -fs "http://localhost:${LOCAL_PORT}/actuator/health/readiness" >/dev/null 2>&1 && break
  sleep 1
done

BASE="http://localhost:${LOCAL_PORT}"
PODS="$(kubectl -n "${NAMESPACE}" get pods -l app.kubernetes.io/name=demo-app -o jsonpath='{.items[*].metadata.name}')"
NODES="$(kubectl -n "${NAMESPACE}" get pods -l app.kubernetes.io/name=demo-app -o jsonpath='{.items[*].spec.nodeName}')"

echo "==> Running checks"
curl -fs "${BASE}/actuator/health/liveness" | grep -q '"status":"UP"' && pass "liveness probe UP" || fail "liveness probe"
curl -fs "${BASE}/actuator/health/readiness" | grep -q '"status":"UP"' && pass "readiness probe UP" || fail "readiness probe"

HTML="$(curl -fs "${BASE}/" || true)"
grep -q "Java web app running on" <<<"${HTML}" && pass "home page shows the app message" || fail "home page message"
grep -q "Running in Kubernetes" <<<"${HTML}" && pass "home page detects Kubernetes" || fail "Kubernetes detection"

INFO="$(curl -fs "${BASE}/api/info" || true)"
json() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" <<<"${INFO}"; }

VERSION="$(json version)"; POD="$(json podName)"; NODE="$(json nodeName)"; NS="$(json podNamespace)"
echo "  version=${VERSION} pod=${POD} node=${NODE} namespace=${NS}"

if [[ -n "${EXPECTED_VERSION}" ]]; then
  [[ "${VERSION}" == "${EXPECTED_VERSION}" ]] && pass "version is ${EXPECTED_VERSION}" || fail "version '${VERSION}' != '${EXPECTED_VERSION}'"
else
  [[ -n "${VERSION}" && "${VERSION}" != "n/a" ]] && pass "version reported (${VERSION})" || fail "version missing"
fi
[[ " ${PODS} " == *" ${POD} "* ]] && pass "podName matches a running pod" || fail "podName '${POD}' not in [${PODS}]"
[[ " ${NODES} " == *" ${NODE} "* ]] && pass "nodeName matches the pod's node" || fail "nodeName '${NODE}' not in [${NODES}]"
[[ "${NS}" == "${NAMESPACE}" ]] && pass "namespace is ${NAMESPACE}" || fail "namespace '${NS}'"

echo
if [[ ${FAILURES} -gt 0 ]]; then
  echo "Smoke test FAILED (${FAILURES} check(s))"
  exit 1
fi
echo "Smoke test PASSED"
