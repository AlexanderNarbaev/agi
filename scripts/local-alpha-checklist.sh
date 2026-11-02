#!/usr/bin/env bash
# RECON-W42.6 / W42.8 — the Local Alpha v0.3 checklist as a GATE.
#
# The twelve steps of the mission's §14 acceptance journey, run over HTTP
# against a running workbench, and ASSERTED.
#
# WHY THIS IS A SCRIPT IN THE REPOSITORY, AND WHY IT ASSERTS:
#
#   - the checklist used to live in a temporary directory, so the
#     mission's own acceptance gate could not be re-run by anyone else;
#   - and it only PRINTED its readings. A gate that cannot go red is not
#     a gate — the same words the mutation harness uses about the formal
#     gate, and they apply here.
#
# Each step asserts one property of the journey:
#
#   1. CREATE      the workspace loads with concepts, claims and evidence
#   2. ACQUIRE     a real document becomes claims, concepts and
#                  representations
#   3. EXPLAIN     the imported concept is answered in the document's words
#   4. LEVELS      the same question is answered at four levels, and the
#                  answers differ
#   5. NOT KNOW    a question the workspace cannot support is refused
#   6. FALSE       a false premise is refused, and the refutation is quoted
#   7. PRACTISE    the system says what the learner should do next
#   8. INDEPENDENT the held-out items exist and are held out
#   9. REPRESENT   the import wrote a representation of the new concept
#  10. CONSOLIDATE the consolidation touches no claim
#  11. RETENTION   both arms of the retention experiment report
#  12. RESTART     the state survives a forced termination (this script
#                  records the state; scripts/local-alpha-restart.sh
#                  checks it after the kill)
#
# Usage:
#   PORT=9096 scripts/local-alpha-checklist.sh
#   OUT=/tmp/alpha scripts/local-alpha-checklist.sh
#
# Exit status: 0 when every assertion holds, 1 when any does not.
set -u

PORT=${PORT:-9096}
BASE="http://localhost:$PORT/v1/workbench"
WS=${WS:-photosynthesis}
OUT=${OUT:-/tmp/matrix-alpha-checklist}
mkdir -p "$OUT"

FAILURES=0
STEP=0

say() { STEP=$((STEP + 1)); printf '\n===== %s. %s =====\n' "$STEP" "$1"; }

# check <the condition's result> <what was expected>
check() {
  # Python prints True/False; the comparison is on the lower-case form.
  if [ "$(printf '%s' "$1" | tr 'A-Z' 'a-z')" = "true" ]; then
    printf '  OK    %s\n' "$2"
  else
    printf '  FAIL  %s\n' "$2"
    FAILURES=$((FAILURES + 1))
  fi
}

# The parser reads the FILE the step saved, not the pipe: the curl's
# output is teed to the file, and the file is what the step asserts on.
jqp() { python3 -c "import json,sys
d=json.load(open(sys.argv[1]))
$2" "$1"; }

say "CREATE — load the workspace"
curl -s -m 30 -X POST "$BASE/workspaces/$WS/load" | tee "$OUT/01.json" > /dev/null
jqp "$OUT/01.json" "
c = d.get('concepts', 0); cl = d.get('claims', 0); s = d.get('sources', 0)
e = d.get('evidence', 0); r = d.get('representations', 0)
print('  the concepts:', c, '| the claims:', cl, '| the sources:', s,
      '| the evidence:', e, '| the representations:', r)
print('ASSERT', c > 0 and cl > 0 and s > 0 and e > 0 and r > 0)" > "$OUT/01.txt"
cat "$OUT/01.txt"
check "$(grep -o 'ASSERT .*' "$OUT/01.txt" | cut -d' ' -f2)" "the workspace holds concepts, claims, sources, evidence and representations"

say "ACQUIRE — import a real document"
cat > "$OUT/lesson.txt" <<'EOF'
Cellular respiration is the process by which cells break down glucose to release energy.

Glycolysis is the first stage of cellular respiration. It splits one glucose molecule into two pyruvate molecules in the cytoplasm.

The Krebs cycle is the second stage of cellular respiration. It happens in the mitochondrial matrix and produces NADH and FADH2.

Oxidative phosphorylation is the third stage of cellular respiration. It uses the electron transport chain in the inner mitochondrial membrane to produce ATP.
EOF
curl -s -m 60 -X POST "$BASE/workspaces/$WS/documents" -F "file=@$OUT/lesson.txt" | tee "$OUT/02.json" > /dev/null
jqp "$OUT/02.json" "
print('  the source:', d.get('sourceId'), '| the format:', d.get('format'),
      '| the segments:', d.get('segments'), '| the claims:', d.get('claimsProposed'),
      '| the concepts created:', d.get('conceptsCreated'),
      '| the representations created:', d.get('representationsCreated'))
print('ASSERT', (d.get('claimsProposed') or 0) > 0 and (d.get('conceptsCreated') or 0) > 0
      and (d.get('representationsCreated') or 0) > 0)" > "$OUT/02.txt"
cat "$OUT/02.txt"
check "$(grep -o 'ASSERT .*' "$OUT/02.txt" | cut -d' ' -f2)" "the document became claims, concepts and representations"

say "EXPLAIN — the imported concept, in the document's own words"
curl -s -m 30 -X POST "$BASE/workspaces/$WS/ask" -H 'Content-Type: application/json' \
  -d '{"question":"What is the Krebs cycle?","level":"INTRODUCTORY"}' | tee "$OUT/03.json" > /dev/null
jqp "$OUT/03.json" "
st = d.get('statement') or ''
print('  answered:', d.get('answered'))
print('  the answer:', st.split(chr(10))[0][:160])
print('  the support score:', d.get('supportScore'))
print('ASSERT', d.get('answered') is True and 'Krebs cycle' in st)" > "$OUT/03.txt"
cat "$OUT/03.txt"
check "$(grep -o 'ASSERT .*' "$OUT/03.txt" | cut -d' ' -f2)" "the imported concept is answered"

say "LEVELS — the same question at four levels of abstraction"
LEVELS=""
for L in INTRODUCTORY INTERMEDIATE ADVANCED MECHANISTIC; do
  curl -s -m 30 -X POST "$BASE/workspaces/$WS/ask" -H 'Content-Type: application/json' \
    -d "{\"question\":\"What is photosynthesis?\",\"level\":\"$L\"}" | jqp "
st = (d.get('statement') or '').replace(chr(10),' ')
print('  $L:', st[:120])"
  LEVELS="$LEVELS$(curl -s -m 30 -X POST "$BASE/workspaces/$WS/ask" -H 'Content-Type: application/json' \
    -d "{\"question\":\"What is photosynthesis?\",\"level\":\"$L\"}" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("statement") or "").replace(chr(10)," "))')|"
done
DISTINCT=$(printf '%s' "$LEVELS" | tr '|' '\n' | sort -u | grep -c .)
printf '  the distinct statements across the four levels: %s\n' "$DISTINCT"
if [ "$DISTINCT" -ge 2 ]; then check true "the levels give different answers"; else check false "the levels give different answers"; fi

say "KNOW WHAT IT DOES NOT KNOW — a question it cannot support"
curl -s -m 30 -X POST "$BASE/workspaces/$WS/ask" -H 'Content-Type: application/json' \
  -d '{"question":"What is quantum chromodynamics?","level":"ADVANCED"}' | tee "$OUT/05.json" > /dev/null
jqp "$OUT/05.json" "
print('  answered:', d.get('answered'), '| the reason:', (d.get('abstentionReason') or '')[:150])
print('ASSERT', d.get('answered') is False and bool(d.get('abstentionReason')))" > "$OUT/05.txt"
cat "$OUT/05.txt"
check "$(grep -o 'ASSERT .*' "$OUT/05.txt" | cut -d' ' -f2)" "the unsupported question is refused, and the reason is given"

say "REFUSE A FALSE PREMISE — the workspace holds the refutation"
curl -s -m 30 -X POST "$BASE/workspaces/$WS/ask" -H 'Content-Type: application/json' \
  -d '{"question":"Since photosynthesis produces oxygen from carbon dioxide, how much oxygen does a plant release in an hour?","level":"INTRODUCTORY"}' | tee "$OUT/06.json" > /dev/null
jqp "$OUT/06.json" "
r = (d.get('abstentionReason') or '')
print('  answered:', d.get('answered'))
print('  the reason:', r[:230])
print('ASSERT', d.get('answered') is False and 'contradict' in r.lower())" > "$OUT/06.txt"
cat "$OUT/06.txt"
check "$(grep -o 'ASSERT .*' "$OUT/06.txt" | cut -d' ' -f2)" "the false premise is refused and the refutation is quoted"

say "PRACTISE — what the system tells the learner to do next"
curl -s -m 30 "$BASE/workspaces/$WS/exercises/next" | tee "$OUT/07.json" > /dev/null
jqp "$OUT/07.json" "
sel = d.get('selection') or {}
print('  the reason:', d.get('reason'))
print('  the item:', sel.get('prompt'))
print('  the concept:', sel.get('concept'), '| the dimension:', sel.get('dimension'))
print('ASSERT', bool(d.get('reason')) and bool(sel.get('prompt')))" > "$OUT/07.txt"
cat "$OUT/07.txt"
check "$(grep -o 'ASSERT .*' "$OUT/07.txt" | cut -d' ' -f2)" "the system says what to practise and why"

say "EVALUATE INDEPENDENTLY — the held-out item"
curl -s -m 30 "$BASE/workspaces/$WS/assessment" | tee "$OUT/08.json" > /dev/null
jqp "$OUT/08.json" "
items = d.get('items') or []
held = [i for i in items if i.get('heldOut')]
print('  the items:', len(items), '| the held-out ones:', len(held))
for i in held[:3]:
    print('   -', i.get('id'), '|', (i.get('prompt') or '')[:90])
print('ASSERT', len(items) > 0 and len(held) > 0 and len(held) < len(items))" > "$OUT/08.txt"
cat "$OUT/08.txt"
check "$(grep -o 'ASSERT .*' "$OUT/08.txt" | cut -d' ' -f2)" "there are held-out items, and they are a strict subset"

say "IMPROVE THE REPRESENTATION — what the import wrote"
curl -s -m 30 "$BASE/workspaces/$WS/representations" | tee "$OUT/09.json" > /dev/null
jqp "$OUT/09.json" "
rs = d if isinstance(d, list) else (d.get('representations') or [])
found = [r for r in rs if 'second stage of cellular respiration' in (r.get('statement') or '')]
print('  the representations:', len(rs))
for r in found:
    print('   -', r.get('level'), '|', (r.get('statement') or '')[:130])
    print('     the omitted:', len(r.get('deliberatelyOmitted') or []), 'items')
print('ASSERT', len(found) > 0)" > "$OUT/09.txt"
cat "$OUT/09.txt"
check "$(grep -o 'ASSERT .*' "$OUT/09.txt" | cut -d' ' -f2)" "the import wrote a representation of the new concept"

say "CONSOLIDATE — bounded, marked, and no claim is touched"
BEFORE=$(curl -s -m 30 "$BASE/workspaces/$WS/claims" | python3 -c 'import json,sys
d=json.load(sys.stdin)
print(len(d if isinstance(d,list) else (d.get("claims") or [])))')
curl -s -m 60 -X POST "$BASE/workspaces/$WS/consolidate?maxForgotten=5&ageWindowDays=0" | tee "$OUT/10.json" > /dev/null
jqp "$OUT/10.json" "
print('  examined:', d.get('examined'), '| the candidates:', d.get('candidates'),
      '| the forgotten:', d.get('forgotten'), '| the idempotent:', d.get('idempotent'))
for m in (d.get('marked') or [])[:3]:
    print('   -', m.get('kind'), '|', (m.get('reason') or '')[:80])
print('ASSERT', (d.get('forgotten') or 0) <= 5)" > "$OUT/10.txt"
AFTER=$(curl -s -m 30 "$BASE/workspaces/$WS/claims" | python3 -c 'import json,sys
d=json.load(sys.stdin)
print(len(d if isinstance(d,list) else (d.get("claims") or [])))')
cat "$OUT/10.txt"
printf '  the claims before: %s | after: %s\n' "$BEFORE" "$AFTER"
check "$(grep -o 'ASSERT .*' "$OUT/10.txt" | cut -d' ' -f2)" "the consolidation stayed within its budget"
if [ "$BEFORE" = "$AFTER" ]; then check true "the consolidation touched no claim"; else check false "the consolidation touched no claim"; fi

say "RETENTION — measured against the no-consolidation arm"
curl -s -m 180 "$BASE/workspaces/$WS/retention-experiment?maxSessions=20" | tee "$OUT/11.json" > /dev/null
jqp "$OUT/11.json" "
b = d.get('baseline') or {}; c = d.get('consolidation') or {}
print('  the items in the dataset:', d.get('itemsInTheDataset'))
print('  the baseline:      the sessions', b.get('sessions'), '| the retention', b.get('theRetentionStage'), '| the evidence', b.get('theRetentionEvidence'))
print('  the consolidation: the sessions', c.get('sessions'), '| the retention', c.get('theRetentionStage'), '| the evidence', c.get('theRetentionEvidence'))
print('ASSERT', bool(b.get('sessions')) and bool(c.get('sessions')))" > "$OUT/11.txt"
cat "$OUT/11.txt"
check "$(grep -o 'ASSERT .*' "$OUT/11.txt" | cut -d' ' -f2)" "both arms of the retention experiment report"

say "RESTART — the state before the kill"
curl -s -m 30 "$BASE/workspaces/$WS/concepts" | tee "$OUT/12-concepts.json" > /dev/null
jqp "$OUT/12-concepts.json" "
cs = d if isinstance(d, list) else (d.get('concepts') or [])
print('  the concepts:', len(cs))
print('ASSERT', len(cs) > 0)" > "$OUT/12.txt"
curl -s -m 30 -X POST "$BASE/workspaces/$WS/ask" -H 'Content-Type: application/json' \
  -d '{"question":"What is the Krebs cycle?","level":"INTRODUCTORY"}' | tee "$OUT/12-before.json" > /dev/null
jqp "$OUT/12-before.json" "
st = d.get('statement') or ''
print('  the answer:', st.split(chr(10))[0][:160])
print('ASSERT', d.get('answered') is True and 'Krebs cycle' in st)" > "$OUT/12b.txt"
cat "$OUT/12.txt" "$OUT/12b.txt"
check "$(grep -o 'ASSERT .*' "$OUT/12.txt" | cut -d' ' -f2)" "the concepts survive"
check "$(grep -o 'ASSERT .*' "$OUT/12b.txt" | cut -d' ' -f2)" "the imported answer is still there"

printf '\n===== the checklist: %s =====\n' "$FAILURES"
if [ "$FAILURES" -eq 0 ]; then
  echo "every assertion held"
  exit 0
fi
echo "$FAILURES assertion(s) did not hold"
exit 1
