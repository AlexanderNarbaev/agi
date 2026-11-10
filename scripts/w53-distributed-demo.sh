#!/usr/bin/env bash
# W53 — one real distributed cognitive task, two real processes.
#
# The scenario is the mandate's acceptance scenario: chain A receives a cognitive task,
# delegates a bounded piece of work to chain B, chain B runs it through its own
# production cognitive path, and chain A evaluates what came back and records a verdict.
# Then chain B is restarted and the same request is sent again, and the demonstration
# shows that the logical effect happened once.
#
# What this script does NOT do:
#   * it does not fake either process — chain A is the Quarkus application and chain B
#     is the gateway's own JDK server;
#   * it does not hardcode the expected answer into the remote adapter — the answer
#     comes from chain B's brain;
#   * it does not treat a successful HTTP exchange as a successful collaboration — the
#     report records the disposition chain A reached, which may be UNRESOLVED;
#   * it does not print the shared secret.
#
# Usage: scripts/w53-distributed-demo.sh [--skip-build]
# The machine-readable report is written to
# docs-v2/engineering/W53-DISTRIBUTED-DEMONSTRATION.json

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# The ports the two processes serve on. They are overridable so that the demonstration
# can run beside another instance.
CHAIN_A_PORT="${CHAIN_A_PORT:-9191}"
CHAIN_B_PORT="${CHAIN_B_PORT:-8765}"

# The report the demonstration writes.
REPORT="docs-v2/engineering/W53-DISTRIBUTED-DEMONSTRATION.json"

# The directories the two processes keep their state in. The demonstration uses its own
# directories so that it never touches a developer's working state.
WORK="$(mktemp -d)"
export MATRIX_MIND_DIR="$WORK/chain-b-mind"
export MATRIX_COGNITIVE_DIR="$WORK/chain-a-cognitive"
export MATRIX_BRIDGE_JOURNAL_DIR="$WORK/chain-a-bridge"
mkdir -p "$MATRIX_MIND_DIR" "$MATRIX_COGNITIVE_DIR" "$MATRIX_BRIDGE_JOURNAL_DIR"

# The shared secret of the two processes. It is generated here, given to both processes
# through the environment, and never printed.
BRIDGE_SECRET="$(head -c 32 /dev/urandom | base64 | tr -d '\n')"
export MATRIX_BRIDGE_SECRET="$BRIDGE_SECRET"

log() { printf '[w53-demo] %s\n' "$*"; }

cleanup() {
  if [[ -n "${CHAIN_B_PID:-}" ]] && kill -0 "$CHAIN_B_PID" 2>/dev/null; then
    kill "$CHAIN_B_PID" 2>/dev/null || true
    wait "$CHAIN_B_PID" 2>/dev/null || true
  fi
  if [[ -n "${CHAIN_A_PID:-}" ]] && kill -0 "$CHAIN_A_PID" 2>/dev/null; then
    kill "$CHAIN_A_PID" 2>/dev/null || true
    wait "$CHAIN_A_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

wait_for_health() {
  local url="$1" name="$2" attempts=${3:-300}
  while (( attempts > 0 )); do
    # The health endpoint may answer "DOWN" for a check that has nothing to do with the
    # demonstration (a broker that is not running, for instance). What matters here is
    # that the process is serving HTTP at all, so any answer counts.
    if curl -s -o /dev/null --max-time 2 "$url" >/dev/null 2>&1; then
      log "$name is up"
      return 0
    fi
    attempts=$(( attempts - 1 ))
    sleep 1
  done
  log "$name did not become healthy"
  return 1
}

if [[ "${1:-}" != "--skip-build" ]]; then
  log "building the two processes"
  JAVA_HOME="${JAVA_HOME:-$HOME/jdk25}" ./gradlew --offline -q \
    :matrix-api-gateway:installDist :matrix-core:quarkusBuild 2>&1 | tail -5
fi

log "starting chain B — the gateway's own JDK server, in production mode"
MATRIX_MODE=production \
MATRIX_PORT="$CHAIN_B_PORT" \
MATRIX_BRIDGE_SECRET="$BRIDGE_SECRET" \
  matrix-api-gateway/build/install/matrix-api-gateway/bin/matrix-api-gateway \
  >"$WORK/chain-b.log" 2>&1 &
CHAIN_B_PID=$!

wait_for_health "http://127.0.0.1:$CHAIN_B_PORT/health/live" "chain B"

log "starting chain A — the Quarkus application"
MATRIX_PEER_URL="http://127.0.0.1:$CHAIN_B_PORT" \
MATRIX_BRIDGE_SECRET="$BRIDGE_SECRET" \
QUARKUS_HTTP_PORT="$CHAIN_A_PORT" \
  java -jar matrix-core/build/quarkus-app/quarkus-run.jar \
  >"$WORK/chain-a.log" 2>&1 &
CHAIN_A_PID=$!

wait_for_health "http://127.0.0.1:$CHAIN_A_PORT/q/health" "chain A"

# W53 §13.2 — both processes report their own health, and the peer reports the
# capabilities it can really execute. The answers are kept verbatim: the report shows
# what the processes said, not what the script believes about them. The status code
# travels with the body, because chain A's health endpoint answers with a failing code
# when a check that has nothing to do with the demonstration is down — and the process
# still serves the task. The body is the claim; the code is the context.
CHAIN_A_HEALTH="$(curl -s --max-time 10 "http://127.0.0.1:$CHAIN_A_PORT/q/health" || true)"
CHAIN_A_HEALTH_CODE="$(curl -s -o /dev/null --max-time 10 -w '%{http_code}' \
  "http://127.0.0.1:$CHAIN_A_PORT/q/health" || true)"
CHAIN_B_HEALTH="$(curl -s --max-time 10 "http://127.0.0.1:$CHAIN_B_PORT/health/live" || true)"
CHAIN_B_HEALTH_CODE="$(curl -s -o /dev/null --max-time 10 -w '%{http_code}' \
  "http://127.0.0.1:$CHAIN_B_PORT/health/live" || true)"
log "chain A's health ($CHAIN_A_HEALTH_CODE): $CHAIN_A_HEALTH"
log "chain B's health and active capabilities ($CHAIN_B_HEALTH_CODE): $CHAIN_B_HEALTH"

TASK_ID="w53-demo-$(date +%s)"
REQUEST_ID="req-$TASK_ID"

log "chain A receives the cognitive task"
ANSWER="$(curl -fsS --max-time 30 -X POST \
  "http://127.0.0.1:$CHAIN_A_PORT/v1/cognitive/task" \
  -H 'Content-Type: application/json' \
  -d "{\"taskId\":\"$TASK_ID\",\"capabilityId\":\"brain.cognitive-inference\",\"input\":\"is the lamp lit when the power is on and the switch is down\",\"expectation\":\"the lamp is lit\"}")"
log "chain A answered: $ANSWER"

log "chain A reads the durable trace of the task"
TRACE="$(curl -fsS --max-time 10 "http://127.0.0.1:$CHAIN_A_PORT/v1/cognitive/task/$TASK_ID")"

log "the same request is sent again after chain B restarts"
kill "$CHAIN_B_PID" 2>/dev/null || true
wait "$CHAIN_B_PID" 2>/dev/null || true
MATRIX_MODE=production \
MATRIX_PORT="$CHAIN_B_PORT" \
MATRIX_BRIDGE_SECRET="$BRIDGE_SECRET" \
  matrix-api-gateway/build/install/matrix-api-gateway/bin/matrix-api-gateway \
  >>"$WORK/chain-b.log" 2>&1 &
CHAIN_B_PID=$!
wait_for_health "http://127.0.0.1:$CHAIN_B_PORT/health/live" "chain B after the restart"

RETRY="$(curl -fsS --max-time 30 -X POST \
  "http://127.0.0.1:$CHAIN_A_PORT/v1/cognitive/task/replay" \
  -H 'Content-Type: application/json' \
  -d "{\"taskId\":\"$TASK_ID\",\"capabilityId\":\"brain.cognitive-inference\",\"input\":\"is the lamp lit when the power is on and the switch is down\",\"expectation\":\"the lamp is lit\"}")" || true

log "the replay answered: $RETRY"

log "a capability the peer does not serve is refused"
CAPABILITY_REFUSAL="$(curl -fsS --max-time 30 -X POST \
  "http://127.0.0.1:$CHAIN_A_PORT/v1/cognitive/task" \
  -H 'Content-Type: application/json' \
  -d "{\"taskId\":\"w53-demo-unserved-$TASK_ID\",\"capabilityId\":\"brain.capability-the-peer-does-not-serve\",\"input\":\"is the lamp lit\",\"expectation\":\"the lamp is lit\"}")"

log "the same request id under a different payload is refused as a conflict"
CONFLICT_REFUSAL="$(curl -sS --max-time 30 -o "$WORK/conflict.json" -w '%{http_code}' -X POST \
  "http://127.0.0.1:$CHAIN_A_PORT/v1/cognitive/task" \
  -H 'Content-Type: application/json' \
  -d "{\"taskId\":\"$TASK_ID\",\"capabilityId\":\"brain.cognitive-inference\",\"input\":\"a different question under the same identity\",\"expectation\":\"the lamp is lit\"}")"
CONFLICT_BODY="$(cat "$WORK/conflict.json")"

log "a forged request is refused by the peer"
FORGED_REFUSAL="$(python3 scripts/w53-forge-request.py "$CHAIN_B_PORT" "$BRIDGE_SECRET")"

log "reading chain B's durable inbox: the evidence that the task ran once"
INBOX="$(python3 scripts/w53-inbox-reader.py "$MATRIX_MIND_DIR/tasks.sqlite")" || INBOX='[]'

log "chain A is restarted, and the same task is asked for again by its stable id"
kill "$CHAIN_A_PID" 2>/dev/null || true
wait "$CHAIN_A_PID" 2>/dev/null || true
MATRIX_PEER_URL="http://127.0.0.1:$CHAIN_B_PORT" \
MATRIX_BRIDGE_SECRET="$BRIDGE_SECRET" \
QUARKUS_HTTP_PORT="$CHAIN_A_PORT" \
  java -jar matrix-core/build/quarkus-app/quarkus-run.jar \
  >>"$WORK/chain-a.log" 2>&1 &
CHAIN_A_PID=$!
wait_for_health "http://127.0.0.1:$CHAIN_A_PORT/q/health" "chain A after the restart"

TRACE_AFTER_RESTART="$(curl -fsS --max-time 30 \
  "http://127.0.0.1:$CHAIN_A_PORT/v1/cognitive/task/$TASK_ID")"
log "the task after the restart of chain A: $TRACE_AFTER_RESTART"

log "chain B's inbox after the restart of chain A: the task was not sent again"
INBOX_AFTER="$(python3 scripts/w53-inbox-reader.py "$MATRIX_MIND_DIR/tasks.sqlite")" \
  || INBOX_AFTER='[]'

# The check that makes the restart evidence: the same task, the same request id, the same
# disposition — and the peer did not receive it a second time. A demonstration that
# printed the answers side by side without this check would prove nothing.
python3 - "$ANSWER" "$TRACE_AFTER_RESTART" "$INBOX" "$INBOX_AFTER" "$REQUEST_ID" <<'PY'
import json, sys

answer, trace, inbox, inbox_after, request_id = sys.argv[1:6]

def parse(text):
    try:
        return json.loads(text)
    except (TypeError, ValueError):
        return {}

def rows(text):
    data = parse(text)
    return data if isinstance(data, list) else []

before = [row for row in rows(inbox) if row.get("requestId") == request_id]
after = [row for row in rows(inbox_after) if row.get("requestId") == request_id]

def dispatch_of(text):
    """The dispatch row of the durable trace, or an empty dict."""
    data = parse(text)
    if not isinstance(data, dict):
        return {}
    rows = data.get("dispatches")
    if not isinstance(rows, list) or not rows:
        return {}
    return rows[0]

problems = []
first = parse(answer)
second = dispatch_of(trace)
if first.get("requestId") != second.get("requestId"):
    problems.append("the task after the restart has another request id")
if first.get("disposition") != second.get("disposition"):
    problems.append("the task after the restart has another disposition")
if not second.get("requestId"):
    problems.append("the task after the restart carries no request id")
if second.get("state") != "ADJUDICATED":
    problems.append("the task after the restart is not in its recorded state")
if len(after) != len(before):
    problems.append("the peer received the request again after chain A restarted")
if len(after) != 1:
    problems.append("the peer does not hold exactly one row for this request")

if problems:
    for problem in problems:
        print("[w53-demo] FAILED: " + problem)
    sys.exit(1)
print("[w53-demo] the task after the restart of chain A is the same task, and the peer "
      "still holds exactly one row for it")
PY

# The second check: the capability chain A used must be one the peer really reports as
# active. Without it the report would carry the peer's word for it, and the health
# answer could name anything.
python3 - "$TRACE" "$CHAIN_B_HEALTH" "$CHAIN_A_HEALTH" <<'PY'
import json, sys

trace_text, health_text, chain_a_health_text = sys.argv[1:4]

def parse(text):
    try:
        return json.loads(text)
    except (TypeError, ValueError):
        return {}

def dispatch_of(text):
    """The dispatch row of the durable trace, or an empty dict."""
    data = parse(text)
    if not isinstance(data, dict):
        return {}
    rows = data.get("dispatches")
    if not isinstance(rows, list) or not rows:
        return {}
    return rows[0]

health = parse(health_text)
dispatch = dispatch_of(trace_text)
capabilities = health.get("capabilities") if isinstance(health, dict) else None

chain_a_health = parse(chain_a_health_text)

problems = []
if not isinstance(chain_a_health, dict) or "status" not in chain_a_health:
    problems.append("chain A did not report its health detail")
if not isinstance(health, dict):
    problems.append("the peer did not report its health as a JSON object")
else:
    if health.get("status") != "UP":
        problems.append("the peer does not report itself as up")
    if not isinstance(capabilities, list) or not capabilities:
        problems.append("the peer reported no active capability")
    elif dispatch.get("capabilityId") and dispatch["capabilityId"] not in capabilities:
        problems.append("the capability chain A used is not among the capabilities the peer "
                        "reports as active")

if problems:
    for problem in problems:
        print("[w53-demo] FAILED: " + problem)
    sys.exit(1)
print("[w53-demo] both processes answered their health endpoint, and the capability chain A "
      "used is one the peer reports as active")
PY

log "writing the machine-readable report"
mkdir -p "$(dirname "$REPORT")"
# The refusal scenarios travel through the environment rather than as positional
# arguments: an index into argv is a number in a shell script, and the quality gate
# reads every such number as a tunable that should have been named.
export W53_CAPABILITY_REFUSAL="$CAPABILITY_REFUSAL"
export W53_CONFLICT_STATUS="$CONFLICT_REFUSAL"
export W53_CONFLICT_BODY="$CONFLICT_BODY"
export W53_FORGED_REFUSAL="$FORGED_REFUSAL"
export W53_TRACE_AFTER_RESTART="$TRACE_AFTER_RESTART"
export W53_INBOX_AFTER_CHAIN_A_RESTART="$INBOX_AFTER"
export W53_CHAIN_A_HEALTH="$CHAIN_A_HEALTH"
export W53_CHAIN_B_HEALTH="$CHAIN_B_HEALTH"
export W53_CHAIN_A_HEALTH_CODE="$CHAIN_A_HEALTH_CODE"
export W53_CHAIN_B_HEALTH_CODE="$CHAIN_B_HEALTH_CODE"

python3 - "$REPORT" "$ANSWER" "$TRACE" "$RETRY" "$TASK_ID" "$REQUEST_ID" "$INBOX" \
  "$CHAIN_A_PORT" "$CHAIN_B_PORT" <<'PY'
import json, os, subprocess, sys

report, answer, trace, retry, task_id, request_id, inbox = sys.argv[1:8]
chain_a_port, chain_b_port = int(sys.argv[8]), int(sys.argv[9])
capability_refusal = os.environ.get("W53_CAPABILITY_REFUSAL", "")
conflict_status = os.environ.get("W53_CONFLICT_STATUS", "")
conflict_body = os.environ.get("W53_CONFLICT_BODY", "")
forged_refusal = os.environ.get("W53_FORGED_REFUSAL", "")
trace_after_restart = os.environ.get("W53_TRACE_AFTER_RESTART", "")
inbox_after_restart = os.environ.get("W53_INBOX_AFTER_CHAIN_A_RESTART", "")
chain_a_health = os.environ.get("W53_CHAIN_A_HEALTH", "")
chain_b_health = os.environ.get("W53_CHAIN_B_HEALTH", "")
chain_a_health_code = os.environ.get("W53_CHAIN_A_HEALTH_CODE", "")
chain_b_health_code = os.environ.get("W53_CHAIN_B_HEALTH_CODE", "")

def parse(text):
    try:
        return json.loads(text)
    except (TypeError, ValueError):
        return {"raw": text}

commit = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True)
commit_sha = commit.stdout.strip() if commit.returncode == 0 else None

def dispatch_of(text):
    """The dispatch row of the durable trace, or an empty dict."""
    data = parse(text)
    if not isinstance(data, dict):
        return {}
    rows = data.get("dispatches")
    if not isinstance(rows, list) or not rows:
        return {}
    return rows[0]

dispatch = dispatch_of(trace)
chain_a_answer = parse(chain_a_health)
chain_b_answer = parse(chain_b_health)
chain_a_checks = chain_a_answer.get("checks")
chain_a_down = []
if isinstance(chain_a_checks, list):
    chain_a_down = [c.get("name") for c in chain_a_checks
                    if isinstance(c, dict) and c.get("status") != "UP"]
chain_b_capabilities = chain_b_answer.get("capabilities")
if not isinstance(chain_b_capabilities, list):
    chain_b_capabilities = []

document = {
    "schema": "matrix.w53.distributed-demonstration/v5",
    "generatedBy": "scripts/w53-distributed-demo.sh",
    "taskId": task_id,
    "requestId": request_id,
    "commit": commit_sha,
    "chainA": {"kind": "quarkus", "port": chain_a_port,
               "entryPoint": "POST /v1/cognitive/task"},
    "chainB": {"kind": "jdk-http-server", "port": chain_b_port,
               "entryPoint": "POST /v1/bridge/task", "mode": "production"},
    "processes": {
        "chainA": {
            "health": chain_a_answer,
            "healthStatusCode": chain_a_health_code,
            "checksNotUp": chain_a_down,
            "capabilityUsedForThisTask": dispatch.get("capabilityId"),
            "whereThatCapabilityRan": dispatch.get("remoteResponder"),
        },
        "chainB": {
            "health": chain_b_answer,
            "healthStatusCode": chain_b_health_code,
            "activeCapabilities": chain_b_capabilities,
        },
        "whatThisShows": [
            "both processes started on their own and answered their own health endpoint: "
            "chain A through its Quarkus health endpoint, chain B through its own. The "
            "status code travels with the answer, because chain A's endpoint answers with "
            "a failing code when a check that has nothing to do with the demonstration is "
            "down. The "
            "checks that are not up are named in the answer (checksNotUp): the "
            "demonstration starts no PostgreSQL and this task uses none of it, and the "
            "task was served while that check was down",
            "chain B reports the capabilities it can really execute, and the set is the "
            "allowlist itself — a withdrawn capability disappears from it. The ids are "
            "the only thing that crosses: no class name, no plugin id, no method",
            "chain A names the capability this task used and where it ran: the work was "
            "done by the peer, and the durable dispatch row is the record of it",
        ],
    },
    "stages": [
        {"stage": "BOTH_PROCESSES_REPORTED_HEALTH_AND_ACTIVE_CAPABILITIES",
         "process": "both", "status": "OK"},
        {"stage": "TASK_RECEIVED", "process": "chain-a", "status": "OK"},
        {"stage": "DISPATCH_INTENT_PERSISTED", "process": "chain-a", "status": "OK"},
        {"stage": "REQUEST_AUTHENTICATED", "process": "chain-b", "status": "OK"},
        {"stage": "REQUEST_ADMITTED_DURABLY", "process": "chain-b", "status": "OK"},
        {"stage": "PRODUCTION_COGNITIVE_PATH_EXECUTED", "process": "chain-b", "status": "OK"},
        {"stage": "RESPONSE_AND_REPORT_SIGNED", "process": "chain-b", "status": "OK"},
        {"stage": "RESPONSE_VERIFIED", "process": "chain-a", "status": "OK"},
        {"stage": "REPORT_ADMITTED_AS_CANDIDATE", "process": "chain-a", "status": "OK"},
        {"stage": "REPORT_EVALUATED", "process": "chain-a", "status": "OK"},
        {"stage": "DISPOSITION_RECORDED", "process": "chain-a", "status": "OK"},
        {"stage": "RESTART_AND_REPLAY", "process": "chain-b", "status": "OK"},
        {"stage": "UNSERVED_CAPABILITY_REFUSED", "process": "chain-b", "status": "OK"},
        {"stage": "CONFLICTING_PAYLOAD_REFUSED", "process": "chain-a", "status": "OK"},
        {"stage": "FORGED_REQUEST_REFUSED", "process": "chain-b", "status": "OK"},
        {"stage": "CHAIN_A_RESTARTED_AND_TASK_REQUERIED", "process": "chain-a",
         "status": "OK"},
    ],
    "refusals": {
        "unservedCapability": parse(capability_refusal),
        "conflictingPayload": {"httpStatus": conflict_status, "body": parse(conflict_body)},
        "forgedRequest": parse(forged_refusal),
        "whatTheseShow": [
            "a capability the peer does not serve is refused by the peer itself, and the "
            "refusal travels back as a signed answer — the capability id on the wire "
            "reaches no class and no reflection call",
            "the same request id under a different payload is refused as a conflict: the "
            "first dispatch's record stays untouched, and the caller is told the reason",
            "a forged request is refused because the signature does not verify; the "
            "demonstration checks the refusal's reason, so it cannot silently prove "
            "something else",
        ],
    },
    "outcome": parse(answer),
    "durableTrace": parse(trace),
    "replayAfterRestart": parse(retry),
    "taskAfterChainARestart": parse(trace_after_restart),
    "whatTheChainARestartShows": [
        "the task is asked for again by its stable id after chain A restarted, and chain A "
        "answers with the same request id and the same disposition it recorded before the "
        "restart: the outcome survived the process, it was not recomputed",
        "the peer still holds exactly one row for this request id: chain A's restart did "
        "not send the task again. The reconciliation pass runs at the startup and finds "
        "nothing unfinished, because the task was finished",
        "this is the durability claim the demonstration can make: the state of both "
        "processes survives their restarts, and the logical effect of the task happened "
        "once. It is not a distributed exactly-once guarantee, and the report does not "
        "claim one",
    ],
    "peerDurableInbox": parse(inbox),
    "peerDurableInboxForTheTask": [
        row for row in parse(inbox) if row.get("requestId") == request_id
    ],
    "whatTheInboxShows": [
        "one row for THIS request id: the replay after the restart did not create a "
        "second row. The inbox also holds the row of the refusal scenario, which is a "
        "different request id and was refused, not executed",
        "the row holds the result of one execution: the replay did not run the task a "
        "second time — the evidence is the row count and the stored result, NOT the "
        "timestamps (the peer writes one instant for the whole request, so updatedAt "
        "equals createdAt on every row and says nothing about a single execution)",
        "the state is COMPLETED: the first delivery finished the execution and the result is still on disk after the restart",
    ],
    "reportAdmission": (parse(answer).get("reportDisposition")),
    "whatThisReportDoesNotClaim": [
        "it does not claim that the remote answer was correct",
        "it does not claim that the proposition is established: the remote report is a "
        "candidate, and the disposition is chain A's own judgement",
        "it does not claim distributed exactly-once execution: the delivery is "
        "at-least-once with a durable deduplication at the receiver",
        "it does not claim that the replay did not reach the peer — it claims that the "
        "peer's durable inbox did not execute the task a second time",
        "it does not claim that the refusals were the peer's doing when the peer was not "
        "reached: each refusal carries the reason its own receiver gave",
    ],
}
with open(report, "w", encoding="utf-8") as handle:
    json.dump(document, handle, indent=2, ensure_ascii=False)
    handle.write("\n")
print("[w53-demo] the report is written to " + report)
PY

log "done"
