# PHASES — Detailed Engineering Plan

**Status:** normative · **Version:** v1 · **Date:** 2026-09-07

> Companion to PARADIGM.md and REQUIREMENTS.md.
> Maps each requirement group to a concrete phase with explicit
> RUN-by-RUN steps, dependencies, acceptance criteria, and EXP
> evidence requirements.

---

## Phasing Principle

Each phase **closes a complete cognitive loop** (perception → decision
→ action → consolidation), not just a single module. This prevents
the "wrap another layer around last week's layer" drift.

A phase is **DONE** when:
1. All acceptance criteria are met
2. EXP evidence recorded in Verification Ledger
3. Required review gates (per Goal Guard) all PASS
4. WAL entry summarises the phase

---

## Phase α — Substrate Readiness (1-2 days)

**Goal**: tighten the substrate invariants, isolate any decision-path
leakage, and produce a clean separation between runtime and offline tools.

### Scope (from REQUIREMENTS)
- R0.1-R0.7: tighten global invariants
- R1.1-R1.6: BirUnit, BIR-forms, BRC-step
- PARADIGM §3: move Qwen-related code out of runtime decision path
- R0.7: every decision leaves x-matrix-trace

### RUN Plan (estimate 8-10 RUNs)

**RUN 141** — Qwen isolation
- Move `api/OnnxChatResource`, `bridge/Qwen*`, `bridge/OnnxRuntimeAdapter`, etc.
  into new package `tools/distill/`
- Keep as opt-in CLI / Gradle task; no REST endpoint
- Tests: 3 — verify Qwen import goes through tools, not decision path
- Evidence: grep confirms no LLM symbols in `consciousness/`, `brain/`,
  `mediator/`, `reasoning/`, `actions/`, `ethics/`

**RUN 142** — Distill CLI
- `DistillCli` — main class for offline distillation pipeline
- Read corpus → run Qwen → output boolean weights
- Tests: 4 — distill dry-run mode, output schema validation
- Evidence: 1 corpus distilled; weights written to JSON

**RUN 143** — BirUnit tightening
- Add property tests for K_MAX=20 enforcement
- Add tests for boundary cases (exactly K_MAX, K_MAX+1)
- Tests: 6 — K_MAX boundary, exception messages
- Evidence: 100% of BirUnit call sites verify K_MAX

**RUN 144** — x-matrix-trace rollout
- Add `MatrixTrace` interface + `ConsciousTrace` impl
- Wrap each decision-path entry (ConsciousLoop, BrainLoopService, Action)
  with `MatrixTrace.step(name, input, output)`
- Tests: 5 — trace entries for each path, immutability check
- Evidence: trace bytes count from EXP run

**RUN 145** — Hash-chain audit
- Add `HashChain` module + integration with MatrixTrace
- Each step hashes prev_hash + this_step
- Tests: 4 — chaining, tampering detection
- Evidence: tamper test rejected by verification

**RUN 146** — TLA+ BRC-Step
- Author `formal/tla/BRC-Step.tla` — state transitions on BRC
- Add TLC model-checked spec for K_MAX≤20 and no-LLM
- Tests: 1 (config check) — TLC runs without errors
- Evidence: TLC report

**RUN 147** — Determinism audit
- Grep decision-path for `Math.random`, `Instant.now`, `System.currentTimeMillis`,
  `ThreadLocalRandom`, `SecureRandom`
- Each finding: prove it is OFF path or replace
- Tests: 5 — each replaced reference
- Evidence: 0 hits in decision-path, EXP run reproducible

**RUN 148** — Phase α EXP
- Run end-to-end determinism test: same input → same output 100 times
- Capture trace, hash-chain, K_MAX, audit grep
- Tests: 1 — full Phase α EXP
- Evidence: report published in `research/reports/EXP-α-report.md`

**RUN 149** — Phase α docs
- Update PARADIGM/REQUIREMENTS/TRACEABILITY with Phase α status
- Update WAL + FINALSUMMARY
- Tests: 0 (documentation-only)
- Commit: phase α closure

### Acceptance Criteria for Phase α
- [ ] No LLM class in decision-path packages
- [ ] x-matrix-trace emitted for every decision
- [ ] Hash-chain verifies backward to genesis
- [ ] 100/100 same-input-same-output runs
- [ ] JaCoCo ≥82%, all tests pass
- [ ] TLA+ BRC-Step spec TLC-clean

---

## Phase β — Cognition Loop (5-7 days)

**Goal**: complete the perception → attention → deliberation → gate →
action loop end-to-end. Output: a CLI demo where MATRIX consumes text
input, returns a deterministic text response, all gates verified.

### Scope
- R3.* Perception (full)
- R5.* Consciousness (router, deliberation, gate as classes)
- R6.* Action (gate as class)
- R9.* Ethics 4-cascade (full integration)

### RUN Plan (estimate 25-35 RUNs, 5-7 per module)

#### β.1 — Perception (RUN 150-156)

**RUN 150** — TextEncoder
- `perception/TextEncoder`: text → boolean vector via BPE→hash→booleanize
- Tests: 6 — determinism, length, K_MAX compatibility
- Evidence: 100% deterministic across runs

**RUN 151** — SignalModule registry
- `signals/SignalModule` interface + default implementations
- text, audio-stub, video-stub
- Tests: 5 — register/lookup

**RUN 152** — MultimodalProxy
- `proxy/MultimodalProxy` — text ↔ binary, audio-stub ↔ binary
- Tests: 4 — round-trip
- Evidence: encoded vector 100% reproducible

**RUN 153** — PerceptionBus
- `perception/PerceptionBus` — orchestrator
- Tests: 4 — push/pull

**RUN 154** — Saliency
- `perception/SaliencyEngine` — bottom-up saliency from signal
- Tests: 3 — score by signal type

**RUN 155** — Perception EXP
- End-to-end text → bool → metrics
- Tests: 1 — full pipeline

**RUN 156** — Perception docs
- SPEC-004 update, design notes

#### β.2 — Attention & Deliberation (RUN 157-165)

**RUN 157** — Impulse model
- `consciousness/Impulse` — top-down motivation (curiosity, integrity,
  goal-driven)
- Tests: 4 — priority, determinism

**RUN 158** — AttentionRouter
- `consciousness/AttentionRouter` — top-down × bottom-up merge
- Tests: 5 — sorted by mergedScore, deterministic
- Evidence: H-045 integration test

**RUN 159** — DeliberationEngine
- `consciousness/DeliberationEngine` — BRC + MCTS/LATS orchestrator
- Tests: 6 — budget, deterministic

**RUN 160** — PredictionModel
- `consciousness/PredictionModel` — predict own output, score error
- Tests: 4

**RUN 161** — ArousalDynamics integration
- Update arousal based on prediction-error
- Tests: 3

**RUN 162** — BrainLoopService full wiring
- Wire perception → attention → deliberation
- Tests: 4 — full loop, end-to-end determinism

**RUN 163** — BrainLoop EXP
- Run loop 100 times with same input, same output
- Tests: 1 — full integration EXP

**RUN 164** — Attention/Decision docs
- Update SPEC-006 status

**RUN 165** — Phase β.2 acceptance checkpoint

#### β.3 — Action Gate (RUN 166-174)

**RUN 166** — ActionGate cascade
- `consciousness/ActionGate`: 4-cascade explicit
- Tests: 7 — each gate independently
- Evidence: H-038 acceptance test

**RUN 167** — ConsciousTrace
- Append-only trace via MatrixTrace
- Tests: 4

**RUN 168** — PlanRunner integration
- PlanRunner receives trace + gate verdict
- Tests: 5

**RUN 169** — AC-3 PlanPreprocessor end-to-end
- Full AC-3 preprocess test (positive + negative)
- Tests: 5

**RUN 170** — LieDetector integration
- Wire LieDetector into ActionGate
- Tests: 4

**RUN 171** — FROZEN FNL Guardian
- Wire FROZENFNLGuardian into ActionGate
- Tests: 5

**RUN 172** — OutputSafetyFilter
- Add output-side filter
- Tests: 4

**RUN 173** — Action Arena end-to-end
- Full action loop: gate → runner → execute
- Tests: 5

**RUN 174** — Phase β EXP + docs

### Acceptance Criteria for Phase β
- [ ] CLI demo: `echo "question" | matrix-loop` returns deterministic
      answer through full pipeline
- [ ] All gates (Adversarial, Ethical, Structural, Lie, FROZEN-FNL)
      exercised end-to-end
- [ ] ConsciousTrace captures every step
- [ ] 100/100 same-input-same-output
- [ ] Predictable response time (<100ms per cycle on CPU)

---

## Phase γ — Memory & Learning (5-7 days)

**Goal**: M0/M1/M2 hierarchy fully wired, sleep cycle (TR/REM)
consolidates between sessions, offline distillation delivers first
boolean weights from a real corpus.

### Scope
- R4.* Memory hierarchy (full)
- R10.* Lifecycle (TR/REM consolidation)
- R2.2 Distillation via Qwen (offline, tools/distill/)
- R11.* Federation digests (minimal)

### RUN Plan (estimate 25-35 RUNs)

#### γ.1 — Memory Backends (RUN 175-182)
- SQLite backend hardening
- Redis backend (already exists, harden)
- PersistentHierarchicalMemory tests
- HierarchicalMemory eviction policy tests
- SdmReader integration tests

#### γ.2 — Consolidation Cycle (RUN 183-191)
- TR consolidation M2→M1
- REM consolidation M1→M0
- ConsolidationCycle scheduler
- FreezeRecoveryManager
- Snapshot+rollback
- Phase γ.2 EXP

#### γ.3 — Offline Distillation (RUN 192-200)
- DistillCli corpus reader
- Qwen inference batch in tools/distill/
- Booleanization pipeline
- Weight serializer
- Sample distillation EXP

#### γ.4 — Federation (RUN 201-208)
- Decentralized digests
- PoA consensus integration
- FederatedMesh integration tests
- Phase γ EXP

### Acceptance Criteria for Phase γ
- [ ] Memory hierarchy: M0/M1/M2 with persistence
- [ ] TR/REM consolidation cycle verified end-to-end
- [ ] At least one real corpus distilled through Qwen → boolean weights
- [ ] Federation digests hand-shake test
- [ ] All persistence paths survive process restart

---

## Phase δ — Pilots & Verification (3-5 days)

**Goal**: Two pilots (GridWorld, Proactive chatbot) and full TLA+
verification of BRC-step, FrozenEthicalFNL, MCTS-visit.

### Scope
- R12.1 Pilot #1 GridWorld
- R12.2 Pilot #2 Proactive chatbot
- TLA+ verification (formal)
- EXP reports closure

### RUN Plan (estimate 20-30 RUNs)

#### δ.1 — TLA+ Verification (RUN 209-216)
- BRC-Step.tla already from Phase α; harden
- FrozenEthicalFNL.tla — verify 4 prohibitions mathematically
- MCTS-Visit.tla — convergence
- Phase δ.1 EXP

#### δ.2 — Pilot #1 GridWorld (RUN 217-224)
- Simulator (4-neuron agent, food, hazards, day/night)
- GA with elitism
- Jupyter notebook
- Video of generations 0/50/200
- Acceptance: survival >80% by generation 200

#### δ.3 — Pilot #2 Proactive Chatbot (RUN 225-233)
- Telegram bot skeleton
- Text ↔ bool via BrainLoopService (NOT Qwen)
- ProactiveEthicalScanner init
- Fact learning via mutation in sleep cycle

#### δ.4 — Closure (RUN 234-238)
- EXP close-out cards
- White-paper draft (community)
- v2.0 release notes

### Acceptance Criteria for Phase δ
- [ ] Pilot #1: survival >80% by generation 200
- [ ] Pilot #2: 100% block on Three-Prohibition inputs
- [ ] TLA+: BRC-Step TLC-clean, FrozenEthicalFNL TLC-clean,
      MCTS-Visit TLC-clean
- [ ] v2.0 release notes published

---

## Dependencies Between Phases

- Phase α must complete before β begins (substrate must be locked).
- Phase β must complete before γ begins (loop must exist before
  memory integrated).
- Phase γ must complete before δ begins (pilots need real data).
- Each phase ends with WAL entry + EXP report.

## Risk Register

| Risk | Likelihood | Mitigation |
|---|---|---|
| Decision-path still calls something hidden | medium | grep + instrumentation |
| TLA+ spec doesn't match code | medium | code-generate spec, or vice versa |
| Pilot #1 simulation unrealistic | low | start with 4-neuron agent |
| Distillation pipeline too slow | medium | batch, offline caching |
| Federation handshake unstable | high | start with digests only |

## Update Protocol

- Run finishes → WAL entry with RUN number + summary.
- Phase finishes → update TRACEABILITY.md, FINALSUMMARY.md.
- Phase blocked → risk register updated.
- Major deviation → RFC (per AGENTS.md).
