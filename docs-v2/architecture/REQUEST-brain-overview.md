# REQUEST-brain-overview — Brain-like Anatomy of MATRIX

## Что это

AR-документ: кросс-секционные директивы, **как** MATRIX организован в виде brain-like контура. Это не спека и не дизайн — конкретика уходит в [SPEC-004-perception](../specifications/SPEC-004-perception.md), [SPEC-005-deliberation](../specifications/SPEC-005-deliberation.md), [SPEC-006-action](../specifications/SPEC-006-action.md), [SPEC-007-memory-hierarchy](../specifications/SPEC-007-memory-hierarchy.md), [SPEC-008-autonomy-impulses](../specifications/SPEC-008-autonomy-impulses.md), [SPEC-009-decentralized-digests](../specifications/SPEC-009-decentralized-digests.md). Настоящий AR фиксирует **скелет** и инварианты стыков; ничего не добавляет сверх существующего OVERVIEW и DESIGN-03.

## Анатомия (высокий уровень)

```
 ╔══════════════════════════════════════╗
 ║ FROZEN ETHICAL CORE (CONSTITUTION IV)║
 ║ не обходится, не мутируется, не выкл. ║
 ╚════════════════════╦═════════════════╝
 ║ каждый шаг — через FROZEN-гейт
 ┌───────────────────┐ ║ ┌──────────────────┐
 │ PERCEPTION │ BitSet / packed │ │ ACTION │
 │ (signals/, enc.) ├─────────────────►╬────────────────►│ (actions/, plan) │
 │ SPEC-004 │ │ │ SPEC-006 │
 └────────┬──────────┘ │ └─────────┬────────┘
 │ │ │
 ▼ ▼ ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │ CONSCIOUSNESS / DELIBERATION │
 │ BRC + Viewpoint + AgentLoop + MCTS/LATS + DevLoop │
 │ SPEC-005 │
 └────────────────────────────────────────────────────────────────────────┘
 ▲ ▲ ▲
 │ MEMORY SUBSTRATE │ │
 │ ┌────────────────────────┐ │ │
 ├─►│ m0 / M0 short-term │ │ working + episodic │
 │ │ M1..M3 long-term │ │ semantic + procedural │
 │ │ M4..M5+ subconscious │ │ federated + reflexes │
 │ └─────────┬──────────────┘ │ │
 │ ▼ │ │
 │ Anonymizer (k-anon + DP) │ │
 │ │ │ │
 └────────────┼─────────────────┼───────────────────────────┘
 ▼ ▼
 ┌──────────────────────────────────────┐
 │ AUTONOMY IMPULSES │
 │ curiosity · consolidation · │
 │ integrity-check · share-digest │
 │ SPEC-008 │
 └──────────────────┬───────────────────┘
 ▼
 ┌─────────────────────────────────────┐
 │ DECENTRALIZED DIGESTS (noosphere) │
 │ neuron-blocks × Anonymizer × ELSP │
 │ SPEC-009 │
 └─────────────────────────────────────┘
```

## 1. Perception — вход

- Кодировщики (`signals/{Text,Image,Audio}SignalModule`, DESIGN-06): внешний сенсорный поток → булев BitSet (INV-P4, плотность 0.1–0.3 для SDM-совместимости).
- Distiller (`distill/Distiller`, SPEC-001 этап B): автономный учитель → калибровочный корпус → BIR (не path решений, а инициализация).
- Инвариант: perception **никогда** не вызывает LLM; любая «интеллектуальная» интерпретация — в Delibерации.
- Граница: декодер (rendering) — обратный модуль; в этой AR он относится к Action.

## 2. Consciousness / Deliberation — ядро

- BRC (`reasoning/BrcChain`): witnessed-step цепочка; контракт нужен (`needs-spec` в MODULES.md), пакет `BRC-Step` TLA+ — top-priority next-format-contract.
- Viewpoint (`brain/Viewpoint`, DESIGN-02): взвешенный ансамбль named-evaluators; tie-break по min-name.
- MCTS/LATS (`mcts/`) поверх World Model; World Model — предиктор «состояние+действие → следствие», строится из event-sourcing.
- DevLoop (`devloop/`, SPEC-000): maturity gates MA-0..MA-5; **DevLoop не в Delibерации пользовательского запроса** — он отдельный (см. Autonomy).
- Контракт INV-1: в обход `BooleanRuntime.evaluate(BIR, long[])` — прямые вызовы `.evaluate()` за пределами whitelist заблокированы source-scan стражем.

## 3. Action — выход

- Планы (`actions/PlanRunner`, DESIGN-13): Hoare-триплеты `P{effect}Q`; ошибки `precondition_violated`/`postcondition_violated`/`invariant_violated`.
- AC-3 fast-fail (`actions/PlanPreprocessor`, DESIGN-15): CSP-предобработка до исполнения.
- Версионируемый атомарный своп (`actions/VersionedContract`): инвариант `next.version == version + 1`.
- **Каждое действие** до фиксации state проходит `ethics/EthicalFilter → StructuralSafetyGuard → LieDetector` — см. §FROZEN Core.

## 4. Memory substrate — три уровня

Подробно — [REQUEST-memory-hierarchy.md](REQUEST-memory-hierarchy.md). Здесь только скелет:

| Слой | Тип | Срок | Реализация |
|---|---|---|---|
| m0 (short-term) | рабочий регистр | такт | `HierarchicalMemory` in-memory |
| M1 (episodic) | эпизоды | сессия+ | PG (R2DBC), SdmReader |
| M2 (semantic) | факты, концепты | постоянно | BIR-реестр + FCA-решётка |
| M3 (procedural) | навыки, планы | постоянно | BirNet-композиции, Skill Library |
| M4 (collective) | федеративный пул | глобально | CRDT (`noosphere/Crdt`), ML-DSA v2 |
| M5+ (subconscious) | рефлексы, кэши | глобально | Anonymizer digests, fast-path |

## 5. Autonomy impulses — побуждающая активность

Подробно — [REQUEST-autonomy-impulses.md](REQUEST-autonomy-impulses.md). Четыре импульса: **curiosity** (исследование компетенций), **consolidation** (sleep-цикл), **integrity-check** (lineage + drift), **share-digest** (анонимизированный выход в noosphere). Все импульсы проходят через `budgeter/ConjugateBudgeter` (DESIGN-11) и обязаны соблюдать FROZEN Core.

## 6. Decentralized digests — noosphere

Подробно — [REQUEST-decentralized-digests.md](REQUEST-decentralized-digests.md). Neuron-blocks множатся рядом с пользователем; в общий пул — только анонимизированные дайджесты (`federation/Anonymizer`, k-anonymous + DP-noise). Подпись и репликация — через ELSP v1/v2 (DESIGN-08).

## 7. FROZEN Ethical Core — единственная неизменяемая вертикаль

- Четыре запрета вшиты в `ethics/frozen/FROZENFNLGuardian`; TLA+ спека `FrozenEthicalFNL` (FORMAL-CONTRACTS.md).
- FROZEN-артефакты — hash-locked: любая попытка модификации ловится audit-цепью (`audit/HashChain`, x-matrix-trace header).
- **Запрещено**: обход этики через Impulse, Action, Autonomy; снижение запретов в новых доменах без RFC-мандата и `consensus/ConsensusBenchmark`.
- **Не обещается** «не используется во вред» в абсолютном смысле — FROZEN снижает риск, но не устраняет (CONSTITUTION VI).

## Кросс-секционные инварианты

1. **Детерминизм пути решения** — любые вход → одинаковый выход; Random/wall-clock вне рантайма (CONSTITUTION I; DESIGN-14).
2. **K_MAX=20** на BirUnit (`TruthTable.java`); не менять без RFC.
3. **Покрытие тестами ≥ 82%** (JaCoCo gate, CONSTITUTION V).
4. **Никаких LLM в рантайме** (CONSTITUTION VI) — дистиллят только при загрузке.
5. **Audit-цепь** (`audit/HashChain`) — каждое решение оставляет witness; tamper-evident.
6. **Substrate-neutrality**: всё выше BIR зависит только от контракта `evaluate(BIR, bits) → bits`; смена бэкенда (JVM/FPGA/quantum) — без изменения логики.

## Что этот AR НЕ утверждает

- Никаких «AGI», «общий интеллект», «самостоятельное мышление» (CONSTITUTION VI).
- Никаких абсолютных гарантий безопасности — только измеримые инварианты.
- Никаких обещаний сроков на M5+/subconscious, federated digests — preregistered гипотезы H-011, H-016 и др.

## Куда расти

Гэп-анализ (`engineering/SDD-COVERAGE.md`) фиксирует `reasoning/BrcChain` как top-priority `needs-spec`. Открытые задачи Brain-волны:
- SPEC-004..009 — placeholder для спецификаций, которые напишут другие исполнители.
- TLA+ `BRC-Step` (атомарный preserved-step) — next-format-contract.
- TLA+ `Memory-M4-Causal` — quorum R/W eventual consistency.
