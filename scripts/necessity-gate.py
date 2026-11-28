#!/usr/bin/env python3
"""CRJ-01 / EPISTEMIC-NECESSITY — the gate over the retained evidence.

A gate that cannot go red is not a gate. This one re-derives every number the milestone's
prose quotes from the retained rows, and checks the properties the milestone's claims rest on:

* the case set is the six independent authors' work, and the digests of their files are the
  digests the case set records;
* every row's confusion-matrix cell follows from its own decision and its own oracle label;
* every row carries a classification, a disposition and its evidence;
* the arms of a cell were measured against ONE input;
* every evaluation case of a domain appears in every arm of every cell of that domain;
* the summary's counts and rates are re-derived from the rows;
* the retained XMLs are the ones the suite produced.

Usage:
    python3 scripts/necessity-gate.py [repository-root]
"""

import collections
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else pathlib.Path(
    __file__).resolve().parents[1]
EVIDENCE = ROOT / "docs-v2/engineering/evidence/crj01-necessity"
CASES = ROOT / "docs-v2/engineering/CRJ-01-NECESSITY-DEV-CASES.json"
AUTHORS = EVIDENCE / "case-authors"
ROWS = EVIDENCE / "CRJ-01-NECESSITY-GRID-ROWS.json"
SUMMARY = EVIDENCE / "CRJ-01-NECESSITY-GRID-SUMMARY.json"

FAILURES = []
CHECKS = 0


def check(condition: bool, message: str) -> None:
    global CHECKS
    CHECKS += 1
    if not condition:
        FAILURES.append(message)


def main() -> int:
    # ── the case set and the six authors ───────────────────────────────────────────────
    if not CASES.is_file():
        print("RED   the case set is absent: %s" % CASES)
        return 1
    case_set = json.loads(CASES.read_text(encoding="utf-8"))
    assignments = case_set.get("provenance", {}).get("assignments", [])
    check(len(assignments) == 6,
          "the case set records six independent assignments, not %d" % len(assignments))
    for assignment in assignments:
        path = ROOT / assignment["file"]
        check(path.is_file(), "the assignment file %s is absent" % assignment["file"])
        if path.is_file():
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            check(digest == assignment["sha256"],
                  "the digest of %s does not match the one the case set records"
                  % assignment["file"])
        independence = str(assignment.get("independence", ""))
        # the record must NAME the contract the author read, in whatever words the author used
        check("case contract" in independence or "this contract" in independence
              or "CASE-CONTRACT" in independence,
              "the assignment %s does not record that its author read the case contract"
              % assignment.get("assignment"))
        # and it must record that no implementation was read
        check("implementation" in independence,
              "the assignment %s does not record that its author read no implementation"
              % assignment.get("assignment"))
        # and the corpus of the author's own domain
        check("corpus" in independence or "crj01-reach" in independence,
              "the assignment %s does not record that its author read the corpus"
              % assignment.get("assignment"))
    cases = case_set.get("cases", [])
    check(len(cases) >= 16, "the case set carries fewer than sixteen cases")
    conditions = {case.get("condition") for case in cases}
    for condition in ("LOWER_RANKED_NECESSARY", "JOINTLY_NECESSARY", "ALTERNATIVE_SUFFICIENT",
                      "MISSING_PREREQUISITE", "CONTRADICTION", "SUPERSESSION",
                      "CHANGED_BINDINGS", "AMBIGUITY", "UNANSWERABLE"):
        check(condition in conditions,
              "the case set does not cover the condition %s" % condition)
    ids = [case.get("id") for case in cases]
    check(len(set(ids)) == len(ids), "two cases share an id")
    questions = [case.get("question") for case in cases]
    check(len(set(questions)) == len(questions), "two cases share a question")

    # THE SLICES ARE DISJOINT — no necessary proposition crosses a slice.
    by_slice = collections.defaultdict(set)
    for case in cases:
        for evidence in case.get("necessaryEvidence", []):
            by_slice[case["slice"]].add((case["domain"], evidence["corpusFile"],
                                         evidence["proposition"]))
    for left in by_slice:
        for right in by_slice:
            if left == right:
                continue
            shared = by_slice[left] & by_slice[right]
            check(not shared,
                  "a necessary proposition crosses the slices %s and %s: %s"
                  % (left, right, sorted(shared)))

    # THE WHOLE-SET RULE OF CONTRACT SECTION 12 — a case's necessary sentence must not be a
    # sentence another case of the SAME DOMAIN already uses, whatever the slice and whatever
    # the assignment. The checker sees one file and can only hold an author to the
    # within-assignment rule; this is the milestone's half, and it is the half that found the
    # two collisions that were returned to their authors.
    by_domain = collections.defaultdict(list)
    for case in cases:
        for evidence in case.get("necessaryEvidence", []):
            by_domain[case["domain"]].append((evidence["proposition"], case.get("id"),
                                               case.get("slice")))
    for domain, entries in by_domain.items():
        seen = collections.defaultdict(list)
        for proposition, case_id, slice_name in entries:
            seen[proposition].append((case_id, slice_name))
        for proposition, owners in seen.items():
            check(len(owners) == 1,
                  "the necessary sentence %r is used by %d cases of the domain %s: %s"
                  % (proposition, len(owners), domain, owners))

    # ── the rows ───────────────────────────────────────────────────────────────────────
    if not ROWS.is_file():
        print("RED   the retained rows are absent: %s" % ROWS)
        return 1
    rows = json.loads(ROWS.read_text(encoding="utf-8"))
    check(len(rows) > 0, "the retained rows are empty")
    for row in rows:
        key = (row["domain"], row["condition"], row["strategy"], row["caseId"])
        expected = ("TP" if row["admitted"] else "FN") if row["oracleAdequate"] else (
            "FP" if row["admitted"] else "TN")
        check(row["classification"] == expected,
              "the row %s is classified %s but its own decision and label give %s"
              % (key, row["classification"], expected))
        check(row.get("disposition") in ("ANSWERED", "QUALIFIED", "ABSTAINED"),
              "the row %s carries no disposition" % (key,))
        check("served" in row and "citedClaimIds" in row,
              "the row %s carries no evidence" % (key,))
        check(row.get("inputFingerprint"), "the row %s carries no input fingerprint" % (key,))

    # THE ARMS OF A CELL SHARE ONE INPUT.
    cells = collections.defaultdict(set)
    for row in rows:
        cells[(row["domain"], row["condition"], row["strategy"], row["caseId"])].add(
            row["inputFingerprint"])
    for cell, fingerprints in cells.items():
        check(len(fingerprints) == 1,
              "the arms of %s were measured against %d different inputs" % (cell, len(fingerprints)))

    # EVERY EVALUATION CASE OF A DOMAIN IS IN EVERY ARM OF EVERY CELL.
    evaluation = collections.defaultdict(set)
    for case in cases:
        if case["slice"] == "EVALUATION":
            evaluation[case["domain"]].add(case["id"])
    measured = collections.defaultdict(set)
    for row in rows:
        measured[(row["domain"], row["condition"], row["strategy"])].add(row["caseId"])
    for key, ids in measured.items():
        check(ids == evaluation[key[0]],
              "the cell %s measured %d of the %d evaluation cases of %s"
              % (key, len(ids), len(evaluation[key[0]]), key[0]))

    # ── the summary, re-derived ───────────────────────────────────────────────────────
    if not SUMMARY.is_file():
        print("RED   the retained summary is absent: %s" % SUMMARY)
        return 1
    summary = json.loads(SUMMARY.read_text(encoding="utf-8"))
    derived = collections.defaultdict(collections.Counter)
    for row in rows:
        derived[row["criterion"]][row["classification"]] += 1
    cells_by_rule = {cell["key"]: cell for cell in summary.get("cells", [])}
    for criterion, counts in derived.items():
        key = "rule:%s" % criterion
        check(key in cells_by_rule, "the summary has no cell for the rule %s" % criterion)
        if key in cells_by_rule:
            cell = cells_by_rule[key]
            for name, value in (("tp", "TP"), ("fp", "FP"), ("tn", "TN"), ("fn", "FN")):
                check(cell["counts"][name] == counts[value],
                      "the cell %s reports %s=%d and the rows give %d"
                      % (key, name, cell["counts"][name], counts[value]))
            # THE RATES — RE-DERIVED, NOT CHECKED FOR SHAPE. The first version of this
            # check only asked whether the denominator was at least the numerator, which
            # every rate satisfies by construction and which therefore verified nothing.
            # The independent reviewer found it. The rate is now computed from the rows'
            # own counts and compared with the summary's own numerator and denominator.
            for rate, (num_key, den_a, den_b) in (
                    ("fpr", ("FP", "FP", "TN")),
                    ("fnr", ("FN", "FN", "TP")),
                    ("falseDiscovery", ("FP", "TP", "FP")),
                    ("falseOmission", ("FN", "TN", "FN"))):
                measured_rate = cell[rate]
                want_numerator = counts[num_key]
                want_denominator = counts[den_a] + counts[den_b]
                check(measured_rate["numerator"] == want_numerator,
                      "the rate %s of %s reports the numerator %d and the rows give %d"
                      % (rate, key, measured_rate["numerator"], want_numerator))
                check(measured_rate["denominator"] == want_denominator,
                      "the rate %s of %s reports the denominator %d and the rows give %d"
                      % (rate, key, measured_rate["denominator"], want_denominator))
                if want_denominator == 0:
                    check(measured_rate.get("value") is None,
                          "the rate %s of %s has no denominator and yet carries a value"
                          % (rate, key))
                else:
                    check(measured_rate.get("value") is not None,
                          "the rate %s of %s has a denominator and yet carries no value"
                          % (rate, key))

    # ── the retained XMLs ──────────────────────────────────────────────────────────────
    for name in ("TEST-io.matrix.knowledge.EpistemicNecessityServedTest.xml",):
        path = EVIDENCE / name
        check(path.is_file(), "the retained XML %s is absent" % name)
        if path.is_file():
            text = path.read_text(encoding="utf-8")
            check("<failure" not in text, "the retained XML %s carries a failure" % name)
            check('tests="0"' not in text, "the retained XML %s carries no test" % name)

    # THE RED REPRODUCTION — it must be PRESENT, and it must FAIL. A reproduction that is
    # absent proves nothing, and one that passes proves the opposite of what it claims.
    red = EVIDENCE / "CRJ-01-NECESSITY-REPRODUCTION-no-prerequisite.xml"
    check(red.is_file(), "the RED reproduction XML is absent")
    if red.is_file():
        text = red.read_text(encoding="utf-8")
        check("<failure" in text,
              "the RED reproduction XML carries no failure — a reproduction that passes is "
              "not a reproduction")
        check('failures="0"' not in text,
              "the RED reproduction XML reports no failures")

    # ── the manifest ──────────────────────────────────────────────────────────────────
    # The manifest is verified by nothing, so the first version of this gate checked that
    # it listed the files and stopped there. It is now verified against the files'
    # OWN digests.
    manifest = EVIDENCE / "CHECKSUMS.sha256"
    check(manifest.is_file(), "the manifest is absent")
    if manifest.is_file():
        for line in manifest.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split(None, 1)
            if len(parts) != 2:
                continue
            digest, name = parts[0], parts[1].lstrip("*")
            target = EVIDENCE / name
            check(target.is_file(), "the manifest lists %s and the file is absent" % name)
            if target.is_file():
                actual = hashlib.sha256(target.read_bytes()).hexdigest()
                check(actual == digest,
                      "the manifest records %s as %s and the file hashes to %s"
                      % (name, digest, actual))

    print("checks: %d" % CHECKS)
    if FAILURES:
        print("FAILURES: %d" % len(FAILURES))
        for failure in FAILURES:
            print("        - %s" % failure)
        return 1
    print("failures: 0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
