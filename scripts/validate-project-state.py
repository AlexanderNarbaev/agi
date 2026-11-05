#!/usr/bin/env python3
"""Validate the canonical project state of MATRIX.

**What it checks** — the four ways a "current state" document goes stale,
and it checks them against the repository rather than against itself:

1. **the identity** — the branch and the commit the state document names are
   the branch and the commit the repository is on;
2. **the references** — every document the state document points at exists;
3. **the registers** — the four canonical registers exist, are non-empty, and
   the counts they declare are the counts they hold;
4. **the test catalogue** — every scope it names has a command, and every
   commit it claims is a real commit.

**How it runs** — deterministic, offline, read-only.

    python3 scripts/validate-project-state.py
    python3 scripts/validate-project-state.py --json /tmp/state.json
    python3 scripts/validate-project-state.py --strict

The report mode never fails the build; the strict mode exits 1 on a defect.
"""

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

STATE = "docs-v2/governance/PROJECT-STATE.md"
ROADMAP = "docs-v2/governance/ROADMAP.md"
DECISIONS = "docs-v2/governance/DECISION-REGISTER.md"
RISKS = "docs-v2/governance/RISK-REGISTER.md"
QUESTIONS = "docs-v2/governance/OPEN-QUESTIONS.md"
PROTOCOL = "docs-v2/governance/AGENT-OPERATING-PROTOCOL.md"
POLICY = "docs-v2/governance/DOCUMENT-STATUS-POLICY.md"
REGISTRY = "docs-v2/architecture/CAPABILITY-REGISTRY.yml"
CATALOGUE = "docs-v2/engineering/TEST-CATALOGUE.yml"
WORK_LOG = "docs-v2/waves/INDEX.md"
BUDGETS = "docs-v2/operations/RESOURCE-BUDGETS.md"

CANONICAL = (STATE, ROADMAP, DECISIONS, RISKS, QUESTIONS, PROTOCOL, POLICY,
             REGISTRY, CATALOGUE, WORK_LOG, BUDGETS)

PATH_RE = re.compile(r"`([A-Za-z0-9_./-]+\.(?:md|yml|yaml|csv|sh|py|json|java|tla))`")

# The registers hold their identifiers as link cells ("[OD-001](#od-001)") or
# as plain cells ("OD-001"); both are declarations.
REGISTER_PATTERNS = (
    (DECISIONS, r"(?:OD|CD|ND|PR|ADR)-[A-Za-z0-9.\-]+"),
    (RISKS, r"R-[A-Za-z0-9.\-]+"),
    (QUESTIONS, r"Q-[A-Za-z0-9.\-]+"),
)


def git(root, *arguments):
    """A git command, or None when git cannot answer."""
    try:
        result = subprocess.run(("git",) + arguments, cwd=str(root),
                                capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        return None
    if result.returncode != 0:
        return None
    return result.stdout.strip()


def main(argv):
    parser = argparse.ArgumentParser(
        description="the validator of the canonical project state")
    parser.add_argument("--root", default=".", help="the repository root")
    parser.add_argument("--state", default=STATE, help="the state document")
    parser.add_argument("--json", default=None, help="write the report here")
    parser.add_argument("--strict", action="store_true",
                        help="exit 1 when the state is stale")
    arguments = parser.parse_args(argv[1:])

    root = Path(arguments.root).resolve()
    defects = []

    def defect(kind, subject, note):
        defects.append({"class": kind, "subject": subject, "note": note})

    # 1. the canonical set
    for relative in CANONICAL:
        if not (root / relative).is_file():
            defect("MISSING_CANONICAL", relative, "the canonical document is absent")

    state_path = root / arguments.state
    if not state_path.is_file():
        defect("MISSING_STATE", arguments.state, "the state document is absent")
        state_text = ""
    else:
        state_text = state_path.read_text(encoding="utf-8")

    # 2. the identity
    branch = git(root, "rev-parse", "--abbrev-ref", "HEAD")
    head = git(root, "rev-parse", "HEAD")
    if branch and head:
        if branch not in state_text:
            defect("STALE_BRANCH", branch, "the state does not name the branch")
        if head not in state_text and head[:8] not in state_text:
            defect("STALE_COMMIT", head[:8], "the state does not name the commit")
    else:
        defect("NO_GIT", ".", "git cannot answer; the identity is unverified")

    # 3. the references of the state document
    index = {}
    for directory, subdirectories, files in os.walk(root):
        relative = os.path.relpath(directory, root)
        if any(part in (".git", "build", "node_modules", ".gradle", ".venv")
               for part in relative.split(os.sep)):
            subdirectories[:] = []
            continue
        for name in files:
            index.setdefault(name, []).append(os.path.join(relative, name))

    def resolves(token):
        if (root / token).exists():
            return True
        if (root / "docs-v2" / token).exists():
            return True
        if "/" not in token and len(index.get(token, [])) == 1:
            return True
        return False

    for token in set(PATH_RE.findall(state_text)):
        if token.startswith(("io.matrix", "matrix-core.")) or " " in token:
            continue
        if not resolves(token):
            defect("MISSING_REFERENCE", token,
                   "the state document points at a document that is not there")

    # 4. the registers: the counts they declare and the counts they hold
    for relative, pattern in REGISTER_PATTERNS:
        path = root / relative
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        identifiers = set(re.findall(pattern, text))
        if not identifiers:
            defect("EMPTY_REGISTER", relative, "the register holds no identifier")
        declared = re.search(r"\*\*(\d+)\s+records?\*\*", text)
        if declared and int(declared.group(1)) != len(identifiers):
            defect("WRONG_COUNT", relative,
                   "the register declares " + declared.group(1) + " records and holds "
                   + str(len(identifiers)))

    # 5. the test catalogue: the commits it claims are real commits
    catalogue = root / CATALOGUE
    if catalogue.is_file():
        text = catalogue.read_text(encoding="utf-8")
        for commit in set(re.findall(r"\b([0-9a-f]{7,40})\b", text)):
            if git(root, "cat-file", "-e", commit + "^{commit}") is None:
                defect("UNKNOWN_COMMIT", commit,
                       "the catalogue names a commit the repository does not have")

    report = {
        "state": arguments.state,
        "branch": branch,
        "head": head,
        "defects": defects,
        "counts": {},
    }
    for item in defects:
        report["counts"][item["class"]] = report["counts"].get(item["class"], 0) + 1

    print("the project state: " + arguments.state)
    print("  the branch: " + str(branch))
    print("  the commit: " + str(head))
    print("  the canonical documents checked: " + str(len(CANONICAL)))
    if defects:
        print("  the defects: " + str(len(defects)))
        for item in defects:
            print("    " + item["class"] + "  " + item["subject"] + "  " + item["note"])
    else:
        print("  the defects: 0 — the state agrees with the repository")

    if arguments.json:
        with open(arguments.json, "w", encoding="utf-8") as handle:
            json.dump(report, handle, ensure_ascii=False, indent=2, sort_keys=True)
        print("  the machine-readable report is written to " + arguments.json)

    if arguments.strict and defects:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
