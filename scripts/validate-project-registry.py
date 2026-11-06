#!/usr/bin/env python3
"""Validate the canonical capability registry of MATRIX.

**What it checks** — the three rules the registry itself promises, and the
schema that carries them:

1. every record has every field, and every path it names exists;
2. every status is in the declared vocabulary of its axis;
3. a record may be `SERVED` only if it names a serving path, and `MEASURED`
   only if it names a benchmark.

**How it runs** — deterministic, offline, read-only. It writes nothing except
the JSON report it is asked for.

    python3 scripts/validate-project-registry.py
    python3 scripts/validate-project-registry.py --json /tmp/registry.json
    python3 scripts/validate-project-registry.py --strict

The report mode never fails the build (exit 0); the strict mode exits 1 when
the registry violates one of its own rules.

**Dependency:** PyYAML. If it is missing, the tool says so and exits 2 — it
does not silently pass.
"""

import argparse
import json
import os
import sys
from pathlib import Path

REGISTRY = "docs-v2/architecture/CAPABILITY-REGISTRY.yml"

# The fields every record carries. The registry declares them in its own
# header; this list is the validator's copy of that contract.
REQUIRED_FIELDS = (
    "id", "name", "description", "domain",
    "implementation", "integration", "verification", "operational",
    "serving_chain",
    "source_paths", "production_callers", "serving_path",
    "test_paths", "test_evidence", "benchmark_evidence",
    "formal_spec", "dependencies", "limitations", "risks",
    "owner", "decision_ref", "last_verified_commit", "last_verified_at",
    "next_verification_condition",
)

# The placeholders that mean "there is no path", and the tokens that mean
# "this is not a path at all".
NOT_A_PATH = ("n/a", "none", "absent", "present", "-", "—", "")
PATH_SUFFIXES = (".java", ".kt", ".yml", ".yaml", ".csv", ".json", ".md",
                 ".sh", ".py", ".tla", ".properties", ".txt", ".gradle")


def paths_in(cell):
    """The path-like tokens of a cell.

    A cell is either a list of paths, or a sentence that names a path among
    other things ("the gate — scripts/formal-gate.sh"). Only the tokens that
    look like a repository path are returned; the prose around them is not a
    path and must not be resolved.
    """
    if cell is None:
        return []
    tokens = cell if isinstance(cell, list) else [cell]
    found = []
    for token in tokens:
        text = str(token).strip()
        if not text or text in NOT_A_PATH:
            continue
        if "<" in text or "{" in text or "*" in text:
            continue
        if " " in text:
            # A sentence: only its fragments can be paths. The sentence
            # itself is prose, and resolving prose produces nonsense.
            for fragment in text.replace(";", " ").replace(" — ", " ").split():
                fragment = fragment.strip(",;:()[]")
                if is_a_path(fragment):
                    found.append(fragment)
        elif is_a_path(text):
            found.append(text)
    return found


def is_a_path(token):
    """Whether a token names a file that could exist in the repository."""
    text = str(token).strip()
    if not text or text in NOT_A_PATH:
        return False
    if "<" in text or "{" in text or "*" in text:
        return False
    if text.startswith("io.matrix"):
        return False
    name = text.split("/")[-1]
    if name.startswith(".") and "." not in name[1:]:
        # ".tla" is an extension, not a file name
        return False
    if "." not in name:
        return False
    return text.endswith(PATH_SUFFIXES)


def build_index(root):
    """Every file of the repository, by its bare name."""
    index = {}
    skip = (".git", "build", "node_modules", ".gradle", ".venv", "target")
    for directory, subdirectories, files in os.walk(root):
        relative = os.path.relpath(directory, root)
        if any(part in skip for part in relative.split(os.sep)):
            subdirectories[:] = []
            continue
        for name in files:
            index.setdefault(name, []).append(os.path.join(relative, name))
    return index


def resolve(root, document, token, index):
    """Whether a path resolves, under the honest readings.

    A path may be written relative to the repository root, relative to the
    document, or relative to `docs-v2` — and a bare file name may name a file
    anywhere in the repository. A name that several files share is not
    resolved: the reference is ambiguous, and an ambiguous reference is not a
    broken one.
    """
    text = str(token).strip()
    if text.startswith("/"):
        return (root / text.lstrip("/")).exists()
    for candidate in (
        root / text,
        document.parent / text,
        root / "docs-v2" / text,
        root / "matrix-core" / text,
    ):
        if candidate.exists():
            return True
    if "/" not in text:
        matches = index.get(text, [])
        if len(matches) == 1:
            return True
    return False


def main(argv):
    parser = argparse.ArgumentParser(
        description="the validator of the canonical capability registry")
    parser.add_argument("--root", default=".", help="the repository root")
    parser.add_argument("--registry", default=REGISTRY,
                        help="the registry, relative to the root")
    parser.add_argument("--json", default=None,
                        help="write the machine-readable report here")
    parser.add_argument("--strict", action="store_true",
                        help="exit 1 when the registry violates its own rules")
    arguments = parser.parse_args(argv[1:])

    root = Path(arguments.root).resolve()
    path = root / arguments.registry
    if not path.is_file():
        print("the registry is not there: " + str(path))
        return 2
    try:
        import yaml
    except ImportError:
        print("PyYAML is not installed; the registry cannot be read. "
              "Install it (python3 -m pip install pyyaml) or use the venv.")
        return 2

    with path.open(encoding="utf-8") as handle:
        registry = yaml.safe_load(handle)

    index = build_index(root)
    capabilities = registry.get("capabilities") or []
    vocabularies = registry.get("status_vocabularies") or {}
    defects = []

    def defect(kind, subject, note):
        defects.append({"class": kind, "subject": subject, "note": note})

    seen_ids = {}
    for record in capabilities:
        identifier = str(record.get("id", "<no id>"))
        if identifier in seen_ids:
            defect("DUPLICATE_ID", identifier, "the id is declared twice")
        seen_ids[identifier] = True
        for field in REQUIRED_FIELDS:
            if field not in record:
                defect("MISSING_FIELD", identifier, "the field " + field + " is absent")
        for axis, vocabulary in vocabularies.items():
            value = record.get(axis)
            if value is None:
                continue
            allowed = vocabulary if isinstance(vocabulary, list) else vocabulary.keys()
            if value not in allowed:
                defect("ILLEGAL_STATUS", identifier,
                       axis + " = " + str(value) + " is not in the vocabulary")
        if record.get("operational") == "SERVED":
            serving = str(record.get("serving_path") or "").strip()
            if not serving or serving in NOT_A_PATH:
                defect("SERVED_WITHOUT_A_PATH", identifier,
                       "the record is SERVED but names no serving path")
        if record.get("verification") == "MEASURED":
            benchmark = str(record.get("benchmark_evidence") or "").strip()
            if not benchmark or benchmark in NOT_A_PATH:
                defect("MEASURED_WITHOUT_A_BENCHMARK", identifier,
                       "the record is MEASURED but names no benchmark evidence")
        if record.get("verification") == "FORMALLY_VERIFIED":
            formal = str(record.get("formal_spec") or "").strip()
            if not formal or formal in NOT_A_PATH:
                defect("FORMALLY_VERIFIED_WITHOUT_A_SPEC", identifier,
                       "the record is FORMALLY_VERIFIED but names no specification")
        for field in ("source_paths", "test_paths", "production_callers",
                      "benchmark_evidence", "formal_spec", "decision_ref"):
            for token in paths_in(record.get(field)):
                if not resolve(root, path, token, index):
                    defect("MISSING_PATH", identifier,
                           field + ": " + str(token))

    report = {
        "registry": arguments.registry,
        "capabilities": len(capabilities),
        "fields_per_capability": len(REQUIRED_FIELDS),
        "defects": defects,
        "counts": {},
    }
    for item in defects:
        report["counts"][item["class"]] = report["counts"].get(item["class"], 0) + 1

    print("the registry: " + arguments.registry)
    print("  the capabilities: " + str(len(capabilities)))
    print("  the fields per record: " + str(len(REQUIRED_FIELDS)))
    if defects:
        print("  the defects: " + str(len(defects)))
        for item in defects:
            print("    " + item["class"] + "  " + item["subject"] + "  " + item["note"])
    else:
        print("  the defects: 0 — the registry holds its own rules")

    if arguments.json:
        with open(arguments.json, "w", encoding="utf-8") as handle:
            json.dump(report, handle, ensure_ascii=False, indent=2, sort_keys=True)
        print("  the machine-readable report is written to " + arguments.json)

    if arguments.strict and defects:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
