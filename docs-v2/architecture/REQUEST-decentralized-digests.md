# REQUEST-decentralized-digests — анонимные дайджесты в noosphere

**Статус: normative · singleton AR** · changelog `2026-08-26 — brain wave v1`.

## Что это

AR-документ: директивы по топологии «neuron-blocks × near-user × digest-up». Существующая норма — [DESIGN-08-federation](../designs/DESIGN-08-federation.md) (ELSP v1 Ed25519 + ML-DSA v2, `federation/Anonymizer`). Этот AR **расширяет роль** Anonymizer'а с «единичного фильтра исходящих digests» до «границы между локальным контекстом и глобальным пулом знаний». Полная спецификация — [SPEC-009-decentralized-digests](../specifications/SPEC-009-decentralized-digests.md) (placeholder).

## Принцип

```
                  УЗЕЛ ПОЛЬЗОВАТЕЛЯ (one peer)
   ┌─────────────────────────────────────────────────────────┐
   │  ┌──────────────┐   ┌──────────────┐   ┌─────────────┐  │
   │  │ neuron-block │   │ neuron-block │   │ neuron-block│  │
   │  │  (context A) │   │  (context B) │   │ (context C) │  │
   │  └──────┬───────┘   └──────┬───────┘   └──────┬──────┘  │
   │         │ local cache      │                  │         │
   │         └──────────┬───────┴──────────────────┘         │
   │                    ▼                                     │
   │           ┌──────────────────────┐                       │
   │           │  Aggregator (M2 Δ)   │                       │
   │           └──────────┬───────────┘                       │
   │                      ▼                                   │
   │   ╔═══════════════════════════════════════════════════╗  │
   │   ║   Anonymizer  (k-anonymous bucket + DP-noise)     ║  │
   │   ║   • suppress quasi-identifiers                    ║  │
   │   ║   • k ≥ K_MIN (config)                            ║  │
   │   ║   • DP noise ε ≤ ε_BUDGET (audited)               ║  │
   │   ║   • tombstone on request                          ║  │
   │   ╚════════════════════════════╤══════════════════════╝  │
   │                                ▼                         │
   │                     ┌─────────────────────┐              │
   │                     │  ELSP sign (ML-DSA) │              │
   │                     └─────────┬───────────┘              │
   └───────────────────────────────┼───────────────────────────┘
                                   ▼
                          ┌──────────────────┐
                          │  Gossip to peers │
                          └─────────┬────────┘
                                    ▼
                ╔═══════════════════════════════════════╗
                ║  NOOSPHERE M5 — anonymized digest pool ║
                ║   (CRDT LWW; lineage; X-Matrix-Tenant) ║
                ╚═══════════════════════════════════════╝
```

## Новая роль Anonymizer

В DESIGN-08 Anonymizer — фильтр с k-анонимностью. Здесь роль расширяется до **пограничного слоя** между локальным и глобальным:

| Слой ответственности | Что делает | Где |
|---|---|---|
| Quasi-identifier suppression | удаляет/обобщает связки, реконструирующие идентичность | `federation/Anonymizer` |
| K-anonymous bucketing | гарантирует ≥ K записей в любом выпущенном бакете | `federation/Anonymizer` |
| DP noise injection | ε ≤ ε_BUDGET, δ ≤ δ_BUDGET; аудит через `audit/HashChain` | `federation/Anonymizer` |
| Tombstone propagation | на запрос «забудь» — tombstone в M2 и propagation в M5 | `federation/Anonymizer` + M4 CRDT |
| Schema-binding | digest знает свою схему (ioSchema); дайджесты без схемы не принимаются | registry |

Anonymizer — **единственная** точка, через которую локальный контекст покидает узел. Любой другой путь нарушает AR (для исключений — RFC-мандат).

## Neuron-blocks: локальная репликация у пользователя

- **Определение**: neuron-block — логически связная группа BirUnit + связная M0/M1/M2, обслуживающая конкретный контекст (диалог, домен, persona).
- **Мультипликация**: блоки растут вширь рядом с пользователем (per-tenant, per-domain); не централизованная схема.
- **Граница**: один neuron-block не видит данные другого neuron-block без явного cross-context вызова через Guardrail.
- **Контракт**: каждый neuron-block детерминирован, audit-trailed, проходит FROZEN-гейт на входе и выходе.

## Что уходит в M5 (и что нет)

**Уходит** (после Anonymizer):
- агрегированные счётчики компетенций (per-domain histogram, k ≥ K_MIN);
- сжатые схемы фактов с k-анонимной bucketing;
- pattern-fingerprints (Ricci-fingerprint по SPEC-003, но агрегированные и зашумлённые);
- (опц.) DP-noisy распределения, полезные для curriculum-ordering на стороне peer-ов.

**НЕ уходит**:
- эпизоды M1 (содержат идентифицирующий контекст);
- отдельные BIR-артефакты (слишком гранулярно для анонимизации);
- любые payload-ы с персональными данными (NFR-9 GOALS-REQUIREMENTS);
- FROZEN-артефакты этики (они публичны через audit, но не через M5 — отдельный канал);
- состояние m0/M0 (transient).

## Контракт Anonymizer

```text
Anonymizer.localPublish(record, policy) -> Either<AnonymizedDigest, Rejection>

  policy:
    kMin          : Nat       (≥ K_MIN config; default 5)
    epsilonBudget : Real      (≤ ε_BUDGET)
    deltaBudget   : Real      (≤ δ_BUDGET)
    schemaRequired: Boolean   (true)

  pre:
    record.isFROZEN = false       * FROZEN-артефакты не идут через M5
    schemaRequired => record.ioSchema ≠ null
    record contains no PII per NFR-9

  post on success:
    digest.bucketSize >= kMin
    digest.noise satisfies (εBudget, δBudget)-DP
    digest.lineage in audit/HashChain
    digest.signature valid (ML-DSA v2)

  post on failure:
    Rejection{ reason, auditTraceId }
    * rejection тоже логируется (для adversarial audit)
```

## Угрозы и контрмеры

| Угроза | Контрмера |
|---|---|
| Re-identification через корреляцию дайджестов | k-anonymous bucketing; ограничение временного окна публикации |
| Side-channel через DP-budget exhaustion | ε-budget — per-peer counter; exhaustion → reject до конца окна |
| Sybil-инжекция ложных дайджестов | ELSP-подпись (ML-DSA v2); reputation peer-ов (noosphere) |
| Забывание (right-to-be-forgotten) | tombstone через `federation/Anonymizer.forget(recordId)` → CRDT-tombstone |
| Membership inference | добавление калибровочного шума; дифф-приватность через DP budget |
| Adversarial composition (combine digests) | ограничение cardinality выпуска per-window; линейный budget |

## Стыковка с DESIGN-08

| DESIGN-08 компонент | Роль в этом AR |
|---|---|
| `ElspChannelMlDsa` | подпись/верификация дайджестов (ML-DSA v2, нативен в JDK25) |
| `ArtifactSigner` | подпись самих артефактов до aggregation (опц., для reputation) |
| `Anonymizer` | расширенная роль — см. таблицу выше |
| `MeshFederation` | gossip + topology |
| `ElspChannel` (v1 Ed25519) | legacy-совместимость; v2 предпочтительно |

## Стыковка с этикой

- Любой дайджест, прежде чем попасть в Anonymizer, проверяется через FROZEN-гейт (EthicalFilter + LieDetector). **Нет** «сырых» данных в M5.
- `digest.isFROZEN` гарантирует, что FROZEN-артефакты не покидают узел как редактируемые записи — только как подписанные attestations (см. Memory-M4-Causal FrozenImmutability в REQUEST-memory-hierarchy.md).
- Audit-цепь `audit/HashChain` обязательна для каждого `localPublish` вызова — успех и отказ.

## Открытые задачи

- H-008 (proposed): MPDT proof memory batch mode ≥1000 units/tick — производительность aggregator-а.
- H-011 (running): SDM M1 read beats flat top-K Hamming — recall на локальном M1, прежде чем aggregation.
- TLA+ `Memory-M4-Causal` (next-format-contract) — quorum R/W eventual consistency M5.
- DP-budget calibration — экспериментальный подбор ε для разных доменов (не обещание, preregistered EXP).
- mTLS для peer-interconnect — отложено (DESIGN-08); нужно для production-grade.

## Чего этот AR НЕ утверждает

- Никаких абсолютных гарантий приватности — это инженерные инварианты с настраиваемыми порогами (NFR-9).
- Никаких обещаний, что noosphere «выучит» агрегированное — федерация — это транспорт и пул, не учитель.
- Никакого PII в M5 в любой форме — это запрещено явно; компрометация Anonymizer — RFC-инцидент.

Для исторической глубины см. `archive/2026-08-pre-v2/vision/vision/ARCHITECTURE.md` §3.4 (Noosphere — 4-слойное решение: governance, доверие, транспорт, устойчивость знания) и `archive/2026-08-pre-v2/vision/vision/GOALS-REQUIREMENTS.md` FR-12, FR-13, NFR-9, NFR-11.