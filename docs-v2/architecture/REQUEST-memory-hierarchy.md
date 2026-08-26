# REQUEST-memory-hierarchy — иерархия памяти MATRIX

**Статус: normative · singleton AR** · changelog `2026-08-26 — brain wave v1`.

## Что это

AR-документ: кросс-секционные директивы по иерархии памяти. Текущая норма — [DESIGN-05-memory](../designs/DESIGN-05-memory.md) (5 слоёв M0..M4). Этот AR **расширяет** DESIGN-05 тремя дополнительными слоями (m0, M5, M5+) и фиксирует TLA+-обязательства. Полная спецификация — [SPEC-007-memory-hierarchy](../specifications/SPEC-007-memory-hierarchy.md) (placeholder, ещё не написан).

## Слои и физика

```
                  Latency        Eviction            Persistence
   ┌────────────────────────────────────────────────────────────────────┐
 m0 │ working register   <1 µs    overwrite-on-commit   in-proc Map    │
 M0 │ scratchpad buffer  <10 µs   ring-overwrite       off-heap arena │
 M1 │ episodic stream    <50 ms   SDM-decay counter    PG (R2DBC)     │
 M2 │ semantic facts    <5 ms    tombstones + Φ       BIR registry    │
 M3 │ procedural skills <1 ms   versioned swap       BirNet + lineage │
 M4 │ noosphere / pool   async   CRDT-LWW + tombstone federation gossip│
 M5 │ subconscious cache <100 µs  budgeted             Anonymizer + ELSP│
 M5+│ reflexes (fast)   <1 µs   immutable until MA-4 FROZEN after cert│
   └────────────────────────────────────────────────────────────────────┘
                          │
                          ▼
                audit/HashChain — append-only
                (x-matrix-trace header на каждом fixed-point)
```

## Короткая память (m0, M0)

- **m0**: регистр текущего такта Delibерации. Переживает только текущий шаг BRC. Не пишется в журнал. Семантика — «что я держу в фокусе прямо сейчас».
- **M0**: scratchpad на одну сессию (одна когерентная «нить разговора»). Переживает несколько шагов; eviction — ring-overwrite при заполнении.
- **Реализация**: `memory/HierarchicalMemory`, off-heap arena (DESIGN-05 §реализация).
- **Граница**: m0/M0 — **не** Durable; при crash — пусто. Это by design.

## Длинная память (M1, M2, M3)

- **M1 (episodic)**: поток эпизодов с Хэмминг-адресацией. Реализация — `memory/SdmReader` (Kanerva SDM, см. [science/SUBSTRATE-MODELS.md](../science/SUBSTRATE-MODELS.md) §3). Счётчики затухают — полураспад есть политика забывания с точной временной константой (не эвристика). Домен: «что было».
- **M2 (semantic)**: факты, концепты, импликации атрибутов. BIR-реестр + FCA-решётка ([SPEC-003](../specifications/SPEC-003-knowledge-topology.md) и SUBSTRATE-MODELS §4.1). Tombstones обязательны (GDPR-аналог; см. NFR-9 privacy в GOALS-REQUIREMENTS). Домен: «что известно».
- **M3 (procedural)**: BirNet-композиции, планы, навыки. Семантика: «как делать». Каждая единица — versioned `actions/VersionedContract` (DESIGN-13); promote только через FNL-гейт (DESIGN-12).
- **Контракт целостности**: M2/M3 элементы имеют `LineageLedger` запись; `audit/HashChain` — append-only с tamper-evident.

## Subconscious (M4, M5, M5+)

- **M4 (noosphere pool)**: федеративный пул знаний. CRDT LWW/GrowOnlySet (`noosphere/Crdt`); gossip + ML-DSA v2 (`federation/ElspChannelMlDsa`, DESIGN-08). Подмножество CAPABILITY загружается через `lifecycle/FnlGate` (SHADOW→CANDIDATE→PROMOTED), см. DESIGN-12.
- **M5 (anonymized digests)**: дайджесты локального контекста, прошедшие через `federation/Anonymizer` (k-anonymous + DP-noise). Не содержит PII; домен: «что мир знает в целом». Подробно — [REQUEST-decentralized-digests](REQUEST-decentralized-digests.md).
- **M5+ (reflexes)**: pre-computed быстрые пути для safety-critical. FROZEN после сертификации MA-N. Источник сигнала — расхождение предсказания и исхода в event-sourcing (аналог climbing fiber, см. SUBSTRATE-MODELS §5.1). Домен: «как реагировать за микросекунды».

## Эвикция и forget-policy

- **m0/M0**: overwrite-on-commit / ring-overwrite — без сохранения.
- **M1**: SDM-счётчики с полураспадом τ (конфиг домена); достижение порога → tombstone.
- **M2**: явный tombstone через `audit/HashChain`; никакого «неявного» стирания.
- **M3**: версионирование; demoted-версии остаются для аудита, но неактивны.
- **M4**: CRDT LWW с явной quorum-проверкой (`noosphere/QuorumChecker`).
- **M5**: TTL в дайджесте; по истечении — удаление из пула (tombstone в M2).
- **M5+**: иммутабельны (FROZEN).

## TLA+-обязательства (next-format-contract кандидат)

Имя спеки: `Memory-M4-Causal`. Цель — формализовать eventual consistency M4 при quorum R/W.

```tla
\* Скетч — НЕ финальный TLA+, перенесён в formal/ отдельной задачей.
VARIABLES localLog, peerLog, quorumReached, tombstoned

TypeOK ==
  /\ localLog \in Seq(M4Record)
  /\ peerLog \in [PeerId -> Seq(M4Record)]
  /\ quorumReached \in BOOLEAN
  /\ tombstoned \subseteq M4Record

\* M1: локальный append строго монотонен
Monotonicity == Len(localLog') = Len(localLog) + 1

\* M2: tombstone не восстанавливается
TombstoneIrreversible == \A r \in tombstoned: r \notin M4Active'

\* M3: eventual consistency — каждый peer рано или поздно видит все
\* не-tombstoned записи после quorum R/W
EventualConsistency ==
  [](quorumReached => <>(\A p \in Peers:
    M4Active \subseteq Image(peerLog[p])))

\* M4: FROZEN-артефакты (этика) никогда не появляются в M4 как
\* редактируемые записи — только как подписанные attestations
FrozenImmutability ==
  \A r \in M4Record: r.isFROZEN => r \notin EditableSet

Spec == Init /\ [][Next]_<<localLog, peerLog, quorumReached, tombstoned>>
          /\ WF_<<...>>(QuorumStep)
          /\ EventualConsistency
          /\ FrozenImmutability
```

Полная спека — `formal/MemoryM4Causal.tla` (next-format-contract, не реализовано). Аудит — реализация `noosphere/QuorumChecker` + integration-test на Testcontainers.

## Инварианты иерархии

1. **Направление записи**: m0 → M0 → M1 → M2 → M3 (promote через FNL). Обратное направление — только через явный demote с аудитом.
2. **Чтение**: любой слой читается; задержки даны в таблице.
3. **FROZEN-инвариант**: этические артефакты никогда не правятся в M4 (см. FrozenImmutability в TLA+-скетче).
4. **Privacy-инвариант**: M5-дайджесты не реконструируемы до уровня идентификации пользователя (см. Anonymizer k-anon + DP budget в DESIGN-08).
5. **Lineage-инвариант**: каждый promote в M2/M3 сопровождается записью в `LineageLedger`.

## Открытые задачи

- H-011 (running): SDM M1 read beats flat top-K Hamming precision@5 — измеряется.
- H-016 (proposed): MonotoneDecoder within target accuracy at ≥5× smaller corpus — после полных цепей Ханселя.
- TLA+ `Memory-M4-Causal` — top-priority next-format-contract (FORMAL-CONTRACTS.md).
- H-014 (proposed): VC-оценка предсказывает holdout-window threshold — для размера окна Φ-гейта.

## Куда расти

- [SPEC-007-memory-hierarchy](../specifications/SPEC-007-memory-hierarchy.md) — формальная спека (placeholder).
- Расширение `lifecycle/FnlGate` для двойного карантина IMPORT_M4 ↔ DESIGN-08 (отложено в DESIGN-12).
- M5+ рефлексы после сертификации MA-N — preregistered EXP, не обещание сроков.

Для исторической глубины см. `archive/2026-08-pre-v2/science/science/SUBSTRATE-MODELS.md` §3 (SDM/Каnerва), §4.1 (FCA), §5 (мозжечковый слой → M5+ рефлексы); для онтологии верхнего уровня — `archive/2026-08-pre-v2/vision/vision/ARCHITECTURE.md` §3.3 (консолидация «гиппокамп→неокортекс»).