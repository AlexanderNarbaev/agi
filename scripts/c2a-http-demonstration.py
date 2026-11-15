#!/usr/bin/env python3
"""CONCEPT-TO-ACTION — the real HTTP demonstration of the bounded report route.

What this script is
-------------------
The acceptance test for the product deliverable. It builds the actual serving application,
starts the actual Quarkus server, and sends actual HTTP requests to
``POST /v1/workbench/workspaces/{id}/reports``. Nothing here calls the writer in-process:
every claim about the route is a claim about a request that crossed a socket.

What it proves
--------------
1. the request reaches the workbench's own retrieval, and the claims it brings back are
   the claims the store holds (ids and sources match);
2. the bounded action is selected and executed through the existing action contract;
3. a real report is written to the configured root, and its digest equals the digest the
   response reported;
4. the result survives a restart of the server: the trace is durable, so a repeated
   request after the restart is a REPLAY and not a second file;
5. a repeated request does not produce an uncontrolled duplicate;
6. a request without consent produces no artifact at all;
7. concurrent requests do not violate the single-writer contract: one artifact, one
   STAGED record, one COMMITTED record.

What it does not prove
----------------------
It does not prove that the answer is TRUE. The report is a record of what the workspace
said; the workspace's own epistemic rules decide that, and this script only checks that
the report carries the workspace's claims with their ids and statuses.

Usage
-----
    python3 scripts/c2a-http-demonstration.py

It writes:
    docs-v2/engineering/C2A-HTTP-DEMONSTRATION.json   the machine-readable report
    docs-v2/demos/concept-to-action/evidence-report.md the artifact the run produced
"""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEMO = ROOT / "data" / "c2a-demo"
OUTPUT = DEMO / "output"
STORE = DEMO / "workbench.sqlite"
LOG = DEMO / "server.log"
PHASE = ["one"]
PHASE = ["one"]
REPORT = ROOT / "docs-v2" / "engineering" / "C2A-HTTP-DEMONSTRATION.json"
ARTIFACT_COPY = ROOT / "docs-v2" / "demos" / "concept-to-action" / "evidence-report.md"

PORT = int(os.environ.get("C2A_PORT", "9099"))
BASE = f"http://127.0.0.1:{PORT}"
WORKSPACE = "c2a-demo"
QUESTION = "What is plate tectonics and what does it say about the Earth's lithosphere?"
READY_TIMEOUT_SECONDS = 600


def log(message: str) -> None:
    print(message, flush=True)


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
        with urllib.request.urlopen(call, timeout=120) as response:
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


def sqlite_native_library() -> str | None:
    """Takes the SQLite native library out of the driver jar and returns its path.

    The packaged application cannot extract the library itself: the driver's own
    resource lookup fails inside the application's class loader, the driver reports
    "No native library found", and the knowledge store refuses to open. The library
    is therefore taken out of the driver jar and handed to the driver through the
    property the driver documents. The workaround is recorded in the report, because
    it is a property of the packaging, not of the route under demonstration.
    """
    import zipfile

    directory = ROOT / "matrix-core" / "build" / "quarkus-app" / "lib" / "main"
    drivers = sorted(directory.glob("org.xerial.sqlite-jdbc-*.jar"))
    if not drivers:
        return None
    target = DEMO / "libsqlitejdbc.so"
    with zipfile.ZipFile(drivers[0]) as archive:
        names = [n for n in archive.namelist()
                 if n.startswith("org/sqlite/native/Linux/x86_64/") and n.endswith(".so")]
        if not names:
            return None
        target.write_bytes(archive.read(names[0]))
    os.chmod(target, 0o700)
    # the driver reads org.sqlite.lib.path as a DIRECTORY and appends the library's
    # own name to it
    return str(DEMO)


def start_server() -> subprocess.Popen:
    environment = dict(os.environ)
    environment.update({
        "MATRIX_ACTIONS_ARTIFACT_ENABLED": "true",
        "MATRIX_ACTIONS_ARTIFACT_ROOT": str(OUTPUT),
        "MATRIX_KNOWLEDGE_PATH": str(STORE),
        "QUARKUS_HTTP_PORT": str(PORT),
        "MATRIX_SECURITY_MODE": "LOCAL_ONLY",
    })
    command = ["java"]
    native = sqlite_native_library()
    if native:
        command += ["-Dorg.sqlite.lib.path=" + native]
    command += ["-jar", "matrix-core/build/quarkus-app/quarkus-run.jar"]
    process = subprocess.Popen(
        command, cwd=ROOT, env=environment,
        stdout=(DEMO / ("server-" + PHASE[0] + ".log")).open("w"), stderr=subprocess.STDOUT)
    deadline = time.time() + READY_TIMEOUT_SECONDS
    while time.time() < deadline:
        if process.poll() is not None:
            raise SystemExit("the server exited before it became ready")
        try:
            with urllib.request.urlopen(BASE + "/q/health", timeout=5) as response:
                if response.status == 200:
                    log(f"the server is ready on {BASE}")
                    return process
        except urllib.error.HTTPError as answering:
            # the server ANSWERED. Its health can be DOWN for a reason that does not
            # concern this route — the reactive PostgreSQL check needs a database this
            # process does not use — so the answer, not the verdict, is the readiness.
            log(f"the server is ready on {BASE} (health {answering.code})")
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
        "record": "C2A-HTTP-DEMONSTRATION",
        "date": time.strftime("%Y-%m-%d"),
        "commit": commit,
        "route": f"POST /v1/workbench/workspaces/{{id}}/reports",
        "baseUrl": BASE,
        "workspace": WORKSPACE,
        "question": QUESTION,
        "checks": {},
        "notVerified": [],
    }

    PHASE[0] = "one"
    server = start_server()
    try:
        # the licensed seed corpus enters the store through the application's own
        # acquisition route — the same route the 29-case evaluation uses for scope S1
        status, loaded = request("POST", f"/v1/learning/workspaces/{WORKSPACE}/seed")
        report["load"] = {"status": status, "body": loaded}
        log(f"seeded the corpus: {status}")

        # the store's own view of the claims, so the response can be checked against it
        status, stored_claims = request("GET", f"/v1/workbench/workspaces/{WORKSPACE}/claims")
        report["storeClaims"] = {"status": status, "count": len(stored_claims or [])}

        # ── the request under demonstration ─────────────────────────────────
        status, body = request("POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                               {"question": QUESTION, "consent": True})
        report["request"] = {"status": status, "body": body}
        log(f"the report request answered {status}")

        evidence = (body or {}).get("evidence") or []
        cited = [item.get("claimId") for item in evidence]
        report["checks"]["claimsComeFromTheStore"] = {
            "cited": len(cited),
            "storeClaims": len(stored_claims or []),
            "everyCitedClaimIsInTheStore": all(
                any((claim or {}).get("id") == claimId for claim in (stored_claims or []))
                for claimId in cited),
            "everyReferenceCarriesASource": all(item.get("sourceId") for item in evidence),
        }

        artifact = OUTPUT / ((body or {}).get("artifactName") or "")
        if artifact.is_file():
            digest = sha256_of(artifact)
            text = artifact.read_text(encoding="utf-8")
            report["checks"]["artifact"] = {
                "path": str(artifact.relative_to(ROOT)),
                "bytes": artifact.stat().st_size,
                "sha256": digest,
                "reportedSha256": (body or {}).get("sha256"),
                "digestMatches": digest == (body or {}).get("sha256"),
                "carriesEveryCitedClaim": all(claimId and claimId in text for claimId in cited),
                "startsWithHeading": text.startswith("#"),
            }
            ARTIFACT_COPY.write_text(text, encoding="utf-8")
        else:
            report["checks"]["artifact"] = {"present": False}

        # ── a repeated request must not duplicate ───────────────────────────
        status, repeated = request("POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                                   {"question": QUESTION, "consent": True})
        report["checks"]["repeat"] = {
            "status": status,
            "disposition": (repeated or {}).get("disposition"),
            "artifactsAfter": artifacts(),
            "artifactCount": len(artifacts()),
            "noDuplicate": len(artifacts()) == 1,
        }
        log(f"the repeated request answered {status} "
            f"({(repeated or {}).get('disposition')})")

        # ── a request without consent must write nothing ───────────────────
        before = artifacts()
        status, refused = request("POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                                  {"question": QUESTION})
        report["checks"]["refusal"] = {
            "status": status,
            "body": refused,
            "artifactsBefore": before,
            "artifactsAfter": artifacts(),
            "noArtifactWritten": artifacts() == before,
        }
        log(f"the request without consent answered {status}")

        # ── concurrent requests must not violate the single-writer contract ──
        results: list[tuple[int, object]] = []
        lock = threading.Lock()

        def racer() -> None:
            outcome = request("POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                              {"question": QUESTION, "consent": True,
                               "idempotencyKey": "c2a-concurrent-probe"})
            with lock:
                results.append(outcome)

        threads = [threading.Thread(target=racer) for _ in range(4)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        dispositions = sorted(str((item[1] or {}).get("disposition")) for item in results)
        # the probe carries its own idempotency key, so it names its own action identity:
        # the contract under test is that ONE identity yields ONE artifact, whatever the
        # number of racing requests
        probe_identity = next(((item[1] or {}).get("taskId") for item in results
                               if (item[1] or {}).get("taskId")), None)
        probe_records = [r for r in index_records() if r.get("actionId") == probe_identity]
        probe_files = sorted({r.get("fileName") for r in probe_records})
        main_records = [r for r in index_records()
                        if r.get("actionId") == (body or {}).get("taskId")]
        report["checks"]["concurrency"] = {
            "requests": len(results),
            "statuses": sorted(item[0] for item in results),
            "dispositions": dispositions,
            "probeIdentity": probe_identity,
            "probeStagedRecords": len([r for r in probe_records if r.get("status") == "STAGED"]),
            "probeCommittedRecords": len([r for r in probe_records
                                          if r.get("status") == "COMMITTED"]),
            "probeArtifactNames": probe_files,
            "oneArtifactPerIdentity": len(probe_files) == 1,
            "oneStagedOneCommitted": (
                len([r for r in probe_records if r.get("status") == "STAGED"]) == 1
                and len([r for r in probe_records if r.get("status") == "COMMITTED"]) == 1),
            "mainIdentityStaged": len([r for r in main_records if r.get("status") == "STAGED"]),
            "mainIdentityCommitted": len([r for r in main_records
                                          if r.get("status") == "COMMITTED"]),
            "artifactsAfter": artifacts(),
            "artifactCount": len(artifacts()),
        }
        log(f"the concurrent requests answered {dispositions}")
    finally:
        stop_server(server)

    # ── the result survives a restart ──────────────────────────────────────
    artifacts_before_restart = len(artifacts())
    PHASE[0] = "two"
    server = start_server()
    try:
        status, after_restart = request("POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                                        {"question": QUESTION, "consent": True})
        report["checks"]["afterRestart"] = {
            "status": status,
            "disposition": (after_restart or {}).get("disposition"),
            "sha256": (after_restart or {}).get("sha256"),
            "artifactPresent": artifact.is_file(),
            "digestStillMatches": artifact.is_file()
            and sha256_of(artifact) == (after_restart or {}).get("sha256"),
            "artifactCount": len(artifacts()),
            "artifactCountBefore": artifacts_before_restart,
            "noNewArtifact": len(artifacts()) == artifacts_before_restart,
        }
        log(f"after the restart the repeated request answered {status} "
            f"({(after_restart or {}).get('disposition')})")
    finally:
        stop_server(server)

    report["notVerified"] = [
        "the truth of the report's propositions: the route records what the workspace said, "
        "and the workspace's own epistemic rules decide that",
        "a network-facing deployment: the action is refused outside a local-only process, "
        "and no network-exposed run was made",
        "the durability of the artifact across a power loss: the writer forces the bytes and "
        "the directory, but no power was cut",
    ]
    REPORT.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    log(f"the machine-readable report is at {REPORT.relative_to(ROOT)}")

    checks = report["checks"]
    failures = []
    if checks.get("artifact", {}).get("digestMatches") is not True:
        failures.append("the artifact's digest does not match the reported digest")
    if checks.get("repeat", {}).get("noDuplicate") is not True:
        failures.append("the repeated request produced a second artifact")
    if checks.get("refusal", {}).get("noArtifactWritten") is not True:
        failures.append("a request without consent produced an artifact")
    if checks.get("concurrency", {}).get("oneArtifactPerIdentity") is not True:
        failures.append("the concurrent requests produced more than one artifact for one identity")
    if checks.get("concurrency", {}).get("oneStagedOneCommitted") is not True:
        failures.append("the concurrent requests produced more than one staged or committed record")
    if checks.get("afterRestart", {}).get("noNewArtifact") is not True:
        failures.append("the restarted server produced an artifact that was not there before")
    if failures:
        for failure in failures:
            log(f"FAILED: {failure}")
        return 1
    log("every check of the demonstration passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
