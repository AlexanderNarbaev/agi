#!/usr/bin/env python3
"""W51 §6 — the epistemic evaluation runner.

It runs the 29 cases of the versioned dataset
(``matrix-core/src/main/resources/epistemic-eval/cases.json``) against a
LOCAL instance of the application and reports every case and every category.

THE BOUNDARY
------------
The application is started with the JVM's HTTP and HTTPS clients pointed at
a closed port on the loopback interface, so a request cannot leave the
machine even if some code tried to make one. The corpus the cases are
measured against is the licensed, pre-cached pool that ships inside the
application; no live acquisition happens and no model is called.

WHAT IT PRINTS
--------------
One line per case with the verdict, the expectation and what was observed,
then a summary per category, then the totals. A case that honestly fails
stays a failure: the runner never adjusts an expectation to make a case
pass, and it never excludes a case.

WHAT IT WRITES
--------------
A machine-readable report in ``docs-v2/engineering/`` with the same numbers.

USAGE
-----
    scripts/w51-epistemic-eval.py [--port N] [--keep-running] [--base URL]

The runner starts its own instance by default. With ``--base URL`` it uses
an instance that is already running, and it does not start or stop anything.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATASET = os.path.join(ROOT, "matrix-core", "src", "main", "resources",
                        "epistemic-eval", "cases.json")
REPORT_DIR = os.path.join(ROOT, "docs-v2", "engineering")
REPORT_NAME = "W51-EPISTEMIC-EVALUATION.json"
JAR = os.path.join(ROOT, "matrix-core", "build", "quarkus-app", "quarkus-run.jar")

# The constants the quality gate wants named rather than written inline.
DEFAULT_PORT = 9098
CLOSED_PROXY_PORT = 9
STARTUP_TIMEOUT_SECONDS = 600
STARTUP_POLL_SECONDS = 3
REQUEST_TIMEOUT_SECONDS = 1800
POLL_SECONDS = 1


# ── the application ─────────────────────────────────────────────────────────

def start_application(port: int, work: str) -> subprocess.Popen:
    """Starts the packaged application with every outbound client closed."""
    if not os.path.exists(JAR):
        raise SystemExit(
            "w51-eval: %s is missing; run ./gradlew :matrix-core:quarkusBuild first" % JAR)
    shutil.rmtree(work, ignore_errors=True)
    os.makedirs(work, exist_ok=True)
    java = os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "java") \
        if os.environ.get("JAVA_HOME") else "java"
    environment = dict(os.environ)
    environment["QUARKUS_HTTP_PORT"] = str(port)
    environment["MATRIX_KNOWLEDGE_PATH"] = os.path.join(work, "knowledge.sqlite")
    log = open(os.path.join(work, "app.log"), "w", encoding="utf-8")
    process = subprocess.Popen(
        [java,
         "-Dhttp.proxyHost=127.0.0.1", "-Dhttp.proxyPort=%d" % CLOSED_PROXY_PORT,
         "-Dhttps.proxyHost=127.0.0.1", "-Dhttps.proxyPort=%d" % CLOSED_PROXY_PORT,
         "-Dhttp.nonProxyHosts=", "-Dhttps.nonProxyHosts=",
         "-jar", JAR],
        stdout=log, stderr=subprocess.STDOUT, env=environment,
        cwd=ROOT, start_new_session=True)
    deadline = time.time() + STARTUP_TIMEOUT_SECONDS
    while time.time() < deadline:
        if process.poll() is not None:
            raise SystemExit("w51-eval: the application exited; see %s/app.log" % work)
        try:
            with open(os.path.join(work, "app.log"), encoding="utf-8",
                      errors="replace") as fh:
                if "Listening on:" in fh.read():
                    return process
        except OSError:
            pass
        time.sleep(STARTUP_POLL_SECONDS)
    raise SystemExit("w51-eval: the application did not start; see %s/app.log" % work)


def stop_application(process: subprocess.Popen | None) -> None:
    if process is None:
        return
    try:
        os.killpg(os.getpgid(process.pid), signal.SIGTERM)
        process.wait(timeout=30)
    except (OSError, subprocess.TimeoutExpired):
        try:
            process.kill()
        except OSError:
            pass


# ── the calls ───────────────────────────────────────────────────────────────

def call(base: str, method: str, path: str, body: Any = None) -> Any:
    url = base + path
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as failure:
        raw = failure.read().decode("utf-8", "replace")
        return {"_httpError": failure.code, "_body": raw}
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return {"_raw": raw}


def prepare_scope(base: str, scope: dict) -> list[dict]:
    """Runs the calls that bring a workspace into the state a scope names.

    The calls and what they produced are RETURNED, so that the artifact
    carries the import: which workspace, which call, how many documents and
    how many claims. A run whose corpus did not arrive is not a measurement
    of the cases, and the artifact has to say so.
    """
    workspace = scope["workspace"]
    steps: list[dict] = []
    for step in scope["prepare"]:
        path = step["path"].replace("{workspace}", urllib.parse.quote(workspace))
        query = step.get("query") or {}
        if query:
            path += "?" + urllib.parse.urlencode(query)
        response = call(base, step["method"], path)
        record = {
            "call": step["method"] + " " + step["path"] + ("?" + urllib.parse.urlencode(query)
                                                            if query else ""),
            "workspace": workspace,
            "httpError": response.get("_httpError") if isinstance(response, dict) else None,
            "documentsAcquired": response.get("documentsAcquired")
            if isinstance(response, dict) else None,
            "claimsAdded": response.get("claimsAdded") if isinstance(response, dict) else None,
            "acquired": response.get("acquired") if isinstance(response, dict) else None,
            "revisionsApplied": response.get("revisionsApplied")
            if isinstance(response, dict) else None,
        }
        steps.append(record)
        if record["httpError"] is not None:
            raise SystemExit("w51-eval: the preparation call %s failed with HTTP %s"
                             % (record["call"], record["httpError"]))
    return steps


def run_case(base: str, case: dict, scope: dict) -> Any:
    """Runs the call of one case and returns the response."""
    workspace = scope["workspace"]
    call_spec = case["call"]
    path = call_spec["path"].replace("{workspace}", urllib.parse.quote(workspace))
    query = call_spec.get("query") or {}
    if query:
        path += "?" + urllib.parse.urlencode(query)
    body = call_spec.get("body")
    if body is not None:
        body = dict(body)
        if "{question}" in body:
            body["question"] = case["question"]
        if "{assertion}" in body:
            body["assertion"] = case["assertion"]
    return call(base, call_spec["method"], path, body)



# ── where each kind of failure is decided ───────────────────────────────────
# The lead's requirement: a symptom is not a cause. Every problem the
# runner reports names the file and the line where the decision was made,
# so that a reader can go and look at it.

CAUSE_RETRIEVAL = ("KnowledgeRetriever.java:386 — the relevance floor and the status filter: "
                    "a claim reaches the answer's evidence only when it covers "
                    "DEFAULT_MINIMUM_COVERAGE (0.25) of the question's terms AND its status is "
                    "in EpistemicPolicy.CITABLE_STATUSES")
CAUSE_ABSTENTION = ("KnowledgeRetriever.java:446 — the abstention: the retrieval found nothing "
                     "citable and the answer says so")
CAUSE_DEMAND = ("KnowledgeWorkbench.java:634 — applyDemand: the demand analysis refuses the "
                "question before the answer is given")
CAUSE_PREMISE = ("KnowledgeWorkbench.java:613 — applyPremise: the premise of the question is "
                 "contradicted by the workspace")
CAUSE_GAPS = ("KnowledgeRetriever.java:713 — unmatchedTerms: the terms of the question that no "
              "claim of the workspace covers")
CAUSE_VERDICT = ("PremiseAssessment.java:592 — agreeOn: the verdict is decided by whether the "
                 "contradicting accounts settle each other")
CAUSE_REVISION = ("ModelRevisionDecider.java:52 — decide: whether the new evidence revises the "
                  "model, and which claim it revises")
CAUSE_REFINER = ("KnowledgeRefiner.java:100 — refine: the refinement that supersedes the claim "
                 "and records the revision")


def cause_for_abstention(response: dict) -> str:
    """Names the gate that refused the answer, from the reason it gave."""
    reason = (response.get("abstentionReason") or "").lower()
    if "premise" in reason:
        return CAUSE_PREMISE
    if "quantity" in reason or "measured quantity" in reason:
        return ("KnowledgeWorkbench.java:643 — the quantity gate: a question that asks for a "
                "number is answered only when the evidence states one "
                "(EpistemicPolicy.MINIMUM_QUANTITY_EVIDENCE)")
    if "content terms" in reason:
        return ("QuestionDemand.java:102 — MINIMUM_DEMAND_COVERAGE is 0.0, so the demand "
                "coverage does not refuse a knowledge question; "
                "KnowledgeWorkbench.java:650 is where that reason is written")
    if "established" in reason:
        return CAUSE_ABSTENTION
    if "covers enough" in reason:
        return CAUSE_ABSTENTION
    return ("LearningWorkbenchResource.java:480 — the ask route; the refusal reason is: %r"
            % response.get("abstentionReason"))

# ── the judgement ──────────────────────────────────────────────────────────

class Problem(dict):
    """A failure and the code that produced it."""

    def __init__(self, text: str, cause: str):
        super().__init__(problem=text, cause=cause)


def propositions(response: dict) -> list[str]:
    return [claim.get("proposition", "") for claim in response.get("claims") or []]


def distinct_claims(response: dict) -> int:
    return len({claim.get("id") for claim in response.get("claims") or []})


def distinct_sources(response: dict) -> int:
    return len({source.get("id") for source in response.get("sources") or []})


def source_titles(response: dict) -> set[str]:
    return {source.get("title") for source in response.get("sources") or []}


def stem(word: str) -> str:
    """The singular form of a word, for the comparison of two forms."""
    lowered = word.strip().lower()
    if lowered.endswith("ies") and len(lowered) > 4:
        return lowered[:-3] + "y"
    if lowered.endswith("es") and len(lowered) > 3:
        return lowered[:-2]
    if lowered.endswith("s") and len(lowered) > 2:
        return lowered[:-1]
    return lowered


def gap_names(response: dict, term: str) -> bool:
    """Whether the gap list NAMES the term.

    The dataset says "the gap list names the tsunami term" — the TERM, not
    one spelling of it. The corpus says "tsunamis" and the question says
    "tsunami", and the gap list holds the corpus's form.
    """
    wanted = stem(term)
    return any(stem(gap) == wanted for gap in gap_terms(response))


def gap_terms(response: dict) -> set[str]:
    terms: set[str] = set()
    for gap in response.get("gaps") or []:
        for word in str(gap).replace(",", " ").split():
            terms.add(word.strip().lower())
    return terms


def judge_answer(case: dict, response: dict) -> tuple[bool, list[str], str]:
    """Judges an answer case against the dataset's conditions."""
    expect = case["expect"]
    problems: list[str] = []
    answered = bool(response.get("answered"))
    cited = propositions(response)
    claims = distinct_claims(response)
    sources = distinct_sources(response)
    observed = "answered=%s; %d claims; %d sources" % (answered, claims, sources)

    if expect.get("answered") is not None and answered != expect["answered"]:
        problems.append(Problem(
            "answered=%s, expected %s" % (answered, expect["answered"]),
            cause_for_abstention(response) if not answered else CAUSE_RETRIEVAL))
    if expect.get("answered") is False:
        if (response.get("statement") or "").strip():
            problems.append(Problem("the statement is not empty", CAUSE_ABSTENTION))
        if response.get("sources"):
            problems.append(Problem("%d source(s) still cited" % len(response["sources"]),
                                    CAUSE_ABSTENTION))
        if expect.get("noClaims") and response.get("claims"):
            problems.append(Problem("%d claim(s) still cited" % len(response["claims"]),
                                    CAUSE_ABSTENTION))
    minimum_claims = expect.get("minDistinctClaims")
    if minimum_claims is not None and claims < minimum_claims:
        problems.append(Problem("only %d distinct claims (< %d)" % (claims, minimum_claims),
                                CAUSE_RETRIEVAL))
    minimum_sources = expect.get("minDistinctSources")
    if minimum_sources is not None and sources < minimum_sources:
        problems.append(Problem("only %d distinct sources (< %d)" % (sources, minimum_sources),
                                CAUSE_RETRIEVAL))
    required_source = expect.get("requiredSource")
    if required_source and required_source not in source_titles(response):
        problems.append(Problem("the source %s is absent" % required_source, CAUSE_RETRIEVAL))
    for proposition in expect.get("requiredPropositions") or []:
        if proposition not in cited:
            problems.append(Problem("the proposition is not cited: %s" % proposition[:90],
                                    CAUSE_RETRIEVAL))
    any_of = expect.get("requiredPropositionsAny")
    if any_of and not any(proposition in cited for proposition in any_of):
        problems.append(Problem("none of the named propositions is cited", CAUSE_RETRIEVAL))
    for term in expect.get("requiredGapTerms") or []:
        if not gap_names(response, term):
            problems.append(Problem("the gap list does not name %r (gaps=%s)"
                                    % (term, sorted(gap_terms(response))), CAUSE_GAPS))
    return not problems, problems, observed


def judge_premise(case: dict, response: dict) -> tuple[bool, list[str], str]:
    """Judges a false-premise case against the dataset's conditions."""
    expect = case["expect"]
    problems: list[str] = []
    findings = response.get("findings") or []
    verdicts = [finding.get("verdict") for finding in findings]
    verdict = verdicts[0] if verdicts else None
    evidence = [ref for finding in findings for ref in (finding.get("evidence") or [])]
    disputed = any(finding.get("disputedPremise") for finding in findings)
    observed = "verdict=%s; promoted=%s; disputed=%s; %d evidence ref(s)" % (
        verdict, response.get("promoted"), disputed, len(evidence))

    if verdict != expect["verdict"]:
        problems.append(Problem("the verdict is %s, expected %s" % (verdict, expect["verdict"]),
                                CAUSE_VERDICT))
    if response.get("promoted") is not False:
        problems.append(Problem("promoted=%s" % response.get("promoted"),
                                "PremiseAssessment.java:265 — assess: the assessment never writes"))
    if expect.get("noEvidence") and evidence:
        problems.append(Problem("evidence is named where none is expected", CAUSE_VERDICT))
    if expect.get("disputedPremise") == "named" and not disputed:
        problems.append(Problem("the disputed premise is not named", CAUSE_VERDICT))
    required_source = expect.get("requiredSource")
    if required_source and not any(ref.get("sourceTitle") == required_source for ref in evidence):
        problems.append(Problem("the source %s is not named" % required_source, CAUSE_VERDICT))
    for proposition in expect.get("requiredPropositions") or []:
        if not any(ref.get("proposition") == proposition for ref in evidence):
            problems.append(Problem("the claim is not named: %s" % proposition[:90], CAUSE_VERDICT))
    for fragment in expect.get("reasonContains") or []:
        reasons = " ".join(finding.get("reason") or "" for finding in findings)
        if fragment not in reasons:
            problems.append(Problem("the reason does not say %r" % fragment, CAUSE_VERDICT))
    return not problems, problems, observed


def judge_revision(case: dict, response: dict, claims_before: list[dict],
                   claims_after: list[dict]) -> tuple[bool, list[str], str]:
    """Judges a revision case against the dataset's conditions."""
    expect = case["expect"]
    problems: list[str] = []
    added = response.get("claimsAdded", 0)
    applied = response.get("revisionsApplied", 0)
    decisions = response.get("decisions") or []
    observed = "claimsAdded=%d; decisions=%d; revisionsApplied=%d" % (
        added, len(decisions), applied)

    if expect.get("claimsAddedGreaterThan") and added <= 0:
        problems.append(Problem("the document was not acquired",
                                "AcquisitionCycle.java:174 — acquireAndRevise: the document "
                                "was acquired but produced no claims"))
    if expect.get("revisionsApplied") == "none" and applied != 0:
        problems.append(Problem("%d supersessions applied, 0 expected" % applied,
                                CAUSE_REVISION))
    if expect.get("revisionsApplied") == "atLeastOne" and applied < 1:
        problems.append(Problem("no supersession applied", CAUSE_REVISION))
    reasons = [decision.get("reason") or "" for decision in decisions]
    if expect.get("requiredReasonAny"):
        if not any(any(fragment in reason for fragment in expect["requiredReasonAny"])
                   for reason in reasons):
            problems.append(Problem("no decision carries the reason the case names",
                                    CAUSE_REVISION))
    if expect.get("noClaimChangesStatus"):
        before = {claim["id"]: claim for claim in claims_before}
        changed = [claim["id"] for claim in claims_after
                   if claim["id"] in before
                   and (before[claim["id"]]["status"] != claim["status"]
                        or before[claim["id"]]["version"] != claim["version"])]
        if changed:
            problems.append(Problem("%d claim(s) changed status or version: %s"
                                    % (len(changed), ", ".join(changed[:4])), CAUSE_REFINER))
    required_claim = expect.get("requiredClaim")
    if required_claim:
        match = [claim for claim in claims_after
                 if claim.get("proposition") == required_claim["proposition"]]
        if not match:
            problems.append(Problem("the claim the case names is not in the workspace",
                                    CAUSE_REFINER))
        else:
            claim = match[0]
            if claim.get("status") != required_claim["status"]:
                problems.append(Problem("that claim is %s, expected %s"
                                        % (claim.get("status"), required_claim["status"]),
                                        CAUSE_REFINER))
            if claim.get("version") != required_claim["version"]:
                problems.append(Problem("that claim is at version %s, expected %s"
                                        % (claim.get("version"), required_claim["version"]),
                                        CAUSE_REFINER))
    required_new = expect.get("requiredNewClaim")
    if required_new:
        match = [claim for claim in claims_after
                 if claim.get("proposition") == required_new["proposition"]]
        if not match:
            problems.append(Problem("the new claim the case names is not in the workspace",
                                    CAUSE_REFINER))
        else:
            claim = match[0]
            if claim.get("status") != required_new["status"]:
                problems.append(Problem("the new claim is %s, expected %s"
                                        % (claim.get("status"), required_new["status"]),
                                        CAUSE_REFINER))
            if claim.get("version") != required_new["version"]:
                problems.append(Problem("the new claim is at version %s, expected %s"
                                        % (claim.get("version"), required_new["version"]),
                                        CAUSE_REFINER))
    return not problems, problems, observed


def _scope_of(scopes: dict, case: dict) -> str:
    """The scope whose case list names this case."""
    for name, scope in scopes.items():
        if case["id"] in scope.get("cases", []):
            return name
    return case["scope"]


def claims_of(base: str, workspace: str) -> list[dict]:
    path = "/v1/workbench/workspaces/%s/claims" % urllib.parse.quote(workspace)
    response = call(base, "GET", path)
    return response if isinstance(response, list) else []


# ── the run ────────────────────────────────────────────────────────────────

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--base", default=None,
                        help="use an instance that is already running at this base URL "
                             "(the routes carry the /v1 prefix themselves)")
    parser.add_argument("--work", default="/tmp/w51-epistemic-eval")
    parser.add_argument("--report", default=os.path.join(REPORT_DIR, REPORT_NAME))
    arguments = parser.parse_args()

    with open(DATASET, encoding="utf-8") as handle:
        dataset = json.load(handle)
    scopes = dataset["scopes"]
    cases = dataset["cases"]

    process = None
    base = arguments.base
    if base is None:
        base = "http://127.0.0.1:%d" % arguments.port
        print("w51-eval: starting the application on port %d (outbound HTTP closed)"
              % arguments.port, flush=True)
        process = start_application(arguments.port, arguments.work)

    # THE ORDER OF THE SCOPES IS THE ORDER OF THE DATASET.
    #
    # A scope is the STATE OF THE WORKSPACE a case is asked in, and the
    # dataset says which cases belong to which scope. The scope R1 asks
    # its answer before the scope R2 makes the second revision, which is
    # the order the W50 protocol used: the answer R-03 was asked at step
    # 11, between the two revisions, in a workspace of 687 claims.
    #
    # The runner therefore walks the scopes in the order the dataset
    # declares them and runs the cases each scope names. A case that no
    # scope names is a defect of the dataset, and the runner says so
    # rather than dropping it.
    by_id = {case["id"]: case for case in cases}
    ordered: list[dict] = []
    claimed: set[str] = set()
    for scope_name in scopes:
        for case_id in scopes[scope_name].get("cases", []):
            if case_id not in by_id:
                raise SystemExit("w51-eval: the scope %s names the case %s, which the dataset "
                                 "does not hold" % (scope_name, case_id))
            ordered.append(by_id[case_id])
            claimed.add(case_id)
    missing = [case["id"] for case in cases if case["id"] not in claimed]
    if missing:
        raise SystemExit("w51-eval: no scope names the case(s) %s" % ", ".join(missing))

    results: list[dict] = []
    try:
        preparations: list[dict] = []
        claims_before: dict[str, list[dict]] = {}
        for case in ordered:
            scope_name = case["scope"]
            scope = scopes.get(scope_name) or scopes.get(scope_name.split("->")[0])
            # The scope the case is ASKED in is the scope that names it.
            scope = scopes[_scope_of(scopes, case)]
            steps = prepare_scope(base, scope)
            preparations.append({"scope": scope_name, "askedIn": scope["workspace"],
                                 "description": scope.get("description"), "steps": steps})
            for step in steps:
                print("w51-eval: scope %s | %s | documents=%s claims=%s"
                      % (scope_name, step["call"], step["documentsAcquired"],
                         step["claimsAdded"]), flush=True)
            workspace = scope["workspace"]
            before = claims_of(base, workspace)
            response = run_case(base, case, scope)
            after = claims_of(base, workspace)
            claims_before[scope_name] = before

            if case["category"] == "false-premise":
                passed, problems, observed = judge_premise(case, response)
            elif case["category"] == "revision" and "file" in case:
                passed, problems, observed = judge_revision(case, response, before, after)
            else:
                passed, problems, observed = judge_answer(case, response)

            verdict = "PASS" if passed else "FAIL"
            print("%-6s %-28s %-4s | %s" % (case["id"], case["category"], verdict, observed),
                  flush=True)
            for problem in problems:
                print("       - %s" % problem["problem"], flush=True)
                print("         at %s" % problem["cause"], flush=True)
            results.append({
                "id": case["id"], "category": case["category"], "scope": scope_name,
                "verdict": verdict, "observed": observed, "problems": problems,
            })
    finally:
        stop_application(process)

    categories: dict[str, dict[str, int]] = {}
    for result in results:
        bucket = categories.setdefault(result["category"], {"passed": 0, "failed": 0})
        bucket["passed" if result["verdict"] == "PASS" else "failed"] += 1
    passed = sum(1 for result in results if result["verdict"] == "PASS")

    print("")
    print("w51-eval: the summary per category")
    for category in sorted(categories):
        bucket = categories[category]
        total = bucket["passed"] + bucket["failed"]
        print("  %-28s %2d/%2d pass" % (category, bucket["passed"], total))
    print("  %-28s %2d/%2d pass" % ("TOTAL", passed, len(results)))

    report = {
        "dataset": dataset["dataset"],
        "datasetVersion": dataset["version"],
        "source": dataset["source"],
        "runner": "scripts/w51-epistemic-eval.py",
        "offline": True,
        "boundary": "the JVM's HTTP and HTTPS clients are pointed at a closed port on the "
                    "loopback interface; no request leaves the machine and no model is called",
        "preparations": preparations,
        "results": results,
        "byCategory": categories,
        "passed": passed,
        "total": len(results),
    }
    os.makedirs(os.path.dirname(arguments.report), exist_ok=True)
    with open(arguments.report, "w", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print("w51-eval: the report is at %s" % arguments.report)
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
