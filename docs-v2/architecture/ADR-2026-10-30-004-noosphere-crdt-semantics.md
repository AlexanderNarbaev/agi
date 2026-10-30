# ADR-2026-10-30-004 — Noosphere CRDT: one deletion semantics per class, and durable tombstones

**Статус:** accepted · **Дата:** 2026-10-30 · **RECON-W39** · **Область:** `io.matrix.noosphere`

## Контекст

`formal/MemoryM4Causal.tla` моделирует M4-слой распределённой памяти:
реплики, tombstone-ы как пары `(key, epoch)`, per-key векторные часы,
epoch-aware правило вставки, причинный merge и frozen-ключи.

Java-реализация, на которую спека ссылается, — `io.matrix.noosphere.GrowOnlySet`.
RECON-W39 проверила её и нашла **три разных несовместимых семантики удаления**
в одном пакете:

| Класс | Семантика удаления | Соответствует `MemoryM4Causal`? |
|---|---|---|
| `Crdt` (интерфейс) | javadoc обещает: «tombstone необратим: любой merge, который **пере-введёт** ключ с epoch ≤ epoch, отвергается» — то есть **более новый** insert (epoch > E) ключ **оживляет** | да (F2) |
| `GrowOnlySet` | tombstone — **вечный**: epoch принимается и **отбрасывается**, фильтр merge безусловный (`removeAll`) | **нет** — это 2P-Set, а не M4 |
| `CausalCrdt` | LWW по `(writerId, clock)`, tombstone побеждает при большем векторе; интерфейс `Crdt` **не реализует** | частично (другой конфликт-порядок) |

Плюс **реальный дефект**: `GrowOnlySet.toJson()` сериализует **только**
`elements`, а `fromJson()` читает список элементов и создаёт **пустое**
множество tombstone-ов. Ключ, помеченный tombstone до сериализации, после
десериализации **может быть введён заново** — history удалений теряется при
перезапуске.

Дополнительно: `FrozenKeys` / `frozenSeen` в модели не имеют **никакого**
Java-соответствия, а `GrowOnlySet` и `CausalCrdt` **не имеют ни одного
production-вызывающего** (проверено: только `GrowOnlySetCausalTest`,
`Exp411PhaseSTest` и `Exp418GrandMasterIntegrationTest` в
`src/experimental`).

## Решение

### R1. Один класс — одна семантика удаления

Никакой класс не может одновременно быть G-Set и нести tombstone-ы.
`GrowOnlySet` — это **two-phase set (2P-Set)**: монотонное добавление плюс
вечные tombstone-ы. Имя класса остаётся (публичный API, переименование ломает
импорт), но **документация приводится в соответствие**: это 2P-Set, а не
G-Set, и `tombstoneAt(key, epoch)` **не использует** epoch — epoch остаётся в
сигнатуре только ради совместимости с интерфейсом и с более богатыми CRDT.

### R2. Epoch-aware вариант — отдельный явный класс

`io.matrix.noosphere.EpochAwareCausalSet` реализует семантику
`MemoryM4Causal`:

- per-key **insert epoch** (аналог `vc[r][k]` в модели);
- **tombstone epoch** — tombstone хранит `(key, epoch)`;
- **epoch-aware правило вставки**: вставка с epoch `e` отвергается, если
  существует tombstone `(k, t)` с `t ≥ e` (то есть tombstone покрывает
  вставку); вставка с epoch **больше** tombstone-а допустима — ровно как
  требует F2 модели;
- **причинный merge**: по каждому ключу побеждает запись с большим epoch;
  tombstone с epoch `t` удаляет ключ, чей insert-epoch ≤ `t`;
- **frozen-ключи**: ключ, помеченный frozen, не может быть вставлен.

Именно этот класс — соответствие для `formal/MemoryM4Causal.tla`.

`CausalCrdt` (LWW по писателям) **не трогается**: это другая, также
корректная семантика для другой задачи; она не реализует интерфейс `Crdt`
и не претендует на соответствие M4-модели. Смешивать их нельзя, и ADR это
фиксирует.

### R3. Tombstone-ы переживают сериализацию — в обоих классах

Формат версионируется:

```json
{"schemaVersion": 2, "elements": ["k1"], "tombstones": ["k2"]}
```

- `schemaVersion` обязателен в новом формате; версия 1 (голый JSON-массив
  элементов) **читается** для обратной совместимости;
- неизвестная версия → отказ (не молчаливое игнорирование);
- противоречивое состояние (например, ключ одновременно в `elements` и в
  `tombstones`) → отказ;
- tombstone-ы **не теряются** при round-trip: `serialize → deserialize →
  merge` сохраняет необратимость удаления;
- вывод **детерминирован** (отсортированные множества) — чтобы формат можно
  было использовать для хеширования и сравнения.

### R4. Интерфейс `Crdt` перестаёт обещать то, чего не делает

Javadoc `Crdt.tombstoneAt` сейчас обещает epoch-семантику для всех
реализаций. Обещание переносится на конкретные реализации: интерфейс
фиксирует, что реализация **либо** поддерживает удаление (override),
**либо** не поддерживает (default бросает `UnsupportedOperationException`),
и что epoch-упорядочивание — свойство конкретной реализации, а не
интерфейса.

### R5. Frozen-ключи — в явной реализации, не в общем интерфейсе

`FrozenKeys` — свойство M4-класса, а не всех CRDT. Оно живёт в
`EpochAwareCausalSet` и не добавляется в `Crdt<T>`.

## Последствия

**Положительно:** у каждого класса одна семантика; дефект потери tombstone-ов
устранён в обоих вариантах; `formal/MemoryM4Causal.tla` получает реальное
Java-соответствие; M4-видение (epoch-aware распределённая память)
сохранено, а не тихо выброшено.

**Отрицательное:** изменение формата сериализации `GrowOnlySet` — breaking
change для потребителей, читающих старый формат. Старый формат остаётся
**читаемым**, но новый writer пишет версионированный объект. В репозитории
потребителей `GrowOnlySet.toJson()` нет (production-вызывающих нет вовсе),
поэтому фактического влияния нет.

**Открытым остаётся:** ни `GrowOnlySet`, ни `EpochAwareCausalSet` не имеют
production-вызывающего. Это зафиксировано в реестре соответствия
(`formal/spec-implementation-map.yml`) как `IMPLEMENTATION_ONLY`, а не
замаскировано под production-возможность. Включение в Noosphere-меш — отдельная
задача (меш сейчас не является распределённой реплицирующей системой:
`MeshFederation` держит `ConcurrentHashMap<String,FnlPackage>`).

## Связанные документы

- `formal/MemoryM4Causal.tla`, `formal/MemoryM4Causal.cfg`
- `matrix-core/src/main/java/io/matrix/noosphere/GrowOnlySet.java`
- `matrix-core/src/main/java/io/matrix/noosphere/Crdt.java`
- `matrix-core/src/main/java/io/matrix/noosphere/EpochAwareCausalSet.java`
- `matrix-core/src/test/java/io/matrix/noosphere/GrowOnlySetPersistenceTest.java`
- `matrix-core/src/test/java/io/matrix/noosphere/EpochAwareCausalSetTest.java`
- `docs-v2/engineering/TLA-STATUS.md`
