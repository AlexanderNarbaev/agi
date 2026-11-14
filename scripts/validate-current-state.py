#!/usr/bin/env python3
"""Validate the CURRENT-state documents against the repository.

**What it is.** A narrow, deterministic, offline consistency check for the four
kinds of contradiction that a "current state" document can carry while the
existing validators stay green: a release claim the repository already
contradicts, a serving topology that denies the crossing the tree carries, and
evidence citations that no longer resolve. It is *report-only* by default and
never rewrites a document.

**What it is not.** It is not a second document system and not a semantic
checker. Every check is an invariant over facts that are already in the tree:
the commit subjects of the main line (a pull request's squash is a fact), the
existence of a compiled contract file, and the line counts of the files the
registers cite. No network access, no model, no heuristics over prose beyond
one exact stale phrase per check.

USAGE
-----
    python3 scripts/validate-current-state.py [--root .] [--json OUT] [--strict]
    python3 scripts/validate-current-state.py --release-subjects-file F   (tests)

EXIT CODES
----------
0  no contradiction (report-only mode always exits 0)
1  --strict and at least one contradiction
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

STATE = "docs-v2/governance/PROJECT-STATE.md"
TOPOLOGY = "docs-v2/architecture/RUNTIME-TOPOLOGY.md"
RISKS = "docs-v2/governance/RISK-REGISTER.md"
QUESTIONS = "docs-v2/governance/OPEN-QUESTIONS.md"
BRIDGE_CONTRACT = ("matrix-core/src/main/java/io/matrix/contract/bridge/"
                   "CognitiveTaskRequest.java")

# The exact phrases that assert a release is pending. A current-state document
# that says one of these while the main line carries the pull request's squash
# contradicts the repository.
RELEASE_PENDING_PATTERNS = (
    re.compile(r"pull request #(\d+)\s+is open", re.IGNORECASE),
    re.compile(r"pull request #(\d+)\) is open", re.IGNORECASE),
    re.compile(r"the release is not done", re.IGNORECASE),
    re.compile(r"awaits the owner's release decision", re.IGNORECASE),
)

# The exact phrase that asserts the two serving applications do not meet. The
# bridge contract's presence in the tree contradicts it.
NO_CROSSING_PHRASES = ("do not meet", "не встречаются")

CITATION_RE = re.compile(
    r"([A-Za-z0-9_./-]+\.(?:java|py|sh|md|json|yml|yaml)):(\d+)")
EVIDENCE_RE = re.compile(r"^- \*\*evidence\*\*:.*$", re.MULTILINE)


def read(path):
    try:
        return path.read_text(encoding="utf-8")
    except OSError:
        return ""


def git(root, *arguments):
    try:
        result = subprocess.run(("git",) + arguments, cwd=str(root),
                                capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        return None
    return result.stdout if result.returncode == 0 else None


def merged_pull_requests(root, subjects_file):
    """The pull-request numbers whose squash is on the main line."""
    if subjects_file:
        text = read(Path(subjects_file))
        if not text:
            return set(), "the --release-subjects-file is unreadable"
        mainline = "the given subject file"
    else:
        out = git(root, "log", "origin/main", "--oneline")
        mainline = "origin/main"
        if out is None:
            out = git(root, "log", "main", "--oneline")
            mainline = "main"
        if out is None:
            return None, "no main line is readable (git cannot answer)"
        text = out
    numbers = set()
    for match in re.finditer(r"\(#(\d+)\)", text):
        numbers.add(int(match.group(1)))
    return numbers, mainline


def check_release(root, subjects_file, defects):
    state = read(root / STATE)
    if not state:
        return
    numbers, mainline = merged_pull_requests(root, subjects_file)
    if numbers is None:
        defects.append({"kind": "UNREADABLE", "document": STATE,
                        "note": mainline})
        return
    for pattern in RELEASE_PENDING_PATTERNS:
        for match in pattern.finditer(state):
            if match.groups() and int(match.group(1)) not in numbers:
                continue
            defects.append({
                "kind": "RELEASE_CLAIM",
                "document": STATE,
                "claim": match.group(0),
                "repository": ("%s's history carries the squash of pull request #%s"
                               % (mainline, match.group(1))) if match.groups()
                              else ("%s's history carries the release squashes: %s"
                                    % (mainline, sorted(numbers))),
                "note": "a current-state document claims a release is pending that the main line has already merged",
            })


def check_topology(root, defects):
    topology = read(root / TOPOLOGY)
    if not topology:
        return
    contract = root / BRIDGE_CONTRACT
    if not contract.is_file():
        return
    for phrase in NO_CROSSING_PHRASES:
        if phrase in topology:
            defects.append({
                "kind": "TOPOLOGY_CROSSING",
                "document": TOPOLOGY,
                "claim": phrase,
                "repository": "the governed bridge contract exists at " + BRIDGE_CONTRACT,
                "note": "the topology denies the crossing the tree carries",
            })


SOURCE_ROOTS = ("matrix-core/src/main/java", "matrix-core/src/test/java",
               "matrix-core/src/main/resources", "scripts", "docs-v2",
               "matrix-core/src/test/resources")


def basename_index(root):
    """Every source file by basename, for the registers that cite a bare name."""
    index = {}
    for relative_root in SOURCE_ROOTS:
        base = root / relative_root
        if not base.is_dir():
            continue
        for path in base.rglob("*"):
            if path.is_file():
                index.setdefault(path.name, []).append(path)
    return index


def citation_root(root, relative, index):
    """Resolve a cited path against the roots the registers cite from."""
    candidates = (root / relative, root / "matrix-core/src/main/java" / relative,
                  root / "matrix-core/src/test/java" / relative,
                  root / "matrix-core/src/main/resources" / relative)
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    # The registers often cite a bare file name ("WriterLock.java:145"). Resolve
    # it only when the tree holds exactly one file with that name: a basename
    # that names two files is not evidence, it is an ambiguity.
    hits = index.get(Path(relative).name)
    if hits and len(hits) == 1:
        return hits[0]
    return None


def check_stale_evidence(root, defects):
    index = basename_index(root)
    for document in (RISKS, QUESTIONS):
        text = read(root / document)
        if not text:
            continue
        for line in EVIDENCE_RE.findall(text):
            for relative, number in CITATION_RE.findall(line):
                resolved = citation_root(root, relative, index)
                if resolved is None:
                    defects.append({
                        "kind": "STALE_EVIDENCE", "document": document,
                        "claim": "%s:%s" % (relative, number),
                        "repository": "the cited file does not exist in the tree",
                        "note": "a register cites evidence that no longer resolves",
                    })
                    continue
                length = len(read(resolved).splitlines())
                if int(number) > length:
                    defects.append({
                        "kind": "STALE_EVIDENCE", "document": document,
                        "claim": "%s:%s" % (relative, number),
                        "repository": "the cited file has %d lines" % length,
                        "note": "a register cites a line beyond the end of its evidence",
                    })


def main(argv):
    parser = argparse.ArgumentParser(
        description="the consistency check of the current-state documents")
    parser.add_argument("--root", default=".", help="the repository root")
    parser.add_argument("--json", default=None, help="write the report here")
    parser.add_argument("--strict", action="store_true",
                        help="exit 1 when a contradiction is found")
    parser.add_argument("--release-subjects-file", default=None,
                        help="a file of commit subjects (tests; no git needed)")
    arguments = parser.parse_args(argv[1:])
    root = Path(arguments.root).resolve()

    defects = []
    check_release(root, arguments.release_subjects_file, defects)
    check_topology(root, defects)
    check_stale_evidence(root, defects)

    print("the current state: %s" % (root / STATE))
    print("  the contradictions: %d" % len(defects))
    for defect in defects:
        print("  %s in %s" % (defect["kind"], defect["document"]))
        print("    claim:      %s" % defect.get("claim", "-"))
        print("    repository: %s" % defect.get("repository", defect.get("note", "-")))
        print("    why:        %s" % defect["note"])

    if arguments.json:
        Path(arguments.json).write_text(
            json.dumps({"root": str(root), "defects": defects}, indent=2),
            encoding="utf-8")
        print("  the report is at %s" % arguments.json)

    if arguments.strict and defects:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
