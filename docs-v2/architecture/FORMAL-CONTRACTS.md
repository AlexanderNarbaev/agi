# FORMAL-CONTRACTS

Каталог формальных контрактов MATRIX. **RECON-W39** — этот документ теперь
указывает на реестр, а не дублирует его.

## Авторитетный источник

**`formal/spec-implementation-map.yml`** — реестр соответствия спецификаций и
реализации. Для каждой из девяти TLA+-спецификаций он содержит: Java-исходники,
production-вызывающих, unit- и integration-тесты, свойства, допущения,
абстракции, ограничения и **явный статус соответствия** из закрытого словаря
(`SPECIFICATION_ONLY`, `IMPLEMENTATION_ONLY`, `ABSTRACT_MODEL`, `MODEL_CHECKED`,
`JAVA_UNIT_VERIFIED`, `TRACE_CONFORMANT`, `INTEGRATION_VERIFIED`,
`PRODUCTION_PATH_VERIFIED`, `BLOCKED`, `UNVERIFIED`).

Реестр проверяется автоматически:
`matrix-quality/src/test/java/io/matrix/quality/SpecImplementationMapTest.java`.
Проверка отказывает в статусе `PRODUCTION_PATH_VERIFIED`, если production-вызывающий
не назван, — это и есть механизм, не дающий реестру преувеличивать.

Документ `docs-v2/engineering/TLA-STATUS.md` описывает, что именно проверяет
формальный гейт и что он **не** проверяет.

## Матрица «спецификация ↔ реализация ↔ контракт»

| TLA+ | Класс(ы) | Контракт | Статус (по реестру) |
|---|---|---|---|
| `BotEthicsPipeline` | `api/BotEthicsPipeline`, `api/BotEthicsResource`, `ethics/FROZENFNLGuardian`, `ethics/frozen/FrozenEthicalFNL` | вердикт FNL ведёт к tombstone и записи аудита | MODEL_CHECKED, PRODUCTION_PATH_VERIFIED; расхождение: `FnlTriggers` — точное вхождение, а Java сопоставляет фразы |
| `BrcStep` | `reasoning/BrcStep`, `reasoning/BrcChain`, `reasoning/BrcState` | атомарный булев шаг; композиция цепей | MODEL_CHECKED, PRODUCTION_PATH_VERIFIED для `evaluate`; `compose` — **без production-вызывающего**; три из четырёх свойств относятся к `compose` |
| `ConjugateBudgeterDP` | `budgeter/ConjugateBudgeter` | 0/1-рюкзак, witness, теневая цена | MODEL_CHECKED, **TRACE_CONFORMANT**, PRODUCTION_PATH_VERIFIED |
| `Consensus` | `consensus/ConsensusEngine`, `consensus/WeightedVoting` | взвешенное голосование | MODEL_CHECKED; **production-вызывающего нет** — `ConsensusEngine` создаётся только в тестах; порог в модели — счётчик голосов, в Java — отношение весов |
| `FrozenEthicalFNL` | `ethics/frozen/{FrozenEthicalFNL,FrozenAxiomNeuron,TextFeatureExtractor,TruthTableUtil}` | четыре запрета (Конституция IV) | MODEL_CHECKED, PRODUCTION_PATH_VERIFIED (FROZEN-зона); отношение триггеров в cfg — не то, что в Java |
| `HashChain` | `audit/HashChain`, `audit/HashLink`, `audit/FrozenFNLHashChain` | цепная целостность audit-trail | MODEL_CHECKED, PRODUCTION_PATH_VERIFIED |
| `MemoryM4Causal` | `noosphere/EpochAwareCausalSet`, `noosphere/GrowOnlySet`, `noosphere/Crdt` | монотонность, необратимость tombstone, причинное слияние, неизменяемость frozen | MODEL_CHECKED; **production-вызывающего нет**; `EpochAwareCausalSet` добавлен в RECON-W39 |
| `MctsLatsVisit` | `mcts/{MctsTree,MctsNode,LatsNode,LatsReflector}`, `federation/liquid/MCTSPlanner` | счётчики посещений, ацикличность дерева | MODEL_CHECKED, PRODUCTION_PATH_VERIFIED (`io.matrix.mcts`); модель описывает `MCTSPlanner`, у которого вызывающего нет |
| `MPDTNeuron` | `neuron/{NeuronLayer,TruthTable,DecisionTree}`, `bir/{TtForm,BooleanRuntime,DecisionTreeAdapter}` | выход — чистая функция от (k, таблица, вход) | MODEL_CHECKED, PRODUCTION_PATH_VERIFIED; свойства k привязаны к `K_MAX = 2` в cfg, тогда как в Java предел 20 |

`CellLifecycle` — спецификации по-прежнему нет (отложено, формализация).

## Матрица «класс ↔ инвариант»

| Класс | Инвариант | Метод проверки |
|---|---|---|
| `bir/BooleanRuntime` | детерминизм eval | unit test + INV-1 source-scan |
| `ethics/frozen/FROZENFNLGuardian` | запреты независимы от входа | TLA+ `FrozenEthicalFNL` + EXP-006 |
| `audit/HashChain` | append-only + tamper-evident | TLA+ `HashChain` + `HashChainIntegrityTest` |
| `reasoning/BrcChain` | пред-/пост-условия шагов | TLA+ `BrcStep` (`evaluate`); `compose` — только jqwik-property |
| `lifecycle/FnlGate` | монотонные переходы без отката | EXP-009 + manual |
| `media/PatternLockedAnchor` | системный якорь не двигаем без консенсуса | **needs-spec** |

## Контракты, не имеющие TLA+-собственника

Эти свойства важны, но не покрыты формальной моделью (только тесты/опыт):
- `mediator/InstanceMediator` — согласованность уровней (См. DESIGN-02).
- `memory/SdmReader` — теоретические гарантии SDM по Ханселю (полные цепи Ханселя — отложено).
- `hades/Eleutheria` — поведение освобождения при верифицированной φ (открытый вопрос DESIGN-12).

## Формальный гейт

`.github/workflows/tla.yml` (RECON-W38 §14, инцидент D8; RECON-W39 §5,
инцидент D9): SANY + TLC на каждой спецификации, отчёт по размеру
пространства состояний, пустой карантинный список, фильтр путей,
**генерируемый из реестра**, проверка контрольной суммы инструмента и
запуск harness мутаций.

Harness мутаций (`scripts/mutation-harness.py`) применяет намеренный дефект
к **копии** спецификации в изолированном временном каталоге и проверяет, что
свойства его отвергают. Отчёт: `docs-v2/engineering/MUTATION-REPORT.md`.
Результат на 2026-10-30: **12 убиты, 1 выжил** (кворум `Consensus` — реальная
дыра, зафиксированная явно), 0 невалидных, 0 расхождений.

## Расширение

Новые TLA+-спек-кандидаты:
- `BRC-Step` (atomic preserved step contract) — закрытие пробела reasoning/BrcChain.

Все перечислены как **next-format-contracts** в `engineering/PLAN.md`.
