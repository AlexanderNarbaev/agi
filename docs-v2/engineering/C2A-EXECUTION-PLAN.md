# CONCEPT-TO-ACTION — execution plan and evidence ledger

**Record:** `C2A-EXECUTION-PLAN`
**Date:** 2026-11-15
**Branch:** `feature/concept-to-action` (from `develop` @ `429eba27`)
**Authorization:** scoped Level 3 (commits, pushes, PRs, release integration) — owner directive
"CONCEPT-TO-ACTION", §3 and §10.

---

## 1. Verified starting state (Stage 0)

| Fact | Value | How it was verified |
|---|---|---|
| `main` | `724ea537a24a96be5fdae24bdf6da716abcbe44f` | `git rev-parse origin/main` |
| `develop` | `429eba2795a702bc12012db989c6477f0adef36c` | `git rev-parse origin/develop` |
| tree of both | `6051348d65e2f8b34ea3ecd161994550f6e5f057` | `git rev-parse origin/main^{tree} origin/develop^{tree}` |
| `main` is an ancestor of `develop` | yes | `git merge-base --is-ancestor` (holds at `429eba27`) |
| working tree | clean except the harness file `.dsh-project-memory/admission-shadow.jsonl` (modified at runtime) and the owner's untracked `OpeAi/` | `git status --porcelain` |
| default branch | `main` | repository default branch |
| serving application | `matrix-core` (Quarkus 3.38.3, `io.quarkus:quarkus-rest`) — Chain A | `matrix-core/build.gradle`, `io.matrix.MatrixApplication` (`@QuarkusMain`) |
| workbench HTTP surface | `@Path("/v1/workbench")`, `io.matrix.knowledge.LearningWorkbenchResource` | source |
| JDK / Gradle | JDK 25 (`$HOME/jdk25`, 25.0.5), Gradle 9.6.0 | `java -version`, `gradle/wrapper/gradle-wrapper.properties` |
| hardware probe | 32 logical CPUs, 59 GiB RAM total / ~50 GiB available, 181 GiB free on `/` | `nproc`, `free -g`, `df -h` |

**Not re-verified by this wave (unchanged from the EVID-BASE release):** the 18/29 retrieval
baseline and the E1 window figures are read from the retained artefacts, not re-measured here —
see §5 for what was re-measured.

---

## 2. Stage plan and dependency order

| Stage | Deliverable | Depends on |
|---|---|---|
| 0 | Reconnaissance + this plan | — |
| 1 | `ArtifactWriter` hardening: in-process + cross-process exclusion, atomic identity check, bounded index, forged-record fix, durability, root-replacement detection, negative control | 0 |
| 2 | One protected product API (`POST /v1/workbench/workspaces/{id}/reports`), opt-in + LOCAL_ONLY + consent, typed response, **real HTTP demonstration** | 1 |
| 3 | Concept coverage baseline + grounded definition/relation candidates (research-only) | 0 |
| 4 | Independent held-out set + candidate comparison + promotion decision | 3 |
| 5 | Real learning evidence + representation comparison; A/B/C stays OPEN | 0 |
| 6 | Independent reviews, validators, negative controls, end-to-end journey | 1–5 |
| 7 | Branch → PR → reviews → merge `develop` → release PR → `main` → sync back → verify trees | 6 |

**Rules that hold across stages**

- No new general agent framework, no second action engine, no graph database.
- The production retrieval path is unchanged until Stage 4 proves a candidate on independent data.
- Every number in a document carries the command or file that produced it.
- A negative result is published, not hidden.
- `K_MAX = 20`, the production fold, the trainer width, Exp324 (2 ms), ADR §4.4 (A/B/C) are
  untouched.

---

## 3. Stage 1 — what changes in the writer, and why

The six residual risks named in `docs-v2/engineering/B3-EVIDENCE-TO-ACTION.md` §7 map to six
changes:

| Risk (B3 §7) | Change |
|---|---|
| no lock; identity check and index append are not atomic | a per-root lease: a JVM-wide `ReentrantLock` keyed by the canonical root path **and** an OS `FileLock` on `<root>/.artifact-lock`; the whole write runs inside the lease, so the check-then-append is atomic with respect to every other writer |
| a forged `COMMITTED` record denies future writes | a record binds a name only when the artifact **on disk** carries its recorded digest; an unverifiable record is stale or forged and never denies |
| unbounded trace | `MAX_INDEX_RECORDS` and `MAX_INDEX_BYTES`; the writer fails closed at the limit and never truncates the index |
| untested I/O paths | the failure windows (staged write, `STAGED` append, move, `COMMITTED` append) get forced in tests |
| power-loss durability not established | the index is appended and `force(true)`d; the root directory is `force(true)`d after the atomic move |
| single-writer ownership | a second **process** is refused (`REFUSED_WRITER_CONFLICT`); a replaced root is refused (`REFUSED_ROOT_REPLACED`) |

**Negative control.** `scripts/artifact-writer-negative-control.sh` mutates the writer's lease
acquisition in place (the repository's established mutation pattern, `scripts/mutation-safety-check.sh`)
and runs the concurrency regression test. The mutation must be **KILLED** — the test must fail
without the lease. The script restores the original under a trap.

---

## 4. Stage 2 — the product path

- Route: `POST /v1/workbench/workspaces/{id}/reports` (a sibling of the existing
  `/v1/workbench/workspaces/{id}/...` routes; no existing route is changed).
- Request: `{question, reportName?, consent, idempotencyKey?, level?}` — no paths, no commands,
  no tool names, no raw content.
- The **server** derives the destination root from configuration and the action identity from
  the request identity + the idempotency key + the workspace + the question.
- Activation: opt-in configuration; default off; the enabled action is `LOCAL_ONLY` — a
  network-exposed process refuses it even with a valid bearer token.
- Response: task id, action name and version, disposition, artifact name + safe relative
  locator, SHA-256, evidence references, epistemic disposition, trace reference, limitations and
  refusal reasons. No absolute filesystem path in the response.
- The report content is generated **server-side** from `workbench.ask(...)`: the workspace's own
  claims, sources, limitations and abstention state. A written report is not evidence for its own
  contents, and the response says so.
- Acceptance is a **real HTTP request against the running Quarkus server** with a committed
  reproducer and a machine-readable report — not an in-process unit test.

---

## 5. Evidence ledger

Appended as the stages produce evidence. Each row: what was measured, the command that measured
it, the artefact that holds it.

| # | Stage | What | How | Artefact |
|---|---|---|---|---|
| 1 | 0 | branch SHAs, identical trees `6051348d`, clean tree | `git rev-parse`, `git status` | this file §1 |
| 2 | 0 | serving app = `matrix-core` (Quarkus), workbench at `/v1/workbench` | source read | this file §1 |
| 3 | 0 | hardware: 32 CPU, 59 GiB RAM, 181 GiB disk, JDK 25 | `nproc`, `free -g`, `df -h` | this file §1 |

*(appended as the stages complete)*
