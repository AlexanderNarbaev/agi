# MODULES — пакет↔спека/дизайн/гипотеза

Полная таблица 69 пакетов `matrix-core/src/main/java/io/matrix/` категоризирована по SDD-COVERAGE-свипу 2026-08-26.

| Категория | Пакетов | Классов | Покрытие |
|---|---|---|---|
| SPEC/DESIGN-mapped | 22 | 187 | ~41% |
| Research-experimental | 10 | 69 | ~15% |
| Needs-spec | 15 | 92 | ~20% |
| Utility-infra | 22 | 102 | ~22% |

## Mapped (примеры)

| Пакет | ↔ Спека/Дизайн/Эксп |
|---|---|
| `bir/` | SPEC-002, DESIGN-14 (миграция), INV-1 |
| `tsetlin/`, `bir.producers.motone/` | DESIGN-09, H-010 accepted (EXP-010) |
| `evolution/` | DESIGN-04, H-002/H-003 refuted-toy (EXP-002/003) |
| `devloop/` | SPEC-000, DESIGN-15 |
| `ktopo/` | SPEC-003 (WIP — Ricci-анализ) |
| `federation/` | DESIGN-08 (ElspChannel Ed25519 + ML-DSA v2) |
| `lifecycle/` | DESIGN-07/12 (CauldronProtocol, FnlGate, ConsolidationCycle) |
| `actions/` | DESIGN-13/15 (PlanRunner Hoare + PlanPreprocessor) |
| `brain/` | DESIGN-02 (Viewpoint) |
| `budgeter/` | DESIGN-11 (ConjugateBudgeter DP) |
| `distill/` | SPEC-001 этап B, EXP-009B/C |

## Needs-spec (топ бэклога)

| Пакет | Зачем спека |
|---|---|
| `reasoning/BrcChain` | ядро верифицируемых решений — формальные свойства отсутствуют |
| `mediator/` | слои согласования (GoldenRatioAllocator, MetaGoalValidator) без контракта |
| `hades/` (Eleutheria, BurdenLiftingRitual, DerangementDetector) | критичные инварианты не документированы |
| `memory/` (HierarchicalMemory, SdmReader, SqliteMemoryBackend) | M0–M4 фактически |
| `rag/` | правила retrieval-а; гипотеза относится к этому |
| `compression/TruthTableMinimizer` | tooling-свойства (минимизация QBF-сохранение) |

Полная карта соответствий (пакет ↔ SPEC/DESIGN/HYPOTHESES/TLA+ ↔ тесты) — в [engineering/SDD-COVERAGE.md](../engineering/SDD-COVERAGE.md).
