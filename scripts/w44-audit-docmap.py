#!/usr/bin/env python3
"""W44.0 — read-only test map and document map generator.

Companion to `w44-audit-inventory.py`. It writes two more matrices of the
RECON-W44.0 audit:

  W44-TEST-MAP.csv  every test class, the production types it exercises, the
                    benchmark it belongs to, and the EXECUTED result taken from
                    the JUnit XML of a real run. A test that was never executed
                    is reported as `not-executed`, never as passing.

  W44-DOC-MAP.csv   every Markdown document, its category, its heading, how
                    many distinct production types it names, how many capability
                    claims it makes, and which source files it points at.

It reads only; it writes only under `docs-v2/engineering/`.
"""

from __future__ import annotations

import csv
import json
import os
import re
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs-v2" / "engineering"

EXCLUDED_DIR_NAMES = {
    ".git", "build", ".gradle", "node_modules", ".venv", "venv", "__pycache__",
    ".idea", ".vscode", "target", "out", ".mypy_cache", ".pytest_cache", ".next",
    ".turbo", "dist", ".opencode", ".codex", ".veai", ".swarm", "site-packages",
}
EXCLUDED_PATH_PREFIXES = {"data", "models", "logs", "back-ref", "fwd-ref",
                          "defined-elsewhere"}

PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)
TYPE_RE = re.compile(r"^\s*(?:(?:public|protected|private|static|final|abstract|sealed)\s+)*"
                     r"(class|interface|record|enum|@interface)\s+(\w+)", re.M)
IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w.]+)\s*;", re.M)

CLAIM_WORDS = re.compile(
    r"\b(can|provides?|supports?|implements?|enables?|offers?|exposes?|"
    r"handles?|manages?|tracks?|measures?|guarantees?|owns?|performs?)\b",
    re.I)

# document category by path, most specific first
CATEGORY_RULES = [
    ("docs-v2/engineering/ADR", "adr"),
    ("docs-v2/engineering/ADR-", "adr"),
    ("docs-v2/designs/DESIGN", "design"),
    ("docs-v2/research/HYPOTHES", "hypothesis"),
    ("docs-v2/research/", "research"),
    ("docs-v2/engineering/", "engineering"),
    ("docs-v2/architecture/", "architecture"),
    ("docs-v2/specs/", "specification"),
    ("formal/", "formal-specification"),
    ("docs/", "documentation"),
    ("wal/", "session-history"),
]


def classify(path: str) -> str:
    for prefix, cat in CATEGORY_RULES:
        if path.startswith(prefix):
            return cat
    name = Path(path).name.lower()
    if name in ("readme.md", "contributing.md", "install.md", "migration.md"):
        return "operational-manual"
    if name in ("consti*tution.md", "agents.md", "security", "code_of_conduct"):
        return "normative"
    if name in ("session.md", "_template.md", "access_summary.md"):
        return "session-report"
    if name.startswith("w") and name.endswith("-report.md"):
        return "session-report"
    return "other"


def walk_files():
    for dirpath, dirnames, filenames in os.walk(ROOT):
        rel = Path(dirpath).relative_to(ROOT)
        top = rel.parts[0] if rel.parts else ""
        dirnames[:] = [d for d in dirnames
                       if d not in EXCLUDED_DIR_NAMES and not d.startswith(".")]
        if top in EXCLUDED_PATH_PREFIXES:
            dirnames[:] = []
            continue
        for fn in filenames:
            yield Path(dirpath) / fn


def module_of(path: Path) -> str:
    parts = path.relative_to(ROOT).parts
    if parts[0] == "pilots" and len(parts) > 1:
        return "pilots/" + parts[1]
    return parts[0]


def source_set_of(path: Path) -> str:
    parts = path.relative_to(ROOT).parts
    if "src" in parts:
        i = parts.index("src")
        if i + 1 < len(parts):
            return parts[i + 1]
    return "other"


def main() -> int:
    # ---- executed test evidence -----------------------------------------
    executed: dict[str, dict] = {}
    for module in ("matrix-core", "matrix-brain-runtime", "matrix-api-gateway",
                   "matrix-quality", "matrix-audit", "matrix-billing",
                   "matrix-observability", "matrix-sdk-java", "matrix-operator",
                   "matrix-spigot", "matrix-tools-distill",
                   "pilots/smart-home-agent", "pilots/edu-assessor",
                   "pilots/compliance-bot"):
        results = ROOT / module / "build" / "test-results" / "test"
        if not results.exists():
            continue
        for xml in results.glob("TEST-*.xml"):
            try:
                root = ET.parse(xml).getroot()
            except ET.ParseError:
                continue
            name = root.get("name", "")
            executed[f"{module}:{name}"] = {
                "tests": root.get("tests", "0"),
                "failures": root.get("failures", "0"),
                "errors": root.get("errors", "0"),
                "skipped": root.get("skipped", "0"),
                "time": root.get("time", ""),
                "file": str(xml.relative_to(ROOT)),
            }

    # ---- production type index ------------------------------------------
    prod_types: dict[str, str] = {}   # fqn -> module
    prod_files: list[tuple[str, str]] = []  # (fqn, file)
    for p in walk_files():
        if p.suffix != ".java":
            continue
        if source_set_of(p) != "main":
            continue
        text = p.read_text(encoding="utf-8", errors="replace")
        pkg = PACKAGE_RE.search(text)
        pkg = pkg.group(1) if pkg else ""
        for m in TYPE_RE.finditer(text):
            fqn = f"{pkg}.{m.group(2)}" if pkg else m.group(2)
            prod_types.setdefault(fqn, module_of(p))
            prod_files.append((fqn, str(p.relative_to(ROOT))))
    # longest-first so that a nested class matches before its outer class
    sorted_fqns = sorted(prod_types, key=len, reverse=True)

    # ---- test map -------------------------------------------------------
    rows = []
    for p in sorted(walk_files()):
        if p.suffix != ".java":
            continue
        src_set = source_set_of(p)
        if src_set not in ("test", "jmh", "integrationTest"):
            continue
        text = p.read_text(encoding="utf-8", errors="replace")
        pkg_m = PACKAGE_RE.search(text)
        pkg = pkg_m.group(1) if pkg_m else ""
        t_m = TYPE_RE.search(text)
        cls = f"{pkg}.{t_m.group(2)}" if (pkg and t_m) else (t_m.group(2) if t_m else p.stem)
        referenced = set()
        for fqn in sorted_fqns:
            if fqn in text:
                referenced.add(fqn)
        for imp in IMPORT_RE.findall(text):
            if imp in prod_types:
                referenced.add(imp)
        key = f"{module_of(p)}:{cls}"
        ex = executed.get(key, {})
        rows.append({
            "test_class": cls,
            "file": str(p.relative_to(ROOT)),
            "module": module_of(p),
            "source_set": src_set,
            "kind": "benchmark" if src_set == "jmh" or "Benchmark" in cls
                    or "JMH" in text[:400] else "test",
            "executed": "yes" if ex else "not-executed",
            "tests_run": ex.get("tests", "0"),
            "failures": ex.get("failures", "0"),
            "errors": ex.get("errors", "0"),
            "skipped": ex.get("skipped", "0"),
            "seconds": ex.get("time", ""),
            "evidence_xml": ex.get("file", ""),
            "production_types_referenced": len(referenced),
            "production_types": ";".join(sorted(referenced)[:12]),
        })

    fields = ["test_class", "file", "module", "source_set", "kind", "executed",
              "tests_run", "failures", "errors", "skipped", "seconds",
              "evidence_xml", "production_types_referenced", "production_types"]
    with (OUT / "W44-TEST-MAP.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields)
        w.writeheader()
        for r in sorted(rows, key=lambda x: (x["module"], x["test_class"])):
            w.writerow(r)

    # ---- document map ----------------------------------------------------
    doc_rows = []
    for p in sorted(walk_files()):
        if p.suffix != ".md":
            continue
        rel = str(p.relative_to(ROOT))
        text = p.read_text(encoding="utf-8", errors="replace")
        lines = text.splitlines()
        heading = ""
        for line in lines:
            if line.startswith("# "):
                heading = line[2:].strip()
                break
        if not heading:
            for line in lines:
                if line.startswith("#"):
                    heading = line.lstrip("#").strip()
                    break
        claims = sum(1 for line in lines if CLAIM_WORDS.search(line) and len(line) > 40)
        named = [fqn for fqn in sorted_fqns if fqn in text]
        # also count references to production source files by path
        files_named = set(re.findall(r"([\w./-]+\.java)", text))
        doc_rows.append({
            "document": rel,
            "category": classify(rel),
            "heading": heading[:160],
            "lines": len(lines),
            "bytes": len(text.encode("utf-8")),
            "capability_claim_sentences": claims,
            "production_types_named": len(named),
            "production_types": ";".join(named[:8]),
            "source_files_named": len(files_named),
            "source_files": ";".join(sorted(files_named)[:8]),
        })

    dfields = ["document", "category", "heading", "lines", "bytes",
               "capability_claim_sentences", "production_types_named",
               "production_types", "source_files_named", "source_files"]
    with (OUT / "W44-DOC-MAP.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=dfields)
        w.writeheader()
        for r in sorted(doc_rows, key=lambda x: x["document"]):
            w.writerow(r)

    print(f"test map: {len(rows)} test classes "
          f"({sum(1 for r in rows if r['executed'] == 'yes')} with executed evidence)")
    print(f"doc map: {len(doc_rows)} markdown documents")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
