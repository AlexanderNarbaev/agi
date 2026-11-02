#!/usr/bin/env python3
"""Reproducible integration census for the MATRIX integration matrix.

What it is for
--------------
The integration matrix (``docs-v2/engineering/W42-INTEGRATION-MATRIX.md``)
records, per component, whether it is really wired into a workflow. Its
status column was hand-counted, and a hand-counted number goes stale the
moment the next wave lands: W42.7 wired two shadow classes and the matrix
still called them ``DISCONNECTED``.

This script makes the countable part of the matrix reproducible. For every
component the matrix names, it counts:

  * the production files that mention the name -- the ``src/main`` trees of
    every module, EXCLUDING the component's own file;
  * the test files that mention the name.

The counts are EXACTLY defined: a file counts once per component when the
component's name occurs anywhere in it. Nothing is inferred from a call
graph, because a call graph would need a parser and a parser would make
its own guesses.

What it is NOT
--------------
A count of production files is not a count of production CALL SITES, and
neither is a judgement about whether the result is consumed. The matrix's
``INTEGRATED`` and ``PRODUCTION_PATH_VERIFIED`` statuses stay ASSERTED,
with their evidence -- the script supplies the countable evidence, and a
reader who wants the judgement looks at the cited lines.

Usage
-----
  scripts/integration-census.py                 # the whole matrix
  scripts/integration-census.py ErrorDrivenLearner ConsciousBrain
  scripts/integration-census.py --markdown      # the table, for a report

Exit status is 0 when the census ran. It does not fail the build: the
matrix's statuses are judgements, and a census cannot make a judgement.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
MATRIX = REPO_ROOT / "docs-v2/engineering/W42-INTEGRATION-MATRIX.md"
SOURCE_ROOTS = [
    REPO_ROOT / "matrix-core/src/main/java",
    REPO_ROOT / "matrix-core/src/main/resources",
    REPO_ROOT / "matrix-brain-runtime/src/main/java",
    REPO_ROOT / "matrix-api-gateway/src/main/java",
    REPO_ROOT / "matrix-audit/src/main/java",
    REPO_ROOT / "matrix-ethics/src/main/java",
]
TEST_ROOTS = [
    REPO_ROOT / "matrix-core/src/test/java",
    REPO_ROOT / "matrix-brain-runtime/src/test/java",
    REPO_ROOT / "matrix-api-gateway/src/test/java",
    REPO_ROOT / "matrix-audit/src/test/java",
    REPO_ROOT / "matrix-ethics/src/test/java",
]
# The name a component's own file carries: a file named after the
# component does not count as a reference to it.
FILE_NAME = re.compile(r"([A-Za-z0-9_]+)\.(java|kt|ts|js|html|json)$")


# The matrix's own status vocabulary. A row that starts with one of these
# is a row ABOUT a status, not a row ABOUT a component.
STATUS_NAMES = {
    "EXISTS",
    "TESTED",
    "INTEGRATED",
    "PRODUCTION_PATH_VERIFIED",
    "EXPERIMENTAL",
    "DISCONNECTED",
    "UNVERIFIED",
}


def matrix_components() -> list[str]:
    """The component names the matrix names, in the order it names them."""
    if not MATRIX.exists():
        return []
    text = MATRIX.read_text(encoding="utf-8", errors="replace")
    names: list[str] = []
    seen: set[str] = set()
    for line in text.splitlines():
        if not line.startswith("| `"):
            continue
        for name in re.findall(r"`([A-Za-z][A-Za-z0-9_.]*)`", line):
            if name in STATUS_NAMES or name in seen:
                continue
            seen.add(name)
            names.append(name)
    return names


def source_files(roots: list[Path]) -> list[Path]:
    files: list[Path] = []
    for root in roots:
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if path.is_file() and path.suffix in {".java", ".kt", ".ts", ".js", ".html", ".json"}:
                files.append(path)
    return files


def census(name: str, files: list[Path]) -> list[Path]:
    """The files that mention the name, excluding the component's own file."""
    own = FILE_NAME.match(name)
    own_stem = own.group(1) if own else name
    pattern = re.compile(r"\b" + re.escape(name) + r"\b")
    hits: list[Path] = []
    for path in files:
        if path.stem == own_stem:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        if pattern.search(text):
            hits.append(path)
    return hits


def relative(path: Path) -> str:
    try:
        return str(path.relative_to(REPO_ROOT))
    except ValueError:
        return str(path)


def main(argv: list[str]) -> int:
    as_markdown = "--markdown" in argv
    wanted = [a for a in argv if not a.startswith("-")]

    production = source_files(SOURCE_ROOTS)
    tests = source_files(TEST_ROOTS)

    if wanted:
        names = wanted
    else:
        names = matrix_components()

    if not names:
        print("no components were found", file=sys.stderr)
        return 1

    rows = []
    for name in names:
        prod = census(name, production)
        test = census(name, tests)
        rows.append((name, prod, test))

    if as_markdown:
        print("| the component | the production files | the test files | the production sites |")
        print("|---|---|---|---|")
        for name, prod, test in rows:
            sites = "<br>".join(relative(p) for p in prod) if prod else "—"
            print(f"| `{name}` | {len(prod)} | {len(test)} | {sites} |")
        return 0

    for name, prod, test in rows:
        print(f"{name}: {len(prod)} production file(s), {len(test)} test file(s)")
        for path in prod:
            print(f"    {relative(path)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
