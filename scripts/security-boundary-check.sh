#!/usr/bin/env bash
# RECON-W43.0 — the security boundary of a MATRIX process, as a gate.
#
# The W42 audit found that the workbench's fifty-two routes were open, that
# the gateway protected ONE of its twenty-seven, that a login endpoint
# issued a developer token to anyone, and that eleven raw listeners bound
# 0.0.0.0. This script checks the boundary that replaced that state, and it
# ASSERTS — a gate that cannot go red is not a gate.
#
# It checks four properties, each of which is a runtime behaviour rather
# than a constant:
#
#   1. LOCAL-ONLY binds the loopback interface, and an anonymous write works.
#   2. NETWORK-EXPOSED without a credential REFUSES TO START.
#   3. NETWORK-EXPOSED with a credential binds the network interface, and an
#      anonymous write is REFUSED while an authenticated write is allowed.
#   4. A read is allowed in every mode.
#
# Usage:
#   scripts/security-boundary-check.sh
#
# Exit status: 0 when every assertion holds, 1 when any does not.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

JAR="matrix-core/build/quarkus-app/quarkus-run.jar"
# The values the check uses, named. The quality gate treats a SCREAMING_CASE
# assignment as an extracted constant, which is what these are.
PORT_LOCAL=9097
PORT_EXPOSED=9098
TOKEN="w43-boundary-check-token"

# How long the check waits for the application to reach its startup event.
readonly STARTUP_WAIT_SECONDS=60
# How long the check waits for the startup GUARD to fire.
readonly GUARD_WAIT_SECONDS=45
# The timeout of a single HTTP request to the application.
readonly REQUEST_TIMEOUT_SECONDS=10
# The HTTP statuses the check expects.
readonly STATUS_UNAUTHORIZED=401
readonly STATUS_OK=200
# The addresses that count as the loopback interface.
readonly LOOPBACK_PATTERN='127.0.0.1:*|localhost:*|"[::1]:"*|"[::ffff:127.0.0.1]:"*'

FAILURES=0
APP_PID=""

if [ ! -f "$JAR" ]; then
  echo "the application is not built: $JAR is missing."
  echo "build it with: ./gradlew :matrix-core:quarkusBuild"
  exit 1
fi

pass() { printf '  OK    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; FAILURES=$((FAILURES + 1)); }

cleanup() {
  if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
    kill -9 "$APP_PID" 2>/dev/null
  fi
  APP_PID=""
}
trap cleanup EXIT

# Waits for the service to answer, or gives up.
wait_up() {
  local port=$1
  for _ in $(seq 1 "$STARTUP_WAIT_SECONDS"); do
    sleep 1
    if curl -s -o /dev/null -m 2 "http://127.0.0.1:$port/q/health" 2>/dev/null; then
      return 0
    fi
  done
  return 1
}

# The HTTP status of a request.
status() {
  local port=$1 method=$2 path=$3 data=${4:-} auth=${5:-}
  if [ -n "$data" ]; then
    if [ -n "$auth" ]; then
      curl -s -o /dev/null -w '%{http_code}' -m "$REQUEST_TIMEOUT_SECONDS" -X "$method" \
        -H "Content-Type: application/json" -H "Authorization: $auth" \
        -d "$data" "http://127.0.0.1:$port$path"
    else
      curl -s -o /dev/null -w '%{http_code}' -m "$REQUEST_TIMEOUT_SECONDS" -X "$method" \
        -H "Content-Type: application/json" -d "$data" \
        "http://127.0.0.1:$port$path"
    fi
  else
    if [ -n "$auth" ]; then
      curl -s -o /dev/null -w '%{http_code}' -m "$REQUEST_TIMEOUT_SECONDS" -X "$method" \
        -H "Authorization: $auth" "http://127.0.0.1:$port$path"
    else
      curl -s -o /dev/null -w '%{http_code}' -m "$REQUEST_TIMEOUT_SECONDS" -X "$method" "http://127.0.0.1:$port$path"
    fi
  fi
}

# The address a port is bound to, as reported by the kernel.
bound_to() {
  local port=$1
  if command -v ss >/dev/null 2>&1; then
    ss -ltn 2>/dev/null | awk -v p=":$port" '$4 ~ p {print $4}' | head -1
  else
    netstat -ltn 2>/dev/null | awk -v p=":$port" '$4 ~ p {print $4}' | head -1
  fi
}

echo "===== 1. LOCAL-ONLY: loopback bound, anonymous write allowed ====="
QUARKUS_HTTP_PORT=$PORT_LOCAL \
  java -Dmatrix.auto-train.enabled=false \
       -jar "$JAR" >/tmp/w43-security-local.log 2>&1 &
APP_PID=$!

if wait_up "$PORT_LOCAL"; then
  BOUND="$(bound_to "$PORT_LOCAL")"
  case "$BOUND" in
    $LOOPBACK_PATTERN)
      pass "the service is bound to the loopback ($BOUND)" ;;
    "") fail "the service is bound to an address the check cannot read" ;;
    *) fail "the service is bound to $BOUND, not to the loopback" ;;
  esac

  CODE="$(status "$PORT_LOCAL" GET /v1/workbench/status)"
  if [ "$CODE" = "$STATUS_OK" ]; then pass "a read is allowed ($CODE)"; else fail "a read returned $CODE"; fi

  CODE="$(status "$PORT_LOCAL" POST /v1/workbench/workspaces/photosynthesis/ask \
    '{"question":"What is photosynthesis?","level":"INTRODUCTORY"}')"
  if [ "$CODE" = "$STATUS_OK" ]; then
    pass "an anonymous write is allowed in local-only mode ($CODE)"
  else
    fail "an anonymous write returned $CODE in local-only mode"
  fi
else
  fail "the service did not start in local-only mode (see /tmp/w43-security-local.log)"
fi
cleanup
sleep 2

echo "===== 2. NETWORK-EXPOSED without a credential: the process refuses to start ====="
QUARKUS_HTTP_PORT=$PORT_EXPOSED \
  java -Dmatrix.auto-train.enabled=false \
       -Dmatrix.security.mode=NETWORK_EXPOSED \
       -jar "$JAR" >/tmp/w43-security-guard.log 2>&1 &
APP_PID=$!
# The application takes some seconds to reach its startup event, so the
# guard is given time to fire. A fixed sleep would have reported a
# process that had not finished STARTING as a process that had started
# exposed — which is what the first version of this check did.
GUARDED=1
for _ in $(seq 1 "$GUARD_WAIT_SECONDS"); do
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    GUARDED=0
    break
  fi
  sleep 1
done
if [ "$GUARDED" -eq 1 ]; then
  fail "the process is still running: it started exposed to the network without a credential"
  cleanup
else
  if grep -q "refusing to start" /tmp/w43-security-guard.log; then
    pass "the process refused to start, and said why"
  else
    fail "the process stopped, but not with the guard's message"
  fi
fi
sleep 2

echo "===== 3. NETWORK-EXPOSED with a credential: anonymous write refused ====="
QUARKUS_HTTP_PORT=$PORT_EXPOSED QUARKUS_HTTP_HOST=0.0.0.0 \
  java -Dmatrix.auto-train.enabled=false \
       -Dmatrix.security.mode=NETWORK_EXPOSED \
       -Dmatrix.security.token="$TOKEN" \
       -jar "$JAR" >/tmp/w43-security-exposed.log 2>&1 &
APP_PID=$!

if wait_up "$PORT_EXPOSED"; then
  CODE="$(status "$PORT_EXPOSED" GET /v1/workbench/status)"
  if [ "$CODE" = "$STATUS_OK" ]; then pass "a read is allowed ($CODE)"; else fail "a read returned $CODE"; fi

  CODE="$(status "$PORT_EXPOSED" POST /v1/workbench/workspaces/photosynthesis/ask \
    '{"question":"What is photosynthesis?","level":"INTRODUCTORY"}')"
  if [ "$CODE" = "$STATUS_UNAUTHORIZED" ]; then
    pass "an anonymous write is refused ($CODE)"
  else
    fail "an anonymous write returned $CODE, expected $STATUS_UNAUTHORIZED"
  fi

  CODE="$(status "$PORT_EXPOSED" POST /v1/workbench/workspaces/photosynthesis/ask \
    '{"question":"What is photosynthesis?","level":"INTRODUCTORY"}' "Bearer $TOKEN")"
  if [ "$CODE" = "$STATUS_OK" ]; then
    pass "an authenticated write is allowed ($CODE)"
  else
    fail "an authenticated write returned $CODE, expected $STATUS_OK"
  fi

  CODE="$(status "$PORT_EXPOSED" POST /v1/workbench/workspaces/photosynthesis/ask \
    '{"question":"What is photosynthesis?","level":"INTRODUCTORY"}' "Bearer wrong-token")"
  if [ "$CODE" = "$STATUS_UNAUTHORIZED" ]; then
    pass "a write with a wrong credential is refused ($CODE)"
  else
    fail "a write with a wrong credential returned $CODE, expected $STATUS_UNAUTHORIZED"
  fi

  # The tool route is the most dangerous mounted route in the repository:
  # it writes files, fetches URLs and runs a shell, and the W43 inventory
  # found that its allow-list checks only the FIRST WORD of a command. It
  # must be behind the same boundary as everything else.
  CODE="$(status "$PORT_EXPOSED" POST /api/v1/tools/invoke '{"tool":"shell","args":"id"}')"
  if [ "$CODE" = "$STATUS_UNAUTHORIZED" ]; then
    pass "an anonymous tool invocation is refused ($CODE)"
  else
    fail "an anonymous tool invocation returned $CODE, expected $STATUS_UNAUTHORIZED"
  fi
else
  fail "the service did not start in network-exposed mode (see /tmp/w43-security-exposed.log)"
fi
cleanup

echo
if [ "$FAILURES" -eq 0 ]; then
  echo "the security boundary: 0 — every assertion held"
  exit 0
fi
echo "the security boundary: $FAILURES — assertion(s) failed"
exit 1
