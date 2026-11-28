#!/usr/bin/env python3
"""CRJ-01 / EPISTEMIC-NECESSITY — assemble the independent case set.

The four case-author files are the deliverable of the independent authorship. This script
ASSEMBLES them into the canonical case set and adds nothing of its own: every case, every
necessary proposition, every alternative sufficient set and every disposition comes from the
authors' bytes, and the digests of those bytes are recorded so that a reader can check that
the assembled set is the authors' set.

The script never edits an author's file.

Usage:
    python3 scripts/necessity-cases.py
"""

import hashlib
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
AUTHORS = ROOT / "docs-v2/engineering/evidence/crj01-necessity/case-authors"
OUT = ROOT / "docs-v2/engineering/CRJ-01-NECESSITY-DEV-CASES.json"

# the corpus of each domain, and the scope the learning episode declares. The corpus is the
# one the previous milestone imported through the production acquisition path; this milestone
# reuses it rather than writing a second corpus, so that the two milestones are measured on
# the same bytes.
DOMAINS = {
    "wbe": {
        "subject": "wind-turbine blade leading-edge erosion",
        "files": ["wbe-a-erosion.md", "wbe-b-inspection.md", "wbe-c-coatings.md",
                  "wbe-d-aerodynamics.md"],
        "declaredScope": ["wbe-a-erosion.md", "wbe-b-inspection.md", "wbe-c-coatings.md"],
    },
    "hpd": {
        "subject": "hospital pharmacy unit-dose dispensing",
        "files": ["hpd-a-dispensing.md", "hpd-b-identification.md", "hpd-c-reconciliation.md",
                  "hpd-d-labelling.md"],
        "declaredScope": ["hpd-a-dispensing.md", "hpd-b-identification.md", "hpd-c-reconciliation.md"],
    },
}

ASSIGNMENTS = ["A1", "A2", "A3", "A4", "A5", "A6"]


def sha256_of(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    assignments = []
    cases = []
    for assignment in ASSIGNMENTS:
        path = AUTHORS / f"{assignment}.json"
        if not path.is_file():
            print(f"RED   the assignment file of {assignment} is missing: {path}", file=sys.stderr)
            return 1
        doc = json.loads(path.read_text(encoding="utf-8"))
        assignments.append({
            "assignment": assignment,
            "domain": doc.get("domain"),
            "file": str(path.relative_to(ROOT)),
            "sha256": sha256_of(path),
            "independence": doc.get("independence"),
            "notes": doc.get("notes", ""),
        })
        for case in doc.get("cases", []):
            case = dict(case)
            case["assignment"] = assignment
            cases.append(case)

    # the propositions each slice uses, so that a reader can see the disjointness at a glance
    by_slice = {}
    for case in cases:
        for evidence in case.get("necessaryEvidence", []):
            key = "%s|%s" % (evidence["corpusFile"], evidence["proposition"])
            by_slice.setdefault(case["slice"], set()).add(key)

    corpus_digests = {}
    for domain, spec in DOMAINS.items():
        for name in spec["files"]:
            corpus = ROOT / "matrix-core/src/test/resources/crj01-reach" / domain / name
            corpus_digests["%s/%s" % (domain, name)] = sha256_of(corpus)

    document = {
        "split": "crj-01-necessity-dev-cases",
        "version": "1.0",
        "date": "2026-11-28",
        "sealed": False,
        "sealedNote": ("the sealed evaluation is NOT EXECUTED; this is a DEV split on synthetic, "
                       "test-owned data, and the isolation a sealed split needs was not "
                       "established from this executor"),
        "author": {
            "role": ("six INDEPENDENT DSH case authors, commissioned as separate bounded "
                     "assignments of four questions each — four in the first round and two in "
                     "the follow-up round"),
            "independence": ("each author read ONLY the case contract and the corpus of its own "
                             "domain; no implementation, no instrument, no previous case set, no "
                             "pre-registration and no governance document. The authors did not "
                             "read each other's files. The case set is therefore NOT the "
                             "implementer's, and the measurement it supports is a DEV "
                             "measurement with an independent oracle."),
            "contract": "docs-v2/engineering/CRJ-01-NECESSITY-CASE-CONTRACT.md",
            "checker": "scripts/necessity-case-check.py",
            "assembledBy": ("this script; it reads the four authors' files and adds nothing of "
                            "its own"),
        },
        "provenance": {
            "assignments": assignments,
            "corpusSha256": corpus_digests,
            "slicePropositions": {key: sorted(value) for key, value in sorted(by_slice.items())},
        },
        "domains": [
            {"id": domain, "subject": spec["subject"], "files": spec["files"],
             "declaredScope": spec["declaredScope"]}
            for domain, spec in DOMAINS.items()
        ],
        "cases": cases,
    }
    OUT.write_text(json.dumps(document, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("GREEN %s" % OUT.relative_to(ROOT))
    print("      %d cases from %d independent assignments" % (len(cases), len(assignments)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
