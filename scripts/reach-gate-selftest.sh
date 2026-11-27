#!/usr/bin/env bash
# CRJ-01 / COMPETENCE-REACH — the self-test of the milestone's supervisor gate.
#
# A gate that cannot fail is not a gate. This script copies the milestone's evidence into a
# TEMPORARY directory, breaks it six different ways, and checks that the gate REFUSES each
# broken copy BY THE CHECK THAT BELONGS TO IT — not merely by some check.
#
# The repository is never written to: the copy is made under a temporary directory and the
# gate is pointed at it with REACH_GATE_ROOT.
#
# Exit code 0 when all six mutations are caught by the right check, 1 otherwise.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "${here}/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT

copy_tree() {
  mkdir -p "${work}/docs-v2/engineering/evidence" "${work}/docs-v2/engineering" \
           "${work}/matrix-core/src/test/resources"
  cp -r "${root}/docs-v2/engineering/evidence/crj01-reach" \
        "${work}/docs-v2/engineering/evidence/crj01-reach"
  cp "${root}/docs-v2/engineering/CRJ-01-REACH-DEV-CASES.json" \
     "${work}/docs-v2/engineering/CRJ-01-REACH-DEV-CASES.json"
  cp -r "${root}/matrix-core/src/test/resources/crj01-reach" \
        "${work}/matrix-core/src/test/resources/crj01-reach"
}

# The gate must PASS the pristine copy.
copy_tree
out="$(REACH_GATE_ROOT="${work}" python3 "${here}/reach-gate.py" 2>&1)" || true
if ! printf '%s' "${out}" | grep -q "failed 0"; then
  echo "FAIL: the gate does not pass the pristine evidence"
  printf '%s\n' "${out}"
  exit 1
fi
echo "ok   the gate passes the pristine evidence"

mutations=0
caught=0

# A mutation: a python snippet that breaks the copy, the check that must catch it, and a
# human description. Each is applied to a FRESH copy.
#
# NINE mutations, nine checks: the last three are the ones the INDEPENDENT REVIEW demanded -
# a mislabelled row's kind, a forged scope and a falsified validation outcome must not pass.
mutate() {
  local description="$1"
  local expected="$2"
  local snippet="$3"
  copy_tree
  REACH_GATE_ROOT="${work}" python3 -c "${snippet}" || true
  # THE MANIFEST of the copy is refreshed: a mutation that the manifest alone would catch
  # proves nothing about the check it names.
  python3 - "${work}" <<'PY' || true
import hashlib, pathlib, sys
directory = pathlib.Path(sys.argv[1]) / "docs-v2/engineering/evidence/crj01-reach"
names = sorted(path.name for path in directory.iterdir()
               if path.is_file() and path.suffix in {".md", ".json", ".xml"}
               and path.name != "CHECKSUMS.sha256")
(directory / "CHECKSUMS.sha256").write_text("".join(
    f"{hashlib.sha256((directory / name).read_bytes()).hexdigest()}  {name}\n" for name in names),
    encoding="utf-8")
PY
  local out
  out="$(REACH_GATE_ROOT="${work}" python3 "${here}/reach-gate.py" 2>&1)" || true
  mutations=$((mutations + 1))
  if printf '%s' "${out}" | grep -q "FAILED: ${expected}"; then
    caught=$((caught + 1))
    echo "ok   ${description} -> caught by '${expected}'"
  else
    echo "FAIL ${description} -> NOT caught by '${expected}'"
    printf '%s\n' "${out}" | tail -20
  fi
}

mutate "a row is removed from the retained rows" \
  "every evaluation case of every domain is measured in every arm of every condition" \
  'import json,os
p=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/evidence/crj01-reach/CRJ-01-REACH-GRID-ROWS.json"
rows=json.load(open(p))
rows.pop(5)
json.dump(rows,open(p,"w"))'

mutate "a false admission is relabelled" \
  "every false admission and every false rejection is re-derived from the row it belongs to" \
  'import json,os
p=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/evidence/crj01-reach/CRJ-01-REACH-GRID-ROWS.json"
rows=json.load(open(p))
for r in rows:
    if r["falseAdmission"]:
        r["falseAdmission"]=False
        break
json.dump(rows,open(p,"w"))'

mutate "a denominator is changed" \
  "the summary's counts are re-derived from the rows, with the two denominators separate" \
  'import json,os
p=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/evidence/crj01-reach/CRJ-01-REACH-GRID-SUMMARY.json"
s=json.load(open(p))
for cell in s["cells"]:
    for st in cell["strategies"]:
        for c,counts in st["arms"].items():
            counts["falseAdmissionDenominator"]=counts["falseAdmissionDenominator"]+1
            json.dump(s,open(p,"w"))
            raise SystemExit(0)
json.dump(s,open(p,"w"))'

mutate "a refusal loses its reason" \
  "every refusal names its reason as a field of its own" \
  'import json,os
p=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/evidence/crj01-reach/CRJ-01-REACH-GRID-ROWS.json"
rows=json.load(open(p))
for r in rows:
    if not r["admitted"]:
        r["fallbackReason"]=""
        break
json.dump(rows,open(p,"w"))'

mutate "a corpus digest in the case set is changed" \
  "the case set's corpus digests match the corpus bytes" \
  'import json,os
p=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/CRJ-01-REACH-DEV-CASES.json"
c=json.load(open(p))
key=sorted(c["provenance"]["corpusSha256"])[0]
c["provenance"]["corpusSha256"][key]="0"*len(c["provenance"]["corpusSha256"][key])
json.dump(c,open(p,"w"))'

mutate "the RED stops failing" \
  "the RED carries failures" \
  'import os,shutil
base=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/evidence/crj01-reach"
shutil.copyfile(base+"/TEST-io.matrix.knowledge.CompetenceReachServedTest.xml",
                base+"/CRJ-01-REACH-REPRODUCTION-no-criterion.xml")'

echo
mutate "a row's kind is mislabelled" \
  "every row names the case of the case set it measures, with that case's own kind and answerability" \
  'import json,os
p=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/evidence/crj01-reach/CRJ-01-REACH-GRID-ROWS.json"
rows=json.load(open(p))
for r in rows:
    r["kind"]="PLAIN"
json.dump(rows,open(p,"w"))'

mutate "an arm's scope is forged" \
  "every arm's scope is the scope the summary records, and its validation outcome is consistent with its own agreement" \
  'import json,os
p=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/evidence/crj01-reach/CRJ-01-REACH-GRID-ROWS.json"
rows=json.load(open(p))
for r in rows:
    if r["strategy"]=="DECLARED":
        r["scope"]=r["scope"][:1]
json.dump(rows,open(p,"w"))'

mutate "a scope's validation outcome is falsified" \
  "every arm's scope is the scope the summary records, and its validation outcome is consistent with its own agreement" \
  'import json,os
p=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/evidence/crj01-reach/CRJ-01-REACH-GRID-SUMMARY.json"
summary=json.load(open(p))
for cell in summary["cells"]:
    for strategy in cell["strategies"]:
        strategy["validated"]=True
        strategy["validationAgreement"]=1.0
json.dump(summary,open(p,"w"))'

mutate "a breakdown by the kind of the case is falsified" \
  "the breakdown by the kind of the case is re-derived from the rows" \
  'import json,os
p=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/evidence/crj01-reach/CRJ-01-REACH-GRID-SUMMARY.json"
summary=json.load(open(p))
for cell in summary["cells"]:
    for strategy in cell["strategies"]:
        for criterion,breakdown in (strategy.get("byKind") or {}).items():
            for kind,counts in breakdown.items():
                counts["falseAdmissions"]=0
json.dump(summary,open(p,"w"))'

mutate "one arm is measured against another input" \
  "the six arms of every cell were measured against ONE input" \
  'import json,os
p=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/evidence/crj01-reach/CRJ-01-REACH-GRID-ROWS.json"
rows=json.load(open(p))
rows[0]["inputFingerprint"]="0000000000000000000000000000000000000000000000000000000000000000"
json.dump(rows,open(p,"w"))'

mutate "a summary's mean completeness is falsified" \
  "the summary's counts are re-derived from the rows, with the two denominators separate" \
  'import json,os
p=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/evidence/crj01-reach/CRJ-01-REACH-GRID-SUMMARY.json"
summary=json.load(open(p))
for cell in summary["cells"]:
    for strategy in cell["strategies"]:
        for criterion,counts in strategy["arms"].items():
            counts["meanCompleteness"]=0.0
json.dump(summary,open(p,"w"))'

mutate "a cell's verdict is falsified" \
  "every cell's verdict is re-derived from the arms it compares" \
  'import json,os
p=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/evidence/crj01-reach/CRJ-01-REACH-GRID-SUMMARY.json"
summary=json.load(open(p))
for cell in summary["cells"]:
    for strategy in cell["strategies"]:
        difference=strategy.get("criterionDifference") or {}
        if difference.get("verdict")=="NOT ESTABLISHED":
            difference["verdict"]="DIFFERENCE OBSERVED"
            strategy["criterionDifference"]=difference
json.dump(summary,open(p,"w"))'

mutate "one arm of a cell is restamped onto another input" \
  "the six arms of every cell were measured against ONE input" \
  'import json,os
p=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/evidence/crj01-reach/CRJ-01-REACH-GRID-ROWS.json"
rows=json.load(open(p))
seen=set()
for r in rows:
    key=(r["domain"],r["condition"])
    if key==("wbe","BASELINE") and r["arm"]=="DECLARED/ANCHOR" and len(seen)<1:
        r["inputFingerprint"]="1111111111111111111111111111111111111111111111111111111111111111"
        seen.add(key)
json.dump(rows,open(p,"w"))'

mutate "a row is moved from one cell to another" \
  "every cell carries every case of its domain in every one of the six arms, once" \
  'import json,os
p=os.environ["REACH_GATE_ROOT"]+"/docs-v2/engineering/evidence/crj01-reach/CRJ-01-REACH-GRID-ROWS.json"
rows=json.load(open(p))
for r in rows:
    if r["condition"]=="BASELINE":
        r["condition"]="CHANGED_BINDINGS"
        break
json.dump(rows,open(p,"w"))'

echo "mutations ${mutations}, caught by the right check ${caught}"
if [ "${caught}" -ne "${mutations}" ]; then
  exit 1
fi
exit 0
