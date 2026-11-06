#!/usr/bin/env bash
# RECON-W42.6 / W42.8 — what survives a forced termination.
#
# The twelfth step of the Local Alpha v0.3 checklist is the restart: the
# application's own JVM is killed with SIGKILL — no shutdown, no flush —
# and the state it left behind must still be there.
#
# This script is the other half of scripts/local-alpha-checklist.sh: it
# checks the state AFTER the kill, and it ASSERTS, so the gate can go red.
#
# Usage:
#   PORT=9096 scripts/local-alpha-restart.sh
#
# Exit status: 0 when every assertion holds, 1 when any does not.
set -u

PORT=${PORT:-9096}
BASE="http://localhost:$PORT/v1/workbench"
WS=${WS:-photosynthesis}
OUT=${OUT:-/tmp/matrix-alpha-checklist}
mkdir -p "$OUT"

FAILURES=0

check() {
  # Python prints True/False; the comparison is on the lower-case form.
  if [ "$(printf '%s' "$1" | tr 'A-Z' 'a-z')" = "true" ]; then
    printf '  OK    %s\n' "$2"
  else
    printf '  FAIL  %s\n' "$2"
    FAILURES=$((FAILURES + 1))
  fi
}

# The parser reads the FILE the step saved, not the pipe.
jqp() { python3 -c "import json,sys
d=json.load(open(sys.argv[1]))
$2" "$1"; }

printf '\n===== the state after the forced restart =====\n'

curl -s -m 30 "$BASE/workspaces/$WS/concepts" | tee "$OUT/restart-concepts.json" > /dev/null
jqp "$OUT/restart-concepts.json" "
cs = d if isinstance(d, list) else (d.get('concepts') or [])
print('  the concepts:', len(cs))
for c in cs:
    n = c.get('name') if isinstance(c, dict) else c
    if n and ('Krebs' in str(n) or 'Glycolysis' in str(n) or 'Oxidative' in str(n)):
        print('   -', n)
print('ASSERT', len(cs) > 0 and any('Krebs' in str(c) for c in cs))" > "$OUT/restart-concepts.txt"
cat "$OUT/restart-concepts.txt"
check "$(grep -o 'ASSERT .*' "$OUT/restart-concepts.txt" | cut -d' ' -f2)" "the concepts survive the kill"

curl -s -m 30 -X POST "$BASE/workspaces/$WS/ask" -H 'Content-Type: application/json' \
  -d '{"question":"What is the Krebs cycle?","level":"INTRODUCTORY"}' | tee "$OUT/restart-answer.json" > /dev/null
jqp "$OUT/restart-answer.json" "
st = d.get('statement') or ''
print('  answered:', d.get('answered'))
print('  the answer:', st.split(chr(10))[0][:160])
print('ASSERT', d.get('answered') is True and 'Krebs cycle' in st)" > "$OUT/restart-answer.txt"
cat "$OUT/restart-answer.txt"
check "$(grep -o 'ASSERT .*' "$OUT/restart-answer.txt" | cut -d' ' -f2)" "the imported account is still there"

curl -s -m 30 "$BASE/workspaces/$WS/representations" | tee "$OUT/restart-representations.json" > /dev/null
jqp "$OUT/restart-representations.json" "
rs = d if isinstance(d, list) else (d.get('representations') or [])
print('  the representations:', len(rs))
for r in rs:
    st = r.get('statement') or ''
    if 'second stage of cellular respiration' in st:
        print('   - the imported account is still there:', st[:130])
        break
print('ASSERT', any('second stage of cellular respiration' in (r.get('statement') or '') for r in rs))" > "$OUT/restart-representations.txt"
cat "$OUT/restart-representations.txt"
check "$(grep -o 'ASSERT .*' "$OUT/restart-representations.txt" | cut -d' ' -f2)" "the imported representation survives"

curl -s -m 30 "$BASE/workspaces/$WS/claims" | tee "$OUT/restart-claims.json" > /dev/null
jqp "$OUT/restart-claims.json" "
cl = d if isinstance(d, list) else (d.get('claims') or [])
print('  the claims:', len(cl))
print('ASSERT', len(cl) > 0)" > "$OUT/restart-claims.txt"
cat "$OUT/restart-claims.txt"
check "$(grep -o 'ASSERT .*' "$OUT/restart-claims.txt" | cut -d' ' -f2)" "the claims survive"

curl -s -m 60 -X POST "$BASE/workspaces/$WS/consolidate?maxForgotten=5&ageWindowDays=0" | tee "$OUT/restart-consolidate.json" > /dev/null
jqp "$OUT/restart-consolidate.json" "
print('  the consolidation after the restart: the candidates', d.get('candidates'),
      '| the forgotten', d.get('forgotten'), '| the idempotent', d.get('idempotent'))
print('ASSERT', d.get('examined') is not None)" > "$OUT/restart-consolidate.txt"
cat "$OUT/restart-consolidate.txt"
check "$(grep -o 'ASSERT .*' "$OUT/restart-consolidate.txt" | cut -d' ' -f2)" "the consolidation runs after the restart"

printf '\n===== the restart: %s =====\n' "$FAILURES"
if [ "$FAILURES" -eq 0 ]; then
  echo "every assertion held"
  exit 0
fi
echo "$FAILURES assertion(s) did not hold"
exit 1
