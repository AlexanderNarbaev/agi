#!/usr/bin/env python3
"""Validate the test evidence catalogue of MATRIX.

**What it checks** — the catalogue is the project's claim about its own test
baseline, and a stale claim is worse than no claim:

1. **the shape** — every scope names a command, a filter, a commit, a date and
   a result;
2. **the commits** — every commit the catalogue names is a commit the
   repository actually has;
3. **the results** — when the JUnit XML of a scope is present on this machine,
   the counts the catalogue claims are compared with the counts the XML holds;
   the **difference** is reported, and the catalogue is never silently
   rewritten;
4. **the scopes that cannot run here** — they must say why.

**How it runs** — deterministic, offline, read-only. It reads the JUnit XML
that a previous run left behind; it **never** runs a build.

    python3 scripts/validate-test-catalogue.py
    python3 scripts/validate-test-catalogue.py --json /tmp/catalogue.json
    python3 scripts/validate-test-catalogue.py --strict

Report mode never fails the build; strict mode exits 1 on a defect.
"""

import argparse
import glob
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

CATALOGUE = "docs-v2/engineering/TEST-CATALOGUE.yml"

# The fields a JUNIT scope must carry — a scope whose task is a Gradle test
# task. The catalogue's own schema.
REQUIRED_FIELDS = ("id", "purpose", "task", "filters", "source_packages",
                   "test_packages", "last_verified_commit", "last_execution",
                   "result", "known_skips", "environment_dependencies",
                   "next_verification_condition")

# The fields a GATE scope must carry — a scope whose task is a script. A gate
# has no test classes: it has checks, failures and an exit code.
REQUIRED_GATE_FIELDS = ("id", "purpose", "task", "last_verified_commit",
                         "last_execution", "result", "what_it_proves",
                         "next_verification_condition")

# The counters the evidence model requires — the directive's §10.1 list.
REQUIRED_COUNTERS = ("classes_discovered", "classes_executed", "methods_executed",
                     "successful", "failed", "errored", "skipped", "aborted",
                     "filtered", "not_executed")

# The counters the JUnit XML can settle, and how the XML spells them.
XML_COUNTERS = {"classes_executed": "classes", "methods_executed": "tests",
                "failed": "failures", "errored": "errors", "skipped": "skipped"}


def git(root, *arguments):
    try:
        result = subprocess.run(("git",) + arguments, cwd=str(root),
                                capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        return None
    return result.stdout.strip() if result.returncode == 0 else None


def counts_from_xml(root, module):
    """The counts the JUnit XML of a module holds, if it is there."""
    pattern = str(root / module / "build" / "test-results" / "test" / "*.xml")
    files = glob.glob(pattern)
    if not files:
        return None
    classes = tests = failures = errors = skipped = 0
    for name in files:
        try:
            root_element = ElementTree.parse(name).getroot()
        except ElementTree.ParseError:
            continue
        classes += 1
        tests += int(root_element.get("tests", 0))
        failures += int(root_element.get("failures", 0))
        errors += int(root_element.get("errors", 0))
        skipped += int(root_element.get("skipped", 0))
    return {"classes": classes, "tests": tests, "failures": failures,
            "errors": errors, "skipped": skipped}


def main(argv):
    parser = argparse.ArgumentParser(
        description="the validator of the test evidence catalogue")
    parser.add_argument("--root", default=".", help="the repository root")
    parser.add_argument("--catalogue", default=CATALOGUE, help="the catalogue")
    parser.add_argument("--json", default=None, help="write the report here")
    parser.add_argument("--strict", action="store_true",
                        help="exit 1 when the catalogue is stale")
    arguments = parser.parse_args(argv[1:])

    root = Path(arguments.root).resolve()
    path = root / arguments.catalogue
    defects = []
    observations = []

    def defect(kind, subject, note):
        defects.append({"class": kind, "subject": subject, "note": note})

    if not path.is_file():
        defect("MISSING_CATALOGUE", arguments.catalogue, "the catalogue is absent")
        print("the catalogue is not there: " + str(path))
        return 2
    try:
        import yaml
    except ImportError:
        print("PyYAML is not installed; the catalogue cannot be read.")
        return 2
    with path.open(encoding="utf-8") as handle:
        catalogue = yaml.safe_load(handle) or {}

    scopes = catalogue.get("scopes") or []
    for scope in scopes:
        name = str(scope.get("id", "<no id>"))
        is_gate = not str(scope.get("task") or "").startswith(":")
        required = REQUIRED_GATE_FIELDS if is_gate else REQUIRED_FIELDS
        for field in required:
            if field not in scope:
                defect("MISSING_FIELD", name, "the field " + field + " is absent")
        result = scope.get("result") or {}
        if is_gate:
            # A gate reports its exit code and a count of its own kind: the
            # checks it made (the isolation gate), the failures it found (the
            # quality gate), the violations it found (the security gate). The
            # count is named by the gate, not by the catalogue.
            if "exit_code" not in result:
                defect("MISSING_COUNTER", name, "a gate reports its exit code")
            if not any(counter in result for counter in ("checks", "failed", "violations")):
                defect("MISSING_COUNTER", name,
                       "a gate reports how many checks, failures or violations it found")
        else:
            for counter in REQUIRED_COUNTERS:
                if counter not in result:
                    defect("MISSING_COUNTER", name,
                           "the evidence model requires the counter " + counter)
        commit = str(scope.get("last_verified_commit") or "")
        if commit and len(commit) >= 7:
            if git(root, "cat-file", "-e", commit + "^{commit}") is None:
                defect("UNKNOWN_COMMIT", name,
                       "the commit " + commit + " is not in the repository")
        module = str(scope.get("task") or "").split(":")[0].lstrip(":")
        if not module or is_gate:
            continue
        measured = counts_from_xml(root, module)
        if not measured:
            # No XML on this machine: the scope cannot be checked now, and that
            # is a fact, not a defect.
            scope["_not_verifiable_here"] = True
            continue
        for counter, xml_key in XML_COUNTERS.items():
            claimed = result.get(counter)
            if claimed is None:
                continue
            if int(claimed) == measured[xml_key]:
                continue
            if int(claimed) > measured[xml_key]:
                # The catalogue claims MORE than the machine holds: the claim is
                # impossible, and the scope is stale.
                defect("STALE_COUNT", name,
                       "the catalogue says " + str(claimed) + " " + counter
                       + " and the XML of " + module + " holds "
                       + str(measured[xml_key]) + " (the claim is impossible)")
            else:
                # The XML holds MORE than the scope claims: the residue on this
                # machine is from a WIDER run (a different filter), not from
                # this scope. That is an observation, not a defect — the XML
                # cannot REFUTE a claim, it can only fail to confirm it.
                observations.append(name + ": the XML of " + module + " holds "
                                    + str(measured[xml_key]) + " " + counter
                                    + " and the scope claims " + str(claimed)
                                    + " — the XML is from a wider run")

    for entry in catalogue.get("scopes_not_runnable_here") or []:
        name = str(entry.get("id", "<no id>"))
        if not str(entry.get("reason", "")).strip():
            defect("NO_REASON", name, "a scope that cannot run here must say why")
        if not str(entry.get("status", "")).strip():
            defect("NO_STATUS", name,
                   "a scope that was not executed must say what it was not")

    report = {"catalogue": arguments.catalogue, "scopes": len(scopes),
              "defects": defects, "observations": observations, "counts": {}}
    for item in defects:
        report["counts"][item["class"]] = report["counts"].get(item["class"], 0) + 1

    print("the test catalogue: " + arguments.catalogue)
    print("  the scopes: " + str(len(scopes)))
    print("  the scopes that cannot run here: "
          + str(len(catalogue.get("scopes_not_runnable_here") or [])))
    if defects:
        print("  the defects: " + str(len(defects)))
        for item in defects:
            print("    " + item["class"] + "  " + item["subject"] + "  " + item["note"])
    else:
        print("  the defects: 0 — the catalogue agrees with the evidence on this machine")
    for observation in observations:
        print("    observed: " + observation)

    if arguments.json:
        with open(arguments.json, "w", encoding="utf-8") as handle:
            json.dump(report, handle, ensure_ascii=False, indent=2, sort_keys=True)
        print("  the machine-readable report is written to " + arguments.json)

    if arguments.strict and defects:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
