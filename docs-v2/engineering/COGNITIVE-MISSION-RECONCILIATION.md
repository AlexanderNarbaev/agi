# MATRIX — Scientific Mission and Capability Reconciliation

**Status:** PROPOSED strategic interpretation, not a rewrite of constitutional invariants. **Source baseline:** `develop` at `ee72d46c105051cf87d260f7ff868022141db2fc` (2026-11-20). Verify HEAD before implementing recommendations.
**Related:** `docs-v2/governance/COGNITIVE-DEVELOPMENT-PROGRAM.md`, `CRJ-01-ACCEPTANCE.md`, `AGENT-COMMISSION-COGNITIVE-PROGRAM.md`.
**All newly authored material:** English; historical source-language passages are preserved.

## 1. The original mission versus the administrative W46 milestone

| Source | Meaning actually present in the source | Interpretation for the proposed program |
|---|---|---|
| `docs-v2/science/GOALS-REQUIREMENTS.md` (Mission) | Resource-efficient, verifiable, honest AI-class computing from laptop to federation; competence expansion through preregistered experiments | This is the **scientific mission**, not merely a documentation goal |
| `docs-v2/vision/BRAIN-LIKE-SYSTEM.md` | Perception, deliberation and action over hierarchical memory; autonomy impulses; FROZEN safety; independent nodes | These are high-level mechanisms to test as a coherent system, not mandatory new modules |
| `docs-v2/science/FOUNDATIONS.md` | Borrow **operationalizable mechanisms**, not analogies, from mathematics, computing, neuroscience and other sciences | Cross-disciplinary candidates must have concrete contracts, tests and comparisons |
| `docs-v2/science/OPEN-PROBLEMS.md` | Continually growing competence, deterministic serving, federation and portability are open research objectives | Do not claim any such objective solved without reproducible end-to-end evidence |
| `CONSTITUTION.md` Article I, II, III, V, VI | Stratified stochasticity, `K_MAX=20` scoped Boolean artifacts, frozen boundaries, coverage gate, no unverified AGI/infallibility claims | All program work remains subordinate to these accepted invariants |
| `docs-v2/governance/ROADMAP.md` §1–2 | O1 self-description, O2 one documentary truth, O3 reproducibility; W46 milestone | Those are valuable **engineering enablers**, not replacements for the scientific mission; the W46 passage should become historical milestone context |
| `docs-v2/architecture/RUNTIME-TOPOLOGY.md` | Chain A and B have separate state; a governed Bridge crosses that boundary; several cycles and researched modules remain unserved | Capability integration must follow actual served callers and enforce existing ownership |
| `docs-v2/architecture/CAPABILITY-REGISTRY.yml` | Four independent axes for implementation, integration, verification and operation | Keep this as the **single** capability registry. Do not infer serving from existence of a class |
| `docs-v2/governance/PROJECT-STATE.md` | Dated administrative snapshot; release evidence; some carried forward stale text | Correct only the current snapshot under its established update/validation convention |

**Resolution proposal:** Keep the three W46 objectives as *enablers*, not the primary mission. Adopt the seven observable cognitive capabilities and three cycles from the program as the scientific development direction. Do not amend the Constitution to achieve this.

## 2. Evidence-derived state at the 2026-11-20 baseline

- PR #63 was merged into `main` at `188d756e`; `develop` at `ee72d46c` includes its release synchronization and follow-up records. The tree difference is three documentation files. Do not conflate Git ahead-count under squash history with unreleased code.
- The released, compared epistemic test result is **18/25**, with seven remaining positive-case errors. Evaluation and training contamination controls improved, but the same inspected held-out cases cannot be rebranded as newly unseen.
- The report action is a real bounded local serving capability. Its second-run benefit was measured as **replay of a prior same-identity result**, not an independently verified improvement on a new related task.
- The separate GraalVM native-image workflow remains red from an inherited resource limitation; a successful JVM release lane and native-image support are different claims.
- Program-specific risks needing independent reproduction: changed-payload idempotency; Article I file-granular exemptions; print-only adversarial probes; missing concept definitions; stale current-facing docs and unresolved owner decisions.
- Chain A↔B communication is governed by versioned Bridge contracts. A second data-sharing channel would violate the intended state-ownership boundary unless explicitly designed and approved.

## 3. Architectural contract mapping

| Domain | Existing implementation / serving candidates | Known gap to measure | No-go shortcut |
|---|---|---|---|
| Perception/concepts | Workbench ingestion, claims/referents, signal encoders | Cross-source stable identity and grounded relations on novel inputs | Treat every lexical token as an object |
| Knowledge/memory | KnowledgeStore, CognitiveStore, brain runtime memory, procedural records | Cross-cycle identity and promotion boundaries; independent learning evidence | Merge stores or share mutable databases between applications |
| Model/prediction | Microworld, hypothesis models, BIR, planner research | Consequence prediction and post-observation revision on a served task | Assume that a modeled prediction was observed |
| Selection/attention | KernelSelector and algorithm registry; cost ledgers | Quality/accuracy-cost evidence measured under actual workload | Always prefer cheaper replay regardless of changed request |
| Action/observation | `POST /v1/workbench/workspaces/{id}/reports` and bounded writer | New-payload conflict behavior and effect verification after faults | Let the planner grant external permissions |
| Developmental learning | Episode history, procedural memory and learning substrates | Demonstrable transfer on **different**, held-out tasks | Count a cache hit as learning |
| Federation | Governed Bridge and federated research mechanisms | Two-node independent truth assessment and bounded autonomy | Signed report automatically promoted to fact |

Treat these as **targets to verify**. A table entry does not prove a complete serving path. The first agent task is a source-and-test grounded reachability matrix with precise owners, routes, production callers, datasets and negative controls.

## 4. Minimal canonical change plan

1. **Now, this PR:** add the proposed English cognitive program, CRJ-01 falsifiable acceptance, this reconciliation, and the bounded agent commission. Update only the active strategic pointer at the top of `ROADMAP.md` and canonical navigation in `INDEX.md`. Do not erase existing wave tables, change constitutional text or rewrite history.
2. **After owner accepts PR:** a named coordinator starts read-only C0 source-verified reachability and freezes the evaluation design before any tuning. Any normative or production-boundary amendment returns as a separate RFC/PR.
3. **After objective baseline and owner authorization:** propose surgical changes to the active milestone/status sections of `ROADMAP.md` and `PROJECT-STATE.md`, synchronizing precise current SHA and validation conventions. Retain original W46/W53/EPISTEMIC documents as historical evidence. Do not replace them wholesale with aspirational content.
4. **As capability changes actually land:** update `CAPABILITY-REGISTRY.yml` rows with observed implementation, serving, verification and operation, exact caller/test path and tested SHA; update the one `ROADMAP.md` task row; do not create a second capability registry.
5. **Before stable release:** ensure all approved technical checks, actual packaged behavior, security gates and explicit owner consent to merge. Never use admin bypass for a failed technical check.

## 5. Gaps requiring the owner's decision, not arbitrary defaults

- Contract and backward compatibility behavior for an identical idempotency key with a changed report request; first reproduce, then offer options and the smallest safe semantics.
- Amendment to a governance-controlled Article I gate and accepted baseline: draft exact RFC, demonstrate failing negative-control fixture, secure approval.
- Existing provisional identity/alias rule (OD-065), fold replacement, peer durable payload and missing publication credentials: preserve current statuses until directly resolved.
- Scope of real-world autonomous acts, new external services, peer network exposure, personal data use and durable learning promotions.
- Major dependency upgrades and modifications to frozen or owner-owned agent/CI config.

## 6. Acceptance of this reconciliation

A reviewer must be able to identify: (a) where the mission originates, (b) which statements are accepted invariants versus proposed goals, (c) which current capabilities are genuinely reachable, (d) what tests would falsify each proposed competence, and (e) which changes require an owner decision. If any are missing, mark this reconciliation INCOMPLETE rather than reporting strategic agreement as an implemented capability.

**Result classification for this document:** `ENGINEERING_MAINTENANCE` and `RESEARCH_INFORMATION_GAIN` only. It introduces no executable cognitive capability.
