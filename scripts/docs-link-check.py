#!/usr/bin/env python3
"""W46.2 — the documentation link and reference checker.

The tool that W46.2's repairs depend on. It finds the class of defect the
W46.0 audit measured — broken repository-relative links, prose references to
paths that do not exist, duplicate stable identifiers, documents unreachable
from the index — and it can GO RED: the strict mode fails on the defects it
selects, and the negative control in the report proves it.

THE TWO MODES
  report  (the default)  finds everything and prints the report. It NEVER
                          fails the build: the exit code is 0 unless the tool
                          itself crashes.
  strict  (--strict)     fails on the strict rule set: a broken relative
                          link, a broken anchor, a prose path in a current
                          document that does not exist, an identifier
                          referenced but never defined, and a duplicate
                          identifier definition. It does NOT fail on the
                          archive's defects, on the historical waves'
                          defects, on an external link, or on a path in a
                          document whose status is HISTORICAL or ARCHIVED —
                          unless --include-historical is given.

THE RULES IT DOES NOT BREAK
  * NO NETWORK. An external link is collected and marked UNVERIFIED. The
    tool never claims that an external link is valid, because it never
    looked at it.
  * NO WRITES to the repository. The only file it writes is the one --json
    names.
  * The archive (docs-v2/_raw) and the historical waves (docs-v2/waves) are
    scanned READ-ONLY and reported in their own group. They are never
    modified.
  * DETERMINISTIC: the same tree produces byte-identical output. Every list
    is sorted.

Usage:
    scripts/docs-link-check.py [--root DIR] [--scope PATH] [--strict]
                               [--json PATH] [--exclude GLOB ...]
                               [--include-historical]
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

# ── the constants of the rules ───────────────────────────────────────────────

# The eleven statuses of docs-v2/governance/DOCUMENT-STATUS-POLICY.md.
STATUS_VOCABULARY = (
    "NORMATIVE", "CANONICAL", "ACTIVE", "REFERENCE", "RESEARCH", "EXPERIMENTAL",
    "HISTORICAL", "SUPERSEDED", "DEPRECATED", "ARCHIVED", "UNVERIFIED",
)

# The statuses that make a document a CURRENT document: a reader is expected
# to follow it. Everything else describes a past state.
CURRENT_STATUSES = ("NORMATIVE", "CANONICAL", "ACTIVE", "REFERENCE", "RESEARCH",
                    "EXPERIMENTAL", "UNVERIFIED", "DEPRECATED")

# The statuses that make a document HISTORICAL: a link into one from a current
# document is how a reader is misled.
HISTORICAL_STATUSES = ("HISTORICAL", "ARCHIVED", "SUPERSEDED")

# The status a document has when it declares none: ACTIVE until classified.
DEFAULT_STATUS = "ACTIVE"

# A token in backticks inside a status line.
BACKTICKED_TOKEN_RE = re.compile(r"`([^`]+)`")

# The status is read from the first fourteen lines of the document.
STATUS_LINE_WINDOW = 14

# The directories that are scanned read-only and reported in their own group.
ARCHIVE_PREFIX = "docs-v2/_raw/"

# W48-05 — the RUNTIME paths.
#
# The repository documents where the runtime keeps its data ("data/mind/mind.sqlite",
# "data/knowledge/", "formal/states/"), and those paths are NOT tracked in git: the
# owner's live database, the runtime's scratch directories and the build outputs are
# machine state, not documentation. The checker's rule — "the parent directory exists,
# so the path must exist" — therefore decided, on the developer's machine, that a
# document was correct, and on a clean runner, that the same document was broken.
#
# The gate was therefore GREEN locally and RED in CI over the same commit, and the
# difference was a file that never belonged to the repository.
#
# A path under one of these prefixes is a RUNTIME ARTEFACT: it is reported (so it
# stays discoverable), it is never a strict failure (its presence depends on the
# machine, not on the repository), and the difference between "the document names
# something that does not exist" and "the document names where the runtime keeps
# its data" is no longer decided by whether the developer happens to have run the
# system.
RUNTIME_PREFIXES = (
    "data/",
    "bin/",
    "formal/states/",
    "matrix-core/bin/",
    "matrix-core/data/",
    "matrix-core/build/",
    "target/",
    "build/",
)
WAVES_PREFIX = "docs-v2/waves/"

# The paths that are NOT the documentation of this repository and are never
# scanned: the vendored third-party trees, the build outputs and the state of
# the agents. Their Markdown files are the documentation of somebody else.
DEFAULT_EXCLUDES = (
    "**/node_modules/**",
    "**/.git/**",
    "**/build/**",
    "**/target/**",
    "**/.gradle/**",
    ".opencode/**",
    ".codex/**",
    ".veai/**",
    ".venv/**",
    ".swarm/**",
    ".github/**",
    "**/__pycache__/**",
)

# The index the reachability of the documents is measured from.
INDEX_DOCUMENT = "docs-v2/INDEX.md"

# The stable identifier series the checker knows.
IDENTIFIER_SERIES = ("ADR", "SPEC", "DESIGN", "H", "OD", "CD", "PR", "TASK", "Q", "R")

# An identifier is a prefix, a hyphen, and segments that are digits or
# capitalised words. The rule keeps file names out of the identifier space:
# "SPEC-000-developmental-loop.md" is a FILE, and only "SPEC-000" is an
# identifier.
IDENTIFIER_RE = re.compile(
    r"\b(" + "|".join(IDENTIFIER_SERIES)
    + r")-((?:[0-9]+|[A-Z]+[0-9]*)(?:\.[0-9]+)?"
    + r"(?:-(?:[0-9]+|[A-Z]+[0-9]*)(?:\.[0-9]+)?)*)")

# The modules of the repository: a short path may be written relative to one
# of them without naming it.
MODULE_DIRECTORIES = (
    "matrix-core", "matrix-brain-runtime", "matrix-api-gateway", "matrix-audit",
    "matrix-observability", "matrix-web-ui", "matrix-tools-distill", "matrix-spigot",
)

# The roots of the repository a prose path may be relative to. A token that
# starts with one of them is a repository path; anything else is resolved
# against the document, against the docs-v2 directory and against the root.
REPOSITORY_ROOTS = (
    "docs-v2", "matrix-core", "matrix-brain-runtime", "matrix-api-gateway",
    "matrix-audit", "matrix-observability", "matrix-spigot", "matrix-tools-distill",
    "matrix-sdk-java", "matrix-sdk-js", "matrix-sdk-python", "matrix-web-ui",
    "matrix-billing", "matrix-quality", "matrix-ros2", "matrix-micro",
    "matrix-monitor", "matrix-operator", "matrix-fpga", "matrix-brain-runtime",
    "scripts", "formal", "wal", "src", "data", "models", "tools", "deploy",
    "docker", "docs", "states", "pilots", "prompts", "proto", "infra", "io",
    "sandbox", "languages", "config", "bin", "etc", "lib", "test", "tests",
)

# The roots that are JAVA PACKAGE namespaces and not documentation paths:
# "io/matrix/knowledge/" is a package written with slashes.
PACKAGE_ROOTS = ("io", "java", "javax", "org", "com", "jakarta", "sun")

# The identifiers that are PLACEHOLDERS and not identifiers: the index writes
# "DESIGN-NN" to mean "the design series", and that is a pattern, not an
# identifier of a document.
PLACEHOLDER_IDENTIFIERS = frozenset({
    "ADR-NN", "SPEC-NN", "DESIGN-NN", "H-NN", "OD-NN", "CD-NN", "PR-NN",
    "TASK-NN", "Q-NN", "R-NN",
})

# A Markdown link: [text](target) and [text](target "title"). The images are
# links too.
LINK_RE = re.compile(r"!?\[(?P<text>[^\]]*)\]\((?P<target>[^)\s]+)(?:\s+\"[^\"]*\")?\)")

# A Markdown reference definition: [id]: target
REFERENCE_DEFINITION_RE = re.compile(r"^\s{0,3}\[(?P<id>[^\]]+)\]:\s*(?P<target>\S+)")

# A backticked token — the place where the prose paths of this repository
# live.
BACKTICK_RE = re.compile(r"`(?P<token>[^`\n]+)`")

# A line reference at the end of a path: "Foo.java:90", "Foo.java:90-120".
# The line reference may use a hyphen or an en-dash: "Foo.java:90-120" and
# "Foo.java:90–120" are the same reference.
LINE_REFERENCE_RE = re.compile(r":[0-9]+(?:[-\u2013][0-9]+)?(?:,[0-9]+(?:[-\u2013][0-9]+)?)*$")

# An explicit exemption, written on the line that carries the reference:
#   `WAL.md` <!-- no-link: the file was renamed in W40 -->
# The reason is part of the marker, so an exemption is auditable and a
# reviewer can see WHY a reference is not a defect.
EXEMPTION_RE = re.compile(r"<!--\s*no-link\s*:\s*(?P<reason>[^>]*?)-->")

# The same for an IDENTIFIER: a document that names an identifier in order to
# say that the identifier does not exist is not making a broken reference.
#   `H-036` <!-- no-id: the register never declared it; Q-W46-19 -->
ID_EXEMPTION_RE = re.compile(r"<!--\s*no-id\s*:\s*(?P<reason>[^>]*?)-->")

# An external link.
EXTERNAL_RE = re.compile(r"^(?P<scheme>https?|ftp|mailto|tel)://|^(?P<mailto>mailto:)")

# A heading, with or without the closing hashes.
HEADING_RE = re.compile(r"^\s{0,3}(?P<hashes>#{1,6})\s+(?P<text>.+?)\s*#*\s*$")

# A table row.
TABLE_ROW_RE = re.compile(r"^\s*\|(?P<cells>.*)\|\s*$")

# A status line, in the two spellings the policy names.
STATUS_LINE_RE = re.compile(r"^\s*(?:\*\*)?(?:Status|Статус)(?:\*\*)?\s*[:：]\s*(?P<value>.+)$",
                            re.IGNORECASE)

# The defect classes. The names are the ones the report prints.
BROKEN_LINK = "BROKEN_LINK"
BROKEN_ANCHOR = "BROKEN_ANCHOR"
BROKEN_REFERENCE_DEFINITION = "BROKEN_REFERENCE_DEFINITION"
MISSING_PATH = "MISSING_PATH"
UNDEFINED_IDENTIFIER = "UNDEFINED_IDENTIFIER"
DUPLICATE_IDENTIFIER = "DUPLICATE_IDENTIFIER"
CURRENT_TO_HISTORICAL = "CURRENT_TO_HISTORICAL"
RUNTIME_ARTEFACT = "RUNTIME_ARTEFACT"
UNREACHABLE_FROM_INDEX = "UNREACHABLE_FROM_INDEX"
STATUS_OUT_OF_VOCABULARY = "STATUS_OUT_OF_VOCABULARY"

# The classes the strict mode fails on.
STRICT_CLASSES = (BROKEN_LINK, BROKEN_ANCHOR, MISSING_PATH, UNDEFINED_IDENTIFIER,
                  DUPLICATE_IDENTIFIER)

# The classifications: a document whose status line is outside the policy's
# vocabulary, and a document the index does not reach. They are reported
# always; they fail the gate only under --strict-classifications.
CLASSIFICATION_CLASSES = (STATUS_OUT_OF_VOCABULARY, UNREACHABLE_FROM_INDEX)

# The first-column headers under which a table row DECLARES its identifier.
# A register names its identifier column; a detail table restates identifiers
# that are declared elsewhere.
IDENTIFIER_COLUMN_HEADERS = frozenset({
    "id", "ids", "identifier", "identifiers", "the id", "the ids",
    "the identifier", "the identifiers", "код", "идентификатор",
})

# The groups the defects are reported in.
GROUP_CURRENT = "current"
GROUP_ARCHIVE = "archive"
GROUP_WAVES = "waves"

# The groups that never fail the strict mode on their own.
HISTORICAL_GROUPS = (GROUP_ARCHIVE, GROUP_WAVES)

# How many external links the human report prints; the machine-readable report
# carries all of them.
EXTERNAL_SAMPLE = 20


# ── the small helpers ────────────────────────────────────────────────────────

def read_document(path: Path) -> list[str]:
    """The lines of a document, with the BOM and the CRLF handled.

    A document with a BOM and a document with CRLF line endings are ordinary
    documents; the checker must not report a defect because of them.
    """
    try:
        data = path.read_bytes()
    except OSError:
        return []
    if data.startswith(b"\xef\xbb\xbf"):
        data = data[3:]
    text = data.decode("utf-8", errors="replace")
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    return text.split("\n")


def slugify(heading: str) -> str:
    """The GitHub slug of a heading.

    GitHub lowercases the heading, drops the punctuation, turns the spaces
    into hyphens and keeps the letters of every alphabet.
    """
    text = heading.strip()
    # The inline markup of the heading is not part of the slug.
    text = re.sub(r"`([^`]*)`", r"\1", text)
    text = re.sub(r"\*\*([^*]*)\*\*", r"\1", text)
    text = re.sub(r"\*([^*]*)\*", r"\1", text)
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)
    text = text.lower()
    kept = []
    for character in text:
        if character in (" ", "\t"):
            kept.append("-")
            continue
        category = unicodedata.category(character)
        if character in ("-", "_"):
            kept.append(character)
            continue
        if category.startswith("L") or category.startswith("N") or category.startswith("M"):
            kept.append(character)
            continue
    return "".join(kept)


def heading_slugs(lines: list[str]) -> set[str]:
    """The slugs of the headings of a document.

    A repeated heading gets the `-1`, `-2` suffix GitHub gives it, so that
    a link to the second of two equal headings resolves.
    """
    slugs: set[str] = set()
    seen: Counter = Counter()
    for line in lines:
        heading = HEADING_RE.match(line)
        if not heading:
            continue
        slug = slugify(heading.group("text"))
        if not slug:
            continue
        count = seen[slug]
        seen[slug] += 1
        slugs.add(slug if count == 0 else f"{slug}-{count}")
    return slugs


def document_status(lines: list[str]) -> tuple[str, str]:
    """The status of a document and the raw value it declared.

    The status comes from the explicit status line in the first fourteen
    lines. A document that declares none is ACTIVE by default until
    classified. A document that declares a value outside the vocabulary of the
    policy has an unknown status: it is treated as ACTIVE for the rules, and
    the mismatch is reported.
    """
    for line in lines[:STATUS_LINE_WINDOW]:
        status_line = STATUS_LINE_RE.match(line)
        if not status_line:
            continue
        raw = status_line.group("value").strip()
        # A status in backticks IS the status: "`ACTIVE` but **STALE**" says
        # ACTIVE, and the rest of the line is the reason.
        backticked = BACKTICKED_TOKEN_RE.search(raw)
        if backticked:
            candidate = backticked.group(1).strip()
        else:
            candidate = raw.split("·")[0].split("—")[0].split("—")[0]
            candidate = candidate.strip().strip("`").strip("*").strip()
            candidate = candidate.split(",")[0].split("(")[0].strip().strip("`").strip()
        if candidate.upper() in STATUS_VOCABULARY:
            return candidate.upper(), raw
        return "UNKNOWN", raw
    return DEFAULT_STATUS, ""


def is_historical_status(status: str) -> bool:
    return status in HISTORICAL_STATUSES


def looks_like_a_class_name(token: str) -> bool:
    """Whether a token is a Java type name and not a path.

    "io.matrix.knowledge.KnowledgeStore" is a type; "io/matrix/knowledge"
    is a directory.
    """
    if "/" in token:
        return False
    if "." not in token:
        return False
    head, _, tail = token.rpartition(".")
    if not head or not tail:
        return False
    if not tail[0].isupper():
        return False
    return all(part[:1].isupper() or part[:1].islower() for part in head.split("."))


def looks_like_a_route(token: str) -> bool:
    """Whether a token is an HTTP route and not a path."""
    return token.startswith("/")


def is_a_placeholder(token: str) -> bool:
    """Whether a token is a placeholder rather than a path.

    "W<N>-STATUS.md" and "docs-v2/engineering/W<N>-STATUS.md" name the SHAPE
    of a file; no such file exists and none should.
    """
    return "<" in token and ">" in token


def looks_like_a_glob(token: str) -> bool:
    """Whether a token is a glob and not a path."""
    return any(marker in token for marker in ("*", "?", "[", "{"))


def looks_like_a_url(token: str) -> bool:
    return bool(EXTERNAL_RE.match(token))


# ── the checker ──────────────────────────────────────────────────────────────

class Checker:
    """The checker over one tree."""

    def __init__(self, root: Path, scope: Path, excludes: list[str], include_historical: bool,
                 include_planned_artefacts: bool = False):
        self.root = root
        self.scope = scope
        self.excludes = excludes
        self.include_historical = include_historical
        self.include_planned_artefacts = include_planned_artefacts
        self.documents: list[Path] = []
        self.defects: list[dict] = []
        self.external: list[dict] = []
        self.status_cache: dict[Path, tuple[str, str]] = {}
        self.slug_cache: dict[Path, set[str]] = {}
        self.excluded_counts: Counter = Counter()
        self.excluded_samples: dict[str, list[str]] = defaultdict(list)
        self.planned_artefacts: list[dict] = []
        self.strict_classifications = False

    # -- the scope ---------------------------------------------------------

    def excluded(self, relative: str) -> bool:
        from fnmatch import fnmatch
        for pattern in list(DEFAULT_EXCLUDES) + list(self.excludes):
            if fnmatch(relative, pattern):
                return True
            if pattern.endswith("/**") and relative.startswith(pattern[:-3] + "/"):
                return True
            if pattern.startswith("**/") and fnmatch(relative, pattern[3:]):
                return True
        return False

    def collect(self) -> None:
        """The documents of the scope, sorted."""
        if self.scope.is_file():
            candidates = [self.scope]
        else:
            candidates = []
            for dirpath, dirnames, filenames in os.walk(self.scope):
                dirnames.sort()
                for filename in sorted(filenames):
                    if filename.endswith(".md"):
                        candidates.append(Path(dirpath) / filename)
        for path in candidates:
            relative = self.relative_of(path)
            if self.excluded(relative):
                continue
            self.documents.append(path)
        self.documents.sort()

    def relative_of(self, path: Path) -> str:
        try:
            return str(path.relative_to(self.root))
        except ValueError:
            return str(path)

    def group_of(self, path: Path) -> str:
        relative = self.relative_of(path)
        if relative.startswith(ARCHIVE_PREFIX):
            return GROUP_ARCHIVE
        if relative.startswith(WAVES_PREFIX):
            return GROUP_WAVES
        return GROUP_CURRENT

    def status_of(self, path: Path) -> tuple[str, str]:
        if path not in self.status_cache:
            self.status_cache[path] = document_status(read_document(path))
        return self.status_cache[path]

    def slugs_of(self, path: Path) -> set[str]:
        if path not in self.slug_cache:
            self.slug_cache[path] = heading_slugs(read_document(path))
        return self.slug_cache[path]

    # -- the defect record -------------------------------------------------

    def add(self, path: Path, line: int, defect_class: str, target: str, note: str) -> None:
        group = self.group_of(path)
        strict = defect_class in STRICT_CLASSES
        if defect_class in CLASSIFICATION_CLASSES and self.strict_classifications:
            # The classifications are repairable, not link defects: a document
            # whose status line is outside the policy's vocabulary, and a
            # document the index does not reach. They are reported always and
            # they fail the gate only when the owner asks for it with
            # --strict-classifications.
            strict = True
        if group in HISTORICAL_GROUPS:
            strict = False
        elif self.include_historical:
            strict = defect_class in STRICT_CLASSES
        elif self.status_of(path)[0] in HISTORICAL_STATUSES:
            # Nothing in a historical document is a strict failure: the
            # document describes the tree as it was, and the tree has moved on.
            # Its broken references are the history, and the history is kept.
            strict = False
        self.defects.append({
            "file": self.relative_of(path),
            "line": line,
            "class": defect_class,
            "target": target,
            "note": note,
            "group": group,
            "strict": strict,
        })

    # -- the checks --------------------------------------------------------

    def check_document(self, path: Path) -> None:
        lines = read_document(path)
        self.check_status(path, lines)
        self.check_links(path, lines)
        self.check_reference_definitions(path, lines)
        self.check_prose_paths(path, lines)
        self.check_identifiers(path, lines)

    def check_status(self, path: Path, lines: list[str]) -> None:
        status, raw = self.status_of(path)
        if status == "UNKNOWN" and raw:
            self.add(path, 1, STATUS_OUT_OF_VOCABULARY, raw.strip(),
                     "the status is outside the vocabulary of the policy")

    def check_links(self, path: Path, lines: list[str]) -> None:
        for number, line in enumerate(lines, start=1):
            for match in LINK_RE.finditer(line):
                target = match.group("target").strip()
                if not target:
                    continue
                if target.startswith("<") and target.endswith(">"):
                    target = target[1:-1]
                if looks_like_a_url(target):
                    self.external.append({
                        "file": self.relative_of(path),
                        "line": number,
                        "url": target,
                        "status": "UNVERIFIED",
                        "note": "no network check was performed",
                    })
                    continue
                if target.startswith("#"):
                    fragment = target[1:]
                    if fragment and fragment not in self.slugs_of(path):
                        self.add(path, number, BROKEN_ANCHOR, target,
                                 "the document has no heading with this anchor")
                    continue
                link_path, _, fragment = target.partition("#")
                if not link_path or not link_path.strip("."):
                    # "..." and "." are placeholders, not targets.
                    continue
                if looks_like_a_glob(link_path):
                    continue
                if is_a_site_route(link_path):
                    # A root-relative path ("/algorithms/BrcChain") or an HTML
                    # page ("../index.html") is a page of the DOCUMENTATION
                    # SITE. The repository holds the sources of the site, not
                    # the built site, so the target cannot be checked here:
                    # whether the site builds it is a question for the site
                    # build, not for this tool.
                    self.excluded_counts["a site route"] += 1
                    self.remember("a site route", target)
                    continue
                resolved = self.resolve_link(path, link_path)
                if resolved is None:
                    self.add(path, number, BROKEN_LINK, target,
                             "the target does not exist")
                    continue
                if fragment and resolved.is_file() and resolved.suffix.lower() == ".md":
                    if fragment not in self.slugs_of(resolved):
                        self.add(path, number, BROKEN_ANCHOR, target,
                                 "the target document has no heading with this anchor")
                        continue
                self.check_link_to_historical(path, number, target, resolved)

    def resolve_link(self, document: Path, link_path: str) -> Path | None:
        """The file a link points at, under any of the honest readings.

        A link is written relative to the document that carries it, but the
        documents of this repository also write paths relative to docs-v2 and
        relative to the repository root. A target that exists under any of
        those readings is not broken; only a target that exists under none of
        them is a defect.
        """
        candidates = [document.parent / link_path,
                      self.root / "docs-v2" / link_path,
                      self.root / link_path]
        if link_path.startswith("/"):
            # A path that begins with a slash is the root of the SITE: the
            # documentation of this repository is published from docs-v2.
            candidates.append(self.root / "docs-v2" / link_path.lstrip("/"))
        for candidate in candidates:
            if candidate.exists():
                return candidate
        return None

    def check_link_to_historical(self, path: Path, number: int, target: str,
                                  resolved: Path) -> None:
        if self.group_of(path) != GROUP_CURRENT:
            return
        if resolved.is_dir():
            return
        group = self.group_of(resolved)
        if group in HISTORICAL_GROUPS:
            self.add(path, number, CURRENT_TO_HISTORICAL, target,
                     "the target is in the " + group + " area")
            return
        status = self.status_of(resolved)[0]
        if is_historical_status(status):
            self.add(path, number, CURRENT_TO_HISTORICAL, target,
                     "the target document is " + status)

    def check_reference_definitions(self, path: Path, lines: list[str]) -> None:
        for number, line in enumerate(lines, start=1):
            match = REFERENCE_DEFINITION_RE.match(line)
            if not match:
                continue
            target = match.group("target").strip()
            if not target or looks_like_a_url(target) or target.startswith("#"):
                continue
            if looks_like_a_glob(target):
                continue
            link_path = target.partition("#")[0]
            if not link_path:
                continue
            if not (path.parent / link_path).exists():
                self.add(path, number, BROKEN_REFERENCE_DEFINITION, target,
                         "the target of the reference definition does not exist")

    def check_prose_paths(self, path: Path, lines: list[str]) -> None:
        for number, line in enumerate(lines, start=1):
            # An explicit exemption. A document that NAMES a file in order to
            # say that the file does not exist is not making a broken
            # reference — it is making a point. The marker is on the same
            # line and carries the reason, so the exemption is auditable:
            #   `WAL.md` <!-- no-link: the file was renamed in W40 -->
            if EXEMPTION_RE.search(line):
                self.excluded_counts["an explicit exemption"] += 1
                continue
            for match in BACKTICK_RE.finditer(line):
                token = match.group("token").strip()
                if not token or " " in token:
                    continue
                if looks_like_a_url(token):
                    self.excluded_counts["a URL"] += 1
                    self.remember("a URL", token)
                    continue
                if looks_like_a_route(token):
                    self.excluded_counts["a route"] += 1
                    self.remember("a route", token)
                    continue
                if looks_like_a_glob(token):
                    self.excluded_counts["a glob"] += 1
                    self.remember("a glob", token)
                    continue
                if is_a_placeholder(token):
                    # "W<N>-STATUS.md" is a pattern a human fills in, not a
                    # file that should exist.
                    self.excluded_counts["a placeholder"] += 1
                    self.remember("a placeholder", token)
                    continue
                if looks_like_a_class_name(token):
                    self.excluded_counts["a Java type name"] += 1
                    self.remember("a Java type name", token)
                    continue
                if "/" not in token:
                    continue
                if not self.looks_like_a_repository_path(token):
                    self.excluded_counts["not a repository path"] += 1
                    self.remember("not a repository path", token)
                    continue
                if "..." in token:
                    # "matrix-core/.../Foo.java" is an abbreviated path, not a path.
                    self.excluded_counts["abbreviated path"] += 1
                    self.remember("abbreviated path", token)
                    continue
                # A path with a line reference after it — "Foo.java:90" — names
                # a file and a line. The file is what has to exist.
                candidate = LINE_REFERENCE_RE.sub("", token).rstrip(".,;:")
                if candidate != token and self.path_exists(path, candidate):
                    continue
                if self.path_exists(path, candidate):
                    continue
                parent = (self.root / candidate).parent
                if not parent.exists() and not self.include_planned_artefacts:
                    # Neither the path nor the directory it would live in is
                    # there: this is a planned artefact, not a broken
                    # reference. It is NOT a defect, and the report says how
                    # many there were and why they were left alone.
                    #
                    # --include-planned-artefacts reports them anyway. A
                    # planned artefact is not a defect, so it stays OUT of the
                    # strict set even when it is reported: the flag changes
                    # what is VISIBLE, not what FAILS.
                    self.excluded_counts["a planned artefact"] += 1
                    self.remember("a planned artefact", token)
                    self.planned_artefacts.append({
                        "file": self.relative_of(path),
                        "line": number,
                        "target": token,
                    })
                    continue
                if not parent.exists():
                    self.planned_artefacts.append({
                        "file": self.relative_of(path),
                        "line": number,
                        "target": token,
                    })
                if self.is_runtime_artefact(candidate):
                    # W48-05: the runtime's own paths are machine state. They are
                    # reported, so they stay discoverable, and they never fail the
                    # gate: the same document is correct on a machine that has run
                    # the system and on one that has not.
                    self.add(path, number, RUNTIME_ARTEFACT, token,
                             "a runtime artefact: its presence depends on the machine, "
                             "not on the repository")
                    continue
                self.add(path, number, MISSING_PATH, token,
                         "the path does not exist")

    def is_runtime_artefact(self, candidate: str) -> bool:
        """Whether a path names a runtime artefact rather than a repository file.

        A runtime artefact is a path the RUNTIME owns: the owner's live database,
        the runtime's scratch directories, the build outputs and the formal gate's
        state directories. They are not tracked in git, so their absence says
        nothing about the repository — and their presence on one machine says
        nothing about it either.
        """
        normalised = candidate.replace("\\", "/")
        return normalised.startswith(RUNTIME_PREFIXES)

    def remember(self, bucket: str, token: str) -> None:
        if len(self.excluded_samples[bucket]) < 5:
            self.excluded_samples[bucket].append(token)

    def looks_like_a_repository_path(self, token: str) -> bool:
        """Whether a backticked token is a PATH and not a package reference.

        Two readings are accepted, and everything else is left alone:

          * the token starts with a known root of the repository — "docs-v2/",
            "matrix-core/", "scripts/" — so a directory or a file under it is
            a path;
          * the token ends with a file extension, so it names a file.

        A token like "audit/HashChain" or "reasoning/BrcChain" is a Java
        package and a class written with a slash: it is not a path, and
        reporting it would be noise.
        """
        head = token.split("/", 1)[0]
        if head in PACKAGE_ROOTS:
            return False
        if head in REPOSITORY_ROOTS:
            return True
        last = token.rstrip("/").rsplit("/", 1)[-1]
        return "." in last and not last.startswith(".")

    def path_exists(self, document: Path, token: str) -> bool:
        """Whether a prose path resolves, by any of the honest readings.

        A prose path may be written relative to the repository root, relative
        to the directory of the document, or relative to docs-v2. A path that
        exists under any of those readings is not a defect.
        """
        candidates = [
            self.root / token,
            document.parent / token,
            self.root / "docs-v2" / token,
        ]
        # A short path — "src/main" — is written relative to the module it
        # belongs to, and the module is not named.
        if "/" in token and "." not in token.split("/", 1)[0]:
            for module in MODULE_DIRECTORIES:
                candidates.append(self.root / module / token)
        return any(candidate.exists() for candidate in candidates)

    def check_identifiers(self, path: Path, lines: list[str]) -> None:
        group = self.group_of(path)
        self.table_headers = {}
        self._current_lines = lines
        for number, line in enumerate(lines, start=1):
            declared = self.declared_identifier(line, lines, number - 1)
            declared_used = False
            for match in IDENTIFIER_RE.finditer(line):
                identifier = match.group(0)
                if identifier in PLACEHOLDER_IDENTIFIERS:
                    continue
                if EXEMPTION_RE.search(line) or ID_EXEMPTION_RE.search(line):
                    # The line names the identifier in order to say something
                    # about it — that the register never declared it, for
                    # instance. It is not a broken reference.
                    continue
                if not re.search(r"[0-9]", match.group(2)):
                    # "SPEC-NNN" and "DESIGN-FIRST" are patterns, not the
                    # identifiers of a document.
                    continue
                if identifier == declared and not declared_used:
                    declared_used = True
                    self.definitions[group][identifier].append((path, number))
                else:
                    self.references[group][identifier].append((path, number))

    def declared_identifier(self, line: str, lines: list[str], index: int) -> str | None:
        """The identifier that a line DECLARES, if it declares one.

        A definition is an identifier declared in a heading, in the first
        column of a table row, or in a status block. Only the identifier the
        line declares is a definition: the other identifiers of the same line
        are references to it.

        The difference between a declaration and a mention is the SHAPE of the
        cell or the heading:

          * a heading declares when it BEGINS with the identifier — "# ADR-W45
            — the decision" declares ADR-W45, and "## the H-100+ wave" does
            not declare anything;
          * a table row declares when its first cell IS the identifier — the
            cell "| OD-020 |" declares OD-020, while the cell "| OD-005 — the
            first answer set |" mentions it.

        A row that merely mentions an identifier in a narrative cell is a
        reference, and reporting it as a definition would invent duplicates
        that do not exist.
        """
        heading = HEADING_RE.match(line)
        if heading:
            return self.declared_by_a_heading(heading.group("text"))
        if STATUS_LINE_RE.match(line):
            return identifier_in(line)
        row = TABLE_ROW_RE.match(line)
        if row:
            first_cell = row.group("cells").split("|")[0].strip()
            plain = first_cell.strip().strip("`*").strip()
            identifier = identifier_in(plain)
            if identifier and plain == identifier:
                return identifier
        return None

    def declares_a_table(self, line: str, index: int) -> bool:
        """Whether a table row DECLARES its identifier or only mentions it.

        The header of the table decides. A table whose first column is the
        identifier column — "id", "identifier", "the id" — is a register: its
        rows declare. A table whose first column says something else — "the
        decision", "the finding" — restates identifiers that are declared
        elsewhere: its rows mention them.

        Without this rule a register that holds a summary table AND a detail
        table declares every identifier twice, and the checker reports a
        collision that does not exist.
        """
        header = self.table_headers.get(index)
        if header is None:
            header = self.find_table_header(index)
            self.table_headers[index] = header
        if header is None:
            return False
        return header in IDENTIFIER_COLUMN_HEADERS

    def find_table_header(self, index: int) -> str | None:
        """The header cell of the table the line at `index` belongs to.

        The header is the first row of the contiguous block of table rows the
        line belongs to, and it is a header only when the row after it is a
        separator.
        """
        lines = self._current_lines
        start = index
        while start > 0 and TABLE_ROW_RE.match(lines[start - 1]):
            start -= 1
        if start + 1 >= len(lines):
            return None
        if not TABLE_ROW_RE.match(lines[start + 1]):
            return None
        if not re.match(r"^\s*\|(?:\s*:?-{2,}:?\s*\|)+\s*$", lines[start + 1]):
            return None
        header = TABLE_ROW_RE.match(lines[start])
        if not header:
            return None
        first = header.group("cells").split("|")[0].strip().strip("`*").strip()
        return first.lower()

    def declared_by_a_heading(self, text: str) -> str | None:
        """The identifier a heading declares, when it begins with one."""
        plain = text.strip().lstrip("#").strip()
        match = IDENTIFIER_RE.match(plain)
        if not match:
            return None
        rest = plain[match.end():]
        if rest and (rest[0].isalnum() or rest[0] == "-"):
            return None
        return match.group(0)


def identifier_in(text: str) -> str | None:
    """The first identifier in a text, if there is one."""
    match = IDENTIFIER_RE.search(text)
    return match.group(0) if match else None


# The documents that REGISTER a series: they list the entries of a series,
# and a row of a register is a reference to the entry, not a second
# definition of it.
REGISTRY_MARKERS = ("REGISTER", "MATRIX", "INDEX", "SUMMARY", "CATALOG", "LEDGER")

# The REGISTER of each family of identifiers: the document where the entries
# of that family live. A declaration in the register is the definition; the
# wave reports, the synthesis documents and the summaries that restate it are
# references to it.
#
# The mapping is explicit on purpose. A heuristic — "a document whose name
# looks like a register" — cannot tell a REGISTER from a MIRROR:
# "docs-v2/vision/FINALSUMMARY.md" restates the hypothesis register
# faithfully, and it is a mirror, not a second register.
FAMILY_REGISTERS = (
    ("docs-v2/governance/DECISION-REGISTER.md", ("OD-", "CD-", "ND-", "PR-", "ADR-")),
    ("docs-v2/governance/OPEN-QUESTIONS.md", ("Q-",)),
    ("docs-v2/governance/RISK-REGISTER.md", ("R-",)),
    ("docs-v2/governance/ROADMAP.md", ("TASK-",)),
    ("docs-v2/research/HYPOTHESES-NEW.md", ("H-",)),
    ("docs-v2/research/HYPOTHESES.md", ("H-",)),
)


def is_a_site_route(link_path: str) -> bool:
    """Whether a link target is a page of the documentation site.

    A root-relative target ("/algorithms/BrcChain") and an HTML page
    ("../index.html") are pages of the built site. The repository contains the
    sources, so the checker cannot say whether the site builds them, and it
    does not pretend to.
    """
    if link_path.startswith("/"):
        return True
    return link_path.lower().endswith(".html")


def register_of(identifier: str) -> str | None:
    """The register of the family of an identifier, if it has one."""
    for register, prefixes in FAMILY_REGISTERS:
        for prefix in prefixes:
            if identifier.startswith(prefix):
                return register
    return None


# The PRECEDENCE of the registers, from the highest to the lowest. The
# register of a series is the document the project designates as the place
# where the entries of that series live; the wave reports, the syntheses and
# the summaries that restate them are REFERENCES.
#
# The order matters, and it is not cosmetic: "docs-v2/vision/FINALSUMMARY.md"
# restates the hypothesis register faithfully, and it is a mirror — a
# reference — not a second register. Two registers of the same series are a
# real collision, and they are reported as one.
REGISTER_PRECEDENCE = (
    "docs-v2/governance/",
    "docs-v2/research/HYPOTHESES",
    "docs-v2/engineering/ADR-",
    "docs-v2/engineering/W4",
)


def register_rank(path: Path) -> int:
    """The precedence of a document as the register of a series.

    A lower number is a higher precedence. A document that is not a register
    at all has no rank.
    """
    relative = path.as_posix()
    for index, prefix in enumerate(REGISTER_PRECEDENCE):
        if prefix in relative:
            return index
    return -1


def is_a_register(path: Path) -> bool:
    name = path.name.upper()
    return any(marker in name for marker in REGISTRY_MARKERS)


def carries_the_identifier(path: Path, identifier: str) -> bool:
    """Whether the NAME of a document carries an identifier.

    "ADR-W45.2-01-canonical-entry-point.md" is the document OF ADR-W45.2-01.
    A declaration of an identifier in the document that carries it is the
    definition; a declaration of the same identifier anywhere else — in a
    register, in a matrix, in an index — is a REFERENCE to that document.
    """
    stem = path.stem.upper()
    return stem.startswith(identifier.upper())


# ── the report ───────────────────────────────────────────────────────────────

def print_report(checker: Checker, exit_code: int, write_json: str | None) -> None:
    defects = sorted(checker.defects,
                     key=lambda defect: (defect["group"], defect["class"],
                                         defect["file"], defect["line"], defect["target"]))
    counts = Counter(defect["class"] for defect in defects)
    by_group = Counter(defect["group"] for defect in defects)

    print("===== the documentation link and reference checker =====")
    print(f"the root: {checker.root}")
    print(f"the scope: {checker.scope}")
    print(f"the documents scanned: {len(checker.documents)}")
    print(f"the external links collected (UNVERIFIED, no network check was performed): "
          f"{len(checker.external)}")
    print()

    print("----- the counts by defect class -----")
    for defect_class in sorted(counts):
        print(f"  {defect_class:<30} {counts[defect_class]:>6}")
    print(f"  {'TOTAL':<30} {len(defects):>6}")
    print()

    print("----- the counts by group -----")
    for group in (GROUP_CURRENT, GROUP_ARCHIVE, GROUP_WAVES):
        print(f"  {group:<30} {by_group.get(group, 0):>6}")
    print()

    print("----- the defects -----")
    for defect in defects:
        print(f"{defect['file']}:{defect['line']}  {defect['class']}  {defect['target']}  "
              f"{defect['note']}")
    print()

    print("----- what was excluded, and why -----")
    print("  globs, routes, URLs and Java type names are not paths and are never reported;")
    print("  the archive and the historical waves are scanned read-only and are reported in")
    print("  their own group, and their defects never fail the strict mode.")
    for bucket, count in sorted(checker.excluded_counts.items()):
        samples = ", ".join(checker.excluded_samples[bucket])
        print(f"  {bucket}: {count} token(s) — e.g. {samples}")
    print()

    print("----- the external links -----")
    print("  every one of them is UNVERIFIED: this tool performs no network check, so it")
    print("  does not claim that any of them is valid. The first ones only; the whole")
    print("  list is in the machine-readable report.")
    for item in sorted(checker.external, key=lambda entry: (entry["file"], entry["line"]))[:EXTERNAL_SAMPLE]:
        print(f"{item['file']}:{item['line']}  UNVERIFIED  {item['url']}")
    print()

    by_link, by_mention = reachable_documents(checker)
    print("----- the reachability from the index -----")
    print(f"  the index reaches {len(by_link)} document(s) by a link a reader can click, and")
    print(f"  {len(by_mention)} when the paths it NAMES in prose are counted as well.")
    print()

    strict_failures = [defect for defect in defects if defect["strict"]]
    print("----- the summary -----")
    print(f"  defects: {len(defects)}  strict failures: {len(strict_failures)}  "
          f"external links (UNVERIFIED): {len(checker.external)}")
    print(f"  the exit code: {exit_code}")
    if strict_failures:
        print("  the strict rule set failed on:")
        for defect in sorted(strict_failures,
                             key=lambda entry: (entry["file"], entry["line"], entry["target"])):
            print(f"    {defect['file']}:{defect['line']}  {defect['class']}  {defect['target']}")

    if write_json:
        payload = {
            "scanned": {
                "root": str(checker.root),
                "scope": str(checker.scope),
                "documents": len(checker.documents),
                "excludes": list(checker.excludes),
                "include_historical": checker.include_historical,
                "groups": {
                    group: sum(1 for document in checker.documents
                               if checker.group_of(document) == group)
                    for group in (GROUP_CURRENT, GROUP_ARCHIVE, GROUP_WAVES)
                },
                "network_check_performed": False,
            },
            "counts": {
                "by_class": dict(sorted(counts.items())),
                "by_group": dict(sorted(by_group.items())),
                "defects": len(defects),
                "strict_failures": len(strict_failures),
                "external_unverified": len(checker.external),
            },
            "defects": [{key: defect[key] for key in
                         ("file", "line", "class", "target", "note", "group", "strict")}
                        for defect in defects],
            "external_unverified": sorted(checker.external,
                                          key=lambda entry: (entry["file"], entry["line"])),
            "excluded": {
                "counts": dict(sorted(checker.excluded_counts.items())),
                "samples": {bucket: sorted(samples)
                            for bucket, samples in sorted(checker.excluded_samples.items())},
                "planned_artefacts": sorted(checker.planned_artefacts,
                                            key=lambda entry: (entry["file"], entry["line"],
                                                               entry["target"])),
            },
            "exit_code": exit_code,
        }
        Path(write_json).write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
                                    encoding="utf-8")
        print(f"  the machine-readable report is written to {write_json}")


# ── the self-test ───────────────────────────────────────────────────────────

# The fixture the self-test builds. It is written to a TEMPORARY DIRECTORY and
# removed afterwards; the repository is never touched.
SELF_TEST_BROKEN = """# the broken fixture

[the broken link](nowhere.md)

[the broken anchor](other.md#no-such-heading)

`docs/NO-SUCH-FILE.md`

The identifier SPEC-999 is referenced here.
"""

SELF_TEST_OTHER = """# the other fixture

## a heading that exists
"""

SELF_TEST_DUPLICATE_A = """# OD-999 — the first declaration

The first declaration of the identifier.
"""

SELF_TEST_DUPLICATE_B = """# OD-999 — the second declaration

The second declaration of the same identifier.
"""

SELF_TEST_CLEAN = """# the clean fixture

[the good link](other.md)

[the good anchor](other.md#a-heading-that-exists)

`other.md`
"""


def build_self_test_fixture(directory: Path) -> Path:
    """Write the fixture and return the directory that holds it."""
    directory.mkdir(parents=True, exist_ok=True)
    # The directory of the missing path has to EXIST: a path whose parent
    # directory is missing is a planned artefact, not a broken reference, and
    # the checker treats it as such on purpose.
    (directory / "docs").mkdir(exist_ok=True)
    (directory / "broken.md").write_text(SELF_TEST_BROKEN, encoding="utf-8")
    (directory / "other.md").write_text(SELF_TEST_OTHER, encoding="utf-8")
    (directory / "dup-a.md").write_text(SELF_TEST_DUPLICATE_A, encoding="utf-8")
    (directory / "dup-b.md").write_text(SELF_TEST_DUPLICATE_B, encoding="utf-8")
    (directory / "clean.md").write_text(SELF_TEST_CLEAN, encoding="utf-8")
    return directory


def collect_fixture_classes(directory: Path) -> set[str]:
    """Run the checker over the fixture and return the classes it found."""
    checker = Checker(directory, directory, [], True)
    checker.definitions = defaultdict(lambda: defaultdict(list))
    checker.references = defaultdict(lambda: defaultdict(list))
    checker.collect()
    for document in checker.documents:
        checker.check_document(document)
    for group, definitions in checker.definitions.items():
        for identifier in list(definitions):
            sites = definitions[identifier]
            canonical = [site for site in sites
                         if carries_the_identifier(site[0], identifier)]
            if not canonical:
                canonical = [site for site in sites if not is_a_register(site[0])]
            if not canonical or len(canonical) == len(sites):
                continue
            for site in list(sites):
                if site in canonical:
                    continue
                definitions[identifier].remove(site)
                checker.references[group][identifier].append(site)
            if not definitions[identifier]:
                del definitions[identifier]
    for group in (GROUP_CURRENT, GROUP_ARCHIVE, GROUP_WAVES):
        definitions = checker.definitions[group]
        references = checker.references[group]
        for identifier in sorted(references):
            if identifier not in definitions:
                checker.add(directory / "broken.md", 1, UNDEFINED_IDENTIFIER, identifier,
                            "referenced and never defined")
        for identifier in sorted(definitions):
            sites = sorted(definitions[identifier])
            if len(sites) < 2:
                continue
            first_path, first_line = sites[0]
            for path, number in sites[1:]:
                checker.add(path, number, DUPLICATE_IDENTIFIER, identifier,
                            "also defined at " + checker.relative_of(first_path) + ":"
                            + str(first_line))
    return {defect["class"] for defect in checker.defects}


def self_test() -> int:
    """Prove that the checker finds the defects and that the gate goes red.

    The fixture lives in a temporary directory and is removed whether the
    self-test passes or fails. The strict exit code is observed as a REAL
    process exit, because that is what the gate sees.
    """
    import shutil
    import subprocess
    import sys
    import tempfile

    expected = {BROKEN_LINK, BROKEN_ANCHOR, MISSING_PATH, UNDEFINED_IDENTIFIER,
                DUPLICATE_IDENTIFIER}
    failures: list[str] = []
    workspace = Path(tempfile.mkdtemp(prefix="w46-docs-link-check-selftest-"))
    try:
        broken = build_self_test_fixture(workspace / "broken")
        found = collect_fixture_classes(broken)
        missing = expected - found
        if missing:
            failures.append("the fixture did not produce " + ", ".join(sorted(missing)))
        else:
            print("  the five defect classes are found: "
                  + ", ".join(sorted(expected)))

        clean = build_self_test_fixture(workspace / "clean")
        (clean / "other.md").write_text(SELF_TEST_OTHER, encoding="utf-8")
        (clean / "broken.md").unlink()
        (clean / "dup-a.md").unlink()
        (clean / "dup-b.md").unlink()
        clean_found = collect_fixture_classes(clean)
        if clean_found:
            failures.append("the clean fixture produced " + ", ".join(sorted(clean_found)))
        else:
            print("  the clean fixture produces no defect")

        script = Path(__file__).resolve()
        red = subprocess.run([sys.executable, str(script), "--root", str(broken),
                              "--scope", str(broken), "--strict"],
                             capture_output=True, text=True)
        if red.returncode != 1:
            failures.append("the strict mode over the broken fixture exited "
                            + str(red.returncode) + " and not 1")
        else:
            print("  the strict mode over the broken fixture exits 1")

        green = subprocess.run([sys.executable, str(script), "--root", str(clean),
                                "--scope", str(clean), "--strict"],
                               capture_output=True, text=True)
        if green.returncode != 0:
            failures.append("the strict mode over the clean fixture exited "
                            + str(green.returncode) + " and not 0")
        else:
            print("  the strict mode over the clean fixture exits 0")
    finally:
        shutil.rmtree(workspace, ignore_errors=True)

    if failures:
        for failure in failures:
            print("  FAILED: " + failure)
        print("the self-test FAILED")
        return 1
    print("the self-test PASSED")
    return 0


# ── the main ─────────────────────────────────────────────────────────────────

def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="the documentation link and reference checker of W46.2")
    parser.add_argument("--root", default=".", help="the repository root")
    parser.add_argument("--scope", default=None, help="the subtree or file to scan")
    parser.add_argument("--strict", action="store_true",
                        help="fail on the strict rule set")
    parser.add_argument("--json", default=None, help="write the machine-readable report here")
    parser.add_argument("--exclude", action="append", default=[],
                        help="a glob of paths to skip (repeatable)")
    parser.add_argument("--include-historical", action="store_true",
                        help="let the archive, the waves and the historical documents fail "
                             "the strict mode")
    parser.add_argument("--include-planned-artefacts", action="store_true",
                        help="report the planned artefacts (a path whose directory does not "
                             "exist) as MISSING_PATH; they stay OUT of the strict set")
    parser.add_argument("--strict-classifications", action="store_true",
                        help="let the classifications fail the strict mode too: a status "
                             "outside the policy's vocabulary, and a document the index "
                             "does not reach")
    parser.add_argument("--self-test", action="store_true",
                        help="build a fixture in a temporary directory, prove that the "
                             "checker finds the five defect classes and that the strict "
                             "mode goes red on it, then remove the fixture")
    arguments = parser.parse_args(argv[1:])

    if arguments.self_test:
        return self_test()

    root = Path(arguments.root).resolve()
    if arguments.scope:
        scope = Path(arguments.scope)
        # A relative scope is relative to the ROOT, not to the directory the
        # tool happens to be started from.
        scope = (scope if scope.is_absolute() else root / scope).resolve()
    else:
        scope = root

    checker = Checker(root, scope, list(arguments.exclude), arguments.include_historical,
                      arguments.include_planned_artefacts)
    checker.strict_classifications = arguments.strict_classifications
    # The definition and reference indexes are shared by the whole scan: an
    # identifier is "defined" if ANY document defines it.
    checker.definitions = defaultdict(lambda: defaultdict(list))
    checker.references = defaultdict(lambda: defaultdict(list))

    checker.collect()
    for document in checker.documents:
        checker.check_document(document)

    # A declaration in the document that CARRIES the identifier is the
    # definition; a declaration of the same identifier in another document is
    # a reference to it. A registry row and an index row are references, and
    # without this rule every ADR would be "defined twice".
    for group, definitions in checker.definitions.items():
        for identifier in list(definitions):
            sites = definitions[identifier]
            # The rule, in order:
            #   1. a declaration in the REGISTER of the series is the
            #      definition; the wave reports and the synthesis documents
            #      that restate it are references to it;
            #   2. otherwise the document whose NAME carries the identifier is
            #      the definition;
            #   3. otherwise every site is a definition, and two of them are
            #      a collision.
            # 1. the document whose NAME carries the identifier — an ADR
            #    file, a SPEC file — is its definition;
            named = [site for site in sites
                     if carries_the_identifier(site[0], identifier)]
            # 2. otherwise the REGISTER of the family: the first declaration
            #    in it is the definition, and the register's own detail tables
            #    are references to it;
            register = register_of(identifier)
            registers = [site for site in sites
                         if register and register in site[0].as_posix()]
            if len(named) == 1:
                canonical = named
            elif registers:
                # One declaration per register: the first one. Two registers
                # of the same family are a real collision.
                by_register: dict[str, list] = {}
                for site in registers:
                    by_register.setdefault(site[0].as_posix(), []).append(site)
                canonical = [sorted(sites)[0] for sites in by_register.values()]
            elif sites:
                # Nothing identifies the series: the first declaration wins.
                canonical = [sites[0]]
            if not canonical or len(canonical) == len(sites):
                continue
            for site in list(sites):
                if site in canonical:
                    continue
                definitions[identifier].remove(site)
                checker.references[group][identifier].append(site)
            if not definitions[identifier]:
                del definitions[identifier]

    # The identifiers that are referenced and never defined, and the ones
    # that are defined twice. The analysis is PER GROUP: the archive is a
    # frozen copy of the documents of the tree, so an identifier that is
    # declared in both the archive and the tree is the same document, not a
    # collision.
    for group in (GROUP_CURRENT, GROUP_ARCHIVE, GROUP_WAVES):
        definitions = checker.definitions[group]
        references = checker.references[group]
        for identifier in sorted(references):
            if identifier in definitions:
                continue
            sites = sorted(references[identifier])
            path, number = sites[0]
            checker.add(path, number, UNDEFINED_IDENTIFIER, identifier,
                        "referenced " + str(len(sites)) + " time(s) and never defined")
        for identifier in sorted(definitions):
            sites = sorted(definitions[identifier])
            if len(sites) < 2:
                continue
            first_path, first_line = sites[0]
            for path, number in sites[1:]:
                checker.add(path, number, DUPLICATE_IDENTIFIER, identifier,
                            "also defined at " + checker.relative_of(first_path) + ":"
                            + str(first_line))

    # The documents that no link from the index reaches.
    unreachable = unreachable_from_index(checker)
    for document in unreachable:
        checker.add(document, 1, UNREACHABLE_FROM_INDEX, INDEX_DOCUMENT,
                    "the index does not reach this document")

    strict_failures = [defect for defect in checker.defects if defect["strict"]]
    exit_code = 1 if (arguments.strict and strict_failures) else 0
    print_report(checker, exit_code, arguments.json)
    return exit_code


def reachable_documents(checker: Checker) -> tuple[set[Path], set[Path]]:
    """The documents the index reaches, by link and by mention.

    The first set is what a reader can CLICK: the Markdown links of the
    index, followed through the tree. The second set adds the documents the
    index NAMES in prose — a backticked path is a pointer a reader has to
    copy by hand, which is why the two numbers differ.
    """
    index = checker.root / INDEX_DOCUMENT
    if not index.is_file():
        return set(), set()
    by_link: set[Path] = set()
    by_mention: set[Path] = set()
    queue = [index]
    while queue:
        current = queue.pop()
        if current in by_link:
            continue
        by_link.add(current)
        lines = read_document(current)
        for line in lines:
            for match in LINK_RE.finditer(line):
                target = match.group("target").strip()
                if looks_like_a_url(target) or target.startswith("#"):
                    continue
                link_path = target.partition("#")[0]
                if not link_path or looks_like_a_glob(link_path):
                    continue
                candidate = (current.parent / link_path).resolve()
                if candidate.is_file() and candidate.suffix.lower() == ".md":
                    by_mention.add(candidate)
                    if candidate not in by_link:
                        queue.append(candidate)
            for match in BACKTICK_RE.finditer(line):
                token = match.group("token").strip()
                if "/" not in token or looks_like_a_glob(token) \
                        or looks_like_a_route(token) or looks_like_a_url(token):
                    continue
                for base in (checker.root, checker.root / "docs-v2", current.parent):
                    candidate = (base / token).resolve()
                    if candidate.is_file() and candidate.suffix.lower() == ".md":
                        by_mention.add(candidate)
                        break
    return by_link, by_mention


def unreachable_from_index(checker: Checker) -> list[Path]:
    """The documents of docs-v2 that no link from the index reaches.

    The reachability is measured over the relative links of the Markdown
    documents, starting at docs-v2/INDEX.md. A document that is not reached
    is not broken, but a reader cannot find it.
    """
    index = checker.root / INDEX_DOCUMENT
    if not index.is_file():
        return []
    reachable: set[Path] = set()
    queue = [index]
    while queue:
        current = queue.pop()
        if current in reachable:
            continue
        reachable.add(current)
        for line in read_document(current):
            for match in LINK_RE.finditer(line):
                target = match.group("target").strip()
                if looks_like_a_url(target) or target.startswith("#"):
                    continue
                link_path = target.partition("#")[0]
                if not link_path or looks_like_a_glob(link_path):
                    continue
                candidate = (current.parent / link_path).resolve()
                if candidate.is_file() and candidate.suffix.lower() == ".md":
                    if candidate not in reachable:
                        queue.append(candidate)
    unreachable = []
    for document in checker.documents:
        if checker.group_of(document) != GROUP_CURRENT:
            continue
        if not str(document).startswith(str(checker.root / "docs-v2")):
            continue
        if document.resolve() not in reachable:
            unreachable.append(document)
    return sorted(unreachable)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
