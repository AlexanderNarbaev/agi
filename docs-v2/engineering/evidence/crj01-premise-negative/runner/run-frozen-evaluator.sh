#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ $# != 3 ]]; then
    echo "usage: run-frozen-evaluator.sh EVALUATOR_WORKTREE EXPECTED_SHA LABEL" >&2
    exit 2
fi
root=$(cd "$(dirname "$0")/.." && pwd -P)
repo=$(cd "$1" && pwd -P)
expected_sha=$2
label=$3
[[ "$repo" = "$root/"* && "$label" =~ ^[a-zA-Z0-9_-]+$ ]]
[[ $(git -C "$repo" rev-parse HEAD) = "$expected_sha" ]]
[[ -z $(git -C "$repo" status --porcelain --untracked-files=no) ]]

(
    cd "$root"
    sha256sum --strict -c MANIFEST.sha256
)
(
    cd "$root/runner"
    sha256sum --strict -c RUNNER.sha256
)

test_class=matrix-core/src/test/java/io/matrix/crj/runner/FrozenPremiseJourneyTest.java
[[ ! -e "$repo/$test_class" ]]
evidence="$root/evidence-$label"
mkdir "$evidence"
install -m 600 "$root/runner/FrozenPremiseJourneyTest.java" "$repo/$test_class"
trap 'rm -f -- "$repo/$test_class"' EXIT
gradle=$(find ~/.gradle/wrapper/dists/gradle-9.6.0-bin -path '*/bin/gradle' -print -quit)
[[ -x "$gradle" ]]
export MATRIX_FROZEN_EVALUATION="$root"
export GRADLE_USER_HOME=/home/alexandr-narbaev/Projects/agi/matrix-crj-provenance.5BER3h/gradle
command_args=(--offline --no-daemon --no-build-cache --max-workers=2
              :matrix-core:test --tests io.matrix.crj.runner.FrozenPremiseJourneyTest)
{
    printf 'commit=%s\nroot=%s\n' "$expected_sha" "$repo"
    printf '%q ' "$gradle" "${command_args[@]}"
    printf '\n'
} > "$evidence/command.txt"
cd "$repo"
set +e
/usr/bin/time -v -o "$evidence/resource.txt" flock -w 20 /tmp/gradle-matrix.lock \
    timeout --kill-after=15s 600 "$gradle" "${command_args[@]}" > "$evidence/gradle.log" 2>&1
status=$?
set -e
printf '%s\n' "$status" > "$evidence/exit-status.txt"
xml="$repo/matrix-core/build/test-results/test/TEST-io.matrix.crj.runner.FrozenPremiseJourneyTest.xml"
if [[ -f "$xml" ]]; then
    cp -- "$xml" "$evidence/"
fi
exit "$status"
