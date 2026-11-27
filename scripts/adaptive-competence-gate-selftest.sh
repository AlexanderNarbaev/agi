#!/usr/bin/env bash
# CRJ-01 / ADAPTIVE-COMPETENCE — the SELF-TEST of the supervisor gate.
#
# WHAT THIS IS. The milestone's gate checks claims about the served path. A gate that
# cannot go red is not a gate: it would pass on a repository that never ran the suite.
# This script proves the gate CAN fail, and it does it the only safe way — on a COPY of the
# evidence in a temporary directory. The repository is never written to.
#
# WHAT IT PROVES. For each mutation below, the gate must exit NON-ZERO and must name the
# check that failed:
#
#   1. the retained XML carries a failure   -> the suite's own verdict is read
#   2. the served evidence loses a case     -> a case of the split is missing
#   3. a fallback row loses its reason      -> the record has a hole in it
#   4. the corpus digest is changed          -> the case no longer matches its corpus
#   5. the manifest is broken                -> the evidence does not verify
#   6. the RED stops failing                -> a gate that cannot go red is not a gate
#
# THE EXIT CODE is the verdict: 0 when every mutation was caught.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

PASSED=0
FAILED=0

note() { printf '%s\n' "$*"; }

# Runs the gate against a MUTATED COPY of the repository's evidence.
#
# $1 — the name of the mutation
# $2 — the shell code that mutates the copy
# $3 — the check that MUST fire (a gate that fails for another reason proves less)
run_mutation() {
  local name="$1"
  local mutation="$2"
  local expected="$3"
  local copy="$WORK/$name"
  rm -rf "$copy"
  mkdir -p "$copy"

  # the copy holds only what the gate reads: the evidence, the engineering artefacts and
  # the corpus. The gate resolves the repository root from its own path, so the copy is
  # given its own gate script.
  mkdir -p "$copy/docs-v2/engineering/evidence" "$copy/docs-v2/engineering" \
           "$copy/matrix-core/src/test/resources/crj01-adaptive" "$copy/scripts"
  cp -r "$REPO/docs-v2/engineering/evidence/crj01-adaptive-competence" \
        "$copy/docs-v2/engineering/evidence/"
  cp -r "$REPO/docs-v2/engineering/evidence/crj01-h2-served-transfer" \
        "$copy/docs-v2/engineering/evidence/"
  cp "$REPO"/docs-v2/engineering/CRJ-01-ADAPTIVE-*.json \
        "$REPO"/docs-v2/engineering/CRJ-01-ADAPTIVE-*.md \
        "$copy/docs-v2/engineering/" 2>/dev/null || true
  cp -r "$REPO/matrix-core/src/test/resources/crj01-adaptive/." \
        "$copy/matrix-core/src/test/resources/crj01-adaptive/"
  cp "$HERE/adaptive-competence-gate.py" "$copy/scripts/"

  ( cd "$copy" && eval "$mutation" ) >/dev/null 2>&1 || true

  local output
  local status=0
  output="$(cd "$copy" && python3 scripts/adaptive-competence-gate.py 2>&1)" || status=$?
  if [ "$status" -eq 0 ]; then
    note "FAIL  $name — the gate ACCEPTED the mutated evidence (exit 0): a gate that cannot go red is not a gate"
    FAILED=$((FAILED + 1))
  elif ! printf '%s\n' "$output" | grep -q "^FAIL $expected"; then
    note "FAIL  $name — the gate refused the evidence, but NOT with the check that belongs to it:"
    printf '%s\n' "$output" | grep '^FAIL' | sed 's/^/        /' | cut -c1-130
    note "        the check that must fire: $expected"
    FAILED=$((FAILED + 1))
  else
    note "PASS  $name — the gate refused the mutated evidence with the check that belongs to it:"
    printf '%s\n' "$output" | grep "^FAIL $expected" | sed 's/^/        /' | cut -c1-130
    PASSED=$((PASSED + 1))
  fi
}

note "the gate's self-test — each mutation must be caught"
note ""

run_mutation "the retained XML carries a failure" \
  "python3 - <<'PY'
import re, pathlib
p = pathlib.Path('docs-v2/engineering/evidence/crj01-adaptive-competence/TEST-io.matrix.knowledge.AdaptiveCompetenceServedTest.xml')
text = p.read_text(encoding='utf-8')
text = re.sub(r'failures=\"0\"', 'failures=\"1\"', text, count=1)
p.write_text(text, encoding='utf-8')
PY" "the serving suite passed"

run_mutation "the served evidence loses a case" \
  "python3 - <<'PY'
import json, pathlib
p = pathlib.Path('docs-v2/engineering/evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-EVIDENCE.json')
data = json.loads(p.read_text(encoding='utf-8'))
data['rows'] = [row for row in data['rows'] if row.get('caseId') != 'AC-DHX-01']
p.write_text(json.dumps(data, indent=1), encoding='utf-8')
PY" "every measured case is in the evidence"

run_mutation "a fallback row loses its reason" \
  "python3 - <<'PY'
import json, pathlib
p = pathlib.Path('docs-v2/engineering/evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-EVIDENCE.json')
data = json.loads(p.read_text(encoding='utf-8'))
for row in data['rows']:
    if row.get('fallbackReason'):
        row['fallbackReason'] = ''
        break
p.write_text(json.dumps(data, indent=1), encoding='utf-8')
PY" "every fallback row names its reason as a field of its own"

run_mutation "the corpus digest is changed" \
  "python3 - <<'PY'
import json, pathlib
p = pathlib.Path('docs-v2/engineering/CRJ-01-ADAPTIVE-DEV-SPLIT.json')
data = json.loads(p.read_text(encoding='utf-8'))
data['cases'][0]['corpusSha256'] = '0' * 64
p.write_text(json.dumps(data, indent=1), encoding='utf-8')
PY" "the split's corpus digests match the corpus bytes"

run_mutation "the manifest is broken" \
  "printf 'tampered\\n' >> docs-v2/engineering/evidence/crj01-adaptive-competence/README.md" \
  "the retained manifest verifies: docs-v2/engineering/evidence/crj01-adaptive-competence"

run_mutation "the RED stops failing" \
  "python3 - <<'PY'
import pathlib
p = pathlib.Path('docs-v2/engineering/evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-REPRODUCTION-no-gate.xml')
text = p.read_text(encoding='utf-8')
import re
text = re.sub(r'failures=\"\d+\"', 'failures=\"0\"', text, count=1)
p.write_text(text, encoding='utf-8')
PY" "the RED carries failures"

note ""
note "the mutations caught: $PASSED of $((PASSED + FAILED))"
if [ "$FAILED" -ne 0 ]; then
  note "the gate's self-test FAILED: $FAILED mutation(s) were accepted"
  exit 1
fi
note "the gate can go red"
exit 0
