# FORMAL-CONTRACTS

Каталог TLA+-спецификаций из `formal/`. Мэппинг на код и на инварианты.

## Спек-каталог

| TLA+ | Класс(ы) | Инвариант | Статус |
|---|---|---|---|
| `BotEthicsPipeline` | `ethics/BotEthicsPipeline`, `ethics/EthicalFilter`, `ethics/frozen/FrozenEthicalFNL` | четыре запрета (Конституция IV) при любых входах | TLA+ CFG; мат. соотв. подтверждено эксп. (EXP-006 FPR 0%, TPR 100%) |
| `Consensus` | `consensus/ConsensusBenchmark` (внутренний) | Byzantine/дебат: соглашение k-of-n агентов | черновик, без формальной верификации |
| `FrozenEthicalFNL` | `ethics/frozen/FrozenAxiomNeuron`, `FrozenEthicalFNL`, `TextFeatureExtractor`, `TruthTableUtil` | монотонность запретов | TLA+ (FROZEN) |
| `HashChain` | `audit/HashChain` | цепная целостность audit-trail | TLA+ CFG |
| `MPDTNeuron` | `neuron/MPDTNeuron*` | границы состояний автоматов Цетлина | без TLA+ — карточка Нужна спека |
| `CellLifecycle` (нет файла — см. needs-spec) | `lifecycle/CauldronProtocol`, `FnlGate` | SHADOW→CANDIDATE→PROMOTED | **отложено: формализация** |

## Матрица «класс↔инвариант»

| Класс | Инвариант | Метод проверки |
|---|---|---|
| `bir/BooleanRuntime` | детерминизм eval | unit test + INV-1 source-scan |
| `ethics/frozen/FROZENFNLGuardian` | запреты независимы от входа | TLA+ `FrozenEthicalFNL` + EXP-006 |
| `audit/HashChain` | append-only + tamper-evident | TLA+ `HashChain` |
| `reasoning/BrcChain` | пред-/пост-условия шагов | **needs-spec** (BRC contract) |
| `lifecycle/FnlGate` | монотонные переходы без отката | EXP-009 + manual |
| `media/PatternLockedAnchor` | системный якорь не двигаем без консенсуса | **needs-spec** |

## Контракты, не имеющие TLA+ -собственника

Эти свойства важны, но не покрыты формальной моделью (только тесты/опыт):
- `mediator/InstanceMediator` — согласованность уровней (См. DESIGN-02).
- `memory/SdmReader` — теоретические гарантии SDM по Ханселю (полные цепи Ханселя — отложено).
- `hades/Eleutheria` — поведение освобождения при верифицированной φ (открытый вопрос DESIGN-12).

## Расширение

Новые TLA+-спек-кандидаты:
- `BRC-Step` (atomic preserved step contract) — закрытие пробела reasoning/BrcChain.
- `ConjugateBudgeter-DP` — инварианты DP: асимптотическая оптимальность, bounded shadow-price range.
- `MCTS-LATS-Visit` — конвергенция к α-Root (теоретически не записана).
- `Memory-M4-Causal` — eventual consistency при quorum R/W (draft есть в `noosphere/p2p/`).

Все перечислены как **next-format-contracts** в `engineering/PLAN.md`.
