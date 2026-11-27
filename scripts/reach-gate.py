#!/usr/bin/env python3
"""CRJ-01 / COMPETENCE-REACH — the INDEPENDENT supervisor gate.

The gate re-derives the milestone's evidence from the RETAINED rows, without trusting the
test that produced them. It is deterministic, offline and read-only: it writes nothing, and
it never runs a build.

Every check names what it asserts. A gate that cannot fail is not a gate, and the gate's
self-test (``scripts/reach-gate-selftest.sh``) proves that six specific mutations of the
evidence are each caught by the check they belong to.
"""

from __future__ import annotations

import hashlib
import json
import math
import os
import sys
from pathlib import Path

# THE ROOT THE GATE READS. The environment override exists so that the gate's SELF-TEST can
# point it at a COPY of the evidence: the self-test must be able to break the evidence on
# purpose, and it must never break the repository's own.
ROOT = Path(os.environ.get("REACH_GATE_ROOT", Path(__file__).resolve().parents[1]))
EVIDENCE = ROOT / "docs-v2/engineering/evidence/crj01-reach"
ROWS = EVIDENCE / "CRJ-01-REACH-GRID-ROWS.json"
SUMMARY = EVIDENCE / "CRJ-01-REACH-GRID-SUMMARY.json"
CHECKSUMS = EVIDENCE / "CHECKSUMS.sha256"
CASES = ROOT / "docs-v2/engineering/CRJ-01-REACH-DEV-CASES.json"
CORPUS = ROOT / "matrix-core/src/test/resources/crj01-reach"
GREEN = EVIDENCE / "TEST-io.matrix.knowledge.CompetenceReachServedTest.xml"
RED = EVIDENCE / "CRJ-01-REACH-REPRODUCTION-no-criterion.xml"

STRATEGIES = ("CITATION_DERIVED", "DECLARED", "EVIDENCE_DERIVED")
CRITERIA = ("ANCHOR", "CLAIM_SUFFICIENCY")
CONDITIONS = ("BASELINE", "CHANGED_BINDINGS", "SUPERSEDED")

failures: list[str] = []
checks = 0


def check(name: str, condition: bool, detail: str = "") -> None:
    global checks
    checks += 1
    if condition:
        print(f"  ok   {name}")
    else:
        print(f"  FAIL {name}{(': ' + detail) if detail else ''}")
        failures.append(name)


def _round3(value: float) -> float:
    """The producer's rounding: HALF UP to three decimals.

    The summary rounds with Java's Math.round, which rounds a half UP. Python's round
    rounds a half to even, so the gate would disagree with the producer on an exact half
    and report a defect that is not one."""
    return math.floor(value * 1000 + 0.5) / 1000


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_json(path: Path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def main() -> int:
    print("CRJ-01 / COMPETENCE-REACH — the independent supervisor gate")

    # ── the retained evidence exists and its manifest verifies ──────────────────────────────
    check("the retained evidence directory exists", EVIDENCE.is_dir())
    if not EVIDENCE.is_dir():
        return report()
    manifest_ok = True
    manifest_detail = ""
    if CHECKSUMS.is_file():
        for line in CHECKSUMS.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            digest, _, name = line.partition("  ")
            target = EVIDENCE / name.strip()
            if not target.is_file() or sha256(target) != digest.strip():
                manifest_ok = False
                manifest_detail = name.strip()
                break
    else:
        manifest_ok = False
        manifest_detail = "CHECKSUMS.sha256 is absent"
    check("the retained manifest verifies", manifest_ok, manifest_detail)

    check("the retained rows exist", ROWS.is_file())
    check("the retained summary exists", SUMMARY.is_file())
    check("the case set exists", CASES.is_file())
    if not (ROWS.is_file() and SUMMARY.is_file() and CASES.is_file()):
        return report()

    rows = load_json(ROWS)
    summary = load_json(SUMMARY)
    case_set = load_json(CASES)

    # ── the rows are the rows of the case set ───────────────────────────────────────────────
    evaluation = [
        case
        for case in case_set["cases"]
        if case["slice"] == "EVALUATION"
    ]
    domains = sorted({case["domain"] for case in case_set["cases"]})
    # THE COUNT. The evaluation cases ALREADY carry their domain, so the number of cases is
    # the number of (domain, case) pairs: multiplying by the number of domains again would
    # count every case twice and ask for rows that do not exist.
    expected = len(evaluation) * len(CONDITIONS) * len(STRATEGIES) * len(CRITERIA)
    check("every evaluation case of every domain is measured in every arm of every condition",
          len(rows) == expected,
          f"the rows carry {len(rows)}, the case set implies {expected}")

    seen = {(row["domain"], row["condition"], row["arm"], row["caseId"]) for row in rows}
    check("no row is measured twice",
          len(seen) == len(rows),
          f"{len(rows) - len(seen)} rows are duplicates")

    # ── every cell carries every arm ────────────────────────────────────────────────────────
    cells = {(row["domain"], row["condition"]) for row in rows}
    arms_ok = True
    arms_detail = ""
    for domain, condition in sorted(cells):
        for strategy in STRATEGIES:
            for criterion in CRITERIA:
                arm = f"{strategy}/{criterion}"
                count = sum(
                    1
                    for row in rows
                    if row["domain"] == domain
                    and row["condition"] == condition
                    and row["arm"] == arm
                )
                if count == 0:
                    arms_ok = False
                    arms_detail = f"{domain}/{condition}/{arm}"
                    break
    check("every cell carries all six arms", arms_ok, arms_detail)

    # ── the two denominators are separate and are re-derived from the rows ─────────────────
    denominators_ok = True
    denominators_detail = ""
    for cell in summary["cells"]:
        for strategy in cell["strategies"]:
            for criterion, counts in strategy["arms"].items():
                selected = [
                    row
                    for row in rows
                    if row["domain"] == cell["domain"]
                    and row["condition"] == cell["condition"]
                    and row["strategy"] == strategy["strategy"]
                    and row["criterion"] == criterion
                ]
                admissions = sum(1 for row in selected if row["admitted"])
                refusals = sum(1 for row in selected if not row["admitted"])
                false_admissions = sum(1 for row in selected if row["falseAdmission"])
                false_rejections = sum(1 for row in selected if row["falseRejection"])
                # THE MEANS AND THE VERDICT ARE RE-DERIVED TOO. A count that survives a
                # falsified mean, or a verdict that survives falsified counts, is a claim
                # the reader cannot check.
                completeness = sum(
                    row["completeness"] for row in selected if row["admitted"]
                )
                expected_completeness = (
                    _round3(completeness / admissions) if admissions else 0.0
                )
                coverage = sum(row["coverage"] for row in selected)
                expected_coverage = (
                    _round3(coverage / len(selected)) if selected else 0.0
                )
                fallbacks = sum(1 for row in selected if row["fallback"])
                if (counts["admissions"] != admissions
                        or counts["refusals"] != refusals
                        or counts["falseAdmissions"] != false_admissions
                        or counts["falseRejections"] != false_rejections
                        or counts["falseAdmissionDenominator"] != admissions
                        or counts["falseRejectionDenominator"] != refusals
                        or abs(float(counts["meanCompleteness"]) - expected_completeness) > 0.001
                        or abs(float(counts["meanCoverage"]) - expected_coverage) > 0.001
                        or int(counts["fallbacks"]) != fallbacks):
                    denominators_ok = False
                    denominators_detail = (
                        f"{cell['domain']}/{cell['condition']}/{strategy['strategy']}/{criterion}"
                    )
                    break
    check("the summary's counts are re-derived from the rows, with the two denominators "
          "separate", denominators_ok, denominators_detail)

    # ── a false admission is an admission that was not acceptable ───────────────────────────
    errors_ok = True
    errors_detail = ""
    for row in rows:
        expected_admission = row["admitted"] and not _acceptable(row)
        expected_rejection = (
            not row["admitted"] and row["answerable"] and row["carries"]
        )
        if row["falseAdmission"] != expected_admission or row["falseRejection"] != expected_rejection:
            errors_ok = False
            errors_detail = f"{row['domain']}/{row['condition']}/{row['arm']}/{row['caseId']}"
            break
    check("every false admission and every false rejection is re-derived from the row it "
          "belongs to", errors_ok, errors_detail)

    # ── every refusal names its reason ──────────────────────────────────────────────────────
    fallback_ok = all(
        row["fallbackReason"].strip() for row in rows if not row["admitted"]
    )
    check("every refusal names its reason as a field of its own", fallback_ok)

    # ── every scope was validated ──────────────────────────────────────────────────────────
    scope_ok = all(
        str(strategy.get("validationReason", "")).strip()
        for cell in summary["cells"]
        for strategy in cell["strategies"]
    ) if summary["cells"] else False
    check("every candidate scope carries the outcome of its validation", scope_ok)

    # ── the rows agree with the CASE SET, not only with each other ──────────────────────────
    # WHY THIS CHECK EXISTS. The counts above are re-derived from the producer's own booleans:
    # a producer that mislabels a row's kind, or that records a scope the summary does not
    # carry, leaves every count intact. The rows must therefore be crossed with the oracle
    # they claim to measure.
    oracle_ok = True
    oracle_detail = ""
    case_by_id: dict[tuple[str, str], dict] = {}
    for case in case_set["cases"]:
        case_by_id.setdefault((case["domain"], case["id"]), case)
    for row in rows:
        case = case_by_id.get((row["domain"], row["caseId"]))
        if case is None:
            oracle_ok = False
            oracle_detail = f"{row['domain']}/{row['caseId']}: the row names a case the case set does not hold"
            break
        if case["slice"] != "EVALUATION":
            oracle_ok = False
            oracle_detail = f"{row['domain']}/{row['caseId']}: the row measures a case of the {case['slice']} slice"
            break
        if row["kind"] != case["kind"]:
            oracle_ok = False
            oracle_detail = f"{row['domain']}/{row['caseId']}: the row says {row['kind']}, the case says {case['kind']}"
            break
        if bool(row["answerable"]) != bool(case["answerable"]):
            oracle_ok = False
            oracle_detail = f"{row['domain']}/{row['caseId']}: the row says answerable={row['answerable']}, the case says {case['answerable']}"
            break
        if row["arm"] != f"{row['strategy']}/{row['criterion']}":
            oracle_ok = False
            oracle_detail = f"{row['domain']}/{row['caseId']}: the arm {row['arm']} is not the strategy and the criterion of the row"
            break
    check("every row names the case of the case set it measures, with that case's own kind and "
          "answerability", oracle_ok, oracle_detail)

    # ── every arm's scope is the scope the summary records ─────────────────────────────────
    scope_ok = True
    scope_detail = ""
    for cell in summary["cells"]:
        for strategy in cell["strategies"]:
            recorded = sorted(str(source) for source in strategy.get("scope", []))
            if int(strategy.get("scopeSize", -1)) != len(recorded):
                scope_ok = False
                scope_detail = f"{cell['domain']}/{cell['condition']}/{strategy['strategy']}: the scope size is not the length of the scope"
                break
            agreement = strategy.get("validationAgreement")
            validated = bool(strategy.get("validated"))
            if validated != (agreement is not None and float(agreement) >= 1.0):
                scope_ok = False
                scope_detail = f"{cell['domain']}/{cell['condition']}/{strategy['strategy']}: the validation outcome and its agreement disagree"
                break
            for row in rows:
                if (row["domain"] == cell["domain"] and row["condition"] == cell["condition"]
                        and row["strategy"] == strategy["strategy"]):
                    if sorted(str(source) for source in row["scope"]) != recorded:
                        scope_ok = False
                        scope_detail = f"{cell['domain']}/{cell['condition']}/{strategy['strategy']}/{row['caseId']}: the row's scope is not the scope the summary records"
                        break
            if not scope_ok:
                break
        if not scope_ok:
            break
    check("every arm's scope is the scope the summary records, and its validation outcome is "
          "consistent with its own agreement", scope_ok, scope_detail)

    # ── the case set's corpus digests are the digests of the corpus bytes ───────────────────
    digests_ok = True
    digests_detail = ""
    for key, digest in case_set.get("provenance", {}).get("corpusSha256", {}).items():
        domain, _, name = key.partition("/")
        target = CORPUS / domain / name
        if not target.is_file() or sha256(target) != digest:
            digests_ok = False
            digests_detail = key
            break
    check("the case set's corpus digests match the corpus bytes", digests_ok, digests_detail)

    # ── every CELL carries its own rows ─────────────────────────────────────────────────────
    # The global count can be satisfied by a cell that is short and a cell that is long.
    # Each cell must carry exactly the cases of its domain in every arm.
    per_cell_ok = True
    per_cell_detail = ""
    for cell in {(row["domain"], row["condition"]) for row in rows}:
        selected = [
            row for row in rows
            if row["domain"] == cell[0] and row["condition"] == cell[1]
        ]
        cases = {row["caseId"] for row in selected}
        arms = {row["arm"] for row in selected}
        per_case = {}
        for row in selected:
            per_case[(row["caseId"], row["arm"])] = per_case.get((row["caseId"], row["arm"]), 0) + 1
        if (len(arms) != len(STRATEGIES) * len(CRITERIA)
                or any(count != 1 for count in per_case.values())
                or len(per_case) != len(cases) * len(arms)):
            per_cell_ok = False
            per_cell_detail = (f"{cell[0]}/{cell[1]}: {len(cases)} case(s) in {len(arms)} arm(s), "
                               f"{len(per_case)} measurement(s)")
            break
    check("every cell carries every case of its domain in every one of the six arms, once",
          per_cell_ok, per_cell_detail)

    # ── the six arms of a cell were measured against ONE input ──────────────────────────────
    # The fingerprint is the digest of the view the cell was measured against. A cell whose
    # arms carry different fingerprints was NOT a paired comparison, and the counts of such a
    # cell mean nothing.
    fingerprint_ok = True
    fingerprint_detail = ""
    for cell in {(row["domain"], row["condition"]) for row in rows}:
        prints = {
            row["inputFingerprint"]
            for row in rows
            if row["domain"] == cell[0] and row["condition"] == cell[1]
        }
        if len(prints) != 1:
            fingerprint_ok = False
            fingerprint_detail = f"{cell[0]}/{cell[1]}: {len(prints)} different inputs"
            break
    check("the six arms of every cell were measured against ONE input", fingerprint_ok,
          fingerprint_detail)

    # ── the verdict of every cell is re-derived from the arms it compares ───────────────────
    # The verdict is the pre-registration's own rule, and a verdict that survives falsified
    # arms is a claim.
    verdict_ok = True
    verdict_detail = ""
    for cell in summary["cells"]:
        for strategy in cell["strategies"]:
            arms = strategy.get("arms") or {}
            anchor = arms.get("ANCHOR") or {}
            sufficiency = arms.get("CLAIM_SUFFICIENCY") or {}
            expected = "NOT ESTABLISHED"
            if (int(sufficiency.get("admissions", 0)) != int(anchor.get("admissions", 0))
                    or int(sufficiency.get("falseAdmissions", 0))
                    != int(anchor.get("falseAdmissions", 0))
                    or int(sufficiency.get("falseRejections", 0))
                    != int(anchor.get("falseRejections", 0))
                    or abs(float(sufficiency.get("meanCompleteness", 0.0))
                           - float(anchor.get("meanCompleteness", 0.0))) > 0.001):
                expected = "DIFFERENCE OBSERVED"
            recorded = (strategy.get("criterionDifference") or {}).get("verdict")
            if recorded is not None and recorded != expected:
                verdict_ok = False
                verdict_detail = (f"{cell['domain']}/{cell['condition']}/"
                                  f"{strategy['strategy']}: the verdict says {recorded}, the arms "
                                  f"say {expected}")
                break
        if not verdict_ok:
            break
    check("every cell's verdict is re-derived from the arms it compares", verdict_ok, verdict_detail)

    # ── the breakdown by the kind of the case is re-derived from the rows ───────────────────
    # The pre-registration asks for the case kinds to be reported as a breakdown within each
    # cell. A breakdown that is not re-derived is a claim, not a measurement.
    kind_ok = True
    kind_detail = ""
    for cell in summary["cells"]:
        for strategy in cell["strategies"]:
            by_kind = strategy.get("byKind") or {}
            for criterion, arms in strategy["arms"].items():
                breakdown = by_kind.get(criterion) or {}
                selected = [
                    row
                    for row in rows
                    if row["domain"] == cell["domain"]
                    and row["condition"] == cell["condition"]
                    and row["strategy"] == strategy["strategy"]
                    and row["criterion"] == criterion
                ]
                for kind in {row["kind"] for row in selected}:
                    counted = breakdown.get(kind) or {}
                    of_kind = [row for row in selected if row["kind"] == kind]
                    expected = {
                        "cases": len(of_kind),
                        "admissions": sum(1 for row in of_kind if row["admitted"]),
                        "refusals": sum(1 for row in of_kind if not row["admitted"]),
                        "falseAdmissions": sum(1 for row in of_kind if row["falseAdmission"]),
                        "falseRejections": sum(1 for row in of_kind if row["falseRejection"]),
                    }
                    for field, value in expected.items():
                        if int(counted.get(field, -1)) != value:
                            kind_ok = False
                            kind_detail = (f"{cell['domain']}/{cell['condition']}/"
                                           f"{strategy['strategy']}/{criterion}/{kind}: the "
                                           f"breakdown says {field}={counted.get(field)}, the "
                                           f"rows say {value}")
                            break
                    if not kind_ok:
                        break
                if not kind_ok:
                    break
            if not kind_ok:
                break
        if not kind_ok:
            break
    check("the breakdown by the kind of the case is re-derived from the rows", kind_ok, kind_detail)

    # ── the retained XML: the GREEN carries no failure and the RED carries failures ────────
    check("the retained GREEN XML exists", GREEN.is_file())
    check("the retained RED XML exists", RED.is_file())
    if GREEN.is_file():
        check("the serving suite passed", _xml_failures(GREEN) == 0,
              f"{_xml_failures(GREEN)} failures")
    if RED.is_file():
        check("the RED carries failures", _xml_failures(RED) > 0,
              "a gate that cannot go red is not a gate")

    return report()


def _acceptable(row: dict) -> bool:
    """Whether a served answer is acceptable for its case, from the row's own fields."""
    if row["answerable"]:
        return bool(row["evidenceComplete"]) and bool(row["dispositionAllowed"])
    return bool(row["dispositionAllowed"]) and row["disposition"] != "ANSWERED"


def _xml_failures(path: Path) -> int:
    text = path.read_text(encoding="utf-8")
    total = 0
    for marker in ('failures="', 'errors="'):
        index = text.find(marker)
        while index >= 0:
            start = index + len(marker)
            end = start
            while end < len(text) and text[end].isdigit():
                end += 1
            total += int(text[start:end])
            index = text.find(marker, end)
    return total


def report() -> int:
    print()
    print(f"checks {checks}, failed {len(failures)}")
    for name in failures:
        print(f"  FAILED: {name}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
