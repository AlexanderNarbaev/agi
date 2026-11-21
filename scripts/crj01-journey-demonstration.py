#!/usr/bin/env python3
"""CRJ-01 — the Cognitive Reference Journey over the REAL packaged HTTP route.

What this script is
-------------------
The local half of the CRJ-01 acceptance contract, executed against the artifact the
owner would actually run: the packaged Quarkus application, started as a process, driven
over a socket. Nothing here calls the writer, the workbench or the kernel in-process —
every statement about the journey is a statement about a request that crossed a socket,
and every statement about the effect is read back from the file system by a reader that
did not write it.

What it proves (and what it does not)
-------------------------------------
It proves, with a denominator for each claim:

1. PERCEIVE/RESTORE — a source-bearing corpus enters the workspace through the
   application's own acquisition route, and the store holds claims that carry sources;
2. INFER — the route answers the question with CITED claims, every citation naming a
   claim the store holds and a source;
3. PLAN — the trace names the method the kernel chose and why;
4. AUTHORIZE — a request without consent writes nothing at all;
5. ACT — one bounded artifact is written, and the digest the response reported is the
   digest of the bytes on disk;
6. OBSERVE — an independent reader finds the artifact, its digest and exactly one
   STAGED and one COMMITTED record for the identity;
7. IDEMPOTENCY (CRJ-01.1) — under a REUSED key, the exact replay stays a correct replay,
   and a CHANGED question is refused with an explicit conflict instead of being answered
   with the earlier journey's report;
8. RESTART — the server is stopped and started again, and the repeated request is still
   a replay of the same artifact: the trace and the artifact are durable.

It does NOT prove transfer. The reuse it demonstrates is the EXACT replay — arm B of the
CRJ-01 acceptance contract — and the script says so in its own output. The
developmental milestone (arm C on different, unseen tasks) is a separate, later
measurement, and this script does not claim it.

Usage
-----
    python3 scripts/crj01-journey-demonstration.py

It writes:
    docs-v2/engineering/CRJ-01-JOURNEY-DEMONSTRATION.json   the machine-readable report
    docs-v2/demos/crj-01/evidence-report.md                 the artifact the run produced
"""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEMO = ROOT / "data" / "crj01-demo"
OUTPUT = DEMO / "output"
STORE = DEMO / "workbench.sqlite"
REPORT = ROOT / "docs-v2" / "engineering" / "CRJ-01-JOURNEY-DEMONSTRATION.json"
ARTIFACT_COPY = ROOT / "docs-v2" / "demos" / "crj-01" / "evidence-report.md"

PORT = int(os.environ.get("CRJ01_PORT", "9098"))
BASE = f"http://127.0.0.1:{PORT}"
WORKSPACE = "crj01-demo"
QUESTION = "What is plate tectonics and what does it say about the Earth's lithosphere?"
OTHER_QUESTION = "How does the lithosphere move beneath the plates?"
KEY = "crj01-reused-key"
READY_TIMEOUT_SECONDS = 600

# the arm C leg — the workspace with four sources, the questions it is observed on, and the
# questions the candidate must survive
ARM_C_WORKSPACE = "photosynthesis"
ARM_C_OBSERVED = [
    "What is photosynthesis?",
    "How do plants convert light into chemical energy?",
    "What does oxygenic photosynthesis release?",
]
ARM_C_HELD_OUT = [
    "What is the role of water in photosynthesis?",
    "Which organisms perform oxygenic photosynthesis?",
    "What is the Calvin-Benson cycle?",
    "How does RuBisCO compete with oxygenation?",
    "What limits photosynthetic output in angiosperms?",
]
ARM_C_LATER_QUESTION = "What are the light-dependent reactions?" 

CHECKS: list[dict] = []


def log(message: str) -> None:
    print(message, flush=True)


def check(name: str, passed: bool, detail: object) -> bool:
    """One check, recorded with its own denominator."""
    CHECKS.append({"check": name, "passed": bool(passed), "detail": detail})
    log(("  PASS  " if passed else "  FAIL  ") + name + " — " + json.dumps(detail)[:220])
    return bool(passed)


def sha256_of(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def request(method: str, path: str, body: dict | None = None) -> tuple[int, object]:
    """One real HTTP request. Returns the status and the parsed body."""
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    call = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(call, timeout=180) as response:
            payload = response.read().decode("utf-8")
            status = response.status
    except urllib.error.HTTPError as failure:
        payload = failure.read().decode("utf-8")
        status = failure.code
    try:
        return status, json.loads(payload) if payload else None
    except json.JSONDecodeError:
        return status, payload


def build() -> str:
    log("building the serving application (the real artifact the request will reach)")
    command = ["flock", "/tmp/gradle-matrix.lock", "./gradlew", ":matrix-core:quarkusBuild",
               "--offline", "--console=plain"]
    finished = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    if finished.returncode != 0:
        log(finished.stdout[-4000:])
        log(finished.stderr[-4000:])
        raise SystemExit("the build failed")
    return subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True,
                          text=True).stdout.strip()


def start_server(phase: str) -> subprocess.Popen:
    environment = dict(os.environ)
    environment.update({
        "MATRIX_ACTIONS_ARTIFACT_ENABLED": "true",
        "MATRIX_ACTIONS_ARTIFACT_ROOT": str(OUTPUT),
        "MATRIX_KNOWLEDGE_PATH": str(STORE),
        "QUARKUS_HTTP_PORT": str(PORT),
        "MATRIX_SECURITY_MODE": "LOCAL_ONLY",
    })
    if "org.sqlite.lib.path" in environment:
        raise SystemExit("the environment carries org.sqlite.lib.path; the demonstration "
                         "must run without the recorded workaround")
    command = ["java", "-jar", "matrix-core/build/quarkus-app/quarkus-run.jar"]
    process = subprocess.Popen(
        command, cwd=ROOT, env=environment,
        stdout=(DEMO / ("server-" + phase + ".log")).open("w"), stderr=subprocess.STDOUT)
    deadline = time.time() + READY_TIMEOUT_SECONDS
    while time.time() < deadline:
        if process.poll() is not None:
            raise SystemExit("the server exited before it became ready")
        try:
            with urllib.request.urlopen(BASE + "/q/health", timeout=5) as response:
                if response.status == 200:
                    log(f"the server is ready on {BASE} ({phase})")
                    return process
        except urllib.error.HTTPError as answering:
            log(f"the server is ready on {BASE} ({phase}, health {answering.code})")
            return process
        except Exception:
            time.sleep(3)
    process.kill()
    raise SystemExit("the server did not become ready in time")


def stop_server(process: subprocess.Popen) -> None:
    process.terminate()
    try:
        process.wait(timeout=60)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=30)


def artifacts() -> list[str]:
    if not OUTPUT.is_dir():
        return []
    return sorted(p.name for p in OUTPUT.iterdir()
                  if p.is_file() and not p.name.startswith("."))


def index_records() -> list[dict]:
    index = OUTPUT / ".artifact-index.ndjson"
    if not index.exists():
        return []
    records = []
    for line in index.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            records.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return records


def main() -> int:
    if DEMO.exists():
        shutil.rmtree(DEMO)
    OUTPUT.mkdir(parents=True)
    # the authorized root must not be writable by other users; this host's umask creates
    # directories as 0775, so the mode is set explicitly
    os.chmod(OUTPUT, 0o700)
    ARTIFACT_COPY.parent.mkdir(parents=True, exist_ok=True)

    commit = build()
    report: dict = {
        "record": "CRJ-01-JOURNEY-DEMONSTRATION",
        "date": time.strftime("%Y-%m-%d"),
        "commit": commit,
        "route": "POST /v1/workbench/workspaces/{id}/reports",
        "baseUrl": BASE,
        "workspace": WORKSPACE,
        "question": QUESTION,
        "otherQuestion": OTHER_QUESTION,
        "idempotencyKey": KEY,
        "milestone": "the CRJ-01 LOCAL milestone (ending after RESTART); the developmental "
                     "milestone (arm C on different, unseen tasks) is NOT demonstrated here",
        "checks": {},
        "notVerified": [],
    }

    arm_c: dict = {"milestone": "the governed learned competence (arm C)"}
    episodes: list[str] = []
    server = start_server("one")
    try:
        # ── PERCEIVE / RESTORE ───────────────────────────────────────────────
        status, loaded = request("POST", f"/v1/learning/workspaces/{WORKSPACE}/seed")
        report["seed"] = {"status": status, "body": loaded}
        check("the source-bearing corpus enters through the acquisition route",
              status == 200, {"status": status})

        status, stored_claims = request("GET", f"/v1/workbench/workspaces/{WORKSPACE}/claims")
        status_s, stored_sources = request("GET", f"/v1/workbench/workspaces/{WORKSPACE}/sources")
        claims = stored_claims or []
        sources = stored_sources or []
        report["store"] = {"claims": len(claims), "sources": len(sources)}
        check("the store holds claims that carry sources",
              len(claims) > 0 and len(sources) > 0,
              {"claims": len(claims), "sources": len(sources)})

        # ── AUTHORIZE — no consent, no artifact ──────────────────────────────
        before = artifacts()
        status, body = request("POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                               {"question": QUESTION, "consent": False, "idempotencyKey": "no-consent"})
        check("a request without consent is refused and writes nothing",
              status == 403 and artifacts() == before,
              {"status": status, "artifactsBefore": len(before),
               "artifactsAfter": len(artifacts()), "error": (body or {}).get("error")})

        # ── INFER / PLAN / ACT ───────────────────────────────────────────────
        status, body = request("POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                               {"question": QUESTION, "consent": True, "idempotencyKey": KEY})
        report["request"] = {"status": status, "body": body}
        check("the question is answered by a bounded action", status == 201,
              {"status": status, "disposition": (body or {}).get("disposition")})

        evidence = (body or {}).get("evidence") or []
        cited = [item.get("claimId") for item in evidence]
        check("every cited claim is a claim the store holds",
              bool(cited) and all(any((c or {}).get("id") == cid for c in claims)
                                  for cid in cited),
              {"cited": len(cited), "storeClaims": len(claims),
               "everyCitedClaimIsInTheStore": all(
                   any((c or {}).get("id") == cid for c in claims) for cid in cited)})
        check("every citation names a source", bool(evidence)
              and all(item.get("sourceId") for item in evidence),
              {"references": len(evidence),
               "everyReferenceCarriesASource": all(item.get("sourceId")
                                                  for item in evidence)})

        identity = (body or {}).get("taskId")
        reported_digest = (body or {}).get("sha256")
        artifact = OUTPUT / ((body or {}).get("artifactName") or "")
        observed_digest = sha256_of(artifact) if artifact.is_file() else None
        check("the artifact on disk carries the digest the response reported",
              observed_digest is not None and observed_digest == reported_digest,
              {"artifact": artifact.name, "reported": reported_digest,
               "observed": observed_digest})

        records = [r for r in index_records() if r.get("actionId") == identity]
        statuses = [r.get("status") for r in records]
        check("the index holds exactly one STAGED and one COMMITTED record for the identity",
              statuses.count("STAGED") == 1 and statuses.count("COMMITTED") == 1,
              {"records": len(records), "statuses": statuses})

        # ── PLAN — the trace names the method and the reason ────────────────
        # the response's `traceReference` names the ARTIFACT INDEX, not the episode: the
        # journey's trace lives in the kernel's store under the episode the identity names
        # ("report-" + the action identity), and the trace route reads that episode
        episode_id = "report-" + str(identity)
        status, steps = request("GET", f"/v1/cognitive/trace/{episode_id}")
        phases = [s.get("phase") for s in (steps or [])]
        plan_step = next((s for s in (steps or []) if s.get("phase") == "PLAN"), None)
        check("the trace records the plan, the permission, the action and the observation",
              status == 200 and {"PLAN", "PERMISSION", "ACTION", "OBSERVE"}.issubset(set(phases)),
              {"status": status, "episode": episode_id, "phases": phases})
        check("the plan names the method that ran and why",
              plan_step is not None and bool(plan_step.get("detail")),
              {"plan": (plan_step or {}).get("detail")})

        # ── IDEMPOTENCY (CRJ-01.1) ───────────────────────────────────────────
        status, replay = request("POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                                 {"question": QUESTION, "consent": True, "idempotencyKey": KEY})
        check("the exact replay stays a correct replay",
              status == 200 and (replay or {}).get("disposition") == "REPLAYED"
              and (replay or {}).get("sha256") == reported_digest,
              {"status": status, "disposition": (replay or {}).get("disposition"),
               "sameDigest": (replay or {}).get("sha256") == reported_digest})

        artifacts_before_conflict = artifacts()
        status, conflict = request("POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                                   {"question": OTHER_QUESTION, "consent": True,
                                    "idempotencyKey": KEY})
        check("a reused key with a CHANGED question is refused, not answered with the "
              "earlier report",
              status == 409 and (conflict or {}).get("error") == "REFUSED_IDENTITY",
              {"status": status, "error": (conflict or {}).get("error"),
               "detail": (conflict or {}).get("detail")})
        check("the refused conflict wrote no second artifact",
              artifacts() == artifacts_before_conflict,
              {"artifacts": artifacts()})
        check("the earlier artifact is untouched by the refused conflict",
              artifact.is_file() and sha256_of(artifact) == reported_digest,
              {"observed": sha256_of(artifact) if artifact.is_file() else None,
               "reported": reported_digest})

        # ── ARM C, first beat — the serving process OBSERVES three journeys ──
        status, loaded = request("POST", f"/v1/workbench/workspaces/{ARM_C_WORKSPACE}/load")
        arm_c["load"] = {"status": status}
        for question in ARM_C_OBSERVED:
            status, observed = request("POST",
                                       f"/v1/workbench/workspaces/{ARM_C_WORKSPACE}/reports",
                                       {"question": question, "consent": True,
                                        "idempotencyKey": "arm-c-observed-" + str(len(episodes))})
            if status != 201:
                arm_c["observations"] = {"status": status,
                                         "error": (observed or {}).get("error")}
                break
            episodes.append("report-" + (observed or {}).get("taskId", ""))
        arm_c["observedEpisodes"] = len(episodes)
    finally:
        stop_server(server)

    # ── ARM C — the governed learned competence ─────────────────────────────
    #
    # The leg runs in three beats, and the order is the point:
    #   1. the serving process OBSERVES three journeys and writes their traces;
    #   2. the serving process STOPS, and the operator's command learns the procedure from
    #      those traces — the store has one writer, and the command is that writer;
    #   3. the serving process starts again and is asked a DIFFERENT question, which the
    #      promoted procedure answers.
    if len(episodes) == len(ARM_C_OBSERVED):
        cases_file = DEMO / "arm-c-cases.tsv"
        cases_file.write_text("\n".join(f"case-{index}\t{question}" for index, question
                                          in enumerate(ARM_C_HELD_OUT)), encoding="utf-8")
        quarantine_file = DEMO / "arm-c-quarantine.txt"
        quarantine_file.write_text("", encoding="utf-8")
        # the command runs ALONE, with the application's own jars on the classpath: booting
        # the whole application would start the background trainers and write their state,
        # which is not what an operator who asks for a promotion asked for
        command = ["java", "-cp",
                   "matrix-core/build/quarkus-app/app/*"
                   ":matrix-core/build/quarkus-app/lib/main/*"
                   ":matrix-core/build/quarkus-app/lib/boot/*",
                   "io.matrix.cli.LearnReportProcedureCommand",
                   "--workspace", ARM_C_WORKSPACE,
                   "--episodes", ",".join(episodes),
                   "--cases", str(cases_file),
                   "--quarantine", str(quarantine_file),
                   "--knowledge-path", str(STORE)]
        learned = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
        arm_c["command"] = " ".join(command[2:])
        arm_c["exitCode"] = learned.returncode
        arm_c["output"] = learned.stdout.strip()
        if learned.stderr.strip():
            arm_c["stderr"] = learned.stderr.strip()[-2000:]
        check("the operator's command promotes the procedure from the observed journeys",
              learned.returncode == 0 and "promoted: true" in learned.stdout,
              {"exitCode": learned.returncode,
               "output": learned.stdout.strip()[:400]})

    # ── RESTART — the trace and the artifact are durable ─────────────────────
    server = start_server("two")
    try:
        status, after_restart = request("POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                                         {"question": QUESTION, "consent": True,
                                          "idempotencyKey": KEY})
        check("after a restart the repeated request is still a replay of the same artifact",
              status == 200 and (after_restart or {}).get("disposition") == "REPLAYED"
              and (after_restart or {}).get("sha256") == reported_digest,
              {"status": status, "disposition": (after_restart or {}).get("disposition"),
               "sameDigest": (after_restart or {}).get("sha256") == reported_digest})

        status, conflict = request("POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                                   {"question": OTHER_QUESTION, "consent": True,
                                    "idempotencyKey": KEY})
        check("after a restart the changed-question conflict is still refused",
              status == 409 and (conflict or {}).get("error") == "REFUSED_IDENTITY",
              {"status": status, "error": (conflict or {}).get("error")})

        # ── ARM C, after the restart — a DIFFERENT question, answered by the
        #    procedure that was learned before the restart ────────────────────
        status, later = request("POST", f"/v1/workbench/workspaces/{ARM_C_WORKSPACE}/reports",
                                {"question": ARM_C_LATER_QUESTION, "consent": True,
                                 "idempotencyKey": "arm-c-later-question"})
        arm_c["laterQuestion"] = {"status": status,
                                  "disposition": (later or {}).get("disposition")}
        check("the later question is answered by the served route",
              status == 201, {"status": status})
        if status == 201:
            episode = "report-" + str((later or {}).get("taskId", ""))
            status, steps = request("GET", f"/v1/cognitive/trace/{episode}")
            plan = next((step.get("detail") for step in (steps or [])
                         if step.get("phase") == "PLAN"), "")
            arm_c["plan"] = plan
            check("the kernel chose the PROMOTED PROCEDURE for the later question",
                  "procedure." in str(plan),
                  {"episode": episode, "plan": str(plan)[:300]})
            cited = [item.get("sourceId") for item in ((later or {}).get("evidence") or [])]
            arm_c["citedSources"] = sorted(set(s for s in cited if s))
            check("the answer cites evidence", len(arm_c["citedSources"]) > 0,
                  {"citedSources": arm_c["citedSources"][:8]})
    finally:
        stop_server(server)

    if artifact.is_file():
        ARTIFACT_COPY.write_text(artifact.read_text(encoding="utf-8"), encoding="utf-8")

    report["checks"] = {"total": len(CHECKS),
                        "passed": sum(1 for c in CHECKS if c["passed"]),
                        "failed": sum(1 for c in CHECKS if not c["passed"]),
                        "detail": CHECKS}
    report["artifacts"] = artifacts()
    report["armC"] = arm_c
    report["notVerified"] = [
        "that the answer is TRUE — the report is a record of what the workspace said",
        "transfer to a DIFFERENT task: the reuse demonstrated here is the exact replay "
        "(arm B), not the governed learned competence (arm C)",
        "the packaged application's own health beyond the routes this script calls",
        "the per-arm compute cost, which the CRJ-01 protocol marks NOT_INSTRUMENTED",
    ]
    REPORT.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    passed = report["checks"]["passed"]
    failed = report["checks"]["failed"]
    log(f"{passed}/{passed + failed} checks passed; the report is at {REPORT}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
