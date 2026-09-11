# TRACEABILITY — SPEC ↔ DESIGN ↔ код ↔ тесты ↔ EXP

**Статус:** normative · **Версия:** v1 · **Дата:** 2026-09-07

> Эта таблица — карта соответствия между заявленным видением
> (SPEC) и фактической реализацией (классы, тесты).
> Главное назначение — найти гэпы: где видение есть, а кода нет.

---

## Условные обозначения

- ✅ = реализовано и покрыто тестами (≥80%)
- 🟡 = частично реализовано, есть гэпы
- ❌ = не реализовано
- ⏸ = отложено (вне текущей фазы)

---

## T0 — Глобальные инварианты

| SPEC / CONSTITUTION | Реализация | Статус | Тесты | EXP |
|---|---|---|---|---|
| CONSTITUTION I (детерминизм) | весь chain runner, без RNG в decision-path | ✅ | Yes | H-001..006 |
| CONSTITUTION I (no LLM in decision) | Планируется: вынести Qwen в tools/distill/ | 🟡 | - | - |
| CONSTITUTION II (K_MAX=20) | TruthTable.java | ✅ | KMAX tests | - |
| CONSTITUTION III (FROZEN zones) | ethics/frozen/, CONSTITUTION.md guard | ✅ | Yes | TLA+ |
| CONSTITUTION IV (4 запрета) | FROZENFNLGuardian + 4 каскад | ✅ | Yes | TLA+ EthicalFNL |
| CONSTITUTION V (coverage ≥82%) | JaCoCo gate | ✅ | gate enforced | - |
| CONSTITUTION VI (no false claims) | policy | ✅ | manual | - |
| CONSTITUTION VII (stack) | STANDARDS-MATRIX.md | ✅ | manual | - |
| CONSTITUTION VIII (every decision auditable) | x-matrix-trace | 🟡 | partial | - |

## T1 — Substrate

| SPEC | DESIGN | Классы | Статус | Тесты | EXP |
|---|---|---|---|---|---|
| SPEC-001 weight conversion | DESIGN-01..02 | `bir/BirCompiler`, `bir/WeightConverter`, `bir/Scaler` | ✅ | Yes | H-006 |
| SPEC-001 float→bool | DESIGN-02 | `bir/ThresholdExtractor` | ✅ | Yes | - |
| SPEC-001 K_MAX=20 | DESIGN-01 | `bir/TruthTable` | ✅ | Yes | H-007 |
| SPEC-002 bir forms | DESIGN-03 | `bir/TtForm`, `bir/ClauseSetForm`, `bir/BddForm` | ✅ | Yes | - |
| SPEC-002 BRC-step | DESIGN-03 | `reasoning/BrcChain`, `BrcState`, `BrcStep` | ✅ | Yes | TLA+ BRC-Step |
| SPEC-002-quantum-bir-mps | DESIGN-03-quantum | (drafts) | ❌ | - | - |
| SPEC-003 knowledge topology | DESIGN-09 | `knowledge/*` | 🟡 | partial | - |

## T2 — Knowledge / Noosphere

| SPEC | DESIGN | Классы | Статус | Тесты | EXP |
|---|---|---|---|---|---|
| L4 — Mediator | DESIGN-08 | `mediator/InstanceMediator` | ✅ | Yes | - |
| L5 — DNA | DESIGN-14 | `bridge/*` | 🟡 | partial | - |
| L6 — Memory hierarchy | DESIGN-05 | `memory/HierarchicalMemory` | 🟡 | partial | - |
| Noosphere | DESIGN-09 | `noosphere/Index`, `Registry`, `Credits` | ✅ | Yes | - |

## T3 — Perception

| SPEC | DESIGN | Классы | Статус | Тесты | EXP |
|---|---|---|---|---|---|
| SPEC-004 perception | DESIGN-16 | `perception/FeedbackPerception` | ❌ | - | - |
| SPEC-004 signal modules | DESIGN-06 | `signals/SignalModule` | ❌ | - | - |
| SPEC-004 multimodal proxy | DESIGN-06 | `proxy/*` | 🟡 | partial | H-039 |

## T4 — Memory

| SPEC | DESIGN | Классы | Статус | Тесты | EXP |
|---|---|---|---|---|---|
| SPEC-011 hierarchy | DESIGN-05 | `memory/HierarchicalMemory`, `MemoryHierarchy` | ✅ | Yes | - |
| SPEC-011 sqlite backend | DESIGN-05 | `SqliteMemoryBackend` | ✅ | Yes | - |
| SPEC-011 SDM reader | DESIGN-05 | `SdmReader` | ✅ | Yes | - |
| SPEC-011 persistent | DESIGN-05 | `PersistentHierarchicalMemory` | ✅ | Yes | - |
| Sleep cycle (TR/REM) | DESIGN-19 | `sleep/*`, TR/REM | 🟡 | partial | H-046 |

## T5 — Consciousness

| SPEC | DESIGN | Классы | Статус | Тесты | EXP |
|---|---|---|---|---|---|
| SPEC-006 deliberation | DESIGN-18 | `consciousness/ConsciousnessLoop` | ✅ | Yes | - |
| SPEC-006 attention router | DESIGN-18 | (in ConsciousLoop) | 🟡 | partial | H-045 |
| SPEC-006 deliberation engine | DESIGN-18 | `consciousness/DeliberationEngine` | 🟡 | partial | - |
| SPEC-006 action gate | DESIGN-18 | `consciousness/ActionGate` | 🟡 | partial | - |
| DESIGN-18 prediction-error | DESIGN-18 | (в ConsciousLoop) | 🟡 | partial | H-046 |
| BrainLoopService | DESIGN-18 | `consciousness/BrainLoopService` | ✅ | Yes | H-043,044 |

## T6 — Action

| SPEC | DESIGN | Классы | Статус | Тесты | EXP |
|---|---|---|---|---|---|
| SPEC-005 action | DESIGN-17 | `actions/PlanRunner` | ✅ | Yes | - |
| SPEC-005 action arena | DESIGN-17 | `actions/ActionArena` | ✅ | Yes | - |
| DESIGN-15 AC-3 preprocess | DESIGN-15 | `actions/PlanPreprocessor` | ✅ | Yes | - |
| DESIGN-17 plan runner | DESIGN-17 | `actions/PlanRunner` | ✅ | Yes | - |
| SPEC-005 proactive initiative | DESIGN-17 | `actions/ProactiveEthicalScanner` | ✅ | Yes | - |

## T7 — Reasoning

| SPEC | DESIGN | Классы | Статус | Тесты | EXP |
|---|---|---|---|---|---|
| SPEC-008 BRC | DESIGN-03 | `reasoning/BrcChain` | ✅ | Yes | TLA+ BRC-Step |
| SPEC-008 MCTS | DESIGN-08 | `mcts/MctsTree` | ✅ | Yes | - |
| SPEC-008 LATS | DESIGN-08 | `mcts/LatsNode` | ✅ | Yes | - |
| Hansel-Chains | algorithms/ | `algorithms/Hansel-Chains` | ✅ | Yes | - |

## T8 — Mediator

| SPEC | DESIGN | Классы | Статус | Тесты | EXP |
|---|---|---|---|---|---|
| SPEC-009 mediator | DESIGN-09 | `mediator/InstanceMediator` | ✅ | Yes | - |
| SPEC-009 driver state | DESIGN-09 | `mediator/DriverState`, `Goal`, `Task` | ✅ | Yes | - |
| SPEC-009 allocator | DESIGN-09 | `mediator/GoldenRatioAllocator` | ✅ | Yes | - |
| SPEC-009 validator | DESIGN-09 | `mediator/MetaGoalValidator` | ✅ | Yes | - |
| SPEC-009 hierarchy | DESIGN-09 | `mediator/hierarchy/*`, `scheduler/*` | ✅ | Yes | - |

## T9 — Ethics

| SPEC | DESIGN | Классы | Статус | Тесты | EXP |
|---|---|---|---|---|---|
| SPEC-006 ethics 4-cascade | DESIGN-03 | `ethics/AdversarialInputFilter` | ✅ | Yes | - |
| SPEC-006 ethical filter | DESIGN-03 | `ethics/EthicalFilter` | ✅ | Yes | - |
| SPEC-006 safety guard | DESIGN-03 | `ethics/StructuralSafetyGuard` | ✅ | Yes | - |
| SPEC-006 lie detector | DESIGN-03 | `ethics/LieDetector` | ✅ | Yes | - |
| FROZEN FNL | FROZEN-FNL.md | `ethics/frozen/FROZENFNLGuardian` | ✅ | Yes | TLA+ FrozenEthicalFNL |
| Output safety | DESIGN-19 | `ethics/OutputSafetyFilter` | ✅ | Yes | H-051 |
| Freeze recovery | lifecycle | `ethics/FreezeRecoveryManager` | ✅ | Yes | - |
| Periodic proactive scanner | SPEC-005 | `ethics/PeriodicProactiveScanner` | ✅ | Yes | - |
| Proactive ethical scanner | SPEC-005 | `ethics/ProactiveEthicalScanner` | ✅ | Yes | - |
| FROZEN GDPREscalator | CONSTITUTION IV | `ethics/FROZENGDPREscalator` | ✅ | Yes | - |

## T10 — Lifecycle

| SPEC | DESIGN | Классы | Статус | Тесты | EXP |
|---|---|---|---|---|---|
| DESIGN-12 taskcell | DESIGN-12 | `lifecycle/TaskCell` | 🟡 | partial | - |
| Consolidation cycle | DESIGN-19 | `lifecycle/ConsolidationCycle` | 🟡 | partial | - |
| Snapshot | snapshot/ | `snapshot/*` | ✅ | Yes | - |

## T11 — Federation

| SPEC | DESIGN | Классы | Статус | Тесты | EXP |
|---|---|---|---|---|---|
| SPEC-009 decentralized digests | DESIGN-09 | `federated/*` | 🟡 | partial | - |
| Federated Mesh | algorithms/ | `algorithms/FederatedMesh` | ✅ | Yes | - |
| PoA consensus | consensus/ | `consensus/*` | ✅ | Yes | - |

## T12 — Pilots

| SPEC | Статус | Тесты | EXP |
|---|---|---|---|
| L13 §2 Pilot #1 GridWorld | ❌ | - | - |
| L13 §3 Pilot #2 Proactive chatbot | 🟡 scaffolding | partial | - |
| L13 Pilot #3 Smart home | ❌ | - | - |
| L13 Pilot #4 Robotic arm | ❌ | - | - |
| L13 Pilot #5 Cauldron | ❌ | - | - |
| L13 Pilot #6 HADES | ❌ | - | - |
| L13 Pilot #7 Noosphere | ❌ | - | - |

## T13 — Operations

| SPEC | Статус | Тесты | EXP |
|---|---|---|---|
| Compose деплой (Prometheus, Jaeger, Grafana, Kafka, Postgres) | 🟡 | - | - |
| Chaos Mesh drills | ❌ | - | - |
| SLO/SLI dashboard | ❌ | - | - |
| Alertmanager → Slack | ❌ | - | - |

## T14 — Community

| SPEC | Статус | Тесты | EXP |
|---|---|---|---|
| CONTRIBUTING.md / CODE_OF_CONDUCT / SECURITY.md | ✅ | - | - |
| GitHub Discussions, Matrix, Discord | ⏸ | - | - |
| Weblate | ⏸ | - | - |
| WASM Playground | ❌ | - | - |
| Jupyter notebooks | ❌ | - | - |
| Video course | ❌ | - | - |

---

## Сводные гэпы (топ для следующей волны)

| № | Что | Файл/Класс | Что нужно |
|---|---|---|---|
| G1 | Perception полностью | `perception/FeedbackPerception` → новая подсистема | Текст→bool, signal bus |
| G2 | AC-3 plan preprocess end-to-end | `actions/PlanPreprocessor` | Unit-тесты на пустой домен |
| G3 | Consolidation cycle (TR/REM) | `lifecycle/ConsolidationCycle` | TR: M2→M1; REM: M1→M0 |
| G4 | AttentionRouter | (в ConsciousLoop) | Извлечь в отдельный класс, тесты |
| G5 | ActionGate | (новый) | 4-каскад явный класс + state |
| G6 | ConsciousTrace | (append-only журнал) | Реализация |
| G7 | PredictionModel | (новый) | Сон-консолидация |
| G8 | Qwen → tools/distill/ | весь `api/OnnxChat*`, `bridge/Qwen*`, `bridge/Onnx*` | Перенести в `tools/distill/` |
| G9 | Pilot #1 GridWorld | новый модуль | simulator + agent + GA |
| G10 | Pilot #2 Proactive chatbot | telegram bot | text via BRC, без Qwen |
| G11 | TLA+ BRC-Step | `formal/tla/BRC-Step.tla` | Проверить state transitions |
| G12 | TLA+ FrozenEthicalFNL | `algorithms/FROZEN-EthicalFNL.tla` | Проверить 4 запрета |

---

## Связь с EXP/HYPOTHESES

См. `research/HYPOTHESES.md` и `research/HYPOTHESES-NEW.md` —
содержит 50+ эмпирических гипотез по каждой подсистеме.

См. `research/reports/EXP-*-report.md` — карточки принятых/опровергнутых
гипотез с численными результатами.
