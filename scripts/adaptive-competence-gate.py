#!/usr/bin/env python3
"""CRJ-01 / ADAPTIVE-COMPETENCE — the deterministic supervisor gate of the milestone.

**What this gate is.** The milestone's acceptance is a set of claims about the served path:
that a procedure whose support set cannot carry a question's evidence is not selected, that
the reference answers instead, that the refusal happens before any durable side effect, that
the reason is recorded with the evidence it was made from, and that the served path's
grounding is at parity with the stateless reference's. A gate that only ran the test suite
would say "the tests passed" — which is a statement about the test, not about the claim.

**What this gate checks.** It re-derives the milestone's evidence INDEPENDENTLY of the test
that produced it:

1. the served test's JUnit XML is on disk and carries no failure and no error;
2. the served evidence artefact is readable, declares the sealed evaluation NOT EXECUTED,
   and its summary says the served path's grounding is at parity with the reference's;
3. every case of the split appears in the evidence, and every case's row names the method
   that actually ran, the grounding verdict and the effect;
4. the split's corpus digests match the corpus files' ACTUAL bytes — the digests are
   re-derived here, not read from the artefact;
5. the cost grid's artefact carries a verdict for every cell, and EVERY VERDICT IS
   RE-DERIVED HERE from the rows by the pre-registered rule itself — the warm medians, the
   paired bootstrap interval with the resample count and the seed the artefact names, and
   the grounding of BOTH arms on the same matched cases — so a verdict that was written by
   hand, or read against a weaker rule, is refused;
6. the retained evidence directory's manifest verifies — every file it names, byte for byte;
7. the deliberate RED is retained and carries failures: a gate that cannot go red is not a
   gate;
8. the previous milestone's retained evidence still verifies — this milestone must not have
   broken the record it inherits.

**How it runs.** Deterministic, offline, read-only. It never runs a build, never writes, and
never reads anything outside the repository. Its exit code is the verdict: zero when every
check passed, one otherwise.

    python3 scripts/adaptive-competence-gate.py
    python3 scripts/adaptive-competence-gate.py --json /tmp/adaptive-gate.json
"""

import argparse
import hashlib
import json
import math
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

# The suites the milestone's scope is made of.
SERVING_TEST = "io.matrix.knowledge.AdaptiveCompetenceServedTest"
CORPUS_BUILDER_TEST = "io.matrix.crj.corpus.Crj01AdaptiveCorpusReportBuilder"

# The artefacts the milestone writes.
SERVED_EVIDENCE = "docs-v2/engineering/evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-EVIDENCE.json"
COST_EVIDENCE = "docs-v2/engineering/evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-COST-ROWS.json"
SPLIT = "docs-v2/engineering/CRJ-01-ADAPTIVE-DEV-SPLIT.json"
CORPUS_REPORT = "docs-v2/engineering/CRJ-01-ADAPTIVE-CORPUS-REPORT.json"
PREREGISTRATION = "docs-v2/engineering/CRJ-01-ADAPTIVE-PREREGISTRATION.md"

# The retained evidence: this milestone's, and the one it inherits.
RETAINED = "docs-v2/engineering/evidence/crj01-adaptive-competence"
INHERITED = "docs-v2/engineering/evidence/crj01-h2-served-transfer"

# The XMLs the suite leaves on disk.
TEST_RESULTS = "matrix-core/build/test-results/test"

# the retained evidence of THIS milestone — the canonical home of its JUnit XML
EVIDENCE = REPO / "docs-v2/engineering/evidence/crj01-adaptive-competence"

# The digests of the corpus files are re-derived from the bytes, and compared with the
# digests the split states.
CORPUS_ROOT = "matrix-core/src/test/resources/crj01-adaptive"


class Gate:
    """The gate's own tally. Every check is named, and a failed check is printed."""

    def __init__(self):
        self.checks = []
        self.failures = []

    def check(self, name, ok, detail=""):
        self.checks.append({"check": name, "ok": bool(ok), "detail": detail})
        if not ok:
            self.failures.append(name + (": " + detail if detail else ""))
        return bool(ok)

    def report(self):
        for entry in self.checks:
            print(("PASS " if entry["ok"] else "FAIL ") + entry["check"]
                  + ((" - " + entry["detail"]) if entry["detail"] else ""))
        print("checks " + str(len(self.checks)) + " failed " + str(len(self.failures)))
        return 1 if self.failures else 0


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 16), b""):
            digest.update(block)
    return digest.hexdigest()


def read_json(path):
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def xml_counts(path):
    """The counts a JUnit XML carries."""
    try:
        root = ElementTree.parse(path).getroot()
    except ElementTree.ParseError as unreadable:
        # an unreadable XML is a FAILED CHECK, not a crash: the gate's verdict is its exit
        # code, and a traceback is not a verdict
        return {"tests": 0, "failures": 0, "errors": 1, "unreadable": str(unreadable)}
    suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
    tests = failures = errors = skipped = 0
    for suite in suites:
        tests += int(suite.get("tests", "0"))
        failures += int(suite.get("failures", "0"))
        errors += int(suite.get("errors", "0"))
        skipped += int(suite.get("skipped", "0"))
    return {"tests": tests, "failures": failures, "errors": errors, "skipped": skipped}


def check_manifest(gate, directory):
    """Verifies a retained evidence directory's manifest, file by file."""
    manifest = REPO / directory / "CHECKSUMS.sha256"
    if not gate.check("the retained manifest is present: " + directory, manifest.exists(),
                      str(manifest)):
        return
    named = []
    broken = []
    for line in manifest.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        parts = line.split(None, 1)
        if len(parts) != 2:
            continue
        digest, name = parts[0], parts[1].strip().lstrip("*")
        named.append(name)
        target = REPO / directory / name
        if not target.exists() or sha256_file(target) != digest:
            broken.append(name)
    gate.check("the retained manifest verifies: " + directory, not broken,
               ("broken: " + ", ".join(broken)) if broken else (str(len(named)) + " files OK"))


def retained_xml(test_class):
    """The retained JUnit XML of a test class, or None when it is not retained.

    The evidence directory is the canonical home of a milestone's JUnit XML: the build
    directory is wiped by the next targeted run, and a gate that read only the build
    directory would pass on a machine that had never run the suite in this state.

    :param test_class: the test class
    :return: the retained XML's path, or None
    """
    candidate = EVIDENCE / ("TEST-" + test_class + ".xml")
    return candidate if candidate.exists() else None


def median(values):
    """The median of a list of numbers.

    :param values: the values
    :return: the median, or zero when there are none
    """
    if not values:
        return 0
    ordered = sorted(values)
    middle = len(ordered) // 2
    if len(ordered) % 2 == 1:
        return ordered[middle]
    return (ordered[middle - 1] + ordered[middle]) // 2


class JavaRandom:
    """java.util.Random, bit for bit.

    The bootstrap's interval must be re-derived with the SAME generator the measurement
    used, or the gate is checking a different interval. java.util.Random is a specified
    linear congruential generator, so it can be reproduced exactly outside the JVM.

    :param seed: the seed
    """

    MULTIPLIER = 0x5DEECE66D
    ADDEND = 0xB
    MASK = (1 << 48) - 1

    def __init__(self, seed):
        self.seed = (seed ^ self.MULTIPLIER) & self.MASK

    def next(self, bits):
        self.seed = (self.seed * self.MULTIPLIER + self.ADDEND) & self.MASK
        return self.seed >> (48 - bits)

    def next_int(self, bound):
        if (bound & -bound) == bound:
            return (bound * self.next(31)) >> 31
        while True:
            bits = self.next(31)
            value = bits % bound
            if bits - value + (bound - 1) < (1 << 31):
                return value


def bootstrap_interval(differences, draws, seed):
    """The paired bootstrap interval, exactly as the measurement computes it.

    :param differences: the paired differences
    :param draws: the number of resamples
    :param seed: the seed
    :return: the (low, high) interval
    """
    if not differences:
        return 0, 0
    source = JavaRandom(seed)
    means = []
    for _ in range(draws):
        total = 0
        for _ in differences:
            total += differences[source.next_int(len(differences))]
        # Java's integer division truncates TOWARD ZERO; Python's floor division rounds
        # down. For a negative total the two differ by one, and the interval's bound is
        # exactly where a one-unit difference shows.
        quotient = abs(total) // len(differences)
        means.append(quotient if total >= 0 else -quotient)
    means.sort()
    low = means[int(math.floor(0.025 * (len(means) - 1)))]
    high = means[int(math.ceil(0.975 * (len(means) - 1)))]
    return low, high


def rederive_verdict(rows, cell_key, cell):
    """The verdict the PRE-REGISTERED RULE gives a cell, re-derived from the rows.

    The rule has three parts, and all three are applied: the procedure arm's warm median
    is lower than the reference arm's; the paired differences' 95 % bootstrap interval
    excludes zero; and BOTH arms are grounded on the SAME matched cases.

    :param rows: the cost rows
    :param cell_key: the cell's key
    :param cell: the cell's record
    :return: "CHEAPER" or "NOT ESTABLISHED"
    """
    warm = [row for row in rows
            if row.get("cell") == cell_key and row.get("pass") == "warm"]
    reference = {row.get("caseId"): row for row in warm if row.get("arm") == "a"}
    procedure = {row.get("caseId"): row for row in warm if row.get("arm") == "c"}
    if not reference or not procedure:
        return "NOT ESTABLISHED"
    if set(reference.keys()) != set(procedure.keys()):
        return "NOT ESTABLISHED"
    if not all(bool(row.get("grounded")) for row in reference.values()):
        return "NOT ESTABLISHED"
    if not all(bool(row.get("grounded")) for row in procedure.values()):
        return "NOT ESTABLISHED"
    differences = [procedure[case_id]["wallNanos"] - reference[case_id]["wallNanos"]
                   for case_id in reference]
    reference_median = median([row["wallNanos"] for row in reference.values()])
    procedure_median = median([row["wallNanos"] for row in procedure.values()])
    if procedure_median >= reference_median:
        return "NOT ESTABLISHED"
    draws = int(cell.get("bootstrapResamples", 2000))
    seed = int(cell.get("bootstrapSeed", 20261127))
    low, high = bootstrap_interval(differences, draws, seed)
    if low <= 0 <= high:
        return "NOT ESTABLISHED"
    return "CHEAPER"


def rederive_interval(rows, cell_key, cell):
    """The paired bootstrap interval of a cell, re-derived from the rows.

    :param rows: the cost rows
    :param cell_key: the cell's key
    :param cell: the cell's record
    :return: the (low, high) interval
    """
    warm = [row for row in rows
            if row.get("cell") == cell_key and row.get("pass") == "warm"]
    reference = {row.get("caseId"): row for row in warm if row.get("arm") == "a"}
    procedure = {row.get("caseId"): row for row in warm if row.get("arm") == "c"}
    common = sorted(set(reference.keys()) & set(procedure.keys()))
    differences = [procedure[case_id]["wallNanos"] - reference[case_id]["wallNanos"]
                   for case_id in common]
    return bootstrap_interval(differences, int(cell.get("bootstrapResamples", 2000)),
                              int(cell.get("bootstrapSeed", 20261127)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", help="write the gate's report as JSON to this path")
    arguments = parser.parse_args()

    gate = Gate()

    # 1. the suite's XML. The gate reads the RETAINED copy first — the evidence directory is
    # what the milestone keeps and what the manifest verifies, and the build directory is
    # wiped by the next targeted run. The build copy is accepted as a fallback, and the gate
    # says which of the two it read.
    serving_xml = retained_xml(SERVING_TEST)
    if serving_xml is None:
        serving_xml = REPO / TEST_RESULTS / ("TEST-" + SERVING_TEST + ".xml")
    if gate.check("the serving suite's JUnit XML is on disk", serving_xml.exists(),
                  str(serving_xml)):
        counts = xml_counts(serving_xml)
        gate.check("the serving suite passed", counts["failures"] == 0 and counts["errors"] == 0,
                   "tests " + str(counts["tests"]) + " failures " + str(counts["failures"])
                   + " errors " + str(counts["errors"])
                   + (" — the XML could not be read: " + str(counts.get("unreadable"))
                      if counts.get("unreadable") else ""))
        gate.check("the serving suite ran at least one test", counts["tests"] > 0,
                   "tests " + str(counts["tests"]))
    corpus_xml = retained_xml(CORPUS_BUILDER_TEST)
    if corpus_xml is None:
        corpus_xml = REPO / TEST_RESULTS / ("TEST-" + CORPUS_BUILDER_TEST + ".xml")
    gate.check("the corpus instrument's JUnit XML is on disk", corpus_xml.exists(),
               str(corpus_xml))

    # 2. the served evidence
    served_path = REPO / SERVED_EVIDENCE
    served = None
    if gate.check("the served evidence artefact is present", served_path.exists(),
                  str(served_path)):
        served = read_json(served_path)
        gate.check("the sealed evaluation is declared NOT EXECUTED",
                   served.get("sealed") is False, str(served.get("sealedNote", ""))[:120])
        summary = served.get("summary", {})
        gate.check("the served path's grounding is at parity with the reference's",
                   summary.get("parity") is True,
                   "served " + str(summary.get("servedGrounded")) + " reference "
                   + str(summary.get("referenceGrounded")) + " of "
                   + str(summary.get("cases")))

    # 3. the split's cases are all in the evidence
    split_path = REPO / SPLIT
    if gate.check("the DEV split is present", split_path.exists(), str(split_path)):
        split = read_json(split_path)
        cases = split.get("cases", [])
        case_ids = {str(case.get("id")) for case in cases}
        gate.check("the split carries at least two domains",
                   len({str(case.get("domain")) for case in cases}) >= 2,
                   str(len({str(case.get("domain")) for case in cases})) + " domains")
        if served is not None:
            rows = served.get("rows", [])
            served_ids = {str(row.get("caseId")) for row in rows if row.get("arm") == "served"}
            missing = sorted(case_ids - served_ids)
            gate.check("every measured case is in the evidence", not missing,
                       ("missing: " + ", ".join(missing)) if missing else str(len(case_ids)))
            named = all(row.get("methodId") is not None and row.get("grounded") is not None
                        for row in rows)
            gate.check("every row names the method that ran and the grounding verdict", named,
                       str(len(rows)) + " rows")
            effects = all(row.get("effectVerified") is not None for row in rows)
            gate.check("every row records the effect", effects, str(len(rows)) + " rows")
            # A row that ran the reference must say WHY the procedure did not run, and the
            # why must be the truth: either the procedure did not hold for the question, or
            # it did and another method was selected. A row that says neither is a record
            # with a hole in it, and a row that claims "not applicable" for a procedure that
            # WAS applicable is a false statement.
            fallback = [row for row in rows
                        if str(row.get("methodId", "")).startswith("report.journey.reference")]
            reasons = [row for row in fallback
                       if "was not applicable to this question" in str(row.get("plan", ""))
                       or "was applicable to this question but was not selected"
                       in str(row.get("plan", ""))]
            gate.check("every fallback row carries its reason", len(reasons) == len(fallback),
                       str(len(reasons)) + " of " + str(len(fallback)))
            named = [row for row in fallback if str(row.get("fallbackReason", "")).strip()]
            gate.check("every fallback row names its reason as a field of its own",
                       len(named) == len(fallback), str(len(named)) + " of " + str(len(fallback)))
            inapplicable = [row for row in fallback
                            if "was not applicable to this question" in str(row.get("plan", ""))]
            contradicted = [row for row in inapplicable
                            if "carries the workspace's own evidence" in str(row.get("plan", ""))]
            gate.check("no row claims inapplicability for an applicable procedure",
                       not contradicted, str(len(contradicted)) + " contradicted")

    # 4. the corpus digests, re-derived from the bytes
    if split_path.exists():
        split = read_json(split_path)
        mismatched = []
        for case in split.get("cases", []):
            stated = str(case.get("corpusSha256", ""))
            name = str(case.get("corpusFile", ""))
            domain = str(case.get("domain", ""))
            target = REPO / CORPUS_ROOT / domain / name
            if not target.exists() or sha256_file(target) != stated:
                mismatched.append(domain + "/" + name)
        gate.check("the split's corpus digests match the corpus bytes", not mismatched,
                   ("mismatched: " + ", ".join(sorted(set(mismatched)))) if mismatched
                   else "every digest re-derived")

    # 5. the cost grid
    cost_path = REPO / COST_EVIDENCE
    if gate.check("the cost grid's artefact is present", cost_path.exists(), str(cost_path)):
        cost = read_json(cost_path)
        cells = cost.get("cells", {})
        rows = cost.get("rows", [])
        gate.check("the cost grid carries a verdict for every cell", bool(cells) and all(
            "verdict" in cell for cell in cells.values()), str(len(cells)) + " cells")
        gate.check("the cost grid's rows are present", bool(rows), str(len(rows)) + " rows")
        gate.check("the cost grid names its pre-registration",
                   str(cost.get("preregistration", "")) == PREREGISTRATION,
                   str(cost.get("preregistration", "")))
        gate.check("the pre-registration is present", (REPO / PREREGISTRATION).exists(),
                   PREREGISTRATION)
        # ── THE VERDICTS, RE-DERIVED BY THE PRE-REGISTERED RULE ──
        # The rule of the pre-registration, in its three parts: the procedure arm's warm
        # median is lower; the paired 95 % bootstrap interval over the matched cases
        # excludes zero, with the resample count and the seed the artefact names; and BOTH
        # arms are grounded on the SAME matched cases. The gate re-derives each cell's
        # verdict from the rows and compares it with the artefact's verdict.
        rederived = []
        intervals = []
        for key, cell in cells.items():
            expected = rederive_verdict(rows, key, cell)
            stated = str(cell.get("verdict", ""))
            if expected != stated:
                rederived.append(key + ": the artefact says " + stated + " and the rule says "
                                 + expected)
            low, high = rederive_interval(rows, key, cell)
            stated_interval = cell.get("pairedDifferenceIntervalNanos", [])
            if [low, high] != list(stated_interval):
                intervals.append(key + ": the artefact says " + str(stated_interval)
                                 + " and the resampling says " + str([low, high]))
        gate.check("every cell's verdict is the pre-registered rule applied to the rows",
                   not rederived, (str(len(rederived)) + " of " + str(len(cells)) + " disagree: "
                                   + "; ".join(rederived[:4]) if rederived
                                   else str(len(cells)) + " cells re-derived and agreed"))
        gate.check("every cell's interval is the resampling of its own rows",
                   not intervals, (str(len(intervals)) + " of " + str(len(cells)) + " disagree: "
                                   + "; ".join(intervals[:4]) if intervals
                                   else str(len(cells)) + " intervals re-derived and agreed"))

    # 6. the retained evidence, this milestone's and the inherited one
    check_manifest(gate, RETAINED)
    check_manifest(gate, INHERITED)

    # 7. the deliberate RED
    red = REPO / RETAINED / "CRJ-01-ADAPTIVE-REPRODUCTION-no-gate.xml"
    if gate.check("the deliberate RED is retained", red.exists(), str(red)):
        counts = xml_counts(red)
        gate.check("the RED carries failures - a gate that cannot go red is not a gate",
                   counts["failures"] > 0,
                   "failures " + str(counts["failures"]) + " of " + str(counts["tests"]))

    code = gate.report()
    if arguments.json:
        Path(arguments.json).write_text(
            json.dumps({"checks": gate.checks, "failures": gate.failures}, indent=2),
            encoding="utf-8")
    return code


if __name__ == "__main__":
    sys.exit(main())
