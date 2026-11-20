# CRJ-01 — Cognitive Reference Journey: executable acceptance contract

**Status:** PROPOSED / not yet executed. **Owner approval required** before new production actions, network exposure, schema migration, changes to protected files or Level-3 operations.
**Program:** `docs-v2/governance/COGNITIVE-DEVELOPMENT-PROGRAM.md`.
**Purpose:** Establish whether MATRIX's existing, served mechanisms can form and apply new knowledge on a different related task, and whether actual effects and epistemic standing are retained across restarts.

## 1. Scientific question

Can the system, given unfamiliar but independently checkable information, (a) distinguish evidence from hypotheses, (b) maintain durable entity/claim/procedure identity, (c) choose a valid method, (d) carry out one authorized bounded action, (e) independently observe the effect, and (f) use a learned rule or procedure to improve a **different** held-out task after restart?

**Null hypothesis:** any observed improvement is explained by exact request replay, preexisting rules, lexical overlap, selection bias or unaccounted computation.

This is not a claim of intelligence or generalization. A failed result is a legitimate experiment result and must change the next research decision.

## 2. Three evaluation arms

| Arm | Access to prior experience | What this isolates |
|---|---|---|
| A: stateless reference | Current workspace and approved reference methods, but no stored episode/procedural transfer | Benefit of the ordinary deterministic task method |
| B: exact replay/cache | Same context as A plus exact-key replay/retained original result; no generalization to different tasks | Repetition/caching advantage |
| C: governed learned competence | A's sources and B's safety controls plus **only independently validated, versioned** learned claims/rules/procedures and their provenance | Incremental benefit of genuinely transferable experience |

A, B and C receive equivalent permission, candidate sources and compute budgets. Use identical fresh test tasks and record whether any arm is inapplicable. Cached identical queries must be scored separately from genuinely new task instances. If arm C wins only exact repeats, do not call it learning transfer.

## 3. Domains, evaluation freezing and ground truth

1. **Evidence-rich technical documents:** unseen sources with real referents, conflicting or incomplete claims and attributed changes in revision/scope. Source truth is a separate, precommitted evidence manifest. This domain assesses entity formation, citation selection, conflict/abstention and procedural reuse.
2. **Deterministic microworld:** an independently specified transition function, bounded observations/actions and delayed outcomes. Oracle knows initial state, authorized actions, state transitions and post-action truth. This domain assesses prediction, purposeful observation, model revision and strategy transfer.

The first design stage must select and pre-register a small **dev set** and an independently sealed test set, ideally 30–50 novel tasks across both domains if the data and budget permit. Never present a reused or developer-inspected sealed set as untouched. Preserve immutable dataset hashes, generator/source versions, selection rules, random seeds, oracle implementation hashes and exact branch SHA before tuning. If examples are authored by the same agent that builds the solver, use an independent reviewer to freeze the test cases and a separately implemented oracle.

Constrain evaluation so question changes, paraphrases, irrelevant-context shifts and source version changes cannot pass through an exact cache identity. Negative controls must include unsupported premises, false claims, ambiguous names, contradictory evidence and an unavailable action.

## 4. Minimum real serving journey — phase-by-phase gate

| Phase | Required observable evidence | Negative control |
|---|---|---|
| PERCEIVE | Previously unknown input is parsed into attributed observations and scoped candidate entities | Misidentified pronoun or unsupported referent does not become verified |
| RESTORE | Relevant preexisting workspace knowledge and history are loaded by canonical IDs; source revisions identified | Historical discarded revision is not silently current |
| INFER | Hypothesis/answer and explicit uncertainty are produced, with cited evidence or an honest refusal | Unsupported or contradicted premise is not treated as established |
| PLAN | Existing kernel/portfolio chooses an applicable method with traceable reason and budget | Unmet precondition prevents execution; forced invalid method refuses |
| AUTHORIZE | Explicit local action permission is checked independently of the planner | No consent, forbidden root, unsupported external exposure → no write |
| ACT | A bounded report artifact is written, using the approved writer and idempotency contract | Reused key with changed payload does not return stale success |
| OBSERVE | Separate reader verifies file bytes, digest, ledger/index and state transition | Corrupted, missing or mismatched artifact is reported as failed effect |
| LEARN | Independent outcome is admitted to an eligible learning/proposal stream; raw experiment traffic stays quarantined | Held-out evaluation observation cannot contaminate durable organic learning |
| VALIDATE | Proposed knowledge/procedure is checked on independent examples, then promoted/rejected with version and provenance | Incorrect rule, source contradiction or insufficient evidence blocks promotion |
| REUSE | Different related task retrieves accepted knowledge/procedure and changes the choice, prediction or result | Exact cache disabled; change in workspace/revision invalidates stale applicability |
| RESTART | Packaged app is stopped and restarted; same accepted version and trace remain usable | Recoverable interrupted action neither duplicates effects nor hides uncertainty |
| FEDERATE (later) | A second independent node receives signed candidate evidence and applies local verification | Forged, conflicting or unauthorized peer cannot promote fact or bypass consent |

**Important:** CRJ-01's first local milestone ends after RESTART. FEDERATE is a separate conditional expansion, requiring owner-approved external capability and network boundaries. The first journey must use actually mounted HTTP/CLI serving endpoints, not only direct Java resource calls. Retain small direct Java tests for fault injection but do not conflate them with packaged execution.

## 5. Safety-first acceptance gates

**G0 — baseline and reachability.** Identify real served entry points, data owners, actual callers and prototype-only modules. Demonstrate a complete positive scenario with current baseline, preserving observed faults; pin the SHA and trace. No new subsystem unless a measured gap demands it.

**G1 — reliability.** Independently test same key/same payload (replay), same key/different question, level, report name or workspace (conflict or safe refusal), tampered stored digest, source revision, process crash, missing result and no consent. A new bug must fail before the fix and pass afterward. Do not silently change action identity semantics: seek an owner decision if the external contract must change.

**G2 — epistemics.** Positive cases score grounded answer/citation; negatives score correct refusal and named provenance. Track missing gold at retrieval versus citation layer. Any increase in positive answer coverage that worsens incorrect confident answers is rejected pending explicit scientific trade-off authorization.

**G3 — learning beyond replay.** Arm C must outperform A and B on an independently frozen set of **different tasks**, with per-case outcome, confidence interval or paired uncertainty treatment as appropriate, and a pre-registered minimum meaningful improvement. If the observed interval is compatible with no benefit, classify INCONCLUSIVE. If deterioration occurs, classify REJECTED. Never invent an effect-size threshold after viewing test results.

**G4 — persistence and independent effect.** Preserve actual packaged request/response, independent artifact digest, action index, committed cognitive trace, model version, accepted procedures and restart evidence. A direct method call alone does not satisfy this gate.

**G5 — compute economics.** Report wall time, CPU/RAM, resource/CI cost, per-task latency and user-relevant accuracy by arm, where instrumented; mark unavailable metrics `NOT_INSTRUMENTED`. If advanced hardware shows no end-to-end gain over CPU reference, keep it experimental.

## 6. Required experiment artifacts

- `CRJ-01-PROTOCOL.md` — preregistered hypotheses, arms, domains, metrics, data split, freeze hashes, budgets and stop rules. Proposed, not evidence until frozen.
- `CRJ-01-ARCHITECTURE-GAP.md` — source-linked path map and delta; zero invented callers.
- `CRJ-01-EVIDENCE.json` — evaluated rows with `case_id`, immutable version, arm, answer/outcome, oracle verdict, citations, duration, permission decision and trace reference.
- `CRJ-01-RESULT.md` — baseline/delta/uncertainty, negative outcomes, observed deficiencies and decision.
- JUnit XML, packaged smoke logs, request/response JSON, checksums, restart proof, CI URLs and actual SHA captured outside ephemeral `build/test-results` before later tests erase them.

Do not generate these named result files with fake data at program inception. Use the existing research/evidence catalogues and accepted document-status conventions rather than creating a competing register.

## 7. Immediate work backlog (requires source verification before implementation)

| Task | Required next action | Acceptance / stop |
|---|---|---|
| CRJ-01.0 | Independent read-only source map: reachability of perception → claims → kernel → action → episode/procedure → future behavior | Every link names a production caller; unserved mechanisms explicitly labeled |
| CRJ-01.1 | Reproduce or disprove changed-payload idempotency risk through packaged HTTP before implementation | Same key + different payload never returns a wrong stale success; exact replay stays correct |
| CRJ-01.2 | Review R-EPIS-10 member-granular Article I test gap and R-EPIS-11 print-only instruments | Submit owner RFC for protected gate changes; demonstrate negative controls for security-critical assertions |
| CRJ-01.3 | Freeze first two-domain test manifest and three-arm comparator | No tuning against frozen data; reusable baseline with independent oracle |
| CRJ-01.4 | Build smallest approved cross-cycle adapter where demonstrated missing, preserving Chain A/B ownership | Executed route and persisted trace, no duplicated conductor/DB |
| CRJ-01.5 | Compare A/B/C on different unseen tasks and assess genuine transfer | Positive independently reproducible gain or honest falsified/inconclusive hypothesis |
| CRJ-01.6 | Conditional second-node test and workload-based CPU/GPU/FPGA research | Separate owner-approved scope; no network expansion by default |

## 8. Decision and resource rules

Research tasks are reversible and scoped; production and persisted-data changes need the existing owner's authorization class. No autonomous rewrite of `CONSTITUTION.md`, `AGENTS.md`, `.github/workflows/**`, `.veai/**`, `opencode.json`, live `data/` or other protected material.

The coordinator assigns independent specialists to non-overlapping tasks, caps deep-research parallelism at four, runs one heavy Gradle job at a time, and records actual expenditure. Every significant checkpoint must say PRODUCT_GAIN, RESEARCH_INFORMATION_GAIN or ENGINEERING_MAINTENANCE, with CONTINUE/PIVOT/STOP and evidence. Pivot after two substantive unsuccessful attempts with no new knowledge. Do not optimize an experimentally falsified hypothesis indefinitely.

**Acceptance authority:** owner and independent reviewer. A syntactically valid specification, passing unit test or algorithm benchmark is never sufficient evidence for an unserved end-to-end cognitive capability.
