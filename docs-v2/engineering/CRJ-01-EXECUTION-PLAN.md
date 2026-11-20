# CRJ-01 — Execution plan: the task DAG, the budget and the owner-only boundaries

**Status:** ACTIVE · **Date:** 2026-11-20 · **Phase:** C0 executed, C1 prepared, C2 partially executed
**Program:** `docs-v2/governance/COGNITIVE-DEVELOPMENT-PROGRAM.md` (accepted as PR #64)
**Commission:** `docs-v2/engineering/AGENT-COMMISSION-COGNITIVE-PROGRAM.md`
**Contract:** `docs-v2/engineering/CRJ-01-ACCEPTANCE.md`
**Branch:** `develop` · **Baseline HEAD at the start:** `b7bd1be4`
**Outcome of this round:** `docs-v2/engineering/CRJ-01-VALUE-CHECKPOINT.md`

## 0. What this document is

The commission's C0 requires "an exact feature/task DAG with separate owners, acceptance tests,
computation budget and owner-only boundaries". This is that DAG. It is a **plan**, not a register:
the canonical task states live in `docs-v2/governance/ROADMAP.md` — the owner's
**"Current strategic direction"** table, which registers the program as **COG-00 … COG-06** — the
present truth lives in `docs-v2/governance/PROJECT-STATE.md`, the decisions live in
`docs-v2/governance/DECISION-REGISTER.md`, and the risks in `docs-v2/governance/RISK-REGISTER.md`.
The `CRJ.0.x` / `CRJ.1.x` / `CRJ.2.x` ids **below are the sub-tasks of the owner's COG rows**, not a
second register; each one names the COG row it serves.

**The unit of work is a measured change in behaviour on a served path.** A document, a diagram, a
test-only class, a branch count or an agent round is not a capability (`AGENT-COMMISSION-COGNITIVE-PROGRAM.md` §1).

## 1. The DAG

### C0 — confirm reality (parallel, mostly read-only)

| the id | the task | the owner | the deliverable | the acceptance test | the budget |
|---|---|---|---|---|---|
| **CRJ.0.1** | the reachability map, **Chain A**: perception → claim/referent → hypothesis → method → permission → action → effect | an independent specialist (architecture) | the map: every link marked **SERVED / TEST-ONLY / DORMANT / UNKNOWN**, each with the real production caller at `path:line` | no claim without a citation; no invented caller; a DORMANT link names where it is referenced instead | read-only; no CI; one agent-round |
| **CRJ.0.2** | the reachability map, **Chain B + the bridge + learning**: episode → procedure → new knowledge → a later task | an independent specialist (architecture) | the same, for the brain runtime, the gateway bridge and the learning path | the same | the same |
| **CRJ.0.3** | the **idempotency red team** (CRJ-01.1): a changed question under a reused key | an independent specialist (red team) | a verdict **PROVEN RISK / REFUTED / PARTIALLY REAL** with the deciding code line; the four-variant table (changed question / level / output name / workspace state); the blast radius | the reproduction proposal names the fixture, the two requests and the assertion — **proposed, not executed** here; the coordinator executes it in the single heavy JVM | read-only; no CI |
| **CRJ.0.4** | the **false-green instrument review** (CRJ-01.2): R-EPIS-10 (the Article I exemption is file-granular) and R-EPIS-11 (the print-only probe family) | an independent specialist (instrument review) | the exact code line that makes the exemption file-granular; the files now outside the gate; the minimal fix and the sites it would expose; the classification of every probe class as **measurement / gate / mix** | every claim at `path:line`; the fix is the smallest one, and its governance consequence (the accepted baseline, the owner decision) is named | read-only; no CI |
| **CRJ.0.5** | the **task DAG, the budget and the owner-only boundaries** | the coordinator | this document + the ROADMAP rows | every task has an owner, an acceptance test and a budget; every owner-only boundary is named | one agent-round |
| **CRJ.0.6** | the **CRJ-01 protocol draft** (C1 preparation) | an independent specialist (science) | the draft: two domains, three arms, the oracle and the denominators, the dev/test split, the freeze procedure, the negative controls, the stop/pivot rules | every number is **measured from the repository** or marked `PROPOSED` / `NOT_INSTRUMENTED`; no invented threshold; the owner questions named | read-only; no CI |
| **CRJ.0.7** | the **verification runs** of CRJ.0.3 and CRJ.0.4 | the coordinator | the executed reproduction (if CRJ.0.3 is PROVEN) and the instrument check | **one** heavy `matrix-core` JVM at a time; the JUnit XML retained in `docs-v2/engineering/evidence/` | 1–2 Gradle runs, ≈2 × 8 min |

### C1 — freeze the scientific contract

| the id | the task | the owner | the deliverable | the acceptance test |
|---|---|---|---|---|
| **CRJ.1.1** | the preregistration of CRJ-01 | the coordinator, with the owner where a decision is needed | `docs-v2/engineering/CRJ-01-PROTOCOL.md` — the frozen contract | signed **before** any implementation; the immutable hashes (dataset, generator, oracle, seeds, branch SHA) recorded |
| **CRJ.1.2** | the **arm C** mechanism: what "governed learned competence" reads, and which of its parts exist today | the owner (a Level-2/3 boundary) | the recorded decision | the decision names the classes it reuses and the smallest thing to build |
| **CRJ.1.3** | the **minimum meaningful improvement** and the domains | the owner | the recorded decision | a number with its justification, or `NOT_INSTRUMENTED` — never a threshold chosen after seeing results |
| **CRJ.1.4** | the **freeze** of the sealed test set | an independent reviewer (not the author of the tasks) | the hashes and the manifest | the set the author inspected **cannot** be presented as untouched; the current EPISTEMIC/C2A set's status is stated honestly |
| **CRJ.1.5** | the **negative controls** | an independent specialist | one concrete instance per control class (unsupported premise, false claim, ambiguous name, contradictory evidence, unavailable action) | each has a fixture and an expected verdict |

### C2 — the smallest owner-authorized production integration

| the id | the task | the owner | the acceptance test |
|---|---|---|---|
| **CRJ.2.1** | the **thinnest missing component** that CRJ.1.1 proves is missing | the coordinator, after the owner's approval | the component reuses existing contracts; no new global conductor, knowledge DB, skills directory, agent framework or duplicate register |
| **CRJ.2.2** | the **real packaged execution**: a source-bearing input → a warranted answer or refusal → a selected method → an authorized action → an effect observed by a reader that did not write it | the coordinator | the **packaged** application, not the test class; the artefact's digest verified; the trace retained |
| **CRJ.2.3** | the **arm comparison** | the coordinator | the table with the denominators; arm C's advantage separated from replay, preexisting rules, lexical overlap and selection bias |

## 2. The owner-only boundaries — never inferred, never timed out

A timeout is not consent. The following require the **real owner's** approval, asked through the
native question tool with a grounded recommendation, the alternatives and an editable choice:

1. any new production action, network exposure, or a change to the live `data/` store;
2. any schema change or a migration of a live database;
3. secrets, credentials, the Docker Hub publication (**R-W48-04**) and any deployment;
4. the protected material: `CONSTITUTION.md`, `AGENTS.md`, `.github/workflows/**`, `.veai/**`,
   `opencode.json`, the live `data/`;
5. the merge of a release into `main` (**OD-074** — the ruleset's logged admin bypass covers the
   impossible self-review requirement only);
6. the change to an accepted instrument or baseline: `docs-v2/quality/decision-path-baseline.txt`
   (**R-EPIS-10**), the quality gate's baseline, the capability registry's rules;
7. the **scientific decisions** of CRJ-01: the domains, the arms, the oracle, the minimum
   meaningful improvement (**CRJ.1.2**, **CRJ.1.3**).

Everything else in this DAG is Level 1–2: read-only inspection, tests, and documentation that does
not touch the protected material.

## 3. The computation budget of C0

| the item | the cost |
|---|---|
| the specialists | 4 read-only agents, no CI, no Gradle |
| the coordinator's Gradle runs | 1–2 heavy `matrix-core` JVMs, **one at a time** (≈2 × 8 min), only for CRJ.0.7 |
| the CI minutes | 0 for C0 (documentation-only changes → the fast lane's matrix is empty, **OD-066**) |
| the wall time | one round of specialists plus the verification runs |
| the risk budget | none: no production effect, no live-store write, no network exposure, no protected file |

## 4. The known hazards of this phase, named

- **The sealed sets are inspected.** The EPISTEMIC set (16 cases) and the C2A set (38 cases) have
  been read during development, and H-P02's question was verbatim in the production code until
  2026-11-20. They are **not** untouched and cannot be presented as such (the contract says so
  explicitly). A new set, frozen by an independent reviewer, is required for CRJ-01.
- **The served path is one route.** The reachability map's first honest answer may be that the
  full journey exists only in pieces — the map must say so rather than smooth it over.
- **The idempotency risk is unexecuted.** Until CRJ.0.7 runs it, it is a source-based hypothesis.
- **The fast lane does not build the packaged application nor run the full suite**
  (**R-EPIS-09**): the release lane must be watched before any merge.

---

## 5. What this round executed (2026-11-20)

The plan above was written before any measurement. This section records what actually ran, so
the plan and the result cannot drift apart.

| the id | the outcome | the artefact |
|---|---|---|
| **CRJ.0.1 / CRJ.0.2** | **DONE** — the reachability map; the verdict is **PARTIAL**: one connected served path (Chain A's report journey) and five named break points | `docs-v2/engineering/CRJ-01-REACHABILITY.md` |
| **CRJ.0.3** | **PROVEN RISK, then FIXED** — the changed-payload case was reproduced (red before the fix) and closed under **OD-079**; the four-variant table: changed question = **PROVEN**, changed level = **PROVEN** (the same mechanism), changed report name = **REFUTED** (the writer's name guard), changed workspace state = **REFUTED** (the fingerprint guard) | `docs-v2/engineering/CRJ-01-RISK-VERDICT.md` §1; the retained XMLs |
| **CRJ.0.4** | **DONE** — R-EPIS-10 **confirmed** and **closed** under **OD-080** (the baseline did **not** have to change); R-EPIS-11 **measured**: 11 classes, 51 methods, 0 pure gates | `docs-v2/engineering/CRJ-01-RISK-VERDICT.md` §2 |
| **CRJ.0.5** | **this document**, updated with the outcome | — |
| **CRJ.0.6** | **DONE as a DRAFT** — the preregistration is written and **not frozen**; five owner decisions (D1–D5) are open | `docs-v2/engineering/CRJ-01-PROTOCOL.md` |
| **CRJ.0.7** | **DONE** — the reproduction was executed in the one heavy JVM, the RED artefact was retained before the fix, and the GREEN artefact after it | `docs-v2/engineering/evidence/` |
| **CRJ.1.1** | **BLOCKED on the owner** — the protocol cannot be frozen before D1–D5 are answered and an independent reviewer freezes the set | the protocol's §10 |
| **CRJ.2.1 / CRJ.2.2** | **DONE for the local milestone** — the thinnest missing component was the replay's applicability domain, and the packaged journey ran **16/16** checks over real HTTP, including a restart | `docs-v2/engineering/CRJ-01-JOURNEY-DEMONSTRATION.json` |
| **CRJ.2.3** | **NOT DONE** — the arm comparison needs a frozen protocol and an arm C that exists; the reuse demonstrated so far is arm B (the exact replay) | the protocol's §2 |

**The budget actually spent.** 9 Gradle invocations (one heavy `matrix-core` JVM at a time,
`--offline`, no `--rerun`); 2 runs of the packaged journey; 0 CI minutes; the risk budget was
none — no production data, no network exposure, no protected file, no live store. The agent
budget was **not** as planned: 3 durable teammates and 2 managed subagents were dispatched, and
**5 of those runs failed** mid-task, so the coordinator executed CRJ.0.3 and CRJ.0.4 itself. The
critical path did not wait for any agent.

**The decision:** **CONTINUE** — see `docs-v2/engineering/CRJ-01-VALUE-CHECKPOINT.md` §6.
