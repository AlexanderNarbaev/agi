#!/usr/bin/env python3
"""Generate the project report of MATRIX from the canonical documents.

**What it is** — the one command that answers "what is the state of the
project?" without reading ten documents: it reads the canonical set and
prints what each of them says, with the counts it holds.

**What it does not do** — it does not decide, and it does not invent. Every
number in the report is counted from a canonical document at the moment the
report is generated, and the report says which document each number came
from.

    python3 scripts/generate-project-report.py
    python3 scripts/generate-project-report.py --json /tmp/report.json
    python3 scripts/generate-project-report.py --out docs-v2/engineering/

It writes nothing except where it is told to, and it never touches the
repository's data, its databases or its history.
"""

import argparse
import json
import subprocess
import sys
from collections import Counter
from pathlib import Path

STATE = "docs-v2/governance/PROJECT-STATE.md"
ROADMAP = "docs-v2/governance/ROADMAP.md"
DECISIONS = "docs-v2/governance/DECISION-REGISTER.md"
RISKS = "docs-v2/governance/RISK-REGISTER.md"
QUESTIONS = "docs-v2/governance/OPEN-QUESTIONS.md"
REGISTRY = "docs-v2/architecture/CAPABILITY-REGISTRY.yml"
CATALOGUE = "docs-v2/engineering/TEST-CATALOGUE.yml"
BUDGETS = "docs-v2/operations/RESOURCE-BUDGETS.md"


def git(root, *arguments):
    try:
        result = subprocess.run(("git",) + arguments, cwd=str(root),
                                capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        return None
    return result.stdout.strip() if result.returncode == 0 else None


def read(root, relative):
    path = root / relative
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def count(text, pattern):
    return len(set(__import__("re").findall(pattern, text)))


def main(argv):
    parser = argparse.ArgumentParser(
        description="the project report of MATRIX, generated from the canonical set")
    parser.add_argument("--root", default=".", help="the repository root")
    parser.add_argument("--json", default=None, help="write the report here")
    parser.add_argument("--out", default=None,
                        help="write the report into this directory as "
                             "PROJECT-REPORT.md")
    arguments = parser.parse_args(argv[1:])

    root = Path(arguments.root).resolve()
    report = {}

    report["identity"] = {
        "branch": git(root, "rev-parse", "--abbrev-ref", "HEAD"),
        "head": git(root, "rev-parse", "HEAD"),
        "remote": git(root, "rev-parse", "--abbrev-ref", "--symbolic-full-name",
                      "@{u}"),
        "working_tree": git(root, "status", "--porcelain", "--untracked-files=no"),
    }

    # the capability registry: the distribution on the four axes
    registry = {}
    try:
        import yaml
        registry = yaml.safe_load(read(root, REGISTRY)) or {}
    except ImportError:
        print("PyYAML is not installed; the capability distribution is omitted.")
    capabilities = registry.get("capabilities") or []
    axes = {}
    for axis in ("implementation", "integration", "verification", "operational"):
        counter = Counter(str(record.get(axis)) for record in capabilities)
        axes[axis] = dict(sorted(counter.items()))
    report["capabilities"] = {
        "count": len(capabilities),
        "axes": axes,
        "serving_chain": dict(sorted(Counter(
            str(record.get("serving_chain")) for record in capabilities).items())),
        "source": REGISTRY,
    }

    # the registers
    decisions = read(root, DECISIONS)
    risks = read(root, RISKS)
    questions = read(root, QUESTIONS)
    report["decisions"] = {
        "owner": count(decisions, r"\|\s*(?:OD)-[A-Za-z0-9.\-]+\s*\|"),
        "coordinator": count(decisions, r"\|\s*(?:CD)-[A-Za-z0-9.\-]+\s*\|"),
        "normative": count(decisions, r"\|\s*(?:ND)-[A-Za-z0-9.\-]+\s*\|"),
        "proposals": count(decisions, r"\|\s*(?:PR)-[A-Za-z0-9.\-]+\s*\|"),
        "adrs": count(decisions, r"\|\s*(?:ADR)-[A-Za-z0-9.\-]+\s*\|"),
        "source": DECISIONS,
    }
    report["risks"] = {"count": count(risks, r"R-W46-[0-9]+"), "source": RISKS}
    report["questions"] = {"count": count(questions, r"Q-[A-Za-z0-9.\-]+"),
                           "source": QUESTIONS}

    # the roadmap
    roadmap = read(root, ROADMAP)
    report["roadmap"] = {
        "tasks": count(roadmap, r"TASK-[A-Za-z0-9.\-]+"),
        "source": ROADMAP,
    }

    # the test catalogue
    catalogue = {}
    try:
        import yaml
        catalogue = yaml.safe_load(read(root, CATALOGUE)) or {}
    except ImportError:
        pass
    report["tests"] = {
        "scopes": len(catalogue.get("scopes") or []),
        "not_runnable_here": len(catalogue.get("scopes_not_runnable_here") or []),
        "source": CATALOGUE,
    }

    # the human-readable report
    lines = ["# MATRIX — PROJECT REPORT", "",
             "**Generated:** by `scripts/generate-project-report.py` from the canonical set. "
             "**Every number is counted from the document named beside it.**", "",
             "## The identity", "",
             "| | |", "|---|---|",
             "| the branch | `" + str(report["identity"]["branch"]) + "` |",
             "| the commit | `" + str(report["identity"]["head"]) + "` |",
             "| the upstream | `" + str(report["identity"]["remote"]) + "` |",
             "| the working tree (tracked changes) | "
             + ("clean" if not report["identity"]["working_tree"]
                else "modified files — see `git status`") + " |",
             "", "## The capabilities", "",
             "**" + str(report["capabilities"]["count"]) + " records** — "
             + "the source is `" + REGISTRY + "`.", "",
             "| the axis | the distribution |", "|---|---|"]
    for axis, distribution in axes.items():
        lines.append("| " + axis + " | "
                     + ", ".join(str(value) + " × " + str(count)
                                 for value, count in distribution.items()) + " |")
    lines += ["", "## The registers", "",
              "| the register | the records | the source |", "|---|---|---|",
              "| the owner decisions | " + str(report["decisions"]["owner"])
              + " | `" + DECISIONS + "` |",
              "| the coordinator decisions | " + str(report["decisions"]["coordinator"])
              + " | `" + DECISIONS + "` |",
              "| the normative decisions | " + str(report["decisions"]["normative"])
              + " | `" + DECISIONS + "` |",
              "| the proposals | " + str(report["decisions"]["proposals"])
              + " | `" + DECISIONS + "` |",
              "| the ADRs | " + str(report["decisions"]["adrs"])
              + " | `" + DECISIONS + "` |",
              "| the risks | " + str(report["risks"]["count"])
              + " | `" + RISKS + "` |",
              "| the open questions | " + str(report["questions"]["count"])
              + " | `" + QUESTIONS + "` |",
              "| the roadmap tasks | " + str(report["roadmap"]["tasks"])
              + " | `" + ROADMAP + "` |",
              "", "## The tests", "",
              "| the scopes | " + str(report["tests"]["scopes"]) + " |",
              "| the scopes that cannot run here | "
              + str(report["tests"]["not_runnable_here"]) + " |",
              "", "**The state document:** `" + STATE + "` · "
              "**the resource budgets:** `" + BUDGETS + "`", ""]

    text = "\n".join(lines)
    print(text)
    if arguments.out:
        target = Path(arguments.out) / "PROJECT-REPORT.md"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")
        print("the report is written to " + str(target))
    if arguments.json:
        with open(arguments.json, "w", encoding="utf-8") as handle:
            json.dump(report, handle, ensure_ascii=False, indent=2, sort_keys=True)
        print("the machine-readable report is written to " + arguments.json)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
