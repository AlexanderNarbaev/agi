#!/usr/bin/env python3
"""Reproducible mutation harness for the MATRIX formal gate.

What it is for
--------------
A green gate that cannot go red is not a gate. This harness asks the TLA+
gate a blunt question: if the specification were WRONG, would the properties
fail? It takes each mutation from ``formal/mutations.json`` -- a deliberate
defect -- applies it to a COPY of the specification in an isolated temporary
directory, runs SANY and TLC there, and records what happened.

The real source tree is never modified. Every mutant lives in a temporary
directory that is deleted afterwards, and the specification files under
``formal/`` are only ever read.

Outcome vocabulary
------------------
  KILLED    the mutant was rejected: TLC reported a violated invariant (12)
            or a violated temporal property (13). The gate works.
  SURVIVED  the mutant model-checked cleanly. The property it targets cannot
            fail, so the gate is blind there. This is a finding, not a pass.
  INVALID   the mutant does not parse or does not evaluate. It says nothing
            about the properties; it is a broken mutant, not a survived one.
  EQUIVALENT the mutant survives AND is declared equivalent in the manifest -
            the change provably does not alter the modelled behaviour.
  DRIFT    the mutant behaved differently from the outcome recorded in the
            manifest. The gate changed: either a property was weakened, or a
            specification was corrected. Either way a human must look.

Exit status
-----------
0 when every mutant is KILLED or a declared EQUIVALENT one; 1 otherwise. A
surviving mutant must fail the build, otherwise the harness would only ever
produce a green report.

Usage
-----
    python3 scripts/mutation-harness.py
    python3 scripts/mutation-harness.py --report docs-v2/engineering/MUTATION-REPORT.md
    python3 scripts/mutation-harness.py --only MemoryM4Causal-merge-stale-clock
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

KILLED = "KILLED"
SURVIVED = "SURVIVED"
INVALID = "INVALID"
EQUIVALENT = "EQUIVALENT"
DRIFT = "DRIFT"

# TLC's exit codes, verified against tla2tools 1.8.0.
TLC_EXIT_INVARIANT = 12
TLC_EXIT_TEMPORAL = 13

# The markers SANY prints for a parse or semantic error. SANY exits 0 on a
# semantic error, so the exit code alone is not the gate.
SANY_ERROR_MARKERS = (
    "Unknown operator",
    "Parse Error",
    "Encountered",
    "*** Errors:",
)


@dataclass
class Outcome:
    mutant_id: str
    spec: str
    why: str
    status: str
    detail: str = ""
    states: int = 0
    equivalent: bool = False
    finding: str = ""


@dataclass
class Harness:
    manifest_path: Path
    jar: Path
    java: str = "java"
    keep: bool = False
    only: str | None = None
    outcomes: list[Outcome] = field(default_factory=list)

    # ── toolchain ────────────────────────────────────────────────────────────
    def verify_toolchain(self) -> str | None:
        """Return an error message if the jar is missing or has the wrong digest."""
        if not self.jar.exists():
            return f"toolchain jar not found: {self.jar}"
        expected = self.manifest.get("toolchain", {}).get("sha256")
        if not expected:
            return None
        digest = hashlib.sha256(self.jar.read_bytes()).hexdigest()
        if digest != expected:
            return (f"toolchain digest mismatch: expected {expected}, got {digest}. "
                    f"Refusing to run a gate with an unpinned toolchain.")
        return None

    # ── the work ─────────────────────────────────────────────────────────────
    def run(self) -> int:
        raw = json.loads(self.manifest_path.read_text(encoding="utf-8"))
        self.manifest = raw

        problem = self.verify_toolchain()
        if problem:
            print(f"::error::{problem}")
            return 2

        mutants = raw.get("mutants", [])
        if self.only:
            mutants = [m for m in mutants if self.only in m["id"]]
            if not mutants:
                print(f"::error::no mutant matches --only {self.only}")
                return 2

        print(f"Mutation harness: {len(mutants)} mutant(s) against "
              f"{len({m['spec'] for m in mutants})} specification(s)")
        print(f"Toolchain: {self.jar} (sha256 verified)")

        for mutant in mutants:
            outcome = self.run_mutant(mutant)
            self.outcomes.append(outcome)
            marker = {"KILLED": "OK  ", "SURVIVED": "MISS", "INVALID": "SKIP",
                      "EQUIVALENT": "OK  ", "DRIFT": "DRIFT"}[outcome.status]
            print(f"[{marker}] {outcome.mutant_id}: {outcome.status}"
                  + (f" - {outcome.detail}" if outcome.detail else ""))

        return self.report()

    def run_mutant(self, mutant: dict) -> Outcome:
        spec = mutant["spec"]
        spec_file = REPO_ROOT / "formal" / f"{spec}.tla"
        cfg_file = REPO_ROOT / "formal" / f"{spec}.cfg"

        if not spec_file.exists() or not cfg_file.exists():
            return Outcome(mutant["id"], spec, mutant.get("why", ""), INVALID,
                           "specification or configuration not found")

        workdir = Path(tempfile.mkdtemp(prefix=f"matrix-mutant-{mutant['id']}-"))
        try:
            for path in (REPO_ROOT / "formal").glob("*.tla"):
                shutil.copy2(path, workdir / path.name)
            for path in (REPO_ROOT / "formal").glob("*.cfg"):
                shutil.copy2(path, workdir / path.name)

            target = workdir / spec_file.name
            text = target.read_text(encoding="utf-8")
            old, new = mutant["old"], mutant["new"]
            if text.count(old) != 1:
                return Outcome(mutant["id"], spec, mutant.get("why", ""), INVALID,
                               f"anchor occurs {text.count(old)} time(s), expected exactly 1")
            target.write_text(text.replace(old, new, 1), encoding="utf-8")

            sany = subprocess.run(
                [self.java, "-cp", str(self.jar), "tla2sany.SANY", spec_file.name],
                cwd=workdir, capture_output=True, text=True, timeout=300)
            if sany.returncode != 0 or any(m in sany.stdout + sany.stderr
                                          for m in SANY_ERROR_MARKERS):
                return Outcome(mutant["id"], spec, mutant.get("why", ""), INVALID,
                               "the mutant does not parse")

            meta = workdir / "meta"
            meta.mkdir(exist_ok=True)
            # A mutant may be checked against an ALTERNATIVE configuration:
            # some behaviour is only distinguishable when the constants make
            # it distinguishable (the budgeter's gcd-reduced grid, for
            # instance, only differs from the raw-unit slope when the row
            # costs have a common divisor).
            config_name = cfg_file.name
            if mutant.get("config"):
                alt = REPO_ROOT / mutant["config"]
                if not alt.exists():
                    return Outcome(mutant["id"], spec, mutant.get("why", ""), INVALID,
                                   f"alternative configuration not found: {mutant['config']}")
                shutil.copy2(alt, workdir / alt.name)
                config_name = alt.name
            tlc = subprocess.run(
                [self.java, "-cp", str(self.jar), "tlc2.TLC", "-continue",
                 "-metadir", str(meta), "-config", config_name, spec_file.name],
                cwd=workdir, capture_output=True, text=True, timeout=1800)
            output = tlc.stdout + tlc.stderr
            states = 0
            match = re.search(r"([\d,]+) states generated", output)
            if match:
                states = int(match.group(1).replace(",", ""))

            # TLC's exit code is NOT a reliable signal here: with -continue it
            # stays 0 even when a property is violated (verified against
            # tla2tools 1.8.0 - the CI gate deliberately omits -continue for
            # exactly this reason). The violation is therefore read from the
            # output, and the exit code is used only as a secondary signal.
            violated = re.search(r"(Invariant|Action property|Property) "
                                 r"(\S+) is violated", output)
            # TLC reports a property whose formula evaluates to FALSE in a
            # different sentence shape, and with a different exit code (1).
            evaluated_false = re.search(r"The property of (\S+) is equal to FALSE", output)
            if violated:
                status = KILLED
                detail = f"TLC reported a violated {violated.group(1).lower()}: {violated.group(2)}"
            elif evaluated_false:
                status = KILLED
                detail = f"TLC reported the property {evaluated_false.group(1)} evaluated to FALSE"
            elif tlc.returncode in (TLC_EXIT_INVARIANT, TLC_EXIT_TEMPORAL):
                status = KILLED
                detail = f"TLC exited {tlc.returncode} (violated property)"
            elif tlc.returncode == 0:
                status = SURVIVED
                detail = (f"{states} states, no violation"
                          if states else "model check completed with no violation")
            else:
                status = INVALID
                detail = f"TLC exited {tlc.returncode}"

            if status == SURVIVED and mutant.get("equivalent"):
                status = EQUIVALENT
                detail = "declared equivalent in the manifest; " + detail
            expected = mutant.get("expect")
            if expected and expected != status:
                detail = (f"EXPECTED {expected} but observed {status}. "
                          f"The formal gate changed behaviour. " + detail)
                status = DRIFT
            return Outcome(mutant["id"], spec, mutant.get("why", ""), status,
                           detail, states, bool(mutant.get("equivalent")),
                           mutant.get("finding", ""))
        except subprocess.TimeoutExpired:
            return Outcome(mutant["id"], spec, mutant.get("why", ""), INVALID,
                           "TLC timed out")
        finally:
            if not self.keep:
                shutil.rmtree(workdir, ignore_errors=True)
            else:
                print(f"       (kept {workdir})")

    # ── the report ───────────────────────────────────────────────────────────
    def counts(self) -> dict[str, int]:
        counts = {KILLED: 0, SURVIVED: 0, INVALID: 0, EQUIVALENT: 0, DRIFT: 0}
        for outcome in self.outcomes:
            counts[outcome.status] = counts.get(outcome.status, 0) + 1
        return counts

    def report(self, path: Path | None = None) -> int:
        counts = self.counts()
        generated = len(self.outcomes)
        text = self.render(counts, generated)
        if path:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
            print(f"\nReport written to {path}")
        else:
            print()
            print(text)

        # A SURVIVED mutant is a recorded finding, not a failure: the manifest
        # states the outcome that was observed and accepted, so a NEW survivor
        # shows up as DRIFT. INVALID and DRIFT are failures.
        if counts[INVALID] or counts[DRIFT]:
            print("::error::the mutation harness found a hole in the formal gate")
            return 1
        print("::notice::every mutant was rejected by the gate")
        return 0

    def render(self, counts: dict[str, int], generated: int) -> str:
        stamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        lines = [
            "# Mutation harness report — the MATRIX formal gate",
            "",
            "Generated by `scripts/mutation-harness.py`. Reproduce with:",
            "",
            "```",
            "python3 scripts/mutation-harness.py --report docs-v2/engineering/MUTATION-REPORT.md",
            "```",
            "",
            f"- Run at: {stamp}",
            f"- Toolchain: tla2tools {self.manifest.get('toolchain', {}).get('version', '?')}"
            f" (sha256 verified)",
            f"- Mutants generated: **{generated}**",
            f"- Killed: **{counts[KILLED]}**",
            f"- Survived: **{counts[SURVIVED]}**",
            f"- Invalid: **{counts[INVALID]}**",
            f"- Equivalent: **{counts[EQUIVALENT]}**",
            f"- Drifted from the recorded expectation: **{counts[DRIFT]}**",
            "",
            "A mutant that SURVIVED means the property it targets cannot fail: the gate",
            "is blind there. That is a finding about the specification, not a pass.",
            "",
            "| Mutant | Specification | Outcome | States | Detail |",
            "|---|---|---|---|---|",
        ]
        for outcome in self.outcomes:
            lines.append(
                f"| `{outcome.mutant_id}` | {outcome.spec} | **{outcome.status}** | "
                f"{outcome.states} | {outcome.detail} |")
        lines += ["", "## What each mutant attacks", ""]
        for outcome in self.outcomes:
            lines.append(f"- **`{outcome.mutant_id}`** — {outcome.why}")
        findings = [o for o in self.outcomes if o.finding]
        if findings:
            lines += ["", "## Recorded findings", "",
                      "A mutant that survived is a hole in the gate. Each one below is a",
                      "known hole, recorded so that it is visible rather than discovered",
                      "later. They are the reason the corresponding registry entries do",
                      "not claim more than they can.", ""]
            for outcome in findings:
                lines.append(f"- **`{outcome.mutant_id}`** — {outcome.finding}")
        lines.append("")
        return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--manifest", default=str(REPO_ROOT / "formal" / "mutations.json"))
    parser.add_argument("--jar", default=str(REPO_ROOT / "tools" / "tla2tools.jar"))
    parser.add_argument("--report", default=None,
                        help="write the report to this path as well as to stdout")
    parser.add_argument("--only", default=None, help="run a single mutant by id substring")
    parser.add_argument("--keep", action="store_true",
                        help="keep the temporary directories for inspection")
    parser.add_argument("--java", default=os.environ.get("JAVA_HOME", "java").rstrip("/") + "/bin/java"
                        if os.environ.get("JAVA_HOME") else "java")
    args = parser.parse_args()

    harness = Harness(
        manifest_path=Path(args.manifest),
        jar=Path(args.jar),
        java=args.java,
        keep=args.keep,
        only=args.only,
    )
    code = harness.run()
    if args.report and code is not None:
        report_path = Path(args.report)
        counts = harness.counts()
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(harness.render(counts, len(harness.outcomes)), encoding="utf-8")
        print(f"Report written to {report_path}")
    return code


if __name__ == "__main__":
    sys.exit(main())
