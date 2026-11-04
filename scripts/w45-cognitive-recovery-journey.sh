#!/usr/bin/env bash
#
# W45.1 — the process-kill recovery journey of the cognitive request lifecycle.
#
# WHAT THIS HARNESS PROVES
# W45.0 showed a recovery by reopening the stores INSIDE one JVM: no shutdown
# hook ran, no connection was torn down by the operating system, and nothing
# that had not already been committed was lost. That is not a crash.
#
# This harness stages the crash that matters: a real SIGKILL of a separate
# process, and a NEW process that starts against the SAME database.
#
# THE MECHANISM (io.matrix.cognitive.RequestFailpoint, already written)
# The system property w45.failpoint names a checkpoint of the request
# lifecycle, the system property w45.failpoint.marker names a file. The
# process writes that file — the checkpoint name and its own pid — when it
# reaches the checkpoint, and then BLOCKS FOREVER. The harness watches for the
# marker and sends SIGKILL to the EXACT child JVM by its recorded pid. Nothing
# happens between the marker and the kill, so the death is a true external
# SIGKILL and not a graceful shutdown.
#
# THE CONTRACT UNDER TEST (the recovery policy of W45.1)
#   * a request whose owner process is GONE and whose last durable checkpoint
#     is CLAIMED (or none) may be executed again, IN THE SAME EPISODE;
#   * a request whose owner process is GONE and whose last durable checkpoint
#     is at or after EPISODE_OPENED may NOT be executed again: the caller is
#     told the outcome is indeterminate (HTTP 409, "state":"INDETERMINATE");
#   * a COMPLETED request replays its stored outcome (HTTP 200) and does no work.
#
# THE SCENARIOS
#   A. the kill lands AFTER the observation was committed and BEFORE the
#      idempotency completion (OBSERVATION_COMMITTED): the retry must NOT
#      duplicate the observation and must say the outcome is indeterminate;
#   B. the kill lands BEFORE anything durable was written (CLAIMED when it is
#      reachable, EPISODE_OPENED otherwise): the retry must be able to execute
#      the request again, in the SAME episode;
#   C. the kill lands AFTER the request completed (IDEMPOTENCY_COMPLETED): the
#      retry must return the original outcome, with no second episode, no
#      second prediction and no second case.
#
# HYGIENE
# Everything the harness touches is inside ONE private temporary directory
# that it creates itself: a disposable SQLite database, the logs, the marker
# files and the scratch directory of the JVM. The owner's own database is never
# opened. The pids are the pids the harness started: no pkill, no wildcards.
#
# Usage:
#   scripts/w45-cognitive-recovery-journey.sh
#
# Environment:
#   W45_SKIP_BUILD=1   do not build the application (the jar must exist)
#   W45_KEEP=1         keep the private temporary directory and print its path
#
# Exit status: 0 when every assertion holds, 1 when any does not.
set -u

# ── configuration ────────────────────────────────────────────────────────────

# The default java on this machine is a JRE and the build fails without this.
export JAVA_HOME="${JAVA_HOME:-/home/alexandr-narbaev/jdk25}"
export PATH="$JAVA_HOME/bin:$PATH"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

readonly JAR="$ROOT/matrix-core/build/quarkus-app/quarkus-run.jar"
readonly HELPER="$ROOT/scripts/w45-cognitive-recovery-db.py"
readonly GRADLE="./gradlew"
readonly GRADLE_TASK=":matrix-core:quarkusBuild"

# The assignment of the micro-world. It must name EVERY variable of the world,
# so both of them.
readonly ASSIGNMENT="power=1,switch=0"

# How long the harness waits for the service to answer, for the marker to
# appear, and for a single HTTP request.
readonly STARTUP_WAIT_SECONDS=120
readonly MARKER_WAIT_SECONDS=45
readonly HTTP_TIMEOUT_SECONDS=30
# The delivery that is expected to BLOCK at a failpoint must not time out
# before the marker appears: the timeout of that delivery is longer than the
# wait for the marker.
readonly BLOCKED_TIMEOUT_SECONDS=180
# The exit status a shell reports for a process killed by a signal: 128 + the signal
# number. SIGINT is 2, SIGTERM is 15, and SIGKILL is 9.
readonly EXIT_BY_SIGNAL=128
readonly SIGINT_EXIT=130
readonly SIGTERM_EXIT=143
readonly SIGKILL_EXIT=137
# How long to wait for a killed child to disappear from the process table, in tenths
# of a second.
readonly KILL_WAIT_TENTHS=50
# How many bytes of a response body the harness prints.
readonly BODY_PREVIEW_BYTES=400
# The HTTP statuses the contract names.
readonly HTTP_OK=200
readonly HTTP_CONFLICT=409

FAILURES=0
CHECKS=0
PRIV=""
APP_PID=""
APP_LOG=""
# The evidence of the kill, filled in by kill_app. It is a global and NOT the
# output of a command substitution: `wait` only reports the status of a child
# of THIS shell, and a command substitution runs in a subshell.
KILL_EVIDENCE=""
REQUEST_PID=""

# ── output ───────────────────────────────────────────────────────────────────

pass() { CHECKS=$((CHECKS + 1)); printf '  OK    %s\n' "$1"; }
fail() { CHECKS=$((CHECKS + 1)); FAILURES=$((FAILURES + 1)); printf '  FAIL  %s\n' "$1"; }
note() { printf '  ..    %s\n' "$1"; }
head1() { printf '\n===== %s =====\n' "$1"; }

# ── the private directory ────────────────────────────────────────────────────

# The ONE place the harness writes. Everything else it touches is a pid it
# started or a file below this directory.
make_private_dir() {
  if [ -z "$PRIV" ]; then
    PRIV="$(mktemp -d "${TMPDIR:-/tmp}/w45-cognitive-recovery-XXXXXX")" || return 1
  fi
  mktemp -d "$PRIV/$1-XXXXXX"
}

cleanup() {
  if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
    kill -9 "$APP_PID" 2>/dev/null
    wait "$APP_PID" 2>/dev/null
  fi
  APP_PID=""
  if [ -n "$PRIV" ] && [ -d "$PRIV" ]; then
    if [ "${W45_KEEP:-0}" = "1" ]; then
      printf '\nthe private directory is KEPT: %s\n' "$PRIV"
    else
      # Only the directory this script created, and only below its own root.
      rm -rf "$PRIV"
      printf '\nthe private directory is removed: %s\n' "$PRIV"
    fi
  fi
}
trap cleanup EXIT
trap "exit $SIGINT_EXIT" INT
trap "exit $SIGTERM_EXIT" TERM

# ── the store reader ─────────────────────────────────────────────────────────

db_snapshot() { python3 "$HELPER" "$1" "$2" 2>/dev/null; }
db_field() { python3 "$HELPER" "$1" "$2" --field "$3" 2>/dev/null; }

# The classification the CONTRACT gives to a durable checkpoint:
#   CLAIMED (or none) — the request wrote nothing and MAY be executed again;
#   anything at or after EPISODE_OPENED — the request wrote something and may
#   NOT be executed again: the caller is told the outcome is indeterminate.
classify_checkpoint() {
  case "$1" in
    ""|CLAIMED) echo "the contract says it wrote NOTHING, so the request MAY be executed again" ;;
    *) echo "the contract says it WROTE something, so the request may NOT be executed again" ;;
  esac
}

# ── the application ──────────────────────────────────────────────────────────

# A free port, asked of the operating system. 8080 and 8180 are never used:
# they belong to the services of the owner.
free_port() {
  python3 - <<'PY'
import socket
s = socket.socket()
s.bind(("127.0.0.1", 0))
print(s.getsockname()[1])
s.close()
PY
}

# Starts the application against a database of this harness, on a port of this
# harness, and waits until it answers its health endpoint.
#
# The WORKING DIRECTORY of the application is the private directory of the
# scenario: the application writes its logs, its chain state and its memory
# files relative to it, and the journey must not write into the data of the
# owner.
#
# $1 the work directory   $2 the port   $3 the database   $4 the failpoint
# $5 the marker file
start_app() {
  local work="$1" port="$2" db="$3" failpoint="$4" marker="$5"
  mkdir -p "$work/tmp"
  local -a args=(
    "-Djava.io.tmpdir=$work/tmp"
    "-Dmatrix.auto-train.enabled=false"
    "-Dmatrix.knowledge.path=$db"
  )
  if [ -n "$failpoint" ]; then
    args+=("-Dw45.failpoint=$failpoint" "-Dw45.failpoint.marker=$marker")
  fi
  APP_LOG="$work/app-$(date +%H%M%S).log"
  note "the application starts: cwd=$work port=$port database=$db failpoint=${failpoint:-none}"
  # `exec` REPLACES the subshell with the JVM, so $! is the pid of the JVM
  # itself and not the pid of a wrapper.
  (
    cd "$work" || exit 1
    exec env QUARKUS_HTTP_PORT="$port" java "${args[@]}" -jar "$JAR"
  ) >"$APP_LOG" 2>&1 </dev/null &
  APP_PID=$!
  local waited=0
  while [ "$waited" -lt "$STARTUP_WAIT_SECONDS" ]; do
    sleep 1
    waited=$((waited + 1))
    if ! kill -0 "$APP_PID" 2>/dev/null; then
      note "the application exited during start-up (see $APP_LOG)"
      APP_PID=""
      return 1
    fi
    # The LIVENESS endpoint is the one that says "this process serves HTTP".
    # The readiness endpoint of this application also carries the check of the
    # optional PostgreSQL datasource, and this journey does not use it: its
    # data is in SQLite. The answer of the liveness endpoint is the honest
    # signal that the application is up.
    if curl -sf -o /dev/null -m 2 "http://127.0.0.1:$port/q/health/live" 2>/dev/null; then
      note "the application answers on port $port (pid $APP_PID)"
      return 0
    fi
  done
  note "the application did not answer within ${STARTUP_WAIT_SECONDS}s (see $APP_LOG)"
  return 1
}

# Sends SIGKILL to the EXACT pid this harness started, and waits until the
# process is really gone. The exit status of a process killed with SIGKILL is
# 137: 128 + 9. That number is the evidence that the death was a signal and
# not a shutdown.
#
# The evidence is left in KILL_EVIDENCE, because `wait` reports the status of
# a child of THIS shell only: a command substitution would run in a subshell
# and would see no child at all.
kill_app() {
  KILL_EVIDENCE="no process to kill"
  [ -n "$APP_PID" ] || return 0
  local pid="$APP_PID"
  local status=0
  if kill -0 "$pid" 2>/dev/null; then
    kill -9 "$pid" 2>/dev/null
  fi
  wait "$pid" 2>/dev/null
  status=$?
  APP_PID=""
  local waited=0
  while [ -d "/proc/$pid" ] && [ "$waited" -lt "$KILL_WAIT_TENTHS" ]; do
    sleep 0.1
    waited=$((waited + 1))
  done
  if [ -d "/proc/$pid" ]; then
    KILL_EVIDENCE="gone=no status=$status"
  else
    KILL_EVIDENCE="gone=yes status=$status"
  fi
  return 0
}

# Waits for the failpoint marker to appear.
#
# 0 the marker appeared
# 1 the marker did not appear in time
# 2 the request finished without ever reaching the failpoint
wait_for_marker() {
  local marker="$1" seconds="$2"
  local deadline=$((SECONDS + seconds))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if [ -f "$marker" ]; then
      return 0
    fi
    if [ -n "${REQUEST_PID:-}" ] && ! kill -0 "$REQUEST_PID" 2>/dev/null; then
      sleep 1
      [ -f "$marker" ] && return 0
      return 2
    fi
    sleep 0.2
  done
  return 1
}

# ── the HTTP surface ────────────────────────────────────────────────────────

# Sends one observation under an idempotency key. The status code goes into
# the file, the body into the file next to it.
#
# $1 the port   $2 the idempotency key   $3 the tag of the output files
# $4 the directory of the files   $5 the timeout in seconds
observe() {
  local port="$1" key="$2" tag="$3" dir="$4"
  local timeout="${5:-$HTTP_TIMEOUT_SECONDS}"
  curl -s -m "$timeout" -o "$dir/$tag.body" -w "%{http_code}" \
    -X POST -H "Content-Type: application/json" \
    -d "{\"assignment\":\"$ASSIGNMENT\",\"idempotencyKey\":\"$key\"}" \
    "http://127.0.0.1:$port/v1/cognitive/observe" >"$dir/$tag.status" 2>"$dir/$tag.err"
}

json_field() {
  python3 -c 'import json,sys
field = sys.argv[1]
try:
    data = json.load(open(sys.argv[2]))
except Exception:
    print("")
    raise SystemExit(0)
value = data
for part in field.split("."):
    if isinstance(value, list):
        value = value[int(part)] if part.isdigit() else ""
    elif isinstance(value, dict):
        value = value.get(part, "")
    else:
        value = ""
print("" if value is None else (value if isinstance(value, str) else json.dumps(value)))' \
    "$1" "$2"
}

# ── the build ────────────────────────────────────────────────────────────────

build_app() {
  if [ "${W45_SKIP_BUILD:-0}" = "1" ]; then
    note "the build is skipped (W45_SKIP_BUILD=1)"
  else
    note "the build: $GRADLE $GRADLE_TASK -x test"
    if ! "$GRADLE" "$GRADLE_TASK" -x test --console=plain; then
      echo "the application did not build; the journey cannot run."
      return 1
    fi
  fi
  if [ ! -f "$JAR" ]; then
    echo "the application is not built: $JAR is missing."
    return 1
  fi
  note "the application is $JAR"
  return 0
}

# ── the scenario ─────────────────────────────────────────────────────────────

# run_scenario <letter> <title> <failpoint> <policy>
#
# The policy is what the CONTRACT says must happen when the process dies at
# this checkpoint:
#   indeterminate   the retry must NOT repeat the work and must answer 409
#                   INDETERMINATE
#   re-executable   the retry must repeat the work, in the SAME episode
#   replay          the retry must replay the stored outcome and do no work
run_scenario() {
  local letter="$1" title="$2" failpoint="$3" policy="$4"

  head1 "scenario $letter — $title"
  note "the failpoint under test: $failpoint"
  note "the policy the contract demands: $policy"

  local work db port marker key
  work="$(make_private_dir "scenario-$letter")" || { fail "no private directory"; return 1; }
  db="$work/knowledge.sqlite"
  marker="$work/failpoint.marker"
  port="$(free_port)"
  key="w45-$letter-$(date +%s)-$$"

  note "the private directory: $work"
  note "the idempotency key: $key"

  # ── the first delivery, killed at the failpoint ──────────────────────────
  if ! start_app "$work" "$port" "$db" "$failpoint" "$marker"; then
    fail "the application did not start (see $APP_LOG)"
    return 1
  fi

  observe "$port" "$key" first "$work" "$BLOCKED_TIMEOUT_SECONDS" &
  REQUEST_PID=$!
  local marker_state="unreached"
  wait_for_marker "$marker" "$MARKER_WAIT_SECONDS"
  case "$?" in
    0) marker_state="reached" ;;
    2) marker_state="unreached-request-finished" ;;
    *) marker_state="unreached-timeout" ;;
  esac

  if [ "$marker_state" = "reached" ]; then
    pass "the failpoint $failpoint was reached: the marker exists"
    note "the marker says: $(tr '\n' ' ' <"$marker")"
    local marker_pid
    marker_pid="$(sed -n '2p' "$marker")"
    if [ "$marker_pid" = "$APP_PID" ]; then
      pass "the marker names the pid of this process ($marker_pid)"
    else
      fail "the marker names pid $marker_pid, but the application runs as $APP_PID"
    fi
  else
    fail "the failpoint $failpoint was NOT reached: the marker did not appear ($marker_state)"
    if [ -s "$work/first.status" ]; then
      note "the request answered instead of blocking: HTTP $(cat "$work/first.status")"
      note "the body: $(head -c "$BODY_PREVIEW_BYTES" "$work/first.body")"
    fi
  fi

  # ── the state BEFORE the kill ───────────────────────────────────────────
  db_snapshot "$db" "$key" >"$work/before.json"
  local before_state before_checkpoint before_episodes before_cases before_occurrences
  local before_predictions before_evidence
  before_state="$(db_field "$db" "$key" idempotency.state)"
  before_checkpoint="$(db_field "$db" "$key" idempotency.checkpoint)"
  before_episodes="$(db_field "$db" "$key" episodes)"
  before_cases="$(db_field "$db" "$key" cases)"
  before_occurrences="$(db_field "$db" "$key" occurrences)"
  before_predictions="$(db_field "$db" "$key" predictions_of_request)"
  before_evidence="$(db_field "$db" "$key" evidence)"
  printf '  ---- BEFORE the kill ----\n'
  printf '  episodes=%s cases=%s occurrences=%s evidence=%s predictions_of_request=%s idempotency.state=%s checkpoint=%s\n' \
    "$before_episodes" "$before_cases" "$before_occurrences" "$before_evidence" \
    "$before_predictions" "$before_state" "$before_checkpoint"

  # ── the kill ─────────────────────────────────────────────────────────────
  local killed_pid="$APP_PID"
  kill_app
  note "the kill: SIGKILL to pid $killed_pid — $KILL_EVIDENCE"
  case "$KILL_EVIDENCE" in
    *"status=$SIGKILL_EXIT"*) pass "the process died of SIGKILL (exit status $SIGKILL_EXIT) and is gone" ;;
    *) fail "the process did not die of SIGKILL: $KILL_EVIDENCE" ;;
  esac
  REQUEST_PID=""

  # ── the state the new process will find ──────────────────────────────────
  local after_kill_state after_kill_checkpoint
  after_kill_state="$(db_field "$db" "$key" idempotency.state)"
  after_kill_checkpoint="$(db_field "$db" "$key" idempotency.checkpoint)"
  printf '  ---- AFTER the kill, BEFORE the restart ----\n'
  printf '  idempotency.state=%s checkpoint=%s\n' "$after_kill_state" "$after_kill_checkpoint"
  # The classification the CONTRACT gives to the checkpoint the process died
  # at. It is printed here so that the outcome of the retry can be read
  # against the rule that produced it.
  note "the durable checkpoint at the kill was $after_kill_checkpoint: $(classify_checkpoint "$after_kill_checkpoint")"

  # ── the restart, against the SAME database ───────────────────────────────
  if ! start_app "$work" "$port" "$db" "" ""; then
    fail "the application did not start again (see $APP_LOG)"
    return 1
  fi

  # ── the retry, with the SAME key ─────────────────────────────────────────
  observe "$port" "$key" retry "$work"
  local retry_status retry_state retry_episode
  retry_status="$(cat "$work/retry.status" 2>/dev/null)"
  retry_state="$(json_field state "$work/retry.body")"
  retry_episode="$(json_field episodeId "$work/retry.body")"
  note "the retry answered: HTTP $retry_status state=$retry_state episodeId=$retry_episode"
  note "the body: $(head -c "$BODY_PREVIEW_BYTES" "$work/retry.body")"

  # ── the state AFTER the retry ────────────────────────────────────────────
  db_snapshot "$db" "$key" >"$work/after.json"
  local after_state after_checkpoint after_episodes after_cases after_occurrences
  local after_predictions after_of_request after_occurrences_of_request after_evidence
  after_state="$(db_field "$db" "$key" idempotency.state)"
  after_checkpoint="$(db_field "$db" "$key" idempotency.checkpoint)"
  after_episodes="$(db_field "$db" "$key" episodes)"
  after_cases="$(db_field "$db" "$key" cases)"
  after_occurrences="$(db_field "$db" "$key" occurrences)"
  after_predictions="$(db_field "$db" "$key" predictions_of_request)"
  after_of_request="$(db_field "$db" "$key" episodes_of_request)"
  after_occurrences_of_request="$(db_field "$db" "$key" occurrences_of_request)"
  after_evidence="$(db_field "$db" "$key" evidence)"
  printf '  ---- AFTER the retry ----\n'
  printf '  episodes=%s cases=%s occurrences=%s evidence=%s predictions_of_request=%s\n' \
    "$after_episodes" "$after_cases" "$after_occurrences" "$after_evidence" "$after_predictions"
  printf '  idempotency.state=%s checkpoint=%s episodes_of_request=%s occurrences_of_request=%s\n' \
    "$after_state" "$after_checkpoint" "$after_of_request" "$after_occurrences_of_request"
  # The rows of observation_occurrence carry no episode id in this build: the
  # kernel writes the observation without naming the episode it belongs to. The
  # count of the whole store is therefore the measure of "how many times was
  # this observation committed" — and the store of a scenario holds one
  # request only.
  if [ "${after_occurrences_of_request:-0}" = "0" ] && [ "${after_occurrences:-absent}" != "absent" ]; then
    note "the occurrences carry no episode id: the count of the store ($after_occurrences) is the measure"
  fi

  # ── the assertions that hold for EVERY scenario ──────────────────────────
  if [ "${after_of_request:-0}" = "1" ]; then
    pass "the request opened exactly ONE episode ($after_of_request)"
  else
    fail "the request has ${after_of_request:-0} episodes, expected exactly 1"
  fi
  if [ "${after_episodes:-0}" = "1" ]; then
    pass "the store holds exactly ONE episode in total ($after_episodes): no second episode"
  else
    fail "the store holds ${after_episodes:-0} episodes, expected exactly 1"
  fi
  if [ "$after_state" = "IN_PROGRESS" ]; then
    fail "the idempotency row is still IN_PROGRESS: the request was left without a decision"
  else
    pass "the idempotency row is not IN_PROGRESS: it is $after_state"
  fi
  local recovery
  recovery="$(db_field "$db" "$key" idempotency.recovery)"
  if [ "$after_state" = "COMPLETED" ]; then
    pass "the request is COMPLETED: it was not interrupted and needs no recovery decision"
  elif [ -n "$recovery" ]; then
    pass "the recovery decision is recorded: $recovery"
  else
    fail "the idempotency row carries no recorded recovery decision"
  fi
  local history
  history="$(python3 -c 'import json,sys
data = json.load(open(sys.argv[1]))
for event in data.get("request_history", []):
    print("       " + str(event.get("occurred_at")) + " " + str(event.get("state"))
          + (" @" + str(event.get("checkpoint")) if event.get("checkpoint") else "")
          + " — " + str(event.get("detail")))' "$work/after.json" 2>/dev/null)"
  printf '  ---- the lifecycle history of the request ----\n%s\n' "$history"
  if [ -n "$history" ]; then
    pass "the lifecycle history of the request explains what happened"
  else
    fail "the lifecycle history of the request is empty"
  fi

  # ── the assertions of the scenario ───────────────────────────────────────
  case "$policy" in
    indeterminate)
      if [ "$retry_status" = "409" ]; then
        pass "the retry answered $HTTP_CONFLICT: the request was not executed again"
      else
        fail "the retry answered HTTP '$retry_status', expected $HTTP_CONFLICT"
      fi
      if [ "$retry_state" = "INDETERMINATE" ]; then
        pass "the retry says the outcome is INDETERMINATE"
      else
        fail "the retry says state='$retry_state', expected INDETERMINATE"
      fi
      if [ "${after_occurrences:-0}" = "${before_occurrences:-x}" ]; then
        pass "the retry committed no new observation ($after_occurrences occurrences, as before the kill)"
      else
        fail "the observations grew from ${before_occurrences:-?} to ${after_occurrences:-?} across the retry"
      fi
      if [ "${after_cases:-0}" = "${before_cases:-x}" ]; then
        pass "the retry committed no new case ($after_cases cases, as before the kill)"
      else
        fail "the cases grew from ${before_cases:-?} to ${after_cases:-?} across the retry"
      fi
      if [ "${after_predictions:-0}" = "${before_predictions:-x}" ]; then
        pass "the retry wrote no new prediction ($after_predictions, as before the kill)"
      else
        fail "the predictions grew from ${before_predictions:-?} to ${after_predictions:-?} across the retry"
      fi
      ;;
    re-executable)
      if [ "$retry_status" = "200" ]; then
        pass "the retry answered $HTTP_OK: the request was executed again"
      else
        fail "the retry answered HTTP '$retry_status', expected $HTTP_OK (the request had written nothing)"
        note "the request was killed at the checkpoint $after_kill_checkpoint, and $(classify_checkpoint "$after_kill_checkpoint")"
        note "the answer the contract gives for that checkpoint is the one the retry gave: HTTP $retry_status state=$retry_state"
      fi
      local claimed_episode
      claimed_episode="$(db_field "$db" "$key" idempotency.episode_id)"
      if [ -n "$claimed_episode" ] && [ "$claimed_episode" = "$retry_episode" ]; then
        pass "the retry ran in the SAME episode ($retry_episode)"
      else
        fail "the retry ran in episode '$retry_episode', but the claim held '$claimed_episode'"
      fi
      if [ "${after_episodes:-0}" = "1" ]; then
        pass "the request still owns exactly ONE episode after the retry"
      else
        fail "the store holds ${after_episodes:-0} episodes after the retry, expected exactly 1"
      fi
      ;;
    replay)
      if [ "$retry_status" = "200" ]; then
        pass "the retry answered $HTTP_OK: the stored outcome was replayed"
      else
        fail "the retry answered HTTP '$retry_status', expected $HTTP_OK"
      fi
      local claimed_episode
      claimed_episode="$(db_field "$db" "$key" idempotency.episode_id)"
      if [ -n "$claimed_episode" ] && [ "$claimed_episode" = "$retry_episode" ]; then
        pass "the replay names the SAME episode ($retry_episode)"
      else
        fail "the replay names episode '$retry_episode', but the record holds '$claimed_episode'"
      fi
      if [ "${after_occurrences:-0}" = "${before_occurrences:-x}" ]; then
        pass "the replay committed no new observation ($after_occurrences occurrences, as before the kill)"
      else
        fail "the observations grew from ${before_occurrences:-?} to ${after_occurrences:-?} across the replay"
      fi
      if [ "${after_cases:-0}" = "${before_cases:-x}" ]; then
        pass "the replay committed no new case ($after_cases cases, as before the kill)"
      else
        fail "the cases grew from ${before_cases:-?} to ${after_cases:-?} across the replay"
      fi
      # A cycle writes TWO predictions of its episode: the forecast of the model
      # and the prediction of the probe. What matters here is that the replay
      # wrote NO new one.
      if [ "${after_predictions:-0}" = "${before_predictions:-x}" ]; then
        pass "the replay wrote no new prediction ($after_predictions, as before the kill)"
      else
        fail "the predictions grew from ${before_predictions:-?} to ${after_predictions:-?} across the replay"
      fi
      ;;
  esac

  # ── the evidence, for the report ─────────────────────────────────────────
  {
    echo "scenario $letter: failpoint=$failpoint policy=$policy"
    echo "  before: episodes=$before_episodes cases=$before_cases occurrences=$before_occurrences evidence=$before_evidence predictions=$before_predictions state=$before_state checkpoint=$before_checkpoint"
    echo "  after : episodes=$after_episodes cases=$after_cases occurrences=$after_occurrences evidence=$after_evidence predictions=$after_predictions state=$after_state checkpoint=$after_checkpoint"
    echo "  retry : HTTP $retry_status state=$retry_state episodeId=$retry_episode"
  } >>"$PRIV/summary.txt"

  stop_app_quietly
}

# Stops the application of a scenario, if it is still running.
stop_app_quietly() {
  if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
    kill -9 "$APP_PID" 2>/dev/null
    wait "$APP_PID" 2>/dev/null
  fi
  APP_PID=""
}

# ── the probe of the reachability of a failpoint ────────────────────────────

# Is the named checkpoint EVER reported to the listener? The failpoint fires
# only where the lifecycle reports a checkpoint, so a checkpoint that is never
# reported can never be staged. The probe answers that question with a marker
# file and a process, not with an assumption.
#
# $1 the checkpoint
probe_failpoint() {
  local checkpoint="$1"
  local work db port marker key
  work="$(make_private_dir "probe-$checkpoint")" || return 2
  db="$work/knowledge.sqlite"
  marker="$work/failpoint.marker"
  port="$(free_port)"
  key="w45-probe-$checkpoint-$(date +%s)-$$"
  if ! start_app "$work" "$port" "$db" "$checkpoint" "$marker"; then
    stop_app_quietly
    return 2
  fi
  observe "$port" "$key" probe "$work" "$BLOCKED_TIMEOUT_SECONDS" &
  REQUEST_PID=$!
  wait_for_marker "$marker" "$MARKER_WAIT_SECONDS"
  local result=$?
  REQUEST_PID=""
  stop_app_quietly
  if [ "$result" -eq 0 ]; then
    note "the failpoint $checkpoint IS reachable: the marker appeared"
    return 0
  fi
  note "the failpoint $checkpoint is NOT reachable: the marker never appeared"
  return 1
}

# ── the journey ──────────────────────────────────────────────────────────────

head1 "the application is built"
if ! build_app; then
  exit 1
fi

PRIV="$(mktemp -d "${TMPDIR:-/tmp}/w45-cognitive-recovery-XXXXXX")" || exit 1
note "the private directory: $PRIV"

# ── scenario A ───────────────────────────────────────────────────────────────
run_scenario "A" "killed after the observation was committed" \
  "${W45_FP_A:-OBSERVATION_COMMITTED}" "indeterminate"

# ── scenario B ───────────────────────────────────────────────────────────────
# The checkpoint CLAIMED is the one that means "nothing durable was written".
# It is only usable if the lifecycle REPORTS it, so the reachability is probed
# first, in a private directory of its own.
head1 "scenario B — the reachability of the checkpoint CLAIMED"
if probe_failpoint "CLAIMED"; then
  CLAIMED_REACHABLE=1
  note "scenario B will use the failpoint CLAIMED"
else
  CLAIMED_REACHABLE=0
  note "scenario B falls back to the failpoint EPISODE_OPENED, as the mission allows"
fi
run_scenario "B" "killed before anything durable was written" \
  "$([ "$CLAIMED_REACHABLE" = "1" ] && echo CLAIMED || echo EPISODE_OPENED)" "re-executable"

# ── scenario C ───────────────────────────────────────────────────────────────
run_scenario "C" "killed after the request completed" \
  "${W45_FP_C:-IDEMPOTENCY_COMPLETED}" "replay"

# ── the summary ──────────────────────────────────────────────────────────────
head1 "the summary"
if [ -f "$PRIV/summary.txt" ]; then
  cat "$PRIV/summary.txt"
fi
printf '\n%d checks, %d failed\n' "$CHECKS" "$FAILURES"
if [ "$FAILURES" -eq 0 ]; then
  echo "the process-kill recovery journey: 0 — every assertion held"
  exit 0
fi
echo "the process-kill recovery journey: $FAILURES — assertion(s) failed"
exit 1
