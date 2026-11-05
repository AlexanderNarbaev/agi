# W46.0 — CONSOLIDATION PLAN

**What W46.1, W46.2 and W46.3 will do, in what order, and what each one costs.**
**Prepared:** 2026-11-04, on the evidence of the W46.0 audit. **Nothing in this plan has been
executed yet**; W46.0 is read-only and this wave does not commit or push.

---

## 1. The state of the audit — ten deliverables

| the deliverable | the state |
|---|---|
| `W46-INITIAL-STATE.md` | **done** — the branch, the HEAD, the remote, the eleven owner entries, the environment, the discrepancy with the directive's premise |
| `W46-REPOSITORY-TOPOLOGY.md` | **done** — 14 modules included, 3 commented out, 2 outside Gradle, 1 orphan file; 8 dependency edges and **no module cycle**; two class-level cycles and a 42-package SCC; 79 CLI entry points; 11 raw listeners; the storage ownership |
| `W46-DOCUMENT-AUDIT.csv` | **done** — 880 rows (every document outside `docs-v2/_raw/`) with its category, status, currency, last update, the known stale reference and the successor |
| `W46-CAPABILITY-RECONCILIATION.csv` | **done** — 24 capabilities with **four independent axes**: the implementation, the integration, the verification and the operational status |
| `W46-ARCHITECTURE-TRUTH-MAP.md` | **done** — the two chains, the route census, the six cycles, the persistence map, and the ten facts no document records |
| `W46-HARDWARE-AND-RESOURCE-REPORT.md` | **done** — the machine as it is, the probe's eleven gaps, the resource envelope with every value classified, and the GPU truth |
| `W46-OWNER-DECISION-PACKET.md` | **done** — D1–D4 reconciled against the repository: **approved and implemented**, with three residual gaps on D3 and one defect on D4 |
| `W46-RISK-REGISTER.csv` | **done** — 20 risks with the severity, the probability, the impact, the evidence, the exposure, the mitigation and the owner |
| `W46-OPEN-QUESTIONS.csv` | **done** — 15 questions with a stable ID, the competing explanations, the experiment that would resolve it and the owner decision it needs |
| `W46-CONSOLIDATION-PLAN.md` | this document |

---

## 2. The five things the audit found that must change

Everything else is bookkeeping. These five are the substance.

### 2.1 The decisions are already made — the plan must not re-open them

D1–D4 were answered by the owner (twice; the second answer took all four recommended
options) and implemented in W45.2 as `a6ded233`. The W46 directive asks for them to be marked
`OWNER_APPROVAL_PENDING`; **the owner's own answer supersedes that instruction**. The plan
therefore treats them as decided and carries only the residual gaps:

| the gap | the kind | the cost |
|---|---|---|
| **D3-a** — the flag is `?replace=true`, the directive names `replaceExisting: true` | an owner decision | one small wave, with a deprecation path if the shape changes |
| **D3-b** — no dry-run / preview mode | an owner decision | one small wave, and a new HTTP surface |
| **D3-c** — a workspace replacement erases the evidence of an observation while keeping its case and its decision history | an owner decision | one small wave and a migration |
| **D4-a** — the writer lock does not resolve symlinked path aliases | **a defect**, not a decision | one small defect fix and a test |
| **F-01** — the gateway serves a **stub** by default | an owner decision | a launcher default and a `/health/live` correction |
| **F-02** — chain A opens **three** ports, two of them undeclared | an owner decision | a declaration, and possibly a configuration change |
| **F-03** — the W45.2 declaration mis-attributes `BirBrainCycle` to chain A | **a defect** in the coordinator's own work | a one-line correction and a test |

### 2.2 There is no single current-state document, and four documents pretend to be one

`SESSION.md` (7885 lines, September's body), `state.yaml` (2026-08-10, `status: init`),
`docs-v2/engineering/PLAN.md` (three waves behind) and the root `README.md` (which presents
the gateway as the project and names a `WAL.md` that does not exist) are four candidates with
four different ages. **W46.1 creates one** and marks the other three as what they are.

### 2.3 There are two capability registries with two different vocabularies, and both are stale

`docs-v2/architecture/CAPABILITY-REGISTRY.md` (dated 2026-10-30, with three refuted numbers)
and `docs-v2/engineering/W44-CAPABILITY-REGISTRY.md` (a different vocabulary). **W46.1
replaces both with one machine-readable registry** on the four-axis model that
`W46-CAPABILITY-RECONCILIATION.csv` already uses, and marks the two predecessors as
superseded with pointers.

### 2.4 The link graph is unchecked, and it is broken in 51 places

The audit found 51 broken repository-relative links, 116 prose references to paths that do
not exist, 42 duplicate stable identifiers (including **a real collision**: `SPEC-002` is two
different documents), and **580 of the 849 `docs-v2` documents unreachable** from
`docs-v2/INDEX.md`. **W46.3 builds the checker** that would have caught this, and **W46.2
repairs the current documents** — the historical ones keep their history.

### 2.5 The GPU claim in the documentation is false, and the fix is one environment variable

The hardware is healthy, the CUDA provider is compiled in, and the model MATRIX already ships
runs **12.6× faster** on the GPU — but `OnnxRuntimeAdapter` sets the library path as a JVM
system property while a native `dlopen` consults `LD_LIBRARY_PATH`, so the registration
fails, the exception is swallowed, and the diagnostic that reports the active provider is a
**hardcoded `CPUExecutionProvider`** string. **The fix is one line in the launcher; the
diagnostic must stop lying.** This is a Level 2 change (it touches the serving path), so it
goes to the owner with a measured before/after.

---

## 3. The plan by phase

### W46.1 — the canonical governance structure

**The rule: reuse what already performs a role; do not create a second source of truth.**

| the role | the current holder | the action |
|---|---|---|
| the project identity and the current state | **none** | create `docs-v2/governance/PROJECT-STATE.md` — a snapshot, not a log: the branch, the HEAD, the latest wave, the test baseline, the blockers, the pending decisions, the next tasks |
| the roadmap | `docs-v2/engineering/PLAN.md` (three waves behind) | create `docs-v2/governance/ROADMAP.md` with the stable task IDs; point PLAN.md at it with a status annotation |
| the decisions | scattered: `W45-DECISION-LOG.md`, `W45-OWNER-DECISIONS.md`, `W45.2-OWNER-DECISIONS.md`, 8 ADRs | create `docs-v2/governance/DECISION-REGISTER.md` — one row per decision with its stable ID, its status, its date and its successor |
| the risks | `docs-v2/engineering/RISK-REGISTER.md`, `W42-RISK-REGISTER.md` | promote the W46 register to `docs-v2/governance/RISK-REGISTER.md`; mark the predecessors superseded |
| the open questions | `docs-v2/quality/OPEN-DECISIONS.md` (2026-10-18) | promote the W46 register to `docs-v2/governance/OPEN-QUESTIONS.md` |
| the agent protocol | `AGENTS.md` (normative, protected) | create `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` — the autonomy levels, the task classification, the subagent protocol, the handoff format. **The amendments to `AGENTS.md` are prepared as a separate proposal with the exact diff** — the file itself is not edited |
| the document statuses | `W46-DOCUMENT-AUDIT.csv` | create `docs-v2/governance/DOCUMENT-STATUS-POLICY.md` — what each status means and who may change it |
| the capability registry | two, both stale | create `docs-v2/architecture/CAPABILITY-REGISTRY.yml` on the four-axis model, with a validator |
| the work log | `wal/SESSION_WAL.md` (ignored by git, last touched at W43.5) | **one authoritative chronological work log.** The decision: the W46 wave records live in `docs-v2/waves/` as the wave reports, and the log becomes an index of the waves rather than a second narrative |
| the architecture | `OVERVIEW.md`, `MATRIX-COGNITIVE-ARCHITECTURE.md` | create `docs-v2/architecture/RUNTIME-TOPOLOGY.md` (the truth map's serving part) and `DATA-AND-MEMORY-ARCHITECTURE.md` (its persistence part); annotate the two predecessors |
| the test catalogue | none | create `docs-v2/engineering/TEST-CATALOGUE.yml` — per scope: the filter, the last verified commit, the exact results, the known skips, the environmental dependencies |
| the operations | `HARDWARE-PROFILE.md` (stale in six fields, misleading in one), `RUNBOOK.md` (no backup procedure) | regenerate the profile, and create `RESOURCE-BUDGETS.md` and `RECOVERY-RUNBOOK.md` |

**What W46.1 must NOT do:** move files. The target structure is a *logical* one; every moved
path gets a **compatibility pointer** instead, and nothing is renumbered.

### W46.2 — the reconciliation

In order:

1. **Repair the current navigation**: the root `README.md` (a concise entry point, both
   applications, the real commands), `docs-v2/INDEX.md` (generated or validated against the
   tree — not a second hand-maintained inventory).
2. **Repair the broken references in the current documents** — the 51 broken links, the
   `WAL.md` references in `BRANCHING-STRATEGY.md`, `INSTALL.md`, `docs/index.md` and
   `README.md:49`, and the five non-existent paths in `AGENTS.md` (**as a proposed amendment**,
   not as an edit).
3. **Reconcile `SESSION.md` and `state.yaml`** — mark them historical with a pointer to the
   canonical current state, and change neither one's content.
4. **Reconcile the architecture documents**: annotate `MATRIX-COGNITIVE-ARCHITECTURE.md`
   (the merging is decided), `OVERVIEW.md` (the chains and the cycles are missing),
   `CAPABILITY-REGISTRY.md` (superseded), `STANDARDS-MATRIX.md` (the version's source is
   wrong; the ADR list is incomplete; the gates are not blocking — and the document does not
   say so).
5. **Resolve the native-image contradiction** — the W44 map attributes all 24 failures to
   native-image; the W45 reconciliation established 23 native + 1 ordinary JVM. **One
   authoritative text**, and the other points at it.
6. **Decide the fate of `constitution.md`** — it is an unfilled template duplicating
   `CONSTITUTION.md`; on a case-insensitive filesystem the two names collide. It must not be
   deleted silently; the proposal is a status header naming its successor.
7. **Preserve the W44 and W45 records** — they are evidence. The registry marks them
   HISTORICAL and names their successors; nothing is rewritten.
8. **Make the negative evidence discoverable** — the negative results index, the hypothesis
   registers, and the four high-risk findings that are currently invisible from the navigation.

### W46.3 — the autonomous engineering infrastructure

| the tool | what it does | the contract |
|---|---|---|
| `scripts/docs-link-check.py` | the link, anchor, prose-path and duplicate-id checker | deterministic, no network, a report-only mode and a strict mode, machine-readable output, a non-zero exit in the strict mode |
| `scripts/validate-project-registry.py` | the capability registry's schema, the source paths, the evidence references | the same |
| `scripts/validate-project-state.py` | the current-state document against the actual repository (the branch, the HEAD, the wave, the test baseline) | the same |
| `scripts/generate-project-report.py` | the project report from the registries | the same |
| `scripts/hardware-probe.sh` — **extended** | the eleven missing measurements (the CUDA **toolkit** against the **compatibility**, cuDNN, `/tmp`, the Gradle daemon/workers/test heap, Docker, the Python/ML stack, the ONNX providers, GraalVM, the build-directory sizes, the JDK against the JRE, the virtualization key) | it writes into the repository only on an explicit flag |
| `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` | the session initialisation, the task classification, the autonomy levels, the subagent protocol, the end-of-session handoff | — |
| the skill inventory | the state of `.opencode/skills/`, `.codex/`, `.veai/`, the MCP configuration | **a recommendation, not six new packages** |

**The rules the tools must obey:** deterministic; no network unless asked; no modification of
user data; a report-only mode; clear exit codes; machine-readable output; existing
dependencies only; **no new database and no external service for the project metadata**.

---

## 4. What is deliberately NOT in W46

- **No production code change** beyond the two defects (D4-a, F-03) and the GPU launcher
  line, each of which needs the owner's authorisation.
- **No new kernel, memory store, conductor, world model, hormonal system or agent framework.**
- **No second store.** The audit confirmed that none is needed.
- **No full `matrix-core` suite run.** It needs the owner's approval and a resource estimate.
- **No commit and no push** until the owner reviews the final report.
- **No edit to `CONSTITUTION.md`, `AGENTS.md`, `.gitignore`, `opencode.json`, `.codex/**`,
  `.veai/**`, the existing `.opencode/**`, `ethics/frozen/**`, `avro/**` or
  `.github/workflows/**`** — proposed amendments are prepared as separate files with the
  exact diff.
- **No deletion of a historical document.** The archive policy assigns a status, names the
  successor and removes the obsolete links from the current navigation — it does not remove
  the history.
- **`docs-v2/_raw/` stays untouched.**

---

## 5. The risk of the plan itself

| the risk | the mitigation |
|---|---|
| the consolidation becomes **documentation volume** instead of a reduction in the time to understand the project | the acceptance test is a measure: a new agent must answer the thirteen questions of the mission from the canonical documents alone. If it cannot, the structure is wrong, not the volume |
| the document registry becomes **a second stale inventory** | it is **generated** from the tree, and a validator checks it against the tree; a hand-maintained registry is exactly the failure mode this wave exists to remove |
| the wave never stops | W46.0 ends with a checkpoint and the owner's review. **Every phase has a checkpoint; no phase begins without the previous one's acceptance criteria being met** |
| the agents repeat the archaeology | the session protocol, the work log and the registries are the memory. The audit's own numbers (1162 documents, 79 CLI entry points, 2909 classes, 76 858 edges, 210 + 28 routes, the storage ownership) are **in the repository** so that the next agent does not recompute them |
