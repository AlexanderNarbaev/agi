#!/usr/bin/env python3
"""EPISTEMIC P0.5 — the packaged application must open its own store.

What this script is
-------------------
The guard for the packaging defect this repository recorded: "the packaged application
cannot open the SQLite store on this host without ``-Dorg.sqlite.lib.path=<a directory
that holds libsqlitejdbc.so>``". The driver extracts its native library out of its own
jar into ``java.io.tmpdir`` and loads it there; when that extraction cannot happen, the
driver reports "No native library found for os.name=..., os.arch=..." and the store
refuses to open. The recorded workaround was an ad-hoc property on the command line.

This script proves the opposite: it starts the PACKAGED application, from a working
directory OUTSIDE the repository, with no such property anywhere — not on the command
line, not in the environment — and it then exercises the product route that touches the
store.

What it proves
--------------
1. the packaged application starts and opens its store with no manual native-library
   setup; the driver's own extraction is enough;
2. the report route works through that store: the report is written, and the digest the
   response reports is the digest of the file on disk;
3. a repeated request is a replay and not a second file;
4. a request without consent writes nothing;
5. concurrent requests with one identity produce one artifact;
6. the result survives a restart of the server: the same request after the restart is
   still a replay.

What it does not prove
----------------------
It does not prove that the answer is true. The report is a record of what the workspace
said. It does not exercise the native image (that build is a separate, slower lane and it
is red on this host for an unrelated reason), and it does not test any platform other
than the one it runs on.

Usage
-----
    python3 scripts/packaged-sqlite-smoke.py                 # builds, then smokes
    python3 scripts/packaged-sqlite-smoke.py --skip-build    # smokes the current build

It writes:
    <the work directory>/packaged-sqlite-smoke.json   the machine-readable report
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAR = ROOT / "matrix-core" / "build" / "quarkus-app" / "quarkus-run.jar"

# The property the recorded workaround used. The guard asserts that it is absent.
FORBIDDEN_PROPERTY = "org.sqlite.lib.path"

WORKSPACE = "packaged-smoke"
QUESTION = "What is plate tectonics and what does it say about the Earth's lithosphere?"
READY_TIMEOUT_SECONDS = 600


def log(message: str) -> None:
    print(message, flush=True)


def free_port() -> int:
    """A port nobody is listening on."""
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def sha256_of(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def request(base: str, method: str, path: str, body: dict | None = None) -> tuple[int, object]:
    """One real HTTP request. Returns the status and the parsed body."""
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    call = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(call, timeout=300) as response:
            payload = response.read().decode("utf-8")
            status = response.status
    except urllib.error.HTTPError as failure:
        payload = failure.read().decode("utf-8")
        status = failure.code
    try:
        return status, json.loads(payload) if payload else None
    except json.JSONDecodeError:
        return status, payload


def build() -> None:
    """Builds the packaged application the way an operator would."""
    log("building the packaged application (the artifact the request will reach)")
    command = ["flock", "/tmp/gradle-matrix.lock", "./gradlew", ":matrix-core:quarkusBuild",
               ":matrix-core:verifyPackagedSqliteNativeLibrary", "--offline", "--console=plain"]
    finished = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    if finished.returncode != 0:
        log(finished.stdout[-4000:])
        log(finished.stderr[-4000:])
        raise SystemExit("the build failed")


def start_server(work: Path, port: int, log_path: Path) -> subprocess.Popen:
    """Starts the packaged application from the work directory, with no manual setup."""
    java = Path(os.environ.get("JAVA_HOME", "")) / "bin" / "java"
    command = [str(java) if java.exists() else "java"]
    if FORBIDDEN_PROPERTY in os.environ:
        raise SystemExit("the environment carries " + FORBIDDEN_PROPERTY
                         + "; the guard must run WITHOUT the recorded workaround")
    environment = dict(os.environ)
    environment.pop(FORBIDDEN_PROPERTY, None)
    environment.update({
        "MATRIX_ACTIONS_ARTIFACT_ENABLED": "true",
        "MATRIX_ACTIONS_ARTIFACT_ROOT": str(work / "output"),
        "MATRIX_KNOWLEDGE_PATH": str(work / "knowledge.sqlite"),
        "MATRIX_MIND_DIR": str(work / "mind"),
        "QUARKUS_HTTP_PORT": str(port),
        "MATRIX_SECURITY_MODE": "LOCAL_ONLY",
    })
    (work / "output").mkdir(parents=True, exist_ok=True)
    os.chmod(work / "output", 0o700)
    handle = log_path.open("w", encoding="utf-8")
    process = subprocess.Popen(command + ["-jar", str(JAR)], cwd=work, env=environment,
                               stdout=handle, stderr=subprocess.STDOUT)
    deadline = time.time() + READY_TIMEOUT_SECONDS
    while time.time() < deadline:
        if process.poll() is not None:
            raise SystemExit("the application exited before it became ready; see " + str(log_path))
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/q/health", timeout=5) as response:
                log(f"the application is ready on port {port} (health {response.status})")
                return process
        except urllib.error.HTTPError as answering:
            # The application ANSWERED. Its health can be DOWN for a reason that does not
            # concern the store — the reactive PostgreSQL check needs a database this
            # process does not use — so the answer, not the verdict, is the readiness.
            log(f"the application is ready on port {port} (health {answering.code})")
            return process
        except Exception:
            time.sleep(3)
    raise SystemExit("the application did not become ready in time; see " + str(log_path))


def stop_server(process: subprocess.Popen) -> None:
    process.terminate()
    try:
        process.wait(timeout=60)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=30)


def artifacts(root: Path) -> list[str]:
    return sorted(path.name for path in root.iterdir()
                  if path.is_file() and not path.name.startswith("."))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-build", action="store_true",
                        help="smoke the current packaged build instead of rebuilding it")
    arguments = parser.parse_args()

    if not arguments.skip_build:
        build()
    if not JAR.exists():
        raise SystemExit("the packaged application is missing: " + str(JAR))

    work = Path(tempfile.mkdtemp(prefix="matrix-packaged-smoke-"))
    report: dict = {
        "record": "PACKAGED-SQLITE-SMOKE",
        "date": time.strftime("%Y-%m-%d"),
        "workDirectory": str(work),
        "jar": str(JAR),
        "forbiddenProperty": FORBIDDEN_PROPERTY,
        "propertyUsed": False,
        "checks": {},
    }
    base = ""
    port = free_port()
    try:
        server = start_server(work, port, work / "server-one.log")
        base = f"http://127.0.0.1:{port}"
        try:
            status, loaded = request(base, "POST", f"/v1/learning/workspaces/{WORKSPACE}/seed")
            report["checks"]["seed"] = {"status": status}
            log(f"the corpus was seeded: {status}")

            status, body = request(base, "POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                                   {"question": QUESTION, "consent": True})
            report["checks"]["report"] = {"status": status}
            log(f"the report request answered {status}")

            artifact = work / "output" / ((body or {}).get("artifactName") or "")
            if artifact.is_file():
                digest = sha256_of(artifact)
                report["checks"]["artifact"] = {
                    "bytes": artifact.stat().st_size,
                    "sha256": digest,
                    "reportedSha256": (body or {}).get("sha256"),
                    "digestMatches": digest == (body or {}).get("sha256"),
                }
            else:
                report["checks"]["artifact"] = {"present": False}

            # The driver deletes the library it extracted when the JVM exits, so the
            # evidence is read while the application is still running.
            report["checks"]["extractedLibrary"] = sorted(
                str(path.relative_to(work)) for path in work.rglob("*libsqlitejdbc*"))

            status, repeated = request(base, "POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                                       {"question": QUESTION, "consent": True})
            report["checks"]["repeat"] = {
                "status": status,
                "disposition": (repeated or {}).get("disposition"),
                "artifactCount": len(artifacts(work / "output")),
            }

            before = artifacts(work / "output")
            status, refused = request(base, "POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                                      {"question": QUESTION})
            report["checks"]["refusal"] = {
                "status": status,
                "noArtifactWritten": artifacts(work / "output") == before,
            }

            results: list[tuple[int, object]] = []
            lock = threading.Lock()

            # The probe carries its own idempotency key, so it names its own action
            # identity: the contract under test is that ONE identity yields ONE artifact,
            # whatever the number of racing requests.
            before_probe = len(artifacts(work / "output"))

            def racer() -> None:
                outcome = request(base, "POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                                  {"question": QUESTION, "consent": True,
                                   "idempotencyKey": "packaged-smoke-concurrent-probe"})
                with lock:
                    results.append(outcome)

            threads = [threading.Thread(target=racer) for _ in range(4)]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()
            after_probe = len(artifacts(work / "output"))
            report["checks"]["concurrency"] = {
                "requests": len(results),
                "statuses": sorted(status for status, _ in results),
                "artifactsBefore": before_probe,
                "artifactsAfter": after_probe,
                "artifactsAdded": after_probe - before_probe,
            }
        finally:
            stop_server(server)

        # The restart: the same request, a new process, the same store.
        server = start_server(work, port, work / "server-two.log")
        try:
            status, replayed = request(base, "POST", f"/v1/workbench/workspaces/{WORKSPACE}/reports",
                                       {"question": QUESTION, "consent": True})
            report["checks"]["restart"] = {
                "status": status,
                "disposition": (replayed or {}).get("disposition"),
                "artifactCount": len(artifacts(work / "output")),
            }
            log(f"the request after the restart answered {status} "
                f"({(replayed or {}).get('disposition')})")
        finally:
            stop_server(server)

        report["checks"]["storeOpened"] = (work / "knowledge.sqlite").exists()
    finally:
        (work / "packaged-sqlite-smoke.json").write_text(
            json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        log("the report is at " + str(work / "packaged-sqlite-smoke.json"))

    failures = []
    checks = report["checks"]
    if not checks.get("storeOpened"):
        failures.append("the packaged application did not open its store")
    if not checks.get("extractedLibrary"):
        failures.append("the driver did not extract its native library")
    artifact = checks.get("artifact", {})
    if not artifact.get("digestMatches"):
        failures.append("the report's digest is not the digest of the file on disk")
    if checks.get("repeat", {}).get("artifactCount") != 1:
        failures.append("the repeated request produced a second file")
    if not checks.get("refusal", {}).get("noArtifactWritten"):
        failures.append("the request without consent wrote a file")
    if checks.get("concurrency", {}).get("artifactsAdded") != 1:
        failures.append("the concurrent requests did not produce exactly one artifact")
    if checks.get("restart", {}).get("artifactCount") != checks.get("concurrency", {}).get("artifactsAfter"):
        failures.append("the restart produced a second file")

    for failure in failures:
        log("FAILED: " + failure)
    if failures:
        return 1
    log("the packaged application opened its store and served the report with no "
        + FORBIDDEN_PROPERTY + " property anywhere")
    return 0


if __name__ == "__main__":
    sys.exit(main())
