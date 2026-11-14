# B3 — Evidence-to-Action (EVID-BASE, 2026-11-14)

**Status:** `RESEARCH` — the implementation and its measured demonstration · **Branch:** `feature/evid-base-evidence-to-action`
**Reproduce (from the repository root, JDK 25):**

    export JAVA_HOME=<JDK 25>
    ./scripts/b3-demonstrate.sh

**Artifacts of one run:**

| Artifact | Value |
|---|---|
| the report | `docs-v2/demos/evidence-to-action/output/evidence-report.md` — **51,541 bytes**, SHA-256 `115936e8e14a0326aa08260d5c196fd38e924c875aea7472edf7fdc2c8c8ef1b` |
| the trace | `docs-v2/demos/evidence-to-action/output/.artifact-index.ndjson` — **2 records: STAGED + COMMITTED**, same digest |
| the measurement | `docs-v2/engineering/B3-EVIDENCE-TO-ACTION-RESULT.json` |
| end-to-end latency (this artifact's run) | seed **3,583 ms**, write **2 ms**, total **3,681 ms** |

---

## 1. What was implemented (reuse first)

| Component | Where | What it does |
|---|---|---|
| `ArtifactWriter` | `matrix-core/src/main/java/io/matrix/actions/ArtifactWriter.java` | the *only* new side effect: one artifact into one authorized root, with the boundary, the atomic staged write, the append-only index and crash recovery |
| `ArtifactAction` | `…/actions/ArtifactAction.java` | the adapter that registers the writer as an existing `ActionRegistry.Action` (`report.artifact.write`), so the action is listed, precondition-checked and postcondition-checked like every other action |
| the workflow | `matrix-core/src/test/java/io/matrix/knowledge/EvidenceToActionDemonstrationTest.java` | the real end-to-end path: `AcquisitionCycle.loadSeed` → `KnowledgeWorkbench.ask` → report assembled from the answer's citations → the action → verification → trace |
| the boundary tests | `matrix-core/src/test/java/io/matrix/actions/EvidenceToActionTest.java` | 11 tests over the boundary, idempotency and crash boundaries |

Nothing new was invented where something existed: the corpus import, the retrieval, the answer
record, the action contract and the audit index are the project's own.

## 2. The security boundary, as implemented and tested

| Property | Implementation | Test |
|---|---|---|
| canonical path validation | the root is `toRealPath()`-ed at construction; the target's real parent must equal it | `unordered` names refused; the parent check runs on every write |
| path traversal | a name with a separator, `..`, a leading dot, or the reserved index name is refused **before any I/O** | `unauthorizedDestination`, `reservedNames` |
| symlink escape | a symlink at the target is resolved; its real path must stay under the root | `symlinkEscape` → `REFUSED_SYMLINK_ESCAPE`, the outside file untouched |
| output size bound | 256 KiB; an oversized artifact is refused, never truncated | `contentBoundary` |
| explicit destination authorization | the root is a constructor argument; no default root exists | every test constructs the writer with its root |
| atomic staged write | temp file in the root → `force(true)` → `ATOMIC_MOVE` | `normalCompletion` (no temp left) |
| stable action identity | the writer's `actionId`; the `ArtifactAction` adapter carries the action's name and version | `identityNamesOneArtifact` |
| idempotent retry | the same identity + the same digest and a re-hashed matching file → `REPLAYED`, no second write, mtime unchanged; a tampered or missing file → `RECONCILED_WRITTEN` | `idempotentRepeat`, `tamperedArtifactIsNotReplayed` |
| refusal dispositions | `REFUSED_OUTSIDE_ROOT`, `REFUSED_TRAVERSAL`, `REFUSED_SYMLINK_ESCAPE`, `REFUSED_UNSUPPORTED_TYPE`, `REFUSED_TOO_LARGE`, `REFUSED_INVALID_CONTENT`, `REFUSED_EMPTY_NAME`, `REFUSED_IDENTITY` | each has a test |
| no shell, no network, no arbitrary reads | the executor only calls `writer.write` with the bytes it is given | the whole class has no such API surface |

## 3. Crash boundaries and the guarantee

| State | How it is produced | Recovery |
|---|---|---|
| crash before commit | a staged temp + a `STAGED` record, no commit | `recover()` → `NO_COMMIT`, the temp is removed, no final file; the retry writes exactly once (`crashBeforeCommit`) |
| crash after the staged write, before the outcome record | the same state (the staged file and its record are the durable evidence) | the same resolution |
| crash after the commit, before the response | `COMMITTED` record + the final file | `recover()` → `COMMITTED`, `finalPresent`, no rewrite on retry (`crashAfterCommit`) |
| commit record but the file is gone | delete the artifact | `recover()` → `COMMITTED_BUT_FILE_MISSING`; the retry → `RECONCILED_WRITTEN`, the same digest, one new commit record (`committedButFileMissing`) |

**The guarantee, stated honestly:** for one action identity, **at most one committed artifact
can exist**, and a retry can only reproduce it — a retry re-hashes the artifact against the
recorded digest (a tampered or missing file is rewritten, never replayed from the record).
**Not claimed:** durability across a power loss — Java exposes no directory fsync without
native access, so the guarantee is crash consistency of the file state (the file is either
old or new, never torn), not power-loss durability of the rename. The writer does not claim exactly-once for a
process that dies *between* the staged file's creation and its record (that window leaves a
temporary file that recovery removes); it claims that no state leaves two committed artifacts
or a torn final file.

## 4. The epistemic boundary

The report is assembled only from the answer record: its statement, its claims (each with its
id and status), its sources, its limitations and its unmatched terms. A question the workspace
abstains on produces a report whose Answer section says so and whose `UNGROUNDED` marker is
explicit — the file action succeeds, the epistemic result is the abstention. The action's
output certifies only that the artifact was written and verified (digest, existence); it
certifies nothing about the truth of the sentences, and the report says so in its own
"Epistemic status" section. The system's output is never added to the workspace as a source.

## 5. The demonstration, exactly as it ran

- task: `What is plate tectonics and what does it say about the Earth's lithosphere?`
- the seed corpus imported through the production path: `usgs-this-dynamic-earth.md`,
  `wikipedia-plate-tectonics.md`
- the answer: **answered, 41 claims, 2 sources**
- the artifact: 51,541 bytes, digest `115936e8…c8ef1b` (the trace records the same digest
  twice: `STAGED`, then `COMMITTED`)
- negative controls, all refused and nothing written: a parent-directory name (a leading
  parent reference) → `REFUSED_TRAVERSAL`; an absolute destination → `REFUSED_TRAVERSAL`;
  a symlink escaping the root → `REFUSED_SYMLINK_ESCAPE`
- the retry with the same identity → `REPLAYED`
- the recovery read-back → `COMMITTED`
- measured latency (the committed result): seed 3,583 ms, write 2 ms, total 3,681 ms

## 6. What this does not claim

- It is not general autonomous agency: one bounded write behind an explicit request.
- The artifact's contents are the workspace's claims — some of them are only loosely about the
  question (the retrieval's known weakness, measured in E2); the report shows them as they are
  with their statuses rather than curating them.
- It does not distribute, transmit, or schedule anything; the next invocation is the caller's.
- The demonstration runs in-process through a local `ActionRegistry`; the action is
  **implemented** and exercised by the demonstration, but it is **not wired into a production
  route** (`ActionRegistry` is not instantiated by the running service), so it is not
  operational end-to-end over HTTP. An HTTP route and the identity→`VersionedContract`
  binding are deliberate follow-ups, not claimed here.
