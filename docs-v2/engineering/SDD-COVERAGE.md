# SDD-COVERAGE

Сводка SDD-свипа (полная таблица пакет↔спека/дизайн/гипотеза → в [architecture/MODULES.md](../architecture/MODULES.md)).

## Счётчики

| Категория | Пакетов | Классов | % кодовой площади |
|---|---|---|---|
| SPEC/DESIGN-mapped | 22 | 187 | ~41% |
| Research-experimental | 10 | 69 | ~15% |
| Needs-spec | 15 | 92 | ~20% |
| Utility-infra | 22 | 102 | ~22% |

Всего: **69 пакетов**, **450 классов** (+ 5 корневых демо-файлов).

## Новые связи, которых не было в старой карте

- `consensus/` ↔ `formal/Consensus.tla` (шапка TLA+ ссылается на пакет)
- `audit/` ↔ `formal/HashChain.tla`
- `neuron/` ↔ `formal/MPDTNeuron.tla`
- `ethics/` ↔ `formal/FrozenEthicalFNL.tla` и `formal/BotEthicsPipeline.tla`
- `verification/` ↔ EXP-017
- `guardrail/` ↔ EXP-006
- `pilot/` ↔ EXP-005 / H-005

## Needs-spec (доменная логика без формальной спецификации)

| Пакет | Риск |
|---|---|
| `reasoning/` (BrcChain) | ядро верифицируемых решений без TLA+ |
| `mediator/` (InstanceMediator, GoldenRatioAllocator, MetaGoalValidator) | слои согласования без контракта |
| `hades/` (Eleutheria, BurdenLiftingRitual, DerangementDetector) | освобождение/бдительность без формальной свойств |
| `memory/` (HierarchicalMemory, SdmReader, SqliteMemoryBackend) | M0–M4 фактически реализованы, спеки нет |
| `rag/` (BooleanRag, HybridBooleanRag, RrfFusion, QueryExpander, SkeletonTreeParser) | retrieval-контракт не задан |

Эти пакеты помечены в [architecture/MODULES.md](../architecture/MODULES.md) как `needs-spec`.

## Префиксы-дубликаты

- `explain/` ↔ `explainability/` (разные имена, перекрывающееся назначение)
- `knowledge/` ↔ `ktopo/` (топология vs индекс)
- `federated/` ↔ `federation/` (mesh vs ELSP-crypto)
