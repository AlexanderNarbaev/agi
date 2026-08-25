# SDD Coverage Map — код ↔ спецификации

**Статус: living** · Обновлено 2026-08-25 · Принцип SpecDrivenDevelopment: каждому значимому классу соответствует раздел спеки/дизайна; каждой спеке — реализация или честный BLOCKED-EXT в [PLAN](PLAN-FULL-IMPLEMENTATION.md).

## Спеки → реализация

| Спека | Классы/пакеты | Тесты |
|---|---|---|
| SPEC-000 Developmental Loop | `io.matrix.devloop.*` (12 классов) | DevLoopTest, DevLoopPropertiesTest |
| SPEC-001 Этап B | `io.matrix.distill.{Distiller,OnnxActivationTeacher}` | DistillerTest, OnnxActivationTeacherTest |
| SPEC-002 BIR/продюсеры | `io.matrix.bir.*`, `io.matrix.tsetlin.*`, `bir.producers.monotone.*` | bir+ tsetlin + monotone тесты |
| SPEC-002 FR-D3 | `docs/spec/quantum/BIR-to-MPS.md` (spec-only) | — |
| SPEC-003 Топология | `io.matrix.ktopo.*` | KtopoTest, KtopoPropertiesTest |
| INV-1 (Критерий A) | `bir.Inv1SourceGuardTest` (source-scan страж) | сам тест |

## Дизайны → реализация

| Дизайн | Классы |
|---|---|
| DESIGN-02 Viewpoint | `brain.Viewpoint` |
| DESIGN-05 M4 | `noosphere.Crdt/GrowOnlySet` |
| DESIGN-06 Сигналы | `signals.{TextSignalModule,AudioSignalModule,ImageSignalModule,SignalModuleRegistry}` + прототип prototype-java |
| DESIGN-07 ЖЦ | `lifecycle.{CauldronProtocol,TaskCell,ConsolidationCycle}` + operator CRD×2 |
| DESIGN-08 ELSP | `federation.{ElspChannel(Ed25519),ElspChannelMlDsa(v2 PQ),ArtifactSigner,Anonymizer}` |
| DESIGN-11 Бюджетер | `budgeter.ConjugateBudgeter` + `cauldron.LevinSchedule` |
| DESIGN-12 TaskCell/FNL | `lifecycle.FnlGate` (+TaskCell) |
| DESIGN-13 Реестр действий | `actions.{ActionRegistry,PlanRunner,VersionedContract,PlanPreprocessor}` |
| DESIGN-14 Миграция | аннекс call-site-audit + волны A-1/A-2/A-3c |
| DESIGN-15 AC-3 | `agent.planning.Ac3Solver` + `actions.PlanPreprocessor` |

## Пробелы покрытия (честно)
- `cli/`, `benchmark/` — без тестов (утилитарные).
- Часть ~70 пакетов вне этого маппинга — утилиты/эксперименты прошлых сессий; полная инвентаризация — задача «SDD full sweep» при появлении ресурсов.
