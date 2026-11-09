#!/usr/bin/env bash
# W53 — the performance of the distributed cognitive path.
#
# WHAT THIS MEASURES
# The full task, not the message parser: chain A's ingress, the durable intent, the
# signed dispatch and the network round trip, the peer's authentication and durable
# inbox write, the production inference, the signed answer, chain A's verification,
# the admission of the report and the epistemic evaluation. The stages chain A can
# time itself are timed with a monotonic clock and travel back in the answer.
#
# WHAT THIS DOES NOT CLAIM
#   * it does not compare numbers from different machines — the hardware, the JDK and
#     the JVM arguments are written into the report;
#   * it does not optimize anything — an optimization is accepted only when a profile
#     shows a bottleneck and the before/after numbers exceed the noise on THIS machine;
#   * it does not treat the p99 of a small sample as a p99.
#
# Usage: scripts/w53-distributed-benchmark.sh [--tasks N] [--skip-build]
# The machine-readable report is written to docs-v2/engineering/W53-PERFORMANCE.json

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# The number of tasks the run measures. It is a named value because a benchmark
# without a stated sample size is an anecdote.
TASKS=30

# How many tasks are in flight at once. The mandate asks for the single-task path
# AND a concurrent path, measured separately.
CONCURRENCY=1

# The ports the two processes serve on.
CHAIN_A_PORT="${CHAIN_A_PORT:-9192}"
CHAIN_B_PORT="${CHAIN_B_PORT:-8767}"

# The report the benchmark writes.
REPORT="docs-v2/engineering/W53-PERFORMANCE.json"

# The timeout of one task's client call, in seconds.
readonly TASK_TIMEOUT_SECONDS=60

# The stamp that makes this run's request ids unique. Chain A's cognitive database is
# persistent by design, and a request id is the identity of a dispatch: a second run that
# reuses the ids of the first is not a measurement of the path but of the durable
# idempotency, which correctly refuses a request id it has already seen.
readonly RUN_STAMP="$(date +%s)-$$"

# The percentiles the report states.
readonly PERCENTILE_P50=0.50
readonly PERCENTILE_P95=0.95
readonly PERCENTILE_P99=0.99

# The conversions between the units the report uses.
readonly NANOS_PER_MILLI=1000000
readonly MILLIS_PER_SECOND=1000

while [ $# -gt 0 ]; do
  case "$1" in
    --tasks) TASKS="$2"; shift 2 ;;
    --concurrency) CONCURRENCY="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

# The directories the two processes keep their state in. The benchmark uses its own,
# so that it never touches a developer's working state.
WORK="$(mktemp -d)"
export MATRIX_MIND_DIR="$WORK/chain-b-mind"
export MATRIX_COGNITIVE_DIR="$WORK/chain-a-cognitive"
export MATRIX_BRIDGE_JOURNAL_DIR="$WORK/chain-a-bridge"
mkdir -p "$MATRIX_MIND_DIR" "$MATRIX_COGNITIVE_DIR" "$MATRIX_BRIDGE_JOURNAL_DIR"

# The shared secret of the two processes. It is generated here, given to both
# through the environment, and never printed.
BRIDGE_SECRET="$(head -c 32 /dev/urandom | base64 | tr -d '\n')"
export MATRIX_BRIDGE_SECRET="$BRIDGE_SECRET"

log() { printf '[w53-bench] %s\n' "$*"; }

cleanup() {
  # A run that did not finish keeps its directory: the logs are the evidence of what
  # happened, and a benchmark that throws its evidence away teaches nothing.
  if [ "${BENCH_COMPLETED:-0}" != "1" ]; then
    printf '[w53-bench] the run did not finish; its directory is kept: %s\n' "$WORK"
  fi
  if [ -n "${CHAIN_B_PID:-}" ] && kill -0 "$CHAIN_B_PID" 2>/dev/null; then
    kill "$CHAIN_B_PID" 2>/dev/null || true
    wait "$CHAIN_B_PID" 2>/dev/null || true
  fi
  if [ -n "${CHAIN_A_PID:-}" ] && kill -0 "$CHAIN_A_PID" 2>/dev/null; then
    kill "$CHAIN_A_PID" 2>/dev/null || true
    wait "$CHAIN_A_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

wait_for_health() {
  local url="$1" name="$2" attempts=${3:-300}
  while [ "$attempts" -gt 0 ]; do
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

if [ -z "${SKIP_BUILD:-}" ]; then
  log "building the two processes"
  JAVA_HOME="${JAVA_HOME:-$HOME/jdk25}" ./gradlew --offline -q \
    :matrix-api-gateway:installDist :matrix-core:quarkusBuild 2>&1 | tail -3
fi

log "starting chain B — the gateway's own JDK server, in production mode"
MATRIX_MODE=production \
MATRIX_BRIDGE_SECRET="$BRIDGE_SECRET" \
  matrix-api-gateway/build/install/matrix-api-gateway/bin/matrix-api-gateway \
  "$CHAIN_B_PORT" >"$WORK/chain-b.log" 2>&1 &
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

log "measuring $TASKS tasks through the full path, $CONCURRENCY at a time"
: >"$WORK/samples.jsonl"
: >"$WORK/latencies.txt"

measure_one() {
  local index="$1"
  local task_id="w53-bench-$RUN_STAMP-$index"
  local body="{\"taskId\":\"$task_id\",\"capabilityId\":\"brain.cognitive-inference\",\"input\":\"is the lamp lit when the power is on and the switch is down\",\"expectation\":\"the lamp is lit\"}"
  local answer="$WORK/answer-$index.json"
  curl -s --max-time "$TASK_TIMEOUT_SECONDS" -o "$answer" -w '%{time_total}\n' \
    -X POST "http://127.0.0.1:$CHAIN_A_PORT/v1/cognitive/task" \
    -H 'Content-Type: application/json' -d "$body" >>"$WORK/latencies.txt"
  python3 scripts/w53-answer-reader.py "$answer" "$index" >>"$WORK/samples.jsonl"
}

INDEX=0
while [ "$INDEX" -lt "$TASKS" ]; do
  INDEX=$(( INDEX + 1 ))
  if [ "$CONCURRENCY" -le 1 ]; then
    measure_one "$INDEX"
  else
    # The concurrent path: a bounded number of tasks in flight at once.
    #
    # W53 — the wait is for THESE jobs and for no others. A bare `wait` waits for every
    # background job of the shell, and the two servers are background jobs of this very
    # shell: a bare `wait` therefore waits for chain A and chain B to exit, which they
    # are not going to do until the benchmark kills them. The run would stop after the
    # first batch and hang there, and the hang would look like a defect of the
    # distributed path — it is a defect of the measurement.
    WAITING=0
    JOB_PIDS=""
    while [ "$WAITING" -lt "$CONCURRENCY" ] && [ "$(( INDEX + WAITING ))" -le "$TASKS" ]; do
      measure_one "$(( INDEX + WAITING ))" &
      JOB_PIDS="$JOB_PIDS $!"
      WAITING=$(( WAITING + 1 ))
    done
    for JOB_PID in $JOB_PIDS; do
      wait "$JOB_PID" || true
    done
    INDEX=$(( INDEX + WAITING - 1 ))
  fi
done

BENCH_COMPLETED=1
log "writing the machine-readable report"
mkdir -p "$(dirname "$REPORT")"
python3 - "$REPORT" "$WORK/samples.jsonl" "$WORK/latencies.txt" "$TASKS" "$CONCURRENCY" \
  "$RUN_STAMP" \
  "$PERCENTILE_P50" "$PERCENTILE_P95" "$PERCENTILE_P99" \
  "$NANOS_PER_MILLI" "$MILLIS_PER_SECOND" <<'PY'
import json, platform, subprocess, sys

report, samples_path, latencies_path, tasks = sys.argv[1:5]
concurrency = int(sys.argv[5])
run_stamp, p50, p95, p99, nanos_per_milli, millis_per_second = sys.argv[6:]
p50, p95, p99 = (float(value) for value in (p50, p95, p99))
nanos_per_milli = float(nanos_per_milli)
millis_per_second = float(millis_per_second)


def percentile(values, fraction):
    """The nearest-rank percentile of a sorted list."""
    if not values:
        return None
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round(fraction * (len(ordered) - 1))))
    return ordered[index]


samples = []
with open(samples_path, encoding="utf-8") as handle:
    for line in handle:
        line = line.strip()
        if line:
            samples.append(json.loads(line))

latencies = []
with open(latencies_path, encoding="utf-8") as handle:
    for line in handle:
        line = line.strip()
        if line:
            latencies.append(float(line) * millis_per_second)

stages = {}
for sample in samples:
    for name, value in (sample.get("timingsNanos") or {}).items():
        stages.setdefault(name, []).append(value / nanos_per_milli)

commit = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True)
java = subprocess.run(["java", "-version"], capture_output=True, text=True)
os_release = ""
try:
    with open("/etc/os-release", encoding="utf-8") as handle:
        for line in handle:
            if line.startswith("PRETTY_NAME="):
                os_release = line.split("=", 1)[1].strip().strip('"')
except OSError:
    pass

document = {
    "schema": "matrix.w53.distributed-performance/v1",
    "generatedBy": "scripts/w53-distributed-benchmark.sh",
    "commit": commit.stdout.strip() if commit.returncode == 0 else None,
    "environment": {
        "os": os_release or platform.platform(),
        "jdk": (java.stderr or java.stdout).splitlines()[0] if java.returncode == 0 else None,
        "hardware": platform.processor() or platform.machine(),
        "jvmArguments": "the default of the Quarkus fast-jar and of the gateway's start script",
    },
    "workload": {
        "tasks": int(tasks),
        "concurrency": concurrency,
        "runStamp": run_stamp,
        "requestIds": "w53-bench-<runStamp>-<index> — unique per run, because chain A's "
                      "cognitive database is persistent and a request id is the identity "
                      "of a dispatch",
        "payloadSize": "one sentence of input and one expectation",
        "seed": "none — the workload has no randomness",
        "procedure": "the tasks go through POST /v1/cognitive/task on chain A, as many at "
                     "a time as the concurrency allows; the latency is the client's round "
                     "trip, the stage times are chain A's own monotonic measurements of the "
                     "parts it can time",
    },
    "endToEndMillis": {
        "p50": percentile(latencies, p50),
        "p95": percentile(latencies, p95),
        "p99": percentile(latencies, p99),
        "min": min(latencies) if latencies else None,
        "max": max(latencies) if latencies else None,
        "mean": (sum(latencies) / len(latencies)) if latencies else None,
    },
    "throughputTasksPerSecond": (len(latencies) / (sum(latencies) / millis_per_second)) if latencies else None,
    "stagesMillis": {
        name: {
            "p50": percentile(values, p50),
            "p95": percentile(values, p95),
            "count": len(values),
        }
        for name, values in sorted(stages.items())
    },
    "stageMeanings": {
        "intent_written_nanos": "chain A: the durable dispatch intent, written before the send",
        "dispatch_nanos": "chain A: signing the request, the network round trip, chain B's "
                          "authentication, its durable inbox write, the production inference, "
                          "the signed answer and its verification",
        "report_admission_nanos": "chain A: the report entering the epistemic model through "
                                  "chain A's own bridge receiver",
        "assessment_nanos": "chain A: the epistemic evaluation of the answer",
        "total_nanos": "chain A: the whole task, as chain A can see it",
    },
    "whatThisReportDoesNotClaim": [
        "it does not claim a p99 from a sample of this size",
        "it does not separate chain B's stages from the network round trip — chain A times "
        "the crossing as one interval, because the stages inside the peer are the peer's to "
        "measure",
        "it does not claim that these numbers are comparable with a run on another machine",
        "no optimization was accepted on the strength of this run alone",
    ],
    "samples": samples,
}
with open(report, "w", encoding="utf-8") as handle:
    json.dump(document, handle, indent=2, ensure_ascii=False)
    handle.write("\n")
print("[w53-bench] the report is written to " + report)
PY

log "done"
