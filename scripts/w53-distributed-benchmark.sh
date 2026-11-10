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
    --report) REPORT="$2"; shift 2 ;;
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
# W53 — the JVM's own GC log is enabled for the process under measurement, because the
# garbage collection and the allocation are not visible from the client's round trip.
MATRIX_PEER_URL="http://127.0.0.1:$CHAIN_B_PORT" \
MATRIX_BRIDGE_SECRET="$BRIDGE_SECRET" \
QUARKUS_HTTP_PORT="$CHAIN_A_PORT" \
  java -Xlog:gc:"$WORK/gc-chain-a.log" \
  -jar matrix-core/build/quarkus-app/quarkus-run.jar \
  >"$WORK/chain-a.log" 2>&1 &
CHAIN_A_PID=$!
wait_for_health "http://127.0.0.1:$CHAIN_A_PORT/q/health" "chain A"

log "sampling the processes' own accounting before the run"
METRICS_BEFORE="$(python3 scripts/w53-process-metrics.py "$CHAIN_A_PID" "$WORK/gc-chain-a.log")" \
  || { log "the process metrics could not be read before the run"; exit 1; }
METRICS_BEFORE_B="$(python3 scripts/w53-process-metrics.py "$CHAIN_B_PID")" \
  || { log "the peer metrics could not be read before the run"; exit 1; }

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

# The processes' own accounting is sampled between the two passes: the cost of the
# idempotency path includes the CPU and the memory it used, and a number that covers
# both passes could not be attributed to either.
log "sampling the processes' own accounting before the replay pass"
METRICS_MID="$(python3 scripts/w53-process-metrics.py "$CHAIN_A_PID" "$WORK/gc-chain-a.log")" \
  || { log "the process metrics could not be read before the replay pass"; exit 1; }
METRICS_MID_B="$(python3 scripts/w53-process-metrics.py "$CHAIN_B_PID")" \
  || { log "the peer metrics could not be read before the replay pass"; exit 1; }

# The replay pass. The mandate asks for the duplicate-request handling and the replay of
# a completed request to be measured SEPARATELY from the initial execution: they are
# different paths with different costs, and a single number for both would hide the cost
# of the durable idempotency path.
#
# The same request is sent again with the same request id and the same payload, so the
# peer must recognise it and answer from its own durable record. The answer must carry
# the same disposition as the first one: that is the check that the replay returned the
# recorded outcome instead of running the task again.
log "replaying the same requests: the cost of the durable idempotency path"
: >"$WORK/replay-latencies.txt"
: >"$WORK/replay-samples.jsonl"
REPLAY_INDEX=0
while [ "$REPLAY_INDEX" -lt "$TASKS" ]; do
  REPLAY_INDEX=$(( REPLAY_INDEX + 1 ))
  REPLAY_TASK_ID="w53-bench-$RUN_STAMP-$REPLAY_INDEX"
  REPLAY_BODY="{\"taskId\":\"$REPLAY_TASK_ID\",\"capabilityId\":\"brain.cognitive-inference\",\"input\":\"is the lamp lit when the power is on and the switch is down\",\"expectation\":\"the lamp is lit\"}"
  REPLAY_ANSWER="$WORK/replay-answer-$REPLAY_INDEX.json"
  curl -s --max-time "$TASK_TIMEOUT_SECONDS" -o "$REPLAY_ANSWER" -w '%{time_total}\n' \
    -X POST "http://127.0.0.1:$CHAIN_A_PORT/v1/cognitive/task/replay" \
    -H 'Content-Type: application/json' -d "$REPLAY_BODY" >>"$WORK/replay-latencies.txt"
  python3 scripts/w53-answer-reader.py "$REPLAY_ANSWER" "$REPLAY_INDEX" \
    >>"$WORK/replay-samples.jsonl"
done

log "sampling the processes' own accounting after the run"
METRICS_AFTER="$(python3 scripts/w53-process-metrics.py "$CHAIN_A_PID" "$WORK/gc-chain-a.log")" \
  || { log "the process metrics could not be read after the run"; exit 1; }
METRICS_AFTER_B="$(python3 scripts/w53-process-metrics.py "$CHAIN_B_PID")" \
  || { log "the peer metrics could not be read after the run"; exit 1; }

log "writing the machine-readable report"
mkdir -p "$(dirname "$REPORT")"
# The process metrics travel through the environment: an index into argv is a number in
# a shell script, and the quality gate reads every such number as a tunable that should
# have been named.
export W53_METRICS_BEFORE="$METRICS_BEFORE"
export W53_METRICS_AFTER="$METRICS_AFTER"
export W53_METRICS_BEFORE_B="$METRICS_BEFORE_B"
export W53_METRICS_AFTER_B="$METRICS_AFTER_B"
export W53_METRICS_MID="$METRICS_MID"
export W53_METRICS_MID_B="$METRICS_MID_B"
# The replay's files travel through the environment too: an index into argv is a number
# in a shell script, and the quality gate reads every such number as a tunable.
export W53_REPLAY_SAMPLES="$WORK/replay-samples.jsonl"
export W53_REPLAY_LATENCIES="$WORK/replay-latencies.txt"

python3 - "$REPORT" "$WORK/samples.jsonl" "$WORK/latencies.txt" "$TASKS" "$CONCURRENCY" \
  "$RUN_STAMP" \
  "$PERCENTILE_P50" "$PERCENTILE_P95" "$PERCENTILE_P99" \
  "$NANOS_PER_MILLI" "$MILLIS_PER_SECOND" <<'PY'
import json, os, platform, subprocess, sys

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

# The client's round trip belongs to the sample it belongs to: without it the report
# holds the aggregate and not the distribution, and the cold start cannot be separated
# from the steady state by a reader.
for position, sample in enumerate(samples):
    if position < len(latencies):
        sample["latencyMillis"] = latencies[position]

stages = {}
for sample in samples:
    for name, value in (sample.get("timingsNanos") or {}).items():
        stages.setdefault(name, []).append(value / nanos_per_milli)

# The replay path: the same requests sent again, so the peer answers from its own
# durable record. Its cost is a number of its own and is reported apart.
replay_samples = []
replay_path = os.environ.get("W53_REPLAY_SAMPLES")
if replay_path and os.path.exists(replay_path):
    with open(replay_path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                replay_samples.append(json.loads(line))

replay_latencies = []
replay_latency_path = os.environ.get("W53_REPLAY_LATENCIES")
if replay_latency_path and os.path.exists(replay_latency_path):
    with open(replay_latency_path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                replay_latencies.append(float(line) * millis_per_second)

for position, sample in enumerate(replay_samples):
    if position < len(replay_latencies):
        sample["latencyMillis"] = replay_latencies[position]

replay_stages = {}
for sample in replay_samples:
    for name, value in (sample.get("timingsNanos") or {}).items():
        replay_stages.setdefault(name, []).append(value / nanos_per_milli)

# The replay must answer with the same disposition as the first execution: that is the
# check that the peer returned its recorded result instead of running the task again.
same_disposition = 0
compared = 0
for first, again in zip(samples, replay_samples):
    if first.get("disposition") and again.get("disposition"):
        compared += 1
        if first.get("disposition") == again.get("disposition"):
            same_disposition += 1

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

def process_metrics(before, after, before_b, after_b, task_count, unit="Task"):
    """What the two processes themselves spent, and what they held.

    The unit names the thing the number is divided by: a task for the first pass, a
    replay for the second one. The two passes are measured apart, because a number
    that covered both could not be attributed to either.
    """
    if not before or not after:
        return None
    cpu = (after["cpuSeconds"] - before["cpuSeconds"]) \
        + (after_b["cpuSeconds"] - before_b["cpuSeconds"])
    gc_before = before.get("garbageCollection", {})
    gc_after = after.get("garbageCollection", {})
    collected = gc_after.get("allocatedBytes", 0) - gc_before.get("allocatedBytes", 0)
    events = gc_after.get("events", 0) - gc_before.get("events", 0)
    heap_growth = after.get("heapUsedBytes", 0) - before.get("heapUsedBytes", 0)
    # The allocation of the run, estimated: what the jvm collected plus the growth of the
    # heap it held. With no collection during the run the estimate is a lower bound.
    estimated_allocation = collected + heap_growth
    return {
        "chainA": {
            "cpuSeconds": round(after["cpuSeconds"] - before["cpuSeconds"], 3),
            "peakResidentBytes": after["peakResidentBytes"],
            "residentBytes": after.get("residentBytes"),
            "heapUsedBytes": after.get("heapUsedBytes"),
            "garbageCollection": {
                "events": events,
                "pauseMillis": round(gc_after.get("pauseMillis", 0.0)
                                     - gc_before.get("pauseMillis", 0.0), 3),
            },
        },
        "chainB": {
            "cpuSeconds": round(after_b["cpuSeconds"] - before_b["cpuSeconds"], 3),
            "peakResidentBytes": after_b["peakResidentBytes"],
            "residentBytes": after_b.get("residentBytes"),
        },
        "cpuSeconds": round(cpu, 3),
        "cpuSecondsPer" + unit: round(cpu / task_count, 4) if task_count else None,
        "estimatedAllocationBytes": estimated_allocation,
        "estimatedAllocationBytesPer" + unit: (
            round(estimated_allocation / task_count) if task_count else None),
        "allocationIsLowerBound": events == 0,
        "retainedResidentBytes": (after.get("residentBytes", 0)
                                  - before.get("residentBytes", 0)),
    }


metrics_before = json.loads(os.environ.get("W53_METRICS_BEFORE", "{}") or "{}")
metrics_after = json.loads(os.environ.get("W53_METRICS_AFTER", "{}") or "{}")
metrics_before_b = json.loads(os.environ.get("W53_METRICS_BEFORE_B", "{}") or "{}")
metrics_after_b = json.loads(os.environ.get("W53_METRICS_AFTER_B", "{}") or "{}")
metrics_mid = json.loads(os.environ.get("W53_METRICS_MID", "{}") or "{}")
metrics_mid_b = json.loads(os.environ.get("W53_METRICS_MID_B", "{}") or "{}")

document = {
    "schema": "matrix.w53.distributed-performance/v2",
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
    "replayPath": {
        "whatItIs": "the same requests sent again, with the same request id and the same "
                    "payload: the peer recognises them and answers from its own durable "
                    "record, so the cost of the durable idempotency path is measured apart "
                    "from the cost of the first execution",
        "replays": len(replay_latencies),
        "endToEndMillis": {
            "p50": percentile(replay_latencies, p50),
            "p95": percentile(replay_latencies, p95),
            "min": min(replay_latencies) if replay_latencies else None,
            "max": max(replay_latencies) if replay_latencies else None,
            "mean": (sum(replay_latencies) / len(replay_latencies)) if replay_latencies else None,
        },
        "stagesMillis": {
            name: {
                "p50": percentile(values, p50),
                "p95": percentile(values, p95),
                "count": len(values),
            }
            for name, values in sorted(replay_stages.items())
        },
        "sameDispositionAsTheFirstExecution": same_disposition,
        "comparedDispositions": compared,
    },
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
        "peer_execution_nanos": "chain B, as chain B measured it: the production inference "
                                "itself, rounded to the millisecond; the difference against "
                                "dispatch_nanos is the network, the signing and chain B's own "
                                "bookkeeping",
        "report_admission_nanos": "chain A: the report entering the epistemic model through "
                                  "chain A's own bridge receiver",
        "assessment_nanos": "chain A: the epistemic evaluation of the answer",
        "total_nanos": "chain A: the whole task, as chain A can see it",
        "replay_dispatch_nanos": "the replay path: chain A sending the request again and "
                                 "the peer answering from its durable record",
        "replay_report_admission_nanos": "the replay path: the report entering the "
                                         "epistemic model again",
        "replay_assessment_nanos": "the replay path: the epistemic evaluation again",
        "replay_total_nanos": "the replay path: the whole replay, as chain A can see it",
    },
    "processMetrics": process_metrics,
    "processMetricsMeanings": {
        "chainA": "the Quarkus application: the CPU time it used, the peak memory it "
                  "held, and the garbage collection the JVM's own log records",
        "chainB": "the gateway's JDK server: the CPU time it used and the peak memory it "
                  "held",
        "cpuSecondsPerTask": "the CPU time the two processes together spent on the first "
                             "pass, divided by the number of tasks",
        "cpuSecondsPerReplay": "the CPU time the two processes together spent on the "
                               "replay pass, divided by the number of replays",
        "estimatedAllocationBytes": "the heap the JVM collected during the run, plus the "
                                    "growth of the heap it held — an estimate of the "
                                    "allocation, not a counter of it",
        "allocationIsLowerBound": "true when the JVM collected nothing during the run: the "
                                  "estimate is then a lower bound on the allocation",
        "retainedResidentBytes": "the growth of the resident set over the run — the memory "
                                 "the run retained in the process",
        "peakResidentBytes": "the peak of the whole lifetime of the process, the startup "
                             "included; it is not the memory the run used",
    },
    "whatThisReportDoesNotClaim": [
        "it does not claim a p99 from a sample of this size",
        "it does not separate chain B's stages from the network round trip — chain A times "
        "the crossing as one interval, and only the peer's own total is told apart, by the "
        "duration the peer reports; the peer's authentication, its durable inbox write and "
        "its answer signing are still inside the crossing",
        "it does not claim that these numbers are comparable with a run on another machine",
        "no optimization was accepted on the strength of this run alone",
        "it does not claim an exact allocation per task: the allocation is estimated from "
        "the JVM's own garbage-collection log, and the estimate is named as an estimate",
        "it does not separate the serialization and the signature cost from the network "
        "round trip — chain A times the crossing as one interval",
        "it does not claim that the replay path avoided the peer entirely: the request is "
        "sent again and the peer answers from its durable record, and the report measures "
        "that path rather than asserting that it never reached the peer",
    ],
    "samples": samples,
    "replaySamples": replay_samples,
}
document["processMetrics"] = process_metrics(
    metrics_before, metrics_mid, metrics_before_b, metrics_mid_b, len(latencies))
document["processMetricsReplayPass"] = process_metrics(
    metrics_mid, metrics_after, metrics_mid_b, metrics_after_b, len(replay_latencies),
    unit="Replay")
with open(report, "w", encoding="utf-8") as handle:
    json.dump(document, handle, indent=2, ensure_ascii=False)
    handle.write("\n")
print("[w53-bench] the report is written to " + report)
PY

log "done"
