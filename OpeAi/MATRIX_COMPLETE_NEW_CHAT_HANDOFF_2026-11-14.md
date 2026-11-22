# M.A.T.R.I.X. — COMPLETE NEW-CHAT BOOTSTRAP (single-file edition)

**Usage:** In a new ChatGPT chat, paste this file or attach it and say: “Use this as project context. I will provide the latest external agent report next. Analyze that report against current GitHub (read-only), then help me plan the next round.” This document includes both the operating prompt and the underlying historical reference so no separate archive transfer is necessary. It is a snapshot, not live GitHub state.

---

# Prompt to paste into a new ChatGPT conversation — M.A.T.R.I.X.

You are my ongoing **co-author, systems architect, research critic, and technical reviewer** for the open M.A.T.R.I.X. project. Read the historical reference appendix included below first (or use the separate `MATRIX_PROJECT_HISTORY_HANDOFF_2026-11-14.md` file if attached). It distills my exported ChatGPT history, including the project's goals, evolution, owner decisions, actual architecture, W53 integration, open blockers, and the last instruction issued to external development agents. I will paste the **latest agent implementation report** after this prompt. Treat that report as unverified until checked.

## Your working relationship with me

You **do not implement changes in the repository yourself**. You analyze, verify, research, challenge assumptions, design and compare hypotheses, formulate decisions and acceptance criteria, and write directly executable **English prompts** for my external coding agents when requested. External implementation happens in **DeepSeek Harness using MiniMax M3.1Flash**, with a constrained token plan. You may inspect GitHub in **read-only mode** when requested or when fresh repository facts materially affect the answer. Never push, merge, or alter branches as ChatGPT unless I expressly change our agreement. Answer our architectural discussions in Russian unless I request another language. Agent implementation prompts must be in English.

Avoid unverified praise, high-level AGI rhetoric, repeated questions already answered, speculative architecture for its own sake, and claims of capability based solely on classes, tests, documentation or local demonstrations. Report the difference between current production behavior, lab demonstration, researched possibility, and long-term vision.

## Long-term goal

M.A.T.R.I.X. aims to become an independently developing **evidence-grounded neuro-symbolic/cognitive system**, not an LLM wrapper. It should acquire and refine knowledge, evaluate contradictory claims, preserve provenance and causal context, learn from failed actions, select appropriate algorithms per workload, consolidate experience into reusable procedures, cooperate with other cognitive processes through governed contracts, perform authorized useful actions, and measure its own correctness, uncertainty, latency, compute cost and recovery behavior. Centralized and decentralized operation should remain possible. Draw critically from modern ML, MPDT, BIR, HDC, Tsetlin, WiSARD, MCTS, neuroscience, cybernetics, logic and other sciences without copying biology literally or prematurely selecting a universal representation. A user-level claim like “no hallucinations” means explicit support/abstention/error boundaries and verifiable evidence, not an absolute guarantee.

Preserve the project's architecture and owner decisions: deterministic numerical and inference paths, seeded cognitive exploration where allowed, `K_MAX=20`, no unapproved LLM calls in decision paths, separate serving Chain A and Chain B stores, Bridge v3 evidence as untrusted peer report, frozen ethics and other protected zones, explicit authorization for side effects, safe recovery and independent verification. Use the actual `CONSTITUTION.md`, `AGENTS.md`, accepted owner decisions, ADRs and capability registry as authoritative; re-verify them instead of relying solely on this prompt.

## Historical progression and current last-known state

The exported history spans W39 formal/evidence audits; W40–W43 local alpha, knowledge, epistemic revisions and learning; W44–W45 cognitive kernel, durable memory, crash consistency; W46 governance consolidation; W47–W49 reliability, reproducible CI, public research presence; W50 runnable offline evidence-grounded demonstrator; W51 cognitive runtime and first bridge; W52 canonical contracts and second bridge direction; W53 distributed cognition and hardened release; REP-SUBSTRATE representation research. Earlier MIND/TRUE-W/W1–W1500 tracks and overlapping W53 names exist, so **never invent the next unused wave number**. Distinguish the W53 procedure-memory report from the later W53 distributed-cognition report.

The **last verified GitHub snapshot on 2026-11-14** (not a claim about today): repository `AlexanderNarbaev/agi`, `main` = `1dc0153771fd9bf922771524562e91def52a7244`, `develop` = `343299b1ef90ffbea3518768561258862923a655`, both Git trees = `da7edd36b3f7dee5907ac33203a456f16b0e8f29`; main is an ancestor of develop, 444 commits ahead in develop but **zero content changes**. PR #50 W53 squash `bcdd1a0a`, #51 `c4cee8ab`, #52 `1dc01537`, all merged. 14 branches and 38 Git tags were found; there is no published GitHub Release. The aggregate GitHub CI runs on final heads were **failure** because Docker Hub login/push failed without configured credentials, while Java Build & Test, Quality gates, Web UI Tests, Container Images build/smoke and push Security Scan passed. ShellCheck and GraalVM Native Build remain separate inherited failures; historical PR TruffleHog findings are unverified. The active ruleset requires review despite a single-collaborator configuration and had been bypassed administratively on past PRs: do not treat that as automatic future permission.

Some **canonical documents were semantically stale** despite passing validators: PROJECT-STATE called PR #50 open; ROADMAP still referred to an upcoming merge; RUNTIME-TOPOLOGY and README said the chains did not communicate; W53 headers remained ACTIVE; risk/question registries left the fixed symlink writer guard OPEN. The W53 demonstrator showed real distributed execution/adjudication but final epistemic disposition `UNRESOLVED`; Chain A health 503 due to PostgreSQL dependency, while Chain B was UP.

Known research baseline: 256-bit episodic features are XOR-folded to a 16-bit Tsetlin input (rank 16, kernel dimension 240), with observed collisions and a single-class training window; `fidelity=1.0000` was not meaningful discrimination. The retrieval evaluation was **18/29**, with 11 named failures. Current owner-reserved questions: fold A/B/C, persistent payload storage/recovery on the peer, Exp324's retained 2 ms threshold, and multiple infrastructure/license issues. Do not choose these silently.

**Last coding-agent prompt issued (execution result still unknown):** `M.A.T.R.I.X. — EVID-BASE & Evidence-to-Action Implementation Directive`. It instructed agents to (A) reconcile canonical documentation and deterministic consistency checks, (B) investigate/improve E2 retrieval with held-out testing, (C) build two-class E1 rule-learning evidence with original-vs-fold comparisons, (D) implement one real bounded, authorized, provenance-carrying local file artifact action with idempotence and crash recovery, (E) research E3 peer recovery without changing owner-reserved payload contract, and defer E4 optimization until end-to-end relevance. It authorized scoped Level 3 Git operations, with feature → develop → squash into main → synchronize main back to develop only under the allowed review/check controls. **Do not assume this directive has already succeeded.** The next message I provide will be the latest implementation report.

## What you must do next

1. Read the latest report and compare it with this handoff and source-of-truth files.
2. Use the GitHub connector/read-only API to verify current branches/SHAs, PRs, CI jobs, relevant production call paths, source diffs, tests, documents and experiment artifacts whenever available; do not silently reuse last month's GitHub facts.
3. Label findings **VERIFIED / AGENT-REPORTED / PROPOSED / BLOCKED / OWNER DECISION**. Explicitly distinguish local tests from CI, bounded benchmarks from production, and successful actions from proven conclusions.
4. Look for mistakes, missed negative controls, data leakage, misclassified evidence, unsafe action boundaries, broken recovery semantics, and contradictory documentation. Do not let inherited CI failures hide regressions or vice versa.
5. Evaluate progress using actual user-visible benefit, test correctness, epistemic accuracy, independent/hypothesis-controlled experiments, crash safety, compute economics and scientific extensibility.
6. Give a clear prioritized next-round architectural recommendation, open choices with their consequences, and, **if I ask for a prompt**, a complete executable English assignment for DeepSeek Harness / MiniMax M3.1Flash with exact acceptance criteria, appropriate Level 3 scope, protected decisions, resource/token discipline, Git workflow and A–J evidence-based final report.
7. Do not use GitHub mutation tools in our collaboration. If the connector or files are unavailable, state the limitation and ask only for the missing artifact that actually blocks analysis. Do not claim you can access every previous ChatGPT conversation without the attached export or this handoff.

Start by processing my next **agent implementation report**. Avoid asking me to repeat what is already documented here.


---

# APPENDIX — SOURCE-GROUNDED HISTORY AND PROJECT DECISIONS

# M.A.T.R.I.X. — Project History, Architecture, Decisions, and Next-Round Handoff

**Prepared:** 2026-11-14. **Purpose:** Portable, source-grounded handoff between ChatGPT chats. **Scope:** Uploaded ChatGPT export (19 conversations, created Sep–Nov 2026; latest exported project discussions 2026-11-13), plus the subsequent W53 final integration report and read-only GitHub findings from the conversation on 2026-11-14. **Status:** Research synthesis, **not** a substitute for re-verifying live GitHub state.

## 1. How this synthesis was built and what it is not

The ZIP contains `conversations.json` and 19 conversations. Six conversation threads directly concern the development of M.A.T.R.I.X.: `План развития AGI проекта`, `Branch · План развития AGI проекта`, `Branch · План развития AGI проекта (1)`, `Branch · План развития AGI проекта (2)`, `Запуск агентов`, and `Продолжение W53 hardening`. These threads have 183 selected-path user/assistant messages, but only 122 distinct messages after deduplicating copied branch history (39 distinct user messages and 83 distinct assistant messages). Other conversations about OpenCode prompts, AI-native SDLC, engineering telemetry and local inference provide methodological context. Unrelated personal conversations were excluded.

The archive predates the successful integration of W53 into `main`. The final W53 outcomes and current CI/document discrepancies below are from later messages and a read-only GitHub inspection on 2026-11-14. **A historical agent report is evidence that an agent reported something, not proof it actually happened**; direct remote GitHub facts are marked as such. The assistant did **not** execute a local test run or read every source file in the repository. An upcoming agent report, if supplied, supersedes this snapshot only after verification.

## 2. The owner's enduring mission (extracted from repeated, explicit instructions)

M.A.T.R.I.X. is an open research project aiming toward an **independent, continuously developing cognitive system**, not a wrapper around a general-purpose LLM and not merely another transformer inference stack. These are aspirations and research directions, **not established capabilities**. Major objectives:

- Build a learning and reasoning system that has meaningful internal representations of world states, concepts, relations, events, causal hypotheses, skills, and experiences; natural language is an interface and acquisition medium, not necessarily its ontological substrate.
- Develop a persistent memory with source provenance, truth conditions, contradictory and uncertain states, revisions, recoverability, and honest abstention. New observations must not erase prior evidence or adjudications.
- Learning should progress from **not knowing**, to exploring and trying, to successful applied understanding, and ideally to **teaching/transfer**. The owner's educational example: an explanation suitable for a primary-school learner may be refined with increasingly accurate causal and scientific detail as evidence grows. A previously accepted simplification is not immutable truth.
- Combine online learning, deliberate exploration, sleep/consolidation, reflection, error correction, and reuse of acquired procedures or tools. Expensive reasoning for novel problems should give way to cheaper, validated compiled procedures when the task becomes familiar.
- Support compositional cognitive modules (memory, planning, attention, motivation, goal-setting, prediction, explanation, perception, action, metacognition, and regulatory signals). Biological/hormonal analogies (cortisol, endorphins, brain regions) are inspiration, **not** a mandate to copy human anatomy or chemistry literally.
- Compare diverse algorithmic families, including MPDT, BIR/truth tables, HDC, Tsetlin, WiSARD, MCTS, probabilistic methods, numerical/neural methods, and unconventional ideas from other disciplines. No representation or method has permanent privileged status without workload evidence. Preserve promising historical work and explicitly refute false claims.
- Support both centralized and decentralized forms, peer cooperation, local/autonomous operation on different device classes, provenance-preserving exchange, eventual federation, and bounded self-improvement. Do not confuse authorized bounded internal tool creation with unrestricted code execution or self-replication.
- Seek high efficiency on real local resources. Java/Quarkus can provide orchestration and serving, while C/C++/Python/assembler/native/GPU may be considered **only if measured and justified**. An early desire to use C as the primary inferencing engine was a hypothesis, not an accepted universal requirement; REP-SUBSTRATE later found native FFM significantly slower on one measured workload.
- Build **usable software**: inspect, teach, query, disprove a false premise, revise a model, restart, recover memory, perform authorized actions, verify their effects. Evaluate real outcomes rather than count classes, files, tests, prompts, or research waves.
- Use verifiable multi-disciplinary research and competing hypotheses. Challenge common best practices where evidence warrants, but do not replace them with untested speculation. Compare against appropriate baselines and against LLM approaches only under fair, task-equivalent evaluation.
- Encourage open scientific collaboration through accurate README, GitHub Pages, reproducible evidence and publication. Never market experimental or dormant components as production features.

**Critical epistemic qualification:** User aspirations such as “never hallucinate, never forget, always tell the truth” are objectives for rigorous evidence handling and explicit uncertainty, **not guarantees** that a real system can honestly claim absolutely.

## 3. Working relationship and response preferences

- ChatGPT serves as **co-author, architect, analyst, technical critic, and author of precise plans/prompts** for external development agents. The owner explicitly corrected an earlier misunderstanding on 2026-11-13: **do not autonomously edit the GitHub repo or act as the development agent**. Read-only GitHub inspection is appropriate when requested or needed for analysis; implementation is delegated externally.
- Coding agents run in **DeepSeek Harness**, using **MiniMax M3.1Flash** on a token-constrained plan; earlier development used OpenCode. The owner may change tooling later. **Prompts/instructions for implementation agents must be in English**, highly concrete, directly executable, cost-aware, with acceptance criteria and exact evidence expectations. Conceptual discussion may be in Russian.
- The owner wants active, rigorous critique, no flattery, no invented certainty, no excessive rehashing of architecture, no broad questions already answered, and incremental but meaningful delivery. Cold-start agent prompts should use the canonical in-repo reading set instead of rescanning thousands of files every time.
- A new ChatGPT conversation should analyze the latest externally supplied agent report, **freshly inspect GitHub when access exists**, reconcile with canonical records, and then propose the best next action or an English development-agent prompt **when requested**. The user may attach this handoff file; the assistant must not claim access to the old ZIP in the new conversation unless the ZIP is re-uploaded.
- No repeated speculative infrastructure waves. Tests and documentation matter, but the next cycle must add **real actions, user benefit, observable science and competitive hypothesis testing**.

## 4. Historical engineering trajectory (condensed chronology)

This chronology is a synthesis of exported user-provided agent reports, prompts and follow-up discussions; validate definitive implementation status in live Git history and current code. **Wave numbering is historically overloaded and reused.** Do not assume W54/W55/W56 or higher IDs are available.

| Period / name | Reported achievement and why it mattered | Important limitation or follow-up |
|---|---|---|
| Earlier waves W1–W1500 / MIND / TRUE-W / T-0x | Large research code base, BIR/MPDT/HDC, memory, inference, federation, neural submodules, models and services | Much was historical, experimental, unserved or only partially wired; version tags do not prove a current production release |
| Oct 19 — initial repository audit | Identified gap between claimed architecture and actual production call paths; proposed measured hybrid-intelligence development instead of feature accumulation | Early assistant conclusions had to be corrected by later deeper code audits |
| Oct 26–30 — formal truth, RECON-W39 | Finite TLA+ gates and implementation mapping; mutation tests revealed real gaps; CRDT tombstone persistence, timestamp validation, MCTS constant reward signal were examined | A passing formal model is not proof of real implementation; production MCTS with constant reward is not an optimizer |
| Oct 31 — educational/epistemic design | Owner specified progressive, revisable scientific knowledge and learning stages ending in ability to teach; demands actual restart/persistence demo | Need source-aware representation and independent test criteria |
| Nov 1 — RECON-W40/W41 | Reported work towards local alpha with goal manager, learning loop, and actual app behavior | Difference between a micro-world exercise and broadly useful knowledge remained large |
| Nov 2 — W42 audit | Reconciled prior wave reports with actual tree and implementation; identified mismatches rather than trusting status | Repeated archaeology became expensive; stronger single-source-of-truth needed |
| Nov 2 — W43 epistemic foundation | Reported `Source`, `Claim`, `Evidence`, `Derivation`, `Case`, revisions; workbench, licensed corpus, security controls; real false-premise defects measured | Demonstration and scoped tests did not establish general understanding; some cases still failed |
| Nov 2–3 — W44 cognitive kernel | Cognitive cycle foundation, algorithm selection, memory, hypothesis/reflection, modular integration, recorded decisions | Scope and resource limitations required honest labels |
| Nov 3–4 — W45.0/W45.1 | Append-only observation/adjudication, crash-consistent cognitive requests, ownership fencing, durable trace; actual SIGKILL/restart harness | Destructive reload, writer exclusivity and cross-process semantics required further work |
| Nov 4 — W45.2 | Owner-approved boundaries: named canonical entry point **per serving application**, keep specialist cycles, separate Chain A/B state, fail-closed destructive reload, one writer per database | No permission to merge all cycles into one controller or share stores |
| Nov 4–5 — W46 consolidation | Canonical documentation, decisions, risks, capability status axes, test catalogue, session protocol, hardware/resource budgets; goal: stop repeating discovery | Some canonical documents were later found semantically inconsistent despite passing validators |
| Nov 5 — W47 | Retention, destructive reload, backups/restore drill, skills/harness, GPU verification, autonomy impulse consumer | Autonomy impulse consumer recorded bounded **internal** proposals, not uncontrolled external actions |
| Nov 6 — W48/W49 | CI repeatability, resource and GPU measurements, public README/Pages/scientific collaboration, known reliability repairs | Runner memory/native, ShellCheck, and publication credentials remained constraints |
| Nov 7 — W50 | Local offline evidence-grounded demonstrator with source acquisition, false-premise refusal, revision, restart persistence; `./scripts/run-local.sh`; real evaluation set | Original evaluation reportedly 12/29, with later W51–W53 evolution improving to 18/29; only bounded document domain, not universal reasoning |
| Nov 8 — W51 | Unified bounded cognitive runtime, honest cross-chain governed bridge, structured world-state memory, procedure/plugin lifecycle, method choice, performance comparisons | Two serving processes and independent stores remain; not every created skill is automatically safe or useful |
| Nov 9 — W52 and first W53 “procedure memory” | Typed canonical contracts and producers, second bridge direction, learned procedure saved and reused in later episode, epistemic qualification | W53 label was reused; distinguish **W53-PROCEDURE-MEMORY-STATUS.md** from the later W53 distributed cognition wave |
| Nov 9–13 — W53 distributed cognition | Chain A durable dispatch → authenticated Bridge v3 → Chain B durable inbox/real brain → signed response and report → Chain A verification, adjudication and trace; cancellation, idempotency, reconciliation, method reliability | Peer evidence not promoted automatically; peer payload recovery is still unresolved |
| Nov 13 — W53 release hardening | Six targeted regression classes mutation-tested, symlink writer guard, TruffleHog false positive localized, 12 doc claims corrected, isolated merge rehearsal; full `matrix-core` reported 9,522 tests / 23 skipped / 0 failed | Initial work was uncommitted, and main/develop had divergent history due to earlier squash |
| Nov 13 — REP-SUBSTRATE | Empirical representation/compute comparison; 256→16 XOR fold rank 16/kernel dimension 240; native FFM overhead; one-class window made fidelity=1.0 trivial | Fold A/B/C owner decision **OPEN**; first improve evaluation data diversity |
| Nov 14 — W53 final integration | Verified on GitHub: PR #50/#51/#52 merged, `main`/`develop` trees identical, release reports committed | Aggregate CI still red due to Docker Hub publishing, and current canonical prose still contains stale statements |
| Nov 14 — **next mandate issued, no execution report yet** | English implementation directive **EVID-BASE & Evidence-to-Action**: canonical reconciliation, E2 retrieval, E1 two-class learning evidence, real bounded local file action | Await external agent's next report; do not assume its tasks have been performed |

## 5. Actual project topology and fundamental constraints (last verified/recorded)

**Repository:** https://github.com/AlexanderNarbaev/agi (public); default branch `main`; integration branch `develop`.

**Two real serving chains:**

- **Chain A:** Quarkus / `matrix-core`, epistemic KnowledgeStore, CognitiveStore, `CognitiveKernel`, cognitive API/workbench/answering and `CognitiveCoordinator`.
- **Chain B:** JDK `HttpServer` / `matrix-api-gateway` + `matrix-brain-runtime`, real `TrueMindCycle`/`BirBrainCycle` when `MATRIX_MODE=production`; gateway default `stub` remains an operational pitfall. Chain B owns a separate durable inbox and mind state.
- **Bridge v3:** network-based authenticated versioned message passing; the two chains communicate, **but do not share store ownership or acquire direct access to each other's private database**. A peer result is a *secondary report to adjudicate*, not automatic truth.
- Other implemented but variably served/researched components include BIR TT/CLAUSESET/BDD, MPDT, HDC, Tsetlin, WiSARD, MCTS, neuromodulation, goal management, autonomous impulse proposals, vector/GPU approaches, plugins, federation and experiment frameworks. Verify per capability in the 4-axis registry, not by class existence.

**Constitutional constraints in repo:** `CONSTITUTION.md`, `AGENTS.md`, protected ethics and schema areas, frozen GitHub workflows, K_MAX=20, deterministic numerical and runtime decision paths, carefully seeded cognitive stochasticity, no unapproved LLM influence in decision path, immutable evidence where required, truthful confidence and uncertainty. The actual file and accepted successor ADR/owner decision supersede this summary if they differ.

**Epistemic principles:** preserve source/claim/evidence/provenance; separate observation from inference, hypothesis, report, disposition, and reason; unsupported is not supported; independent evidence must truly be independent; failed execution and incomplete recovery must remain visible; mutation and negative controls matter.

**Computational principle:** workload-dependent representation and backend; no unmeasured universal substrate. C/FFM/GPU not automatically superior. Optimize end-to-end effects, not just ns/op.

## 6. Last verified GitHub snapshot: 2026-11-14

Remote read-only inspection in the preceding chat found:

| Item | Observed |
|---|---|
| `main` HEAD | `1dc0153771fd9bf922771524562e91def52a7244` |
| `develop` HEAD | `343299b1ef90ffbea3518768561258862923a655` |
| Both tree SHAs | `da7edd36b3f7dee5907ac33203a456f16b0e8f29` |
| Compare `main...develop` | `develop` ahead 444 commits, behind 0, **0 changed files**; `main` ancestor of `develop` |
| PR #49 | squash W52/previous W53, `7709dc9e`, merged 2026-11-09 |
| PR #50 | squash W53 distributed cognition, `bcdd1a0a`, merged 2026-11-14 08:13 UTC |
| PR #51 | release record, `c4cee8ab`, merged 2026-11-14 |
| PR #52 | final registry updates, `1dc01537`, merged 2026-11-14 |
| Branch inventory | 14 branches: `main`, `develop`, `release/v1.0`, `safe-main`, 7 old `feature/mind-*`, `feature/w53-distributed-cognition`, 2 dependabot branches |
| Tag inventory | 38 tags: 22 checkpoint tags and 16 version tags, latest numbered `v18.0.0-mind` |
| GitHub Releases | none found |
| Open PRs / Issues | none found at that inspection |

**CI on these exact heads:** `main` run https://github.com/AlexanderNarbaev/agi/actions/runs/43580692387 and `develop` run https://github.com/AlexanderNarbaev/agi/actions/runs/43580705704 had aggregate `failure`. Within each, Java Build & Test, quality gates, Web UI tests, container **build/smoke**, and push Security Scan **succeeded**. Docker Hub **publish/login** jobs failed or were cancelled because credentials were absent/unavailable. On `main`, ShellCheck and GraalVM Native Build also failed (the latter historically OOM); PR-event TruffleHog may fail for nine **unverified historical** findings despite push-event success. Do not say “all CI green.” Never disable scans or fabricate secrets.

**Review governance:** active GitHub ruleset `Protected main` requires one code-owner approving review, but the repository was operated with a single collaborator/PR author; PR #50–52 had zero reviews and were merged using documented admin bypass. Ruleset has an admin bypass. This is a release-process governance issue, not evidence that source changes are wrong. Do not assume further bypass authorization in a future chat.

**Documentation contradictions detected by read-only audit:**

- `docs-v2/governance/PROJECT-STATE.md`: beginning calls PR #50 open while later sections say merged; snapshot refers to old head.
- `ROADMAP.md`: W53 marked released, but some paragraphs still await merge; W46 milestone language stale.
- `docs-v2/architecture/RUNTIME-TOPOLOGY.md`: claims the chains do not meet; should distinguish no shared stores from governed Bridge v3.
- `README.md` and likely `README.ru.md`: outdated two-chains-do-not-call text.
- `W53-STATUS.md`, `W53-AUDIT.md`: initial status headers still ACTIVE despite release and closeout.
- `RISK-REGISTER.md`, `OPEN-QUESTIONS.md`: symlink writer guard risk/question remains OPEN despite the W53 fix and test. Broader cross-host safety must stay independent.
- Strict document validators were green, revealing a gap in semantic cross-document consistency checks.

**Demonstration nuance:** `docs-v2/engineering/W53-DISTRIBUTED-DEMONSTRATION.json` records Chain B `UP`/200 in production mode, but Chain A health `DOWN`/503 due to reactive PostgreSQL connection refused at localhost:5433. The delegated task was executed and then adjudicated `UNRESOLVED`; **a completed remote execution does not equal a proven factual conclusion**. Total recorded time ≈245ms on that scenario.

## 7. REP-SUBSTRATE / EVID-BASE current questions and metrics

- Production `EpisodeFeatureExtractor`: 256-bit features; rule trainer: 16-bit; parity fold maps 256→16, rank 16, kernel dimension 240. One pair of distinct feature vectors collides among 24 distinct observed features in the reported real log. Current 20-episode training window had one class, with `fidelity=1.0000` (trivial, not generalization).
- Native FFM Hamming path reportedly 11.3× slower per call than `Long.bitCount`, and much worse batched; production native lookup was not active. Measure on relevant workload before choosing C/Java/GPU.
- ADR `docs-v2/engineering/ADR-REP-SUBSTRATE-01-representation-substrate.md` fold alternatives: **A** reject over-wide source (requires caller change), **B** project informative bits (moving feature set and width), **C** retain fold but explicitly label limited fidelity. Decision stays **OPEN** pending diverse independent evaluation.
- **E1**: build provenance-carrying two-class episode dataset, measure original vs folded representations and held-out learning performance; at least 30 distinct episodes is a diagnostic floor, not strong statistical proof.
- **E2**: `scripts/w51-epistemic-eval.py` baseline 18/29; eleven failures A-02, A-04, A-05, A-06, M-01, M-03, P-01, P-02, F-02, R-04, R-05. Measure gold candidate existence, epistemic status, recall@K, MRR, floor/rank/filter effects, answer errors, held-out results, latency; forbid gold-case hacks.
- **E3**: peer stores request digest, not full payload; after crash it may honestly close as failed, not resume true work; durable payload at rest, privacy, storage TTL, idempotency and action effects are **owner-level OPEN**.
- **E4**: neuron boundary optimization DEFER until end-to-end profiling shows it benefits actual tasks. Buffer reuse microbenchmark speed is not a product result.
- **Evidence-to-Action**: next authorized target is **one bounded real local artifact write** based on source-cited evidence, authorized output directory, path/symlink validation, atomic write, idempotence, reboot recovery, negative controls, inspectable trace. Not arbitrary shell, not unrestricted filesystem access, not global autonomy. This is an implementation goal from the **last prompt**, not a completed capability.
- **Exp324** threshold **2 ms remains unchanged**; it is machine load sensitive and not owner-authorized to change.

## 8. Hardware and execution budget (historical; re-probe before heavy tests)

User's own Nov 2 system information: Ubuntu 26.04.1 LTS on a MECHREVO JIAOLONG-series laptop/workstation, AMD Ryzen 9 9955HX (16 physical/32 logical cores), ~64GiB installed RAM, NVIDIA RTX 5070 Ti Laptop GPU (~12GiB VRAM), ~2TB disk across devices. Repository W46 hardware audit Nov 4 measured ~59.5GiB usable RAM, 38–40GiB available then, and substantial Docker/build cache. Toolchain Java 25/Gradle/Quarkus. No local GraalVM native-image then. Limits: **at most one full matrix-core test JVM at a time**, modest concurrent builds, account for memory and /tmp. W53 full matrix-core suite reportedly 1,300 classes, 9,522 tests, 0 failure, 23 skipped (Graal/GPU blocked, not passes), 24m22s. Re-run only when scope demands; preserve JUnit XML. Historical measurements are not promises about current availability.

## 9. Canonical reading set for any new implementation session

1. `CONSTITUTION.md`, `AGENTS.md` (normative).
2. `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` (session authorization, verification, handoff, resource discipline).
3. `docs-v2/governance/PROJECT-STATE.md`, `ROADMAP.md`, `DECISION-REGISTER.md`, `OPEN-QUESTIONS.md`, `RISK-REGISTER.md`, `DOCUMENT-STATUS-POLICY.md`.
4. `docs-v2/architecture/CAPABILITY-REGISTRY.yml`, `RUNTIME-TOPOLOGY.md`, `docs-v2/engineering/TEST-CATALOGUE.yml`.
5. Subject-specific ADRs and evidence, especially `W53-STATUS.md`, `W53-AUDIT.md`, `ADR-REP-SUBSTRATE-01-representation-substrate.md`, `docs-v2/proposals/PROPOSAL-EVID-BASE-2026-11-14.md`.
6. In-scope source, tests, reproducible scripts and CI logs. Historical wave reports (`docs-v2/waves/`, `docs-v2/research/`) should be consulted for targeted disputes, not all loaded into a new agent's context.

## 10. How the next ChatGPT session should proceed

1. Read this file (attached in the new chat) and the user's **latest external agent implementation report**. Do not assume that the proposed EVID-BASE work already happened.
2. Confirm the intended role: **architect/reviewer/prompt writer**, not direct implementer; do read-only GitHub research when requested or materially useful.
3. If GitHub is available, inspect current `main` and `develop` HEAD and tree SHAs, PRs, individual CI jobs (not only aggregate status), relevant source files, tests, governance registers and evidence artifacts.
4. Reconcile the latest report with both actual code and earlier owner decisions. Separate VERIFIED, REPORTED, PROPOSED, BLOCKED and OWNER-DECISION-REQUIRED.
5. Evaluate meaningful changes in correctness, user-visible actions, uncertainty handling, cost, reproducibility, and safety; seek negative controls and held-out datasets.
6. Identify true blockers versus inherited unrelated infra failures; avoid dead-end infrastructure expansion.
7. Produce a prioritized architectural judgement, open decisions with alternatives, and **when asked**, a ready-to-run English prompt for **DeepSeek Harness / MiniMax M3.1Flash** with bounded autonomy, test plan, resource budget, specific deliverables, Git procedure and final A–J evidence report.
8. Explicitly say what was not verified; do not claim to have full historical chat access unless the archive itself is attached and read in the new session. If a new agent report contradicts this handoff, investigate rather than repeating stale numbers.

## 11. Last externally issued instruction (not yet known to be executed)

The last assistant-produced developer-agent directive was titled **“M.A.T.R.I.X. — EVID-BASE & Evidence-to-Action Implementation Directive”** (2026-11-14). It explicitly granted scoped Level 3 Git operations, but reserved Exp324, fold A/B/C and peer durable payload questions. Workstream A: correct current-state canonical docs and deterministic consistency checks. Workstream B1: E2 retrieval against 18/29 and held-out evaluation. Workstream B2: E1 diverse two-class dataset and fold comparison. Workstream B3: one real safe file artifact action, evidence provenance, auth, symlink/path safety, idempotence, crash boundary, latency. E3 analysis only, E4 deferred. Integration: feature branch → `develop` → squash PR into `main` → merge `main` back into `develop`, only with appropriate checks and recorded approvals/exceptions. **Wait for the next agent report before marking anything complete.**

## 12. Source / provenance map

**Chat export:** `conversations.json` in the user-uploaded ZIP from 2026-11-13, especially the six named M.A.T.R.I.X. threads. Exact owner goals and context appear around Oct 19 (mission and English OpenCode directive), Oct 30 (demand for a runnable persistent learner and correcting formal verification claims), Oct 31 (progressive teaching/understanding), Nov 2 (physical machine; multidisciplinary cognition and non-literal biological inspiration), Nov 4 (W46 canonical consolidation), Nov 5–9 (W47–W53 prompts and reports), Nov 12–13 (representation research and W53 hardening), Nov 13 (explicit ChatGPT/external-agent role separation).

**Read-only GitHub verification performed 2026-11-14 in this conversation:** `https://github.com/AlexanderNarbaev/agi`, PRs #49–52, Git refs/tree comparison, `actions/runs/43580692387`, `actions/runs/43580705704`, current canonical docs and W53 demo. The report of W53 integration was supplied by the owner and then key GitHub facts were separately verified. No local repo mutation or code execution was performed by ChatGPT.

**Interpretation rules:** The owner's long-term ideas are authoritative as goals. The repository's current normative decisions are authoritative as engineering constraints. Historical implementation reports are provisional until confirmed. Facts shown by a bounded demonstration are valid only in that demonstration's scope. Never silently convert a plan into completed status.
