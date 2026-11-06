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

  * the production files that MENTION the name -- the ``src/main`` trees of
    every module, EXCLUDING the component's own file;
  * the production files that CONSTRUCT it -- a file counts when the text
    ``new <Name>(`` occurs in it;
  * the production files that CALL it -- a file counts when the text
    ``<Name>.<method>(`` or ``new <Name>(`` occurs in it;
  * the test files that mention the name.

A matrix row may name a class with a method (``ErrorDrivenLearner.applyDecay``).
For such a name the census counts the FULL name first and falls back to
the class part, and it says which of the two it counted.

The counts are EXACTLY defined: a file counts once per component when the
component's name occurs anywhere in it, and a construction counts when
``new <Name>(`` occurs in it. Nothing is inferred from a call graph,
because a call graph would need a parser and a parser would make its own
guesses.

WHY THE CONSTRUCTION COUNT IS SEPARATE. A mention is not a use: a
javadoc link, a comment and a call all count as a mention, and the matrix's
``DISCONNECTED`` rows are about CONSTRUCTION SITES. Counting the
constructions separately is what makes the matrix's claims comparable with
the census: a class the matrix calls disconnected must have zero
constructions outside its own file.

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


def own_stem_of(name: str) -> str:
    """The name a component's own file carries."""
    own = FILE_NAME.match(name)
    return own.group(1) if own else name


def census(name: str, files: list[Path]) -> list[Path]:
    """The files that mention the name, excluding the component's own file."""
    own_stem = own_stem_of(name)
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


def constructions(name: str, files: list[Path]) -> list[Path]:
    """The files that CONSTRUCT the name, excluding its own file.

    <p>A construction is the text ``new <Name>(``. The matrix's claims
    about construction sites are comparable with this count, and a class
    the matrix calls disconnected must have zero of them.
    """
    own_stem = own_stem_of(name)
    pattern = re.compile(r"\bnew\s+" + re.escape(name) + r"\s*\(")
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


def call_sites(name: str, files: list[Path]) -> list[Path]:
    """The files that CALL the name, excluding its own file.

    <p>A call is the text ``<Name>.<method>(`` or ``new <Name>(``. The
    javadoc ``{@link Name}`` is not a call, and neither is a mention.
    """
    own_stem = own_stem_of(name)
    pattern = re.compile(r"\b" + re.escape(name)
                         + r"\s*(?:\.\s*\w+\s*\(|\s*\()")
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
        counted = name
        built = constructions(name, production)
        calls = call_sites(name, production)
        if not built and not calls and "." in name:
            # The row names a class WITH A METHOD. The full name was
            # counted first; the class part is the fallback.
            counted = name.split(".")[0]
            built = constructions(counted, production)
            calls = call_sites(counted, production)
        prod = census(name, production)
        test = census(name, tests)
        rows.append((name, counted, prod, built, calls, test))

    if as_markdown:
        print("| the component | the counted as | the mentions | the constructions "
              "| the calls | the test files | the construction sites |")
        print("|---|---|---|---|---|---|---|")
        for name, counted, prod, built, calls, test in rows:
            sites = "<br>".join(relative(p) for p in built) if built else "—"
            print(f"| `{name}` | `{counted}` | {len(prod)} | {len(built)} | {len(calls)} | "
                  f"{len(test)} | {sites} |")
        return 0

    for name, counted, prod, built, calls, test in rows:
        print(f"{name}: counted as {counted} — {len(prod)} mention(s), "
              f"{len(built)} construction(s), {len(calls)} call file(s), "
              f"{len(test)} test file(s)")
        for path in built:
            print(f"    constructed in {relative(path)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
