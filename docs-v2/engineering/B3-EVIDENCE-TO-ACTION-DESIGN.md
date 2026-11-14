# B3 — Evidence-to-Action design (EVID-BASE, 2026-11-14)

**Status:** `DESIGN` · **Branch:** `feature/evid-base-evidence-to-action`
**What this is:** the implementation plan for the directive's §7, anchored on the components
that already exist. Nothing new is built where something existing fits.

---

## 1. The scenario (bounded, real)

A user names an authorized local document collection and asks for a factual analysis.
M.A.T.R.I.X. imports the documents, answers from the workspace's evidence, assembles a report
from the answer's own citations, and **writes a real artifact** into a dedicated allowed output
directory — then verifies the artifact and records an auditable trace.

## 2. The existing components to reuse (found by inspection, not invented)

| Need | The component that exists | What B3 does with it |
|---|---|---|
| the task entry point | `io.matrix.knowledge.LearningWorkbenchResource` (`POST /v1/learning/workspaces/{ws}/seed`, `/learn`, `POST /v1/workbench/workspaces/{ws}/ask`) | the workflow reuses the same seed/ask calls the evaluation runner uses |
| claims + provenance | `KnowledgeStore` / `KnowledgeSnapshot` (claim → evidence → source) | the report's citations are the answer's evidence records |
| evidence retrieval | `KnowledgeRetriever` (already measured in E2) | unchanged; the report cites what it cites and marks the rest |
| the action contract | `io.matrix.actions.ActionRegistry.Action` (`ActionExecutor`, preconditions/postconditions, `ActionResult`) | the new file action IS an `Action` registered in the registry |
| stable action identity | `io.matrix.actions.VersionedContract` (`name`, `version`, `domainHash`) | the idempotency key is the action's versioned identity + the input hash |
| the audit/trace substrate | `io.matrix.audit.HashChain` / `HashChain` links; `io.matrix.api.AuditLogResource` | the execution trace is appended to the existing hash chain, so it survives restart and is tamper-evident |
| the plugin sandbox (for context) | `io.matrix.plugin.DeclarativeSandbox` | not used for file writes; the file action has its own explicit path boundary (below) |

## 3. The security boundary (the only new side effect)

New side effect: writing one artifact into one explicitly authorized directory.

- **Canonical path validation**: resolve the target with `toRealPath()` on the existing prefix
  and refuse any target whose real path is not under the allowed root.
- **Path traversal refusal**: `..`, absolute escapes, and empty names refused before any I/O.
- **Symlink escape refusal**: a symlinked allowed root or a symlinked component inside it must
  resolve under the real root; a link pointing out is refused (the same failure mode the W53
  writer-guard fix addresses, applied to writes).
- **Output size bound**: a hard cap (e.g. 256 KiB) enforced on the serialized bytes before the
  write; exceeding it refuses, it does not truncate silently.
- **Atomic staged write**: write to `name.tmp-<actionId>` in the same directory, `fsync`, then
  `ATOMIC_MOVE` to the final name; a crash leaves either the old file or the new file, never a
  torn one.
- **Stable action identity + idempotency**: the key is `VersionedContract(name, version,
  domainHash) + sha256(input)`; a repeated request with the same key and the same source
  evidence returns the recorded digest and writes nothing a second time.
- **Refusal dispositions**: `REFUSED_OUTSIDE_ROOT`, `REFUSED_SYMLINK_ESCAPE`,
  `REFUSED_TOO_LARGE`, `REFUSED_TRAVERSAL`, `REFUSED_UNSUPPORTED_TYPE` — each a distinct
  `ActionResult.failure` with its reason; no silent fallback.
- **No shell, no network, no arbitrary reads**: the action receives the report bytes in its
  input; it reads nothing but the destination directory's metadata.

## 4. The epistemic boundary (what the report may and may not say)

- The report separates, per conclusion: **supported** (a cited claim with its source), 
  **unresolved** (the workspace has no established claim), **contradicted** (a premise the
  workspace refutes), and **limitations** (the answer's own limitations list).
- Every cited line carries `[claim id] ← [source title] ([locator])`. A conclusion without a
  citation is marked `UNGROUNDED` and cannot be promoted to supported by the action.
- The action does not evaluate truth. Writing the file is recorded as an ACTION, not as
  evidence; the system's own output never becomes a source in the workspace.

## 5. The crash and recovery plan (tests that must exist)

| boundary | the test |
|---|---|
| normal completion | artifact exists, digest recorded, trace appended |
| repeated request, same identity | second call returns the same digest, no second write (mtime/ inode unchanged) |
| crash before commit | staged temp file removed on recovery; no final file; the trace records `STAGED` only |
| crash after staged write, before the outcome record | recovery sees the staged file and no commit record; the final file is absent; the staged file is discarded |
| crash after commit, before the response | recovery sees the committed digest + the final file; no duplicate write on retry |
| unauthorized destination | `REFUSED_OUTSIDE_ROOT`, nothing written |
| symlink escape | `REFUSED_SYMLINK_ESCAPE`, nothing written |
| missing source evidence | the report is still valid, every unsupported section marked `UNGROUNDED` |
| contradictory evidence | the contradicted claim is reported with both sides named |
| invalid artifact content | refused before the write (the content validator runs on the bytes) |
| size exhaustion | `REFUSED_TOO_LARGE` |

At-most-once holds for a completed write; the guarantee is stated as: *one committed file per
action identity, and a retry can only reproduce it*.

## 6. The demonstration

`scripts/b3-evidence-to-action.py` (or a JUnit integration test when the workflow is in-process):
1. seed the plate-tectonics workspace from the packaged corpus (real documents);
2. ask the E2 question that the retrieval answers well (`A-01`);
3. assemble the report from the answer's citations + the unmatched terms + the limitations;
4. execute the bounded file action into `docs-v2/demos/local-learning-loop/artifact/` (a new
   allowed directory under the demo);
5. verify: file exists, JSON/Markdown parses, every cited claim id is in the workspace, the
   SHA-256 matches the trace;
6. the negative control: a second request targeting `../outside` and a symlinked escape —
   both refused, nothing written;
7. print the trace path and the digest.

## 7. What must NOT be claimed

- The action is not autonomous agency; it is one bounded write behind an explicit request.
- The artifact's truthfulness is not certified by the write; its citations are what the
  workspace holds, and ungrounded content is marked.
- No network, no shell, no background execution, no permission expansion.
